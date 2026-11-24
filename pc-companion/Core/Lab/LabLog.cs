using System.Diagnostics;

namespace Mobilepad.Companion.Lab;

/// <summary>
/// recv/submit log of the companion (SPEC §3, §5). Off by default: the receive path only reads
/// <see cref="Active"/> (one volatile load) when no lab run is going. While a run is active
/// each record is a timestamp plus a copy of the state into a buffer allocated (and touched)
/// before the run, so nothing allocates on the receive thread. The seq is not computed here:
/// it is deduced from the mark when the run is written (pc.csv).
/// </summary>
public static class LabLog
{
    public const byte StageRecv = 1;
    public const byte StageSubmit = 2;

    private struct Entry
    {
        public long TNs;
        public byte Stage;
        public ControllerState State;
    }

    public readonly record struct LinkEvent(long TNs, string Kind, string Detail);

    /// <summary>Checked on the receive path; true only while a lab run records.</summary>
    public static volatile bool Active;

    private static Entry[] _buffer = [];
    private static int _next;
    private static long _dropped;
    private static readonly List<LinkEvent> Links = [];
    private static Entry[]? _liveBuffer;
    private static int _liveScanned, _liveRecv, _liveSubmit;

    public static int Capacity => _buffer.Length;
    public static int Count => Math.Min(Volatile.Read(ref _next), _buffer.Length);
    public static long Dropped => Interlocked.Read(ref _dropped);

    /// <summary>Allocates and pre-faults the buffer, then turns recording on.</summary>
    public static void Start(int capacity)
    {
        Active = false;
        var buffer = new Entry[Math.Max(1024, capacity)];
        // Touch every page now, so the first records do not take page faults on the receive thread.
        for (int i = 0; i < buffer.Length; i += 128) buffer[i].TNs = 0;
        _buffer = buffer;
        Volatile.Write(ref _next, 0);
        Interlocked.Exchange(ref _dropped, 0);
        lock (Links) Links.Clear();
        Active = true;
    }

    public static void Record(byte stage, in ControllerState state)
    {
        long t = LabTime.NowNs();
        var buffer = _buffer;
        int i = Interlocked.Increment(ref _next) - 1;
        if ((uint)i < (uint)buffer.Length)
        {
            ref var e = ref buffer[i];
            e.TNs = t;
            e.Stage = stage;
            e.State = state;
        }
        else
        {
            Interlocked.Increment(ref _dropped);
        }
    }

    /// <summary>Connection events seen by the companion during a run (reconnect test).</summary>
    public static void Link(string kind, string detail)
    {
        if (!Active) return;
        var e = new LinkEvent(LabTime.NowNs(), kind, detail);
        lock (Links) Links.Add(e);
    }

    /// <summary>Stops recording and returns the rows with seq = −1 (deduced later from the mark).</summary>
    public static (List<LabRow> Rows, List<LinkEvent> Links) Stop()
    {
        Active = false;
        // Let a writer that already passed the Active check finish its copy.
        Thread.Sleep(20);
        int n = Count;
        var rows = new List<LabRow>(n);
        for (int i = 0; i < n; i++)
        {
            ref var e = ref _buffer[i];
            if (e.TNs == 0) continue;
            rows.Add(new LabRow(-1, e.Stage == StageRecv ? Stages.Recv : Stages.Submit, e.TNs, e.State));
        }
        List<LinkEvent> links;
        lock (Links) links = [.. Links];
        _buffer = [];
        return (rows, links);
    }

    /// <summary>
    /// Live numbers while a run records: counts of recv/submit, the seq of the newest recv (by
    /// the mark) and the median recv→submit over the most recent pairs, in microseconds.
    /// Reads a buffer that is still being written: good enough for a progress display.
    /// </summary>
    public static (int Recv, int Submit, int LastSeq, double RecvToSubmitUs) Live(string mark, int window = 400)
    {
        var buffer = _buffer;
        int n = Math.Min(Volatile.Read(ref _next), buffer.Length);
        int recv = 0, submit = 0, lastSeq = -1;
        var deltas = new List<double>(window);
        long pendingRecv = 0;
        int from = Math.Max(0, n - window * 2);
        for (int i = from; i < n; i++)
        {
            var e = buffer[i];
            if (e.Stage == StageRecv) pendingRecv = e.TNs;
            else if (e.Stage == StageSubmit && pendingRecv != 0)
            {
                deltas.Add((e.TNs - pendingRecv) / 1000.0);
                pendingRecv = 0;
            }
        }
        for (int i = n - 1; i >= 0 && i >= n - 64; i--)
        {
            var e = buffer[i];
            if (e.Stage != StageRecv) continue;
            int value = mark == "ry" ? e.State.RightY : e.State.RightX;
            lastSeq = Fingerprint.DecodeModulo(value);
            if (lastSeq >= 0) break;
        }
        // Totals: incremental scan (only the caller, one UI timer, touches these).
        if (!ReferenceEquals(buffer, _liveBuffer))
        {
            _liveBuffer = buffer;
            _liveScanned = _liveRecv = _liveSubmit = 0;
        }
        for (; _liveScanned < n; _liveScanned++)
        {
            byte stage = buffer[_liveScanned].Stage;
            if (stage == StageRecv) _liveRecv++;
            else if (stage == StageSubmit) _liveSubmit++;
        }
        recv = _liveRecv;
        submit = _liveSubmit;
        deltas.Sort();
        return (recv, submit, lastSeq, deltas.Count > 0 ? deltas[deltas.Count / 2] : double.NaN);
    }

    /// <summary>
    /// Cost of one record (timestamp + copy), measured on a scratch buffer before the run and
    /// written to meta.json. Leaves the log off.
    /// </summary>
    public static double MeasureCostNs(int iterations = 50_000)
    {
        Start(iterations);
        var state = new ControllerState(0x1000, 1, 2, 3, 4, 5, 6);
        for (int i = 0; i < 2000; i++) Record(StageRecv, state); // JIT warm-up
        Volatile.Write(ref _next, 0);
        long t0 = Stopwatch.GetTimestamp();
        for (int i = 0; i < iterations; i++) Record(StageRecv, state);
        long t1 = Stopwatch.GetTimestamp();
        Active = false;
        _buffer = [];
        Volatile.Write(ref _next, 0);
        return (t1 - t0) * 1e9 / Stopwatch.Frequency / iterations;
    }
}
