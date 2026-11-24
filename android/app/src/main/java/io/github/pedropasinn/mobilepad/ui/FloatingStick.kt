package io.github.pedropasinn.mobilepad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * Floating stick: the whole [left] or right half of the controller takes the touch, and the stick
 * is centred where the thumb lands; it follows the finger from there and disappears when it lets
 * go. Controls drawn above this layer keep their touches. [size]: the stick's diameter.
 */
@Composable
fun BoxScope.FloatingStickZone(left: Boolean, size: Dp, onMove: (Float, Float) -> Unit, onHeld: (Boolean) -> Unit = {}) {
    val move by rememberUpdatedState(onMove)
    val held by rememberUpdatedState(onHeld)
    var center by remember { mutableStateOf<Offset?>(null) }
    var knob by remember { mutableStateOf(Offset.Zero) }
    val radiusPx = with(LocalDensity.current) { size.toPx() / 2f }
    val s = LocalSkin.current
    val art = LocalSkinParts.current[Part.STICK]
    UnbufferedTouch()
    Box(
        Modifier
            .align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
            .fillMaxHeight()
            .fillMaxWidth(0.5f)
            .pointerInput(radiusPx) {
                fun update(p: Offset, c: Offset) {
                    var d = p - c
                    val l = d.getDistance()
                    if (l > radiusPx) d = d * (radiusPx / l)
                    knob = d
                    move(d.x / radiusPx, -d.y / radiusPx)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val c = down.position
                    center = c
                    knob = Offset.Zero
                    held(true)
                    move(0f, 0f)
                    drag(down.id) { ch -> update(ch.position, c) }
                    center = null
                    knob = Offset.Zero
                    move(0f, 0f)
                    held(false)
                }
            },
    ) {
        center?.let { c ->
            Canvas(
                Modifier
                    .offset { IntOffset((c.x - radiusPx).roundToInt(), (c.y - radiusPx).roundToInt()) }
                    .size(size),
            ) { drawStick(s, art, knob, true) }
        }
    }
}
