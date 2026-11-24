package io.github.pedropasinn.mobilepad.net

import io.github.pedropasinn.mobilepad.protocol.ControllerState
import io.github.pedropasinn.mobilepad.protocol.Wire
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * The USB path end to end on the JVM: [WifiConnection] against a fake companion on loopback
 * (states on the TCP control channel, as through adb reverse). Checks that a state leaves as
 * soon as it changes, not on the keep-alive tick, and prints how long send() -> bytes takes.
 */
class WifiConnectionTest {
    private lateinit var server: ServerSocket
    private lateinit var connection: WifiConnection
    private lateinit var pc: Socket
    private lateinit var input: DataInputStream

    @Before
    fun setUp() {
        server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        connection = WifiConnection(0x0123456789ABCDEFL)
        connection.connect(ConnectTarget(listOf("127.0.0.1"), server.localPort, mobilepad = true, usb = true))
        server.soTimeout = 5000
        pc = server.accept()
        pc.tcpNoDelay = true
        input = DataInputStream(pc.getInputStream())
        val hello = readFrame()
        assertEquals(Wire.TYPE_HELLO, hello.type)
        // Reply: u16 UDP port 0 (USB), OS Windows, "MBP" + extension version.
        val body = byteArrayOf(0, 0, 1) + Wire.EXTENSION_MAGIC + Wire.EXTENSION_VERSION
        val reply = ByteArray(Wire.RX_HEADER_SIZE + body.size)
        reply[0] = reply.size.toByte()
        reply[2] = Wire.MAJOR
        reply[3] = 22
        reply[4] = Wire.TYPE_HELLO.toByte()
        body.copyInto(reply, Wire.RX_HEADER_SIZE)
        pc.getOutputStream().write(reply)
        val deadline = System.currentTimeMillis() + 5000
        while (connection.status.value !is ConnectionStatus.Connected) {
            check(System.currentTimeMillis() < deadline) { "not connected: ${connection.status.value}" }
            Thread.sleep(5)
        }
    }

    @After
    fun tearDown() {
        connection.release()
        runCatching { pc.close() }
        server.close()
    }

    private class Frame(val type: Int, val payload: ByteArray, val receivedAt: Long)

    private fun readFrame(): Frame {
        val lo = input.readUnsignedByte()
        val hi = input.readUnsignedByte()
        val at = System.nanoTime()
        val rest = ByteArray((lo or (hi shl 8)) - 2)
        input.readFully(rest)
        return Frame(rest[4].toInt(), rest.copyOfRange(Wire.HEADER_SIZE - 2, rest.size), at)
    }

    /** Next state frame whose buttons are [buttons]; keep-alive repeats of older states are skipped. */
    private fun awaitState(buttons: Int): Frame {
        while (true) {
            val f = readFrame()
            if (f.type != Wire.TYPE_STATE) continue
            val b = (f.payload[0].toInt() and 0xFF) or ((f.payload[1].toInt() and 0xFF) shl 8)
            if (b == buttons) return f
        }
    }

    @Test
    fun stateChangesLeaveImmediately() {
        awaitState(0) // first (neutral) state or keep-alive: the loop is running
        val latencies = mutableListOf<Double>()
        repeat(300) { i ->
            val buttons = if (i % 2 == 0) 0x1000 else 0x2000
            val sent = System.nanoTime()
            connection.send(ControllerState(buttons = buttons))
            val f = awaitState(buttons)
            latencies += (f.receivedAt - sent) / 1e6
            Thread.sleep(2)
        }
        latencies.sort()
        val p50 = latencies[latencies.size / 2]
        val p99 = latencies[latencies.size * 99 / 100]
        println("send() -> bytes at the PC (loopback, ${latencies.size} states): p50 %.3f ms, p99 %.3f ms, max %.3f ms"
            .format(p50, p99, latencies.last()))
        // The keep-alive repeats every 200 ms: a timer-driven sender would sit near 100 ms.
        assertTrue("median $p50 ms", p50 < 20.0)
    }

    @Test
    fun idleLinkGetsKeepAlive() {
        awaitState(0)
        pc.soTimeout = 1000
        val first = awaitState(0)
        val second = try { awaitState(0) } catch (e: SocketTimeoutException) { throw AssertionError("no keep-alive", e) }
        val gapMs = (second.receivedAt - first.receivedAt) / 1e6
        assertTrue("keep-alive gap $gapMs ms", gapMs in 100.0..600.0)
    }
}
