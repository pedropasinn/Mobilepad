package io.github.pedropasinn.mobilepad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pedropasinn.mobilepad.R
import io.github.pedropasinn.mobilepad.protocol.Buttons
import io.github.pedropasinn.mobilepad.str

/** Buttons that can be remapped, with their Xbox names. */
private val REMAPPABLE = listOf(
    Buttons.A to "A", Buttons.B to "B", Buttons.X to "X", Buttons.Y to "Y",
    Buttons.LEFT_SHOULDER to "LB", Buttons.RIGHT_SHOULDER to "RB",
    Buttons.LEFT_THUMB to "L3", Buttons.RIGHT_THUMB to "R3",
    Buttons.BACK to "Back", Buttons.START to "Start", Buttons.GUIDE to "Guide",
    Buttons.DPAD_UP to "↑", Buttons.DPAD_DOWN to "↓", Buttons.DPAD_LEFT to "←", Buttons.DPAD_RIGHT to "→",
)

private fun nameOf(mask: Int) = REMAPPABLE.firstOrNull { it.first == mask }?.second

/** Swaps of the face buttons, for games that expect the Nintendo positions. */
private val NINTENDO_SWAP = mapOf(Buttons.A to Buttons.B, Buttons.B to Buttons.A, Buttons.X to Buttons.Y, Buttons.Y to Buttons.X)

/**
 * One row per button: what it sends now. Unchanged buttons show their own name; a button can also
 * be turned off. The remapping applies to the on-screen controller and to physical controllers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemapEditor(remap: Map<Int, Int>, onChange: (Map<Int, Int>) -> Unit) {
    Segmented(
        listOf("default" to str(R.string.remap_default), "nintendo" to str(R.string.remap_nintendo)),
        when {
            remap.isEmpty() -> "default"
            remap == NINTENDO_SWAP -> "nintendo"
            else -> "custom"
        },
        { if (it == "default") onChange(emptyMap()) else onChange(NINTENDO_SWAP) },
        Modifier.fillMaxWidth(),
    )
    // Chips in a grid: the fifteen buttons fit without a long list.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        REMAPPABLE.forEach { (mask, name) ->
            val target = remap[mask] ?: mask
            RemapChip(name, target, changed = mask in remap) { new ->
                onChange(if (new == mask) remap - mask else remap + (mask to new))
            }
        }
    }
    Note(str(R.string.remap_note))
}

@Composable
private fun RemapChip(name: String, target: Int, changed: Boolean, onPick: (Int) -> Unit) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (changed) Accent.copy(alpha = 0.22f) else p.surfaceHigh)
                .border(1.dp, if (changed) Accent else p.outline, RoundedCornerShape(12.dp))
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(name, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded)
            if (changed) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = AccentBright, modifier = Modifier.size(14.dp))
                Text(if (target == 0) str(R.string.remap_off) else nameOf(target) ?: "?",
                    color = AccentBright, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded)
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Color(0xFF221E2E),
            shape = RoundedCornerShape(16.dp),
        ) {
            REMAPPABLE.forEach { (mask, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = if (mask == target) AccentBright else p.text, fontFamily = Rounded, fontWeight = FontWeight.Bold) },
                    onClick = { onPick(mask); open = false },
                )
            }
            DropdownMenuItem(text = { Text(str(R.string.remap_off), color = p.text, fontFamily = Rounded) }, onClick = { onPick(0); open = false })
        }
    }
}
