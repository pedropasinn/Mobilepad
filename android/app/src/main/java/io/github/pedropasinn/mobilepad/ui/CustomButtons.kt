package io.github.pedropasinn.mobilepad.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.pedropasinn.mobilepad.input.GamepadInput
import io.github.pedropasinn.mobilepad.protocol.Buttons
import org.json.JSONArray
import org.json.JSONObject

/** What a custom button presses: a button of the virtual pad, or a whole trigger. */
enum class CustomOut(val label: String, val mask: Int = 0, val trigger: Int = 0) {
    A("A", Buttons.A), B("B", Buttons.B), X("X", Buttons.X), Y("Y", Buttons.Y),
    LB("LB", Buttons.LEFT_SHOULDER), RB("RB", Buttons.RIGHT_SHOULDER),
    LT("LT", trigger = 1), RT("RT", trigger = 2),
    UP("↑", Buttons.DPAD_UP), DOWN("↓", Buttons.DPAD_DOWN), LEFT("←", Buttons.DPAD_LEFT), RIGHT("→", Buttons.DPAD_RIGHT),
    L3("L3", Buttons.LEFT_THUMB), R3("R3", Buttons.RIGHT_THUMB),
    BACK("Back", Buttons.BACK), START("Start", Buttons.START),
    /** Not a pad button: switches to the other layout (e.g. race ⇄ menus). */
    SWAP("⇄");

    val isSwap get() = this == SWAP

    fun press(input: GamepadInput, down: Boolean) = when {
        isSwap -> Unit
        trigger == 1 -> input.touch { it.copy(leftTrigger = if (down) 255 else 0) }
        trigger == 2 -> input.touch { it.copy(rightTrigger = if (down) 255 else 0) }
        else -> input.touch { it.with(mask, down) }
    }
}

/**
 * A button the user adds to a layout: a name printed on it, what it presses, where its centre is
 * (fractions of the controller's area, so it fits any screen), its diameter, and whether a press
 * latches it (pressed once it stays held until pressed again, e.g. the accelerator).
 */
data class CustomButton(
    val id: String,
    val label: String,
    val out: CustomOut,
    val fx: Float,
    val fy: Float,
    val size: Float = 72f,
    val latch: Boolean = false,
    /** Icon key (see [SliceIcons]) printed instead of the name; "" prints the name. */
    val icon: String = "",
) {
    /** Key of this button in the layout tweaks (moved, resized, faded, hidden like any part). */
    val key get() = "c:$id"

    fun toJson(): JSONObject = JSONObject().put("id", id).put("label", label).put("out", out.name)
        .put("fx", fx.toDouble()).put("fy", fy.toDouble()).put("size", size.toDouble()).put("latch", latch).put("icon", icon)

    companion object {
        fun fromJson(o: JSONObject) = CustomButton(
            o.optString("id"), o.optString("label"),
            runCatching { CustomOut.valueOf(o.optString("out")) }.getOrDefault(CustomOut.A),
            o.optDouble("fx", 0.5).toFloat(), o.optDouble("fy", 0.5).toFloat(),
            o.optDouble("size", 72.0).toFloat(), o.optBoolean("latch", false), o.optString("icon"),
        )

        fun listToJson(list: List<CustomButton>) = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(s: String): List<CustomButton> = runCatching {
            val a = JSONArray(s)
            (0 until a.length()).map { fromJson(a.getJSONObject(it)) }.filter { it.id.isNotEmpty() }
        }.getOrDefault(emptyList())

        fun newId() = System.currentTimeMillis().toString(36)
    }
}

/**
 * The layout's custom buttons, placed over the controller area ([w] × [h]). A latching button
 * shows a lit ring while it holds its output.
 */
@Composable
fun BoxScope.CustomButtonsLayer(buttons: List<CustomButton>, input: GamepadInput, w: Dp, h: Dp, onSwap: () -> Unit = {}) {
    val latched = remember { mutableStateMapOf<String, Boolean>() }
    for (b in buttons) {
        val on = latched[b.id] == true
        PadButton(
            b.label,
            { down ->
                if (b.out.isSwap) {
                    if (down) {
                        // Leaving this layout: let go of anything it latched.
                        buttons.filter { latched[it.id] == true }.forEach { it.out.press(input, false) }
                        latched.clear()
                        onSwap()
                    }
                } else if (b.latch) {
                    if (down) {
                        val now = !(latched[b.id] ?: false)
                        latched[b.id] = now
                        b.out.press(input, now)
                    }
                } else {
                    b.out.press(input, down)
                }
            },
            Modifier
                .align(Alignment.TopStart)
                .offset(w * b.fx - b.size.dp / 2, h * b.fy - b.size.dp / 2)
                .tweak(b.key)
                .size(b.size.dp),
            // A symbol (an arrow, a letter) is printed big; a word is fitted to the button.
            icon = SliceIcons.resolve(b.icon),
            fontSize = if (SliceIcons.resolve(b.icon) != null) (b.size / 2.7f).toInt().coerceIn(16, 44) else if (b.label.length <= 2) (b.size / 2.4f).toInt().coerceIn(14, 34) else (b.size / 5.2f).toInt().coerceIn(10, 18),
            selected = on,
        )
    }
}
