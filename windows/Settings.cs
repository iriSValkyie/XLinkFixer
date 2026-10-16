using System.Text.Json;
using System.Text.Json.Serialization;

namespace XLinkFixer;

public sealed class Settings
{
    public bool Enabled { get; set; } = true;
    public string Target { get; set; } = "fixupx.com";
    public bool ShowNotification { get; set; } = true;

    [JsonIgnore]
    public static string Path => System.IO.Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "XLinkFixer", "settings.json");

    private static readonly JsonSerializerOptions Options = new() { WriteIndented = true };

    public static Settings Load()
    {
        try
        {
            if (File.Exists(Path))
            {
                var s = JsonSerializer.Deserialize<Settings>(File.ReadAllText(Path), Options);
                if (s != null)
                {
                    if (!Rewriter.Targets.Contains(s.Target)) s.Target = Rewriter.Targets[0];
                    return s;
                }
            }
        }
        catch { /* 壊れていたら既定値 */ }
        return new Settings();
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(System.IO.Path.GetDirectoryName(Path)!);
            File.WriteAllText(Path, JsonSerializer.Serialize(this, Options));
        }
        catch { }
    }
}
