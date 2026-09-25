using Microsoft.Win32;
using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

const int Port = 47990;
const string Version = "7.1";

Console.Title = "QuestLink PC";
Console.WriteLine("==============================================");
Console.WriteLine(" QuestLink PC V" + Version + " - Native Windows");
Console.WriteLine("==============================================");
Console.WriteLine("PC IP: " + GetLanIp());
Console.WriteLine("Port : " + Port);
Console.WriteLine();
Console.WriteLine("Enter this IP in the QuestLink headset app.");
Console.WriteLine("Only VR Only / VR Supported Steam games are shown.");
Console.WriteLine("No Python is used.");
Console.WriteLine();

var listener = new TcpListener(IPAddress.Any, Port);
listener.Start();

while (true)
{
    var client = await listener.AcceptTcpClientAsync();
    _ = Task.Run(() => HandleClient(client));
}

static async Task HandleClient(TcpClient client)
{
    using (client)
    using (var stream = client.GetStream())
    using (var reader = new StreamReader(stream, Encoding.UTF8, false, 4096, leaveOpen: true))
    using (var writer = new StreamWriter(stream, new UTF8Encoding(false), 4096, leaveOpen: true) { AutoFlush = true })
    {
        try
        {
            client.ReceiveTimeout = 10000;
            client.SendTimeout = 10000;

            var line = await reader.ReadLineAsync();
            if (string.IsNullOrWhiteSpace(line))
                return;

            using var doc = JsonDocument.Parse(line);
            var root = doc.RootElement;
            var cmd = root.TryGetProperty("cmd", out var c) ? c.GetString() ?? "" : "";

            object response = cmd switch
            {
                "ping" => new
                {
                    ok = true,
                    app = "QuestLink PC",
                    version = Version,
                    pc = Environment.MachineName,
                    features = new[] { "vr_game_filter", "list_games", "launch_game", "gtag_oculus" }
                },

                "list_games" => new
                {
                    ok = true,
                    games = await SteamVrLibrary.GetVrGamesAsync()
                },

                "launch_game" => await LaunchRequestedGame(root),

                _ => new { ok = false, error = "Unsupported command." }
            };

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

static async Task<object> LaunchRequestedGame(JsonElement root)
{
    if (!root.TryGetProperty("appid", out var appIdElement))
        return new { ok = false, error = "Missing appid." };

    var appId = appIdElement.GetString() ?? appIdElement.ToString();
    var games = await SteamVrLibrary.GetVrGamesAsync();
    var game = games.FirstOrDefault(x => x.AppId == appId);

    if (game is null)
        return new { ok = false, error = "That game is not in the verified PCVR list." };

    if (game.Name.Equals("Gorilla Tag", StringComparison.OrdinalIgnoreCase))
    {
        var exe = SteamVrLibrary.FindInstalledExe(game.AppId, "Gorilla Tag.exe");
        if (exe is not null)
        {
            TryStartMetaQuestLink();
            StopSteamVr();
            Process.Start(new ProcessStartInfo
            {
                FileName = exe,
                Arguments = "-vrmode oculus",
                WorkingDirectory = Path.GetDirectoryName(exe)!,
                UseShellExecute = true
            });
            return new { ok = true, message = "Gorilla Tag launched in Oculus / Quest Link mode." };
        }
    }

    Process.Start(new ProcessStartInfo
    {
        FileName = $"steam://run/{game.AppId}",
        UseShellExecute = true
    });

    return new { ok = true, message = $"Launching {game.Name} through Steam." };
}

static void TryStartMetaQuestLink()
{
    var candidates = new[]
    {
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

static void StopSteamVr()
{
    foreach (var name in new[] { "vrmonitor", "vrserver", "vrdashboard" })
    {
        foreach (var process in Process.GetProcessesByName(name))
        {
            try { process.Kill(entireProcessTree: true); } catch { }
        }
    }
}

static string GetLanIp()
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

record VrGame(string AppId, string Name, string VrSupport);

static class SteamVrLibrary
{
    private static readonly HttpClient Http = new()
    {
        Timeout = TimeSpan.FromSeconds(8)
    };

    private static readonly SemaphoreSlim ScanLock = new(1, 1);
    private static List<VrGame>? _cachedGames;
    private static DateTime _cacheTime;

    public static async Task<List<VrGame>> GetVrGamesAsync()
    {
        if (_cachedGames is not null && DateTime.UtcNow - _cacheTime < TimeSpan.FromMinutes(10))
            return _cachedGames;

        await ScanLock.WaitAsync();
        try
        {
            if (_cachedGames is not null && DateTime.UtcNow - _cacheTime < TimeSpan.FromMinutes(10))
                return _cachedGames;

            var installed = ReadInstalledApps();
            var results = new List<VrGame>();

            foreach (var app in installed)
            {
                var support = await GetVrSupportAsync(app.AppId);
                if (support is "VR Only" or "VR Supported")
                    results.Add(new VrGame(app.AppId, app.Name, support));
            }

            _cachedGames = results.OrderBy(g => g.Name, StringComparer.OrdinalIgnoreCase).ToList();
            _cacheTime = DateTime.UtcNow;
            return _cachedGames;
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
            foreach (Match m in Regex.Matches(text, "\"path\"\s*\"([^\"]+)\""))
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
        var m = Regex.Match(acf, $"\"{Regex.Escape(key)}\"\s*\"([^\"]*)\"", RegexOptions.IgnoreCase);
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
            if (!data.TryGetProperty("categories", out var categories) || categories.ValueKind != JsonValueKind.Array)
                return null;

            foreach (var category in categories.EnumerateArray())
            {
                var description = category.TryGetProperty("description", out var d) ? d.GetString() : null;
                if (description is "VR Only" or "VR Supported")
                    return description;
            }
        }
        catch { }

        return null;
    }
}
