package io.github.pedropasinn.mobilepad.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pedropasinn.mobilepad.R
import io.github.pedropasinn.mobilepad.input.GamepadInput
import io.github.pedropasinn.mobilepad.macro.RadialView
import io.github.pedropasinn.mobilepad.motion.AxisPreset
import io.github.pedropasinn.mobilepad.protocol.Buttons
import io.github.pedropasinn.mobilepad.protocol.ControllerState
import io.github.pedropasinn.mobilepad.str

private val DPAD_MASK = Buttons.DPAD_UP or Buttons.DPAD_DOWN or Buttons.DPAD_LEFT or Buttons.DPAD_RIGHT

/** What the in-game menu offers; null actions hide their tile. */
private class MenuActions(
    val onScreenSaver: () -> Unit,
    val onDisconnect: () -> Unit,
    val steering: Boolean,
    val onSteering: (() -> Unit)?,
    val motionSensor: Boolean,
    val onMotionSensor: (() -> Unit)?,
    val axisPreset: AxisPreset?,
    val onAxisPreset: (AxisPreset) -> Unit,
    val rumble: Boolean,
    val onRumble: (() -> Unit)?,
    val onRecenter: () -> Unit,
    val onAmiibo: (() -> Unit)?,
)

/**
 * The controller as a handheld shell: Xbox-style layout in landscape, Game Boy-style
 * ([PortraitLayout]) when the screen is taller than wide. Every secondary function sits behind the
 * MENU button. [blackout] draws a black layer while controls stay active.
 */
@Composable
fun ControllerScreen(
    input: GamepadInput,
    status: String,
    blackout: Boolean,
    radial: RadialView?,
    nintendoLayout: Boolean = false,
    skin: SkinId = SkinId.VIOLET,
    /** Imported Delta skin; null or without a layout for the orientation → built-in [skin]. */
    deltaSkin: io.github.pedropasinn.mobilepad.skin.DeltaSkin? = null,
    statusKind: StatusKind = StatusKind.IDLE,
    onScreenSaver: () -> Unit,
    onDisconnect: () -> Unit,
    onRadialTap: (Int) -> Unit = {},
    onRadialBack: () -> Unit = {},
    steering: Boolean = false,
    onSteering: (() -> Unit)? = null,
    onRecenter: () -> Unit = {},
    /** Joy-Con-style motion sensor on/off; null hides the option (no gyroscope). */
    motionSensor: Boolean = false,
    onMotionSensor: (() -> Unit)? = null,
    /** Motion axis preset while the sensor is on (null hides the switch). */
    axisPreset: AxisPreset? = null,
    onAxisPreset: (AxisPreset) -> Unit = {},
    /** Phone vibration for the game's rumble. */
    rumble: Boolean = true,
    onRumble: (() -> Unit)? = null,
    /** Opens the amiibo wheel; null hides the option (not connected to a Switch). */
    onAmiibo: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = menuOpen) { menuOpen = false }
    val actions = MenuActions(
        onScreenSaver, onDisconnect, steering, onSteering, motionSensor, onMotionSensor,
        axisPreset, onAxisPreset, rumble, onRumble, onRecenter, onAmiibo,
    )
    fun button(mask: Int): (Boolean) -> Unit = { down -> input.touch { it.with(mask, down) } }
    val labels = if (nintendoLayout) NINTENDO else XBOX
    val shell = Skins.getValue(skin)
    // An imported skin with a layout for this orientation draws on black, cutout strip included.
    val landscapeNow = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val onDelta = remember(deltaSkin, landscapeNow) { deltaSkin?.hasLayout(landscapeNow) == true }

    CompositionLocalProvider(LocalSkin provides shell) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                // Pure black under the saver layer, the cutout strip included.
                .background(
                    if (blackout || onDelta) Brush.verticalGradient(listOf(Color.Black, Color.Black))
                    else Brush.verticalGradient(listOf(shell.shellTop, shell.shellBottom)),
                )
                .windowInsetsPadding(WindowInsets.displayCutout),
        ) {
            // MENU, the recenter shortcut while motion is on, and the status line.
            val menuBlock: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PadButton("", { if (it) menuOpen = true }, Modifier.width(54.dp).height(24.dp), PillShape)
                            ShellLabel(str(R.string.pad_menu))
                        }
                        if (steering || motionSensor) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                PadButton(str(R.string.menu_recenter), { if (it) onRecenter() }, Modifier.size(34.dp),
                                    icon = Icons.Rounded.CenterFocusStrong, fontSize = 14, selected = true)
                                ShellLabel("◎")
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Dot(statusColor(statusKind), 7.dp)
                        Text(status, color = shell.shellInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = Rounded,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            val landscape = maxWidth > maxHeight
            val delta = remember(deltaSkin, landscape) { deltaSkin?.representation(landscape) }
            if (deltaSkin != null && delta != null) {
                DeltaSkinPad(deltaSkin, delta, input, status, statusKind, onMenu = { menuOpen = true })
            } else if (!landscape) {
                PortraitLayout(input, labels, maxWidth, menuBlock, ::button)
            } else {
                val stick = (maxHeight * 0.42f).coerceAtMost(190.dp)
                val face = (maxHeight * 0.14f).coerceIn(48.dp, 72.dp)

                // Shoulders and triggers
                Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PadButton(labels.lt, { d -> input.touch { it.copy(leftTrigger = if (d) 255 else 0) } },
                        Modifier.width(96.dp).height(50.dp), TriggerShape, fontSize = 15)
                    PadButton(labels.lb, button(Buttons.LEFT_SHOULDER), Modifier.width(96.dp).height(50.dp), PillShape, fontSize = 15)
                }
                Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PadButton(labels.rb, button(Buttons.RIGHT_SHOULDER), Modifier.width(96.dp).height(50.dp), PillShape, fontSize = 15)
                    PadButton(labels.rt, { d -> input.touch { it.copy(rightTrigger = if (d) 255 else 0) } },
                        Modifier.width(96.dp).height(50.dp), TriggerShape, fontSize = 15)
                }

                // Centre, between the shoulder groups and above the sticks: MENU and status, the
                // system buttons, then the wordmark in the free space between the sticks.
                Column(
                    Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 222.dp, end = 222.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    menuBlock()
                    SystemButtons(labels, ::button)
                    Wordmark()
                }

                // Left side: stick + D-pad + L3
                Stick(
                    onMove = { x, y -> input.touch { it.copy(leftX = ControllerState.axis(x), leftY = ControllerState.axis(y)) } },
                    modifier = Modifier.align(Alignment.CenterStart).offset(x = 36.dp, y = (-6).dp),
                    size = stick,
                )
                DPad(
                    onChange = { mask -> input.touch { it.copy(buttons = (it.buttons and DPAD_MASK.inv()) or mask) } },
                    modifier = Modifier.align(Alignment.BottomStart).offset(x = stick + 40.dp, y = (-16).dp),
                    size = (stick * 0.8f),
                )
                PadButton("L3", button(Buttons.LEFT_THUMB),
                    Modifier.align(Alignment.BottomStart).offset(x = 20.dp, y = (-18).dp).size(46.dp), fontSize = 13)

                // Right side: ABXY + stick + R3
                FaceButtons(labels, face, ::button, Modifier.align(Alignment.CenterEnd).offset(x = (-36).dp, y = (-20).dp))
                Stick(
                    onMove = { x, y -> input.touch { it.copy(rightX = ControllerState.axis(x), rightY = ControllerState.axis(y)) } },
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = -(face * 3 + 56.dp), y = (-12).dp),
                    size = stick * 0.85f,
                )
                PadButton("R3", button(Buttons.RIGHT_THUMB),
                    Modifier.align(Alignment.BottomEnd).offset(x = (-20).dp, y = (-18).dp).size(46.dp), fontSize = 13)
            }

            // Drawn last, without pointer input: touches still reach the controls underneath.
            if (blackout) Box(Modifier.fillMaxSize().background(Color.Black))
            // Above the black layer: the wheel must stay visible in OLED mode.
            radial?.let {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)))
                RadialMenuOverlay(it, nintendoLayout = nintendoLayout, onTap = onRadialTap, onBack = onRadialBack)
            }
            AnimatedVisibility(menuOpen, enter = fadeIn(), exit = fadeOut()) {
                ControllerMenu(status, statusKind, actions, onClose = { menuOpen = false })
            }
        }
    }
}

/** Button names for the two layouts; positions stay the same, only the printing changes. */
private class PadLabels(
    val lt: String, val lb: String, val rb: String, val rt: String,
    val back: String, val guide: String, val start: String,
    val a: String, val b: String, val x: String, val y: String,
)

private val XBOX = PadLabels("LT", "LB", "RB", "RT", "BACK", "GUIDE", "START", "A", "B", "X", "Y")
// Nintendo printing: the east button is A, the south one B.
private val NINTENDO = PadLabels("ZL", "L", "R", "ZR", "−", "HOME", "+", "B", "A", "Y", "X")

@Composable
private fun FaceButtons(labels: PadLabels, face: Dp, button: (Int) -> (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(face * 3)) {
        PadButton(labels.y, button(Buttons.Y), Modifier.align(Alignment.TopCenter).size(face), fontSize = 20)
        PadButton(labels.x, button(Buttons.X), Modifier.align(Alignment.CenterStart).size(face), fontSize = 20)
        PadButton(labels.b, button(Buttons.B), Modifier.align(Alignment.CenterEnd).size(face), fontSize = 20)
        PadButton(labels.a, button(Buttons.A), Modifier.align(Alignment.BottomCenter).size(face), fontSize = 20)
    }
}

/** Back / Guide / Start as small pills with the name printed underneath. */
@Composable
private fun SystemButtons(labels: PadLabels, button: (Int) -> (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Top) {
        for ((label, mask) in listOf(labels.back to Buttons.BACK, labels.guide to Buttons.GUIDE, labels.start to Buttons.START)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                PadButton("", button(mask), Modifier.width(50.dp).height(22.dp), PillShape)
                ShellLabel(label)
            }
        }
    }
}

@Composable
private fun Wordmark() {
    Text(
        "MOBILEPAD",
        color = LocalSkin.current.shellInk.copy(alpha = 0.7f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        fontFamily = Rounded,
        letterSpacing = 4.sp,
    )
}

/** Vertical, Game Boy-style layout: MENU and status on top, controls in the lower part. */
@Composable
private fun PortraitLayout(
    input: GamepadInput,
    labels: PadLabels,
    width: Dp,
    menuBlock: @Composable () -> Unit,
    button: (Int) -> (Boolean) -> Unit,
) {
    val stick = (width * 0.40f).coerceAtMost(170.dp)
    val face = (width * 0.15f).coerceIn(48.dp, 68.dp)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        menuBlock()
        Spacer(Modifier.weight(1f))
        Wordmark()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PadButton(labels.lt, { d -> input.touch { it.copy(leftTrigger = if (d) 255 else 0) } },
                    Modifier.width(72.dp).height(46.dp), TriggerShape, fontSize = 14)
                PadButton(labels.lb, button(Buttons.LEFT_SHOULDER), Modifier.width(72.dp).height(46.dp), PillShape, fontSize = 14)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PadButton(labels.rb, button(Buttons.RIGHT_SHOULDER), Modifier.width(72.dp).height(46.dp), PillShape, fontSize = 14)
                PadButton(labels.rt, { d -> input.touch { it.copy(rightTrigger = if (d) 255 else 0) } },
                    Modifier.width(72.dp).height(46.dp), TriggerShape, fontSize = 14)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Stick(
                onMove = { x, y -> input.touch { it.copy(leftX = ControllerState.axis(x), leftY = ControllerState.axis(y)) } },
                size = stick,
            )
            Stick(
                onMove = { x, y -> input.touch { it.copy(rightX = ControllerState.axis(x), rightY = ControllerState.axis(y)) } },
                size = stick * 0.85f,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            DPad(
                onChange = { mask -> input.touch { it.copy(buttons = (it.buttons and DPAD_MASK.inv()) or mask) } },
                size = stick * 0.9f,
            )
            FaceButtons(labels, face, button)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            PadButton("L3", button(Buttons.LEFT_THUMB), Modifier.size(44.dp), fontSize = 13)
            SystemButtons(labels, button)
            PadButton("R3", button(Buttons.RIGHT_THUMB), Modifier.size(44.dp), fontSize = 13)
        }
    }
}

/** In-game menu: a glass panel of tiles over a dimmed controller. Taps outside close it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControllerMenu(status: String, statusKind: StatusKind, a: MenuActions, onClose: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 640.dp)
                .glass(RoundedCornerShape(30.dp))
                .background(p.background.copy(alpha = 0.72f))
                // Swallows taps on the panel itself.
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(str(R.string.pad_menu_title), color = p.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded)
                StatusPill(status, statusKind, Modifier.weight(1f, fill = false))
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MenuTile(Icons.Rounded.PlayArrow, Hue.GREEN, str(R.string.menu_resume), onClick = onClose)
                MenuTile(Icons.Rounded.Bedtime, Hue.BLUE, str(R.string.saver)) { onClose(); a.onScreenSaver() }
                a.onRumble?.let { MenuTile(Icons.Rounded.Vibration, Hue.ORANGE, str(R.string.menu_rumble), on = a.rumble, onClick = it) }
                a.onSteering?.let { MenuTile(Icons.Rounded.ScreenRotation, Hue.TEAL, str(R.string.menu_steering), on = a.steering, onClick = it) }
                a.onMotionSensor?.let { MenuTile(Icons.Rounded.Sensors, Hue.VIOLET, str(R.string.menu_sensor), on = a.motionSensor, onClick = it) }
                if (a.steering || a.motionSensor) {
                    MenuTile(Icons.Rounded.CenterFocusStrong, Hue.SLATE, str(R.string.menu_recenter)) { a.onRecenter(); onClose() }
                }
                a.onAmiibo?.let { MenuTile(Icons.Rounded.Nfc, Hue.GOLD, str(R.string.nav_amiibo)) { onClose(); it() } }
                MenuTile(Icons.AutoMirrored.Rounded.Logout, Hue.ROSE, str(R.string.menu_leave)) { onClose(); a.onDisconnect() }
            }
            // Motion axes per game: kart for racing games, Cappy for the hat throw.
            a.axisPreset?.let { preset ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(str(R.string.menu_axes))
                    Segmented(
                        listOf(AxisPreset.KART to str(R.string.menu_axes_kart), AxisPreset.CAPPY to str(R.string.menu_axes_cappy)),
                        preset, a.onAxisPreset, Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Square tile of the in-game menu; [on] lights the border of on/off functions. */
@Composable
private fun MenuTile(icon: ImageVector, hue: Hue, label: String, on: Boolean? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .width(104.dp)
            .glass(RoundedCornerShape(20.dp), selected = on == true)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconTile(icon, if (on == false) Hue.SLATE else hue, 40.dp)
        Text(label, color = p.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Rounded,
            textAlign = TextAlign.Center, maxLines = 2, lineHeight = 14.sp)
        if (on != null) {
            Text(
                str(if (on) R.string.pad_state_on else R.string.pad_state_off),
                color = if (on) AccentBright else p.faint,
                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, letterSpacing = 1.sp,
            )
        }
    }
}
