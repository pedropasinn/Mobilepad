package io.github.pedropasinn.mobilepad.ui.editor

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import io.github.pedropasinn.mobilepad.ui.FadingColumn
import io.github.pedropasinn.mobilepad.ui.Hue
import io.github.pedropasinn.mobilepad.ui.IconTile
import io.github.pedropasinn.mobilepad.ui.Note
import io.github.pedropasinn.mobilepad.ui.Rounded
import io.github.pedropasinn.mobilepad.ui.Segmented
import io.github.pedropasinn.mobilepad.ui.glass
import io.github.pedropasinn.mobilepad.ui.Chevron
import io.github.pedropasinn.mobilepad.ui.screenBackground
import androidx.compose.material.icons.rounded.Timer
import io.github.pedropasinn.mobilepad.str
import io.github.pedropasinn.mobilepad.R
import io.github.pedropasinn.mobilepad.ui.Faint
import io.github.pedropasinn.mobilepad.ui.Ink
import io.github.pedropasinn.mobilepad.ui.KeyCaps
import io.github.pedropasinn.mobilepad.ui.ListRow
import io.github.pedropasinn.mobilepad.ui.LocalPalette
import io.github.pedropasinn.mobilepad.ui.Muted
import io.github.pedropasinn.mobilepad.ui.ScreenHeader
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.pedropasinn.mobilepad.ui.cutoutPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import io.github.pedropasinn.mobilepad.macro.Action
import io.github.pedropasinn.mobilepad.macro.Macro
import io.github.pedropasinn.mobilepad.macro.MacroConfig
import io.github.pedropasinn.mobilepad.macro.DYNAMIC_RADIAL_PREFIX
import io.github.pedropasinn.mobilepad.macro.RadialMenu
import io.github.pedropasinn.mobilepad.macro.RadialMode
import io.github.pedropasinn.mobilepad.protocol.ControllerState
import io.github.pedropasinn.mobilepad.ui.RadialWheel

private sealed interface Editing {
    data class MacroAt(val index: Int) : Editing // -1 = new
    data class Radial(val id: String?) : Editing // null = new
}

/**
 * Entry point of the visual editor: lists macros and radial menus.
 * [dynamicRadials]: menus built at runtime, offered as wheels to open and as folder targets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MacroHomeScreen(
    config: MacroConfig,
    rawInput: StateFlow<ControllerState>,
    onConfig: (MacroConfig) -> Unit,
    onAdvanced: () -> Unit,
    onClose: () -> Unit,
    dynamicRadials: Set<String> = emptySet(),
) {
    // Screens opened on top of the list; a sub-wheel is edited on top of its parent.
    var stack by remember { mutableStateOf(listOf<Editing>()) }
    fun pop() { stack = stack.dropLast(1) }
    BackHandler(enabled = stack.isEmpty(), onBack = onClose)
    val radialIds = config.radials.keys.toList() + dynamicRadials.sorted()

    when (val e = stack.lastOrNull()) {
        is Editing.MacroAt -> {
            MacroEditScreen(
                initial = config.macros.getOrNull(e.index) ?: newMacro(),
                radialIds = radialIds,
                rawInput = rawInput,
                onSave = { macro ->
                    val list = config.macros.toMutableList()
                    if (e.index in list.indices) list[e.index] = macro else list += macro
                    onConfig(config.copy(macros = list))
                    pop()
                },
                onDelete = if (e.index >= 0) {
                    { onConfig(config.copy(macros = config.macros.filterIndexed { i, _ -> i != e.index })); pop() }
                } else null,
                onCancel = ::pop,
            )
            return
        }
        is Editing.Radial -> key(stack.size, e.id) {
            val existing = e.id?.let { config.radials[it] }
            RadialEditScreen(
                initial = existing ?: newRadial(uniqueId(config)),
                takenIds = config.radials.keys,
                folderTargets = radialIds,
                trail = stack.filterIsInstance<Editing.Radial>().map { it.id ?: str(R.string.new_trail) },
                onSave = { menu -> onConfig(saveRadial(config, e.id, menu)); pop() },
                onDelete = if (existing != null) {
                    { onConfig(deleteRadial(config, existing.id)); pop() }
                } else null,
                onCancel = ::pop,
                onOpenSubmenu = { menu, index, target ->
                    var updated = saveRadial(config, e.id, menu)
                    val child = target ?: uniqueId(updated, menu.slices[index].label)
                    if (target == null) {
                        updated = saveRadial(updated, null, newRadial(child).copy(stick = menu.stick, mode = menu.mode))
                        val slices = menu.slices.toMutableList().also { it[index] = it[index].copy(submenu = child) }
                        updated = saveRadial(updated, menu.id, menu.copy(slices = slices))
                    }
                    onConfig(updated)
                    // A new parent now has its id: come back to it by name.
                    stack = stack.dropLast(1) + Editing.Radial(menu.id) + Editing.Radial(child)
                },
            )
            return
        }
        null -> Unit
    }

    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Column(
        Modifier
            .fillMaxSize()
            .screenBackground()
            .cutoutPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(str(R.string.nav_macros), onBack = onClose) {
            Button(onClick = { stack = listOf(Editing.MacroAt(-1)) }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(str(R.string.macro_word))
            }
            Button(onClick = { stack = listOf(Editing.Radial(null)) }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(str(R.string.wheel_word))
            }
            OutlinedButton(onClick = onAdvanced) {
                Icon(Icons.Rounded.Code, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("JSON")
            }
        }

        val macros: @Composable ColumnScope.() -> Unit = {
            GroupHeader(Icons.Rounded.AutoAwesome, Hue.ROSE, str(R.string.nav_macros_short), config.macros.size, str(R.string.macros_sub))
            if (config.macros.isEmpty()) EmptyHint(str(R.string.no_macros))
            config.macros.forEachIndexed { index, macro ->
                MacroRow(macro) { stack = listOf(Editing.MacroAt(index)) }
            }
            ComboWindowRow(config.comboWindowMs) { ms -> onConfig(config.copy(comboWindowMs = ms)) }
        }
        val wheels: @Composable ColumnScope.() -> Unit = {
            GroupHeader(Icons.Rounded.DonutLarge, Hue.VIOLET, str(R.string.wheels), config.radials.size, str(R.string.wheels_sub))
            if (config.radials.isEmpty()) EmptyHint(str(R.string.no_wheels))
            config.radials.values.forEach { menu ->
                WheelRow(menu, config) { stack = listOf(Editing.Radial(menu.id)) }
            }
        }
        // Landscape: macros and wheels side by side, each scrolling on its own; portrait: one list.
        if (wide) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FadingColumn(Modifier.weight(1f).fillMaxHeight(), spacing = 10.dp, content = macros)
                FadingColumn(Modifier.weight(1f).fillMaxHeight(), spacing = 10.dp, content = wheels)
            }
        } else {
            FadingColumn(Modifier.fillMaxWidth().weight(1f), spacing = 10.dp) {
                macros()
                Spacer(Modifier.height(6.dp))
                wheels()
            }
        }
    }
}

/** Title of a group with its count and a one-line explanation. */
@Composable
private fun GroupHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, hue: Hue, title: String, count: Int, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon, hue, 36.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded)
                Text("$count", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Rounded,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(LocalPalette.current.surfaceHigh).padding(horizontal = 8.dp, vertical = 1.dp))
            }
            Text(sub, color = Muted, fontSize = 12.sp, fontFamily = Rounded, maxLines = 2)
        }
    }
}

/** A macro: name, the combo as key caps, and what it does. Also used by the controller's menu. */
@Composable
fun MacroRow(macro: Macro, onClick: (() -> Unit)? = null) {
    val content: @Composable RowScope.() -> Unit = {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(macro.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, maxLines = 1, overflow = TextOverflow.Ellipsis)
            KeyCaps(describeButtons(macro.trigger.buttons, macro.trigger.leftTrigger, macro.trigger.rightTrigger))
            Text("→ " + describeOutput(macro), color = Muted, fontSize = 12.sp, fontFamily = Rounded, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onClick != null) Chevron()
    }
    if (onClick != null) ListRow(onClick = onClick, content = content)
    else Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

/** A wheel: its miniature, name, slices and how it opens. Also used by the controller's menu. */
@Composable
fun WheelRow(menu: RadialMenu, config: MacroConfig, onClick: (() -> Unit)? = null) {
    val opener = config.macros.firstOrNull { it.radial == menu.id }
    val parent = config.radials.values.firstOrNull { r -> r.slices.any { it.submenu == menu.id } }
    val content: @Composable RowScope.() -> Unit = {
        RadialWheel(menu, null, Modifier.size(72.dp), scale = 0.3f)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(menu.id, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                str(R.string.wheel_info, menu.slices.size, str(if (menu.mode == RadialMode.STICKY) R.string.mode_sticky else R.string.mode_hold)),
                color = Muted, fontSize = 12.sp, fontFamily = Rounded,
            )
            when {
                opener != null -> KeyCaps(describeButtons(opener.trigger.buttons, opener.trigger.leftTrigger, opener.trigger.rightTrigger))
                parent != null -> Text(str(R.string.sub_wheel_of, parent.id), color = Muted, fontSize = 12.sp, fontFamily = Rounded)
                else -> Text(str(R.string.no_combo), color = Faint, fontSize = 12.sp, fontFamily = Rounded)
            }
        }
        if (onClick != null) Chevron()
    }
    if (onClick != null) ListRow(onClick = onClick, content = content)
    else Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

/** How long the first button of a combo waits for the others before reaching the game. */
@Composable
private fun ComboWindowRow(current: Long, onChange: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(Icons.Rounded.Timer, Hue.BLUE, 30.dp)
            Text(str(R.string.combo_window), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, modifier = Modifier.weight(1f))
        }
        Segmented(
            listOf(0L, 30L, 50L, 80L, 120L).map { it to if (it == 0L) str(R.string.off) else "$it ms" },
            current, onChange, Modifier.fillMaxWidth(),
        )
        Note(str(R.string.combo_window_sub) + " · " + str(R.string.combo_window_note))
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, color = Faint, fontSize = 13.sp, fontFamily = Rounded, modifier = Modifier.padding(vertical = 4.dp))
}

internal fun describeOutput(macro: Macro): String {
    macro.radial?.let { return str(R.string.out_wheel, it) }
    val steps = macro.actions.joinToString(", ") { a ->
        when (a) {
            is Action.Press -> describeButtons(a.buttons, a.leftTrigger, a.rightTrigger)
            is Action.Keys -> describeKeys(a.modifiers, a.keys)
            is Action.Move -> str(if (a.stick.name == "LEFT") R.string.stick_left else R.string.stick_right)
            is Action.Wait -> str(R.string.out_wait, a.ms)
        }
    }
    val text = steps.ifEmpty { str(R.string.no_action) }
    return if (macro.repeat) str(R.string.out_turbo, text) else text
}

/** [base] when free (e.g. the label of the folder slice), otherwise "ruota N". */
private fun uniqueId(config: MacroConfig, base: String = ""): String =
    base.trim().takeIf { it.isNotEmpty() && it !in config.radials && !it.startsWith(DYNAMIC_RADIAL_PREFIX) }
        ?: generateSequence(1) { it + 1 }.map { str(R.string.wheel_n, it) }.first { it !in config.radials }

/** Saves a radial menu, renaming references when its name changed. */
private fun saveRadial(config: MacroConfig, oldId: String?, menu: RadialMenu): MacroConfig {
    val radials = LinkedHashMap<String, RadialMenu>()
    var placed = false
    for ((id, existing) in config.radials) {
        if (id == oldId) {
            radials[menu.id] = menu
            placed = true
        } else {
            radials[id] = existing
        }
    }
    if (!placed) radials[menu.id] = menu
    if (oldId == null || oldId == menu.id) return config.copy(radials = radials)
    return config.copy(
        macros = config.macros.map { if (it.radial == oldId) it.copy(radial = menu.id) else it },
        radials = retarget(radials, oldId, menu.id),
    )
}

/** Deletes a radial menu and the macros that only opened it; folders that opened it become empty slices. */
private fun deleteRadial(config: MacroConfig, id: String) =
    config.copy(macros = config.macros.filter { it.radial != id }, radials = retarget(config.radials - id, id, null))

/** Points the folders that open [from] at [to]. */
private fun retarget(radials: Map<String, RadialMenu>, from: String, to: String?): Map<String, RadialMenu> =
    radials.mapValuesTo(LinkedHashMap()) { (_, r) ->
        if (r.slices.none { it.submenu == from }) r
        else r.copy(slices = r.slices.map { if (it.submenu == from) it.copy(submenu = to) else it })
    }
