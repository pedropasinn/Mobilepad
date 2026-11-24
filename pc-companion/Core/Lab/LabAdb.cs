using System.Diagnostics;
using System.Text;

namespace Mobilepad.Companion.Lab;

/// <summary>adb commands for one phone (serial), as the lab needs them.</summary>
public sealed class LabAdb(string adbPath, string serial)
{
    public const string PackageDev = "io.github.pedropasinn.mobilepad.dev";
    public const string PackageRelease = "io.github.pedropasinn.mobilepad";
    public const string Activity = "io.github.pedropasinn.mobilepad.MainActivity";

    public string AdbPath { get; } = adbPath;
    public string Serial { get; } = serial;

    /// <summary>Serials of phones in the "device" state.</summary>
    public static async Task<List<string>> DevicesAsync(string adbPath, CancellationToken ct = default)
    {
        var (_, output, _) = await RunRawAsync(adbPath, ["devices"], ct);
        return output.Split('\n')
            .Select(l => l.Trim().Split('\t'))
            .Where(p => p.Length == 2 && p[1] == "device")
            .Select(p => p[0])
            .ToList();
    }

    public Task<(int Code, string Out, string Err)> RunAsync(CancellationToken ct, params string[] args) =>
        RunRawAsync(AdbPath, ["-s", Serial, .. args], ct);

    public async Task<string> ShellAsync(string command, CancellationToken ct = default) =>
        (await RunAsync(ct, "shell", command)).Out;

    /// <summary>Development build first (it is what the lab is used with), then release; null when neither is installed.</summary>
    public async Task<string?> InstalledPackageAsync(CancellationToken ct = default)
    {
        var list = await ShellAsync("pm list packages io.github.pedropasinn.mobilepad", ct);
        var packages = list.Split('\n').Select(l => l.Trim()).Where(l => l.StartsWith("package:")).Select(l => l[8..]).ToHashSet();
        if (packages.Contains(PackageDev)) return PackageDev;
        if (packages.Contains(PackageRelease)) return PackageRelease;
        return null;
    }

    /// <summary>Remote folder of one run: &lt;external files&gt;/lab/&lt;run&gt; (SPEC §4).</summary>
    public static string RemoteRunDir(string package, string runId) => $"/sdcard/Android/data/{package}/files/lab/{runId}";

    /// <summary>File contents through exec-out (binary-safe, no CRLF translation); null when missing.</summary>
    public async Task<string?> ReadFileAsync(string remotePath, CancellationToken ct = default)
    {
        var (code, output, err) = await RunAsync(ct, "exec-out", $"cat '{remotePath}' 2>/dev/null");
        if (code != 0 || output.Length == 0 || err.Contains("No such file")) return null;
        return output;
    }

    public async Task<bool> PullAsync(string remotePath, string localPath, CancellationToken ct = default)
    {
        var (code, _, _) = await RunAsync(ct, "pull", remotePath, localPath);
        return code == 0 && File.Exists(localPath);
    }

    public static async Task<(int Code, string Out, string Err)> RunRawAsync(string adbPath, IEnumerable<string> args, CancellationToken ct)
    {
        var info = new ProcessStartInfo(adbPath)
        {
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true,
            StandardOutputEncoding = Encoding.UTF8,
        };
        foreach (var a in args) info.ArgumentList.Add(a);
        using var process = Process.Start(info)!;
        try
        {
            var stdout = process.StandardOutput.ReadToEndAsync(ct);
            var stderr = process.StandardError.ReadToEndAsync(ct);
            await process.WaitForExitAsync(ct);
            return (process.ExitCode, await stdout, await stderr);
        }
        catch (OperationCanceledException)
        {
            try { process.Kill(); } catch { /* already gone */ }
            throw;
        }
    }
}

/// <summary>One clock comparison: phone minus PC, from the exchanges with the shortest round trip.</summary>
public sealed record SyncResult(long OffsetNs, long AtNs, long RttMinNs, long RttSubsetMedianNs, int Samples, int Used)
{
    /// <summary>SPEC §1: uncertainty of one sync = minimum RTT / 2.</summary>
    public long UncertaintyNs => RttMinNs / 2;
}

/// <summary>
/// Phone↔PC clock model: offsets measured before and after a run, interpolated linearly in
/// between (drift). Uncertainty follows SPEC §6: min RTT/2 + drift.
/// </summary>
public sealed record SyncModel(SyncResult Before, SyncResult? After)
{
    public long DriftNs => After is null ? 0 : After.OffsetNs - Before.OffsetNs;

    public double DriftPpm => After is null || After.AtNs == Before.AtNs ? 0 : DriftNs * 1e6 / (After.AtNs - Before.AtNs);

    public long UncertaintyNs => Math.Max(Before.RttMinNs, After?.RttMinNs ?? 0) / 2 + Math.Abs(DriftNs);

    /// <summary>Phone − PC offset at PC time <paramref name="pcNs"/>.</summary>
    public double OffsetAt(long pcNs)
    {
        if (After is null || After.AtNs <= Before.AtNs) return Before.OffsetNs;
        double k = Math.Clamp((pcNs - Before.AtNs) / (double)(After.AtNs - Before.AtNs), 0, 1);
        return Before.OffsetNs + k * (After.OffsetNs - Before.OffsetNs);
    }

    public long PhoneToPc(long phoneNs) => phoneNs - (long)Math.Round(OffsetAt(phoneNs - Before.OffsetNs));
}

/// <summary>
/// Clock sync over one persistent "adb shell" that answers every line with <c>date +%s%N</c>
/// (an exchange costs a few ms; a new "adb shell date" per sample costs ~50 ms and is lopsided).
/// </summary>
public static class ClockSync
{
    public const int DefaultExchanges = 240;

    public static SyncResult Measure(LabAdb adb, int exchanges = DefaultExchanges, CancellationToken ct = default)
    {
        var info = new ProcessStartInfo(adb.AdbPath)
        {
            RedirectStandardInput = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true,
        };
        foreach (var a in new[] { "-s", adb.Serial, "shell", "while read l; do date +%s%N; done" }) info.ArgumentList.Add(a);
        using var process = Process.Start(info)!;
        process.StandardInput.AutoFlush = true;
        process.StandardInput.NewLine = "\n";
        var samples = new List<(long T0, long Phone, long T1)>(exchanges);
        var thread = Thread.CurrentThread;
        var priority = thread.Priority;
        thread.Priority = ThreadPriority.Highest;
        try
        {
            // Warm-up: the first answers carry the shell's start-up.
            for (int i = 0; i < exchanges + 5 && !ct.IsCancellationRequested; i++)
            {
                long t0 = LabTime.NowNs();
                process.StandardInput.WriteLine("x");
                var line = process.StandardOutput.ReadLine();
                long t1 = LabTime.NowNs();
                if (line is null) break;
                if (i < 5) continue;
                if (long.TryParse(line.Trim().Split(' ')[0], out var phone)) samples.Add((t0, phone, t1));
            }
        }
        finally
        {
            thread.Priority = priority;
            try { process.Kill(); } catch { /* already gone */ }
        }
        ct.ThrowIfCancellationRequested();
        return Reduce(samples) ?? throw new IOException("clock sync: the phone did not answer (date +%s%N)");
    }

    /// <summary>
    /// Keeps the fastest sixth of the exchanges (at least 3): their median offset (phone minus
    /// the PC midpoint of the exchange) is the estimate; min RTT bounds its error.
    /// </summary>
    public static SyncResult? Reduce(IReadOnlyList<(long T0, long Phone, long T1)> samples)
    {
        if (samples.Count == 0) return null;
        var all = samples
            .Select(s => (Rtt: s.T1 - s.T0, Offset: s.Phone - (s.T0 + (s.T1 - s.T0) / 2), Mid: s.T0 + (s.T1 - s.T0) / 2))
            .OrderBy(s => s.Rtt)
            .ToList();
        var keep = all.Take(Math.Min(all.Count, Math.Max(3, all.Count / 6))).ToList();
        return new SyncResult(
            MedianLong(keep.Select(k => k.Offset)),
            MedianLong(keep.Select(k => k.Mid)),
            keep[0].Rtt,
            MedianLong(keep.Select(k => k.Rtt)),
            all.Count,
            keep.Count);
    }

    private static long MedianLong(IEnumerable<long> values)
    {
        var v = values.OrderBy(x => x).ToList();
        return v.Count % 2 == 1 ? v[v.Count / 2] : v[v.Count / 2 - 1] / 2 + v[v.Count / 2] / 2;
    }
}
