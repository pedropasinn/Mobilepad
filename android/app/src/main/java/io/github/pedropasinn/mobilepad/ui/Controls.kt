package io.github.pedropasinn.mobilepad.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pedropasinn.mobilepad.protocol.Buttons
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

// Controls are moulded on the current [Skin]: a lit top edge, a soft drop shadow on the shell, and
// labels embossed in a tone close to the button. A press sinks the button (smaller shadow, lighter
// body, 4 % smaller).

/** Body of a moulded button, shared by buttons, triggers and the stick knob. */
@Composable
private fun Modifier.moulded(shape: Shape, pressed: Boolean, selected: Boolean = false): Modifier {
    val s = LocalSkin.current
    return this
        .graphicsLayer { val k = if (pressed) 0.96f else 1f; scaleX = k; scaleY = k }
        .shadow(if (pressed) 1.dp else 5.dp, shape, ambientColor = s.shadow, spotColor = s.shadow)
        .clip(shape)
        .background(
            if (pressed) Brush.verticalGradient(listOf(s.buttonPressed, s.buttonPressed))
            else Brush.verticalGradient(listOf(s.buttonTop, s.buttonBottom)),
        )
        .border(
            if (selected) 2.dp else 1.dp,
            if (selected) Brush.verticalGradient(listOf(s.glow, s.glow.copy(alpha = 0.6f)))
            else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)),
            shape,
        )
}

/** Momentary button: [onChange] receives true on press and false on release. */
@Composable
fun PadButton(
    label: String,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    color: Color = Color.Unspecified,
    fontSize: Int = 18,
    /** On/off buttons: lit ring while on. */
    selected: Boolean = false,
    icon: ImageVector? = null,
) {
    val view = LocalView.current
    val s = LocalSkin.current
    val callback by rememberUpdatedState(onChange)
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .moulded(shape, pressed, selected)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    callback(true)
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    waitForUpOrCancellation()
                    pressed = false
                    callback(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val ink = when {
            color != Color.Unspecified -> color
            selected -> s.glow
            else -> s.buttonInk
        }
        if (icon != null) {
            Icon(icon, label, tint = ink, modifier = Modifier.size((fontSize + 4).dp))
        } else {
            Text(label, color = ink, fontSize = fontSize.sp, fontWeight = FontWeight.Black, fontFamily = Rounded)
        }
    }
}

/** Label printed on the shell under or beside a button (MENU, SELECT, START). */
@Composable
fun ShellLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = LocalSkin.current.shellInk,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        fontFamily = Rounded,
        letterSpacing = 1.sp,
        modifier = modifier,
    )
}

/** Analog stick in a recess of the shell: [onMove] receives x/y in -1..1 with up positive. */
@Composable
fun Stick(
    onMove: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
) {
    val callback by rememberUpdatedState(onMove)
    val s = LocalSkin.current
    var knob by remember { mutableStateOf(Offset.Zero) }
    var held by remember { mutableStateOf(false) }
    Canvas(
        modifier
            .size(size)
            .pointerInput(Unit) {
                val radius = min(this.size.width, this.size.height) / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                fun update(position: Offset) {
                    var d = position - center
                    val length = d.getDistance()
                    if (length > radius) d = d * (radius / length)
                    knob = d
                    callback(d.x / radius, -d.y / radius)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    held = true
                    update(down.position)
                    drag(down.id) { change -> update(change.position) }
                    held = false
                    knob = Offset.Zero
                    callback(0f, 0f)
                }
            },
    ) {
        val radius = min(this.size.width, this.size.height) / 2f
        // Recess: darker well, shaded at the top edge where the shell overhangs it.
        drawCircle(s.well, radius)
        drawCircle(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent), endY = radius),
            radius,
        )
        drawCircle(Color.White.copy(alpha = 0.10f), radius - 1f, style = Stroke(2f))
        if (held) drawCircle(s.glow.copy(alpha = 0.55f), radius - 2f, style = Stroke(3f))
        // Knob: shadow, moulded cap, concave centre.
        val k = radius * 0.46f
        val c = center + knob
        drawCircle(s.shadow.copy(alpha = 0.45f), k, c + Offset(0f, k * 0.12f))
        drawCircle(
            Brush.verticalGradient(listOf(s.buttonTop, s.buttonBottom), startY = c.y - k, endY = c.y + k),
            k, c,
        )
        drawCircle(Color.White.copy(alpha = 0.14f), k - 1f, c, style = Stroke(1.5f))
        drawCircle(
            Brush.verticalGradient(listOf(s.buttonBottom, s.buttonTop), startY = c.y - k * 0.7f, endY = c.y + k * 0.7f),
            k * 0.7f, c,
        )
    }
}

/** 8-way D-pad moulded as one cross: [onChange] receives the mask of pressed directions. */
@Composable
fun DPad(
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
) {
    val view = LocalView.current
    val s = LocalSkin.current
    val callback by rememberUpdatedState(onChange)
    var current by remember { mutableIntStateOf(0) }
    Canvas(
        modifier
            .size(size)
            .pointerInput(Unit) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val deadZone = min(this.size.width, this.size.height) * 0.12f
                fun update(position: Offset) {
                    val d = position - center
                    val mask = if (hypot(d.x, d.y) < deadZone) 0 else directionMask(atan2(-d.y, d.x))
                    if (mask != current) {
                        current = mask
                        callback(mask)
                        if (mask != 0) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    update(down.position)
                    drag(down.id) { change -> update(change.position) }
                    current = 0
                    callback(0)
                }
            },
    ) {
        val w = this.size.width
        val arm = w / 3f
        val cross = crossPath(w, arm, arm * 0.22f)
        // Drop shadow, then the body with a lit top.
        translate(top = w * 0.025f) { drawPath(cross, s.shadow.copy(alpha = 0.5f)) }
        drawPath(cross, Brush.verticalGradient(listOf(s.buttonTop, s.buttonBottom)))
        // Pressed arms: lighter body.
        clipPath(cross) {
            fun lit(x: Float, y: Float, on: Boolean) { if (on) drawRect(s.buttonPressed, Offset(x, y), Size(arm, arm)) }
            lit(arm, 0f, current and Buttons.DPAD_UP != 0)
            lit(arm, arm * 2, current and Buttons.DPAD_DOWN != 0)
            lit(0f, arm, current and Buttons.DPAD_LEFT != 0)
            lit(arm * 2, arm, current and Buttons.DPAD_RIGHT != 0)
        }
        drawPath(cross, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)), style = Stroke(2f))
        // Concave centre and the four embossed arrows.
        drawCircle(Brush.verticalGradient(listOf(s.buttonBottom, s.buttonTop), startY = w / 2 - arm * 0.3f, endY = w / 2 + arm * 0.3f), arm * 0.3f)
        for (angle in listOf(0f, 90f, 180f, 270f)) rotate(angle) { arrow(w, arm, s.buttonInk) }
    }
}

/** Plus sign with rounded outer corners: the union of two rounded bars. */
private fun crossPath(w: Float, arm: Float, r: Float): Path {
    val h = Path().apply { addRoundRect(RoundRect(0f, arm, w, arm * 2, CornerRadius(r))) }
    val v = Path().apply { addRoundRect(RoundRect(arm, 0f, arm * 2, w, CornerRadius(r))) }
    return Path().apply { op(h, v, PathOperation.Union) }
}

/** Small triangle near the top end of the cross; rotated for the other arms. */
private fun DrawScope.arrow(w: Float, arm: Float, color: Color) {
    val cx = w / 2f
    val tip = arm * 0.32f
    val half = arm * 0.16f
    val path = Path().apply {
        moveTo(cx, tip)
        lineTo(cx + half, tip + half * 1.3f)
        lineTo(cx - half, tip + half * 1.3f)
        close()
    }
    drawPath(path, color)
}

/** Splits the circle in 8 sectors of 45°; diagonals press two directions. */
private fun directionMask(angle: Float): Int {
    val sector = Math.floorMod(Math.round(angle / (Math.PI / 4)).toInt(), 8)
    return when (sector) {
        0 -> Buttons.DPAD_RIGHT
        1 -> Buttons.DPAD_RIGHT or Buttons.DPAD_UP
        2 -> Buttons.DPAD_UP
        3 -> Buttons.DPAD_UP or Buttons.DPAD_LEFT
        4 -> Buttons.DPAD_LEFT
        5 -> Buttons.DPAD_LEFT or Buttons.DPAD_DOWN
        6 -> Buttons.DPAD_DOWN
        else -> Buttons.DPAD_DOWN or Buttons.DPAD_RIGHT
    }
}

val TriggerShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
