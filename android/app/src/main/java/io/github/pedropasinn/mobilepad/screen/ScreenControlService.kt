package io.github.pedropasinn.mobilepad.screen

import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass
import io.github.pedropasinn.mobilepad.IScreenControl
import kotlin.system.exitProcess

/**
 * Shizuku UserService, runs as shell in its own process: the Bluetooth calls that need
 * BLUETOOTH_PRIVILEGED (pairing with the Switch, the gamepad class of device).
 */
class ScreenControlService : IScreenControl.Stub() {
    init {
        if (Build.VERSION.SDK_INT >= 28) HiddenApiBypass.addHiddenApiExemptions("")
    }

    override fun confirmPairing(address: String): Boolean = PrivilegedBluetooth.confirmPairing(address)

    override fun setDeviceClass(deviceClass: Int): Int = PrivilegedBluetooth.setDeviceClass(deviceClass)

    override fun destroy() {
        exitProcess(0)
    }
}
