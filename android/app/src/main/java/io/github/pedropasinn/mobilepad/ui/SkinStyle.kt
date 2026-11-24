package io.github.pedropasinn.mobilepad.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import io.github.pedropasinn.mobilepad.protocol.Buttons
import io.github.pedropasinn.mobilepad.skin.DeltaSkin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * What the controller is drawn with: the colours of the shell and buttons, plus, for an imported
 * Delta skin, the artwork of its buttons cut out of the skin. The Mobilepad layout stays the same
 * (a phone controller has no game screen); only the look is taken from the skin.
 */
@Immutable
class SkinStyle(
    val name: String,
    val colors: Skin,
    /** Button artwork by [Part] key. */
    val parts: Map<Int, ImageBitmap> = emptyMap(),
)

/** Keys of [SkinStyle.parts]: a [Buttons] mask for single buttons, or one of these. */
object Part {
    const val DPAD = -1
    const val MENU = -2
    const val LT = -3
    const val RT = -4
    const val STICK = -5
}

val LocalSkinParts = staticCompositionLocalOf<Map<Int, ImageBitmap>> { emptyMap() }

fun builtInStyle(id: SkinId, name: String) = SkinStyle(name, Skins.getValue(id))

/** Loads (once, then from memory) the style of an imported skin; null while loading or if it fails. */
@Composable
fun rememberDeltaStyle(skin: DeltaSkin?): SkinStyle? {
    val style by produceState(skin?.let { DeltaStyles.cached(it) }, skin) {
        value = skin?.let { DeltaStyles.load(it) }
    }
    return style
}

object DeltaStyles {
    private val cache = HashMap<String, SkinStyle?>()

    private fun key(skin: DeltaSkin) = skin.dir.path + "@" + skin.dir.lastModified()

    fun cached(skin: DeltaSkin): SkinStyle? = synchronized(cache) { cache[key(skin)] }

    suspend fun load(skin: DeltaSkin): SkinStyle? = withContext(Dispatchers.IO) {
        val k = key(skin)
        synchronized(cache) { if (cache.containsKey(k)) return@withContext cache[k] }
        val style = runCatching { build(skin) }.getOrNull()
        synchronized(cache) { cache[k] = style }
        style
    }

    /** Landscape artwork first (closest to the controller's shape), at 3 px per point. */
    private fun build(skin: DeltaSkin): SkinStyle? {
        val layout = skin.representation(true) ?: skin.representation(false) ?: return null
        val scale = 3f
        val w = (layout.mappingWidth * scale).toInt()
        val h = (layout.mappingHeight * scale).toInt()
        val art = renderBitmap(File(skin.dir, layout.asset), w, h) ?: return null

        fun px(r: DeltaSkin.Rect) = android.graphics.Rect(
            (r.x * scale).toInt().coerceIn(0, w - 1), (r.y * scale).toInt().coerceIn(0, h - 1),
            ((r.x + r.width) * scale).toInt().coerceIn(1, w), ((r.y + r.height) * scale).toInt().coerceIn(1, h),
        )
        val holes = (layout.items.map { px(it.frame) } + layout.screens.map { px(it) })

        // Cut out the artwork of each control the skin draws.
        val parts = HashMap<Int, ImageBitmap>()
        for (item in layout.items) {
            val key = when {
                item.thumbstick != null -> null
                item.directions != null -> if (item.directions.stick == null) Part.DPAD else null
                item.buttons.size == 1 -> when (val a = item.buttons[0]) {
                    is DeltaSkin.Action.Press -> a.mask.takeIf { Integer.bitCount(it) == 1 && it and DPAD_BITS == 0 }
                    DeltaSkin.Action.LeftTrigger -> Part.LT
                    DeltaSkin.Action.RightTrigger -> Part.RT
                    DeltaSkin.Action.Menu -> Part.MENU
                    else -> null
                }
                else -> null
            } ?: continue
            if (key in parts) continue
            val r = px(item.frame)
            if (r.width() < 4 || r.height() < 4) continue
            parts[key] = Bitmap.createBitmap(art, r.left, r.top, r.width(), r.height()).asImageBitmap()
        }
        layout.items.firstOrNull { it.thumbstick != null }?.thumbstick?.let { t ->
            val tw = max(8, (t.width * scale).toInt())
            val th = max(8, (t.height * scale).toInt())
            renderBitmap(File(skin.dir, t.image), tw, th)?.let { parts[Part.STICK] = it.asImageBitmap() }
        }

        // Colours: the shell from bands at the top and bottom (controls and screen left out), the
        // buttons from the middle of the face buttons.
        val shellTop = average(art, android.graphics.Rect(0, 0, w, (h * 0.18f).toInt()), holes)
        val shellBottom = average(art, android.graphics.Rect(0, (h * 0.82f).toInt(), w, h), holes)
        val faceKeys = listOf(Buttons.B, Buttons.A, Buttons.Y, Buttons.X, Buttons.START)
        val faceItem = faceKeys.firstNotNullOfOrNull { k ->
            layout.items.firstOrNull { (it.buttons.singleOrNull() as? DeltaSkin.Action.Press)?.mask == k }
        } ?: layout.items.firstOrNull { it.directions != null && it.directions.stick == null }
        val button = faceItem?.let { item ->
            val r = px(item.frame)
            val inset = android.graphics.Rect(r.centerX() - r.width() / 5, r.centerY() - r.height() / 5, r.centerX() + r.width() / 5, r.centerY() + r.height() / 5)
            average(art, inset, emptyList())
        }
        val top = shellTop ?: shellBottom ?: Color(0xFF17161D)
        val bottom = shellBottom ?: shellTop ?: Color(0xFF0D0C12)
        val base = button ?: lerp(bottom, Color.Black, 0.6f)
        val light = base.luminance() > 0.45f
        val shellLight = top.luminance() > 0.45f
        val colors = Skin(
            shellTop = top,
            shellBottom = bottom,
            buttonTop = lerp(base, Color.White, 0.12f),
            buttonBottom = lerp(base, Color.Black, 0.18f),
            buttonPressed = lerp(base, if (light) Color.Black else Color.White, 0.22f),
            buttonInk = if (light) lerp(base, Color.Black, 0.55f) else lerp(base, Color.White, 0.6f),
            shellInk = if (shellLight) lerp(top, Color.Black, 0.6f) else lerp(top, Color.White, 0.6f),
            well = Color.Black.copy(alpha = 0.22f),
            glow = if (shellLight) lerp(top, Color.Black, 0.45f) else Color.White,
            shadow = lerp(bottom, Color.Black, 0.6f),
        )
        return SkinStyle(skin.name, colors, parts)
    }

    private const val DPAD_BITS = Buttons.DPAD_UP or Buttons.DPAD_DOWN or Buttons.DPAD_LEFT or Buttons.DPAD_RIGHT

    /** Mean colour of the opaque pixels of [area], on a sparse grid, skipping [holes]. */
    private fun average(bitmap: Bitmap, area: android.graphics.Rect, holes: List<android.graphics.Rect>): Color? {
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        val stepX = max(1, area.width() / 40)
        val stepY = max(1, area.height() / 12)
        var y = area.top
        while (y < area.bottom) {
            var x = area.left
            while (x < area.right) {
                if (holes.none { it.contains(x, y) }) {
                    val c = bitmap.getPixel(min(x, bitmap.width - 1), min(y, bitmap.height - 1))
                    if ((c ushr 24) > 160) {
                        r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                    }
                }
                x += stepX
            }
            y += stepY
        }
        // Mostly transparent artwork (translucent skins): no shell colour to take.
        if (n < 12) return null
        return Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }
}

/** Draws a PDF (first page, vector, at the exact size) or a PNG/JPEG into a bitmap of w × h. */
fun renderBitmap(file: File, w: Int, h: Int): Bitmap? = runCatching {
    if (!file.exists() || w <= 0 || h <= 0) return null
    if (file.extension.equals("pdf", ignoreCase = true)) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { pdf ->
                pdf.openPage(0).use { page ->
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    } else {
        val source = BitmapFactory.decodeFile(file.path) ?: return null
        Bitmap.createScaledBitmap(source, w, h, true)
    }
}.getOrNull()
