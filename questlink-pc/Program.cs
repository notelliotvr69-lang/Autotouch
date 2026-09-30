using Microsoft.Win32;
using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using System.Windows.Forms;

namespace QuestLinkPC;

internal static class Program
{
    [STAThread]
    static void Main()
    {
        ApplicationConfiguration.Initialize();
        Application.Run(new MainForm());
    }
}

public sealed class MainForm : Form
{
    private const int Port = 47990;
    private const string Version = "7.12-dev";

    private readonly Label status = new();
    private readonly Label lanStatus = new();
    private readonly Label ipLabel = new();
    private readonly ListBox games = new();
    private readonly TrackBar worldScale = new();
    private readonly Label worldScaleValue = new();
    private readonly TrackBar renderScale = new();
    private readonly Label renderScaleValue = new();
    private readonly Button applySteamVr = new();
    private readonly Button launchSelected = new();
    private readonly Button refreshGames = new();
    private readonly Button openSteamVrSettings = new();
    private readonly Label runtimeStatus = new();
    private readonly Label runtimePath = new();
    private readonly Button refreshRuntime = new();
    private readonly Button launchGtagQuick = new();
    private readonly ListBox gtagMods = new();
    private readonly Label gtagModSummary = new();
    private readonly Button refreshGtagMods = new();

    private readonly CancellationTokenSource cts = new();
    private TcpListener? lanListener;
    private Task? lanServerTask;
    private List<VrGame> currentGames = new();

    public MainForm()
    {
        Text = "QuestLink PC";
        Width = 900;
        Height = 670;
        MinimumSize = new Size(760, 560);
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = Color.FromArgb(15, 17, 22);
        ForeColor = Color.White;
        Font = new Font("Segoe UI", 10f);

        BuildUi();
        Shown += async (_, _) =>
        {
            StartLanServer();
            RefreshRuntimeStatus();
            await ReloadGamesAsync();
        };
        FormClosing += (_, _) =>
        {
            cts.Cancel();
            try { lanListener?.Stop(); } catch { }
        };
    }

    private void BuildUi()
    {
        var tabs = new TabControl { Dock = DockStyle.Fill };

        var home = new TabPage("Connection") { BackColor = BackColor, ForeColor = ForeColor };
        var library = new TabPage("PCVR Library") { BackColor = BackColor, ForeColor = ForeColor };
        var steamvr = new TabPage("SteamVR Settings") { BackColor = BackColor, ForeColor = ForeColor };
        var mods = new TabPage("GTAG Mods") { BackColor = BackColor, ForeColor = ForeColor };

        tabs.TabPages.Add(home);
        tabs.TabPages.Add(library);
        tabs.TabPages.Add(steamvr);
        tabs.TabPages.Add(mods);
        Controls.Add(tabs);

        var title = MakeLabel("QuestLink PC V" + Version, 24, true);
        title.SetBounds(24, 24, 500, 40);
        home.Controls.Add(title);

        ipLabel.Text = "PC IP: " + NetworkHelpers.GetLanIp() + ":" + Port;
        ipLabel.SetBounds(28, 82, 700, 32);
        ipLabel.Font = new Font("Segoe UI", 14f, FontStyle.Bold);
        home.Controls.Add(ipLabel);

        lanStatus.Text = "LAN server: starting...";
        lanStatus.SetBounds(28, 122, 760, 28);
        lanStatus.ForeColor = Color.Gold;
        home.Controls.Add(lanStatus);

        status.Text = "Starting...";
        status.SetBounds(28, 152, 760, 28);
        status.ForeColor = Color.LightGray;
        home.Controls.Add(status);

        var note = MakeLabel(
            "Enter the PC IP shown above into the Quest app. Pairing is LAN-only and does not install anything over IP.",
            11, false);
        note.SetBounds(28, 198, 790, 62);
        note.AutoSize = false;
        home.Controls.Add(note);

        var native = MakeLabel(
            "Native Windows app — no Python required.",
            11, true);
        native.SetBounds(28, 266, 700, 30);
        home.Controls.Add(native);

        var runtimeTitle = MakeLabel("QuestLink Runtime / OpenComposite", 15, true);
        runtimeTitle.SetBounds(28, 316, 420, 32);
        home.Controls.Add(runtimeTitle);

        runtimeStatus.SetBounds(28, 354, 760, 28);
        runtimeStatus.Font = new Font("Segoe UI", 11f, FontStyle.Bold);
        home.Controls.Add(runtimeStatus);

        runtimePath.SetBounds(28, 388, 790, 52);
        runtimePath.AutoSize = false;
        runtimePath.ForeColor = Color.LightGray;
        home.Controls.Add(runtimePath);

        refreshRuntime.Text = "Refresh Runtime Status";
        refreshRuntime.SetBounds(28, 452, 210, 42);
        refreshRuntime.Click += (_, _) => RefreshRuntimeStatus();
        home.Controls.Add(refreshRuntime);

        launchGtagQuick.Text = "Launch Gorilla Tag with QuestLink";
        launchGtagQuick.SetBounds(260, 452, 360, 52);
        launchGtagQuick.Font = new Font("Segoe UI", 11f, FontStyle.Bold);
        launchGtagQuick.Click += async (_, _) => await LaunchGorillaTagQuickAsync();
        home.Controls.Add(launchGtagQuick);

        var launchHint = MakeLabel(
            "No SteamVR. QuestLink swaps Gorilla Tag's OpenVR loader to OpenComposite, which forwards GTAG directly into the QuestLink OpenXR runtime.",
            9.5f, false);
        launchHint.SetBounds(28, 518, 790, 55);
        launchHint.AutoSize = false;
        home.Controls.Add(launchHint);

        games.SetBounds(22, 22, 600, 460);
        games.BackColor = Color.FromArgb(20, 23, 30);
        games.ForeColor = Color.White;
        games.BorderStyle = BorderStyle.FixedSingle;
        games.DisplayMember = nameof(VrGame.DisplayName);
        library.Controls.Add(games);

        refreshGames.Text = "Refresh";
        refreshGames.SetBounds(642, 22, 180, 42);
        refreshGames.Click += async (_, _) => await ReloadGamesAsync();
        library.Controls.Add(refreshGames);

        launchSelected.Text = "Launch Selected";
        launchSelected.SetBounds(642, 76, 180, 42);
        launchSelected.Click += async (_, _) => await LaunchSelectedAsync();
        library.Controls.Add(launchSelected);

        var libNote = MakeLabel(
            "QuestLink scans installed Steam PCVR titles plus Meta Horizon PC-library apps. Gorilla Tag also reports whether BepInEx is installed.",
            10, false);
        libNote.SetBounds(642, 140, 190, 180);
        libNote.AutoSize = false;
        library.Controls.Add(libNote);

        var wsTitle = MakeLabel("World Scale", 14, true);
        wsTitle.SetBounds(28, 24, 220, 30);
        steamvr.Controls.Add(wsTitle);

        worldScale.Minimum = 10;
        worldScale.Maximum = 1000;
        worldScale.Value = 100;
        worldScale.TickFrequency = 50;
        worldScale.SetBounds(28, 62, 650, 55);
        worldScale.Scroll += (_, _) => UpdateScaleLabels();
        steamvr.Controls.Add(worldScale);

        worldScaleValue.SetBounds(700, 65, 130, 35);
        steamvr.Controls.Add(worldScaleValue);

        var rsTitle = MakeLabel("Per-game Render Scale", 14, true);
        rsTitle.SetBounds(28, 140, 300, 30);
        steamvr.Controls.Add(rsTitle);

        renderScale.Minimum = 20;
        renderScale.Maximum = 300;
        renderScale.Value = 100;
        renderScale.TickFrequency = 20;
        renderScale.SetBounds(28, 178, 650, 55);
        renderScale.Scroll += (_, _) => UpdateScaleLabels();
        steamvr.Controls.Add(renderScale);

        renderScaleValue.SetBounds(700, 181, 130, 35);
        steamvr.Controls.Add(renderScaleValue);

        applySteamVr.Text = "Apply to Selected Game";
        applySteamVr.SetBounds(28, 260, 230, 46);
        applySteamVr.Click += (_, _) => ApplySteamVrSettings();
        steamvr.Controls.Add(applySteamVr);

        openSteamVrSettings.Text = "Open SteamVR Settings";
        openSteamVrSettings.SetBounds(274, 260, 230, 46);
        openSteamVrSettings.Click += (_, _) => OpenSteamVr();
        steamvr.Controls.Add(openSteamVrSettings);

        var caveat = MakeLabel(
            "These settings only affect games that actually run through SteamVR/OpenVR. If Gorilla Tag is launched in native Oculus / Quest Link mode, SteamVR world scale and render scale do not control that session.",
            10, false);
        caveat.SetBounds(28, 330, 790, 110);
        caveat.AutoSize = false;
        steamvr.Controls.Add(caveat);

        var selectHint = MakeLabel(
            "Select a game on the PCVR Library tab first, then adjust the sliders here.",
            10, false);
        selectHint.SetBounds(28, 455, 760, 40);
        steamvr.Controls.Add(selectHint);

        var modsTitle = MakeLabel("Gorilla Tag Mods / Plugins", 18, true);
        modsTitle.SetBounds(22, 22, 420, 36);
        mods.Controls.Add(modsTitle);

        gtagModSummary.SetBounds(24, 68, 760, 34);
        gtagModSummary.Font = new Font("Segoe UI", 11f, FontStyle.Bold);
        mods.Controls.Add(gtagModSummary);

        gtagMods.SetBounds(22, 116, 620, 410);
        gtagMods.BackColor = Color.FromArgb(20, 23, 30);
        gtagMods.ForeColor = Color.White;
        gtagMods.BorderStyle = BorderStyle.FixedSingle;
        mods.Controls.Add(gtagMods);

        refreshGtagMods.Text = "Rescan GTAG Mods";
        refreshGtagMods.SetBounds(662, 116, 170, 42);
        refreshGtagMods.Click += (_, _) => RefreshGtagMods();
        mods.Controls.Add(refreshGtagMods);

        var modsNote = MakeLabel(
            "QuestLink scans Gorilla Tag's BepInEx folder locally. It lists plugin DLLs and patcher DLLs; it does not modify them.",
            9.5f, false);
        modsNote.SetBounds(662, 176, 175, 150);
        modsNote.AutoSize = false;
        mods.Controls.Add(modsNote);

        UpdateScaleLabels();
        RefreshGtagMods();
    }

    private static Label MakeLabel(string text, float size, bool bold)
    {
        return new Label
        {
            Text = text,
            AutoSize = true,
            ForeColor = Color.White,
            Font = new Font("Segoe UI", size, bold ? FontStyle.Bold : FontStyle.Regular)
        };
    }

    private void UpdateScaleLabels()
    {
        worldScaleValue.Text = worldScale.Value + "%";
        renderScaleValue.Text = renderScale.Value + "%";
    }

    private void RefreshGtagMods()
    {
        var scan = GtagModScanner.Scan();

        gtagMods.DataSource = null;
        gtagMods.DataSource = scan.Entries;

        if (!scan.GtagFound)
        {
            gtagModSummary.Text = "Gorilla Tag not found.";
            gtagModSummary.ForeColor = Color.Orange;
            return;
        }

        if (!scan.BepInExInstalled)
        {
            gtagModSummary.Text = "Gorilla Tag found • BepInEx not detected";
            gtagModSummary.ForeColor = Color.Orange;
            return;
        }

        gtagModSummary.Text =
            $"BepInEx detected • {scan.PluginCount} plugin DLL(s) • {scan.PatcherCount} patcher DLL(s)";
        gtagModSummary.ForeColor = Color.LightGreen;
    }

    private void RefreshRuntimeStatus()
    {
        var openXr = QuestLinkRuntimeRegistry.IsQuestLinkActive();

        runtimeStatus.Text = openXr
            ? "ACTIVE — QuestLink OpenXR runtime ready for OpenComposite/GTAG."
            : "INACTIVE — install/activate the QuestLink runtime first.";

        runtimeStatus.ForeColor = openXr ? Color.LightGreen : Color.Orange;
        runtimePath.Text = "ActiveRuntime: " + QuestLinkRuntimeRegistry.GetActiveRuntime();
        launchGtagQuick.Enabled = openXr;
    }

    private async Task LaunchGorillaTagQuickAsync()
    {
        var list = await SteamVrLibrary.GetVrGamesAsync();
        var game = list.FirstOrDefault(g =>
            g.Name.Equals("Gorilla Tag", StringComparison.OrdinalIgnoreCase) ||
            g.AppId == "1533390");

        if (game is null)
        {
            MessageBox.Show("Gorilla Tag was not found in your Steam library.", "QuestLink");
            return;
        }

        status.Text = "Preparing OpenComposite and QuestLink...";
        var result = await Launcher.LaunchGameAsync(game);
        status.Text = result.Message;
        RefreshRuntimeStatus();

        if (!result.Ok)
            MessageBox.Show(result.Message, "QuestLink");
    }

    private async Task ReloadGamesAsync()
    {
        refreshGames.Enabled = false;
        status.Text = "Scanning installed PCVR games...";
        try
        {
            currentGames = await SteamVrLibrary.GetVrGamesAsync(forceRefresh: true);
            games.DataSource = null;
            games.DataSource = currentGames;
            status.Text = $"Ready — {currentGames.Count} PCVR game(s) found.";
        }
        catch (Exception ex)
        {
            status.Text = "Game scan failed: " + ex.Message;
        }
        finally
        {
            refreshGames.Enabled = true;
        }
    }

    private async Task LaunchSelectedAsync()
    {
        if (games.SelectedItem is not VrGame game)
        {
            MessageBox.Show("Select a PCVR game first.", "QuestLink");
            return;
        }

        var result = await Launcher.LaunchGameAsync(game);
        status.Text = result.Message;
    }

    private void ApplySteamVrSettings()
    {
        if (games.SelectedItem is not VrGame game)
        {
            MessageBox.Show("Select a PCVR game on the Library tab first.", "QuestLink");
            return;
        }

        if (!game.Source.Equals("Steam", StringComparison.OrdinalIgnoreCase))
        {
            MessageBox.Show("SteamVR per-game settings only apply to Steam/OpenVR games.", "QuestLink");
            return;
        }

        try
        {
            SteamVrSettings.WritePerApp(
                game.AppId,
                worldScale.Value / 100.0,
                renderScale.Value);

            status.Text = $"SteamVR settings saved for {game.Name}.";
            MessageBox.Show(
                $"Saved for {game.Name}\n\nWorld scale: {worldScale.Value}%\nRender scale: {renderScale.Value}%",
                "QuestLink");
        }
        catch (Exception ex)
        {
            MessageBox.Show("Could not update SteamVR settings:\n" + ex.Message, "QuestLink");
        }
    }

    private static void OpenSteamVr()
    {
        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = "steam://rungameid/250820",
                UseShellExecute = true
            });
        }
        catch { }
    }

    private void StartLanServer()
    {
        try
        {
            try { lanListener?.Stop(); } catch { }

            lanListener = new TcpListener(IPAddress.Any, Port);
            lanListener.Server.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
            lanListener.Start();

            lanStatus.Text = $"LAN server: LISTENING on 0.0.0.0:{Port}";
            lanStatus.ForeColor = Color.LightGreen;

            lanServerTask = Task.Run(() => AcceptLoopAsync(lanListener, cts.Token));
        }
        catch (Exception ex)
        {
            lanStatus.Text = $"LAN server FAILED: {ex.Message}";
            lanStatus.ForeColor = Color.OrangeRed;
            status.Text = "Quest cannot connect until the LAN server is listening.";
        }
    }

    private async Task AcceptLoopAsync(TcpListener listener, CancellationToken token)
    {
        try
        {
            while (!token.IsCancellationRequested)
            {
                TcpClient client;
                try
                {
                    client = await listener.AcceptTcpClientAsync(token);
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch (ObjectDisposedException)
                {
                    break;
                }

                _ = Task.Run(() => HandleClientAsync(client), token);
            }
        }
        catch (Exception ex)
        {
            if (!token.IsCancellationRequested && IsHandleCreated)
            {
                BeginInvoke(() =>
                {
                    lanStatus.Text = $"LAN server stopped: {ex.Message}";
                    lanStatus.ForeColor = Color.OrangeRed;
                });
            }
        }
    }

    private async Task HandleClientAsync(TcpClient client)
    {
        using (client)
        using (var stream = client.GetStream())
        using (var reader = new StreamReader(stream, Encoding.UTF8, false, 4096, leaveOpen: true))
        using (var writer = new StreamWriter(stream, new UTF8Encoding(false), 4096, leaveOpen: true) { AutoFlush = true })
        {
            try
            {
                var line = await reader.ReadLineAsync();
                if (string.IsNullOrWhiteSpace(line)) return;

                using var doc = JsonDocument.Parse(line);
                var root = doc.RootElement;
                var cmd = root.TryGetProperty("cmd", out var c) ? c.GetString() ?? "" : "";

                object response;

                if (cmd == "ping")
                {
                    response = new
                    {
                        ok = true,
                        app = "QuestLink PC",
                        version = Version,
                        pc = Environment.MachineName,
                        lan_port = Port,
                        server = "listening",
                        features = new[]
                        {
                            "vr_game_filter",
                            "list_games",
                            "launch_game",
                            "gtag_openvr_via_opencomposite",
                            "opencomposite_no_steamvr",
                            "questlink_runtime_detection",
                            "questlink_runtime_status",
                            "gtag_quick_launch",
                            "gtag_mod_scan",
                            "gtag_plugin_list",
                            "meta_pcvr_library",
                            "bepinex_detection",
                            "steamvr_world_scale",
                            "steamvr_render_scale",
                            "remote_steamvr_settings"
                        }
                    };
                }
                else if (cmd == "runtime_status")
                {
                    var openXr = QuestLinkRuntimeRegistry.IsQuestLinkActive();

                    response = new
                    {
                        ok = true,
                        active = openXr,
                        opencomposite = OpenCompositeManager.IsBundled(),
                        openxr_runtime = openXr,
                        active_runtime = QuestLinkRuntimeRegistry.GetActiveRuntime(),
                        preferred_gtag_mode = "openvr_opencomposite",
                        steamvr_required = false
                    };
                }
                else if (cmd == "gtag_mods")
                {
                    var scan = GtagModScanner.Scan();
                    response = new
                    {
                        ok = true,
                        gtag_found = scan.GtagFound,
                        bepinex = scan.BepInExInstalled,
                        plugin_count = scan.PluginCount,
                        patcher_count = scan.PatcherCount,
                        entries = scan.Entries
                    };
                }
                else if (cmd == "list_games")
                {
                    response = new
                    {
                        ok = true,
                        games = await SteamVrLibrary.GetVrGamesAsync()
                    };
                }
                else if (cmd == "launch_game")
                {
                    var appId = root.TryGetProperty("appid", out var id)
                        ? id.GetString() ?? id.ToString()
                        : "";

                    var list = await SteamVrLibrary.GetVrGamesAsync();
                    var game = list.FirstOrDefault(g => g.AppId == appId);

                    if (game is null)
                    {
                        response = new { ok = false, error = "Game not found in PCVR library." };
                    }
                    else
                    {
                        var launch = await Launcher.LaunchGameAsync(game);
                        response = launch.Ok
                            ? new { ok = true, message = launch.Message }
                            : new { ok = false, error = launch.Message };
                    }
                }
                else if (cmd == "set_steamvr_settings")
                {
                    var appId = root.TryGetProperty("appid", out var id)
                        ? id.GetString() ?? id.ToString()
                        : "";

                    var world = root.TryGetProperty("world_scale", out var ws) && ws.TryGetInt32(out var wsv)
                        ? Math.Clamp(wsv, 10, 1000)
                        : 100;

                    var render = root.TryGetProperty("render_scale", out var rs) && rs.TryGetInt32(out var rsv)
                        ? Math.Clamp(rsv, 20, 300)
                        : 100;

                    var list = await SteamVrLibrary.GetVrGamesAsync();
                    var game = list.FirstOrDefault(g => g.AppId == appId);

                    if (game is null)
                    {
                        response = new { ok = false, error = "Game not found." };
                    }
                    else if (!game.Source.Equals("Steam", StringComparison.OrdinalIgnoreCase))
                    {
                        response = new { ok = false, error = "SteamVR settings only apply to Steam/OpenVR games." };
                    }
                    else
                    {
                        SteamVrSettings.WritePerApp(appId, world / 100.0, render);
                        response = new
                        {
                            ok = true,
                            message = $"Saved SteamVR settings for {game.Name}.",
                            world_scale = world,
                            render_scale = render
                        };
                    }
                }
                else if (cmd == "open_steamvr")
                {
                    try
                    {
                        Process.Start(new ProcessStartInfo
                        {
                            FileName = "steam://rungameid/250820",
                            UseShellExecute = true
                        });
                        response = new { ok = true, message = "SteamVR launch requested." };
                    }
                    catch (Exception ex)
                    {
                        response = new { ok = false, error = ex.Message };
                    }
                }
                else
                {
                    response = new { ok = false, error = "Unsupported command." };
                }

                await writer.WriteLineAsync(JsonSerializer.Serialize(response));
            }
            catch (Exception ex)
            {
                try
                {
                    await writer.WriteLineAsync(JsonSerializer.Serialize(new { ok = false, error = ex.Message }));
                }
                catch { }
            }
        }
    }
}

public record VrGame(
    string AppId,
    string Name,
    string VrSupport,
    string Source = "Steam",
    string? LaunchPath = null,
    string? LaunchArgs = null,
    bool BepInExInstalled = false)
{
    public string DisplayName =>
        $"{Name}  •  {VrSupport}  •  {Source}" +
        (BepInExInstalled ? "  •  BepInEx ✓" : "");
}

public record LaunchResult(bool Ok, string Message);

public static class Launcher
{
    public static async Task<LaunchResult> LaunchGameAsync(VrGame game)
    {
        await Task.Yield();

        if (game.Source.Equals("Meta", StringComparison.OrdinalIgnoreCase))
        {
            StartMetaQuestLink();

            if (string.IsNullOrWhiteSpace(game.LaunchPath) || !File.Exists(game.LaunchPath))
                return new LaunchResult(false, "Meta PCVR launch file was not found.");

            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = game.LaunchPath,
                    Arguments = game.LaunchArgs ?? "",
                    WorkingDirectory = Path.GetDirectoryName(game.LaunchPath)!,
                    UseShellExecute = true
                });

                return new LaunchResult(true, $"Launching {game.Name} from the Meta PCVR library.");
            }
            catch (Exception ex)
            {
                return new LaunchResult(false, "Meta launch failed: " + ex.Message);
            }
        }

        if (game.Name.Equals("Gorilla Tag", StringComparison.OrdinalIgnoreCase))
        {
            if (!QuestLinkRuntimeRegistry.IsQuestLinkActive())
            {
                return new LaunchResult(
                    false,
                    "QuestLink is not the active OpenXR runtime. Install/activate the QuestLink runtime first.");
            }

            var exe = SteamVrLibrary.FindInstalledExe(game.AppId, "Gorilla Tag.exe");
            if (exe is null)
                return new LaunchResult(false, "Gorilla Tag.exe was not found in the Steam library.");

            var openComposite = OpenCompositeManager.InstallForGame(Path.GetDirectoryName(exe)!);
            if (!openComposite.Ok)
                return new LaunchResult(false, openComposite.Message);

            try
            {
                using var process = Process.Start(new ProcessStartInfo
                {
                    FileName = exe,
                    Arguments = "-vrmode openvr -force-d3d11",
                    WorkingDirectory = Path.GetDirectoryName(exe)!,
                    UseShellExecute = true
                });

                if (process is null)
                    return new LaunchResult(false, "Windows did not start Gorilla Tag.");

                // Do not send the headset into VR when the game immediately crashes.
                var exit = process.WaitForExitAsync();
                if (await Task.WhenAny(exit, Task.Delay(8000)) == exit || process.HasExited)
                    return new LaunchResult(false,
                        $"Gorilla Tag exited during startup (code {process.ExitCode}). " +
                        "Check Player.log in AppData/LocalLow/Another Axiom/Gorilla Tag and " +
                        "AppData/Local/OpenComposite/logs/opencomposite.log.");

                return new LaunchResult(
                    true,
                    "Gorilla Tag is running through OpenComposite. Connect the Quest client to test VR.");
            }
            catch (Exception ex)
            {
                return new LaunchResult(false, "QuestLink/OpenComposite launch failed: " + ex.Message);
            }
        }

        Process.Start(new ProcessStartInfo
        {
            FileName = $"steam://run/{game.AppId}",
            UseShellExecute = true
        });

        return new LaunchResult(true, $"Launching {game.Name} through Steam.");
    }

    private static void StartMetaQuestLink()
    {
        var candidates = new[]
        {
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Meta Horizon", "Support", "oculus-client", "OculusClient.exe"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Meta Horizon", "Support", "oculus-client", "OculusClient.exe"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Oculus", "Support", "oculus-client", "OculusClient.exe"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Oculus", "Support", "oculus-client", "OculusClient.exe")
        };

        foreach (var file in candidates)
        {
            if (!File.Exists(file)) continue;
            try { Process.Start(new ProcessStartInfo(file) { UseShellExecute = true }); } catch { }
            return;
        }
    }

    private static void StopSteamVr()
    {
        foreach (var name in new[] { "vrmonitor", "vrserver", "vrdashboard" })
        {
            foreach (var process in Process.GetProcessesByName(name))
            {
                try { process.Kill(entireProcessTree: true); } catch { }
            }
        }
    }
}

public record GtagModScan(
    bool GtagFound,
    bool BepInExInstalled,
    int PluginCount,
    int PatcherCount,
    List<string> Entries);

public static class GtagModScanner
{
    public static GtagModScan Scan()
    {
        var root = SteamVrLibrary.GetInstallDirectory("1533390");
        if (string.IsNullOrWhiteSpace(root) || !Directory.Exists(root))
            return new GtagModScan(false, false, 0, 0, new List<string>());

        var bepinex = Path.Combine(root, "BepInEx");
        var installed = Directory.Exists(bepinex) &&
                        (Directory.Exists(Path.Combine(bepinex, "plugins")) ||
                         File.Exists(Path.Combine(root, "winhttp.dll")));

        if (!installed)
            return new GtagModScan(true, false, 0, 0, new List<string>());

        var entries = new List<string>();
        int plugins = 0;
        int patchers = 0;

        ScanDllFolder(Path.Combine(bepinex, "plugins"), "Plugin", entries, ref plugins);
        ScanDllFolder(Path.Combine(bepinex, "patchers"), "Patcher", entries, ref patchers);

        if (entries.Count == 0)
            entries.Add("BepInEx is installed, but no plugin/patcher DLLs were found.");

        return new GtagModScan(true, true, plugins, patchers, entries);
    }

    private static void ScanDllFolder(
        string folder,
        string kind,
        List<string> entries,
        ref int count)
    {
        if (!Directory.Exists(folder))
            return;

        try
        {
            foreach (var file in Directory.EnumerateFiles(folder, "*.dll", SearchOption.AllDirectories)
                                          .OrderBy(Path.GetFileName, StringComparer.OrdinalIgnoreCase))
            {
                count++;
                var relative = Path.GetRelativePath(folder, file);
                entries.Add($"{kind}: {relative}");
            }

            foreach (var file in Directory.EnumerateFiles(folder, "*.disabled", SearchOption.AllDirectories)
                                          .OrderBy(Path.GetFileName, StringComparer.OrdinalIgnoreCase))
            {
                var relative = Path.GetRelativePath(folder, file);
                entries.Add($"Disabled: {relative}");
            }
        }
        catch (Exception ex)
        {
            entries.Add($"{kind} scan error: {ex.Message}");
        }
    }
}

public record OpenCompositeInstallResult(bool Ok, string Message);

public static class OpenCompositeManager
{
    private static string BundledDll =>
        Path.Combine(AppContext.BaseDirectory, "opencomposite", "openvr_api.dll");

    private static string BundledBackend =>
        Path.Combine(AppContext.BaseDirectory, "opencomposite", "opencomposite_backend.dll");

    public static bool IsBundled() => File.Exists(BundledDll) && File.Exists(BundledBackend);

    public static OpenCompositeInstallResult InstallForGame(string gameRoot)
    {
        if (!IsBundled())
            return new OpenCompositeInstallResult(
                false,
                "The OpenComposite compatibility files are missing. Extract the entire QuestLink PC v7.12-dev ZIP, including its opencomposite folder.");

        string? target = null;
        try
        {
            target = Directory.EnumerateFiles(
                    gameRoot,
                    "openvr_api.dll",
                    SearchOption.AllDirectories)
                .OrderBy(p => p.Length)
                .FirstOrDefault(p =>
                    p.Contains("Plugins", StringComparison.OrdinalIgnoreCase) ||
                    p.Contains("x86_64", StringComparison.OrdinalIgnoreCase));

            target ??= Directory.EnumerateFiles(
                    gameRoot,
                    "openvr_api.dll",
                    SearchOption.AllDirectories)
                .OrderBy(p => p.Length)
                .FirstOrDefault();
        }
        catch (Exception ex)
        {
            return new OpenCompositeInstallResult(false, "Could not scan Gorilla Tag for openvr_api.dll: " + ex.Message);
        }

        if (string.IsNullOrWhiteSpace(target))
        {
            return new OpenCompositeInstallResult(
                false,
                "Gorilla Tag's openvr_api.dll was not found, so QuestLink could not install the no-SteamVR bridge.");
        }

        try
        {
            var backup = target + ".questlink-original";
            if (!File.Exists(backup))
                File.Copy(target, backup, overwrite: false);

            // Stage the backend before the forwarding DLL so every export can resolve.
            File.Copy(BundledBackend,
                Path.Combine(Path.GetDirectoryName(target)!, "opencomposite_backend.dll"), overwrite: true);
            File.Copy(BundledDll, target, overwrite: true);

            var ini = Path.Combine(Path.GetDirectoryName(target)!, "opencomposite.ini");
            File.WriteAllText(
                ini,
                "initUsingVulkan=false" + Environment.NewLine +
                "admitUnknownProps=true" + Environment.NewLine);

            return new OpenCompositeInstallResult(
                true,
                "OpenComposite installed for Gorilla Tag.");
        }
        catch (Exception ex)
        {
            return new OpenCompositeInstallResult(false, "Could not install OpenComposite for Gorilla Tag: " + ex.Message);
        }
    }
}

public static class QuestLinkRuntimeRegistry
{
    public static bool IsQuestLinkActive()
    {
        try
        {
            using var key = Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Khronos\OpenXR\1");
            var path = key?.GetValue("ActiveRuntime") as string;
            return !string.IsNullOrWhiteSpace(path) &&
                   path.EndsWith("questlink_runtime.json", StringComparison.OrdinalIgnoreCase) &&
                   File.Exists(path);
        }
        catch
        {
            return false;
        }
    }

    public static string GetActiveRuntime()
    {
        try
        {
            using var key = Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Khronos\OpenXR\1");
            return key?.GetValue("ActiveRuntime") as string ?? "Not set";
        }
        catch
        {
            return "Unavailable";
        }
    }
}

public static class SteamVrLibrary
{
    private static readonly HttpClient Http = new()
    {
        Timeout = TimeSpan.FromSeconds(8)
    };

    private static readonly SemaphoreSlim ScanLock = new(1, 1);
    private static List<VrGame>? cached;
    private static DateTime cacheTime;

    private static readonly HashSet<string> KnownVrApps = new(StringComparer.Ordinal)
    {
        "438100",  // VRChat
        "1533390", // Gorilla Tag
        "250820"   // SteamVR
    };

    public static async Task<List<VrGame>> GetVrGamesAsync(bool forceRefresh = false)
    {
        if (!forceRefresh && cached is not null && DateTime.UtcNow - cacheTime < TimeSpan.FromMinutes(5))
            return cached;

        await ScanLock.WaitAsync();
        try
        {
            var installed = ReadInstalledApps();
            var results = new List<VrGame>();

            foreach (var app in installed)
            {
                var support = await GetVrSupportAsync(app.AppId);

                if (KnownVrApps.Contains(app.AppId))
                    support ??= "PCVR";

                if (!string.IsNullOrWhiteSpace(support))
                {
                    var installDir = GetInstallDirectory(app.AppId);
                    var bepinex = installDir is not null && IsBepInExInstalled(installDir);
                    results.Add(new VrGame(app.AppId, app.Name, support, "Steam", null, null, bepinex));
                }
            }

            results.AddRange(MetaPcLibrary.GetInstalledGames());

            cached = results
                .GroupBy(g => g.AppId, StringComparer.OrdinalIgnoreCase)
                .Select(g => g.First())
                .OrderBy(g => g.Name, StringComparer.OrdinalIgnoreCase)
                .ToList();
            cacheTime = DateTime.UtcNow;
            return cached;
        }
        finally
        {
            ScanLock.Release();
        }
    }

    public static string? GetInstallDirectory(string appId)
    {
        foreach (var lib in SteamLibraries())
        {
            var manifest = Path.Combine(lib, "steamapps", $"appmanifest_{appId}.acf");
            if (!File.Exists(manifest)) continue;

            try
            {
                var text = File.ReadAllText(manifest);
                var installDir = MatchValue(text, "installdir");
                if (string.IsNullOrWhiteSpace(installDir)) continue;

                var candidate = Path.Combine(lib, "steamapps", "common", installDir);
                if (Directory.Exists(candidate))
                    return candidate;
            }
            catch { }
        }

        return null;
    }

    public static bool IsBepInExInstalled(string installDir)
    {
        var bepin = Path.Combine(installDir, "BepInEx");
        return Directory.Exists(bepin) &&
               (Directory.Exists(Path.Combine(bepin, "plugins")) ||
                File.Exists(Path.Combine(installDir, "winhttp.dll")));
    }

    public static string? FindInstalledExe(string appId, string exeName)
    {
        foreach (var lib in SteamLibraries())
        {
            var manifest = Path.Combine(lib, "steamapps", $"appmanifest_{appId}.acf");
            if (!File.Exists(manifest)) continue;

            var text = File.ReadAllText(manifest);
            var installDir = MatchValue(text, "installdir");
            if (string.IsNullOrWhiteSpace(installDir)) continue;

            var candidate = Path.Combine(lib, "steamapps", "common", installDir, exeName);
            if (File.Exists(candidate))
                return candidate;
        }
        return null;
    }

    private static List<(string AppId, string Name)> ReadInstalledApps()
    {
        var apps = new Dictionary<string, string>();

        foreach (var lib in SteamLibraries())
        {
            var steamApps = Path.Combine(lib, "steamapps");
            if (!Directory.Exists(steamApps)) continue;

            foreach (var manifest in Directory.EnumerateFiles(steamApps, "appmanifest_*.acf"))
            {
                try
                {
                    var text = File.ReadAllText(manifest);
                    var id = MatchValue(text, "appid");
                    var name = MatchValue(text, "name");

                    if (!string.IsNullOrWhiteSpace(id) && !string.IsNullOrWhiteSpace(name))
                        apps[id] = name;
                }
                catch { }
            }
        }

        return apps.Select(x => (x.Key, x.Value)).ToList();
    }

    private static IEnumerable<string> SteamLibraries()
    {
        var roots = new List<string>();

        string? steam = null;
        try
        {
            steam = Registry.CurrentUser.OpenSubKey(@"Software\Valve\Steam")?.GetValue("SteamPath") as string;
        }
        catch { }

        if (string.IsNullOrWhiteSpace(steam))
            steam = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Steam");

        if (!Directory.Exists(steam))
            yield break;

        roots.Add(steam);
        var vdf = Path.Combine(steam, "steamapps", "libraryfolders.vdf");

        if (File.Exists(vdf))
        {
            var text = File.ReadAllText(vdf);
            foreach (Match m in Regex.Matches(text, @"""path""\s*""([^""]+)"""))
            {
                var path = m.Groups[1].Value.Replace(@"\\", @"\");
                if (Directory.Exists(path) && !roots.Contains(path, StringComparer.OrdinalIgnoreCase))
                    roots.Add(path);
            }
        }

        foreach (var root in roots)
            yield return root;
    }

    private static string MatchValue(string acf, string key)
    {
        var m = Regex.Match(acf, $@"""{Regex.Escape(key)}""\s*""([^""]*)""", RegexOptions.IgnoreCase);
        return m.Success ? m.Groups[1].Value : "";
    }

    private static async Task<string?> GetVrSupportAsync(string appId)
    {
        try
        {
            var url = $"https://store.steampowered.com/api/appdetails?appids={Uri.EscapeDataString(appId)}&l=english";
            using var response = await Http.GetAsync(url);
            if (!response.IsSuccessStatusCode) return null;

            using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
            if (!doc.RootElement.TryGetProperty(appId, out var wrapper)) return null;
            if (!wrapper.TryGetProperty("success", out var success) || !success.GetBoolean()) return null;
            if (!wrapper.TryGetProperty("data", out var data)) return null;

            if (data.TryGetProperty("categories", out var categories) && categories.ValueKind == JsonValueKind.Array)
            {
                foreach (var category in categories.EnumerateArray())
                {
                    var description = category.TryGetProperty("description", out var d) ? d.GetString() : null;
                    if (string.IsNullOrWhiteSpace(description)) continue;

                    if (description.Contains("VR", StringComparison.OrdinalIgnoreCase))
                        return description;

                    if (description.Contains("Tracked Controller", StringComparison.OrdinalIgnoreCase))
                        return "PCVR / Tracked Controllers";
                }
            }
        }
        catch { }

        return null;
    }
}

public static class MetaPcLibrary
{
    public static List<VrGame> GetInstalledGames()
    {
        var results = new List<VrGame>();

        foreach (var softwareRoot in CandidateSoftwareRoots())
        {
            var manifests = Path.Combine(softwareRoot, "Manifests");
            var software = Path.Combine(softwareRoot, "Software");

            if (!Directory.Exists(manifests))
                continue;

            foreach (var manifest in Directory.EnumerateFiles(manifests, "*.json"))
            {
                try
                {
                    using var doc = JsonDocument.Parse(File.ReadAllText(manifest));
                    var root = doc.RootElement;

                    var canonical = ReadString(root, "canonicalName", "canonical_name", "packageName", "package_name");
                    var appId = ReadString(root, "appId", "app_id", "id");
                    var name = ReadString(root, "displayName", "display_name", "title", "name");
                    var launchFile = ReadString(root, "launchFile", "launch_file", "executable", "launchExecutable");
                    var launchArgs = ReadString(root, "launchParameters", "launch_parameters", "arguments", "launchArgs");

                    if (string.IsNullOrWhiteSpace(canonical) && string.IsNullOrWhiteSpace(appId))
                        continue;

                    var installDir = !string.IsNullOrWhiteSpace(canonical)
                        ? Path.Combine(software, canonical)
                        : software;

                    var launchPath = ResolveLaunchPath(installDir, launchFile);
                    if (launchPath is null)
                        continue;

                    if (string.IsNullOrWhiteSpace(name))
                        name = !string.IsNullOrWhiteSpace(canonical)
                            ? canonical.Replace('-', ' ')
                            : Path.GetFileNameWithoutExtension(launchPath);

                    var key = "meta:" + (!string.IsNullOrWhiteSpace(appId) ? appId : canonical);
                    var bepinex = SteamVrLibrary.IsBepInExInstalled(Path.GetDirectoryName(launchPath)!);

                    results.Add(new VrGame(
                        key,
                        name,
                        "Meta PCVR",
                        "Meta",
                        launchPath,
                        launchArgs,
                        bepinex));
                }
                catch { }
            }
        }

        return results
            .GroupBy(g => g.AppId, StringComparer.OrdinalIgnoreCase)
            .Select(g => g.First())
            .ToList();
    }

    private static IEnumerable<string> CandidateSoftwareRoots()
    {
        var roots = new[]
        {
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Oculus", "Software"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Oculus", "Software"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Meta Horizon", "Software"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Meta Horizon", "Software")
        };

        return roots.Where(Directory.Exists).Distinct(StringComparer.OrdinalIgnoreCase);
    }

    private static string? ResolveLaunchPath(string installDir, string? launchFile)
    {
        if (!string.IsNullOrWhiteSpace(launchFile))
        {
            var direct = Path.IsPathRooted(launchFile)
                ? launchFile
                : Path.Combine(installDir, launchFile);

            if (File.Exists(direct))
                return direct;
        }

        if (!Directory.Exists(installDir))
            return null;

        try
        {
            return Directory.EnumerateFiles(installDir, "*.exe", SearchOption.AllDirectories)
                .FirstOrDefault(p =>
                {
                    var n = Path.GetFileName(p);
                    return !n.Contains("crash", StringComparison.OrdinalIgnoreCase) &&
                           !n.Contains("unins", StringComparison.OrdinalIgnoreCase) &&
                           !n.Contains("setup", StringComparison.OrdinalIgnoreCase);
                });
        }
        catch
        {
            return null;
        }
    }

    private static string? ReadString(JsonElement obj, params string[] names)
    {
        if (obj.ValueKind != JsonValueKind.Object)
            return null;

        foreach (var prop in obj.EnumerateObject())
        {
            if (!names.Any(n => prop.Name.Equals(n, StringComparison.OrdinalIgnoreCase)))
                continue;

            if (prop.Value.ValueKind == JsonValueKind.String)
                return prop.Value.GetString();

            if (prop.Value.ValueKind == JsonValueKind.Number)
                return prop.Value.ToString();
        }

        return null;
    }
}

public static class SteamVrSettings
{
    public static void WritePerApp(string appId, double worldScale, int resolutionScale)
    {
        var path = GetSettingsPath();
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);

        JsonObject root;

        if (File.Exists(path))
        {
            var text = File.ReadAllText(path);
            root = JsonNode.Parse(text)?.AsObject() ?? new JsonObject();
        }
        else
        {
            root = new JsonObject();
        }

        var sectionName = "steam.app." + appId;
        var section = root[sectionName] as JsonObject ?? new JsonObject();

        section["worldScale"] = Math.Round(worldScale, 2);
        section["resolutionScale"] = resolutionScale;

        root[sectionName] = section;

        var options = new JsonSerializerOptions { WriteIndented = true };
        File.WriteAllText(path, root.ToJsonString(options));
    }

    private static string GetSettingsPath()
    {
        var steam = Registry.CurrentUser.OpenSubKey(@"Software\Valve\Steam")?.GetValue("SteamPath") as string;

        if (string.IsNullOrWhiteSpace(steam))
            steam = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Steam");

        return Path.Combine(steam!, "config", "steamvr.vrsettings");
    }
}

public static class NetworkHelpers
{
    public static string GetLanIp()
    {
        try
        {
            using var socket = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
            socket.Connect("8.8.8.8", 80);
            return ((IPEndPoint)socket.LocalEndPoint!).Address.ToString();
        }
        catch
        {
            return Dns.GetHostEntry(Dns.GetHostName())
                .AddressList.FirstOrDefault(a => a.AddressFamily == AddressFamily.InterNetwork)?.ToString()
                ?? "127.0.0.1";
        }
    }
}
