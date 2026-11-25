package io.github.pedropasinn.mobilepad.bt

import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import io.github.pedropasinn.mobilepad.lab.LabProbe
import io.github.pedropasinn.mobilepad.lab.LabStage
import io.github.pedropasinn.mobilepad.protocol.ControllerState

/**
 * Standard HID gamepad + keyboard: PCs, Macs, Linux, Android phones/tablets and
 * Android/Google TV use it without extra software.
 */
class GenericGamepadProfile : HidProfile {
    override val id = ID

    override val sdp = BluetoothHidDeviceAppSdpSettings(
        "Mobilepad",
        "Gamepad Android",
        "Mobilepad",
        (BluetoothHidDevice.SUBCLASS1_KEYBOARD.toInt() or BluetoothHidDevice.SUBCLASS2_GAMEPAD.toInt()).toByte(),
        HidDescriptor.DESCRIPTOR,
    )

    @Volatile private var link: HidLink? = null
    @Volatile private var lastReport: ByteArray? = null

    override fun attach(link: HidLink) {
        this.link = link
        lastReport = null
    }

    override fun detach() {
        link = null
    }

    // Pacing (12 ms: chosen with the Lab analog test, 250 states/s: P95 ~200 ms unpaced, ~57 at 8 ms,
    // ~35 at 12-20 ms). sendReport() only queues: when states come faster than the radio delivers them
    // (a stick moving at touch rate, ~200+ per second), the queue grows and every later input
    // waits behind it (the Lab measured 200 ms+ at 250 states/s). So reports go out at most one per
    // [minIntervalMs], always the newest state: a change after a quiet moment still leaves at once,
    // and a burst is conflated instead of queued.
    private val lock = Object()
    private var pending: Pair<ControllerState, ByteArray>? = null
    private var lastSentNs = 0L
    private var sender: Thread? = null

    override fun onState(state: ControllerState) {
        if (link == null) return
        val report = HidDescriptor.gamepadReport(state)
        if (report.contentEquals(lastReport)) return
        lastReport = report
        val interval = minIntervalMs * 1_000_000L
        synchronized(lock) {
            if (interval <= 0 || (pending == null && System.nanoTime() - lastSentNs >= interval)) {
                lastSentNs = System.nanoTime()
                send(state, report)
                return
            }
            pending = state to report
            if (sender == null) {
                sender = Thread(::paceLoop, "bt-pace").apply {
                    isDaemon = true
                    priority = Thread.MAX_PRIORITY
                    start()
                }
            }
            lock.notifyAll()
        }
    }

    private fun paceLoop() {
        while (true) {
            val next = synchronized(lock) {
                while (pending == null) lock.wait()
                val wait = lastSentNs + minIntervalMs * 1_000_000L - System.nanoTime()
                if (wait > 0) {
                    lock.wait(wait / 1_000_000L, (wait % 1_000_000L).toInt())
                    return@synchronized null
                }
                val taken = pending
                pending = null
                lastSentNs = System.nanoTime()
                taken
            } ?: continue
            send(next.first, next.second)
        }
    }

    private fun send(state: ControllerState, report: ByteArray) {
        val link = link ?: return
        val sent = link.send(HidDescriptor.GAMEPAD_REPORT_ID, report)
        // Lab stage `send`: sendReport returned (off outside a lab run).
        if (sent && LabProbe.enabled) LabProbe.record(LabStage.SEND, state)
    }

    override fun onKeys(modifiers: Int, keys: List<Int>) {
        link?.send(HidDescriptor.KEYBOARD_REPORT_ID, HidDescriptor.keyboardReport(modifiers, keys))
    }

    override fun onGetReport(reportId: Int): ByteArray = when (reportId) {
        HidDescriptor.KEYBOARD_REPORT_ID -> HidDescriptor.keyboardReport(0, emptyList())
        else -> lastReport ?: HidDescriptor.gamepadReport(ControllerState.NEUTRAL)
    }

    companion object {
        const val ID = "generic"

        /** Minimum gap between gamepad reports (0 = no pacing). Lab knob: `--ei btIntervalMs N`. */
        @Volatile var minIntervalMs = 12
    }
}
