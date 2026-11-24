package io.github.pedropasinn.mobilepad.lab

/**
 * Wall clock with nanosecond steps (labs/SPEC.md §1): anchored on a millisecond edge of
 * currentTimeMillis (found by spinning until it ticks, at most ~1 ms), then advanced with nanoTime.
 * The result is UTC nanoseconds since 1970 (CLOCK_REALTIME, what `adb shell date +%s%N` reads),
 * with the monotonic clock's resolution between the two readings.
 */
class WallClock {
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

    fun nowNs(): Long = baseWallNs + (System.nanoTime() - baseNano)

    /** Wall time of a [System.nanoTime] reading taken earlier. */
    fun toWallNs(nano: Long): Long = baseWallNs + (nano - baseNano)
}
