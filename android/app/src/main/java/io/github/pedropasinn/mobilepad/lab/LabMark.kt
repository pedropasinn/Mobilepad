package io.github.pedropasinn.mobilepad.lab

import io.github.pedropasinn.mobilepad.protocol.ControllerState

/** Which stick axis carries the step number (labs/SPEC.md §2). */
enum class MarkAxis(val id: String) {
    RX("rx"), RY("ry");

    fun read(state: ControllerState): Int = (if (this == RX) state.rightX else state.rightY).toInt()

    fun apply(state: ControllerState, seq: Int): ControllerState =
        if (this == RX) state.copy(rightX = LabMark.encode(seq)) else state.copy(rightY = LabMark.encode(seq))
}

/**
 * The fingerprint of SPEC §2: `mark = 1000 + (seq * 7919) % 30000`. 7919 is prime and does not
 * divide 30000, so the map is a bijection on 30000 consecutive steps and can be inverted.
 */
object LabMark {
    const val BASE = 1000
    const val MULT = 7919
    const val MOD = 30000

    /** 7919⁻¹ mod 30000. */
    val INVERSE: Int = (1 until MOD).first { (MULT.toLong() * it) % MOD == 1L }

    fun encode(seq: Int): Short = (BASE + ((seq.toLong() * MULT) % MOD).toInt()).toShort()

    /**
     * The step whose mark is [value], given that the newest step issued so far is [latestSeq]
     * (a state never shows up before its command): the largest seq ≤ latestSeq with that mark.
     * -1 when [value] is not a mark (e.g. 0 in a neutral state) or no such step exists yet.
     */
    fun decode(value: Int, latestSeq: Int): Int {
        val v = value - BASE
        if (v < 0 || v >= MOD || latestSeq < 0) return -1
        val residue = ((v.toLong() * INVERSE) % MOD).toInt()
        val seq = latestSeq - Math.floorMod(latestSeq - residue, MOD)
        return if (seq >= 0) seq else -1
    }
}
