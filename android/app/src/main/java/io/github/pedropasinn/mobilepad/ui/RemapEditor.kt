package io.github.pedropasinn.mobilepad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    REMAPPABLE.forEach { (mask, name) ->
        val target = remap[mask] ?: mask
        RemapRow(name, target, changed = mask in remap) { new ->
            onChange(if (new == mask) remap - mask else remap + (mask to new))
        }
    }
    Note(str(R.string.remap_note))
}

@Composable
private fun RemapRow(name: String, target: Int, changed: Boolean, onPick: (Int) -> Unit) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Cap(name)
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = p.faint, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (changed) Accent.copy(alpha = 0.18f) else p.surfaceHigh)
                    .clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (target == 0) str(R.string.remap_off) else nameOf(target) ?: "?",
                    color = if (changed) AccentBright else p.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded,
                )
                Icon(Icons.Rounded.ExpandMore, null, tint = p.faint, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                REMAPPABLE.forEach { (mask, label) ->
                    DropdownMenuItem(text = { Text(label, fontFamily = Rounded, fontWeight = FontWeight.Bold) }, onClick = { onPick(mask); open = false })
                }
                DropdownMenuItem(text = { Text(str(R.string.remap_off), fontFamily = Rounded) }, onClick = { onPick(0); open = false })
            }
        }
    }
}

@Composable
private fun Cap(text: String) {
    val p = LocalPalette.current
    Box(
        Modifier.width(56.dp).clip(RoundedCornerShape(10.dp)).background(p.surfaceHigh)
            .border(1.dp, p.outline, RoundedCornerShape(10.dp)).padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded) }
}
