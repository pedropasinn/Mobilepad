package io.github.pedropasinn.mobilepad.lab

import io.github.pedropasinn.mobilepad.protocol.Buttons
import io.github.pedropasinn.mobilepad.protocol.ControllerState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lab run parameters, from the intent extras of labs/SPEC.md §4, with the spec's defaults.
 * [avoidButtons]/[avoidLt]/[avoidRt]: inputs that macros or remapping would alter (combo
 * hold-back, consumed triggers, remapped sources); generated steps never use them, so what the
 * test commands is what the link carries.
 */
data class LabParams(
    val test: String,
    val run: String,
    val seed: Long,
    val count: Int,
    val rateHz: Int,
    val seconds: Int,
    val avoidButtons: Int = 0,
    val avoidLt: Boolean = false,
    val avoidRt: Boolean = false,
) {
    companion object {
        val TESTS = listOf("latency", "multi", "analog", "throughput", "reliability", "reconnect")

        fun defaultCount(test: String) = when (test) {
            "latency" -> 200       // pulses (2 steps each)
            "multi" -> 500         // steps
            "analog" -> 0          // 0 = one full sweep pattern (8 s at rateHz)
            "reliability" -> 20_000
            "reconnect" -> 5       // drop/reconnect cycles
            else -> 0              // throughput: bounded by seconds
        }

        fun defaultRate(test: String) = when (test) {
            "analog" -> 250
            "reliability" -> 500
            else -> 0              // throughput: 0 = closed loop (next step once the previous was sent)
        }
    }
}

/** One step: the state to command (mark included) and when, in ns from the start of the run. */
class LabStep(val state: ControllerState, val atNs: Long) {
    companion object {
        /** [atNs] of a closed-loop step: as soon as the previous step reached the link. */
        const val ASAP = -1L
    }
}

/**
 * The step sequence of a test, deterministic for a given seed. [next] is called with seq = 0, 1, 2…
 * in order and returns null at the end (the runner then adds a final neutral step).
 */
abstract class LabPlan(val mark: MarkAxis) {
    abstract fun next(seq: Int): LabStep?

    companion object {
        /** Buttons the generators may use (Guide left out: hosts open overlays on it). */
        val BUTTONS = intArrayOf(
            Buttons.DPAD_UP, Buttons.DPAD_DOWN, Buttons.DPAD_LEFT, Buttons.DPAD_RIGHT, Buttons.START, Buttons.BACK,
            Buttons.LEFT_THUMB, Buttons.RIGHT_THUMB, Buttons.LEFT_SHOULDER, Buttons.RIGHT_SHOULDER,
            Buttons.A, Buttons.B, Buttons.X, Buttons.Y,
        )

        /** Plans for the timed tests (reconnect is procedural and has none). */
        fun of(p: LabParams): LabPlan = when (p.test) {
            "latency" -> LatencyPlan(p)
            "multi" -> MultiPlan(p)
            "analog" -> AnalogPlan(p)
            "throughput" -> RandomPlan(p, periodNs = if (p.rateHz > 0) 1_000_000_000L / p.rateHz else LabStep.ASAP, steps = Int.MAX_VALUE)
            "reliability" -> RandomPlan(p, periodNs = 1_000_000_000L / p.rateHz.coerceAtLeast(1), steps = p.count)
            else -> throw IllegalArgumentException("unknown lab test: ${p.test}")
        }

        /** The button pulsed by the latency test: the first of these outside the avoided ones. */
        fun latencyButton(avoid: Int): Int =
            listOf(Buttons.B, Buttons.X, Buttons.Y, Buttons.A, Buttons.DPAD_DOWN, Buttons.DPAD_RIGHT).firstOrNull { it and avoid == 0 } ?: Buttons.B
    }
}

private fun ms(value: Long) = value * 1_000_000L

/** Pulses of one button: press (held 35–70 ms), release (then 70–180 ms) — irregular, so they never fall into step with any polling. */
private class LatencyPlan(p: LabParams) : LabPlan(MarkAxis.RX) {
    private val random = Random(p.seed)
    private val button = latencyButton(p.avoidButtons)
    private val steps = p.count * 2
    private var at = 0L

    override fun next(seq: Int): LabStep? {
        if (seq >= steps) return null
        val press = seq % 2 == 0
        val step = LabStep(mark.apply(ControllerState(buttons = if (press) button else 0), seq), at)
        at += if (press) ms(random.nextLong(35, 71)) else ms(random.nextLong(70, 181))
        return step
    }
}

/** Random combinations of 1–6 buttons plus triggers and left stick, each held 40–100 ms. */
private class MultiPlan(private val p: LabParams) : LabPlan(MarkAxis.RX) {
    private val random = Random(p.seed)
    private val allowed = BUTTONS.filter { it and p.avoidButtons == 0 }.toIntArray()
    private var at = 0L

    override fun next(seq: Int): LabStep? {
        if (seq >= p.count) return null
        val step = LabStep(mark.apply(randomState(random, allowed, p, minButtons = 1), seq), at)
        at += ms(random.nextLong(40, 101))
        return step
    }
}

/**
 * Same random states as [MultiPlan] (0–6 buttons) at a fixed period, or closed loop
 * ([LabStep.ASAP]); throughput ends on time (the runner), reliability after [steps].
 */
private class RandomPlan(private val p: LabParams, private val periodNs: Long, private val steps: Int) : LabPlan(MarkAxis.RX) {
    private val random = Random(p.seed)
    private val allowed = BUTTONS.filter { it and p.avoidButtons == 0 }.toIntArray()

    override fun next(seq: Int): LabStep? {
        if (seq >= steps) return null
        val at = if (periodNs == LabStep.ASAP) LabStep.ASAP else seq * periodNs
        return LabStep(mark.apply(randomState(random, allowed, p, minButtons = 0), seq), at)
    }
}

/**
 * Left-stick sweep at rateHz, mark on ry: per 8 s pattern, two full-radius circles (2 s each,
 * starting angle from the seed), then ramps of lx up and down, then of ly up and down (1 s each).
 * count = 0 runs one pattern; otherwise count steps (the pattern repeats).
 */
private class AnalogPlan(p: LabParams) : LabPlan(MarkAxis.RY) {
    private val rate = p.rateHz.coerceAtLeast(1)
    private val period = 1_000_000_000L / rate
    private val pattern = rate * 8
    private val steps = if (p.count > 0) p.count else pattern
    private val phase = Random(p.seed).nextDouble(0.0, 2 * PI)

    override fun next(seq: Int): LabStep? {
        if (seq >= steps) return null
        val i = seq % pattern
        val t = i.toDouble() / rate // seconds into the pattern
        var lx = 0
        var ly = 0
        when {
            t < 4.0 -> {
                val angle = phase + 2 * PI * t / 2.0
                lx = (cos(angle) * 32767).roundToInt()
                ly = (sin(angle) * 32767).roundToInt()
            }
            t < 5.0 -> lx = ramp(t - 4.0)
            t < 6.0 -> lx = ramp(1.0 - (t - 5.0))
            t < 7.0 -> ly = ramp(t - 6.0)
            else -> ly = ramp(1.0 - (t - 7.0))
        }
        val state = ControllerState(leftX = lx.toShort(), leftY = ly.toShort())
        return LabStep(mark.apply(state, seq), seq * period)
    }

    private fun ramp(fraction: Double) = (-32768 + (fraction.coerceIn(0.0, 1.0) * 65535).roundToInt()).coerceIn(-32768, 32767)
}

/** Random buttons (between [minButtons] and 6, distinct), triggers (half the time) and left stick. */
internal fun randomState(random: Random, allowed: IntArray, p: LabParams, minButtons: Int): ControllerState {
    var buttons = 0
    if (allowed.isNotEmpty()) {
        val k = random.nextInt(minButtons, minOf(6, allowed.size) + 1)
        var picked = 0
        while (picked < k) {
            val b = allowed[random.nextInt(allowed.size)]
            if (buttons and b == 0) {
                buttons = buttons or b
                picked++
            }
        }
    }
    val lt = if (!p.avoidLt && random.nextBoolean()) random.nextInt(1, 256) else 0
    val rt = if (!p.avoidRt && random.nextBoolean()) random.nextInt(1, 256) else 0
    val lx = random.nextInt(-32768, 32768)
    val ly = random.nextInt(-32768, 32768)
    return ControllerState(buttons = buttons, leftX = lx.toShort(), leftY = ly.toShort(), leftTrigger = lt, rightTrigger = rt)
}
