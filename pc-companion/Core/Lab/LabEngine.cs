using System.Diagnostics;
using System.Globalization;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace Mobilepad.Companion.Lab;

/// <summary>What the user picked on the Lab screen. Null parameters are left to the app's defaults.</summary>
public sealed class LabRequest
{
    public string Test { get; set; } = LabTests.Latency;
    public string Link { get; set; } = LabLinks.Usb;
    public int? Count { get; set; }
    public long? Seed { get; set; }
    public int? RateHz { get; set; }
    public int? Seconds { get; set; }
    /// <summary>adb serial; null = the first phone in "adb devices".</summary>
    public string? Serial { get; set; }
}

/// <summary>What the engine needs from the companion around it.</summary>
public sealed class LabHost
{
    public string? AdbPath { get; init; }
    /// <summary>(link, mark) → observer for that link, or null when none is available.</summary>
    public Func<string, string, ILabObserver?>? CreateObserver { get; init; }
    /// <summary>link → error message when the phone is not connected that way, null when ready.</summary>
    public Func<string, string?>? CheckLink { get; init; }
    public string CompanionVersion { get; init; } = "";
}

/// <summary>Live numbers of the running test, read by the UI timer.</summary>
public sealed class LabProgress
{
    public volatile string Phase = "";
    public volatile string Serial = "";
    public volatile string Package = "";
    public volatile string RunId = "";
    public long StartedTicks;
    public double EstimatedSeconds;
    public int ExpectedSteps;
    public int Recv, Submit, Obs, LastSeq = -1;
    public double PollHz, RecvToSubmitUs = double.NaN;
    public long Dropped;

    public double ElapsedSeconds => StartedTicks == 0 ? 0 : (Stopwatch.GetTimestamp() - StartedTicks) / (double)Stopwatch.Frequency;

    /// <summary>0..1: by seq for counted tests, by time otherwise.</summary>
    public double Fraction =>
        ExpectedSteps > 1 && LastSeq >= 0 ? Math.Clamp((LastSeq + 1) / (double)ExpectedSteps, 0, 1)
        : EstimatedSeconds > 0 ? Math.Clamp(ElapsedSeconds / EstimatedSeconds, 0, 1) : 0;
}

public sealed class LabRunResult
{
    public string Folder { get; init; } = "";
    public string Status { get; init; } = "";
    public string? Error { get; init; }
    public LabSummary? Summary { get; init; }
}

/// <summary>
/// One lab run, end to end (SPEC §5): sync clocks, start the observer and the recv/submit log,
/// trigger the test on the phone (SPEC §4), wait for done.json, sync again, pull phone.csv,
/// write the run folder and analyse it.
/// </summary>
public sealed class LabEngine(LabHost host)
{
    private int _running;

    public bool Running => Volatile.Read(ref _running) != 0;
    public LabProgress Progress { get; private set; } = new();

    public static int DefaultCount(string test) => test switch
    {
        LabTests.Latency => 200,
        LabTests.Multi => 200,
        LabTests.Reliability => 20000,
        LabTests.Reconnect => 5,
        _ => 0,
    };

    /// <summary>Rough duration, for the progress bar and the done.json timeout.</summary>
    public static double EstimateSeconds(LabRequest r)
    {
        int count = r.Count ?? DefaultCount(r.Test);
        int rate = r.RateHz ?? 250;
        return r.Test switch
        {
            LabTests.Latency => count * 0.3 + 3,
            LabTests.Multi => count * 0.15 + 3,
            LabTests.Analog => (r.Seconds ?? (r.Count is { } c ? c / (double)Math.Max(1, rate) : 8)) + 3,
            LabTests.Throughput => (r.Seconds ?? 10) + 3,
            LabTests.Reliability => count / (double)Math.Max(1, r.RateHz ?? 500) + 3,
            LabTests.Reconnect => count * 15 + 5,
            _ => 30,
        };
    }

    /// <summary>
    /// Runs one test. <paramref name="stop"/> ends the wait early (the Stop button): whatever was
    /// recorded is still collected and analysed, with status "stopped".
    /// </summary>
    public async Task<LabRunResult> RunAsync(LabRequest request, CancellationToken stop)
    {
        if (Interlocked.Exchange(ref _running, 1) != 0) throw new InvalidOperationException("a lab run is already going");
        var progress = Progress = new LabProgress();
        string? folder = null;
        var meta = new JsonObject();
        ILabObserver? observer = null;
        ResourceSampler? sampler = null;
        try
        {
            progress.Phase = "adb";
            if (host.AdbPath is null) throw new LabException("ADB not found (install Android platform-tools)");
            var devices = await LabAdb.DevicesAsync(host.AdbPath, stop);
            var serial = request.Serial ?? devices.FirstOrDefault()
                ?? throw new LabException("no phone over ADB: plug it in and allow USB debugging (ADB is needed on every link)");
            var adb = new LabAdb(host.AdbPath, serial);
            progress.Serial = serial;
            var package = await adb.InstalledPackageAsync(stop) ?? throw new LabException("Mobilepad is not installed on the phone");
            progress.Package = package;
            if (host.CheckLink?.Invoke(request.Link) is { } linkError) throw new LabException(linkError);

            long seed = request.Seed ?? Random.Shared.NextInt64(1, int.MaxValue);
            string mark = LabTests.MarkFor(request.Test);
            string runId = $"{DateTime.Now:yyyyMMdd-HHmmss}-{request.Test}-{request.Link}";
            progress.RunId = runId;
            folder = Path.Combine(LabHistory.RunsDir, runId);
            Directory.CreateDirectory(folder);
            double estimate = EstimateSeconds(request);
            progress.EstimatedSeconds = estimate;
            // latency: labCount = pulses, each a press (even seq) and a release (odd seq).
            progress.ExpectedSteps = request.Test switch
            {
                LabTests.Latency => 2 * (request.Count ?? DefaultCount(request.Test)),
                LabTests.Multi or LabTests.Reliability => request.Count ?? DefaultCount(request.Test),
                LabTests.Analog when request.Seconds is null && request.Count is null => 2000,
                _ => 0,
            };

            meta["run_id"] = runId;
            meta["test"] = request.Test;
            meta["link"] = request.Link;
            meta["mark"] = mark;
            meta["status"] = "running";
            meta["params"] = new JsonObject
            {
                ["count"] = request.Count, ["seed"] = seed, ["rate_hz"] = request.RateHz, ["seconds"] = request.Seconds,
            };
            meta["pc"] = new JsonObject
            {
                ["machine"] = Environment.MachineName,
                ["os"] = Environment.OSVersion.VersionString,
                ["cpus"] = Environment.ProcessorCount,
                ["companion_version"] = host.CompanionVersion,
                ["clock"] = "DateTime.UtcNow (GetSystemTimePreciseAsFileTime)",
            };
            meta["app"] = new JsonObject { ["package"] = package };
            meta["phone"] = new JsonObject { ["serial"] = serial };

            // ---- Sync before ----
            progress.Phase = "sync";
            var before = await Task.Run(() => ClockSync.Measure(adb, ClockSync.DefaultExchanges, stop), stop);
            var model = (await adb.ShellAsync("getprop ro.product.model", stop)).Trim();
            ((JsonObject)meta["phone"]!)["model"] = model;
            var remoteDir = LabAdb.RemoteRunDir(package, runId);
            await adb.ShellAsync($"rm -rf '{remoteDir}'", stop);

            // ---- Instrumentation on ----
            double costNs = LabLog.MeasureCostNs();
            int steps = Math.Max(progress.ExpectedSteps, 1000);
            int capacity = request.Test == LabTests.Throughput || request.Test == LabTests.Analog
                ? (int)Math.Clamp(estimate * 4000 * 2, 65536, 2_000_000)
                : Math.Clamp(steps * 6 + 20000, 65536, 2_000_000);
            observer = host.CreateObserver?.Invoke(request.Link, mark);
            observer?.Start(capacity);
            sampler = new ResourceSampler(adb);
            sampler.Start();
            LabLog.Start(capacity);
            long startedNs = LabTime.NowNs();
            progress.StartedTicks = Stopwatch.GetTimestamp();

            // ---- Trigger (SPEC §4) ----
            progress.Phase = "running";
            var args = new List<string>
            {
                "shell", "am", "start", "-f", "0x20000000", "-n", $"{package}/{LabAdb.Activity}",
                "--ez", "lab", "true", "--es", "labTest", request.Test,
            };
            if (request.Count is { } count) args.AddRange(["--ei", "labCount", count.ToString(CultureInfo.InvariantCulture)]);
            args.AddRange(["--el", "labSeed", seed.ToString(CultureInfo.InvariantCulture)]);
            if (request.RateHz is { } rate) args.AddRange(["--ei", "labRateHz", rate.ToString(CultureInfo.InvariantCulture)]);
            if (request.Seconds is { } secs) args.AddRange(["--ei", "labSeconds", secs.ToString(CultureInfo.InvariantCulture)]);
            args.AddRange(["--es", "labRun", runId]);
            meta["intent"] = "adb " + string.Join(' ', args);
            var started = await adb.RunAsync(stop, [.. args]);
            if (started.Out.Contains("Error", StringComparison.Ordinal) || started.Code != 0)
                throw new LabException($"am start failed: {(started.Out + started.Err).Trim()}");

            // ---- Wait for done.json ----
            string status = "ok";
            JsonObject? done = null;
            var deadline = DateTime.UtcNow.AddSeconds(estimate * 3 + 60);
            try
            {
                while (true)
                {
                    await Task.Delay(500, stop);
                    UpdateLive(progress, observer, mark);
                    var text = await adb.ReadFileAsync($"{remoteDir}/done.json", stop);
                    if (text is not null && TryParseObject(text) is { } d)
                    {
                        done = d;
                        break;
                    }
                    if (DateTime.UtcNow > deadline)
                    {
                        status = "timeout";
                        break;
                    }
                }
                // Trailing states: the final neutral still travels to the observer.
                progress.Phase = "finishing";
                await Task.Delay(800, stop);
            }
            catch (OperationCanceledException)
            {
                status = "stopped";
            }

            // ---- Instrumentation off ----
            long endedNs = LabTime.NowNs();
            UpdateLive(progress, observer, mark);
            var (pcRows, links) = LabLog.Stop();
            var obsRows = observer?.Stop() ?? [];
            var resRows = await sampler.StopAsync();
            sampler = null;

            // From here on the Stop button no longer applies: collect what exists.
            var collect = CancellationToken.None;
            progress.Phase = "sync";
            SyncResult? after = null;
            try { after = await Task.Run(() => ClockSync.Measure(adb, ClockSync.DefaultExchanges, collect)); }
            catch (Exception e) when (e is IOException or InvalidOperationException) { }
            var sync = new SyncModel(before, after);

            progress.Phase = "collect";
            var phonePath = Path.Combine(folder, "phone.csv");
            bool havePhone = await adb.PullAsync($"{remoteDir}/phone.csv", phonePath, collect);
            done ??= await adb.ReadFileAsync($"{remoteDir}/done.json", collect) is { } late ? TryParseObject(late) : null;
            var phoneRows = havePhone ? LabCsv.Parse(await File.ReadAllTextAsync(phonePath, collect)) : [];

            // The app states the mark axis it used; it wins over the default for the test.
            if (done?["mark"] is JsonValue mv && mv.TryGetValue<string>(out var doneMark) && doneMark is "rx" or "ry")
            {
                mark = doneMark;
                meta["mark"] = mark;
            }
            var pcWithSeq = LabAnalysis.AssignSeqs(pcRows, mark);
            LabCsv.WriteRows(Path.Combine(folder, "pc.csv"), pcWithSeq);
            LabCsv.WriteObs(Path.Combine(folder, "obs.csv"), obsRows);
            ResourceSampler.Write(Path.Combine(folder, "res.csv"), resRows);

            if (done is not null)
            {
                meta["done"] = done.DeepClone();
                if (done["ok"] is JsonValue ok && ok.TryGetValue<bool>(out var okValue) && !okValue)
                {
                    status = "app_error";
                    meta["error"] = done["error"]?.DeepClone();
                }
                var app = (JsonObject)meta["app"]!;
                app["version"] = done["app_version"]?.DeepClone();
                app["commit"] = done["app_commit"]?.DeepClone();
                ((JsonObject)meta["phone"]!)["device"] = done["device"]?.DeepClone();
            }
            else if (status == "ok")
            {
                status = "no_done";
            }
            meta["status"] = status;
            meta["sync"] = SyncJson(sync);
            meta["times"] = new JsonObject
            {
                ["started_ns"] = startedNs,
                ["ended_ns"] = endedNs,
                ["phone_started_ns"] = done?["started_ns"]?.DeepClone(),
                ["phone_ended_ns"] = done?["ended_ns"]?.DeepClone(),
                ["started_utc"] = LabTime.ToUtc(startedNs).ToString("o", CultureInfo.InvariantCulture),
            };
            meta["observer"] = observer is null ? null : new JsonObject
            {
                ["kind"] = observer.Kind, ["device"] = observer.Device, ["polls"] = observer.Polls,
                ["poll_hz"] = Math.Round(observer.PollHz, 1), ["changes"] = obsRows.Count, ["capacity"] = capacity,
            };
            meta["lab_log"] = new JsonObject
            {
                ["capacity"] = capacity, ["recorded"] = pcRows.Count, ["dropped"] = progress.Dropped,
                ["cost_ns_per_record"] = Math.Round(costNs, 1),
                ["note"] = "cost = timestamp + copy into the pre-allocated buffer, measured before the run; off outside runs (one volatile read)",
            };
            meta["link_events"] = new JsonArray(links.Select(l => (JsonNode)new JsonObject
            {
                ["t_ns"] = l.TNs, ["kind"] = l.Kind, ["detail"] = l.Detail,
            }).ToArray());
            meta["files"] = new JsonArray("meta.json", "phone.csv", "pc.csv", "obs.csv", "res.csv", "summary.json");

            progress.Phase = "analyse";
            var summary = LabAnalysis.Analyze(new LabInput
            {
                RunId = runId,
                Test = request.Test,
                Link = request.Link,
                Mark = mark,
                Phone = phoneRows,
                Pc = pcWithSeq,
                Obs = obsRows,
                ObserverRan = observer is not null,
                ObserverPollHz = observer?.PollHz ?? 0,
                Sync = sync,
                Links = links,
                BootstrapSeed = (ulong)seed,
                PhoneReconnect = ParseReconnect(done),
            });
            if (!havePhone) summary.Notes.Add("phone.csv missing: only the companion's stages are available");
            if (status != "ok") summary.Notes.Add($"run status: {status}");
            await File.WriteAllTextAsync(Path.Combine(folder, "summary.json"), LabJson.Serialize(summary), collect);
            await WriteMetaAsync(folder, meta);
            progress.Phase = "done";
            return new LabRunResult { Folder = folder, Status = status, Summary = summary, Error = meta["error"]?.ToString() };
        }
        catch (Exception e) when (e is LabException or IOException or OperationCanceledException or JsonException or InvalidOperationException
                                      or System.ComponentModel.Win32Exception or UnauthorizedAccessException)
        {
            LabLog.Active = false;
            try { observer?.Stop(); } catch { /* best effort */ }
            if (sampler is not null) await sampler.StopAsync();
            var status = e is OperationCanceledException ? "stopped" : "error";
            progress.Phase = status;
            if (folder is not null)
            {
                meta["status"] = status;
                meta["error"] = e.Message;
                try { await WriteMetaAsync(folder, meta); } catch (IOException) { }
            }
            return new LabRunResult { Folder = folder ?? "", Status = status, Error = e.Message };
        }
        finally
        {
            LabLog.Active = false;
            observer?.Dispose();
            Volatile.Write(ref _running, 0);
        }
    }

    private static void UpdateLive(LabProgress p, ILabObserver? observer, string mark)
    {
        var (recv, submit, lastSeq, us) = LabLog.Live(mark);
        p.Recv = recv;
        p.Submit = submit;
        if (lastSeq >= 0) p.LastSeq = lastSeq;
        p.RecvToSubmitUs = us;
        p.Dropped = LabLog.Dropped;
        if (observer is not null)
        {
            p.Obs = observer.Count;
            p.PollHz = observer.PollHz;
        }
    }

    private static JsonObject SyncJson(SyncModel s)
    {
        static JsonObject One(SyncResult r) => new()
        {
            ["offset_ns"] = r.OffsetNs, ["at_ns"] = r.AtNs, ["rtt_min_ns"] = r.RttMinNs,
            ["rtt_subset_median_ns"] = r.RttSubsetMedianNs, ["samples"] = r.Samples, ["used"] = r.Used,
            ["uncertainty_ns"] = r.UncertaintyNs,
        };
        return new JsonObject
        {
            ["method"] = "persistent adb shell, date +%s%N per line; fastest sixth of the exchanges; offset = phone - PC midpoint",
            ["before"] = One(s.Before),
            ["after"] = s.After is null ? null : One(s.After),
            ["drift_ns"] = s.DriftNs,
            ["drift_ppm"] = Math.Round(s.DriftPpm, 3),
            ["uncertainty_ns"] = s.UncertaintyNs,
        };
    }

    /// <summary>done.json reconnect[] (one object per cycle): the app measures the reconnects itself.</summary>
    private static List<PhoneReconnectCycle> ParseReconnect(JsonObject? done)
    {
        var result = new List<PhoneReconnectCycle>();
        if (done?["reconnect"] is not JsonArray cycles) return result;
        static long? L(JsonNode? n) => n is JsonValue v && v.TryGetValue<long>(out var x) ? x : n is JsonValue d && d.TryGetValue<double>(out var y) ? (long)y : null;
        static bool? B(JsonNode? n) => n is JsonValue v && v.TryGetValue<bool>(out var x) ? x : null;
        foreach (var c in cycles.OfType<JsonObject>())
            result.Add(new PhoneReconnectCycle(L(c["drop_ns"]), L(c["up_ns"]), L(c["reconnect_ns"]), B(c["reconnected"]),
                B(c["released_after_reconnect"]), B(c["down_seen"])));
        return result;
    }

    private static async Task WriteMetaAsync(string folder, JsonObject meta) =>
        await File.WriteAllTextAsync(Path.Combine(folder, "meta.json"), meta.ToJsonString(LabJson.Options));

    private static JsonObject? TryParseObject(string text)
    {
        try { return JsonNode.Parse(text.Trim()) as JsonObject; }
        catch (JsonException) { return null; }
    }
}

public sealed class LabException(string message) : Exception(message);
