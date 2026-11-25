using System.Globalization;

namespace Mobilepad.Companion.Lab;

/// <summary>Wall-clock UTC nanoseconds since the Unix epoch (labs/SPEC.md §1).</summary>
public static class LabTime
{
    private static readonly long EpochTicks = DateTime.UnixEpoch.Ticks;

    /// <summary>
    /// DateTime.UtcNow is GetSystemTimePreciseAsFileTime on Windows (100 ns ticks) and
    /// CLOCK_REALTIME elsewhere: the same clock family as the phone's WallClock.
    /// </summary>
    public static long NowNs() => (DateTime.UtcNow.Ticks - EpochTicks) * 100;

    public static DateTime ToUtc(long ns) => new(EpochTicks + ns / 100, DateTimeKind.Utc);
}

/// <summary>
/// State fingerprint (SPEC §2): step <c>seq</c> is written to the mark axis as
/// <c>1000 + (seq * 7919) % 30000</c>. 7919 is coprime with 30000, so the map is a bijection of
/// 0..29999 onto 1000..30999; longer runs are unwrapped against the previous seq.
/// </summary>
public static class Fingerprint
{
    public const int Base = 1000;
    public const int Multiplier = 7919;
    public const int Period = 30000;

    private static readonly int[] Inverse = BuildInverse();

    public static short Mark(int seq) => (short)(Base + (int)((long)seq * Multiplier % Period));

    /// <summary>seq modulo 30000 for an exact mark value, or −1 when the value is not a mark.</summary>
    public static int DecodeModulo(int value) => value >= Base && value < Base + Period ? Inverse[value - Base] : -1;

    /// <summary>
    /// Decodes a mark read back from some stage.
    /// <paramref name="tolerance"/>: accept marks within ±tolerance of the value (axes with
    /// reduced resolution, Bluetooth HID); among the candidates the one whose seq is closest
    /// to <paramref name="hint"/> wins (then the closest value). <paramref name="hint"/> also
    /// unwraps runs longer than 30000 steps. <paramref name="accept"/> filters candidates
    /// (e.g. only seqs the phone actually commanded). Returns −1 when nothing matches.
    /// </summary>
    public static int Decode(int value, int tolerance = 0, int hint = -1, Func<int, bool>? accept = null)
    {
        int best = -1;
        long bestKey = long.MaxValue;
        for (int v = value - tolerance; v <= value + tolerance; v++)
        {
            int m = DecodeModulo(v);
            if (m < 0) continue;
            int seq = Unwrap(m, hint);
            if (accept is not null && !accept(seq)) continue;
            long valueDistance = Math.Abs(v - value);
            long key = hint < 0 ? valueDistance : Math.Abs((long)seq - hint) * 65536 + valueDistance;
            if (key < bestKey)
            {
                bestKey = key;
                best = seq;
            }
        }
        return best;
    }

    /// <summary>The seq congruent to <paramref name="modulo"/> (mod 30000) closest to <paramref name="hint"/>.</summary>
    public static int Unwrap(int modulo, int hint)
    {
        if (hint < 0) return modulo;
        long k = (long)Math.Round((hint - (double)modulo) / Period);
        long seq = modulo + k * Period;
        if (seq < 0) seq += Period;
        return (int)Math.Min(int.MaxValue, seq);
    }

    private static int[] BuildInverse()
    {
        var inverse = new int[Period];
        for (int s = 0; s < Period; s++) inverse[(int)((long)s * Multiplier % Period)] = s;
        return inverse;
    }
}

/// <summary>Stage names (SPEC §3).</summary>
public static class Stages
{
    public const string Cmd = "cmd";
    public const string State = "state";
    public const string Send = "send";
    public const string Recv = "recv";
    public const string SubmitStart = "submit_start";
    public const string Submit = "submit";
    public const string Obs = "obs";

    public static readonly string[] Order = [Cmd, State, Send, Recv, SubmitStart, Submit, Obs];
}

/// <summary>Test names accepted by the app's LabRunner (SPEC §4).</summary>
public static class LabTests
{
    public const string Latency = "latency";
    public const string Multi = "multi";
    public const string Analog = "analog";
    public const string Throughput = "throughput";
    public const string Reliability = "reliability";
    public const string Reconnect = "reconnect";

    public static readonly string[] All = [Latency, Multi, Analog, Throughput, Reliability, Reconnect];

    /// <summary>Axis carrying the fingerprint: ry when the test needs the right stick free of marks (analog).</summary>
    public static string MarkFor(string test) => test == Analog ? "ry" : "rx";
}

public static class LabLinks
{
    public const string Usb = "usb";
    public const string Wifi = "wifi";
    public const string Bluetooth = "bt";

    public static readonly string[] All = [Usb, Wifi, Bluetooth];
}

/// <summary>One CSV row of phone.csv / pc.csv / obs.csv. <see cref="HasState"/> false = empty state columns.</summary>
public readonly record struct LabRow(int Seq, string Stage, long TNs, ControllerState State, bool HasState = true)
{
    public int Axis(string name) => name switch
    {
        "lx" => State.LeftX,
        "ly" => State.LeftY,
        "rx" => State.RightX,
        "ry" => State.RightY,
        "lt" => State.LeftTrigger,
        "rt" => State.RightTrigger,
        _ => 0,
    };
}

/// <summary>Reads and writes the run CSVs (SPEC §3): buttons hex, signed axes, 0..255 triggers, empty = n/a.</summary>
public static class LabCsv
{
    public const string RowHeader = "seq,stage,t_ns,buttons,lx,ly,rx,ry,lt,rt";
    public const string ObsHeader = "t_ns,stage,buttons,lx,ly,rx,ry,lt,rt";

    public static void WriteRows(string path, IEnumerable<LabRow> rows)
    {
        using var w = new StreamWriter(path, false, new System.Text.UTF8Encoding(false), 1 << 16);
        w.Write(RowHeader);
        w.Write('\n');
        foreach (var r in rows)
        {
            w.Write(r.Seq.ToString(CultureInfo.InvariantCulture));
            w.Write(',');
            w.Write(r.Stage);
            w.Write(',');
            w.Write(r.TNs.ToString(CultureInfo.InvariantCulture));
            w.Write(',');
            WriteState(w, r);
            w.Write('\n');
        }
    }

    public static void WriteObs(string path, IEnumerable<LabRow> rows)
    {
        using var w = new StreamWriter(path, false, new System.Text.UTF8Encoding(false), 1 << 16);
        w.Write(ObsHeader);
        w.Write('\n');
        foreach (var r in rows)
        {
            w.Write(r.TNs.ToString(CultureInfo.InvariantCulture));
            w.Write(',');
            w.Write(Stages.Obs);
            w.Write(',');
            WriteState(w, r);
            w.Write('\n');
        }
    }

    private static void WriteState(TextWriter w, in LabRow r)
    {
        if (!r.HasState)
        {
            w.Write(",,,,,,");
            return;
        }
        var s = r.State;
        w.Write("0x");
        w.Write(s.Buttons.ToString("X4", CultureInfo.InvariantCulture));
        w.Write(',');
        w.Write(s.LeftX.ToString(CultureInfo.InvariantCulture)); w.Write(',');
        w.Write(s.LeftY.ToString(CultureInfo.InvariantCulture)); w.Write(',');
        w.Write(s.RightX.ToString(CultureInfo.InvariantCulture)); w.Write(',');
        w.Write(s.RightY.ToString(CultureInfo.InvariantCulture)); w.Write(',');
        w.Write(s.LeftTrigger.ToString(CultureInfo.InvariantCulture)); w.Write(',');
        w.Write(s.RightTrigger.ToString(CultureInfo.InvariantCulture));
    }

    /// <summary>Parses phone.csv / pc.csv / obs.csv by header names (column order may vary; obs.csv has no seq).</summary>
    public static List<LabRow> Parse(string text)
    {
        var rows = new List<LabRow>();
        using var reader = new StringReader(text);
        var header = reader.ReadLine();
        if (header is null) return rows;
        var cols = header.Trim().TrimStart('﻿').Split(',').Select(c => c.Trim().ToLowerInvariant()).ToArray();
        int Col(string n) => Array.IndexOf(cols, n);
        int cSeq = Col("seq"), cStage = Col("stage"), cT = Col("t_ns"), cB = Col("buttons"),
            cLx = Col("lx"), cLy = Col("ly"), cRx = Col("rx"), cRy = Col("ry"), cLt = Col("lt"), cRt = Col("rt");
        if (cT < 0) return rows;
        string? line;
        while ((line = reader.ReadLine()) is not null)
        {
            line = line.Trim();
            if (line.Length == 0) continue;
            var f = line.Split(',');
            string Get(int i) => i >= 0 && i < f.Length ? f[i].Trim() : "";
            if (!long.TryParse(Get(cT), NumberStyles.Integer, CultureInfo.InvariantCulture, out var t)) continue;
            int seq = int.TryParse(Get(cSeq), NumberStyles.Integer, CultureInfo.InvariantCulture, out var sq) ? sq : -1;
            var stage = cStage >= 0 ? Get(cStage) : Stages.Obs;
            var buttonsText = Get(cB);
            bool hasState = buttonsText.Length > 0 || Get(cRx).Length > 0 || Get(cLx).Length > 0;
            var state = new ControllerState(
                ParseButtons(buttonsText),
                ParseShort(Get(cLx)), ParseShort(Get(cLy)), ParseShort(Get(cRx)), ParseShort(Get(cRy)),
                ParseByte(Get(cLt)), ParseByte(Get(cRt)));
            rows.Add(new LabRow(seq, stage, t, state, hasState));
        }
        return rows;
    }

    public static ushort ParseButtons(string s)
    {
        if (s.Length == 0) return 0;
        if (s.StartsWith("0x", StringComparison.OrdinalIgnoreCase))
            return ushort.TryParse(s.AsSpan(2), NumberStyles.HexNumber, CultureInfo.InvariantCulture, out var h) ? h : (ushort)0;
        return int.TryParse(s, NumberStyles.Integer, CultureInfo.InvariantCulture, out var d) ? (ushort)d : (ushort)0;
    }

    private static short ParseShort(string s) =>
        int.TryParse(s, NumberStyles.Integer, CultureInfo.InvariantCulture, out var v) ? (short)Math.Clamp(v, short.MinValue, short.MaxValue) : (short)0;

    private static byte ParseByte(string s) =>
        int.TryParse(s, NumberStyles.Integer, CultureInfo.InvariantCulture, out var v) ? (byte)Math.Clamp(v, 0, 255) : (byte)0;
}
