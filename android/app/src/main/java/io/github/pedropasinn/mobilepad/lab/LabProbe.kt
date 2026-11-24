package io.github.pedropasinn.mobilepad.lab

import io.github.pedropasinn.mobilepad.protocol.ControllerState

/**
 * Stage probes of the Lab (labs/SPEC.md §3), on only while a lab run is active. The hot path
 * reads [enabled] (one volatile read) and returns: outside a run nothing is allocated or locked.
 *
 * Call sites guard with `if (LabProbe.enabled)` so not even the call is made when off.
 * The step number of a `state`/`send` record is read from the mark in the state ([mark]).
 */
object LabProbe {
    @Volatile @JvmField var enabled: Boolean = false

    /** Newest step issued by the runner; marks are decoded relative to it. */
    @Volatile @JvmField var latestSeq: Int = -1

    @Volatile private var ring: LabRing? = null
    @Volatile private var clock: WallClock? = null
    @Volatile var mark: MarkAxis = MarkAxis.RX
        private set

    // Last state per stage, to skip repeats (keep-alives, sensor-driven republishes of the same state).
    private val last = arrayOfNulls<ControllerState>(LabStage.NAMES.size)

    /** Last state handed to a link, its wall time and step, whether or not it was a repeat. */
    @Volatile @JvmField var lastSend: ControllerState? = null
    @Volatile @JvmField var lastSendNs: Long = 0
    @Volatile @JvmField var lastSendSeq: Int = -1

    fun start(ring: LabRing, clock: WallClock, mark: MarkAxis) {
        this.ring = ring
        this.clock = clock
        this.mark = mark
        latestSeq = -1
        last.fill(null)
        lastSend = null
        lastSendNs = 0
        lastSendSeq = -1
        enabled = true
    }

    fun stop() {
        enabled = false
    }

    /** Drops the buffer once it has been written out. */
    fun clear() {
        enabled = false
        ring = null
        last.fill(null)
        lastSend = null
    }

    /** A `cmd` record: the runner knows the step itself. */
    fun command(seq: Int, state: ControllerState) {
        latestSeq = seq
        val ring = ring ?: return
        val clock = clock ?: return
        if (!enabled) return
        last[LabStage.CMD] = state
        ring.record(seq, LabStage.CMD, clock.nowNs(), state)
    }

    /** A `state` or `send` record; repeats of the previous state for that stage are skipped. */
    fun record(stage: Int, state: ControllerState) {
        if (!enabled) return
        val ring = ring ?: return
        val clock = clock ?: return
        val now = clock.nowNs()
        if (stage == LabStage.SEND) {
            lastSend = state
            lastSendNs = now
        }
        if (state == last[stage]) return
        last[stage] = state
        val seq = LabMark.decode(mark.read(state), latestSeq)
        if (stage == LabStage.SEND && seq >= 0) lastSendSeq = seq
        ring.record(seq, stage, now, state)
    }

    /**
     * Mean cost of one [record] call while on, in ns, measured on a scratch buffer with the same
     * code (distinct states, so none is skipped as a repeat). Must run before [start].
     */
    fun measureOverheadNs(calls: Int = 50_000): Double {
        val scratch = LabRing(calls + calls / 2)
        val states = Array(256) { ControllerState(buttons = it, rightX = LabMark.encode(it)) }
        val saved = Triple(ring, clock, mark)
        start(scratch, WallClock(), MarkAxis.RX)
        latestSeq = 255
        // Warm up the JIT, then measure.
        for (i in 0 until calls / 2) record(LabStage.STATE, states[i and 255])
        val begin = System.nanoTime()
        for (i in 0 until calls) record(LabStage.STATE, states[i and 255])
        val elapsed = System.nanoTime() - begin
        enabled = false
        ring = saved.first
        clock = saved.second
        mark = saved.third
        latestSeq = -1
        last.fill(null)
        lastSend = null
        lastSendSeq = -1
        return elapsed.toDouble() / calls
    }
}
