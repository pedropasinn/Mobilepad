package io.github.pedropasinn.mobilepad.ui

import io.github.pedropasinn.mobilepad.str
import io.github.pedropasinn.mobilepad.R
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.pedropasinn.mobilepad.motion.AxisPreset
import io.github.pedropasinn.mobilepad.motion.GyroMode
import io.github.pedropasinn.mobilepad.motion.MotionController
import io.github.pedropasinn.mobilepad.motion.MotionSettings
import io.github.pedropasinn.mobilepad.protocol.Buttons
import kotlin.math.roundToInt

private val ShakeButtons get() = listOf(
    "A" to Buttons.A, "B" to Buttons.B, "X" to Buttons.X, "Y" to Buttons.Y,
    "LB" to Buttons.LEFT_SHOULDER, "RB" to Buttons.RIGHT_SHOULDER,
    "L3" to Buttons.LEFT_THUMB, "R3" to Buttons.RIGHT_THUMB,
    str(R.string.dir_up) to Buttons.DPAD_UP, str(R.string.dir_down) to Buttons.DPAD_DOWN,
    "Back" to Buttons.BACK, "Start" to Buttons.START,
)

/**
 * Motion functions as a short list (one line each, with its switch) beside a panel that explains
 * the selected one, says what it works with and always shows its settings: faded and locked while
 * the function is off, so the panel is never empty.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MotionPage(motion: MotionController, wide: Boolean) {
    val s by motion.settings.collectAsState()
    val steer by motion.steer.collectAsState()
    val dsuStatus by motion.dsu.status.collectAsState()
    val set = motion::update
    var selected by rememberSaveable { mutableStateOf<String?>(if (wide) "steer" else null) }
    fun state(on: Boolean) = if (on) str(R.string.state_on_text) else str(R.string.state_off_text)

    if (!motion.hasTilt) {
        MasterDetail(
            listOf(PaneItem("none", Icons.Rounded.Explore, Hue.SLATE, str(R.string.nav_motion), str(R.string.no_sensors))),
            null, {}, wide,
        )
        return
    }
    val items = buildList {
        add(PaneItem(
            "steer", Icons.Rounded.ScreenRotation, Hue.TEAL, str(R.string.motion_steer), state(s.steering),
            trailing = { AppSwitch(s.steering, { on -> set { it.copy(steering = on) } }) },
        ) {
            FeatureHeader(Icons.Rounded.ScreenRotation, Hue.TEAL, str(R.string.steer_note),
                listOf(Icons.Rounded.Computer, SwitchConsoleIcon, Icons.Rounded.Android))
            Gate(s.steering) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    SteeringWheel(if (s.steering) steer ?: 0f else 0f, s.maxAngle, Modifier.size(if (wide) 132.dp else 112.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Setting(str(R.string.max_angle), "${s.maxAngle.roundToInt()}°", s.maxAngle, 10f..90f) { v ->
                            set { it.copy(maxAngle = v.roundToInt().toFloat()) }
                        }
                        Setting(str(R.string.dead_zone), "${s.deadZone.roundToInt()}°", s.deadZone, 0f..15f) { v ->
                            set { it.copy(deadZone = v.roundToInt().toFloat()) }
                        }
                    }
                }
                val curve = when {
                    s.curve < 0.9f -> str(R.string.curve_fast)
                    s.curve > 1.1f -> str(R.string.curve_precise)
                    else -> str(R.string.curve_linear)
                }
                Setting(str(R.string.curve), "%.1f · %s".format(s.curve, curve), s.curve, 0.5f..2.5f) { v ->
                    set { it.copy(curve = (v * 10).roundToInt() / 10f) }
                }
                FieldRow(str(R.string.pitch)) { AppSwitch(s.pitchY, { on -> set { it.copy(pitchY = on) } }) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = motion::recenter) { Text(str(R.string.center)) }
                    Note(str(R.string.center_note))
                }
            }
        })
        add(PaneItem(
            "gyro", Icons.Rounded.Sensors, Hue.VIOLET, str(R.string.motion_gyro),
            if (motion.hasGyro) state(s.gyro) else str(R.string.no_gyro),
            trailing = if (motion.hasGyro) ({ AppSwitch(s.gyro, { on -> set { it.copy(gyro = on) } }) }) else null,
        ) {
            FeatureHeader(Icons.Rounded.Sensors, Hue.VIOLET, str(R.string.gyro_note), listOf(SwitchConsoleIcon))
            if (!motion.hasGyro) { Note(str(R.string.no_gyro)); return@PaneItem }
            Gate(s.gyro) {
                SectionLabel(str(R.string.motion_what))
                Segmented(
                    listOf(GyroMode.FULL to str(R.string.full), GyroMode.SHAKE to str(R.string.shake_only)),
                    s.gyroMode, { m -> set { it.copy(gyroMode = m) } }, Modifier.fillMaxWidth(),
                )
                Note(if (s.gyroMode == GyroMode.SHAKE) str(R.string.shake_note) else str(R.string.full_note))
                SectionLabel(str(R.string.menu_axes))
                Segmented(
                    listOf(AxisPreset.KART to str(R.string.menu_axes_kart), AxisPreset.CAPPY to str(R.string.menu_axes_cappy)),
                    s.axes, { a -> set { it.copy(axes = a) } }, Modifier.fillMaxWidth(),
                )
                if (s.axes == AxisPreset.KART) {
                    Setting(str(R.string.steer_sens), str(R.string.steer_sens_value, s.kartGain, (90 / s.kartGain).roundToInt()),
                        s.kartGain, 1f..4f) { v -> set { it.copy(kartGain = (v * 4).roundToInt() / 4f) } }
                }
                if (s.gyroMode == GyroMode.SHAKE) {
                    Setting(str(R.string.shake_strength), "×%.1f".format(s.shakeGain), s.shakeGain, 1f..5f) { v ->
                        set { it.copy(shakeGain = (v * 2).roundToInt() / 2f) }
                    }
                }
                Note(str(R.string.recenter_note))
            }
        })
        add(PaneItem(
            "shake", Icons.Rounded.Vibration, Hue.ORANGE, str(R.string.motion_shake), state(s.shake),
            trailing = { AppSwitch(s.shake, { on -> set { it.copy(shake = on) } }) },
        ) {
            FeatureHeader(Icons.Rounded.Vibration, Hue.ORANGE, str(R.string.shake_toggle_note),
                listOf(Icons.Rounded.Computer, SwitchConsoleIcon, Icons.Rounded.Android))
            Gate(s.shake) {
                SectionLabel(str(R.string.motion_presses))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShakeButtons.forEach { (label, mask) ->
                        FilterChip(
                            selected = s.shakeButton == mask,
                            onClick = { set { it.copy(shakeButton = mask) } },
                            label = { Text(label, fontFamily = Rounded, fontWeight = FontWeight.Bold) },
                        )
                    }
                }
                Setting(str(R.string.sensitivity), "${(s.shakeSensitivity * 100).roundToInt()}%", s.shakeSensitivity, 0f..1f) { v ->
                    set { it.copy(shakeSensitivity = (v * 20).roundToInt() / 20f) }
                }
            }
        })
        if (motion.hasGyro) add(PaneItem(
            "dsu", Icons.Rounded.Lan, Hue.BLUE, str(R.string.motion_dsu), state(s.dsu),
            trailing = { AppSwitch(s.dsu, { on -> set { it.copy(dsu = on) } }) },
        ) {
            FeatureHeader(Icons.Rounded.Lan, Hue.BLUE, str(R.string.dsu_note), listOf(Icons.Rounded.Computer))
            Gate(s.dsu) {
                val addresses = motion.localAddresses().joinToString(" · ").ifEmpty { str(R.string.no_network) }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LocalPalette.current.surfaceHigh).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SectionLabel(str(R.string.motion_dsu_address))
                    Text(str(R.string.dsu_where, addresses, MotionSettings.DSU_PORT), color = Ink, fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold, fontFamily = Rounded)
                }
                if (s.dsu && dsuStatus.isNotEmpty()) Note(dsuStatus)
            }
        })
    }
    MasterDetail(items, selected, { selected = it }, wide, listWidth = 240.dp)
}

/** What the function does and what it works with. */
@Composable
private fun FeatureHeader(icon: ImageVector, hue: Hue, text: String, worksWith: List<ImageVector>) {
    val p = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, hue, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Rounded, lineHeight = 17.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(str(R.string.motion_works_with), color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Rounded)
                worksWith.forEach { b ->
                    Box(Modifier.size(22.dp).clip(CircleShape).background(p.surfaceHigh), contentAlignment = Alignment.Center) {
                        Icon(b, null, tint = p.muted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
    Hairline()
}

/** Settings of a function that is off: shown faded, and taps do nothing. */
@Composable
private fun Gate(on: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Box {
        Column(Modifier.alpha(if (on) 1f else 0.38f), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        if (!on) Box(Modifier.matchParentSize().clickable(remember { MutableInteractionSource() }, indication = null) {})
    }
}

/** A steering wheel turned by the live tilt output, up to the configured angle. */
@Composable
private fun SteeringWheel(value: Float, maxAngle: Float, modifier: Modifier) {
    val p = LocalPalette.current
    val angle by animateFloatAsState(value.coerceIn(-1f, 1f) * maxAngle, label = "wheel")
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val ring = r * 0.16f
        val c = center
        rotate(angle, c) {
            drawCircle(Brush.verticalGradient(listOf(Color(0xFF6B6390), Color(0xFF3A3452))), r - ring / 2, c, style = Stroke(ring))
            // Spokes: left, right and down, like a car wheel.
            listOf(180f, 0f, 90f).forEach { deg ->
                val rad = Math.toRadians(deg.toDouble())
                val end = Offset(c.x + (r - ring) * kotlin.math.cos(rad).toFloat(), c.y + (r - ring) * kotlin.math.sin(rad).toFloat())
                drawLine(Color(0xFF524A72), c, end, strokeWidth = ring * 0.7f, cap = StrokeCap.Round)
            }
            drawCircle(Accent, r * 0.2f, c)
            // Top mark, to see the turn.
            drawLine(AccentBright, Offset(c.x, c.y - r + ring * 0.1f), Offset(c.x, c.y - r + ring * 0.9f), strokeWidth = ring * 0.45f, cap = StrokeCap.Round)
        }
        drawCircle(p.outline, r, c, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Switch(checked = checked, onCheckedChange = onChange)
        Text(label, color = Ink, fontSize = 15.sp)
    }
}

@Composable
private fun Setting(
    label: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, color = Ink, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontFamily = Rounded, modifier = Modifier.weight(1f))
            Text(value, color = AccentBright, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontFamily = Rounded)
        }
        Slider(value = current.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

/** Live steering output: the marker moves from the center towards the side being steered. */
@Composable
private fun SteerBar(value: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, LocalPalette.current.outline, RoundedCornerShape(7.dp)),
    ) {
        Box(Modifier.align(Alignment.Center).width(1.dp).fillMaxHeight().background(LocalPalette.current.outline))
        Layout(
            content = { Box(Modifier.width(10.dp).fillMaxHeight().background(Accent)) },
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        ) { measurables, constraints ->
            val marker = measurables.first().measure(constraints.copy(minWidth = 0))
            val travel = (constraints.maxWidth - marker.width) / 2f
            layout(constraints.maxWidth, constraints.maxHeight) {
                marker.place((travel + value.coerceIn(-1f, 1f) * travel).roundToInt(), 0)
            }
        }
    }
}
