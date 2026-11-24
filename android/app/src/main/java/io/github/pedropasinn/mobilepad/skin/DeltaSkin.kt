package io.github.pedropasinn.mobilepad.skin

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import io.github.pedropasinn.mobilepad.protocol.Buttons
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Controller skins in the `.deltaskin` format of the Delta emulator (also used by community sites
 * such as deltastyles.com): a zip with `info.json` and the artwork (PDF or PNG). Only the layout
 * and artwork are used; the game screen area shows the connection status instead.
 */
class DeltaSkin(val dir: File, val name: String, val gameType: String, private val json: JSONObject) {

    /** Short console name from the game type, e.g. "com.rileytestut.delta.game.gba" → "GBA". */
    val console: String get() = gameType.substringAfterLast('.').uppercase().let { if (it == "GENESIS") "Mega Drive" else it }

    fun hasLayout(landscape: Boolean) = representation(landscape) != null

    /** Phone layouts first (edge-to-edge, then classic), then iPad. */
    fun representation(landscape: Boolean): Layout? {
        val reps = json.optJSONObject("representations") ?: return null
        val orientation = if (landscape) "landscape" else "portrait"
        for ((device, display) in CANDIDATES) {
            val o = reps.optJSONObject(device)?.optJSONObject(display)?.optJSONObject(orientation) ?: continue
            return runCatching { Layout.parse(o, gameType) }.getOrNull() ?: continue
        }
        return null
    }

    class Layout(
        val mappingWidth: Float,
        val mappingHeight: Float,
        /** Artwork file inside the skin folder (PDF or PNG). */
        val asset: String,
        val translucent: Boolean,
        val items: List<Item>,
        /** Game screen areas, in mapping points. */
        val screens: List<Rect>,
    ) {
        companion object {
            fun parse(o: JSONObject, gameType: String): Layout? {
                val size = o.optJSONObject("mappingSize") ?: return null
                val assets = o.optJSONObject("assets") ?: return null
                val asset = listOf("resizable", "large", "medium", "small").firstNotNullOfOrNull { assets.optString(it).ifEmpty { null } } ?: return null
                val defaultEdges = Edges.parse(o.optJSONObject("extendedEdges"))
                val items = mutableListOf<Item>()
                val list = o.optJSONArray("items") ?: JSONArray()
                for (i in 0 until list.length()) {
                    Item.parse(list.getJSONObject(i), defaultEdges, gameType)?.let(items::add)
                }
                val screens = mutableListOf<Rect>()
                o.optJSONArray("screens")?.let { s ->
                    for (i in 0 until s.length()) Rect.parse(s.getJSONObject(i).optJSONObject("outputFrame"))?.let(screens::add)
                }
                Rect.parse(o.optJSONObject("gameScreenFrame"))?.let(screens::add)
                return Layout(
                    size.getDouble("width").toFloat(), size.getDouble("height").toFloat(),
                    asset, o.optBoolean("translucent", false), items, screens,
                )
            }
        }
    }

    data class Rect(val x: Float, val y: Float, val width: Float, val height: Float) {
        val centerX get() = x + width / 2
        val centerY get() = y + height / 2
        fun contains(px: Float, py: Float, e: Edges = Edges.NONE) =
            px >= x - e.left && px <= x + width + e.right && py >= y - e.top && py <= y + height + e.bottom

        companion object {
            fun parse(o: JSONObject?): Rect? = o?.let {
                Rect(it.optDouble("x", 0.0).toFloat(), it.optDouble("y", 0.0).toFloat(),
                    it.optDouble("width", 0.0).toFloat(), it.optDouble("height", 0.0).toFloat())
            }
        }
    }

    data class Edges(val top: Float, val bottom: Float, val left: Float, val right: Float) {
        companion object {
            val NONE = Edges(0f, 0f, 0f, 0f)
            fun parse(o: JSONObject?, fallback: Edges = NONE): Edges = if (o == null) fallback else Edges(
                o.optDouble("top", fallback.top.toDouble()).toFloat(),
                o.optDouble("bottom", fallback.bottom.toDouble()).toFloat(),
                o.optDouble("left", fallback.left.toDouble()).toFloat(),
                o.optDouble("right", fallback.right.toDouble()).toFloat(),
            )
        }
    }

    /**
     * A touch area of the skin. [buttons]: actions while touched. [directions]: a D-pad or stick,
     * split by angle from the centre; [thumbstick] makes it analog (with its knob artwork).
     */
    class Item(
        val frame: Rect,
        val edges: Edges,
        val buttons: List<Action>,
        val directions: Directions?,
        val thumbstick: Thumbstick?,
    ) {
        companion object {
            fun parse(o: JSONObject, defaultEdges: Edges, gameType: String): Item? {
                val frame = Rect.parse(o.optJSONObject("frame")) ?: return null
                val edges = Edges.parse(o.optJSONObject("extendedEdges"), defaultEdges)
                val thumb = o.optJSONObject("thumbstick")?.let {
                    Thumbstick(it.optString("name"), it.optDouble("width", 0.0).toFloat(), it.optDouble("height", 0.0).toFloat())
                }
                return when (val inputs = o.opt("inputs")) {
                    is JSONArray -> {
                        val actions = (0 until inputs.length()).mapNotNull { action(inputs.getString(it), gameType) }
                        if (actions.isEmpty()) null else Item(frame, edges, actions, null, null)
                    }
                    is JSONObject -> {
                        val d = Directions(
                            inputs.optString("up").let { action(it, gameType) },
                            inputs.optString("down").let { action(it, gameType) },
                            inputs.optString("left").let { action(it, gameType) },
                            inputs.optString("right").let { action(it, gameType) },
                        )
                        if (d.all.isEmpty()) null else Item(frame, edges, emptyList(), d, thumb)
                    }
                    else -> null
                }
            }
        }
    }

    class Directions(val up: Action?, val down: Action?, val left: Action?, val right: Action?) {
        val all get() = listOfNotNull(up, down, left, right)
        /** Analog stick that the directions drive, if they are stick directions. */
        val stick: Stick? get() = all.firstNotNullOfOrNull { (it as? Action.Axis)?.stick }
    }

    class Thumbstick(val image: String, val width: Float, val height: Float)

    enum class Stick { LEFT, RIGHT }

    /** What a skin input does on the controller. */
    sealed class Action {
        data class Press(val mask: Int) : Action()
        data object LeftTrigger : Action()
        data object RightTrigger : Action()
        /** Full deflection of a stick: x/y in -1..1, up positive. */
        data class Axis(val stick: Stick, val x: Float, val y: Float) : Action()
        /** Opens Mobilepad's own menu. */
        data object Menu : Action()
    }

    companion object {
        private val CANDIDATES = listOf(
            "iphone" to "edgeToEdge", "iphone" to "standard", "ipad" to "standard", "ipad" to "splitView",
        )

        /**
         * Maps a Delta input name to the controller. Face buttons follow their position (Nintendo
         * printing: A east, B south, X north, Y west), the convention of the app's Nintendo layout.
         */
        fun action(input: String, gameType: String): Action? {
            val genesis = gameType.endsWith("genesis") || gameType.endsWith("megadrive")
            val n64 = gameType.endsWith("n64")
            fun press(mask: Int) = Action.Press(mask)
            return when (input.lowercase()) {
                "a" -> when { genesis -> press(Buttons.X); n64 -> press(Buttons.A); else -> press(Buttons.B) }
                "b" -> when { genesis -> press(Buttons.A); n64 -> press(Buttons.X); else -> press(Buttons.A) }
                "c" -> press(Buttons.B)
                "x" -> if (genesis) press(Buttons.LEFT_SHOULDER) else press(Buttons.Y)
                "y" -> if (genesis) press(Buttons.Y) else press(Buttons.X)
                "z" -> if (genesis) press(Buttons.RIGHT_SHOULDER) else Action.LeftTrigger
                "cross" -> press(Buttons.A)
                "circle" -> press(Buttons.B)
                "square" -> press(Buttons.X)
                "triangle" -> press(Buttons.Y)
                "l", "l1" -> press(Buttons.LEFT_SHOULDER)
                "r", "r1" -> press(Buttons.RIGHT_SHOULDER)
                "zl", "l2" -> Action.LeftTrigger
                "zr", "r2" -> Action.RightTrigger
                "l3" -> press(Buttons.LEFT_THUMB)
                "r3" -> press(Buttons.RIGHT_THUMB)
                "start", "plus" -> press(Buttons.START)
                "select", "minus", "mode", "back" -> press(Buttons.BACK)
                "home", "guide" -> press(Buttons.GUIDE)
                "menu" -> Action.Menu
                "up" -> press(Buttons.DPAD_UP)
                "down" -> press(Buttons.DPAD_DOWN)
                "left" -> press(Buttons.DPAD_LEFT)
                "right" -> press(Buttons.DPAD_RIGHT)
                "analogstickup" -> Action.Axis(Stick.LEFT, 0f, 1f)
                "analogstickdown" -> Action.Axis(Stick.LEFT, 0f, -1f)
                "analogstickleft" -> Action.Axis(Stick.LEFT, -1f, 0f)
                "analogstickright" -> Action.Axis(Stick.LEFT, 1f, 0f)
                "cup", "rightanalogstickup" -> Action.Axis(Stick.RIGHT, 0f, 1f)
                "cdown", "rightanalogstickdown" -> Action.Axis(Stick.RIGHT, 0f, -1f)
                "cleft", "rightanalogstickleft" -> Action.Axis(Stick.RIGHT, -1f, 0f)
                "cright", "rightanalogstickright" -> Action.Axis(Stick.RIGHT, 1f, 0f)
                // Emulator functions (fast forward, save states, touch screen…) have no controller equivalent.
                else -> null
            }
        }

        fun load(dir: File): DeltaSkin? = runCatching {
            val json = JSONObject(File(dir, "info.json").readText())
            DeltaSkin(dir, json.optString("name", dir.name), json.optString("gameTypeIdentifier"), json)
        }.getOrNull()
    }
}

/** Imported skins, one folder each under `files/skins`. */
class DeltaSkinLibrary(root: File) {
    private val folder = File(root, "skins").apply { mkdirs() }
    private val _skins = MutableStateFlow<List<DeltaSkin>>(emptyList())
    val skins: StateFlow<List<DeltaSkin>> = _skins.asStateFlow()

    class ImportResult(val added: Int, val skipped: Int)

    suspend fun load() = withContext(Dispatchers.IO) { _skins.value = scan() }

    fun find(id: String): DeltaSkin? = if (id.isEmpty()) null else _skins.value.firstOrNull { it.dir.name == id }

    suspend fun import(resolver: ContentResolver, uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        var added = 0
        var skipped = 0
        for (uri in uris) if (runCatching { extract(resolver, uri) }.getOrDefault(false)) added++ else skipped++
        _skins.value = scan()
        ImportResult(added, skipped)
    }

    suspend fun delete(skin: DeltaSkin) = withContext(Dispatchers.IO) {
        skin.dir.deleteRecursively()
        _skins.value = scan()
    }

    private fun scan(): List<DeltaSkin> =
        folder.listFiles()?.filter { it.isDirectory }?.mapNotNull { DeltaSkin.load(it) }?.sortedBy { it.name.lowercase() } ?: emptyList()

    /**
     * Unzips into a temporary folder, then moves it in place once `info.json` parses and has at
     * least one layout. Zips made on a Mac may nest everything in a folder and add `__MACOSX`.
     */
    private fun extract(resolver: ContentResolver, uri: Uri): Boolean {
        val temp = File(folder, ".import-${System.nanoTime()}")
        try {
            var total = 0L
            resolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name.replace('\\', '/')
                        if (entry.isDirectory || name.startsWith("__MACOSX/") || name.split('/').any { it == ".." }) continue
                        val out = File(temp, name)
                        if (!out.canonicalPath.startsWith(temp.canonicalPath + File.separator)) continue
                        out.parentFile?.mkdirs()
                        out.outputStream().use { o ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = zip.read(buffer)
                                if (n < 0) break
                                total += n
                                if (total > MAX_BYTES) return false
                                o.write(buffer, 0, n)
                            }
                        }
                    }
                }
            } ?: return false
            val info = temp.walkTopDown().filter { it.name == "info.json" }.minByOrNull { it.path.length } ?: return false
            val base = info.parentFile ?: return false
            val skin = DeltaSkin.load(base) ?: return false
            if (!skin.hasLayout(true) && !skin.hasLayout(false)) return false
            val id = (JSONObject(info.readText()).optString("identifier").ifEmpty { displayName(resolver, uri) })
                .replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
            val target = File(folder, id)
            target.deleteRecursively()
            return base.renameTo(target) || base.copyRecursively(target, overwrite = true)
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }?.substringBeforeLast('.') ?: "skin-${System.currentTimeMillis()}"

    companion object {
        private const val MAX_BYTES = 150L * 1024 * 1024
    }
}
