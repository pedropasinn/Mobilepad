using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Runtime.Versioning;

namespace Mobilepad.Companion.Lab;

/// <summary>
/// Stage <c>obs</c>: reads the controller the way a game does and records every change with
/// its t_ns. Rows with HasState = false mean "controller not present" (empty columns in obs.csv).
/// </summary>
public interface ILabObserver : IDisposable
{
    /// <summary>"xinput" or "wgi".</summary>
    string Kind { get; }

    /// <summary>Which device is being read (slot, name), for meta.json.</summary>
    string Device { get; }

    /// <summary>Axis resolution the observer itself introduces (1 = full 16-bit).</summary>
    int Quantum { get; }

    void Start(int capacity);

    /// <summary>Stops polling and returns the recorded changes.</summary>
    List<LabRow> Stop();

    int Count { get; }
    long Polls { get; }
    double PollHz { get; }
}

/// <summary>
/// Polls on its own Highest-priority thread as fast as it can (yielding only to ready threads on
/// the same core) and records state changes into a pre-allocated buffer.
/// </summary>
public abstract class PollingObserver : ILabObserver
{
    private LabRow[] _rows = [];
    private int _count;
    private long _polls;
    private long _startTicks, _stopTicks;
    private volatile bool _run;
    private Thread? _thread;

    public abstract string Kind { get; }
    public abstract string Device { get; }
    public virtual int Quantum => 1;
    public int Count => Volatile.Read(ref _count);
    public long Polls => Interlocked.Read(ref _polls);

    public double PollHz
    {
        get
        {
            long end = _run ? Stopwatch.GetTimestamp() : _stopTicks;
            double seconds = (end - _startTicks) / (double)Stopwatch.Frequency;
            return seconds > 0 ? Polls / seconds : 0;
        }
    }

    /// <summary>One read. Returns false when the controller is not present.</summary>
    protected abstract bool Read(out ControllerState state);

    protected virtual void OnStart() { }

    public void Start(int capacity)
    {
        _rows = new LabRow[Math.Max(1024, capacity)];
        _count = 0;
        _polls = 0;
        OnStart();
        _run = true;
        _startTicks = Stopwatch.GetTimestamp();
        _thread = new Thread(Loop) { IsBackground = true, Name = $"Mobilepad lab observer ({Kind})", Priority = ThreadPriority.Highest };
        _thread.Start();
    }

    private void Loop()
    {
        bool havePrevious = false, previousPresent = false;
        ControllerState previous = default;
        while (_run)
        {
            bool present = Read(out var state);
            long t = LabTime.NowNs();
            _polls++;
            if (!havePrevious || present != previousPresent || (present && state != previous))
            {
                int i = _count;
                if (i < _rows.Length)
                {
                    _rows[i] = new LabRow(-1, Stages.Obs, t, present ? state : default, present);
                    Volatile.Write(ref _count, i + 1);
                }
                havePrevious = true;
                previousPresent = present;
                previous = state;
            }
            if (!present) Thread.Sleep(1);
            else Thread.Yield();
        }
    }

    public List<LabRow> Stop()
    {
        _run = false;
        _thread?.Join(1000);
        _stopTicks = Stopwatch.GetTimestamp();
        return _rows.Take(Count).ToList();
    }

    public virtual void Dispose()
    {
        _run = false;
    }
}

/// <summary>
/// XInput observer for USB and Wi-Fi: the ViGEm pad's slot, read with XInputGetState (what most
/// PC games call). Slot −1: every slot is read until one shows a fingerprint mark, then that
/// slot is kept.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class XInputObserver(int slot, string mark) : PollingObserver
{
    private int _slot = slot;
    private readonly bool _auto = slot is < 0 or > 3;

    public override string Kind => "xinput";
    public override string Device => _slot is >= 0 and <= 3 ? $"XInput slot {_slot + 1}" : "XInput (slot not found)";

    protected override void OnStart()
    {
        if (_auto) _slot = -1;
    }

    protected override bool Read(out ControllerState state)
    {
        state = default;
        if (_slot < 0)
        {
            // Auto: lock on the first slot whose mark axis carries a valid fingerprint.
            for (uint i = 0; i < 4; i++)
            {
                if (XInputGetState(i, out var s) != 0) continue;
                int value = mark == "ry" ? s.Gamepad.ThumbRY : s.Gamepad.ThumbRX;
                if (Fingerprint.DecodeModulo(value) < 0) continue;
                _slot = (int)i;
                state = Convert(s);
                return true;
            }
            return false;
        }
        if (XInputGetState((uint)_slot, out var x) != 0) return false;
        state = Convert(x);
        return true;
    }

    private static ControllerState Convert(in XInputState s) => new(
        s.Gamepad.Buttons, s.Gamepad.ThumbLX, s.Gamepad.ThumbLY, s.Gamepad.ThumbRX, s.Gamepad.ThumbRY,
        s.Gamepad.LeftTrigger, s.Gamepad.RightTrigger);

    [StructLayout(LayoutKind.Sequential)]
    private struct XInputGamepad
    {
        public ushort Buttons;
        public byte LeftTrigger;
        public byte RightTrigger;
        public short ThumbLX;
        public short ThumbLY;
        public short ThumbRX;
        public short ThumbRY;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct XInputState
    {
        public uint PacketNumber;
        public XInputGamepad Gamepad;
    }

    [DllImport("xinput1_4.dll", EntryPoint = "XInputGetState")]
    private static extern uint XInputGetState(uint userIndex, out XInputState state);
}
