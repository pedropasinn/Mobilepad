using System.Diagnostics;
using System.Globalization;

namespace Mobilepad.Companion.Lab;

public readonly record struct ResRow(long TNs, string Source, string Metric, double Value);

/// <summary>
/// res.csv: every 2 s the companion's CPU% (of the whole machine) and memory, and the phone's
/// battery level, temperature, voltage and current (dumpsys battery; current_now from sysfs
/// when dumpsys does not report it).
/// </summary>
public sealed class ResourceSampler
{
    private readonly LabAdb? _adb;
    private readonly List<ResRow> _rows = [];
    private CancellationTokenSource? _cts;
    private Task? _task;

    public ResourceSampler(LabAdb? adb) => _adb = adb;

    public TimeSpan Interval { get; init; } = TimeSpan.FromSeconds(2);

    public void Start()
    {
        _cts = new CancellationTokenSource();
        _task = Task.Run(() => LoopAsync(_cts.Token));
    }

    public async Task<List<ResRow>> StopAsync()
    {
        _cts?.Cancel();
        if (_task is not null)
        {
            try { await _task; } catch (OperationCanceledException) { }
        }
        lock (_rows) return [.. _rows];
    }

    private async Task LoopAsync(CancellationToken ct)
    {
        using var process = Process.GetCurrentProcess();
        var lastCpu = process.TotalProcessorTime;
        long lastWall = Stopwatch.GetTimestamp();
        while (!ct.IsCancellationRequested)
        {
            process.Refresh();
            long t = LabTime.NowNs();
            var cpu = process.TotalProcessorTime;
            long wall = Stopwatch.GetTimestamp();
            double seconds = (wall - lastWall) / (double)Stopwatch.Frequency;
            if (seconds > 0.05)
            {
                Add(t, "pc", "companion_cpu_pct", (cpu - lastCpu).TotalSeconds / seconds / Environment.ProcessorCount * 100);
                lastCpu = cpu;
                lastWall = wall;
            }
            Add(t, "pc", "companion_working_set_mb", process.WorkingSet64 / 1048576.0);
            Add(t, "pc", "companion_private_mb", process.PrivateMemorySize64 / 1048576.0);
            Add(t, "pc", "companion_gc_heap_mb", GC.GetTotalMemory(false) / 1048576.0);

            if (_adb is not null)
            {
                try
                {
                    var text = await _adb.ShellAsync(
                        "dumpsys battery; echo current_now_sysfs: $(cat /sys/class/power_supply/battery/current_now 2>/dev/null)", ct);
                    foreach (var (metric, value) in ParseBattery(text)) Add(LabTime.NowNs(), "phone", metric, value);
                }
                catch (Exception e) when (e is not OperationCanceledException) { /* adb busy or phone gone: skip this sample */ }
            }
            await Task.Delay(Interval, ct);
        }
    }

    private void Add(long t, string source, string metric, double value)
    {
        lock (_rows) _rows.Add(new ResRow(t, source, metric, Math.Round(value, 3)));
    }

    /// <summary>dumpsys battery: level (%), temperature (tenths of °C), voltage (mV), "current now" (µA on most phones).</summary>
    public static List<(string Metric, double Value)> ParseBattery(string text)
    {
        var result = new List<(string, double)>();
        bool haveCurrent = false;
        foreach (var raw in text.Split('\n'))
        {
            var line = raw.Trim();
            int colon = line.IndexOf(':');
            if (colon <= 0) continue;
            var key = line[..colon].Trim().ToLowerInvariant();
            var valueText = line[(colon + 1)..].Trim();
            if (!double.TryParse(valueText, NumberStyles.Float, CultureInfo.InvariantCulture, out var v)) continue;
            switch (key)
            {
                case "level": result.Add(("battery_level_pct", v)); break;
                case "temperature": result.Add(("battery_temp_c", v / 10)); break;
                case "voltage": result.Add(("battery_voltage_mv", v)); break;
                case "current now":
                    result.Add(("battery_current_now_ua", v));
                    haveCurrent = true;
                    break;
                case "current_now_sysfs" when !haveCurrent:
                    result.Add(("battery_current_now_ua", v));
                    haveCurrent = true;
                    break;
            }
        }
        return result;
    }

    public static void Write(string path, IEnumerable<ResRow> rows)
    {
        using var w = new StreamWriter(path, false, new System.Text.UTF8Encoding(false));
        w.Write("t_ns,source,metric,value\n");
        foreach (var r in rows)
            w.Write($"{r.TNs.ToString(CultureInfo.InvariantCulture)},{r.Source},{r.Metric},{r.Value.ToString(CultureInfo.InvariantCulture)}\n");
    }
}
