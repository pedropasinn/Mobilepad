package io.github.pedropasinn.mobilepad.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.HelpCenter
import androidx.compose.material.icons.rounded.Moving
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Looks3
import androidx.compose.material.icons.rounded.Looks4
import androidx.compose.material.icons.rounded.Looks5
import androidx.compose.material.icons.rounded.Looks6
import androidx.compose.material.icons.rounded.LooksOne
import androidx.compose.material.icons.rounded.LooksTwo
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsMartialArts
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icons for radial-menu slices. A slice stores the key (e.g. "heal"); configurations made with
 * emoji keep working: each emoji of the old picker maps to its icon. Any other text is drawn as is.
 */
object SliceIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "heal" to Icons.Rounded.Favorite,
        "bandage" to Icons.Rounded.Healing,
        "potion" to Icons.Rounded.Science,
        "bomb" to Icons.Rounded.Dangerous,
        "fire" to Icons.Rounded.LocalFireDepartment,
        "ice" to Icons.Rounded.AcUnit,
        "bolt" to Icons.Rounded.Bolt,
        "aim" to Icons.Rounded.GpsFixed,
        "melee" to Icons.Rounded.SportsMartialArts,
        "shield" to Icons.Rounded.Shield,
        "reload" to Icons.Rounded.Refresh,
        "swap" to Icons.Rounded.SwapHoriz,
        "bag" to Icons.Rounded.Backpack,
        "inventory" to Icons.Rounded.Inventory2,
        "map" to Icons.Rounded.Map,
        "compass" to Icons.Rounded.Explore,
        "flag" to Icons.Rounded.Flag,
        "save" to Icons.Rounded.Save,
        "camera" to Icons.Rounded.CameraAlt,
        "screenshot" to Icons.Rounded.Screenshot,
        "pause" to Icons.Rounded.Pause,
        "play" to Icons.Rounded.PlayArrow,
        "menu" to Icons.Rounded.Menu,
        "light" to Icons.Rounded.FlashlightOn,
        "look" to Icons.Rounded.Visibility,
        "run" to Icons.Rounded.DirectionsRun,
        "jump" to Icons.Rounded.Height,
        "hand" to Icons.Rounded.PanTool,
        "car" to Icons.Rounded.DirectionsCar,
        // Racing.
        "gas" to Icons.Rounded.Speed,
        "brake" to Icons.Rounded.KeyboardDoubleArrowDown,
        "drift" to Icons.Rounded.Moving,
        "item" to Icons.Rounded.HelpCenter,
        "up" to Icons.Rounded.ArrowUpward,
        "down" to Icons.Rounded.ArrowDownward,
        "left" to Icons.Rounded.ArrowBack,
        "right" to Icons.Rounded.ArrowForward,
        "rear" to Icons.Rounded.FlipCameraAndroid,
        "mount" to Icons.Rounded.Pets,
        "build" to Icons.Rounded.Build,
        "tool" to Icons.Rounded.Handyman,
        "key" to Icons.Rounded.Key,
        "door" to Icons.Rounded.MeetingRoom,
        "food" to Icons.Rounded.Restaurant,
        "money" to Icons.Rounded.Paid,
        "star" to Icons.Rounded.Star,
        "like" to Icons.Rounded.ThumbUp,
        "chat" to Icons.Rounded.Chat,
        "mic" to Icons.Rounded.Mic,
        "volume" to Icons.Rounded.VolumeUp,
        "mute" to Icons.Rounded.VolumeOff,
        "music" to Icons.Rounded.MusicNote,
        "timer" to Icons.Rounded.Timer,
        "home" to Icons.Rounded.Home,
        "settings" to Icons.Rounded.Settings,
        "1" to Icons.Rounded.LooksOne,
        "2" to Icons.Rounded.LooksTwo,
        "3" to Icons.Rounded.Looks3,
        "4" to Icons.Rounded.Looks4,
        "5" to Icons.Rounded.Looks5,
        "6" to Icons.Rounded.Looks6,
        "phone" to Icons.Rounded.Smartphone,
        "gamepad" to Icons.Rounded.SportsEsports,
        "folder" to Icons.Rounded.Folder,
        "add" to Icons.Rounded.Add,
    )
    private val byKey = all.toMap()

    /** The emoji of earlier versions, without variation selectors. */
    private val legacy = mapOf(
        "❤" to "heal", "🩹" to "bandage", "🧪" to "potion", "💣" to "bomb", "🔫" to "aim", "🗡" to "melee",
        "🏹" to "aim", "🛡" to "shield", "🔄" to "reload", "🎒" to "bag", "🗺" to "map", "🧭" to "compass",
        "💾" to "save", "📷" to "camera", "⏸" to "pause", "▶" to "play", "🔦" to "light", "🔥" to "fire",
        "❄" to "ice", "⚡" to "bolt", "💀" to "bomb", "🎯" to "aim", "🏃" to "run", "🦘" to "jump",
        "👊" to "melee", "✋" to "hand", "🚗" to "car", "🐎" to "mount", "🔊" to "volume", "🔇" to "mute",
        "⚙" to "settings", "🏠" to "home", "💬" to "chat", "👍" to "like", "⭐" to "star", "🔑" to "key",
        "🚪" to "door", "⛏" to "build", "🪓" to "tool", "🍖" to "food", "💰" to "money", "🎵" to "music",
        "1⃣" to "1", "2⃣" to "2", "3⃣" to "3", "4⃣" to "4", "5⃣" to "5", "6⃣" to "6",
        "📱" to "phone", "🎮" to "gamepad", "📁" to "folder", "➕" to "add",
    )

    /** The icon for a slice's icon field, or null to draw the text itself (or nothing). */
    fun resolve(icon: String): ImageVector? {
        if (icon.isEmpty()) return null
        byKey[icon]?.let { return it }
        val bare = icon.replace("️", "").trim()
        return legacy[bare]?.let { byKey[it] }
    }
}
