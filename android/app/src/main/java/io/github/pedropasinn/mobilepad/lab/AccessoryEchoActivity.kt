package io.github.pedropasinn.mobilepad.lab

import android.app.Activity
import android.content.Context
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.ServerSocket

/**
 * Lab spike (not a transport yet): echoes every packet back, over the USB accessory (AOA) and,
 * for comparison, over TCP port 47900 (reached from the PC with `adb forward`). The PC measures
 * round trips on both with the same packets, so the direct cable can be compared with adb.
 */
class AccessoryEchoActivity : Activity() {
    private lateinit var label: TextView
    @Volatile private var fd: ParcelFileDescriptor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        label = TextView(this).apply { textSize = 18f; gravity = Gravity.CENTER; setPadding(48, 48, 48, 48) }
        setContentView(label)
        startTcpEcho()
        openFrom(intent.accessory())
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openFrom(intent.accessory())
    }

    private fun android.content.Intent.accessory(): UsbAccessory? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra(UsbManager.EXTRA_ACCESSORY)

    private fun openFrom(given: UsbAccessory?) {
        val usb = getSystemService(Context.USB_SERVICE) as UsbManager
        val accessory = given ?: usb.accessoryList?.firstOrNull()
        if (accessory == null) { show("No USB accessory (AOA). TCP echo on 47900."); return }
        if (!usb.hasPermission(accessory)) {
            // Opened by hand (not by the attach intent): ask, then come back here.
            val action = "$packageName.AOA_PERMISSION"
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context, i: android.content.Intent) {
                    unregisterReceiver(this)
                    if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) openFrom(accessory)
                    else show("USB accessory permission denied.")
                }
            }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, android.content.IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(receiver, android.content.IntentFilter(action))
            val pending = android.app.PendingIntent.getBroadcast(this, 0,
                android.content.Intent(action).setPackage(packageName), android.app.PendingIntent.FLAG_MUTABLE)
            usb.requestPermission(accessory, pending)
            show("Waiting for the USB accessory permission…")
            return
        }
        val pfd = runCatching { usb.openAccessory(accessory) }.getOrNull()
        if (pfd == null) { show("Could not open the accessory."); return }
        fd = pfd
        show("AOA echo running · ${accessory.manufacturer} ${accessory.model}\nTCP echo on 47900")
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            val input = FileInputStream(pfd.fileDescriptor)
            val output = FileOutputStream(pfd.fileDescriptor)
            val buffer = ByteArray(16384)
            var count = 0L
            try {
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    count++
                }
            } catch (e: IOException) {
                Log.i(TAG, "AOA echo ended after $count packets: ${e.message}")
            }
            runOnUiThread { show("AOA echo ended ($count packets)") }
        }, "aoa-echo").start()
    }

    private fun startTcpEcho() {
        if (tcpStarted) return
        tcpStarted = true
        Thread({
            try {
                ServerSocket(47900).use { server ->
                    while (true) {
                        val socket = server.accept().apply { tcpNoDelay = true }
                        Thread({
                            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
                            socket.use {
                                val input = it.getInputStream()
                                val output = it.getOutputStream()
                                val buffer = ByteArray(16384)
                                try {
                                    while (true) {
                                        val n = input.read(buffer)
                                        if (n < 0) break
                                        output.write(buffer, 0, n)
                                        output.flush()
                                    }
                                } catch (_: IOException) { }
                            }
                        }, "tcp-echo").start()
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "TCP echo: ${e.message}")
            }
        }, "tcp-echo-server").start()
    }

    private fun show(text: String) {
        Log.i(TAG, text)
        label.text = text
    }

    override fun onDestroy() {
        runCatching { fd?.close() }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AccessoryEcho"
        @Volatile private var tcpStarted = false
    }
}
