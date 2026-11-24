package io.github.pedropasinn.mobilepad.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.pedropasinn.mobilepad.input.GamepadInput
import io.github.pedropasinn.mobilepad.protocol.ControllerState
import io.github.pedropasinn.mobilepad.skin.DeltaSkin
import java.io.File
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * An imported Delta skin as the controller. The artwork is scaled to fit (anchored to the bottom in
 * portrait, centred in landscape); touches are hit-tested against the skin's items, several fingers
 * at once, sliding from one button to the next as on the original. A tap on the game screen area
 * opens the menu, which every skin can reach this way.
 */
@Composable
fun DeltaSkinPad(
    skin: DeltaSkin,
    layout: DeltaSkin.Layout,
    input: GamepadInput,
    status: String,
    statusKind: StatusKind,
    onMenu: () -> Unit,
) {
    val view = LocalView.current
    val menu by rememberUpdatedState(onMenu)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val scale = min(w / layout.mappingWidth, h / layout.mappingHeight)
        val ox = (w - layout.mappingWidth * scale) / 2f
        val oy = if (h > w) h - layout.mappingHeight * scale else (h - layout.mappingHeight * scale) / 2f
        val artSize = IntSize((layout.mappingWidth * scale).toInt().coerceAtLeast(1), (layout.mappingHeight * scale).toInt().coerceAtLeast(1))

        val art by produceState<ImageBitmap?>(null, skin.dir, layout.asset, artSize) {
            value = withContext(Dispatchers.IO) { render(File(skin.dir, layout.asset), artSize) }
        }
        val knobs by produceState<Map<String, ImageBitmap>>(emptyMap(), skin.dir, layout, scale) {
            value = withContext(Dispatchers.IO) {
                layout.items.mapNotNull { it.thumbstick }.filter { it.image.isNotEmpty() }.associate { t ->
                    t.image to render(File(skin.dir, t.image), IntSize((t.width * scale).toInt().coerceAtLeast(1), (t.height * scale).toInt().coerceAtLeast(1)))
                }.filterValues { it != null }.mapValues { it.value!! }
            }
        }

        // Finger positions in mapping points; sticks keep the finger that grabbed them.
        val touches = remember { mutableStateMapOf<PointerId, Offset>() }
        val grabbed = remember { mutableStateMapOf<PointerId, Int>() }
        val pressedItems = remember { mutableStateMapOf<Int, Offset>() }
        val measurer = rememberTextMeasurer()

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(layout, scale, ox, oy) {
                    fun toSkin(p: Offset) = Offset((p.x - ox) / scale, (p.y - oy) / scale)
                    var lastMask = 0
                    var menuHeld = false
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (c in event.changes) {
                                val at = toSkin(c.position)
                                if (c.pressed) {
                                    if (!c.previousPressed) {
                                        val stick = layout.items.indexOfFirst { it.thumbstick != null && it.frame.contains(at.x, at.y, it.edges) }
                                        if (stick >= 0) grabbed[c.id] = stick
                                        else if (layout.screens.any { it.contains(at.x, at.y) } && hitItems(layout, at).isEmpty()) menu()
                                    }
                                    touches[c.id] = at
                                } else {
                                    touches.remove(c.id)
                                    grabbed.remove(c.id)
                                }
                                c.consume()
                            }
                            val result = evaluate(layout, touches, grabbed)
                            pressedItems.clear()
                            pressedItems.putAll(result.items)
                            if (result.state.buttons and lastMask.inv() != 0) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            lastMask = result.state.buttons
                            if (result.menu && !menuHeld) menu()
                            menuHeld = result.menu
                            input.touch { result.state }
                        }
                    }
                },
        ) {
            drawRect(Color.Black)
            art?.let { drawImage(it, topLeft = Offset(ox, oy), alpha = if (layout.translucent) 0.9f else 1f) }
            // Game screen area: connection status in place of the game.
            // Skins without a declared screen (Delta works it out) get the status in the middle.
            val screen = layout.screens.firstOrNull()
            (screen ?: DeltaSkin.Rect(layout.mappingWidth * 0.3f, layout.mappingHeight * 0.3f, layout.mappingWidth * 0.4f, layout.mappingHeight * 0.4f)).let { s ->
                val tl = Offset(ox + s.x * scale, oy + s.y * scale)
                val size = Size(s.width * scale, s.height * scale)
                if (screen != null) drawRoundRect(Color(0xFF0C0A18), tl, size, CornerRadius(6f))
                val title = measurer.measure("Mobilepad", TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = Rounded))
                val line = measurer.measure("● $status", TextStyle(color = statusColor(statusKind), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Rounded))
                val hint = measurer.measure("MENU", TextStyle(color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, letterSpacing = 2.sp))
                val cx = tl.x + size.width / 2
                val total = title.size.height + line.size.height + hint.size.height + 16
                var y = tl.y + (size.height - total) / 2
                if (size.width > title.size.width && size.height > total) {
                    drawText(title, topLeft = Offset(cx - title.size.width / 2, y)); y += title.size.height + 6
                    drawText(line, topLeft = Offset(cx - line.size.width / 2, y)); y += line.size.height + 10
                    drawText(hint, topLeft = Offset(cx - hint.size.width / 2, y))
                }
            }
            // Pressed feedback, and the knob of analog sticks.
            layout.items.forEachIndexed { i, item ->
                val f = item.frame
                val tl = Offset(ox + f.x * scale, oy + f.y * scale)
                val size = Size(f.width * scale, f.height * scale)
                val thumb = item.thumbstick
                if (thumb != null) {
                    val d = pressedItems[i] ?: Offset.Zero
                    val c = Offset(tl.x + size.width / 2 + d.x * size.width / 2, tl.y + size.height / 2 - d.y * size.height / 2)
                    val knob = knobs[thumb.image]
                    if (knob != null) drawImage(knob, topLeft = Offset(c.x - knob.width / 2f, c.y - knob.height / 2f))
                    else {
                        val r = max(thumb.width, f.width * 0.45f) * scale / 2
                        drawCircle(Color.Black.copy(alpha = 0.55f), r, c)
                        drawCircle(Color.White.copy(alpha = 0.35f), r, c, style = Stroke(2f))
                    }
                } else if (i in pressedItems) {
                    drawRoundRect(Color.White.copy(alpha = 0.22f), tl, size, CornerRadius(min(size.width, size.height) / 2))
                }
            }
        }
    }
}

private class Evaluation(val state: ControllerState, val items: Map<Int, Offset>, val menu: Boolean)

/** Items under a point: those whose frame contains it, else the nearest whose extended frame does. */
private fun hitItems(layout: DeltaSkin.Layout, p: Offset): List<Int> {
    val direct = layout.items.indices.filter { layout.items[it].thumbstick == null && layout.items[it].frame.contains(p.x, p.y) }
    if (direct.isNotEmpty()) return direct
    return layout.items.indices
        .filter { layout.items[it].thumbstick == null && layout.items[it].frame.contains(p.x, p.y, layout.items[it].edges) }
        .minByOrNull { hypot(layout.items[it].frame.centerX - p.x, layout.items[it].frame.centerY - p.y) }
        ?.let { listOf(it) } ?: emptyList()
}

/** Combines every finger into one controller state. */
private fun evaluate(layout: DeltaSkin.Layout, touches: Map<PointerId, Offset>, grabbed: Map<PointerId, Int>): Evaluation {
    var buttons = 0
    var lt = 0
    var rt = 0
    var lx = 0f; var ly = 0f; var rx = 0f; var ry = 0f
    var menu = false
    val items = mutableMapOf<Int, Offset>()
    fun apply(a: DeltaSkin.Action, amount: Float = 1f) {
        when (a) {
            is DeltaSkin.Action.Press -> buttons = buttons or a.mask
            DeltaSkin.Action.LeftTrigger -> lt = 255
            DeltaSkin.Action.RightTrigger -> rt = 255
            DeltaSkin.Action.Menu -> menu = true
            is DeltaSkin.Action.Axis -> if (a.stick == DeltaSkin.Stick.LEFT) { lx += a.x * amount; ly += a.y * amount } else { rx += a.x * amount; ry += a.y * amount }
        }
    }
    for ((id, p) in touches) {
        val stickIndex = grabbed[id]
        if (stickIndex != null) {
            val item = layout.items[stickIndex]
            val f = item.frame
            var dx = (p.x - f.centerX) / (f.width / 2)
            var dy = -(p.y - f.centerY) / (f.height / 2)
            val len = hypot(dx, dy)
            if (len > 1f) { dx /= len; dy /= len }
            items[stickIndex] = Offset(dx, dy)
            val d = item.directions ?: continue
            when (d.stick) {
                DeltaSkin.Stick.LEFT -> { lx += dx; ly += dy }
                DeltaSkin.Stick.RIGHT -> { rx += dx; ry += dy }
                // Analog item mapped to the D-pad: 8-way from the angle.
                null -> if (len > 0.3f) directional(d, dx, dy).forEach { apply(it) }
            }
            continue
        }
        for (i in hitItems(layout, p)) {
            val item = layout.items[i]
            items[i] = Offset.Zero
            item.buttons.forEach { apply(it) }
            val d = item.directions ?: continue
            val f = item.frame
            val dx = (p.x - f.centerX) / (f.width / 2)
            val dy = -(p.y - f.centerY) / (f.height / 2)
            if (hypot(dx, dy) > 0.18f) directional(d, dx, dy).forEach { apply(it) }
        }
    }
    fun axis(v: Float) = ControllerState.axis(v.coerceIn(-1f, 1f))
    val state = ControllerState(
        buttons = buttons,
        leftX = axis(lx), leftY = axis(ly), rightX = axis(rx), rightY = axis(ry),
        leftTrigger = lt, rightTrigger = rt,
    )
    return Evaluation(state, items, menu)
}

/** 8 sectors of 45°: diagonals give two directions. */
private fun directional(d: DeltaSkin.Directions, dx: Float, dy: Float): List<DeltaSkin.Action> {
    val sector = Math.floorMod(Math.round(atan2(dy, dx) / (Math.PI / 4)).toInt(), 8)
    return when (sector) {
        0 -> listOfNotNull(d.right)
        1 -> listOfNotNull(d.right, d.up)
        2 -> listOfNotNull(d.up)
        3 -> listOfNotNull(d.up, d.left)
        4 -> listOfNotNull(d.left)
        5 -> listOfNotNull(d.left, d.down)
        6 -> listOfNotNull(d.down)
        else -> listOfNotNull(d.down, d.right)
    }
}

/** Draws a PDF (first page, vector, at the exact size) or a PNG/JPEG into a bitmap of [size]. */
private fun render(file: File, size: IntSize): ImageBitmap? = runCatching {
    if (!file.exists()) return null
    if (file.extension.equals("pdf", ignoreCase = true)) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { pdf ->
                pdf.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            }
        }
    } else {
        val source = BitmapFactory.decodeFile(file.path) ?: return null
        Bitmap.createScaledBitmap(source, size.width, size.height, true).asImageBitmap()
    }
}.getOrNull()
