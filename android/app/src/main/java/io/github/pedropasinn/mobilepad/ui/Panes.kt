package io.github.pedropasinn.mobilepad.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Fades the content out at an edge while more of it lies beyond: a cue that the area scrolls, so
 * nothing looks complete when it is not.
 */
fun Modifier.scrollFade(state: ScrollState, edge: Dp = 28.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val e = edge.toPx()
        if (state.canScrollBackward) {
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = e), blendMode = BlendMode.DstIn)
        }
        if (state.canScrollForward) {
            drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - e, endY = size.height), blendMode = BlendMode.DstIn)
        }
    }

/** A scrolling column that shows the fade cue. */
@Composable
fun FadingColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = 14.dp,
    padding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier.scrollFade(scroll).verticalScroll(scroll).padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/**
 * One entry of a list-with-detail page. [summary]: the current value, shown under the title so the
 * list alone tells the state. [trailing]: e.g. a switch. [onOpen]: an entry that opens its own
 * screen instead of a detail.
 */
class PaneItem(
    val key: String,
    val icon: ImageVector,
    val hue: Hue,
    val title: String,
    val summary: String = "",
    val trailing: (@Composable () -> Unit)? = null,
    val onOpen: (() -> Unit)? = null,
    val detail: (@Composable ColumnScope.() -> Unit)? = null,
)

/**
 * Every entry stays in sight: on wide screens the list sits beside the selected entry's detail,
 * each in its own scrolling area; on narrow screens the entries form one list and the selected one
 * unfolds in place.
 */
@Composable
fun MasterDetail(
    items: List<PaneItem>,
    selected: String?,
    onSelect: (String?) -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
    header: (@Composable ColumnScope.() -> Unit)? = null,
) {
    if (wide) {
        val current = items.firstOrNull { it.key == selected && it.detail != null } ?: items.firstOrNull { it.detail != null }
        Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FadingColumn(Modifier.width(290.dp).fillMaxHeight(), spacing = 6.dp) {
                header?.invoke(this)
                items.forEach { item ->
                    PaneRow(item, selected = item.key == current?.key, expandable = false) {
                        if (item.onOpen != null) item.onOpen.invoke() else onSelect(item.key)
                    }
                }
            }
            AnimatedContent(
                current,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(80)) },
                label = "detail",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { item ->
                if (item?.detail != null) {
                    Column(Modifier.fillMaxSize().glass()) {
                        DetailHeader(item)
                        FadingColumn(Modifier.fillMaxSize(), padding = 16.dp) { item.detail.invoke(this) }
                    }
                }
            }
        }
    } else {
        FadingColumn(modifier.fillMaxSize(), spacing = 8.dp) {
            header?.invoke(this)
            items.forEach { item ->
                val open = item.key == selected && item.detail != null
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), selected = open)) {
                    PaneRow(item, selected = false, expandable = item.detail != null, expanded = open, flat = true) {
                        when {
                            item.onOpen != null -> item.onOpen.invoke()
                            open -> onSelect(null)
                            else -> onSelect(item.key)
                        }
                    }
                    AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) { item.detail?.invoke(this) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(item: PaneItem) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(item.icon, item.hue, 28.dp)
        Text(item.title, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        item.trailing?.invoke()
    }
}

@Composable
private fun PaneRow(
    item: PaneItem,
    selected: Boolean,
    expandable: Boolean,
    expanded: Boolean = false,
    flat: Boolean = false,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val base = if (flat) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), selected)
    Row(
        base.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = if (flat) 10.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(item.icon, item.hue, if (flat) 38.dp else 34.dp)
        Column(Modifier.weight(1f)) {
            Text(item.title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            if (item.summary.isNotEmpty()) {
                Text(item.summary, color = p.muted, fontSize = 12.sp, fontFamily = Rounded, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item.trailing?.invoke()
        when {
            item.onOpen != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.faint)
            expandable -> Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = p.faint)
        }
    }
}

/** App-styled switch. */
@Composable
fun AppSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Accent,
            checkedBorderColor = Accent,
            uncheckedThumbColor = LocalPalette.current.muted,
            uncheckedTrackColor = LocalPalette.current.surfaceHigh,
            uncheckedBorderColor = LocalPalette.current.outline,
        ),
    )
}

/** Explanatory text inside a detail. */
@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, color = LocalPalette.current.faint, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = Rounded, modifier = modifier)
}

/** A labelled row inside a detail: label on the left, control on the right. */
@Composable
fun FieldRow(label: String, modifier: Modifier = Modifier, control: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = LocalPalette.current.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Rounded, modifier = Modifier.weight(1f))
        control()
    }
}

/** Thin divider between groups inside a detail. */
@Composable
fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).clip(RoundedCornerShape(1.dp)).background(LocalPalette.current.outline))
}
