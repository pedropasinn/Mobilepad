package io.github.pedropasinn.mobilepad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import io.github.pedropasinn.mobilepad.R

/** What the user changed on one element of the controller: offset (dp), size, opacity, hidden. */
data class ElementTweak(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scale: Float = 1f,
    val alpha: Float = 1f,
    val hidden: Boolean = false,
) {
    val isDefault get() = this == ElementTweak()
}

/**
 * The user's layout for one orientation: tweaks over the built-in positions, keyed by element. An
 * empty layout is the default one.
 */
data class PadLayout(val tweaks: Map<String, ElementTweak> = emptyMap()) {
    operator fun get(key: String) = tweaks[key] ?: ElementTweak()

    fun with(key: String, t: ElementTweak) = PadLayout(if (t.isDefault) tweaks - key else tweaks + (key to t))

    /** "key=dx,dy,scale,alpha,hidden;…" for the preferences. */
    fun encode(): String = tweaks.entries.joinToString(";") { (k, t) ->
        "$k=${t.dx},${t.dy},${t.scale},${t.alpha},${if (t.hidden) 1 else 0}"
    }

    companion object {
        fun decode(s: String?): PadLayout = PadLayout(
            (s ?: "").split(';').mapNotNull { entry ->
                val (k, v) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                val f = v.split(',')
                if (f.size != 5) return@mapNotNull null
                runCatching {
                    k to ElementTweak(f[0].toFloat(), f[1].toFloat(), f[2].toFloat(), f[3].toFloat(), f[4] == "1")
                }.getOrNull()
            }.toMap(),
        )
    }
}

/** Movable parts of the controller, with the name shown in the editor. */
object PadPart {
    const val LT = "lt"
    const val LB = "lb"
    const val RB = "rb"
    const val RT = "rt"
    const val MENU = "menu"
    const val SYSTEM = "system"
    const val WORDMARK = "wordmark"
    const val LSTICK = "lstick"
    const val RSTICK = "rstick"
    const val DPAD = "dpad"
    const val FACE = "face"
    const val L3 = "l3"
    const val R3 = "r3"

    /** MENU can't be hidden: it is the way back to everything else. */
    fun canHide(key: String) = key != MENU

    fun name(key: String): Int = when (key) {
        LT -> R.string.part_lt
        LB -> R.string.part_lb
        RB -> R.string.part_rb
        RT -> R.string.part_rt
        MENU -> R.string.part_menu
        SYSTEM -> R.string.part_system
        WORDMARK -> R.string.part_wordmark
        LSTICK -> R.string.part_lstick
        RSTICK -> R.string.part_rstick
        DPAD -> R.string.part_dpad
        FACE -> R.string.part_face
        L3 -> R.string.part_l3
        else -> R.string.part_r3
    }
}

/** The layout in use and, while editing, where each part is on screen. */
class PadLayoutScope(
    val layout: PadLayout,
    val editing: Boolean,
    val selected: String?,
    /** Kept across layout changes, so a drag does not lose the outlines. */
    val bounds: MutableMap<String, Rect> = mutableStateMapOf(),
)

val LocalPadLayout = compositionLocalOf<PadLayoutScope?> { null }

/**
 * Applies the user's tweak of [key] to a part of the controller: moved, scaled from its centre,
 * faded. A hidden part takes no space and no touches; while editing it stays, faint, so it can be
 * brought back. Transforms apply to touches too, so a bigger button is also easier to hit.
 */
@Composable
fun Modifier.tweak(key: String): Modifier {
    val scope = LocalPadLayout.current ?: return this
    val t = scope.layout[key]
    val hiddenNow = t.hidden && !scope.editing
    return this
        .offset(t.dx.dp, t.dy.dp)
        .then(
            if (hiddenNow) Modifier.layout { m, c ->
                // Measured but not placed: no drawing, no touches.
                val p: Placeable = m.measure(c)
                layout(p.width, p.height) {}
            } else Modifier,
        )
        .graphicsLayer {
            scaleX = t.scale
            scaleY = t.scale
            alpha = if (t.hidden) 0.25f else t.alpha
        }
        .then(if (scope.editing) Modifier.onGloballyPositioned { scope.bounds[key] = it.boundsInRoot() } else Modifier)
}
