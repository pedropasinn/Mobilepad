package io.github.pedropasinn.mobilepad.input

import android.content.Context
import io.github.pedropasinn.mobilepad.protocol.Buttons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

/**
 * End-to-end latency test, driven from the PC (tools/latency): presses and releases a button through
 * the same path as a finger on the screen, [count] times at irregular intervals (so the presses
 * never fall into step with the PC's or the game's polling), and writes when each press was sent.
 *
 * Times are wall-clock nanoseconds (CLOCK_REALTIME, the clock `adb shell date +%s%N` reads), so
 * the PC can line them up with its own after measuring the offset between the two clocks.
 * Output: `<external files>/latency.csv`, one line per press: `index,down_ns,up_ns`.
 */
object LatencyTest {
    @Volatile private var job: Job? = null

    val running get() = job?.isActive == true

    /**
     * [avoid]: buttons that start a combo macro (held back by the combo window): the test presses
     * the first face button outside them, so it measures the link, not the macro wait.
     */
    fun start(context: Context, input: GamepadInput, scope: CoroutineScope, count: Int = 200, avoid: Int = 0, onDone: (File) -> Unit = {}) {
        val button = listOf(Buttons.B, Buttons.X, Buttons.Y, Buttons.A, Buttons.DPAD_DOWN).firstOrNull { it and avoid == 0 } ?: Buttons.B
        if (running) return
        val file = File(context.getExternalFilesDir(null), "latency.csv")
        job = scope.launch(Dispatchers.Default) {
            val clock = WallClock()
            val lines = StringBuilder("index,down_ns,up_ns\n")
            delay(1500) // time for the PC to start listening
            repeat(count) { i ->
                delay(Random.nextLong(70, 180))
                val down = clock.nowNs()
                input.touch { it.with(button, true) }
                delay(Random.nextLong(35, 70))
                val up = clock.nowNs()
                input.touch { it.with(button, false) }
                lines.append(i).append(',').append(down).append(',').append(up).append('\n')
            }
            delay(400)
            file.writeText(lines.toString())
            onDone(file)
        }
    }

    /**
     * Wall clock with nanosecond steps: anchored on a millisecond edge of currentTimeMillis
     * (found by spinning until it ticks), then advanced with nanoTime.
     */
    private class WallClock {
        private val baseWallNs: Long
        private val baseNano: Long

        init {
            val start = System.currentTimeMillis()
            var now = start
            var nano = System.nanoTime()
            while (now == start) {
                nano = System.nanoTime()
                now = System.currentTimeMillis()
            }
            baseWallNs = now * 1_000_000L
            baseNano = nano
        }

        fun nowNs() = baseWallNs + (System.nanoTime() - baseNano)
    }
}
