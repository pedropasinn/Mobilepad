package io.github.pedropasinn.mobilepad.lab

import android.os.Process
import android.util.Log
import io.github.pedropasinn.mobilepad.protocol.ControllerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

/** What the runner needs from the app (MainActivity implements it; tests fake it). */
interface LabHost {
    /** Replaces the on-screen input with [state], as a finger would (`input.touch`). */
    fun touch(state: ControllerState)

    /** Releases everything (neutral on-screen input). */
    fun release()

    /** Link in use: `usb`, `wifi`, `bt` or `none`. */
    fun link(): String

    /** Drops [link] (may be called from any thread). */
    fun dropLink(link: String)

    /** Starts reconnecting [link] to the host it had before [dropLink]. */
    fun restoreLink(link: String)
}

/** App identity written to done.json. */
data class LabAppInfo(
    val version: String,
    val versionCode: Long,
    val commit: String,
    val device: String,
    val android: String,
    val sdk: Int,
)

/**
 * Runs the Lab tests of labs/SPEC.md §4 on a dedicated high-priority thread: each step records
 * `cmd` and goes through [LabHost.touch] (the touch path); the probes record `state` and `send`.
 * Writes `<dir>/phone.csv`, then `<dir>/done.json` (written last, via a rename, so done.json
 * existing means phone.csv is complete).
 *
 * Safety: one run at a time (atomic guard), everything released in `finally`, [cancel] stops a
 * run (the activity calls it when it leaves the foreground).
 */
object LabRunner {
    private const val TAG = "LabRunner"
    const val CAPACITY = 1 shl 18 // records (~7 MB, allocated only for the duration of a run)

    private val guard = AtomicBoolean(false)
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    @Volatile private var cancelReason: String? = null
    @Volatile private var thread: Thread? = null
    @Volatile private var activeDir: File? = null

    /** Waits before the first step, so the PC side is listening (tests shorten it). */
    @Volatile internal var warmupMs = 1000L

    /** Another tool (the old latency test) is using the input: refuse to start meanwhile. */
    @Volatile var busyElsewhere: () -> Boolean = { false }

    val running: Boolean get() = guard.get()

    /**
     * Starts a run writing into [dir]. Returns false (and writes an `ok:false` done.json) when
     * another run is active. [onDone] runs on the lab thread with (ok, message).
     */
    fun start(host: LabHost, params: LabParams, info: LabAppInfo, dir: File, onDone: (Boolean, String) -> Unit = { _, _ -> }): Boolean {
        if (busyElsewhere() || !guard.compareAndSet(false, true)) {
            val started = WallClock().nowNs()
            if (dir.absoluteFile != activeDir) runCatching {
                dir.mkdirs()
                writeDone(dir, doneJson(params, info, "none", ok = false, steps = 0, startedNs = started, endedNs = started,
                    overheadNs = 0.0, ring = null, extra = JSONObject(), notes = emptyList(), error = "another test is running"))
            }
            onDone(false, "another test is running")
            return false
        }
        cancelReason = null
        activeDir = dir.absoluteFile
        _active.value = true
        thread = Thread({ execute(host, params, info, dir, onDone) }, "mobilepad-lab").apply {
            isDaemon = true
            start()
        }
        return true
    }

    /** Stops the current run, if any; it still releases everything and writes its files. */
    fun cancel(reason: String) {
        if (!guard.get()) return
        cancelReason = reason
        thread?.let(LockSupport::unpark)
    }

    /** Blocks until the current run (if any) ends. For tests. */
    internal fun join(timeoutMs: Long) {
        thread?.join(timeoutMs)
    }

    private class Cancelled(reason: String) : Exception(reason)

    private fun execute(host: LabHost, params: LabParams, info: LabAppInfo, dir: File, onDone: (Boolean, String) -> Unit) {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY) }
        val clock = WallClock()
        val startedNs = clock.nowNs()
        var ring: LabRing? = null
        var steps = 0
        var error: String? = null
        val notes = mutableListOf<String>()
        val extra = JSONObject()
        val link = host.link()
        var overhead = 0.0
        var dropped: String? = null
        try {
            dir.mkdirs()
            File(dir, "done.json").delete()
            File(dir, "phone.csv").delete()
            overhead = LabProbe.measureOverheadNs()
            var p = params
            if (p.test == "throughput" && p.rateHz <= 0 && link == "none") {
                p = p.copy(rateHz = 1000)
                notes += "no link: throughput runs open loop at 1000 Hz instead of closed loop"
            }
            val plan = if (p.test == "reconnect") null else LabPlan.of(p)
            ring = LabRing(CAPACITY)
            LabProbe.start(ring, clock, plan?.mark ?: MarkAxis.RX)
            Log.i(TAG, "start ${p.test} run=${p.run} seed=${p.seed} link=$link")
            sleepNs(warmupMs * 1_000_000L)
            if (plan != null) {
                val counter = RunCounter()
                runPlan(host, p, plan, counter, extra)
                steps = counter.steps
            } else {
                if (link == "none") throw IllegalStateException("reconnect needs a connected link")
                val counter = RunCounter()
                runReconnect(host, p, link, clock, counter, extra) { dropped = it }
                steps = counter.steps
            }
        } catch (e: Cancelled) {
            error = "stopped: ${e.message}"
        } catch (e: Throwable) {
            error = e.toString()
            Log.w(TAG, "lab run failed", e)
        } finally {
            // Everything back to neutral, and time for that state to reach the link.
            runCatching { host.release() }
            quietSleepMs(300)
            dropped?.let { l ->
                notes += "link was down when the run ended: reconnecting it"
                runCatching { host.restoreLink(l) }
            }
            LabProbe.stop()
            val endedNs = clock.nowNs()
            val ok = error == null
            runCatching {
                ring?.let { r -> File(dir, "phone.csv").bufferedWriter().use { r.writeCsv(it) } }
                if (ring != null && ring.dropped > 0) notes += "buffer full: ${ring.dropped} records dropped"
                if (link == "bt") notes += "bluetooth: send = sendReport returned (Generic: per changed report; Switch Pro: every periodic 0x30 report)"
                writeDone(dir, doneJson(params, info, link, ok, steps, startedNs, endedNs, overhead, ring, extra, notes, error))
            }.onFailure { Log.w(TAG, "could not write lab results", it) }
            LabProbe.clear()
            thread = null
            activeDir = null
            _active.value = false
            guard.set(false)
            Log.i(TAG, "end ${params.test} ok=$ok steps=$steps ${error ?: ""}")
            onDone(ok, error ?: "${params.test}: $steps steps")
        }
    }

    private class RunCounter {
        var steps = 0
    }

    private fun issue(host: LabHost, seq: Int, state: ControllerState, counter: RunCounter) {
        LabProbe.command(seq, state)
        host.touch(state)
        counter.steps = seq + 1
    }

    private fun runPlan(host: LabHost, p: LabParams, plan: LabPlan, counter: RunCounter, extra: JSONObject) {
        val t0 = System.nanoTime()
        val end = if (p.test == "throughput") t0 + p.seconds * 1_000_000_000L else Long.MAX_VALUE
        var seq = 0
        var stalls = 0
        var late = 0L
        while (true) {
            checkCancel()
            if (System.nanoTime() >= end) break
            val step = plan.next(seq) ?: break
            if (step.atNs == LabStep.ASAP) {
                // Closed loop: the next state once the previous one was handed to the link.
                if (seq > 0 && !waitUntil(50_000_000L) { LabProbe.lastSendSeq >= seq - 1 }) stalls++
            } else {
                sleepUntil(t0 + step.atNs)
                late = maxOf(late, System.nanoTime() - (t0 + step.atNs))
            }
            issue(host, seq, step.state, counter)
            seq++
        }
        // Final step: neutral, still marked, so the PC can check that nothing stays pressed.
        sleepNs(100_000_000L)
        issue(host, seq, plan.mark.apply(ControllerState.NEUTRAL, seq), counter)
        sleepNs(300_000_000L)
        if (p.test == "throughput") {
            extra.put("stalls", stalls)
            extra.put("elapsed_ns", System.nanoTime() - t0)
        }
        extra.put("max_late_ns", late)
    }

    /**
     * Each cycle: press a button (marked step), drop the link with it held, release it while the
     * link is down (marked step), reconnect, then check the first state sent after reconnecting.
     */
    private fun runReconnect(host: LabHost, p: LabParams, link: String, clock: WallClock, counter: RunCounter, extra: JSONObject, setDropped: (String?) -> Unit) {
        val button = LabPlan.latencyButton(p.avoidButtons)
        val mark = MarkAxis.RX
        val cycles = JSONArray()
        var seq = 0
        extra.put("button", "0x" + Integer.toHexString(button).uppercase())
        for (cycle in 0 until p.count) {
            checkCancel()
            val pressSeq = seq++
            issue(host, pressSeq, mark.apply(ControllerState(buttons = button), pressSeq), counter)
            sleepNs(400_000_000L)
            val pressSent = LabProbe.lastSendSeq >= pressSeq
            val downNs = clock.nowNs()
            setDropped(link)
            host.dropLink(link)
            val downSeen = waitUntil(5_000_000_000L) { host.link() != link }
            val downSeenNs = clock.nowNs()
            sleepNs(500_000_000L) // the PC sees the link gone with the button still held
            val releaseSeq = seq++
            issue(host, releaseSeq, mark.apply(ControllerState.NEUTRAL, releaseSeq), counter)
            val restoreNs = clock.nowNs()
            host.restoreLink(link)
            val up = waitUntil(20_000_000_000L) { host.link() == link }
            val upNs = clock.nowNs()
            val entry = JSONObject()
                .put("cycle", cycle)
                .put("press_seq", pressSeq)
                .put("press_sent_before_drop", pressSent)
                .put("drop_ns", downNs)
                .put("down_seen", downSeen)
                .put("down_seen_ns", downSeenNs)
                .put("release_seq", releaseSeq)
                .put("restore_ns", restoreNs)
                .put("reconnected", up)
            if (!up) {
                entry.put("up_ns", JSONObject.NULL)
                cycles.put(entry)
                extra.put("reconnect", cycles)
                throw IllegalStateException("link $link did not come back within 20 s (cycle $cycle)")
            }
            setDropped(null)
            entry.put("up_ns", upNs).put("reconnect_ns", upNs - restoreNs)
            // The first state handed to the link after reconnecting must have the button released.
            var sent = waitUntil(1_000_000_000L) { LabProbe.lastSendNs > upNs }
            var verifySeq = -1
            if (!sent) {
                // Links that only send on change (Bluetooth generic) need a new state to send.
                verifySeq = seq++
                issue(host, verifySeq, mark.apply(ControllerState.NEUTRAL, verifySeq), counter)
                sent = waitUntil(1_000_000_000L) { LabProbe.lastSendNs > upNs }
            }
            val observed = LabProbe.lastSend
            entry.put("verify_seq", verifySeq)
                .put("first_send_after_up_ns", if (sent) LabProbe.lastSendNs else JSONObject.NULL)
                .put("first_send_buttons", if (sent && observed != null) "0x" + Integer.toHexString(observed.buttons).uppercase() else JSONObject.NULL)
                .put("released_after_reconnect", sent && observed != null && observed.buttons and button == 0)
            cycles.put(entry)
            sleepNs(800_000_000L)
        }
        extra.put("reconnect", cycles)
        val finalSeq = seq
        issue(host, finalSeq, mark.apply(ControllerState.NEUTRAL, finalSeq), counter)
        sleepNs(300_000_000L)
    }

    private fun checkCancel() {
        cancelReason?.let { throw Cancelled(it) }
    }

    /** Sleeps until [target] (System.nanoTime): parks while far, spins the last ~0.3 ms. */
    private fun sleepUntil(target: Long) {
        while (true) {
            checkCancel()
            val remaining = target - System.nanoTime()
            if (remaining <= 0) return
            if (remaining > 2_000_000L) LockSupport.parkNanos(minOf(remaining - 1_000_000L, 50_000_000L))
            else if (remaining > 300_000L) LockSupport.parkNanos(remaining - 250_000L)
        }
    }

    private fun sleepNs(ns: Long) = sleepUntil(System.nanoTime() + ns)

    /** Polls [condition] every ~0.2 ms until true (returns true) or [timeoutNs] passes (false). */
    private inline fun waitUntil(timeoutNs: Long, condition: () -> Boolean): Boolean {
        val end = System.nanoTime() + timeoutNs
        while (!condition()) {
            checkCancel()
            if (System.nanoTime() >= end) return false
            LockSupport.parkNanos(200_000L)
        }
        return true
    }

    private fun quietSleepMs(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    internal fun doneJson(
        p: LabParams, info: LabAppInfo, link: String, ok: Boolean, steps: Int, startedNs: Long, endedNs: Long,
        overheadNs: Double, ring: LabRing?, extra: JSONObject, notes: List<String>, error: String?,
    ): JSONObject {
        val json = JSONObject()
            .put("ok", ok)
            .put("count", steps)
            .put("test", p.test)
            .put("run", p.run)
            .put("seed", p.seed)
            .put("params", JSONObject().put("count", p.count).put("rate_hz", p.rateHz).put("seconds", p.seconds))
            .put("mark", if (p.test == "analog") MarkAxis.RY.id else MarkAxis.RX.id)
            .put("link", link)
            .put("app_version", info.version)
            .put("app_version_code", info.versionCode)
            .put("app_commit", info.commit)
            .put("device", info.device)
            .put("android_version", info.android)
            .put("android_sdk", info.sdk)
            .put("started_ns", startedNs)
            .put("ended_ns", endedNs)
            .put("probe_overhead_ns", Math.round(overheadNs * 10) / 10.0)
            .put("rows", ring?.size ?: 0)
            .put("dropped_rows", ring?.dropped ?: 0)
            .put("avoid_buttons", "0x" + Integer.toHexString(p.avoidButtons).uppercase())
            .put("notes", JSONArray(notes))
        val keys = extra.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            json.put(k, extra.get(k))
        }
        if (error != null) json.put("error", error)
        return json
    }

    private fun writeDone(dir: File, json: JSONObject) {
        val tmp = File(dir, "done.json.tmp")
        tmp.writeText(json.toString(1))
        val target = File(dir, "done.json")
        target.delete()
        if (!tmp.renameTo(target)) {
            target.writeText(json.toString(1))
            tmp.delete()
        }
    }
}
