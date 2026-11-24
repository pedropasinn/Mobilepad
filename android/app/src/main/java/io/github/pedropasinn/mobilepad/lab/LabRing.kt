package io.github.pedropasinn.mobilepad.lab

import io.github.pedropasinn.mobilepad.protocol.ControllerState
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLongArray

/** Stages recorded on the phone (labs/SPEC.md §3). */
object LabStage {
    const val CMD = 0
    const val STATE = 1
    const val SEND = 2
    val NAMES = arrayOf("cmd", "state", "send")
}

/**
 * Pre-allocated, lock-free record store: writers on any thread claim a slot with one atomic
 * increment and fill primitive arrays, so a record allocates nothing. Once full, further records
 * are counted in [dropped] and discarded. The timestamp is written last with release semantics
 * ([AtomicLongArray.lazySet]); a slot whose timestamp is still 0 when read is skipped.
 */
class LabRing(val capacity: Int) {
    private val next = AtomicInteger(0)
    private val seq = IntArray(capacity)
    private val stage = ByteArray(capacity)
    private val t = AtomicLongArray(capacity)
    private val buttons = IntArray(capacity)
    private val lx = ShortArray(capacity)
    private val ly = ShortArray(capacity)
    private val rx = ShortArray(capacity)
    private val ry = ShortArray(capacity)
    private val lt = ShortArray(capacity)
    private val rt = ShortArray(capacity)

    /** Records claimed (including those dropped for lack of room). */
    val claimed: Int get() = next.get()
    val size: Int get() = minOf(next.get(), capacity)
    val dropped: Int get() = maxOf(0, next.get() - capacity)

    fun record(seq: Int, stage: Int, tNs: Long, state: ControllerState) {
        val i = next.getAndIncrement()
        if (i >= capacity) return
        this.seq[i] = seq
        this.stage[i] = stage.toByte()
        buttons[i] = state.buttons
        lx[i] = state.leftX
        ly[i] = state.leftY
        rx[i] = state.rightX
        ry[i] = state.rightY
        lt[i] = state.leftTrigger.toShort()
        rt[i] = state.rightTrigger.toShort()
        t.lazySet(i, tNs)
    }

    /** `phone.csv` (SPEC §3): header plus one line per complete record, in claim order. */
    fun writeCsv(out: Appendable) {
        out.append(HEADER).append('\n')
        for (i in 0 until size) {
            val tNs = t.get(i)
            if (tNs == 0L) continue
            appendRow(out, seq[i], stage[i].toInt(), tNs, buttons[i], lx[i].toInt(), ly[i].toInt(), rx[i].toInt(), ry[i].toInt(), lt[i].toInt(), rt[i].toInt())
        }
    }

    companion object {
        const val HEADER = "seq,stage,t_ns,buttons,lx,ly,rx,ry,lt,rt"

        fun appendRow(out: Appendable, seq: Int, stage: Int, tNs: Long, buttons: Int, lx: Int, ly: Int, rx: Int, ry: Int, lt: Int, rt: Int) {
            out.append(seq.toString()).append(',')
                .append(LabStage.NAMES[stage]).append(',')
                .append(tNs.toString()).append(',')
                .append("0x").append(Integer.toHexString(buttons and 0xFFFF).uppercase()).append(',')
                .append(lx.toString()).append(',')
                .append(ly.toString()).append(',')
                .append(rx.toString()).append(',')
                .append(ry.toString()).append(',')
                .append(lt.toString()).append(',')
                .append(rt.toString()).append('\n')
        }
    }
}
