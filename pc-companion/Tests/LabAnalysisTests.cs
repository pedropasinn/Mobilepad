using Mobilepad.Companion;
using Mobilepad.Companion.Lab;
using Xunit;

namespace Mobilepad.Companion.Tests;

/// <summary>
/// Synthetic runs with known timings: the phone clock runs 5 s ahead of the PC; each step goes
/// cmd → state (+0.1 ms) → send (+0.2 ms) → recv (+2 ms, PC) → submit (+0.05 ms) → obs (+1 ms).
/// </summary>
public class LabAnalysisTests
{
    private const long PhoneAhead = 5_000_000_000L;
    private const long T0 = 1_700_000_000_000_000_000L;
    private const double CmdToObsMs = 0.1 + 0.2 + 2 + 0.05 + 1;

    private sealed class Run
    {
        public List<LabRow> Phone = [], Pc = [], Obs = [];
    }

    private static ControllerState Step(int seq, ushort buttons = 0x1000) => new(buttons, 0, 0, Fingerprint.Mark(seq), 0, 0, 0);

    private static Run Build(int steps, Func<int, bool>? lost = null)
    {
        var run = new Run();
        for (int i = 0; i < steps; i++)
        {
            long cmdPc = T0 + i * 10_000_000L;
            var s = Step(i);
            run.Phone.Add(new LabRow(i, Stages.Cmd, cmdPc + PhoneAhead, s));
            run.Phone.Add(new LabRow(i, Stages.State, cmdPc + PhoneAhead + 100_000, s));
            run.Phone.Add(new LabRow(i, Stages.Send, cmdPc + PhoneAhead + 300_000, s));
            if (lost?.Invoke(i) == true) continue;
            run.Pc.Add(new LabRow(-1, Stages.Recv, cmdPc + 2_300_000, s));
            run.Pc.Add(new LabRow(-1, Stages.Submit, cmdPc + 2_350_000, s));
            run.Obs.Add(new LabRow(-1, Stages.Obs, cmdPc + 3_350_000, s));
        }
        // Final release to neutral.
        long end = T0 + steps * 10_000_000L;
        run.Pc.Add(new LabRow(-1, Stages.Recv, end, default));
        run.Pc.Add(new LabRow(-1, Stages.Submit, end + 50_000, default));
        run.Obs.Add(new LabRow(-1, Stages.Obs, end + 1_000_000, default));
        return run;
    }

    private static SyncModel Sync() => new(
        new SyncResult(PhoneAhead, T0 - 1_000_000_000, 400_000, 450_000, 200, 33),
        new SyncResult(PhoneAhead, T0 + 60_000_000_000, 400_000, 450_000, 200, 33));

    private static LabSummary Analyze(Run run, ulong seed = 42) => LabAnalysis.Analyze(new LabInput
    {
        Test = LabTests.Latency, Link = LabLinks.Usb, Mark = "rx",
        Phone = run.Phone, Pc = run.Pc, Obs = run.Obs, ObserverRan = true, Sync = Sync(), BootstrapSeed = seed,
    });

    [Fact]
    public void CleanRun_AllStagesMatchKnownLatencies()
    {
        var s = Analyze(Build(100));
        Assert.Equal(100, s.Loss.Expected);
        Assert.Equal(0, s.Loss.LostBeforeObs);
        Assert.Equal(0, s.Loss.DuplicatesRecv);
        Assert.Equal(0, s.Loss.OutOfOrder);
        Assert.False(s.Loss.Stuck);
        Assert.Equal(0.1, s.Stage("cmd→state")!.P50Ms, 3);
        Assert.Equal(0.2, s.Stage("state→send")!.P50Ms, 3);
        Assert.Equal(2.0, s.Stage("send→recv")!.P50Ms, 3);
        Assert.Equal(0.05, s.Stage("recv→submit")!.P50Ms, 3);
        Assert.Equal(1.0, s.Stage("submit→obs")!.P50Ms, 3);
        Assert.Equal(CmdToObsMs, s.Stage("cmd→obs")!.P50Ms, 3);
        Assert.Equal("estimated", s.Stage("cmd→obs")!.Kind);
        Assert.Equal("measured", s.Stage("recv→submit")!.Kind);
        Assert.Equal(0.2, s.Stage("cmd→obs")!.UncertaintyMs, 3); // RTT min / 2, no drift
        Assert.Equal("cmd→obs", s.Headline.Stage);
    }

    [Fact]
    public void LostPress_DoesNotShiftLaterMatches()
    {
        var s = Analyze(Build(50, lost: i => i == 5 || i == 6));
        Assert.Equal(2, s.Loss.LostBeforeRecv);
        Assert.Equal(2, s.Loss.LostBeforeObs);
        var e2e = s.Stage("cmd→obs")!;
        Assert.Equal(48, e2e.N);
        // Every remaining step keeps its own latency: no step is matched to a neighbour's arrival.
        Assert.Equal(CmdToObsMs, e2e.MinMs, 3);
        Assert.Equal(CmdToObsMs, e2e.MaxMs, 3);
    }

    [Fact]
    public void DuplicatedRecv_IsCountedOnceAndKeepsFirstArrival()
    {
        var run = Build(20);
        var dup = run.Pc.First(r => r.Stage == Stages.Recv && r.State.RightX == Fingerprint.Mark(3));
        run.Pc.Add(dup with { TNs = dup.TNs + 4_000_000 });
        var s = Analyze(run);
        Assert.Equal(1, s.Loss.DuplicatesRecv);
        Assert.Equal(20, s.Loss.Received);
        Assert.Equal(2.0, s.Stage("send→recv")!.MaxMs, 3);
    }

    [Fact]
    public void OutOfOrder_IsDetected()
    {
        var run = Build(20);
        // Step 8 arrives before step 7.
        int i7 = run.Pc.FindIndex(r => r.Stage == Stages.Recv && r.State.RightX == Fingerprint.Mark(7));
        int i8 = run.Pc.FindIndex(r => r.Stage == Stages.Recv && r.State.RightX == Fingerprint.Mark(8));
        var r7 = run.Pc[i7];
        run.Pc[i7] = r7 with { TNs = run.Pc[i8].TNs + 100 };
        var s = Analyze(run);
        Assert.Equal(1, s.Loss.OutOfOrder);
        Assert.Equal(0, s.Loss.LostBeforeRecv);
    }

    [Fact]
    public void StuckButton_AtTheEndIsReported()
    {
        var run = Build(10);
        run.Obs.Add(new LabRow(-1, Stages.Obs, T0 + 999_000_000, new ControllerState(0x2000, 0, 0, 0, 0, 0, 0)));
        var s = Analyze(run);
        Assert.True(s.Loss.Stuck);
        Assert.Equal("0x2000", s.Loss.FinalObsButtons);
    }

    [Fact]
    public void Integrity_FlagsTheFieldThatChanged()
    {
        var run = Build(10);
        int i = run.Pc.FindIndex(r => r.Stage == Stages.Recv && r.State.RightX == Fingerprint.Mark(4));
        run.Pc[i] = run.Pc[i] with { State = run.Pc[i].State with { Buttons = 0x3000 } };
        var s = Analyze(run);
        Assert.Equal(10, s.Integrity.ComparedRecv);
        Assert.Equal(9, s.Integrity.IntactRecv);
        Assert.Equal(1, s.Integrity.MismatchRecv["buttons"]);
    }

    [Fact]
    public void Percentiles_UseLinearInterpolation()
    {
        var v = Enumerable.Range(1, 10).Select(x => (double)x).ToList();
        Assert.Equal(5.5, LabAnalysis.Percentile(v, 0.50), 9);
        Assert.Equal(9.1, LabAnalysis.Percentile(v, 0.90), 9);
        Assert.Equal(9.55, LabAnalysis.Percentile(v, 0.95), 9);
        Assert.Equal(9.91, LabAnalysis.Percentile(v, 0.99), 9);
        Assert.Equal(1, LabAnalysis.Percentile(v, 0), 9);
        Assert.Equal(10, LabAnalysis.Percentile(v, 1), 9);
    }

    [Fact]
    public void Describe_MeanStdevMinMax()
    {
        var s = LabAnalysis.Describe([2, 4, 4, 4, 5, 5, 7, 9], new SplitMix64(1), 200);
        Assert.Equal(5, s.MeanMs, 9);
        Assert.Equal(Math.Sqrt(32.0 / 7), s.StdevMs, 3);
        Assert.Equal(2, s.MinMs);
        Assert.Equal(9, s.MaxMs);
        Assert.Equal(8, s.Histogram.Counts.Sum() + s.Histogram.Overflow);
        Assert.Equal(1.0, s.Cdf[^1][1], 9);
    }

    [Fact]
    public void Bootstrap_IsDeterministicForASeed()
    {
        var rng = new Random(7);
        var data = Enumerable.Range(0, 500).Select(_ => 3 + rng.NextDouble() * 4).OrderBy(x => x).ToArray();
        var a = LabAnalysis.MedianCi(data, new SplitMix64(123), 1000);
        var b = LabAnalysis.MedianCi(data, new SplitMix64(123), 1000);
        var c = LabAnalysis.MedianCi(data, new SplitMix64(124), 1000);
        Assert.Equal(a, b);
        Assert.NotEqual(a, c);
        double median = LabAnalysis.Percentile(data, 0.5);
        Assert.True(a.Low <= median && median <= a.High);
        Assert.True(a.High - a.Low < 1.0);
        // Same through the whole analysis.
        Assert.Equal(Analyze(Build(40), 9).Stage("cmd→obs")!.MedianCi95Ms, Analyze(Build(40), 9).Stage("cmd→obs")!.MedianCi95Ms);
    }

    [Fact]
    public void Fingerprint_RoundTripsEveryMark()
    {
        Assert.Equal(1000, Fingerprint.Mark(0));
        Assert.Equal(8919, Fingerprint.Mark(1));
        Assert.Equal(1000 + (int)(12345L * 7919 % 30000), Fingerprint.Mark(12345));
        var seen = new HashSet<int>();
        for (int s = 0; s < Fingerprint.Period; s++)
        {
            int m = Fingerprint.Mark(s);
            Assert.InRange(m, 1000, 30999);
            Assert.True(seen.Add(m));
            Assert.Equal(s, Fingerprint.Decode(m));
        }
        Assert.Equal(-1, Fingerprint.Decode(0));      // neutral
        Assert.Equal(-1, Fingerprint.Decode(999));
        Assert.Equal(-1, Fingerprint.Decode(31000));
    }

    [Fact]
    public void Fingerprint_UnwrapsLongRunsAndToleratesCoarseAxes()
    {
        // Beyond 30000 steps the mark repeats; the previous seq picks the right lap.
        Assert.Equal(30005, Fingerprint.Decode(Fingerprint.Mark(30005), hint: 30004));
        Assert.Equal(5, Fingerprint.Decode(Fingerprint.Mark(5), hint: 4));
        // Reduced resolution (e.g. 12-bit Bluetooth): nearest mark within ±tolerance, closest to the hint.
        int seq = 777;
        int coarse = Fingerprint.Mark(seq) / 16 * 16;
        Assert.Equal(seq, Fingerprint.Decode(coarse, tolerance: 16, hint: seq - 1));
        Assert.Equal(seq, Fingerprint.Decode(coarse, tolerance: 16, accept: s => s == seq));
    }

    [Fact]
    public void AssignSeqs_DeducesSeqFromTheChosenAxis()
    {
        var rows = new List<LabRow>
        {
            new(-1, Stages.Recv, 1, new ControllerState(0, 0, 0, 0, Fingerprint.Mark(9), 0, 0)),
            new(-1, Stages.Recv, 2, default),
            new(-1, Stages.Obs, 3, default, HasState: false),
        };
        var ry = LabAnalysis.AssignSeqs(rows, "ry");
        Assert.Equal([9, -1, -1], ry.Select(r => r.Seq));
        Assert.Equal(-1, LabAnalysis.AssignSeqs(rows, "rx")[0].Seq);
    }

    [Fact]
    public void Csv_RoundTripsRowsAndEmptyStates()
    {
        var path = Path.Combine(Path.GetTempPath(), $"lab-test-{Guid.NewGuid():N}.csv");
        try
        {
            var rows = new List<LabRow>
            {
                new(3, Stages.Recv, 123456789, new ControllerState(0x1000, -32768, 32767, 1000, -5, 0, 255)),
                new(-1, Stages.Submit, 123456999, default, HasState: false),
            };
            LabCsv.WriteRows(path, rows);
            var text = File.ReadAllText(path);
            Assert.StartsWith("seq,stage,t_ns,buttons,lx,ly,rx,ry,lt,rt\n3,recv,123456789,0x1000,-32768,32767,1000,-5,0,255\n", text);
            var back = LabCsv.Parse(text);
            Assert.Equal(rows[0], back[0]);
            Assert.False(back[1].HasState);
            // The phone's column order may differ; parsing goes by header.
            var phone = LabCsv.Parse("t_ns,seq,stage,buttons,lx,ly,rx,ry,lt,rt\n55,7,cmd,0x0010,,,8919,,,\n");
            Assert.Equal(7, phone[0].Seq);
            Assert.Equal(0x0010, phone[0].State.Buttons);
            Assert.Equal(8919, phone[0].State.RightX);
        }
        finally
        {
            File.Delete(path);
        }
    }

    [Fact]
    public void ClockSync_RecoversOffsetFromFastestExchanges()
    {
        const long offset = 123_456_789;
        var rng = new Random(3);
        var samples = new List<(long, long, long)>();
        long t = 1_000_000_000;
        for (int i = 0; i < 200; i++)
        {
            long up = 200_000 + rng.Next(0, 3_000_000);   // PC → phone, asymmetric jitter
            long down = 200_000 + rng.Next(0, 3_000_000); // phone → PC
            long t0 = t;
            long phone = t0 + up + offset;
            long t1 = t0 + up + down;
            samples.Add((t0, phone, t1));
            t += 5_000_000;
        }
        var r = ClockSync.Reduce(samples)!;
        Assert.Equal(33, r.Used);
        Assert.True(Math.Abs(r.OffsetNs - offset) <= r.RttMinNs, $"offset error {r.OffsetNs - offset} > rtt {r.RttMinNs}");
        var model = new SyncModel(r, r with { OffsetNs = r.OffsetNs + 1_000_000, AtNs = r.AtNs + 10_000_000_000 });
        Assert.Equal(1_000_000, model.DriftNs);
        Assert.Equal(r.RttMinNs / 2 + 1_000_000, model.UncertaintyNs);
        Assert.Equal(100, model.DriftPpm, 6);
    }

    [Fact]
    public void Battery_ParsesDumpsys()
    {
        var text = "Current Battery Service state:\n  AC powered: false\n  level: 87\n  voltage: 4123\n  temperature: 312\n  current_now_sysfs: -345000\n";
        var m = ResourceSampler.ParseBattery(text).ToDictionary(x => x.Metric, x => x.Value);
        Assert.Equal(87, m["battery_level_pct"]);
        Assert.Equal(31.2, m["battery_temp_c"], 6);
        Assert.Equal(-345000, m["battery_current_now_ua"]);
    }

    [Fact]
    public void Compare_ReportsDeltas()
    {
        var a = Analyze(Build(30));
        var slow = Build(30);
        slow.Obs = slow.Obs.Select(o => o with { TNs = o.TNs + 1_000_000 }).ToList();
        var b = Analyze(slow);
        var rows = LabHistory.Compare(a, b);
        var p50 = rows.First(r => r.Metric == "cmd→obs P50 (ms)");
        Assert.Equal(1.0, p50.Delta!.Value, 3);
        Assert.Equal("estimated", p50.Kind);
    }
}
