package io.github.pedropasinn.mobilepad

import android.content.Context
import java.security.SecureRandom

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("mobilepad", Context.MODE_PRIVATE)

    /** Random identifier sent in the handshake, stable across launches like the original. */
    val deviceId: Long
        get() {
            if (!prefs.contains(KEY_DEVICE_ID)) {
                prefs.edit().putLong(KEY_DEVICE_ID, SecureRandom().nextLong()).apply()
            }
            return prefs.getLong(KEY_DEVICE_ID, 0L)
        }

    var lastTarget: String
        get() = prefs.getString(KEY_LAST_TARGET, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_TARGET, value).apply()

    var macroJson: String
        get() = prefs.getString(KEY_MACROS, null) ?: io.github.pedropasinn.mobilepad.macro.MacroConfig.EXAMPLE
        set(value) = prefs.edit().putString(KEY_MACROS, value).apply()

    /** Bluetooth gamepad mode stays on across launches once enabled. */
    var bluetoothEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLUETOOTH, false)
        set(value) = prefs.edit().putBoolean(KEY_BLUETOOTH, value).apply()

    /** Bluetooth profile id: GenericGamepadProfile.ID or SwitchProProfile.ID. */
    var bluetoothProfile: String
        get() = prefs.getString(KEY_BLUETOOTH_PROFILE, null) ?: io.github.pedropasinn.mobilepad.bt.GenericGamepadProfile.ID
        set(value) = prefs.edit().putString(KEY_BLUETOOTH_PROFILE, value).apply()

    /** Class of device the phone had before Switch mode changed it, or -1. */
    var originalDeviceClass: Int
        get() = prefs.getInt(KEY_ORIGINAL_CLASS, -1)
        set(value) = prefs.edit().putInt(KEY_ORIGINAL_CLASS, value).apply()

    /** Motion options as JSON (MotionSettings); missing means everything off. */
    var motionJson: String?
        get() = prefs.getString(KEY_MOTION, null)
        set(value) = prefs.edit().putString(KEY_MOTION, value).apply()

    /** Phone vibration for the game's rumble (companions, Bluetooth Pro Controller). */
    var rumble: Boolean
        get() = prefs.getBoolean(KEY_RUMBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_RUMBLE, value).apply()

    var orientation: ScreenOrientation
        get() = runCatching { ScreenOrientation.valueOf(prefs.getString(KEY_ORIENTATION, null)!!) }
            .getOrDefault(ScreenOrientation.LANDSCAPE)
        set(value) = prefs.edit().putString(KEY_ORIENTATION, value.name).apply()

    /** Colour theme: dark (default) or OLED black. */
    var theme: io.github.pedropasinn.mobilepad.ui.AppTheme
        get() = runCatching { io.github.pedropasinn.mobilepad.ui.AppTheme.valueOf(prefs.getString(KEY_THEME, null)!!) }
            .getOrDefault(io.github.pedropasinn.mobilepad.ui.AppTheme.DARK)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    /** Look of the on-screen controller. */
    var skin: io.github.pedropasinn.mobilepad.ui.SkinId
        get() = runCatching { io.github.pedropasinn.mobilepad.ui.SkinId.valueOf(prefs.getString(KEY_SKIN, null)!!) }
            .getOrDefault(io.github.pedropasinn.mobilepad.ui.SkinId.VIOLET)
        set(value) = prefs.edit().putString(KEY_SKIN, value.name).apply()

    /** Imported Delta skin in use (folder name); empty: the built-in skin. */
    var deltaSkin: String
        get() = prefs.getString(KEY_DELTA_SKIN, null) ?: ""
        set(value) = prefs.edit().putString(KEY_DELTA_SKIN, value).apply()

    /** Button remapping as "source:target,…" masks; empty: none. */
    var remap: Map<Int, Int>
        get() = (prefs.getString(KEY_REMAP, null) ?: "").split(',').mapNotNull { pair ->
            val (a, b) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            (a.toIntOrNull() ?: return@mapNotNull null) to (b.toIntOrNull() ?: return@mapNotNull null)
        }.toMap()
        set(value) = prefs.edit().putString(KEY_REMAP, value.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()

    /** Controller layout for landscape and for portrait (see PadLayout.encode). */
    var layoutLandscape: io.github.pedropasinn.mobilepad.ui.PadLayout
        get() = io.github.pedropasinn.mobilepad.ui.PadLayout.decode(prefs.getString(KEY_LAYOUT_LAND, null))
        set(value) = prefs.edit().putString(KEY_LAYOUT_LAND, value.encode()).apply()
    var layoutPortrait: io.github.pedropasinn.mobilepad.ui.PadLayout
        get() = io.github.pedropasinn.mobilepad.ui.PadLayout.decode(prefs.getString(KEY_LAYOUT_PORT, null))
        set(value) = prefs.edit().putString(KEY_LAYOUT_PORT, value.encode()).apply()

    /** Names printed on the controller's buttons. */
    var buttonNames: io.github.pedropasinn.mobilepad.ui.ButtonNames
        get() = runCatching { io.github.pedropasinn.mobilepad.ui.ButtonNames.valueOf(prefs.getString(KEY_BUTTON_NAMES, null)!!) }
            .getOrDefault(io.github.pedropasinn.mobilepad.ui.ButtonNames.AUTO)
        set(value) = prefs.edit().putString(KEY_BUTTON_NAMES, value.name).apply()

    /** Saved game profiles (see GameProfile) and the one in use ("" none). */
    var profiles: List<GameProfile>
        get() = GameProfile.listFromJson(prefs.getString(KEY_PROFILES, null))
        set(value) = prefs.edit().putString(KEY_PROFILES, GameProfile.listToJson(value)).apply()
    var activeProfile: String
        get() = prefs.getString(KEY_ACTIVE_PROFILE, null) ?: ""
        set(value) = prefs.edit().putString(KEY_ACTIVE_PROFILE, value).apply()

    /** The other layout of each orientation, swapped in from the controller (e.g. menus ⇄ race). */
    var altLandscape: io.github.pedropasinn.mobilepad.ui.PadLayout
        get() = io.github.pedropasinn.mobilepad.ui.PadLayout.decode(prefs.getString(KEY_ALT_LAND, null))
        set(value) = prefs.edit().putString(KEY_ALT_LAND, value.encode()).apply()
    var altPortrait: io.github.pedropasinn.mobilepad.ui.PadLayout
        get() = io.github.pedropasinn.mobilepad.ui.PadLayout.decode(prefs.getString(KEY_ALT_PORT, null))
        set(value) = prefs.edit().putString(KEY_ALT_PORT, value.encode()).apply()

    /** App language: "" follows the system, otherwise a tag from MobilepadApp.LANGUAGES. */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, null) ?: ""
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /** The sensor's centring hint was shown once. */
    var motionHintShown: Boolean
        get() = prefs.getBoolean(KEY_MOTION_HINT, false)
        set(value) = prefs.edit().putBoolean(KEY_MOTION_HINT, value).apply()

    private companion object {
        const val KEY_ORIENTATION = "orientation"
        const val KEY_THEME = "theme"
        const val KEY_SKIN = "skin"
        const val KEY_REMAP = "remap"
        const val KEY_PROFILES = "profiles"
        const val KEY_ACTIVE_PROFILE = "active_profile"
        const val KEY_BUTTON_NAMES = "button_names"
        const val KEY_LAYOUT_LAND = "layout_land"
        const val KEY_LAYOUT_PORT = "layout_port"
        const val KEY_ALT_LAND = "layout_alt_land"
        const val KEY_ALT_PORT = "layout_alt_port"
        const val KEY_DELTA_SKIN = "delta_skin"
        const val KEY_LANGUAGE = "language"
        const val KEY_MOTION_HINT = "motion_hint"
        const val KEY_RUMBLE = "rumble"
        const val KEY_MOTION = "motion"
        const val KEY_ORIGINAL_CLASS = "original_device_class"
        const val KEY_BLUETOOTH_PROFILE = "bluetooth_profile"
        const val KEY_BLUETOOTH = "bluetooth_enabled"
        const val KEY_MACROS = "macros_v2"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_LAST_TARGET = "last_target"
    }
}

/** Screen orientation of the app. [LANDSCAPE]: cameras on the left, USB-C on the right. */
enum class ScreenOrientation(val android: Int) {
    LANDSCAPE(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    REVERSE(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE),
    /** Turns with the phone only when Android's auto-rotate is on. */
    SYSTEM(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE),
    /** Game Boy-style vertical layout. */
    PORTRAIT(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
}
