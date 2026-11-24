package io.github.pedropasinn.mobilepad.ui

import android.content.Context
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.pedropasinn.mobilepad.BuildConfig
import io.github.pedropasinn.mobilepad.R
import io.github.pedropasinn.mobilepad.ScreenOrientation
import io.github.pedropasinn.mobilepad.bt.GenericGamepadProfile
import io.github.pedropasinn.mobilepad.bt.SwitchProProfile
import io.github.pedropasinn.mobilepad.screen.ShizukuScreen
import io.github.pedropasinn.mobilepad.skin.DeltaSkin
import io.github.pedropasinn.mobilepad.str
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

enum class StatusKind { IDLE, BUSY, CONNECTED, ERROR }

/** Pages of the home screen; the controller and the macro editor open as their own screens. */
private enum class Section(val icon: ImageVector, val hue: Hue, val label: Int) {
    CONNECT(Icons.Rounded.Link, Hue.VIOLET, R.string.nav_connect),
    CONTROLLER(Icons.Rounded.Gamepad, Hue.ROSE, R.string.nav_controller_setup),
    MOTION(Icons.Rounded.Explore, Hue.TEAL, R.string.nav_motion),
    EXTRAS(Icons.Rounded.Extension, Hue.GOLD, R.string.nav_extras),
    SETTINGS(Icons.Rounded.Settings, Hue.BLUE, R.string.nav_settings_short),
}

/** Everything the Controller page shows and changes. */
class ControllerPanel(
    val builtIn: SkinId,
    val onBuiltIn: (SkinId) -> Unit,
    val skins: List<DeltaSkin>,
    /** Imported skin in use; null: the built-in one. */
    val selected: String?,
    val onSelect: (String?) -> Unit,
    val onImport: () -> Unit,
    val onDelete: (DeltaSkin) -> Unit,
    /** Skin just imported, shown in a preview until used or dismissed. */
    val preview: DeltaSkin?,
    val onPreviewDone: (use: Boolean) -> Unit,
    val currentName: String,
    val macroSummary: String,
    val onMacros: () -> Unit,
    val orientation: ScreenOrientation,
    val onOrientation: (ScreenOrientation) -> Unit,
    val remap: Map<Int, Int> = emptyMap(),
    val onRemap: (Map<Int, Int>) -> Unit = {},
    /** Style in use and the landscape layout, for the miniature of the layout entry. */
    val style: SkinStyle? = null,
    val layout: PadLayout = PadLayout(),
    val layoutChanges: Int = 0,
    val onEditLayout: () -> Unit = {},
    val onResetLayout: () -> Unit = {},
)

/** State and actions of the Settings page. */
class SettingsPanel(
    val theme: AppTheme,
    val onTheme: (AppTheme) -> Unit,
    val language: String,
    val onLanguage: (String) -> Unit,
    val shizuku: ShizukuScreen.Availability,
    val onShizukuPermission: () -> Unit,
)

@Composable
fun HomeScreen(
    status: String,
    statusKind: StatusKind,
    initialTarget: String,
    onScan: () -> Unit,
    found: List<io.github.pedropasinn.mobilepad.net.FoundHost>,
    searching: Boolean,
    onSearch: () -> Unit,
    onFound: (io.github.pedropasinn.mobilepad.net.FoundHost) -> Unit,
    onUsb: () -> Unit,
    onConnect: (String) -> Unit,
    onPlay: () -> Unit,
    link: Link = Link.NONE,
    bluetooth: BluetoothPanel,
    controller: ControllerPanel,
    settings: SettingsPanel,
    sectionIndex: Int = 0,
    onSection: (Int) -> Unit = {},
    motion: (@Composable (wide: Boolean) -> Unit)? = null,
    amiibo: (@Composable ColumnScope.() -> Unit)? = null,
    amiiboSummary: String = "",
) {
    // Held by the activity: it survives the rebuild that follows a language change.
    val section = Section.entries.getOrElse(sectionIndex) { Section.CONNECT }
    BackHandler(enabled = section != Section.CONNECT) { onSection(Section.CONNECT.ordinal) }
    val sections = Section.entries.filter { it != Section.MOTION || motion != null }
    // Landscape phones and tablets: navigation on the side, list beside detail.
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    AppBackground {
        val page: @Composable (Modifier) -> Unit = { modifier ->
            AnimatedContent(
                targetState = section,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(90)) },
                label = "section",
                modifier = modifier,
            ) { shown ->
                Box(Modifier.fillMaxSize().padding(horizontal = if (wide) 0.dp else 14.dp)) {
                    when (shown) {
                        Section.CONNECT -> ConnectPage(
                            wide, initialTarget, onScan, found, searching, onSearch, onFound, onUsb, onConnect, bluetooth,
                        )
                        Section.CONTROLLER -> ControllerPage(wide, controller)
                        Section.MOTION -> motion?.invoke(wide)
                        Section.EXTRAS -> ExtrasPage(wide, amiibo, amiiboSummary)
                        Section.SETTINGS -> SettingsPage(wide, settings)
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)
                .padding(horizontal = if (wide) 14.dp else 0.dp, vertical = if (wide) 6.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(if (wide) 8.dp else 0.dp),
        ) {
            TopBar(status, statusKind, link, wide, onPlay)
            page(Modifier.weight(1f).fillMaxWidth())
            BottomDock(sections, section, wide, onPlay) { onSection(it.ordinal) }
        }
        // Portrait: the dock takes the width, so the button floats just above its right end.
        if (!wide) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(end = 16.dp, bottom = 86.dp), contentAlignment = Alignment.BottomEnd) {
                ControllerButton(onPlay)
            }
        }
        controller.preview?.let { SkinPreviewDialog(it, controller.onPreviewDone) }
    }
}

/** How the phone is linked, for the status icon. */
enum class Link { NONE, WIFI, USB, BLUETOOTH }

/** The name on the left; connection, clock and battery together on the right. */
@Composable
private fun TopBar(status: String, statusKind: StatusKind, link: Link, wide: Boolean, onPlay: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (wide) 4.dp else 16.dp, vertical = if (wide) 0.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Mobilepad", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = Rounded)
        Spacer(Modifier.weight(1f))
        StatusCluster(status, statusKind, link, onPlay)
    }
}

/**
 * One pill: an icon for the connection (coloured by its state), the time and the battery. Text
 * only while connecting or after an error.
 */
@Composable
private fun StatusCluster(status: String, kind: StatusKind, link: Link, onPlay: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    var now by remember { mutableStateOf(Date()) }
    var battery by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        while (true) {
            now = Date()
            battery = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            delay(20_000)
        }
    }
    val time = remember(now) { DateFormat.getTimeInstance(DateFormat.SHORT).format(now) }
    val connected = kind == StatusKind.CONNECTED
    val icon = when {
        kind == StatusKind.ERROR -> Icons.Rounded.ErrorOutline
        kind == StatusKind.BUSY -> Icons.Rounded.Sync
        !connected -> Icons.Rounded.LinkOff
        link == Link.USB -> Icons.Rounded.Usb
        link == Link.BLUETOOTH -> Icons.Rounded.Bluetooth
        else -> Icons.Rounded.Wifi
    }
    Row(
        Modifier
            .glass(PillShape)
            .padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 5.dp)
            .semantics(mergeDescendants = true) { contentDescription = status },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(statusColor(kind).copy(alpha = if (connected) 0.22f else 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (kind == StatusKind.IDLE) p.muted else statusColor(kind), modifier = Modifier.size(17.dp)) }
        if (kind == StatusKind.BUSY || kind == StatusKind.ERROR) {
            Text(status, color = p.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Rounded,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
        }
        Box(Modifier.width(1.dp).height(14.dp).background(p.outline))
        Text(time, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded)
        if (battery >= 0) {
            val color = if (battery <= 20) Danger else Success
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("$battery%", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Rounded)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(22.dp).height(11.dp).clip(RoundedCornerShape(3.dp)).border(1.5.dp, Muted, RoundedCornerShape(3.dp)).padding(2.dp)) {
                        Box(Modifier.fillMaxWidth(battery.coerceIn(5, 100) / 100f).fillMaxSize().clip(RoundedCornerShape(1.dp)).background(color))
                    }
                    Box(Modifier.width(2.dp).height(5.dp).background(Muted))
                }
            }
        }
    }
}

/** Opens the controller: a round button with just the controller icon. */
@Composable
private fun ControllerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(54.dp)
            .shadow(10.dp, CircleShape, ambientColor = Accent, spotColor = Accent)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFA77BFF), Color(0xFF6A2BE8))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.4f), Color.Transparent)), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = str(R.string.open_controller) },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.SportsEsports, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
}

/** Dock at the bottom, centred: icons, with the name of the page in view beside its icon. */
@Composable
private fun BottomDock(sections: List<Section>, current: Section, wide: Boolean, onPlay: () -> Unit, onSection: (Section) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(bottom = if (wide) 0.dp else 10.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.glass(RoundedCornerShape(26.dp)).padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(if (wide) 6.dp else 2.dp),
        ) {
            sections.forEach { s -> NavItem(s, current == s, Modifier, compact = true, roomy = wide) { onSection(s) } }
        }
        // Landscape: in the bottom right corner, on the dock's line.
        if (wide) ControllerButton(onPlay, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun NavItem(section: Section, selected: Boolean, modifier: Modifier, compact: Boolean = false, roomy: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    val bg by animateColorAsState(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent, label = "nav")
    if (compact) {
        // Landscape dock: icons, and the name of the page in view beside its icon.
        Row(
            modifier.clip(PillShape).background(bg).clickable(onClick = onClick)
                .animateContentSize().padding(horizontal = if (roomy) 12.dp else 8.dp, vertical = 6.dp)
                .semantics { contentDescription = str(section.label) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconTile(section.icon, if (selected) section.hue else Hue.SLATE, if (roomy) 30.dp else 28.dp)
            if (selected) Text(str(section.label), color = p.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                fontFamily = Rounded, maxLines = 1, modifier = Modifier.padding(end = 4.dp))
        }
        return
    }
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(bg).clickable(onClick = onClick).padding(vertical = if (compact) 3.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.then(if (selected) Modifier else Modifier.background(Color.Transparent))) {
            IconTile(section.icon, if (selected) section.hue else Hue.SLATE, 28.dp)
        }
        Text(
            str(section.label), color = if (selected) p.text else p.muted, fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, fontFamily = Rounded,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------- Connect

@Composable
private fun ConnectPage(
    wide: Boolean,
    initialTarget: String,
    onScan: () -> Unit,
    found: List<io.github.pedropasinn.mobilepad.net.FoundHost>,
    searching: Boolean,
    onSearch: () -> Unit,
    onFound: (io.github.pedropasinn.mobilepad.net.FoundHost) -> Unit,
    onUsb: () -> Unit,
    onConnect: (String) -> Unit,
    bluetooth: BluetoothPanel,
) {
    var selected by rememberSaveable { mutableStateOf<String?>(if (bluetooth.active) "bt" else "wifi") }
    MasterDetail(
        items = listOf(
            PaneItem("wifi", Icons.Rounded.Wifi, Hue.TEAL, "Wi‑Fi", str(R.string.wifi_caption), badges = listOf(Icons.Rounded.Computer, SwitchConsoleIcon)) {
                WifiDetail(initialTarget, onScan, found, searching, onSearch, onFound, onConnect)
            },
            PaneItem("usb", Icons.Rounded.Usb, Hue.ORANGE, "USB", str(R.string.usb_caption), badges = listOf(Icons.Rounded.Computer, Icons.Rounded.Bolt)) {
                Note(str(R.string.usb_subtitle))
                Button(onClick = onUsb, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(str(R.string.usb_connect)) }
                Note(str(R.string.usb_note))
            },
            PaneItem(
                "bt", Icons.Rounded.Bluetooth, Hue.BLUE, "Bluetooth", bluetooth.status.ifEmpty { str(R.string.bt_caption) },
                // While active, the state says more than the icons.
                badges = if (bluetooth.status.isEmpty()) listOf(Icons.Rounded.Computer, Icons.Rounded.Android, SwitchConsoleIcon) else emptyList(),
            ) {
                BluetoothDetail(bluetooth)
            },
        ),
        selected = selected,
        onSelect = { selected = it },
        wide = wide,
        listWidth = 210.dp,
    )
}

@Composable
private fun WifiDetail(
    initialTarget: String,
    onScan: () -> Unit,
    found: List<io.github.pedropasinn.mobilepad.net.FoundHost>,
    searching: Boolean,
    onSearch: () -> Unit,
    onFound: (io.github.pedropasinn.mobilepad.net.FoundHost) -> Unit,
    onConnect: (String) -> Unit,
) {
    var target by remember { mutableStateOf(initialTarget) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onScan, modifier = Modifier.weight(1f).height(46.dp)) {
            Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(str(R.string.scan_qr), maxLines = 1)
        }
        OutlinedButton(onClick = onSearch, enabled = !searching, modifier = Modifier.weight(1f).height(46.dp)) {
            Icon(Icons.Rounded.Search, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
            Text(if (searching) str(R.string.searching) else str(R.string.search), maxLines = 1)
        }
    }
    found.forEach { host ->
        val switch = host.name.contains("Switch", true)
        ListRow(onClick = { onFound(host) }) {
            IconTile(if (switch) Icons.Rounded.SportsEsports else Icons.Rounded.Computer, if (switch) Hue.ROSE else Hue.BLUE, 34.dp)
            Column(Modifier.weight(1f)) {
                Text(host.name, color = Ink, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(host.host, color = Muted, fontSize = 12.sp, fontFamily = Rounded)
            }
            Chevron()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            placeholder = { Text(str(R.string.ip_port)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onConnect(target) },
            enabled = target.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = LocalPalette.current.surfaceHigh, contentColor = Ink),
            modifier = Modifier.height(52.dp),
        ) { Text(str(R.string.nav_connect)) }
    }
    Note(str(R.string.wifi_subtitle))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BluetoothDetail(bluetooth: BluetoothPanel) {
    Segmented(
        listOf(GenericGamepadProfile.ID to str(R.string.bt_generic), SwitchProProfile.ID to "Nintendo Switch"),
        bluetooth.profileId, bluetooth.onProfile, Modifier.fillMaxWidth(),
    )
    if (!bluetooth.active) {
        Button(onClick = bluetooth.onStart, modifier = Modifier.fillMaxWidth().height(46.dp)) { Text(str(R.string.bt_enable)) }
    } else {
        bluetooth.pairedHosts.forEach { (address, name) ->
            ListRow(onClick = { bluetooth.onConnect(address) }) {
                IconTile(Icons.Rounded.Computer, Hue.BLUE, 34.dp)
                Text(name, color = Ink, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Chevron()
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = bluetooth.onVisible) { Text(str(R.string.bt_visible)) }
            TextButton(onClick = bluetooth.onDisable) { Text(str(R.string.bt_disable), color = Danger) }
        }
    }
    Note(str(R.string.bt_subtitle))
}

// ---------------------------------------------------------------- Controller

@Composable
private fun ControllerPage(wide: Boolean, c: ControllerPanel) {
    var selected by rememberSaveable { mutableStateOf<String?>("skin") }
    MasterDetail(
        items = listOf(
            PaneItem("skin", Icons.Rounded.Style, Hue.VIOLET, str(R.string.skin), c.currentName) { SkinGallery(c, large = !wide) },
            PaneItem("layout", Icons.Rounded.OpenWith, Hue.ORANGE, str(R.string.layout_title),
                if (c.layoutChanges == 0) str(R.string.remap_none) else str(R.string.layout_count, c.layoutChanges)) {
                LayoutPane(c)
            },
            PaneItem("macros", Icons.Rounded.AutoAwesome, Hue.ROSE, str(R.string.nav_macros), c.macroSummary, onOpen = c.onMacros),
            PaneItem("orientation", Icons.Rounded.ScreenRotation, Hue.GREEN, str(R.string.controller_orientation), orientationName(c.orientation)) {
                OptionList(
                    ScreenOrientation.entries.map { it to orientationName(it) },
                    c.orientation, c.onOrientation,
                )
                Note(str(R.string.controller_orientation_note))
            },
        ),
        selected = selected,
        onSelect = { selected = it },
        wide = wide,
    )
}

@Composable
private fun orientationName(o: ScreenOrientation) = when (o) {
    ScreenOrientation.LANDSCAPE -> str(R.string.orient_landscape)
    ScreenOrientation.REVERSE -> str(R.string.orient_reverse)
    ScreenOrientation.SYSTEM -> str(R.string.orient_system)
    ScreenOrientation.PORTRAIT -> str(R.string.orient_portrait)
}

/**
 * Where each part sits, its size and opacity: edited on the controller itself. Below, the rarely
 * needed swap of what the buttons send.
 */
@Composable
private fun LayoutPane(c: ControllerPanel) {
    c.style?.let { ControllerPreview(it, Modifier.fillMaxWidth().widthIn(max = 520.dp), layout = c.layout, onClick = c.onEditLayout) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = c.onEditLayout) {
            Icon(Icons.Rounded.OpenWith, null)
            Text(" " + str(R.string.layout_edit))
        }
        if (c.layoutChanges > 0) TextButton(onClick = c.onResetLayout) { Text(str(R.string.layout_reset_all)) }
    }
    Note(str(R.string.layout_note))
    SectionLabel(str(R.string.remap_functions))
    RemapEditor(c.remap, c.onRemap)
}

/** The skin in use in large, then every skin as a miniature of the real controller. */
@Composable
private fun SkinGallery(c: ControllerPanel, large: Boolean) {
    val current = c.skins.firstOrNull { it.dir.name == c.selected }
    val currentStyle = if (current != null) rememberDeltaStyle(current) else builtInStyle(c.builtIn, c.currentName)
    // Portrait has room for the skin in use in large; in landscape the cards are the previews.
    if (large) {
        if (currentStyle != null) ControllerPreview(currentStyle, Modifier.fillMaxWidth().widthIn(max = 520.dp))
        else Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp)).background(LocalPalette.current.surfaceHigh))
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(listOf(SkinId.VIOLET, SkinId.ROSE, SkinId.GRAPHITE)) { id ->
            val name = builtInName(id)
            SkinCard(builtInStyle(id, name), name, str(R.string.skin_builtin), c.selected == null && c.builtIn == id, onClick = {
                c.onBuiltIn(id); c.onSelect(null)
            })
        }
        items(c.skins, key = { it.dir.name }) { s ->
            SkinCard(rememberDeltaStyle(s), s.name, s.console, c.selected == s.dir.name, onClick = { c.onSelect(s.dir.name) }, onDelete = { c.onDelete(s) })
        }
        item { ImportCard(c.onImport) }
    }
    Note(str(R.string.skin_import_note))
    val uri = LocalUriHandler.current
    TextButton(onClick = { uri.openUri("https://deltastyles.com") }) { Text("deltastyles.com ↗", maxLines = 1) }
}

@Composable
private fun builtInName(id: SkinId) = when (id) {
    SkinId.VIOLET -> str(R.string.skin_violet)
    SkinId.ROSE -> str(R.string.skin_rose)
    SkinId.GRAPHITE -> str(R.string.skin_graphite)
}

@Composable
private fun SkinCard(style: SkinStyle?, name: String, caption: String, selected: Boolean, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Column(
        Modifier.width(156.dp).glass(RoundedCornerShape(18.dp), selected).clickable(onClick = onClick).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            if (style != null) ControllerPreview(style, Modifier.fillMaxWidth(), onClick = onClick)
            else Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp)).background(p.surfaceHigh))
            if (onDelete != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, str(R.string.amiibo_remove), tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(name, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, color = p.muted, fontSize = 11.sp, fontFamily = Rounded, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ImportCard(onImport: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.width(156.dp).height(118.dp).glass(RoundedCornerShape(18.dp)).clickable(onClick = onImport).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, tint = Color.White)
        }
        Text(str(R.string.skin_import), color = p.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, textAlign = TextAlign.Center)
    }
}

/** Shown right after an import: the controller as it will look, before putting it to use. */
@Composable
private fun SkinPreviewDialog(skin: DeltaSkin, onDone: (Boolean) -> Unit) {
    val style = rememberDeltaStyle(skin)
    val p = LocalPalette.current
    Dialog(onDismissRequest = { onDone(false) }) {
        Column(
            Modifier.widthIn(max = 600.dp).clip(RoundedCornerShape(28.dp)).background(p.background).glass(RoundedCornerShape(28.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(str(R.string.skin_preview_title), color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Rounded)
            Text(skin.name, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded)
            if (style != null) ControllerPreview(style, Modifier.fillMaxWidth())
            else Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(14.dp)).background(p.surfaceHigh))
            Note(str(R.string.skin_preview_note))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                TextButton(onClick = { onDone(false) }) { Text(str(R.string.not_now)) }
                Button(onClick = { onDone(true) }) { Text(str(R.string.skin_use)) }
            }
        }
    }
}

// ---------------------------------------------------------------- Extras

@Composable
private fun ExtrasPage(wide: Boolean, amiibo: (@Composable ColumnScope.() -> Unit)?, amiiboSummary: String) {
    var selected by rememberSaveable { mutableStateOf<String?>("amiibo") }
    val items = buildList {
        if (amiibo != null) add(PaneItem("amiibo", Icons.Rounded.Nfc, Hue.GOLD, str(R.string.amiibo_title), amiiboSummary, detail = amiibo))
    }
    MasterDetail(items, selected, { selected = it }, wide)
}

// ---------------------------------------------------------------- Settings

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPage(wide: Boolean, s: SettingsPanel) {
    var selected by rememberSaveable { mutableStateOf<String?>(if (wide) "appearance" else null) }
    val languageName = io.github.pedropasinn.mobilepad.MobilepadApp.LANGUAGES.firstOrNull { it.first == s.language }?.second ?: str(R.string.language_system)
    val uri = LocalUriHandler.current
    MasterDetail(
        items = listOf(
            PaneItem("appearance", Icons.Rounded.Palette, Hue.ROSE, str(R.string.appearance),
                if (s.theme == AppTheme.OLED) str(R.string.theme_oled) else str(R.string.theme_dark)) {
                Segmented(
                    listOf(AppTheme.DARK to str(R.string.theme_dark), AppTheme.OLED to str(R.string.theme_oled)),
                    s.theme, s.onTheme, Modifier.fillMaxWidth(),
                )
                Note(str(R.string.appearance_sub) + " · " + str(R.string.theme_oled_note))
            },
            PaneItem("language", Icons.Rounded.Language, Hue.TEAL, str(R.string.language), languageName) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = s.language.isEmpty(), onClick = { s.onLanguage("") }, label = { Text(str(R.string.language_system)) })
                    io.github.pedropasinn.mobilepad.MobilepadApp.LANGUAGES.forEach { (tag, name) ->
                        FilterChip(selected = s.language == tag, onClick = { s.onLanguage(tag) }, label = { Text(name) })
                    }
                }
                Note(str(R.string.language_sub))
            },
            PaneItem("shizuku", Icons.Rounded.AdminPanelSettings, Hue.BLUE, "Shizuku", when (s.shizuku) {
                ShizukuScreen.Availability.READY -> str(R.string.shizuku_ready)
                ShizukuScreen.Availability.NO_PERMISSION -> str(R.string.shizuku_perm)
                ShizukuScreen.Availability.NOT_RUNNING -> str(R.string.shizuku_off)
            }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val (dot, text) = when (s.shizuku) {
                        ShizukuScreen.Availability.READY -> Success to str(R.string.shizuku_ready)
                        ShizukuScreen.Availability.NO_PERMISSION -> Warning to str(R.string.shizuku_perm)
                        ShizukuScreen.Availability.NOT_RUNNING -> Faint to str(R.string.shizuku_off)
                    }
                    Dot(dot)
                    Text(text, color = Muted, fontSize = 13.sp, fontFamily = Rounded, modifier = Modifier.weight(1f, fill = false))
                    if (s.shizuku == ShizukuScreen.Availability.NO_PERMISSION) {
                        TextButton(onClick = s.onShizukuPermission) { Text(str(R.string.grant)) }
                    }
                }
                Note(str(R.string.shizuku_sub))
            },
            PaneItem("about", Icons.Rounded.Info, Hue.SLATE, str(R.string.about), "Mobilepad ${BuildConfig.VERSION_NAME}") {
                Text(str(R.string.about_sub, BuildConfig.VERSION_NAME), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Rounded)
                Note(str(R.string.about_note))
                OutlinedButton(onClick = { uri.openUri("https://github.com/pedropasinn/Mobilepad") }) { Text(str(R.string.about_source)) }
            },
        ),
        selected = selected,
        onSelect = { selected = it },
        wide = wide,
    )
}

/** State and actions of the Bluetooth section. [pairedHosts]: address to name. */
class BluetoothPanel(
    val active: Boolean,
    val status: String,
    val pairedHosts: List<Pair<String, String>>,
    val onStart: () -> Unit,
    val onVisible: () -> Unit,
    val onDisable: () -> Unit,
    val profileId: String,
    val onProfile: (String) -> Unit,
    val onConnect: (String) -> Unit,
)
