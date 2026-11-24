package io.github.pedropasinn.mobilepad.ui

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pedropasinn.mobilepad.R
import io.github.pedropasinn.mobilepad.str
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Geometry

/** Rectangle in dp, top-left origin. */
data class PartBox(val x: Float, val y: Float, val w: Float, val h: Float) {
    val cx get() = x + w / 2f
    val cy get() = y + h / 2f
    val right get() = x + w
    val bottom get() = y + h

    /** Where the part shows with [t] applied: moved by dx/dy, scaled from its centre. */
    fun tweaked(t: ElementTweak) = PartBox(cx + t.dx - w * t.scale / 2f, cy + t.dy - h * t.scale / 2f, w * t.scale, h * t.scale)

    fun overlaps(o: PartBox, gap: Float = 0f) =
        x < o.right + gap && o.x < right + gap && y < o.bottom + gap && o.y < bottom + gap

    fun inside(width: Float, height: Float, margin: Float = 0f) =
        x >= margin && y >= margin && right <= width - margin && bottom <= height - margin
}

/**
 * The built-in position of every part, in dp, mirroring the maths of `LandscapeLayout` and
 * `PortraitLayout` in ControllerScreen.kt (keep both in step). [w] × [h] is the area the controller
 * lays out in, i.e. the screen minus the display cutout. Text-made parts (MENU block, system
 * buttons, wordmark) use nominal sizes at font scale 1.
 */
object PadGeometry {
    private const val MENU_W = 140f      // MENU pill + status line ("Mobilepad", a host name…)
    private const val MENU_H = 56f       // pill 26 (name inside) + 6 + link icon 24
    private const val SYSTEM_W = 152f    // widest set (PlayStation): 40 + 36 + 40 + 2 gaps of 18
    private const val SYSTEM_H = 40f     // tallest key (Xbox Guide)
    private const val WORDMARK_W = 108f
    private const val WORDMARK_H = 17f

    fun defaults(w: Float, h: Float, portrait: Boolean): Map<String, PartBox> =
        if (portrait) portrait(w, h) else landscape(w, h)

    private fun landscape(w: Float, h: Float): Map<String, PartBox> {
        val stick = min(h * 0.42f, 190f)
        val face = (h * 0.14f).coerceIn(48f, 72f)
        val d = stick * 0.8f
        val rs = stick * 0.85f
        val menu = PartBox(w / 2 - MENU_W / 2, 12f, MENU_W, MENU_H)
        val system = PartBox(w / 2 - SYSTEM_W / 2, menu.bottom + 16f, SYSTEM_W, SYSTEM_H)
        return mapOf(
            PadPart.LT to PartBox(12f, 12f, 96f, 50f),
            PadPart.LB to PartBox(118f, 12f, 96f, 50f),
            PadPart.RB to PartBox(w - 214f, 12f, 96f, 50f),
            PadPart.RT to PartBox(w - 108f, 12f, 96f, 50f),
            PadPart.MENU to menu,
            PadPart.SYSTEM to system,
            PadPart.WORDMARK to PartBox(w / 2 - WORDMARK_W / 2, system.bottom + 16f, WORDMARK_W, WORDMARK_H),
            PadPart.LSTICK to PartBox(36f, (h - stick) / 2 - 6f, stick, stick),
            PadPart.DPAD to PartBox(stick + 40f, h - d - 16f, d, d),
            PadPart.L3 to PartBox(20f, h - 64f, 46f, 46f),
            PadPart.FACE to PartBox(w - 36f - face * 3, (h - face * 3) / 2 - 20f, face * 3, face * 3),
            PadPart.RSTICK to PartBox(w - face * 3 - 56f - rs, h - 12f - rs, rs, rs),
            PadPart.R3 to PartBox(w - 66f, h - 64f, 46f, 46f),
        )
    }

    /** A column pushed to the bottom by a spacer: rows are placed from the bottom up. */
    private fun portrait(w: Float, h: Float): Map<String, PartBox> {
        val stick = min(w * 0.40f, 170f)
        val face = (w * 0.15f).coerceIn(48f, 68f)
        val d = stick * 0.9f
        val rs = stick * 0.85f
        val row4h = max(44f, SYSTEM_H)
        val row4 = h - 14f - row4h
        val row3h = max(d, face * 3)
        val row3 = row4 - 16f - row3h
        val row2 = row3 - 16f - stick
        val row1 = row2 - 16f - 46f
        val mark = row1 - 16f - WORDMARK_H
        return mapOf(
            PadPart.MENU to PartBox(w / 2 - MENU_W / 2, 14f, MENU_W, MENU_H),
            PadPart.WORDMARK to PartBox(w / 2 - WORDMARK_W / 2, mark, WORDMARK_W, WORDMARK_H),
            PadPart.LT to PartBox(16f, row1, 72f, 46f),
            PadPart.LB to PartBox(96f, row1, 72f, 46f),
            PadPart.RB to PartBox(w - 168f, row1, 72f, 46f),
            PadPart.RT to PartBox(w - 88f, row1, 72f, 46f),
            PadPart.LSTICK to PartBox(16f, row2, stick, stick),
            PadPart.RSTICK to PartBox(w - 16f - rs, row2 + (stick - rs) / 2, rs, rs),
            PadPart.DPAD to PartBox(16f, row3 + (row3h - d) / 2, d, d),
            PadPart.FACE to PartBox(w - 16f - face * 3, row3 + (row3h - face * 3) / 2, face * 3, face * 3),
            PadPart.L3 to PartBox(16f, row4, 44f, 44f),
            PadPart.SYSTEM to PartBox(w / 2 - SYSTEM_W / 2, row4, SYSTEM_W, SYSTEM_H),
            PadPart.R3 to PartBox(w - 60f, row4, 44f, 44f),
        )
    }

    /** Where each visible part ends up with [layout] applied. */
    fun placed(layout: PadLayout, w: Float, h: Float, portrait: Boolean): Map<String, PartBox> =
        defaults(w, h, portrait).filterKeys { !layout[it].hidden }.mapValues { (k, b) -> b.tweaked(layout[k]) }
}

// ---------------------------------------------------------------- Presets

/**
 * Ready-made layouts. Tweaks are offsets from size-dependent built-in positions, so each preset
 * is built for the size of the screen it will run on: it states where parts should land and the
 * offsets are worked out from [PadGeometry].
 */
enum class LayoutPreset(val title: Int, val caption: Int) {
    DEFAULT(R.string.preset_default, R.string.preset_default_caption),
    RETRO(R.string.preset_retro, R.string.preset_retro_caption),
    RACING(R.string.preset_racing, R.string.preset_racing_caption),
    LEFTY(R.string.preset_lefty, R.string.preset_lefty_caption),
    COMPACT(R.string.preset_compact, R.string.preset_compact_caption),
    MINIMAL(R.string.preset_minimal, R.string.preset_minimal_caption);

    /** [w] × [h]: the controller's area in that orientation, in dp. */
    fun build(w: Float, h: Float, portrait: Boolean): PadLayout {
        val b = PresetBuilder(PadGeometry.defaults(w, h, portrait))
        when (this) {
            DEFAULT -> Unit
            RETRO -> if (portrait) retroPortrait(b, w) else retroLandscape(b, w, h)
            RACING -> if (portrait) racingPortrait(b, w) else racingLandscape(b, w, h)
            LEFTY -> lefty(b, w)
            COMPACT -> compact(b, w, h)
            MINIMAL -> minimal(b)
        }
        return b.layout
    }

    companion object {
        /** Same tweaks, give or take rounding. */
        fun sameLayout(a: PadLayout, b: PadLayout): Boolean =
            a.tweaks.keys == b.tweaks.keys && a.tweaks.all { (k, t) ->
                val o = b.tweaks.getValue(k)
                t.hidden == o.hidden && abs(t.dx - o.dx) < 0.5f && abs(t.dy - o.dy) < 0.5f &&
                    abs(t.scale - o.scale) < 0.01f && abs(t.alpha - o.alpha) < 0.01f
            }
    }
}

private class PresetBuilder(val def: Map<String, PartBox>) {
    var layout = PadLayout()

    fun box(key: String) = def.getValue(key)

    /** Centres [key] at ([cx], [cy]) (null: where it is) with that size and opacity. */
    fun put(key: String, cx: Float? = null, cy: Float? = null, scale: Float = 1f, alpha: Float = 1f) {
        val d = box(key)
        val t = ElementTweak(round1((cx ?: d.cx) - d.cx), round1((cy ?: d.cy) - d.cy), round2(scale), round2(alpha))
        layout = layout.with(key, t)
    }

    fun hide(vararg keys: String) = keys.forEach { layout = layout.with(it, layout[it].copy(hidden = true)) }

    private fun round1(v: Float) = (v * 10f).roundToInt() / 10f
    private fun round2(v: Float) = (v * 100f).roundToInt() / 100f
}

private val LEFT_SIDE = listOf(PadPart.LT, PadPart.LB, PadPart.LSTICK, PadPart.DPAD, PadPart.L3)
private val RIGHT_SIDE = listOf(PadPart.RB, PadPart.RT, PadPart.RSTICK, PadPart.FACE, PadPart.R3)

/** Old handheld: no sticks, clicks or triggers; big D-pad and buttons, L/R in the corners. */
private fun retroLandscape(b: PresetBuilder, w: Float, h: Float) {
    b.hide(PadPart.LSTICK, PadPart.RSTICK, PadPart.L3, PadPart.R3, PadPart.LT, PadPart.RT)
    val size = min(h * 0.55f, 230f)
    // Centred in the band under the shoulder row.
    val cy = (h + 56f) / 2
    b.put(PadPart.DPAD, 40f + size / 2, cy, size / b.box(PadPart.DPAD).w)
    b.put(PadPart.FACE, w - 40f - size / 2, cy, size / b.box(PadPart.FACE).w)
    b.put(PadPart.LB, b.box(PadPart.LT).cx, b.box(PadPart.LT).cy)
    b.put(PadPart.RB, b.box(PadPart.RT).cx, b.box(PadPart.RT).cy)
}

private fun retroPortrait(b: PresetBuilder, w: Float) {
    b.hide(PadPart.LSTICK, PadPart.RSTICK, PadPart.L3, PadPart.R3, PadPart.LT, PadPart.RT)
    val size = min(w * 0.42f, 180f)
    // Over the rows of the sticks and of the D-pad, now free.
    val cy = (b.box(PadPart.LSTICK).y + max(b.box(PadPart.DPAD).bottom, b.box(PadPart.FACE).bottom)) / 2
    b.put(PadPart.DPAD, 20f + size / 2, cy, size / b.box(PadPart.DPAD).w)
    b.put(PadPart.FACE, w - 20f - size / 2, cy, size / b.box(PadPart.FACE).w)
    b.put(PadPart.LB, b.box(PadPart.LT).cx, b.box(PadPart.LT).cy)
    b.put(PadPart.RB, b.box(PadPart.RT).cx, b.box(PadPart.RT).cy)
}

/**
 * Racing: left stick low for steering, big brake (LT) and gas (RT) side by side under the right
 * thumb, smaller buttons above them; no right stick or clicks.
 */
private fun racingLandscape(b: PresetBuilder, w: Float, h: Float) {
    b.hide(PadPart.RSTICK, PadPart.L3, PadPart.R3)
    val stickScale = 1.1f
    val stick = b.box(PadPart.LSTICK).w * stickScale
    b.put(PadPart.LSTICK, 24f + stick / 2, h - 24f - stick / 2, stickScale)
    val dpad = b.box(PadPart.DPAD).w * 0.75f
    b.put(PadPart.DPAD, 24f + stick + 20f + dpad / 2, h - 24f - dpad / 2, 0.75f)
    val trig = 1.6f
    val tw = 96f * trig
    val cy = h - 24f - 50f * trig / 2
    b.put(PadPart.RT, w - 24f - tw / 2, cy, trig)
    b.put(PadPart.LT, w - 24f - tw - 14f - tw / 2, cy, trig)
    // Buttons in the band between the shoulder row and the triggers.
    val face = b.box(PadPart.FACE).w * 0.8f
    b.put(PadPart.FACE, w - 24f - face / 2, (74f + (cy - 50f * trig / 2 - 12f)) / 2, 0.8f)
}

private fun racingPortrait(b: PresetBuilder, w: Float) {
    b.hide(PadPart.RSTICK, PadPart.L3, PadPart.R3)
    // Gas over brake where the right stick was.
    val trig = 1.5f
    val cx = w - 16f - 72f * trig / 2
    val row = b.box(PadPart.LSTICK).cy
    val half = 46f * trig / 2 + 5f
    b.put(PadPart.RT, cx, row - half, trig)
    b.put(PadPart.LT, cx, row + half, trig)
    b.put(PadPart.FACE, scale = 0.85f)
    b.put(PadPart.DPAD, scale = 0.85f)
}

/** Mirror image: movement on the right, buttons on the left. MENU and the centre stay put. */
private fun lefty(b: PresetBuilder, w: Float) {
    for (key in LEFT_SIDE + RIGHT_SIDE) b.put(key, w - b.box(key).cx)
    // The D-pad's built-in spot follows the stick's size; a little more room between the two
    // keeps them apart if the real screen is a bit taller than estimated.
    b.put(PadPart.LSTICK, w - b.box(PadPart.LSTICK).cx + 10f)
}

/** Everything 20% smaller and drawn towards the bottom corners; MENU stays where it is. */
private fun compact(b: PresetBuilder, w: Float, h: Float) {
    val k = 0.8f
    fun pull(key: String, ax: Float) {
        val d = b.box(key)
        b.put(key, ax + (d.cx - ax) * k, h + (d.cy - h) * k, k)
    }
    LEFT_SIDE.forEach { pull(it, 0f) }
    RIGHT_SIDE.forEach { pull(it, w) }
    pull(PadPart.SYSTEM, w / 2)
    b.hide(PadPart.WORDMARK)
}

/** See-through controls, no wordmark or system buttons (MENU stays, a little stronger). */
private fun minimal(b: PresetBuilder) {
    b.hide(PadPart.WORDMARK, PadPart.SYSTEM)
    for (key in LEFT_SIDE + RIGHT_SIDE) b.put(key, alpha = 0.45f)
    b.put(PadPart.MENU, alpha = 0.7f)
}

// ---------------------------------------------------------------- Screen size

/** The controller's area in each orientation, in dp. */
data class PresetScreen(val landW: Float, val landH: Float, val portW: Float, val portH: Float) {
    val landscape get() = DpSize(landW.dp, landH.dp)

    companion object {
        /**
         * From the long and short sides of the screen and the depth of the display cutout. The
         * controller runs full screen (system bars hidden) and pads only the cutout, which sits
         * on a short edge: it comes off the long side in both orientations.
         */
        fun of(long: Float, short: Float, cutout: Float) = PresetScreen(long - cutout, short, short, long - cutout)
    }
}

/**
 * Estimate of the controller's area from the home screen. The controller runs full screen, so the
 * whole display counts: from Android 11 on it is read from the window manager's maximum window
 * metrics; before that from [LocalConfiguration], which may leave out the system bars (a smaller
 * estimate, i.e. a safe one). The cutout seen now, whatever the orientation, is taken as the one
 * the controller will pad; with none reported, 24 dp come off anyway, so offsets to the far side
 * fall a little short rather than off screen.
 */
@Composable
fun rememberPresetScreen(): PresetScreen {
    val config = LocalConfiguration.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val insets = WindowInsets.displayCutout
    val cutoutPx = max(max(insets.getLeft(density, dir), insets.getRight(density, dir)), max(insets.getTop(density), insets.getBottom(density)))
    val cutout = with(density) { cutoutPx.toDp().value }
    val (wDp, hDp) = remember(config, context, density) {
        val full = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) runCatching {
            val b = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            b.width() / density.density to b.height() / density.density
        }.getOrNull() else null
        full ?: (config.screenWidthDp.toFloat() to config.screenHeightDp.toFloat())
    }
    val long = max(wDp, hDp).roundToInt().toFloat()
    val short = min(wDp, hDp).roundToInt().toFloat()
    // A small camera hole takes no room (the controller does not pad it): keep just a thin margin.
    val small = rememberSmallCutout()
    return remember(long, short, cutout, small) { PresetScreen.of(long, short, if (small) 8f else if (cutout > 0f) cutout else 24f) }
}

// ---------------------------------------------------------------- Picker

/**
 * One card per preset, each a live miniature of the landscape version on this phone; tapping one
 * applies it to both orientations. The preset the current layouts were made from is highlighted;
 * hand-made changes that match no preset are confirmed before being replaced.
 */
@Composable
fun LayoutPresetPicker(
    style: SkinStyle,
    screen: PresetScreen,
    landscape: PadLayout,
    portrait: PadLayout,
    onApply: (landscape: PadLayout, portrait: PadLayout) -> Unit,
) {
    val built = remember(screen) {
        LayoutPreset.entries.associateWith { it.build(screen.landW, screen.landH, false) to it.build(screen.portW, screen.portH, true) }
    }
    val current = remember(built, landscape, portrait) {
        built.entries.firstOrNull { (_, l) -> LayoutPreset.sameLayout(l.first, landscape) && LayoutPreset.sameLayout(l.second, portrait) }?.key
    }
    val custom = current == null && (landscape.tweaks.isNotEmpty() || portrait.tweaks.isNotEmpty())
    var confirm by remember { mutableStateOf<LayoutPreset?>(null) }
    fun apply(p: LayoutPreset) = built.getValue(p).let { onApply(it.first, it.second) }

    SectionLabel(str(R.string.preset_section))
    CardGrid(LayoutPreset.entries.map { p ->
        val card: @Composable (Modifier) -> Unit = { m ->
            val pick = { if (custom) confirm = p else apply(p) }
            PresetCard(style, screen, built.getValue(p).first, str(p.title), str(p.caption), p == current, pick, m)
        }
        card
    })

    confirm?.let { p ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(str(R.string.preset_replace_title)) },
            text = { Text(str(R.string.preset_replace_text, str(p.title))) },
            confirmButton = { TextButton(onClick = { apply(p); confirm = null }) { Text(str(R.string.preset_replace)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(str(R.string.preset_cancel)) } },
        )
    }
}

@Composable
private fun PresetCard(style: SkinStyle, screen: PresetScreen, layout: PadLayout, name: String, caption: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier.width(156.dp)) {
    val p = LocalPalette.current
    Column(
        modifier.glass(RoundedCornerShape(18.dp), selected).clickable(onClick = onClick).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ControllerPreview(style, Modifier.fillMaxWidth(), layout = layout, screen = screen.landscape, onClick = onClick)
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(name, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
