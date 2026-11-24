using System.Runtime.Versioning;
using Windows.Gaming.Input;

namespace Mobilepad.Companion.Lab;

/// <summary>
/// Bluetooth observer: the phone is a HID gamepad (android bt/HidDescriptor.kt), read through
/// Windows.Gaming.Input.RawGameController as a game would. The report is mapped back to the
/// XUSB layout of the CSVs: HID button slots → XUSB bits, hat → d-pad, axes X/Y/Z/Rz →
/// lx/ly/rx/ry (Y axes un-inverted), Brake/Accelerator → lt/rt.
/// The ViGEm pads (Xbox 360, 045E:028E) are skipped; a controller named "Mobilepad" is preferred.
/// </summary>
[SupportedOSPlatform("windows10.0.16299.0")]
public sealed class WgiObserver : PollingObserver
{
    private RawGameController? _controller;
    private bool[] _buttons = [];
    private GameControllerSwitchPosition[] _switches = [];
    private double[] _axes = [];
    private long _nextSearch;
    private string _device = "Windows.Gaming.Input (no Bluetooth gamepad found)";

    public override string Kind => "wgi";
    public override string Device => _device;

    /// <summary>HID button slot (0-based) → XUSB bit, inverse of HidDescriptor.BUTTON_SLOTS.</summary>
    private static readonly (int Slot, ushort Bit)[] ButtonMap =
    [
        (0, 0x1000), (1, 0x2000), (3, 0x4000), (4, 0x8000), // A B X Y
        (6, 0x0100), (7, 0x0200),                           // LB RB
        (10, 0x0020), (11, 0x0010), (12, 0x0400),           // Back Start Guide
        (13, 0x0040), (14, 0x0080),                         // LThumb RThumb
    ];

    /// <summary>Any non-Xbox gamepad present now (for the "connect over Bluetooth first" check).</summary>
    public static string? FindName() => Pick()?.DisplayName;

    private static RawGameController? Pick()
    {
        var all = RawGameController.RawGameControllers
            .Where(c => !(c.HardwareVendorId == 0x045E && c.HardwareProductId == 0x028E))
            .Where(c => c.AxisCount >= 4)
            .ToList();
        return all.FirstOrDefault(c => (c.DisplayName ?? "").Contains("Mobilepad", StringComparison.OrdinalIgnoreCase)) ?? all.FirstOrDefault();
    }

    private volatile bool _lost;

    protected override void OnStart()
    {
        _controller = null;
        _nextSearch = 0;
        RawGameController.RawGameControllerRemoved -= OnRemoved;
        RawGameController.RawGameControllerRemoved += OnRemoved;
    }

    private void OnRemoved(object? sender, RawGameController removed)
    {
        if (ReferenceEquals(removed, _controller) || removed.NonRoamableId == _controller?.NonRoamableId) _lost = true;
    }

    public override void Dispose()
    {
        RawGameController.RawGameControllerRemoved -= OnRemoved;
        base.Dispose();
    }

    protected override bool Read(out ControllerState state)
    {
        state = default;
        if (_controller is null || _lost)
        {
            _controller = null;
            // The list changes on (re)connection; looking it up every poll would be wasteful.
            long now = Environment.TickCount64;
            if (now < _nextSearch) return false;
            _nextSearch = now + 50;
            _controller = Pick();
            if (_controller is null) return false;
            _lost = false;
            _buttons = new bool[_controller.ButtonCount];
            _switches = new GameControllerSwitchPosition[_controller.SwitchCount];
            _axes = new double[_controller.AxisCount];
            _device = $"{_controller.DisplayName} ({_controller.HardwareVendorId:X4}:{_controller.HardwareProductId:X4}), " +
                      $"{_controller.ButtonCount} buttons, {_controller.AxisCount} axes";
        }
        try
        {
            _controller.GetCurrentReading(_buttons, _switches, _axes);
        }
        catch (Exception)
        {
            _controller = null;
            return false;
        }
        state = Map(_buttons, _switches, _axes);
        return true;
    }

    public static ControllerState Map(bool[] buttons, GameControllerSwitchPosition[] switches, double[] axes)
    {
        ushort bits = 0;
        foreach (var (slot, bit) in ButtonMap)
            if (slot < buttons.Length && buttons[slot]) bits |= bit;
        if (switches.Length > 0)
        {
            bits |= switches[0] switch
            {
                GameControllerSwitchPosition.Up => 0x0001,
                GameControllerSwitchPosition.UpRight => 0x0001 | 0x0008,
                GameControllerSwitchPosition.Right => 0x0008,
                GameControllerSwitchPosition.DownRight => 0x0002 | 0x0008,
                GameControllerSwitchPosition.Down => 0x0002,
                GameControllerSwitchPosition.DownLeft => 0x0002 | 0x0004,
                GameControllerSwitchPosition.Left => 0x0004,
                GameControllerSwitchPosition.UpLeft => 0x0001 | 0x0004,
                _ => 0,
            };
        }
        short Axis(int i) => i < axes.Length ? (short)Math.Clamp(Math.Round(axes[i] * 65535) - 32768, short.MinValue, short.MaxValue) : (short)0;
        // HID Y grows downwards; the phone sent -y-1.
        short Unflip(short v) => (short)Math.Clamp(-v - 1, short.MinValue, short.MaxValue);
        byte Trigger(int i) => i < axes.Length ? (byte)Math.Clamp(Math.Round(axes[i] * 255), 0, 255) : (byte)0;
        return new ControllerState(bits, Axis(0), Unflip(Axis(1)), Axis(2), Unflip(Axis(3)), Trigger(4), Trigger(5));
    }
}
