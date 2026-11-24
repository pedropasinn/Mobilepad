package io.github.pedropasinn.mobilepad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pedropasinn.mobilepad.R
import io.github.pedropasinn.mobilepad.str
import kotlin.math.roundToInt

/**
 * Layout editing on top of the controller: tap a part to pick it, drag it anywhere, then set its
 * size and opacity or hide it in the toolbar. The controls underneath get no input meanwhile.
 * The toolbar moves to the half of the screen the picked part is not in.
 */
@Composable
fun PadLayoutEditor(
    current: PadLayoutScope,
    onSelect: (String?) -> Unit,
    onChange: (PadLayout) -> Unit,
    onDone: () -> Unit,
) {
    val density = LocalDensity.current
    val scope by rememberUpdatedState(current)
    val change by rememberUpdatedState(onChange)
    val select by rememberUpdatedState(onSelect)
    var origin by remember { mutableStateOf(Offset.Zero) }
    var toolDrag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val at = down.position + origin
                    // The smallest part under the finger: the face buttons sit inside nothing, but
                    // a stick may overlap a small button after moving things around.
                    val hit = scope.bounds.entries
                        .filter { it.value.inflate(8.dp.toPx()).contains(at) }
                        .minByOrNull { it.value.width * it.value.height }?.key
                    select(hit)
                    down.consume()
                    if (hit == null) return@awaitEachGesture
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        val d = c.positionChange()
                        if (d != Offset.Zero) {
                            dragging = true
                            val t = scope.layout[hit]
                            change(scope.layout.with(hit, t.copy(dx = t.dx + d.x / density.density, dy = t.dy + d.y / density.density)))
                        }
                        c.consume()
                    }
                    dragging = false
                }
            },
    ) {
        val p = LocalPalette.current
        // Grid and outlines.
        Canvas(Modifier.fillMaxSize()) {
            val step = 24.dp.toPx()
            var x = step
            while (x < size.width) { drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, size.height)); x += step }
            var y = step
            while (y < size.height) { drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y)); y += step }
            scope.bounds.forEach { (key, r) ->
                val sel = key == scope.selected
                val box = r.translate(-origin).inflate(4.dp.toPx())
                drawRoundRect(
                    if (sel) AccentBright else Color.White.copy(alpha = 0.55f),
                    box.topLeft, box.size, CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = (if (sel) 2.5f else 1.5f).dp.toPx(),
                        pathEffect = if (sel) null else PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    ),
                )
            }
        }

        val selected = scope.selected
        val selBox = selected?.let { scope.bounds[it] }?.translate(-origin)
        // Top by default; at the bottom when the picked part is up there.
        val onTop = selBox == null || selBox.top > with(density) { 110.dp.toPx() }
        val narrow = maxWidth < 600.dp
        Column(
            Modifier
                .align(if (onTop) Alignment.TopCenter else Alignment.BottomCenter)
                .offset { IntOffset(toolDrag.x.roundToInt(), toolDrag.y.roundToInt()) }
                .padding(8.dp)
                .then(if (selected == null) Modifier else Modifier.widthIn(max = 860.dp).fillMaxWidth())
                // Out of the way while a part is being dragged.
                .graphicsLayer { alpha = if (dragging) 0f else 1f }
                .background(p.background.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                .glass(RoundedCornerShape(20.dp))
                // Taps on the toolbar stay on it.
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val handle: @Composable () -> Unit = {
                Icon(
                    Icons.Rounded.DragIndicator, null, tint = p.faint,
                    modifier = Modifier
                        .size(width = 28.dp, height = 44.dp)
                        .pointerInput(Unit) { detectDragGestures { c, d -> c.consume(); toolDrag += d } }
                        .padding(horizontal = 2.dp, vertical = 10.dp),
                )
            }
            val finish: @Composable () -> Unit = {
                // Two taps: a stray touch must not wipe the whole layout.
                if (confirmReset) {
                    androidx.compose.material3.TextButton(onClick = { confirmReset = false; onChange(PadLayout()); onSelect(null) }) {
                        Text(str(R.string.layout_reset_confirm), color = Warning, fontFamily = Rounded, fontWeight = FontWeight.ExtraBold)
                    }
                    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(3000); confirmReset = false }
                } else {
                    IconButton(onClick = { confirmReset = true }, enabled = scope.layout.tweaks.isNotEmpty()) {
                        Icon(Icons.Rounded.RestartAlt, str(R.string.layout_reset_all), tint = if (scope.layout.tweaks.isNotEmpty()) p.text else p.faint)
                    }
                }
                Button(onClick = onDone, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Rounded.Check, str(R.string.layout_done), Modifier.size(18.dp))
                    // Narrow and a part picked: the name needs the room, the tick says enough.
                    if (!narrow || selected == null) Text(" " + str(R.string.layout_done), fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                }
            }
            if (selected == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    handle()
                    Text(str(R.string.layout_hint), color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Rounded,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).widthIn(max = 300.dp))
                    finish()
                }
            } else {
                val t = scope.layout[selected]
                fun set(n: ElementTweak) = onChange(scope.layout.with(selected, n))
                val name: @Composable (Modifier) -> Unit = { m ->
                    Text(str(PadPart.name(selected)), color = p.text, fontSize = if (narrow) 14.sp else 15.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, fontFamily = Rounded,
                        maxLines = if (narrow) 2 else 1, overflow = TextOverflow.Ellipsis, modifier = m)
                }
                val sliders: @Composable (Modifier) -> Unit = { m ->
                    MiniSlider(str(R.string.layout_size), "${(t.scale * 100).roundToInt()}%", t.scale, 0.5f..2f, m) { set(t.copy(scale = it)) }
                    MiniSlider(str(R.string.layout_opacity), "${(t.alpha * 100).roundToInt()}%", t.alpha, 0.15f..1f, m) { set(t.copy(alpha = it)) }
                }
                val partActions: @Composable () -> Unit = {
                    if (PadPart.canHide(selected)) {
                        IconButton(onClick = { set(t.copy(hidden = !t.hidden)) }) {
                            Icon(if (t.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, str(R.string.layout_visible),
                                tint = if (t.hidden) Warning else p.text)
                        }
                    }
                    IconButton(onClick = { set(ElementTweak()) }, enabled = !t.isDefault) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, str(R.string.layout_reset_part), tint = if (t.isDefault) p.faint else p.text)
                    }
                }
                if (narrow) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        handle(); name(Modifier.weight(1f)); partActions(); finish()
                    }
                    Row(Modifier.padding(start = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { sliders(Modifier.weight(1f)) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        handle()
                        name(Modifier.widthIn(max = 200.dp))
                        Spacer(Modifier.width(6.dp))
                        sliders(Modifier.weight(1f))
                        partActions()
                        finish()
                    }
                }
            }
        }
    }
}

/** Label and value above a slim slider. */
@Composable
private fun MiniSlider(
    label: String,
    value: String,
    v: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier,
    onChange: (Float) -> Unit,
) {
    val p = LocalPalette.current
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = Rounded, modifier = Modifier.weight(1f))
            Text(value, color = p.text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded)
        }
        Slider(
            v, onChange, valueRange = range,
            modifier = Modifier.height(26.dp),
            colors = SliderDefaults.colors(thumbColor = AccentBright, activeTrackColor = Accent, inactiveTrackColor = p.surfaceHigh),
        )
    }
}
