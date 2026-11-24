package io.github.pedropasinn.mobilepad.lab

import io.github.pedropasinn.mobilepad.protocol.Buttons
import io.github.pedropasinn.mobilepad.protocol.ControllerState
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LabMarkTest {
    @Test
    fun formulaMatchesSpec() {
        assertEquals(1000, LabMark.encode(0).toInt())
        assertEquals(1000 + 7919, LabMark.encode(1).toInt())
        assertEquals(1000 + (5 * 7919) % 30000, LabMark.encode(5).toInt())
        assertEquals(1000 + ((12345L * 7919) % 30000).toInt(), LabMark.encode(12345).toInt())
        assertEquals(1, (7919L * LabMark.INVERSE) % 30000)
    }

    @Test
    fun unique_positive_andDecodable_over30000Steps() {
        val seen = HashSet<Int>()
        for (seq in 0 until 30_000) {
            val v = LabMark.encode(seq).toInt()
            assertTrue(v in 1000..30999)
            assertTrue("duplicate mark at $seq", seen.add(v))
            assertEquals(seq, LabMark.decode(v, seq))
            assertEquals(seq, LabMark.decode(v, 29_999))
        }
        assertEquals(30_000, seen.size)
    }

    @Test
    fun decode_wrapsBeyond30000_relativeToLatest() {
        // seq 40000 shares its mark with 10000; with 40005 issued, the newest wins.
        assertEquals(40_000, LabMark.decode(LabMark.encode(40_000).toInt(), 40_005))
        assertEquals(10_000, LabMark.decode(LabMark.encode(10_000).toInt(), 10_000))
    }

    @Test
    fun decode_rejectsNonMarks() {
        assertEquals(-1, LabMark.decode(0, 100))
        assertEquals(-1, LabMark.decode(999, 100))
        assertEquals(-1, LabMark.decode(31_000, 100))
        assertEquals(-1, LabMark.decode(-5000, 100))
        assertEquals(-1, LabMark.decode(LabMark.encode(7).toInt(), -1)) // nothing issued yet
        assertEquals(-1, LabMark.decode(LabMark.encode(7).toInt(), 3))  // not issued yet
    }

    @Test
    fun markAxis_writesAndReadsTheRightAxis() {
        val s = ControllerState(rightX = 5, rightY = 6)
        assertEquals(LabMark.encode(3), MarkAxis.RX.apply(s, 3).rightX)
        assertEquals(6.toShort(), MarkAxis.RX.apply(s, 3).rightY)
        assertEquals(LabMark.encode(3), MarkAxis.RY.apply(s, 3).rightY)
        assertEquals(LabMark.encode(3).toInt(), MarkAxis.RY.read(MarkAxis.RY.apply(s, 3)))
    }
}

class LabRingTest {
    @Test
    fun recordsInOrder_andCountsDropsWhenFull() {
        val ring = LabRing(3)
        repeat(5) { ring.record(it, LabStage.CMD, 100L + it, ControllerState(buttons = it)) }
        assertEquals(3, ring.size)
        assertEquals(2, ring.dropped)
        val csv = StringBuilder().also(ring::writeCsv).toString().trim().lines()
        assertEquals(LabRing.HEADER, csv[0])
        assertEquals(4, csv.size)
        assertTrue(csv[3].startsWith("2,cmd,102,0x2,"))
    }

    @Test
    fun csvRowFormat() {
        val ring = LabRing(4)
        val state = ControllerState(
            buttons = Buttons.A or Buttons.DPAD_UP, leftX = -32768, leftY = 32767,
            rightX = LabMark.encode(7), rightY = -1, leftTrigger = 255, rightTrigger = 0,
        )
        ring.record(7, LabStage.SEND, 1_732_000_000_123_456_789L, state)
        ring.record(-1, LabStage.STATE, 1_732_000_000_123_456_790L, ControllerState.NEUTRAL)
        val lines = StringBuilder().also(ring::writeCsv).toString().lines()
        assertEquals("seq,stage,t_ns,buttons,lx,ly,rx,ry,lt,rt", lines[0])
        assertEquals("7,send,1732000000123456789,0x1001,-32768,32767,${LabMark.encode(7)},-1,255,0", lines[1])
        assertEquals("-1,state,1732000000123456790,0x0,0,0,0,0,0,0", lines[2])
        assertEquals(0x8000, Integer.decode(StringBuilder().also { LabRing.appendRow(it, 0, 0, 1, Buttons.Y, 0, 0, 0, 0, 0, 0) }.split(",")[3]))
    }

    @Test
    fun concurrentWriters_neverLoseOrTearRecords() {
        val ring = LabRing(40_000)
        val threads = (0 until 4).map { w ->
            Thread {
                for (i in 0 until 10_000) ring.record(w * 10_000 + i, LabStage.STATE, 1L + i, ControllerState(buttons = w, leftX = i.toShort()))
            }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertEquals(40_000, ring.size)
        val rows = StringBuilder().also(ring::writeCsv).toString().trim().lines().drop(1)
        assertEquals(40_000, rows.size)
        val seqs = rows.map { it.substringBefore(',').toInt() }.toSet()
        assertEquals(40_000, seqs.size)
        for (row in rows) {
            val f = row.split(',')
            val seq = f[0].toInt()
            assertEquals(seq / 10_000, Integer.decode(f[3]))
            assertEquals(seq % 10_000, f[4].toInt())
        }
    }
}

class LabProbeTest {
    @After
    fun tearDown() = LabProbe.clear()

    @Test
    fun disabled_recordsNothing() {
        val ring = LabRing(10)
        LabProbe.start(ring, WallClock(), MarkAxis.RX)
        LabProbe.stop()
        LabProbe.record(LabStage.STATE, MarkAxis.RX.apply(ControllerState.NEUTRAL, 0))
        assertEquals(0, ring.size)
    }

    @Test
    fun stateAndSend_takeSeqFromTheMark_andSkipRepeats() {
        val ring = LabRing(100)
        LabProbe.start(ring, WallClock(), MarkAxis.RY)
        val s0 = MarkAxis.RY.apply(ControllerState(leftX = 100), 0)
        val s1 = MarkAxis.RY.apply(ControllerState(leftX = 200), 1)
        LabProbe.command(0, s0)
        LabProbe.record(LabStage.STATE, s0)
        LabProbe.record(LabStage.STATE, s0) // republished without change
        LabProbe.record(LabStage.SEND, s0)
        LabProbe.record(LabStage.SEND, s0) // keep-alive
        LabProbe.command(1, s1)
        LabProbe.record(LabStage.STATE, s1)
        LabProbe.record(LabStage.SEND, ControllerState.NEUTRAL)
        val rows = StringBuilder().also(ring::writeCsv).toString().trim().lines().drop(1).map { it.split(',').take(2).joinToString(",") }
        assertEquals(listOf("0,cmd", "0,state", "0,send", "1,cmd", "1,state", "-1,send"), rows)
        assertEquals(ControllerState.NEUTRAL, LabProbe.lastSend)
        assertEquals(0, LabProbe.lastSendSeq)
    }

    @Test
    fun overheadIsMeasured() {
        val ns = LabProbe.measureOverheadNs(5_000)
        assertTrue(ns > 0 && ns < 100_000)
        assertFalse(LabProbe.enabled)
        val warm = LabProbe.measureOverheadNs(20_000)
        println("probe overhead (JVM): cold %.1f ns, default %.1f ns per record".format(java.util.Locale.US, ns, warm))
    }
}

class LabPlanTest {
    private fun params(test: String, seed: Long = 42, avoid: Int = 0) = LabParams(
        test, "t", seed, LabParams.defaultCount(test), LabParams.defaultRate(test), 10, avoidButtons = avoid,
    )

    private fun steps(p: LabParams, max: Int): List<Pair<ControllerState, Long>> {
        val plan = LabPlan.of(p)
        val out = ArrayList<Pair<ControllerState, Long>>()
        var seq = 0
        while (seq < max) {
            val step = plan.next(seq) ?: break
            out += step.state to step.atNs
            seq++
        }
        return out
    }

    @Test
    fun sameSeed_sameSequence_otherSeed_otherSequence() {
        for (test in listOf("latency", "multi", "analog", "throughput", "reliability")) {
            val a = steps(params(test, 7), 3000)
            val b = steps(params(test, 7), 3000)
            assertEquals(test, a, b)
            if (test != "latency") assertNotEquals(test, a, steps(params(test, 8), 3000))
        }
        // Latency: the button is fixed, the timing depends on the seed.
        assertNotEquals(steps(params("latency", 7), 50).map { it.second }, steps(params("latency", 8), 50).map { it.second })
    }

    @Test
    fun everyStepCarriesItsMark() {
        for (test in listOf("latency", "multi", "analog", "reliability")) {
            val p = params(test)
            val plan = LabPlan.of(p)
            steps(p, 5000).forEachIndexed { seq, (state, _) ->
                assertEquals(test, seq, LabMark.decode(plan.mark.read(state), seq))
            }
        }
        assertEquals(MarkAxis.RY, LabPlan.of(params("analog")).mark)
        // The analog test leaves rx alone: it only moves the left stick.
        assertTrue(steps(params("analog"), 2000).all { it.first.rightX.toInt() == 0 })
    }

    @Test
    fun defaults_andCounts() {
        assertEquals(400, steps(params("latency"), 10_000).size) // 200 pulses
        assertEquals(2000, steps(params("analog"), 10_000).size) // 8 s at 250 Hz
        assertEquals(20_000, steps(params("reliability"), 30_000).size)
        assertEquals(500, steps(params("multi"), 10_000).size)
        assertEquals(4_000_000L, steps(params("analog"), 2)[1].second)
        assertEquals(LabStep.ASAP, steps(params("throughput"), 2)[1].second)
    }

    @Test
    fun avoidedInputs_neverUsed() {
        val avoid = Buttons.A or Buttons.LEFT_SHOULDER or Buttons.B
        val p = params("multi", avoid = avoid).copy(avoidLt = true)
        for ((state, _) in steps(p, 500)) {
            assertEquals(0, state.buttons and avoid)
            assertEquals(0, state.leftTrigger)
            assertTrue(Integer.bitCount(state.buttons) in 1..6)
        }
        // Latency picks the first face button not avoided: B and A are, so X.
        assertTrue(steps(params("latency", avoid = avoid), 2)[0].first.buttons == Buttons.X)
    }

    @Test
    fun latency_alternatesPressAndRelease_withSpecIntervals() {
        val s = steps(params("latency"), 400)
        for (i in s.indices) {
            assertEquals(if (i % 2 == 0) Buttons.B else 0, s[i].first.buttons)
            if (i > 0) {
                val gap = (s[i].second - s[i - 1].second) / 1_000_000
                if (i % 2 == 1) assertTrue(gap in 35..70) else assertTrue(gap in 70..180)
            }
        }
    }
}

class LabRunnerTest {
    private class FakeHost : LabHost {
        val touches = ArrayList<ControllerState>()
        var current = ControllerState.NEUTRAL
        override fun touch(state: ControllerState) {
            touches += state
            current = state
            if (LabProbe.enabled) LabProbe.record(LabStage.STATE, state)
            if (LabProbe.enabled) LabProbe.record(LabStage.SEND, state) // a link that sends at once
        }
        override fun release() = touch(ControllerState.NEUTRAL)
        override fun link() = "usb"
        override fun dropLink(link: String) {}
        override fun restoreLink(link: String) {}
    }

    @Test
    fun multiRun_writesPhoneCsvAndDoneJson() {
        val dir = Files.createTempDirectory("lab").toFile()
        val host = FakeHost()
        LabRunner.warmupMs = 10
        val info = LabAppInfo("0.1.0", 1, "abc1234", "Test Phone", "16", 36)
        val params = LabParams("multi", "run1", 5, 20, 0, 10)
        var result: Pair<Boolean, String>? = null
        assertTrue(LabRunner.start(host, params, info, dir) { ok, msg -> result = ok to msg })
        // A second run is refused while the first is active.
        val other = File(dir, "other")
        assertFalse(LabRunner.start(host, params.copy(run = "other"), info, other))
        assertFalse(JSONObject(File(other, "done.json").readText()).getBoolean("ok"))
        LabRunner.join(20_000)
        assertEquals(true, result?.first)
        assertEquals(ControllerState.NEUTRAL, host.current)

        val done = JSONObject(File(dir, "done.json").readText())
        assertTrue(done.getBoolean("ok"))
        assertEquals(21, done.getInt("count")) // 20 steps + final neutral
        assertEquals("abc1234", done.getString("app_commit"))
        assertEquals("usb", done.getString("link"))
        assertEquals("rx", done.getString("mark"))
        assertTrue(done.getLong("ended_ns") > done.getLong("started_ns"))
        assertTrue(done.getDouble("probe_overhead_ns") > 0)

        val rows = File(dir, "phone.csv").readLines()
        assertEquals(LabRing.HEADER, rows[0])
        for (stage in listOf("cmd", "state", "send")) {
            val seqs = rows.drop(1).map { it.split(',') }.filter { it[1] == stage }.map { it[0].toInt() }.filter { it >= 0 }
            assertEquals(stage, (0..20).toList(), seqs)
        }
        assertFalse(LabRunner.running)
    }
}
