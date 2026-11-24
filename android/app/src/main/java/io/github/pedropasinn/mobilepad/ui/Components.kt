package io.github.pedropasinn.mobilepad.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CardShape = RoundedCornerShape(24.dp)
val PillShape = RoundedCornerShape(50)

/** Icon colours: each feature keeps its own, as on a console home menu. */
enum class Hue(val top: Color, val bottom: Color) {
    VIOLET(Color(0xFFC8B1FF), Color(0xFF5B1BE0)),
    TEAL(Color(0xFF8FFFC8), Color(0xFF00809A)),
    GOLD(Color(0xFFFFE57C), Color(0xFFE5901A)),
    BLUE(Color(0xFF9CC8FF), Color(0xFF2A55E5)),
    ROSE(Color(0xFFFFA6C1), Color(0xFFD3245A)),
    GREEN(Color(0xFFC4F59A), Color(0xFF2E9B4A)),
    ORANGE(Color(0xFFFFC08A), Color(0xFFE2571C)),
    SLATE(Color(0xFFD3D0E0), Color(0xFF5E5A70));

    val brush get() = Brush.verticalGradient(listOf(top, bottom))
}

/** Screen background: deep indigo with two soft lights; plain black on OLED. */
@Composable
fun Modifier.screenBackground(): Modifier {
    val p = LocalPalette.current
    val glow = p !== OledPalette
    return background(p.background).drawBehind {
        if (glow) {
            drawRect(Brush.radialGradient(
                listOf(Color(0x557B3FF2), Color.Transparent),
                center = Offset(size.width * 0.15f, size.height * 0.05f),
                radius = size.maxDimension * 0.55f,
            ))
            drawRect(Brush.radialGradient(
                listOf(Color(0x3300A3B8), Color.Transparent),
                center = Offset(size.width * 0.95f, size.height * 1.0f),
                radius = size.maxDimension * 0.5f,
            ))
        }
    }
}

@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().screenBackground(), content = content)
}

/** Chevron at the end of a clickable row. */
@Composable
internal fun Chevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Faint)
}

/** Frosted panel: translucent fill and a hairline lit from the top edge. */
@Composable
fun Modifier.glass(shape: Shape = CardShape, selected: Boolean = false): Modifier {
    val p = LocalPalette.current
    val edge = if (selected) Brush.verticalGradient(listOf(AccentBright, Accent))
    else Brush.verticalGradient(listOf(p.outline.copy(alpha = (p.outline.alpha * 1.8f).coerceAtMost(1f)), p.outline.copy(alpha = p.outline.alpha * 0.4f)))
    return this
        .clip(shape)
        .background(if (selected) Accent.copy(alpha = 0.16f) else p.surface)
        .border(if (selected) 2.dp else 1.dp, edge, shape)
}

/** Rounded square with a gradient and a white glyph. */
@Composable
fun IconTile(icon: ImageVector, hue: Hue, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(hue.brush),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.56f)) }
}

/** Section panel: icon tile, title, short description, then the content. */
@Composable
internal fun Card(
    icon: ImageVector,
    hue: Hue,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Column(
        modifier.glass().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(icon, hue, 42.dp)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded)
                if (subtitle.isNotEmpty()) Text(subtitle, color = p.muted, fontSize = 13.sp, lineHeight = 17.sp, fontFamily = Rounded)
            }
        }
        content()
    }
}

/** Large home tile: gradient icon over a label; lit border when selected. */
@Composable
internal fun Tile(
    icon: ImageVector,
    hue: Hue,
    label: String,
    caption: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Icon beside the text instead of above it: for a column of tiles. */
    row: Boolean = false,
) {
    val p = LocalPalette.current
    val text: @Composable () -> Unit = {
        Column {
            Text(label, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, maxLines = 1)
            Text(caption, color = p.muted, fontSize = 12.sp, fontFamily = Rounded, maxLines = if (row) 2 else 1, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
        }
    }
    val base = modifier.glass(RoundedCornerShape(22.dp), selected).clickable(onClick = onClick)
    if (row) {
        Row(base.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, hue, 42.dp)
            text()
        }
    } else {
        Column(base.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(icon, hue, 46.dp)
            text()
        }
    }
}

/** Glass panel with a short description on top: the detail of the selected tile. */
@Composable
internal fun Panel(description: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(description, color = LocalPalette.current.muted, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = Rounded)
        content()
    }
}

/** Pill track with one lit segment: replaces rows of chips for single choices. */
@Composable
internal fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(
        modifier.clip(PillShape).background(p.surfaceHigh).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            val bg by animateColorAsState(if (on) Accent else Color.Transparent, label = "segment")
            Box(
                Modifier
                    .weight(1f)
                    .clip(PillShape)
                    .background(bg)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) Color.White else p.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Rounded,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Option list for long labels: one row each, a radio dot on the chosen one. */
@Composable
internal fun <T> OptionList(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surfaceHigh)) {
        options.forEachIndexed { i, (value, label) ->
            val on = value == selected
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape)
                        .border(2.dp, if (on) Accent else p.faint, CircleShape)
                        .padding(4.dp),
                ) { if (on) Box(Modifier.fillMaxSize().clip(CircleShape).background(Accent)) }
                Text(label, color = if (on) p.text else p.muted, fontSize = 14.sp, fontFamily = Rounded, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
            }
            if (i < options.lastIndex) Box(Modifier.padding(start = 46.dp).fillMaxWidth().height(1.dp).background(p.outline))
        }
    }
}

/** Clickable list row inside a screen (macros, wheels). */
@Composable
internal fun ListRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Small caps label above a group of controls. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = Faint, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, letterSpacing = 1.2.sp, modifier = modifier)
}

/** Round glass button with an icon. */
@Composable
internal fun RoundIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).glass(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = Ink, modifier = Modifier.size(size * 0.5f)) }
}

/** Title bar of an editor screen: back, title, then the actions (wrapping on narrow screens). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScreenHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.align(Alignment.CenterVertically),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, null, onBack)
            Text(title, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded)
        }
        Row(
            Modifier.align(Alignment.CenterVertically),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** Combination shown as key caps, e.g. [L3] + [R3]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeyCaps(text: String) {
    val p = LocalPalette.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        text.split(" + ").forEachIndexed { i, key ->
            if (i > 0) Text("+", color = p.faint, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
            Text(
                key,
                color = p.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rounded,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(p.surfaceHigh)
                    .border(1.dp, p.outline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

fun statusColor(kind: StatusKind) = when (kind) {
    StatusKind.CONNECTED -> Success
    StatusKind.ERROR -> Danger
    StatusKind.BUSY -> Warning
    StatusKind.IDLE -> Color(0xFF8E89A3)
}

@Composable
internal fun StatusPill(text: String, kind: StatusKind, modifier: Modifier = Modifier) {
    val color = statusColor(kind)
    Row(
        modifier
            .glass(PillShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Dot(color)
        Text(text, color = if (kind == StatusKind.IDLE) Muted else Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Rounded, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}
