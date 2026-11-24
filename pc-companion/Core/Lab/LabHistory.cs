using System.Text.Json;
using System.Text.Json.Nodes;

namespace Mobilepad.Companion.Lab;

/// <summary>One line of the history: a run folder with its meta and headline numbers.</summary>
public sealed class LabRunEntry
{
    public string Id { get; set; } = "";
    public string Folder { get; set; } = "";
    public DateTime When { get; set; }
    public string Test { get; set; } = "";
    public string Link { get; set; } = "";
    public string Status { get; set; } = "";
    public string AppVersion { get; set; } = "";
    public string AppCommit { get; set; } = "";
    public string Device { get; set; } = "";
    public double SyncUncertaintyMs { get; set; }
    public Headline? Headline { get; set; }
}

public sealed record CompareRow(string Metric, string Kind, double? A, double? B)
{
    public double? Delta => A is { } a && B is { } b ? b - a : null;
    public double? DeltaPct => A is { } a && B is { } b && Math.Abs(a) > 1e-9 ? (b - a) / Math.Abs(a) * 100 : null;
}

/// <summary>Run folders under %LOCALAPPDATA%\Mobilepad\lab\runs, the index.json next to them, and run comparison.</summary>
public static class LabHistory
{
    public static string Root { get; set; } =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Mobilepad", "lab");

    public static string RunsDir => Path.Combine(Root, "runs");
    public static string IndexPath => Path.Combine(Root, "index.json");

    /// <summary>Scans every run folder (newest first) and rewrites index.json.</summary>
    public static List<LabRunEntry> Load()
    {
        var entries = new List<LabRunEntry>();
        if (Directory.Exists(RunsDir))
        {
            foreach (var dir in Directory.GetDirectories(RunsDir))
            {
                try
                {
                    if (ReadEntry(dir) is { } e) entries.Add(e);
                }
                catch (Exception ex) when (ex is IOException or JsonException or UnauthorizedAccessException) { }
            }
        }
        entries.Sort((a, b) => string.CompareOrdinal(b.Id, a.Id));
        try
        {
            Directory.CreateDirectory(Root);
            File.WriteAllText(IndexPath, LabJson.Serialize(entries));
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { }
        return entries;
    }

    public static LabRunEntry? ReadEntry(string folder)
    {
        var metaPath = Path.Combine(folder, "meta.json");
        if (!File.Exists(metaPath)) return null;
        var meta = JsonNode.Parse(File.ReadAllText(metaPath)) as JsonObject;
        if (meta is null) return null;
        var entry = new LabRunEntry
        {
            Id = Str(meta["run_id"]) ?? Path.GetFileName(folder),
            Folder = folder,
            Test = Str(meta["test"]) ?? "",
            Link = Str(meta["link"]) ?? "",
            Status = Str(meta["status"]) ?? "",
            AppVersion = Str(meta["app"]?["version"]) ?? "",
            AppCommit = Str(meta["app"]?["commit"]) ?? "",
            Device = Str(meta["phone"]?["model"]) ?? "",
            SyncUncertaintyMs = meta["sync"]?["uncertainty_ns"] is JsonValue u && u.TryGetValue<long>(out var ns) ? ns / 1e6 : 0,
        };
        if (meta["times"]?["started_ns"] is JsonValue sv && sv.TryGetValue<long>(out var started))
            entry.When = LabTime.ToUtc(started).ToLocalTime();
        else
            entry.When = Directory.GetCreationTime(folder);
        entry.Headline = LoadSummary(folder)?.Headline;
        return entry;
    }

    public static LabSummary? LoadSummary(string folder)
    {
        var path = Path.Combine(folder, "summary.json");
        if (!File.Exists(path)) return null;
        try { return LabJson.Deserialize<LabSummary>(File.ReadAllText(path)); }
        catch (JsonException) { return null; }
    }

    private static string? Str(JsonNode? node) =>
        node is JsonValue v ? (v.TryGetValue<string>(out var s) ? s : v.ToJsonString().Trim('"')) : null;

    /// <summary>Per-stage percentiles and the loss/rate figures of two runs, side by side (B − A).</summary>
    public static List<CompareRow> Compare(LabSummary a, LabSummary b)
    {
        var rows = new List<CompareRow>();
        var names = a.Stages.Select(s => s.Name).Union(b.Stages.Select(s => s.Name))
            .OrderBy(n => Array.FindIndex(LabAnalysis.Pairs, p => LabAnalysis.PairName(p.From, p.To) == n));
        foreach (var name in names)
        {
            var sa = a.Stage(name);
            var sb = b.Stage(name);
            var kind = (sa ?? sb)!.Kind;
            rows.Add(new CompareRow($"{name} P50 (ms)", kind, sa?.P50Ms, sb?.P50Ms));
            rows.Add(new CompareRow($"{name} P95 (ms)", kind, sa?.P95Ms, sb?.P95Ms));
            rows.Add(new CompareRow($"{name} P99 (ms)", kind, sa?.P99Ms, sb?.P99Ms));
            rows.Add(new CompareRow($"{name} mean (ms)", kind, sa?.MeanMs, sb?.MeanMs));
            rows.Add(new CompareRow($"{name} stdev (ms)", kind, sa?.StdevMs, sb?.StdevMs));
        }
        rows.Add(new CompareRow("lost (%)", "measured", a.Loss.LostPct, b.Loss.LostPct));
        rows.Add(new CompareRow("duplicates (recv)", "measured", a.Loss.DuplicatesRecv, b.Loss.DuplicatesRecv));
        rows.Add(new CompareRow("out of order", "measured", a.Loss.OutOfOrder, b.Loss.OutOfOrder));
        rows.Add(new CompareRow("recv rate (Hz)", "measured", a.Rates.RecvHz, b.Rates.RecvHz));
        rows.Add(new CompareRow("observer poll (Hz)", "measured", a.Rates.ObserverPollHz, b.Rates.ObserverPollHz));
        rows.Add(new CompareRow("intact at recv (%)", "measured", a.Integrity.ComparedRecv > 0 ? a.Integrity.IntactRecvPct : null,
            b.Integrity.ComparedRecv > 0 ? b.Integrity.IntactRecvPct : null));
        rows.Add(new CompareRow("sync uncertainty (ms)", "estimated", a.Sync.UncertaintyMs, b.Sync.UncertaintyMs));
        return rows;
    }
}
