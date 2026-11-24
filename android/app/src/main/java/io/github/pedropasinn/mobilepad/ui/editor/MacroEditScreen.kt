package io.github.pedropasinn.mobilepad.ui.editor

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalConfiguration
import io.github.pedropasinn.mobilepad.ui.AppSwitch
import io.github.pedropasinn.mobilepad.ui.FadingColumn
import io.github.pedropasinn.mobilepad.ui.FieldRow
import io.github.pedropasinn.mobilepad.ui.Hue
import io.github.pedropasinn.mobilepad.ui.Note
import io.github.pedropasinn.mobilepad.ui.SectionLabel
import io.github.pedropasinn.mobilepad.ui.Segmented
import androidx.compose.material.icons.Icons
import io.github.pedropasinn.mobilepad.ui.screenBackground
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.SportsEsports
import io.github.pedropasinn.mobilepad.str
import io.github.pedropasinn.mobilepad.R
import io.github.pedropasinn.mobilepad.ui.Danger
import io.github.pedropasinn.mobilepad.ui.Ink
import io.github.pedropasinn.mobilepad.ui.KeyCaps
import io.github.pedropasinn.mobilepad.ui.LocalPalette
import io.github.pedropasinn.mobilepad.ui.Muted
import io.github.pedropasinn.mobilepad.ui.ScreenHeader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import io.github.pedropasinn.mobilepad.macro.Macro
import io.github.pedropasinn.mobilepad.macro.Trigger
import io.github.pedropasinn.mobilepad.protocol.ControllerState

// Labels follow the language: "0,3 s" in Italian, "0.3 s" in English.
private val HOLD_OPTIONS get() = listOf(0L, 300L, 500L, 1000L).map { ms ->
    ms to if (ms == 0L) str(R.string.hold_now) else java.text.NumberFormat.getInstance().format(ms / 1000.0) + " s"
}

/** Edits one macro: input combination and resulting output (actions or radial menu). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MacroEditScreen(
    initial: Macro,
    radialIds: List<String>,
    rawInput: StateFlow<ControllerState>,
    onSave: (Macro) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    var macro by remember { mutableStateOf(initial) }
    var recording by remember { mutableStateOf(false) }
    val trigger = macro.trigger

    if (recording) {
        // Collects the combination pressed on the controller (or the on-screen pad):
        // everything held together, taken when all buttons are released again.
        LaunchedEffect(Unit) {
            var mask = 0
            var lt = false
            var rt = false
            withTimeoutOrNull(10_000) {
                rawInput.collect { s ->
                    mask = mask or s.buttons
                    lt = lt || s.leftTrigger > 128
                    rt = rt || s.rightTrigger > 128
                    val anyHeld = s.buttons != 0 || s.leftTrigger > 128 || s.rightTrigger > 128
                    if (!anyHeld && (mask != 0 || lt || rt)) {
                        macro = macro.copy(trigger = macro.trigger.copy(buttons = mask, leftTrigger = lt, rightTrigger = rt))
                        recording = false
                    }
                }
            }
            recording = false
        }
    }

    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val combo: @Composable ColumnScope.() -> Unit = {
        io.github.pedropasinn.mobilepad.ui.Card(Icons.Rounded.SportsEsports, Hue.VIOLET, str(R.string.combo), str(R.string.combo_sub), Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    KeyCaps(describeButtons(trigger.buttons, trigger.leftTrigger, trigger.rightTrigger))
                }
                Button(onClick = { recording = true }, enabled = !recording) {
                    Text(if (recording) str(R.string.recording) else str(R.string.record))
                }
            }
            ButtonChips(trigger.buttons, trigger.leftTrigger, trigger.rightTrigger) { m, lt, rt ->
                macro = macro.copy(trigger = trigger.copy(buttons = m, leftTrigger = lt, rightTrigger = rt))
            }
            SectionLabel(str(R.string.hold))
            Segmented(HOLD_OPTIONS, trigger.holdMs, { ms -> macro = macro.copy(trigger = trigger.copy(holdMs = ms)) }, Modifier.fillMaxWidth())
            FieldRow(str(R.string.consume)) {
                AppSwitch(trigger.consume, { macro = macro.copy(trigger = trigger.copy(consume = it)) })
            }
        }
    }
    val action: @Composable ColumnScope.() -> Unit = {
        io.github.pedropasinn.mobilepad.ui.Card(Icons.Rounded.Bolt, Hue.GOLD, str(R.string.action), str(R.string.action_sub), Modifier.fillMaxWidth()) {
            Segmented(
                listOf(false to str(R.string.action_sequence), true to str(R.string.open_wheel)),
                macro.radial != null,
                { wheel ->
                    macro = if (!wheel) macro.copy(radial = null)
                    else macro.copy(radial = radialIds.firstOrNull() ?: return@Segmented)
                },
                Modifier.fillMaxWidth(),
            )
            if (macro.radial != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    radialIds.forEach { id ->
                        FilterChip(selected = macro.radial == id, onClick = { macro = macro.copy(radial = id) }, label = { Text(id) })
                    }
                }
                Note(str(R.string.wheel_help))
            } else {
                ActionListEditor(macro.actions) { macro = macro.copy(actions = it) }
                FieldRow(str(R.string.turbo)) { AppSwitch(macro.repeat, { macro = macro.copy(repeat = it) }) }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .screenBackground()
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(str(R.string.edit_macro), onBack = onCancel) {
            Button(
                onClick = { onSave(macro) },
                enabled = trigger.size > 0 && (macro.radial != null || macro.actions.isNotEmpty()),
            ) { Text(str(R.string.save)) }
            onDelete?.let { TextButton(onClick = it) { Text(str(R.string.delete_macro), color = Danger) } }
        }
        OutlinedTextField(
            value = macro.name,
            onValueChange = { macro = macro.copy(name = it) },
            label = { Text(str(R.string.name)) },
            singleLine = true,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        )
        // Landscape: combo and action side by side, each scrolling on its own.
        if (wide) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FadingColumn(Modifier.weight(1f).fillMaxHeight(), content = combo)
                FadingColumn(Modifier.weight(1f).fillMaxHeight(), content = action)
            }
        } else {
            FadingColumn(Modifier.fillMaxWidth().weight(1f)) { combo(); action() }
        }
    }
}

fun newMacro() = Macro(
    name = str(R.string.new_macro_name),
    trigger = Trigger(0, leftTrigger = false, rightTrigger = false, holdMs = 0, consume = true),
    actions = emptyList(),
    radial = null,
    repeat = false,
)
