using System.Buffers.Binary;
using System.Diagnostics;
using System.Net;
using System.Net.Sockets;

namespace Mobilepad.Companion;

/// <summary>
/// Measures the companion's own share of the input latency: a fake phone (as the app does over
/// USB: TCP to 127.0.0.1, states on the control channel) writes a state, and the time until the
/// session hands it to the virtual pad is recorded. Runs the real CompanionServer/PhoneSession
/// in-process with a fake pad, on a free port, without discovery: it never touches a running
/// companion, a phone or ViGEm.
///
///   dotnet run -c Release -- [--count N] [--interval-ms N] [--load] [--nagle]
///
/// --load   busy-spins one normal-priority thread per core (Dolphin keeping the CPU busy).
/// --nagle  leaves Nagle on in the fake phone, like a forwarder that does not set TCP_NODELAY.
/// </summary>
internal static class Program
{
    private static int Main(string[] args)
    {
        int count = Arg(args, "--count", 2000);
        double intervalMs = Arg(args, "--interval-ms", 4);
        bool load = args.Contains("--load");
        bool nagle = args.Contains("--nagle");

        var platform = new FakePlatform();
        using var server = new CompanionServer(platform, preferredPort: 0, discovery: false);

        using var stop = new CancellationTokenSource();
        var spinners = new List<Thread>();
        if (load)
        {
            for (int i = 0; i < Environment.ProcessorCount; i++)
            {
                var t = new Thread(() => { while (!stop.IsCancellationRequested) Thread.SpinWait(1000); }) { IsBackground = true };
                t.Start();
                spinners.Add(t);
            }
        }

        using var phone = new TcpClient(AddressFamily.InterNetwork) { NoDelay = !nagle };
        phone.Connect(IPAddress.Loopback, server.Port);
        var stream = phone.GetStream();

        // Hello as the app sends it to a Mobilepad host: device id (big-endian) + "MBP" + version.
        var hello = new byte[12];
        BinaryPrimitives.WriteUInt64BigEndian(hello, 0x0123456789ABCDEF);
        Protocol.ExtensionMagic.CopyTo(hello, 8);
        hello[11] = Protocol.ExtensionVersion;
        stream.Write(Frame(Protocol.TypeHello, 0, hello));
        ReadReply(stream);
        if (!platform.PadCreated.Wait(5000)) { Console.Error.WriteLine("no pad created"); return 1; }

        var pad = platform.Pad!;
        var latencies = new List<double>(count);
        long intervalTicks = (long)(intervalMs * Stopwatch.Frequency / 1000);
        ushort seq = 1;
        // Warm-up (JIT, thread pool), not measured.
        for (int i = 0; i < 200; i++) SendAndWait(stream, pad, seq++, (ushort)(i & 1), out _);
        for (int i = 0; i < count; i++)
        {
            long next = Stopwatch.GetTimestamp() + intervalTicks;
            if (!SendAndWait(stream, pad, seq++, (ushort)(i & 1), out double ms)) { Console.Error.WriteLine("state lost"); return 1; }
            latencies.Add(ms);
            while (Stopwatch.GetTimestamp() < next) Thread.SpinWait(50);
        }
        stop.Cancel();

        latencies.Sort();
        double P(double q) => latencies[Math.Min(latencies.Count - 1, (int)(q * latencies.Count))];
        Console.WriteLine($"socket write -> pad.Update  ({count} states, every {intervalMs} ms{(load ? ", CPU busy" : "")}{(nagle ? ", Nagle on in sender" : "")})");
        Console.WriteLine($"  mean {latencies.Average():F3} ms  p50 {P(0.50):F3}  p90 {P(0.90):F3}  p99 {P(0.99):F3}  max {latencies[^1]:F3} ms");
        return 0;
    }

    private static bool SendAndWait(NetworkStream stream, FakePad pad, ushort seq, ushort buttons, out double ms)
    {
        var payload = new byte[12];
        BinaryPrimitives.WriteUInt16LittleEndian(payload, buttons);
        BinaryPrimitives.WriteInt16LittleEndian(payload.AsSpan(2), (short)seq);
        long before = pad.Updates;
        long sent = Stopwatch.GetTimestamp();
        stream.Write(Frame(Protocol.TypeState, seq, payload));
        long deadline = sent + Stopwatch.Frequency; // 1 s
        while (Volatile.Read(ref pad.UpdatesField) == before)
        {
            if (Stopwatch.GetTimestamp() > deadline) { ms = 0; return false; }
            Thread.SpinWait(20);
        }
        ms = (pad.LastUpdateTicks - sent) * 1000.0 / Stopwatch.Frequency;
        return true;
    }

    /// <summary>Phone -> PC frame: u16 length | major | minor | u16 sequence | type | payload.</summary>
    private static byte[] Frame(byte type, ushort seq, byte[] payload)
    {
        var p = new byte[Protocol.RxHeaderSize + payload.Length];
        BinaryPrimitives.WriteUInt16LittleEndian(p, (ushort)p.Length);
        p[2] = Protocol.Major;
        p[3] = Protocol.Minor;
        BinaryPrimitives.WriteUInt16LittleEndian(p.AsSpan(4), seq);
        p[6] = type;
        payload.CopyTo(p, Protocol.RxHeaderSize);
        return p;
    }

    private static void ReadReply(NetworkStream stream)
    {
        var len = new byte[2];
        stream.ReadExactly(len);
        var rest = new byte[BinaryPrimitives.ReadUInt16LittleEndian(len) - 2];
        stream.ReadExactly(rest);
    }

    private static T Arg<T>(string[] args, string name, T fallback) where T : IParsable<T>
    {
        int i = Array.IndexOf(args, name);
        return i >= 0 && i + 1 < args.Length && T.TryParse(args[i + 1], null, out var v) ? v : fallback;
    }
}

internal sealed class FakePlatform : IPlatform
{
    public byte OsCode => Protocol.OsWindows;
    public FakePad? Pad;
    public readonly ManualResetEventSlim PadCreated = new();

    public IVirtualPad CreatePad()
    {
        Pad = new FakePad();
        PadCreated.Set();
        return Pad;
    }

    public IKeyboardOutput CreateKeyboard() => new NullKeyboard();

    private sealed class NullKeyboard : IKeyboardOutput
    {
        public void Apply(ReadOnlySpan<byte> payload) { }
        public void ReleaseAll() { }
    }
}

/// <summary>Stands in for ViGEmPad: records when each state is submitted.</summary>
internal sealed class FakePad : IVirtualPad
{
    public long UpdatesField;
    public long LastUpdateTicks;
    public long Updates => Volatile.Read(ref UpdatesField);

    public event Action<byte, byte, byte>? Feedback { add { } remove { } }

    public void Update(ControllerState state)
    {
        LastUpdateTicks = Stopwatch.GetTimestamp();
        Interlocked.Increment(ref UpdatesField);
    }

    public void Dispose() { }
}
