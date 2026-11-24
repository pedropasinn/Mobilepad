package io.github.pedropasinn.mobilepad.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.pedropasinn.mobilepad.macro.MacroConfig
import io.github.pedropasinn.mobilepad.ui.editor.MacroRow
import io.github.pedropasinn.mobilepad.ui.editor.WheelRow
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.OpenWith
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
 * MENU button. [style]: colours and,
 * for an imported skin, its button artwork.
 */
@Composable
fun ControllerScreen(
    input: GamepadInput,
    status: String,
    radial: RadialView?,
    nintendoLayout: Boolean = false,
    style: SkinStyle = builtInStyle(SkinId.VIOLET, ""),
    statusKind: StatusKind = StatusKind.IDLE,
    /** How the phone is linked, shown as an icon under MENU. */
    link: Link = Link.NONE,
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
    /** Macros and wheels, listed in the menu for reference. */
    macroConfig: MacroConfig? = null,
    onEditMacros: (() -> Unit)? = null,
    onOpenWheel: ((String) -> Unit)? = null,
    /** User layouts for each orientation, and the layout editor shown over the controller. */
    landscapeLayout: PadLayout = PadLayout(),
    portraitLayout: PadLayout = PadLayout(),
    onLayout: (portrait: Boolean, PadLayout) -> Unit = { _, _ -> },
    editingLayout: Boolean = false,
    onEditLayout: (() -> Unit)? = null,
    onEditLayoutDone: () -> Unit = {},
    /** Switches to the other saved layout (race ⇄ menus); null hides the menu entry. */
    onSwapLayout: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Back never leaves the controller by accident mid-game: it opens the pause menu, where
    // "Leave" is; pressed again it closes the menu.
    BackHandler(enabled = !editingLayout) { menuOpen = !menuOpen }
    BackHandler(enabled = editingLayout && !menuOpen) { onEditLayoutDone() }
    var selectedPart by remember { mutableStateOf<String?>(null) }
    val partBounds = remember { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    var portraitNow by remember { mutableStateOf(false) }
    val layoutScope = PadLayoutScope(
        if (portraitNow) portraitLayout else landscapeLayout,
        editingLayout, selectedPart.takeIf { editingLayout }, partBounds,
    )
    val actions = MenuActions(
        onDisconnect, steering, onSteering, motionSensor, onMotionSensor,
        axisPreset, onAxisPreset, rumble, onRumble, onRecenter, onAmiibo,
    )
    fun button(mask: Int): (Boolean) -> Unit = { down -> input.touch { it.with(mask, down) } }
    val labels = ActiveButtonNames.value.labels()
    val shell = style.colors

    CompositionLocalProvider(LocalSkin provides shell, LocalSkinParts provides style.parts, LocalPadLayout provides layoutScope) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(shell.shellTop, shell.shellBottom))),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().cutoutPadding()) {
                val menuBlock: @Composable () -> Unit = {
                    MenuBlock(status, statusKind, link, recenter = steering || motionSensor, onMenu = { menuOpen = true }, onRecenter = onRecenter)
                }
                val portrait = maxHeight > maxWidth
                if (portrait != portraitNow) { portraitNow = portrait; partBounds.clear(); selectedPart = null }
                if (portrait) {
                    PortraitLayout(input, labels, maxWidth, menuBlock, ::button)
                } else {
                    LandscapeLayout(input, labels, maxHeight, menuBlock, ::button)
                }
                CustomButtonsLayer(layoutScope.layout.buttons, input, maxWidth, maxHeight, onSwap = { onSwapLayout?.invoke() })
            }

            if (editingLayout) {
                PadLayoutEditor(
                    layoutScope,
                    onSelect = { selectedPart = it },
                    onChange = { onLayout(portraitNow, it) },
                    onDone = { selectedPart = null; onEditLayoutDone() },
                )
            }
            // Above the black layer: the wheel must stay visible in OLED mode.
            radial?.let {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)))
                RadialMenuOverlay(it, nintendoLayout = nintendoLayout, onTap = onRadialTap, onBack = onRadialBack)
            }
            AnimatedVisibility(menuOpen, enter = fadeIn(), exit = fadeOut()) {
                ControllerMenu(status, statusKind, actions, macroConfig, onEditMacros, onOpenWheel, onEditLayout, onSwapLayout, onClose = { menuOpen = false })
            }
        }
    }
}

/** MENU, the recenter shortcut while motion is on, and the status line. */
@Composable
private fun MenuBlock(status: String, statusKind: StatusKind, link: Link, recenter: Boolean, onMenu: () -> Unit, onRecenter: () -> Unit) {
    val shell = LocalSkin.current
    Column(Modifier.tweak(PadPart.MENU), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PadButton(str(R.string.pad_menu), { if (it) onMenu() }, Modifier.width(64.dp).height(26.dp), PillShape, fontSize = 11, part = Part.MENU)
            }
            if (recenter) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PadButton(str(R.string.menu_recenter), { if (it) onRecenter() }, Modifier.size(34.dp),
                        icon = Icons.Rounded.CenterFocusStrong, fontSize = 14, selected = true)
                    ShellLabel("◎")
                }
            }
        }
        // The connection as an icon: its kind, coloured by its state; the words stay for screen readers.
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(shell.shellInk.copy(alpha = 0.12f))
                .semantics { contentDescription = status },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                linkIcon(statusKind, link), null, modifier = Modifier.size(15.dp),
                tint = if (statusKind == StatusKind.IDLE) shell.shellInk.copy(alpha = 0.6f) else statusColor(statusKind),
            )
        }
    }
}

/** Landscape layout; also drawn, scaled down, by [ControllerPreview]. */
@Composable
private fun BoxScope.LandscapeLayout(
    input: GamepadInput,
    labels: PadLabels,
    maxHeight: Dp,
    menuBlock: @Composable () -> Unit,
    button: (Int) -> (Boolean) -> Unit,
) {
    val stick = (maxHeight * 0.42f).coerceAtMost(190.dp)
    val face = (maxHeight * 0.14f).coerceIn(48.dp, 72.dp)

    // Shoulders and triggers
    Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PadButton(labels.lt, { d -> input.touch { it.copy(leftTrigger = if (d) 255 else 0) } },
            Modifier.tweak(PadPart.LT).width(96.dp).height(50.dp), TriggerShape, fontSize = 15, part = Part.LT)
        PadButton(labels.lb, button(Buttons.LEFT_SHOULDER), Modifier.tweak(PadPart.LB).width(96.dp).height(50.dp), PillShape, fontSize = 15, part = Buttons.LEFT_SHOULDER)
    }
    Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PadButton(labels.rb, button(Buttons.RIGHT_SHOULDER), Modifier.tweak(PadPart.RB).width(96.dp).height(50.dp), PillShape, fontSize = 15, part = Buttons.RIGHT_SHOULDER)
        PadButton(labels.rt, { d -> input.touch { it.copy(rightTrigger = if (d) 255 else 0) } },
            Modifier.tweak(PadPart.RT).width(96.dp).height(50.dp), TriggerShape, fontSize = 15, part = Part.RT)
    }

    // Centre, between the shoulder groups and above the sticks: MENU and status, the system
    // buttons, then the wordmark in the free space between the sticks.
    Column(
        Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 222.dp, end = 222.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        menuBlock()
        SystemButtons(labels, button)
        Wordmark()
    }

    // Left side: stick + D-pad + L3
    Stick(
        onMove = { x, y -> input.touch { it.copy(leftX = ControllerState.axis(x), leftY = ControllerState.axis(y)) } },
        modifier = Modifier.align(Alignment.CenterStart).offset(x = 36.dp, y = (-6).dp).tweak(PadPart.LSTICK),
        size = stick,
    )
    DPad(
        onChange = { mask -> input.touch { it.copy(buttons = (it.buttons and DPAD_MASK.inv()) or mask) } },
        modifier = Modifier.align(Alignment.BottomStart).offset(x = stick + 40.dp, y = (-16).dp).tweak(PadPart.DPAD),
        size = (stick * 0.8f),
    )
    PadButton("L3", button(Buttons.LEFT_THUMB),
        Modifier.align(Alignment.BottomStart).offset(x = 20.dp, y = (-18).dp).tweak(PadPart.L3).size(46.dp), fontSize = 13)

    // Right side: ABXY + stick + R3
    FaceButtons(labels, face, button, Modifier.align(Alignment.CenterEnd).offset(x = (-36).dp, y = (-20).dp))
    Stick(
        onMove = { x, y -> input.touch { it.copy(rightX = ControllerState.axis(x), rightY = ControllerState.axis(y)) } },
        modifier = Modifier.align(Alignment.BottomEnd).offset(x = -(face * 3 + 56.dp), y = (-12).dp).tweak(PadPart.RSTICK),
        size = stick * 0.85f,
    )
    PadButton("R3", button(Buttons.RIGHT_THUMB),
        Modifier.align(Alignment.BottomEnd).offset(x = (-20).dp, y = (-18).dp).tweak(PadPart.R3).size(46.dp), fontSize = 13)
}

/**
 * The controller in miniature, exactly as it will look: the real landscape layout drawn at a phone
 * size ([screen], 780 × 360 dp unless known) and scaled to fit. Touches go to [onClick], never to
 * the buttons.
 */
@Composable
fun ControllerPreview(
    style: SkinStyle,
    modifier: Modifier = Modifier,
    layout: PadLayout = PadLayout(),
    onClick: (() -> Unit)? = null,
    screen: DpSize = DpSize(780.dp, 360.dp),
) {
    val input = remember { GamepadInput() }
    Box(modifier.aspectRatio(screen.width / screen.height).clip(RoundedCornerShape(14.dp))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val k = maxWidth / screen.width
            CompositionLocalProvider(LocalSkin provides style.colors, LocalSkinParts provides style.parts, LocalPadLayout provides PadLayoutScope(layout, false, null)) {
                Box(
                    Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .requiredSize(screen)
                        .graphicsLayer { scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0f, 0f) }
                        .background(Brush.verticalGradient(listOf(style.colors.shellTop, style.colors.shellBottom))),
                ) {
                    LandscapeLayout(input, ActiveButtonNames.value.labels(), screen.height, { MenuBlock("Mobilepad", StatusKind.CONNECTED, Link.WIFI, false, {}, {}) }) { { } }
                    CustomButtonsLayer(layout.buttons, input, screen.width, screen.height)
                }
            }
        }
        // Above the controls: the miniature is not playable.
        Box(Modifier.matchParentSize().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.pointerInput(Unit) {}))
    }
}

/** A system button as the console prints it: name (read aloud), mark inside, size and shape. */
private class SysKey(val name: String, val icon: ImageVector?, val w: Int, val h: Int, val round: Boolean, val font: Int)

/** A face button's mark: text, or a symbol drawn in its own colour (PlayStation). */
private class FaceMark(val text: String, val icon: ImageVector? = null, val color: Color = Color.Unspecified)

/** Printing for each console; positions stay the same, only what is printed changes. */
private class PadLabels(
    val lt: String, val lb: String, val rb: String, val rt: String,
    val back: SysKey, val guide: SysKey, val start: SysKey,
    val a: FaceMark, val b: FaceMark, val x: FaceMark, val y: FaceMark,
)

private val XBOX = PadLabels(
    "LT", "LB", "RB", "RT",
    SysKey("View", XboxView, 32, 32, true, 14), SysKey("Guide", XboxGuide, 40, 40, true, 20), SysKey("Menu", ThreeLines, 32, 32, true, 14),
    // Letters in their colours, as on the controller.
    FaceMark("A", color = Color(0xFF6CD43A)), FaceMark("B", color = Color(0xFFFF5A5F)),
    FaceMark("X", color = Color(0xFF4F9BFF)), FaceMark("Y", color = Color(0xFFFFCB3D)),
)
// Nintendo printing: the east button is A, the south one B.
private val NINTENDO = PadLabels(
    "ZL", "L", "R", "ZR",
    SysKey("−", NinMinus, 30, 30, true, 14), SysKey("HOME", NinHome, 36, 36, true, 16), SysKey("+", NinPlus, 30, 30, true, 14),
    FaceMark("B"), FaceMark("A"), FaceMark("Y"), FaceMark("X"),
)
// PlayStation printing: cross south, circle east, square west, triangle north, each in its colour.
private val PLAYSTATION = PadLabels(
    "L2", "L1", "R1", "R2",
    SysKey("Share", PsShare, 40, 24, false, 12), SysKey("PS", null, 36, 36, true, 12), SysKey("Options", ThreeLines, 40, 24, false, 12),
    FaceMark("✕", PsCross, Color(0xFF7CA8FF)), FaceMark("○", PsCircle, Color(0xFFFF6A6A)),
    FaceMark("□", PsSquare, Color(0xFFF39BD0)), FaceMark("△", PsTriangle, Color(0xFF3FD0A8)),
)

/** Which console's names are printed on the controller; [AUTO] follows the connection. */
enum class ButtonNames { AUTO, XBOX, PLAYSTATION, NINTENDO }

private fun ButtonNames.labels() = when (this) {
    ButtonNames.NINTENDO -> NINTENDO
    ButtonNames.PLAYSTATION -> PLAYSTATION
    else -> XBOX
}

/**
 * Names in use right now ([ButtonNames.AUTO] already resolved), set by the activity; the
 * controller and every miniature print them.
 */
val ActiveButtonNames = androidx.compose.runtime.mutableStateOf(ButtonNames.XBOX)

@Composable
private fun FaceButtons(labels: PadLabels, face: Dp, button: (Int) -> (Boolean) -> Unit, modifier: Modifier = Modifier) {
    // A skin with its own coloured buttons keeps its ink; otherwise symbols use their colour.
    val ownColours = LocalSkin.current.faceColors != null
    @Composable
    fun Face(m: FaceMark, mask: Int, place: Modifier) = PadButton(
        m.text, button(mask), place.size(face), fontSize = 20, part = mask,
        icon = m.icon, color = if (ownColours) Color.Unspecified else m.color,
    )
    Box(modifier.tweak(PadPart.FACE).size(face * 3)) {
        Face(labels.y, Buttons.Y, Modifier.align(Alignment.TopCenter))
        Face(labels.x, Buttons.X, Modifier.align(Alignment.CenterStart))
        Face(labels.b, Buttons.B, Modifier.align(Alignment.CenterEnd))
        Face(labels.a, Buttons.A, Modifier.align(Alignment.BottomCenter))
    }
}

/** Back / Guide / Start shaped and marked like the chosen console's, the mark inside the button. */
@Composable
private fun SystemButtons(labels: PadLabels, button: (Int) -> (Boolean) -> Unit) {
    Row(Modifier.tweak(PadPart.SYSTEM), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((key, mask) in listOf(labels.back to Buttons.BACK, labels.guide to Buttons.GUIDE, labels.start to Buttons.START)) {
            PadButton(
                key.name, button(mask), Modifier.size(key.w.dp, key.h.dp), if (key.round) CircleShape else PillShape,
                fontSize = key.font, icon = key.icon, part = mask,
            )
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
        modifier = Modifier.tweak(PadPart.WORDMARK),
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
                    Modifier.tweak(PadPart.LT).width(72.dp).height(46.dp), TriggerShape, fontSize = 14, part = Part.LT)
                PadButton(labels.lb, button(Buttons.LEFT_SHOULDER), Modifier.tweak(PadPart.LB).width(72.dp).height(46.dp), PillShape, fontSize = 14, part = Buttons.LEFT_SHOULDER)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PadButton(labels.rb, button(Buttons.RIGHT_SHOULDER), Modifier.tweak(PadPart.RB).width(72.dp).height(46.dp), PillShape, fontSize = 14, part = Buttons.RIGHT_SHOULDER)
                PadButton(labels.rt, { d -> input.touch { it.copy(rightTrigger = if (d) 255 else 0) } },
                    Modifier.tweak(PadPart.RT).width(72.dp).height(46.dp), TriggerShape, fontSize = 14, part = Part.RT)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Stick(
                onMove = { x, y -> input.touch { it.copy(leftX = ControllerState.axis(x), leftY = ControllerState.axis(y)) } },
                modifier = Modifier.tweak(PadPart.LSTICK),
                size = stick,
            )
            Stick(
                onMove = { x, y -> input.touch { it.copy(rightX = ControllerState.axis(x), rightY = ControllerState.axis(y)) } },
                modifier = Modifier.tweak(PadPart.RSTICK),
                size = stick * 0.85f,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            DPad(
                onChange = { mask -> input.touch { it.copy(buttons = (it.buttons and DPAD_MASK.inv()) or mask) } },
                modifier = Modifier.tweak(PadPart.DPAD),
                size = stick * 0.9f,
            )
            FaceButtons(labels, face, button)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            PadButton("L3", button(Buttons.LEFT_THUMB), Modifier.tweak(PadPart.L3).size(44.dp), fontSize = 13)
            SystemButtons(labels, button)
            PadButton("R3", button(Buttons.RIGHT_THUMB), Modifier.tweak(PadPart.R3).size(44.dp), fontSize = 13)
        }
    }
}

/**
 * In-game menu, like a console pause screen: the main actions on one side, quick settings and a
 * reference of the macros and wheels on the other. Taps outside the panel close it.
 */
@Composable
private fun ControllerMenu(
    status: String,
    statusKind: StatusKind,
    a: MenuActions,
    macros: MacroConfig?,
    onEditMacros: (() -> Unit)?,
    onOpenWheel: ((String) -> Unit)?,
    onEditLayout: (() -> Unit)?,
    onSwapLayout: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val p = LocalPalette.current
    var tab by rememberSaveable { mutableStateOf("quick") }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.66f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        val wide = maxWidth > maxHeight
        val panel = Modifier
            .padding(if (wide) 14.dp else 12.dp)
            .widthIn(max = 900.dp)
            .fillMaxHeight(if (wide) 1f else 0.92f)
            .clip(RoundedCornerShape(30.dp))
            .background(p.background.copy(alpha = 0.94f))
            .glass(RoundedCornerShape(30.dp))
            // Swallows taps on the panel itself.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .padding(16.dp)

        val actions: @Composable (Boolean) -> Unit = { column ->
            val items = buildList {
                add(Triple(Icons.Rounded.PlayArrow, Hue.GREEN, str(R.string.menu_resume)) to onClose)
                onEditMacros?.let { add(Triple(Icons.Rounded.AutoAwesome, Hue.ROSE, str(R.string.nav_macros)) to { onClose(); it() }) }
                onSwapLayout?.let { add(Triple(Icons.Rounded.SwapHoriz, Hue.ORANGE, str(R.string.layout_swap)) to { onClose(); it() }) }
                onEditLayout?.let { add(Triple(Icons.Rounded.OpenWith, Hue.TEAL, str(R.string.layout_edit)) to { onClose(); it() }) }
                a.onAmiibo?.let { add(Triple(Icons.Rounded.Nfc, Hue.GOLD, str(R.string.nav_amiibo)) to { onClose(); it() }) }
                add(Triple(Icons.AutoMirrored.Rounded.Logout, Hue.ROSE, str(R.string.menu_leave)) to { onClose(); a.onDisconnect() })
            }
            if (column) {
                items.forEach { (t, action) -> ActionRow(t.first, t.second, t.third, primary = t.first == Icons.Rounded.PlayArrow, onClick = action) }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { (t, action) -> ActionTile(t.first, t.second, t.third, Modifier.weight(1f), onClick = action) }
                }
            }
        }
        val tabs: @Composable ColumnScope.() -> Unit = {
            Segmented(
                listOf("quick" to str(R.string.menu_quick), "macros" to str(R.string.nav_macros)),
                tab, { tab = it }, Modifier.fillMaxWidth(),
            )
            FadingColumn(Modifier.fillMaxWidth().weight(1f), spacing = 8.dp) {
                if (tab == "quick") QuickSettings(a) else MacroReference(macros, onOpenWheel?.let { open -> { id: String -> onClose(); open(id) } })
            }
        }

        if (wide) {
            Row(panel, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.width(250.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Title and status on one line: the actions below get the height.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(str(R.string.pad_menu_title), color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = Rounded,
                            modifier = Modifier.padding(end = 6.dp))
                        Dot(statusColor(statusKind), 7.dp)
                        Text(status, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Rounded, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FadingColumn(Modifier.weight(1f), spacing = 6.dp) { actions(true) }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp), content = tabs)
            }
        } else {
            Column(panel, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(str(R.string.pad_menu_title), color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = Rounded)
                    StatusPill(status, statusKind, Modifier.weight(1f, fill = false))
                }
                actions(false)
                tabs()
            }
        }
    }
}

/** Switches for what is often changed mid-game. */
@Composable
private fun QuickSettings(a: MenuActions) {
    a.onRumble?.let { SwitchRow(Icons.Rounded.Vibration, Hue.ORANGE, str(R.string.menu_rumble), a.rumble) { it() } }
    a.onSteering?.let { SwitchRow(Icons.Rounded.ScreenRotation, Hue.TEAL, str(R.string.menu_steering), a.steering) { it() } }
    a.onMotionSensor?.let { SwitchRow(Icons.Rounded.Sensors, Hue.VIOLET, str(R.string.menu_sensor), a.motionSensor) { it() } }
    a.axisPreset?.let { preset ->
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(str(R.string.menu_axes))
            Segmented(
                listOf(AxisPreset.KART to str(R.string.menu_axes_kart), AxisPreset.CAPPY to str(R.string.menu_axes_cappy)),
                preset, a.onAxisPreset, Modifier.fillMaxWidth(),
            )
        }
    }
    if (a.steering || a.motionSensor) {
        ActionRow(Icons.Rounded.CenterFocusStrong, Hue.SLATE, str(R.string.menu_recenter), onClick = a.onRecenter)
    }
}

/** What each macro and wheel does, to look up mid-game; a wheel can be opened from here. */
@Composable
private fun MacroReference(config: MacroConfig?, onOpenWheel: ((String) -> Unit)?) {
    if (config == null || (config.macros.isEmpty() && config.radials.isEmpty())) {
        Note(str(R.string.no_macros))
        return
    }
    if (config.macros.isNotEmpty()) SectionLabel(str(R.string.nav_macros_short))
    config.macros.forEach { MacroRow(it) }
    if (config.radials.isNotEmpty()) SectionLabel(str(R.string.wheels))
    config.radials.values.forEach { menu -> WheelRow(menu, config, onOpenWheel?.let { open -> { open(menu.id) } }) }
}

@Composable
private fun SwitchRow(icon: ImageVector, hue: Hue, label: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), selected = on).clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon, if (on) hue else Hue.SLATE, 34.dp)
        Text(label, color = LocalPalette.current.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, modifier = Modifier.weight(1f))
        AppSwitch(on, { onToggle() })
    }
}

@Composable
private fun ActionRow(icon: ImageVector, hue: Hue, label: String, primary: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .then(if (primary) Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF9B6BFF), Color(0xFF6A2BE8)))) else Modifier.glass(RoundedCornerShape(18.dp)))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (primary) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
        } else IconTile(icon, hue, 30.dp)
        Text(label, color = if (primary) Color.White else LocalPalette.current.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded)
    }
}

@Composable
private fun ActionTile(icon: ImageVector, hue: Hue, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.glass(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconTile(icon, hue, 34.dp)
        Text(label, color = LocalPalette.current.text, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = Rounded,
            textAlign = TextAlign.Center, maxLines = 2, lineHeight = 13.sp, overflow = TextOverflow.Ellipsis)
    }
}
