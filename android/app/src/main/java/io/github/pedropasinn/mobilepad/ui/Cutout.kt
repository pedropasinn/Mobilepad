package io.github.pedropasinn.mobilepad.ui

import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import kotlin.math.max
import kotlin.math.min

/**
 * True when the display cutout is only a small camera hole (or there is none): it covers a tiny
 * spot in the middle of an edge, so giving up that whole edge band wastes room. A wide notch or a
 * large cutout returns false.
 */
@Composable
fun rememberSmallCutout(): Boolean {
    val view = LocalView.current
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val insets = WindowInsets.displayCutout
    // Read so that the answer is computed again once the insets arrive or the phone turns.
    val key = listOf(insets.getLeft(density, dir), insets.getTop(density), insets.getRight(density, dir), insets.getBottom(density))
    return remember(view, key) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@remember false
        val cutout = view.rootWindowInsets?.displayCutout ?: return@remember true
        val shortSide = min(view.rootView.width, view.rootView.height)
        if (shortSide <= 0) return@remember false
        cutout.boundingRects.all { max(it.width(), it.height()) < shortSide * 0.25f }
    }
}

/**
 * Keeps content clear of the cutout only when it is worth it: a wide notch gets its band, a small
 * camera hole gets nothing (the controls never reach the middle of the edge where it sits).
 */
fun Modifier.cutoutPadding(): Modifier = composed {
    if (rememberSmallCutout()) this else this.windowInsetsPadding(WindowInsets.displayCutout)
}
