package io.github.pedropasinn.mobilepad.input

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import io.github.pedropasinn.mobilepad.protocol.ControllerState

/**
 * Collects input from on-screen controls, a physical controller and motion (tilt steering,
 * shake), and merges them. Motion updates come from the sensor thread.
 */
class GamepadInput {
    private val touch = MutableStateFlow(ControllerState.NEUTRAL)
    private val physical = MutableStateFlow(ControllerState.NEUTRAL)
    private val motion = MutableStateFlow(ControllerState.NEUTRAL)
    private val _merged = MutableStateFlow(ControllerState.NEUTRAL)
    val state: StateFlow<ControllerState> = _merged.asStateFlow()

    /** Physical-controller state only, used for gestures such as waking the screen. */
    val physicalState: StateFlow<ControllerState> = physical.asStateFlow()

    fun touch(transform: (ControllerState) -> ControllerState) {
        touch.update(transform)
        publish()
    }

    fun physical(transform: (ControllerState) -> ControllerState) {
        physical.update(transform)
        publish()
    }

    fun motion(transform: (ControllerState) -> ControllerState) {
        motion.update(transform)
        publish()
    }

    /** Button remapping: source button mask to target mask (0: disabled). Touch and physical input both go through it. */
    @Volatile
    var remap: Map<Int, Int> = emptyMap()
        set(value) { field = value; publish() }

    /** On-screen buttons currently held. */
    fun touchHeld(): Boolean = touch.value.buttons != 0 || touch.value.leftTrigger > 0 || touch.value.rightTrigger > 0

    fun resetTouch() {
        touch.value = ControllerState.NEUTRAL
        publish()
    }

    @Synchronized
    private fun publish() {
        val merged = ControllerState.merge(ControllerState.merge(touch.value, physical.value), motion.value)
        _merged.value = if (remap.isEmpty()) merged else merged.copy(buttons = remapButtons(merged.buttons, remap))
    }
}

/** Applies [remap] to a button mask: remapped sources are cleared, then each pressed source sets its target. */
fun remapButtons(buttons: Int, remap: Map<Int, Int>): Int {
    var out = buttons
    for (src in remap.keys) out = out and src.inv()
    for ((src, dst) in remap) if (buttons and src != 0) out = out or dst
    return out
}
