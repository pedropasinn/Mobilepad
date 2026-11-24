namespace Mobilepad.Companion.Lab;

/// <summary>Everything one run produced, as read from (or about to be written to) the run folder.</summary>
public sealed class LabInput
{
    public string RunId { get; set; } = "";
    public string Test { get; set; } = LabTests.Latency;
    public string Link { get; set; } = LabLinks.Usb;
    public string Mark { get; set; } = "rx";
    /// <summary>phone.csv, phone clock.</summary>
    public List<LabRow> Phone { get; set; } = [];
    /// <summary>pc.csv (recv/submit), PC clock; seq may be −1 (deduced here).</summary>
    public List<LabRow> Pc { get; set; } = [];
    /// <summary>obs.csv, PC clock.</summary>
    public List<LabRow> Obs { get; set; } = [];
    public bool ObserverRan { get; set; }
    public double ObserverPollHz { get; set; }
    public SyncModel? Sync { get; set; }
    public List<LabLog.LinkEvent> Links { get; set; } = [];
    /// <summary>done.json reconnect[]: the reconnect test reports its cycles there, not in phone.csv.</summary>
    public List<PhoneReconnectCycle> PhoneReconnect { get; set; } = [];
    public ulong BootstrapSeed { get; set; } = 0x5EED_1AB;
    public int BootstrapIterations { get; set; } = 1000;
}

/// <summary>One cycle of the reconnect test as the app reported it (phone clock, ns).</summary>
public sealed record PhoneReconnectCycle(long? DropNs, long? UpNs, long? ReconnectNs, bool? Reconnected, bool? ReleasedAfterReconnect, bool? DownSeen);

/// <summary>
/// Joins the stages by seq (fingerprint, SPEC §2) and computes every metric of summary.json.
/// Matching is by mark, never by order: a lost step leaves a hole and does not shift the steps after it.
/// </summary>
public static class LabAnalysis
{
    public static readonly (string From, string To, bool CrossDevice)[] Pairs =
    [
        (Stages.Cmd, Stages.State, false),
        (Stages.State, Stages.Send, false),
        (Stages.Send, Stages.Recv, true),
        (Stages.Recv, Stages.Submit, false),
        (Stages.Submit, Stages.Obs, false),
        (Stages.Cmd, Stages.Obs, true),
    ];

    public static string PairName(string from, string to) => $"{from}→{to}";

    /// <summary>
    /// Deduces seq from the mark axis for rows that do not carry it, in time order: each decode is
    /// unwrapped against the previous one (runs over 30000 steps). <paramref name="tolerance"/> &gt; 0
    /// accepts the nearest mark within ±tolerance (reduced-resolution axes).
    /// </summary>
    public static List<LabRow> AssignSeqs(IReadOnlyList<LabRow> rows, string mark, int tolerance = 0, Func<int, bool>? accept = null)
    {
        var result = new List<LabRow>(rows.Count);
        int hint = -1;
        foreach (var r in rows)
        {
            if (!r.HasState)
            {
                result.Add(r with { Seq = -1 });
                continue;
            }
            int seq = Fingerprint.Decode(r.Axis(mark), tolerance, hint, accept);
            if (seq >= 0) hint = seq;
            result.Add(r with { Seq = seq });
        }
        return result;
    }

    public static LabSummary Analyze(LabInput input)
    {
        var s = new LabSummary { RunId = input.RunId, Test = input.Test, Link = input.Link, Mark = input.Mark };
        var sync = input.Sync;
        double uncertaintyMs = sync is null ? 0 : sync.UncertaintyNs / 1e6;
        s.Sync = sync is null
            ? new SyncInfo { Available = false }
            : new SyncInfo
            {
                Available = true,
                OffsetMs = R(sync.Before.OffsetNs / 1e6),
                RttMinMs = R(Math.Max(sync.Before.RttMinNs, sync.After?.RttMinNs ?? 0) / 1e6),
                DriftMs = R(sync.DriftNs / 1e6),
                DriftPpm = R(sync.DriftPpm),
                UncertaintyMs = R(uncertaintyMs),
            };
        if (sync is null) s.Notes.Add("no clock sync: send→recv and cmd→obs are not computed");
        else if (sync.After is null) s.Notes.Add("no clock sync after the run: drift unknown (offset of the first sync used)");

        // ---- Phone stages, converted to the PC clock ----
        var first = new Dictionary<string, Dictionary<int, long>>();
        foreach (var st in Stages.Order) first[st] = [];
        var reference = new Dictionary<int, ControllerState>(); // state after remap/macros
        var cmdState = new Dictionary<int, ControllerState>();
        int dupSend = 0;
        var phoneLinkDown = new List<long>();
        var phoneLinkUp = new List<long>();
        foreach (var r in input.Phone.OrderBy(r => r.TNs))
        {
            long t = sync is null ? r.TNs : sync.PhoneToPc(r.TNs);
            if (r.Stage == "link_down") { phoneLinkDown.Add(t); continue; }
            if (r.Stage == "link_up") { phoneLinkUp.Add(t); continue; }
            if (r.Seq < 0 || !first.TryGetValue(r.Stage, out var map)) continue;
            if (!map.TryAdd(r.Seq, t) && r.Stage == Stages.Send) dupSend++;
            if (r.HasState && r.Stage == Stages.State) reference.TryAdd(r.Seq, r.State);
            if (r.HasState && r.Stage == Stages.Cmd) cmdState.TryAdd(r.Seq, r.State);
        }
        foreach (var (seq, st) in cmdState) reference.TryAdd(seq, st);

        var expected = first[Stages.Cmd].Count > 0 ? first[Stages.Cmd].Keys.ToHashSet()
            : first[Stages.State].Count > 0 ? first[Stages.State].Keys.ToHashSet()
            : first[Stages.Send].Keys.ToHashSet();
        Func<int, bool>? accept = expected.Count > 0 ? expected.Contains : null;

        // ---- PC stages (recv/submit), seq from the mark (exact: the companion sees full values) ----
        var pc = AssignSeqs(input.Pc.OrderBy(r => r.TNs).ToList(), input.Mark);
        int dupRecv = 0, outOfOrder = 0, unmarkedRecv = 0, maxRecvSeq = -1;
        var recvStates = new Dictionary<int, ControllerState>();
        LabRow? lastSubmit = null;
        foreach (var r in pc)
        {
            if (r.Stage == Stages.Submit) lastSubmit = r;
            if (r.Seq < 0)
            {
                if (r.Stage == Stages.Recv) unmarkedRecv++;
                continue;
            }
            var map = first[r.Stage];
            if (r.Stage == Stages.Recv)
            {
                if (!map.TryAdd(r.Seq, r.TNs))
                {
                    dupRecv++;
                    continue;
                }
                recvStates[r.Seq] = r.State;
                if (r.Seq < maxRecvSeq) outOfOrder++;
                else maxRecvSeq = r.Seq;
            }
            else map.TryAdd(r.Seq, r.TNs);
        }

        // ---- Observer: resolution first, then decode with that tolerance ----
        var obsSorted = input.Obs.OrderBy(r => r.TNs).ToList();
        s.Axes = AxisResolution(obsSorted);
        int markQuantum = s.Axes.FirstOrDefault(a => a.Axis == input.Mark)?.ResolutionObserved ?? 1;
        var obs = AssignSeqs(obsSorted, input.Mark, markQuantum > 1 ? markQuantum : 0, accept);
        var obsStates = new Dictionary<int, ControllerState>();
        int unmarkedObs = 0;
        foreach (var r in obs)
        {
            if (!r.HasState) continue;
            if (r.Seq < 0) { unmarkedObs++; continue; }
            if (first[Stages.Obs].TryAdd(r.Seq, r.TNs)) obsStates[r.Seq] = r.State;
        }

        // ---- Latency per stage pair ----
        var rng = new SplitMix64(input.BootstrapSeed);
        foreach (var (from, to, cross) in Pairs)
        {
            if (cross && sync is null) continue;
            var a = first[from];
            var b = first[to];
            var values = new List<double>();
            foreach (var (seq, ta) in a)
                if (b.TryGetValue(seq, out var tb)) values.Add((tb - ta) / 1e6);
            if (values.Count == 0) continue;
            var stats = Describe(values, rng, input.BootstrapIterations);
            stats.Name = PairName(from, to);
            stats.From = from;
            stats.To = to;
            stats.Kind = cross ? "estimated" : "measured";
            stats.UncertaintyMs = cross ? R(uncertaintyMs) : 0;
            s.Stages.Add(stats);
        }
        if (s.Stage(PairName(Stages.Cmd, Stages.Obs)) is { Negative: > 0 } neg)
            s.Notes.Add($"{neg.Negative} cmd→obs values below zero: clock sync error larger than the latency");

        // ---- Loss, duplicates, order, stuck ----
        var loss = s.Loss;
        loss.Expected = expected.Count;
        loss.Sent = first[Stages.Send].Keys.Count(expected.Contains);
        loss.Received = first[Stages.Recv].Keys.Count(expected.Contains);
        loss.Submitted = first[Stages.Submit].Keys.Count(expected.Contains);
        loss.Observed = first[Stages.Obs].Keys.Count(expected.Contains);
        loss.ObserverRan = input.ObserverRan;
        loss.LostBeforeRecv = expected.Count - loss.Received;
        loss.LostBeforeObs = input.ObserverRan ? expected.Count - loss.Observed : 0;
        int lost = input.ObserverRan ? loss.LostBeforeObs : loss.LostBeforeRecv;
        loss.LostPct = expected.Count > 0 ? R(100.0 * lost / expected.Count) : 0;
        loss.DuplicatesSend = dupSend;
        loss.DuplicatesRecv = dupRecv;
        loss.OutOfOrder = outOfOrder;
        loss.UnmarkedRecv = unmarkedRecv;
        loss.UnmarkedObs = unmarkedObs;
        if (lastSubmit is { } ls) loss.FinalSubmitButtons = $"0x{ls.State.Buttons:X4}";
        var lastObs = obsSorted.Count > 0 ? obsSorted[^1] : (LabRow?)null;
        if (lastObs is { } lo) loss.FinalObsButtons = lo.HasState ? $"0x{lo.State.Buttons:X4}" : "gone";
        var finalState = lastObs is { HasState: true } fo ? fo.State : lastSubmit?.State;
        loss.Stuck = finalState is { } f && (f.Buttons != 0 || f.LeftTrigger != 0 || f.RightTrigger != 0);

        // ---- Effective rates ----
        s.Rates = new RateStats
        {
            SendHz = R(Rate(first[Stages.Send].Values)),
            RecvHz = R(Rate(first[Stages.Recv].Values)),
            SubmitHz = R(Rate(first[Stages.Submit].Values)),
            ObsHz = R(Rate(obs.Where(o => o.HasState).Select(o => o.TNs))),
            ObserverPollHz = R(input.ObserverPollHz),
        };

        // ---- Value error per axis (obs vs the phone's state) and integrity ----
        foreach (var axis in s.Axes)
        {
            if (axis.Axis == input.Mark) continue;
            var errors = new List<double>();
            foreach (var (seq, o) in obsStates)
                if (reference.TryGetValue(seq, out var refState))
                    errors.Add(Math.Abs(Field(o, axis.Axis) - Field(refState, axis.Axis)));
            if (errors.Count == 0) continue;
            errors.Sort();
            axis.Compared = errors.Count;
            axis.MeanAbsError = R(errors.Average());
            axis.P95AbsError = R(Percentile(errors, 0.95));
            axis.MaxAbsError = R(errors[^1]);
        }
        var integrity = s.Integrity;
        foreach (var (seq, rs) in recvStates)
        {
            if (!reference.TryGetValue(seq, out var refState)) continue;
            integrity.ComparedRecv++;
            if (CompareFields(refState, rs, null, integrity.MismatchRecv)) integrity.IntactRecv++;
        }
        var quantum = s.Axes.ToDictionary(a => a.Axis, a => Math.Max(1, a.ResolutionObserved));
        foreach (var (seq, os) in obsStates)
        {
            if (!reference.TryGetValue(seq, out var refState)) continue;
            integrity.ComparedObs++;
            if (CompareFields(refState, os, quantum, integrity.MismatchObs, input.Mark)) integrity.IntactObs++;
        }
        integrity.IntactRecvPct = integrity.ComparedRecv > 0 ? R(100.0 * integrity.IntactRecv / integrity.ComparedRecv) : 0;
        integrity.IntactObsPct = integrity.ComparedObs > 0 ? R(100.0 * integrity.IntactObs / integrity.ComparedObs) : 0;

        // ---- Reconnects ----
        if (input.Test == LabTests.Reconnect || input.PhoneReconnect.Count > 0 || phoneLinkDown.Count > 0)
            s.Reconnect = Reconnects(input, obsSorted, phoneLinkDown, phoneLinkUp);

        // ---- Time series and headline ----
        var primary = s.Stage(PairName(Stages.Cmd, Stages.Obs))
                      ?? s.Stage(PairName(Stages.Send, Stages.Recv))
                      ?? s.Stage(PairName(Stages.Recv, Stages.Submit));
        if (primary is not null)
        {
            var a = first[primary.From];
            var b = first[primary.To];
            long t0 = first[Stages.Cmd].Count > 0 ? first[Stages.Cmd].Values.Min() : a.Values.DefaultIfEmpty().Min();
            var points = a.Where(p => b.ContainsKey(p.Key))
                .Select(p => (T: (p.Value - t0) / 1e9, Ms: (b[p.Key] - p.Value) / 1e6))
                .OrderBy(p => p.T)
                .ToList();
            int step = Math.Max(1, points.Count / 3000);
            var kept = points.Where((_, i) => i % step == 0).ToList();
            s.Series = new SeriesData { Stage = primary.Name, TS = kept.Select(p => R(p.T)).ToArray(), Ms = kept.Select(p => R(p.Ms)).ToArray() };
            s.Headline = new Headline
            {
                Stage = primary.Name, Kind = primary.Kind, P50Ms = primary.P50Ms, P95Ms = primary.P95Ms, P99Ms = primary.P99Ms,
                N = primary.N, Expected = expected.Count, Lost = lost, UncertaintyMs = primary.UncertaintyMs,
            };
        }
        else
        {
            s.Headline = new Headline { Expected = expected.Count, Lost = lost };
        }
        return s;
    }

    private static ReconnectStats Reconnects(LabInput input, List<LabRow> obs, List<long> phoneDown, List<long> phoneUp)
    {
        var rc = new ReconnectStats();
        var downs = input.Links.Where(l => l.Kind == "down").Select(l => l.TNs).OrderBy(t => t).ToList();
        var ups = input.Links.Where(l => l.Kind == "up").Select(l => l.TNs).OrderBy(t => t).ToList();
        foreach (var d in downs)
        {
            var up = ups.FirstOrDefault(u => u > d);
            if (up > 0) rc.LinkDownToUpMs.Add(R((up - d) / 1e6));
        }
        foreach (var d in phoneDown)
        {
            var up = phoneUp.FirstOrDefault(u => u > d);
            if (up > 0) rc.PhoneDownToUpMs.Add(R((up - d) / 1e6));
        }
        // Observer: present → gone → present.
        long goneAt = 0;
        var obsDrops = new List<long>();
        for (int i = 0; i < obs.Count; i++)
        {
            var r = obs[i];
            if (!r.HasState && goneAt == 0 && i > 0) { goneAt = r.TNs; obsDrops.Add(r.TNs); }
            else if (r.HasState && goneAt != 0)
            {
                rc.ObsGoneToBackMs.Add(R((r.TNs - goneAt) / 1e6));
                goneAt = 0;
            }
        }
        // A drop as seen by whichever source has them (phone, companion, observer).
        var dropTimes = phoneDown.Count > 0 ? phoneDown : downs.Count > 0 ? downs : obsDrops;
        rc.Drops = dropTimes.Count;
        foreach (var d in dropTimes)
        {
            // Released: some observer row after the drop (before the next 5 s) is neutral or the pad is gone.
            bool released = obs.Any(o => o.TNs >= d && o.TNs <= d + 5_000_000_000L && (!o.HasState || o.State.Buttons == 0));
            if (released) rc.ReleasedAfterDrop++;
        }
        // Cycles reported by the app in done.json (preferred source: it knows when it dropped the link).
        if (input.PhoneReconnect.Count > 0)
        {
            rc.PhoneDownToUpMs.Clear();
            rc.Cycles = input.PhoneReconnect.Count;
            foreach (var c in input.PhoneReconnect)
            {
                if (c.ReconnectNs is { } ns) rc.PhoneDownToUpMs.Add(R(ns / 1e6));
                else if (c.DropNs is { } d && c.UpNs is { } u && u > d) rc.PhoneDownToUpMs.Add(R((u - d) / 1e6));
                if (c.Reconnected == true) rc.Reconnected++;
                if (c.ReleasedAfterReconnect == true) rc.ReleasedAfterReconnectPhone++;
            }
        }
        var main = rc.PhoneDownToUpMs.Count > 0 ? rc.PhoneDownToUpMs : rc.LinkDownToUpMs.Count > 0 ? rc.LinkDownToUpMs : rc.ObsGoneToBackMs;
        if (main.Count > 0)
        {
            rc.MeanMs = R(main.Average());
            rc.MaxMs = R(main.Max());
        }
        return rc;
    }

    /// <summary>Distinct values and the smallest step between them, per axis, as the observer saw them.</summary>
    public static List<AxisStats> AxisResolution(IReadOnlyList<LabRow> obs)
    {
        var result = new List<AxisStats>();
        foreach (var axis in new[] { "lx", "ly", "rx", "ry", "lt", "rt" })
        {
            var distinct = obs.Where(o => o.HasState).Select(o => o.Axis(axis)).Distinct().OrderBy(v => v).ToList();
            if (distinct.Count < 2) continue;
            int step = int.MaxValue;
            for (int i = 1; i < distinct.Count; i++) step = Math.Min(step, distinct[i] - distinct[i - 1]);
            // A handful of values says nothing about the resolution (e.g. a stick at rest).
            result.Add(new AxisStats { Axis = axis, DistinctValues = distinct.Count, ResolutionObserved = distinct.Count >= 8 ? step : 1 });
        }
        return result;
    }

    private static bool CompareFields(ControllerState expected, ControllerState actual, Dictionary<string, int>? quantum,
        Dictionary<string, int> mismatches, string? skipAxis = null)
    {
        bool ok = true;
        if (expected.Buttons != actual.Buttons) { Count(mismatches, "buttons"); ok = false; }
        foreach (var axis in new[] { "lx", "ly", "rx", "ry", "lt", "rt" })
        {
            if (axis == skipAxis) continue;
            int tol = quantum is not null && quantum.TryGetValue(axis, out var q) && q > 1 ? q : 0;
            if (Math.Abs(Field(expected, axis) - Field(actual, axis)) > tol) { Count(mismatches, axis); ok = false; }
        }
        return ok;
    }

    private static void Count(Dictionary<string, int> d, string key) => d[key] = d.GetValueOrDefault(key) + 1;

    private static int Field(ControllerState s, string axis) => axis switch
    {
        "lx" => s.LeftX, "ly" => s.LeftY, "rx" => s.RightX, "ry" => s.RightY, "lt" => s.LeftTrigger, "rt" => s.RightTrigger, _ => 0,
    };

    /// <summary>Distinct events per second over their own span.</summary>
    private static double Rate(IEnumerable<long> times)
    {
        long min = long.MaxValue, max = long.MinValue;
        int n = 0;
        foreach (var t in times)
        {
            n++;
            if (t < min) min = t;
            if (t > max) max = t;
        }
        return n >= 2 && max > min ? (n - 1) / ((max - min) / 1e9) : 0;
    }

    /// <summary>n, mean, sample stdev, min/max, P50/90/95/99 (linear interpolation), bootstrap CI of the median, histogram, CDF.</summary>
    public static StageStats Describe(List<double> values, SplitMix64 rng, int bootstrap = 1000)
    {
        var sorted = values.OrderBy(v => v).ToArray();
        int n = sorted.Length;
        double mean = sorted.Average();
        double variance = n > 1 ? sorted.Sum(v => (v - mean) * (v - mean)) / (n - 1) : 0;
        var (lo, hi) = MedianCi(sorted, rng, bootstrap);
        return new StageStats
        {
            N = n,
            MeanMs = R(mean),
            StdevMs = R(Math.Sqrt(variance)),
            MinMs = R(sorted[0]),
            MaxMs = R(sorted[^1]),
            P50Ms = R(Percentile(sorted, 0.50)),
            P90Ms = R(Percentile(sorted, 0.90)),
            P95Ms = R(Percentile(sorted, 0.95)),
            P99Ms = R(Percentile(sorted, 0.99)),
            MedianCi95Ms = [R(lo), R(hi)],
            Negative = sorted.Count(v => v < 0),
            Histogram = Histogram(sorted),
            Cdf = Cdf(sorted),
        };
    }

    /// <summary>Percentile with linear interpolation between closest ranks (type 7): h = (n−1)·q.</summary>
    public static double Percentile(IReadOnlyList<double> sorted, double q)
    {
        if (sorted.Count == 0) return double.NaN;
        double h = (sorted.Count - 1) * Math.Clamp(q, 0, 1);
        int lo = (int)Math.Floor(h);
        int hi = Math.Min(sorted.Count - 1, lo + 1);
        return sorted[lo] + (h - lo) * (sorted[hi] - sorted[lo]);
    }

    /// <summary>
    /// Percentile bootstrap of the median: <paramref name="iterations"/> resamples with replacement
    /// (seeded SplitMix64, so the same data and seed always give the same interval); the interval
    /// is the 2.5th and 97.5th percentiles of the resampled medians.
    /// </summary>
    public static (double Low, double High) MedianCi(double[] sorted, SplitMix64 rng, int iterations = 1000)
    {
        int n = sorted.Length;
        if (n == 0) return (double.NaN, double.NaN);
        if (n == 1 || iterations <= 0) return (sorted[0], sorted[0]);
        var medians = new double[iterations];
        var sample = new double[n];
        for (int b = 0; b < iterations; b++)
        {
            for (int i = 0; i < n; i++) sample[i] = sorted[rng.NextInt(n)];
            medians[b] = n % 2 == 1
                ? Select(sample, n / 2)
                : (Select(sample, n / 2 - 1) + Select(sample, n / 2)) / 2;
        }
        Array.Sort(medians);
        return (Percentile(medians, 0.025), Percentile(medians, 0.975));
    }

    /// <summary>k-th smallest (Hoare quickselect, in place, deterministic pivot).</summary>
    private static double Select(double[] a, int k)
    {
        int left = 0, right = a.Length - 1;
        while (left < right)
        {
            double pivot = a[left + (right - left) / 2];
            int i = left, j = right;
            while (i <= j)
            {
                while (a[i] < pivot) i++;
                while (a[j] > pivot) j--;
                if (i <= j)
                {
                    (a[i], a[j]) = (a[j], a[i]);
                    i++;
                    j--;
                }
            }
            if (k <= j) right = j;
            else if (k >= i) left = i;
            else return a[k];
        }
        return a[k];
    }

    /// <summary>√n bins (10..50) from the minimum to P99.5; what lies above goes to Overflow.</summary>
    public static HistogramData Histogram(double[] sorted)
    {
        int n = sorted.Length;
        double min = sorted[0];
        double max = Math.Max(min + 1e-3, Percentile(sorted, 0.995));
        int bins = Math.Clamp((int)Math.Ceiling(Math.Sqrt(n)), 10, 50);
        double width = (max - min) / bins;
        var counts = new int[bins];
        int overflow = 0;
        foreach (var v in sorted)
        {
            if (v > max) { overflow++; continue; }
            counts[Math.Min(bins - 1, (int)((v - min) / width))]++;
        }
        return new HistogramData
        {
            EdgesMs = Enumerable.Range(0, bins + 1).Select(i => R(min + i * width)).ToArray(),
            Counts = counts,
            Overflow = overflow,
        };
    }

    /// <summary>Empirical CDF, at most 200 points: (x_(i), (i+1)/n).</summary>
    public static double[][] Cdf(double[] sorted)
    {
        int n = sorted.Length;
        int points = Math.Min(200, n);
        var result = new double[points][];
        for (int k = 0; k < points; k++)
        {
            int i = points == 1 ? 0 : (int)Math.Round(k * (n - 1) / (double)(points - 1));
            result[k] = [R(sorted[i]), R((i + 1) / (double)n)];
        }
        return result;
    }

    private static double R(double v) => double.IsFinite(v) ? Math.Round(v, 4) : 0;
}

/// <summary>Small seeded generator (SplitMix64): the same seed gives the same stream on every runtime.</summary>
public sealed class SplitMix64(ulong seed)
{
    private ulong _state = seed;

    public ulong Next()
    {
        ulong z = _state += 0x9E3779B97F4A7C15UL;
        z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9UL;
        z = (z ^ (z >> 27)) * 0x94D049BB133111EBUL;
        return z ^ (z >> 31);
    }

    /// <summary>Uniform in [0, n) (Lemire's multiply-shift; the bias is negligible for n ≪ 2^64).</summary>
    public int NextInt(int n) => (int)(((UInt128)Next() * (ulong)n) >> 64);
}
