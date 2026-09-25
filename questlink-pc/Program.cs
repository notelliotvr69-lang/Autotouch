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
    private const string Version = "7.5";

    private readonly Label status = new();
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

    private readonly CancellationTokenSource cts = new();
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
            _ = Task.Run(() => RunServerAsync(cts.Token));
            await ReloadGamesAsync();
        };
        FormClosing += (_, _) => cts.Cancel();
    }

    private void BuildUi()
    {
        var tabs = new TabControl { Dock = DockStyle.Fill };

        var home = new TabPage("Connection") { BackColor = BackColor, ForeColor = ForeColor };
        var library = new TabPage("PCVR Library") { BackColor = BackColor, ForeColor = ForeColor };
        var steamvr = new TabPage("SteamVR Settings") { BackColor = BackColor, ForeColor = ForeColor };

        tabs.TabPages.Add(home);
        tabs.TabPages.Add(library);
        tabs.TabPages.Add(steamvr);
        Controls.Add(tabs);

        var title = MakeLabel("QuestLink PC V" + Version, 24, true);
        title.SetBounds(24, 24, 500, 40);
        home.Controls.Add(title);

        ipLabel.Text = "PC IP: " + NetworkHelpers.GetLanIp() + ":" + Port;
        ipLabel.SetBounds(28, 82, 700, 32);
        ipLabel.Font = new Font("Segoe UI", 14f, FontStyle.Bold);
        home.Controls.Add(ipLabel);

        status.Text = "QuestLink server starting...";
        status.SetBounds(28, 126, 700, 30);
        status.ForeColor = Color.LightGray;
        home.Controls.Add(status);

        var note = MakeLabel(
            "Enter the PC IP shown above into the Quest app. Pairing is LAN-only and does not install anything over IP.",
            11, false);
        note.SetBounds(28, 174, 790, 70);
        note.AutoSize = false;
        home.Controls.Add(note);

        var native = MakeLabel(
            "Native Windows app — no Python required.",
            11, true);
        native.SetBounds(28, 250, 700, 30);
        home.Controls.Add(native);

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
            "QuestLink scans installed Steam titles and keeps games with Steam VR metadata. Gorilla Tag and VRChat also have explicit installed-app fallbacks so they are not hidden if Steam metadata is incomplete.",
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

        UpdateScaleLabels();
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

    private async Task RunServerAsync(CancellationToken token)
    {
        var listener = new TcpListener(IPAddress.Any, Port);
        listener.Start();

        BeginInvoke(() => status.Text = "QuestLink server ready.");

        try
        {
            while (!token.IsCancellationRequested)
            {
                var client = await listener.AcceptTcpClientAsync(token);
                _ = Task.Run(() => HandleClientAsync(client), token);
            }
        }
        catch (OperationCanceledException) { }
        finally
        {
            listener.Stop();
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
                        features = new[]
                        {
                            "vr_game_filter",
                            "list_games",
                            "launch_game",
                            "gtag_oculus",
                            "steamvr_world_scale",
                            "steamvr_render_scale"
                        }
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

public record VrGame(string AppId, string Name, string VrSupport)
{
    public string DisplayName => $"{Name}  •  {VrSupport}";
}

public record LaunchResult(bool Ok, string Message);

public static class Launcher
{
    public static async Task<LaunchResult> LaunchGameAsync(VrGame game)
    {
        await Task.Yield();

        if (game.Name.Equals("Gorilla Tag", StringComparison.OrdinalIgnoreCase))
        {
            StartMetaQuestLink();
            _ = Task.Run(() => DelayedQuestLinkGorillaTagLaunchAsync(game));

            return new LaunchResult(
                true,
                "Meta Horizon Link opened. Enter Quest Link now — Gorilla Tag will launch automatically in about 20 seconds.");
        }

        Process.Start(new ProcessStartInfo
        {
            FileName = $"steam://run/{game.AppId}",
            UseShellExecute = true
        });

        return new LaunchResult(true, $"Launching {game.Name} through Steam.");
    }

    private static async Task DelayedQuestLinkGorillaTagLaunchAsync(VrGame game)
    {
        // Meta background processes are not a reliable indicator that the headset
        // has actually entered Quest Link. Give the user a predictable window to
        // enter Link, then launch GTAG in the Meta/Oculus runtime directly.
        await Task.Delay(TimeSpan.FromSeconds(20));

        var exe = SteamVrLibrary.FindInstalledExe(game.AppId, "Gorilla Tag.exe");
        if (exe is null)
            return;

        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = exe,
                Arguments = "-vrmode oculus",
                WorkingDirectory = Path.GetDirectoryName(exe)!,
                UseShellExecute = true
            });
        }
        catch { }
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
                    results.Add(new VrGame(app.AppId, app.Name, support));
            }

            cached = results.OrderBy(g => g.Name, StringComparer.OrdinalIgnoreCase).ToList();
            cacheTime = DateTime.UtcNow;
            return cached;
        }
        finally
        {
            ScanLock.Release();
        }
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
