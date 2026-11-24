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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import io.github.pedropasinn.mobilepad.BuildConfig
import io.github.pedropasinn.mobilepad.R
import io.github.pedropasinn.mobilepad.ScreenOrientation
import io.github.pedropasinn.mobilepad.bt.GenericGamepadProfile
import io.github.pedropasinn.mobilepad.bt.SwitchProProfile
import io.github.pedropasinn.mobilepad.screen.SaverMode
import io.github.pedropasinn.mobilepad.screen.ShizukuScreen
import io.github.pedropasinn.mobilepad.str
import java.text.DateFormat
import java.util.Date

enum class StatusKind { IDLE, BUSY, CONNECTED, ERROR }

/** Sections of the home screen; the controller and the macro editor open as their own screens. */
private enum class Section(val icon: ImageVector, val hue: Hue, val label: Int) {
    CONNECT(Icons.Rounded.Link, Hue.VIOLET, R.string.nav_connect),
    MOTION(Icons.Rounded.Explore, Hue.TEAL, R.string.nav_motion),
    AMIIBO(Icons.Rounded.Nfc, Hue.GOLD, R.string.nav_amiibo),
    SETTINGS(Icons.Rounded.Settings, Hue.BLUE, R.string.nav_settings_short),
}

/** Ways to reach the other device, shown as tiles on the Connect section. */
private enum class Method { WIFI, USB, BLUETOOTH }

@Composable
fun ConnectScreen(
    status: String,
    statusKind: StatusKind,
    initialTarget: String,
    saverMode: SaverMode,
    shizuku: ShizukuScreen.Availability,
    onScan: () -> Unit,
    found: List<io.github.pedropasinn.mobilepad.net.FoundHost>,
    searching: Boolean,
    onSearch: () -> Unit,
    onFound: (io.github.pedropasinn.mobilepad.net.FoundHost) -> Unit,
    onUsb: () -> Unit,
    onConnect: (String) -> Unit,
    onTryLayout: () -> Unit,
    onMacros: () -> Unit,
    onSaverMode: (SaverMode) -> Unit,
    theme: AppTheme = AppTheme.DARK,
    onTheme: (AppTheme) -> Unit = {},
    skin: SkinId = SkinId.VIOLET,
    onSkin: (SkinId) -> Unit = {},
    skins: SkinPanel? = null,
    language: String = "",
    onLanguage: (String) -> Unit = {},
    sectionIndex: Int = 0,
    onSection: (Int) -> Unit = {},
    orientation: ScreenOrientation = ScreenOrientation.LANDSCAPE,
    onOrientation: (ScreenOrientation) -> Unit = {},
    onShizukuPermission: () -> Unit,
    bluetooth: BluetoothPanel,
    motion: (@Composable () -> Unit)? = null,
    amiibo: (@Composable () -> Unit)? = null,
) {
    // Held by the activity: it survives the rebuild that follows a language change.
    val section = Section.entries.getOrElse(sectionIndex) { Section.CONNECT }
    BackHandler(enabled = section != Section.CONNECT) { onSection(Section.CONNECT.ordinal) }
    val sections = Section.entries.filter {
        (it != Section.MOTION || motion != null) && (it != Section.AMIIBO || amiibo != null)
    }
    val config = LocalConfiguration.current
    // Phones in portrait: one column; landscape and tablets: side by side where it helps.
    val narrow = config.screenWidthDp < 600
    // Short landscape phones: compact top bar and dock so the content keeps most of the height.
    val short = config.screenHeightDp < 480

    AppBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            TopBar(status, statusKind, short)
            AnimatedContent(
                targetState = section,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(90)) },
                label = "section",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { shown ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = if (narrow) 16.dp else 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (shown) {
                        Section.CONNECT -> ConnectSection(
                            narrow, initialTarget, onScan, found, searching, onSearch, onFound, onUsb, onConnect, bluetooth,
                        )
                        Section.MOTION -> motion?.invoke()
                        Section.AMIIBO -> amiibo?.invoke()
                        Section.SETTINGS -> SettingsSection(
                            narrow, theme, onTheme, skin, onSkin, skins, language, onLanguage, orientation, onOrientation,
                            saverMode, onSaverMode, shizuku, onShizukuPermission,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            Dock(sections, section, short, onSection = { onSection(it.ordinal) }, onMacros = onMacros, onPlay = onTryLayout)
        }
    }
}

/** Name and connection on the left, clock and battery on the right, as on a console home. */
@Composable
private fun TopBar(status: String, statusKind: StatusKind, short: Boolean) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (short) 8.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Mobilepad", color = p.text, fontSize = if (short) 20.sp else 24.sp, fontWeight = FontWeight.Black, fontFamily = Rounded)
        StatusPill(status, statusKind, Modifier.widthIn(max = 320.dp))
        Spacer(Modifier.weight(1f))
        ClockPill()
    }
}

/** Time and battery level, refreshed every 20 s. */
@Composable
private fun ClockPill() {
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
    Row(
        Modifier.glass(PillShape).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(time, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded)
        if (battery >= 0) {
            Box(Modifier.width(1.dp).height(12.dp).background(p.outline))
            Text("$battery%", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Rounded)
            BatteryGlyph(battery)
        }
    }
}

@Composable
private fun BatteryGlyph(level: Int) {
    val color = if (level <= 20) Danger else Success
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(22.dp).height(11.dp).clip(RoundedCornerShape(3.dp)).border(1.5.dp, Muted, RoundedCornerShape(3.dp)).padding(2.dp)) {
            Box(Modifier.fillMaxWidth(level.coerceIn(5, 100) / 100f).fillMaxSize().clip(RoundedCornerShape(1.dp)).background(color))
        }
        Box(Modifier.width(2.dp).height(5.dp).background(Muted))
    }
}

/** Floating dock: the sections, the editors that open their own screen, and the controller. */
@Composable
private fun Dock(
    sections: List<Section>,
    current: Section,
    short: Boolean,
    onSection: (Section) -> Unit,
    onMacros: () -> Unit,
    onPlay: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (short) 6.dp else 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.glass(PillShape).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sections.forEach { s ->
                DockItem(s.icon, s.hue, str(s.label), current == s, short) { onSection(s) }
            }
            DockItem(Icons.Rounded.AutoAwesome, Hue.ROSE, str(R.string.nav_macros_short), false, short, onMacros)
        }
        Spacer(Modifier.width(10.dp))
        // The main action stands apart from the navigation.
        Row(
            Modifier
                .clip(PillShape)
                .background(Brush.horizontalGradient(listOf(Color(0xFF9B6BFF), Color(0xFF6A2BE8))))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)), PillShape)
                .clickable(onClick = onPlay)
                .padding(horizontal = 18.dp, vertical = if (short) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.SportsEsports, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Text(str(R.string.nav_controller), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded)
        }
    }
}

/** Icon in its colour; the selected one shows its name and an underline. */
@Composable
private fun DockItem(icon: ImageVector, hue: Hue, label: String, selected: Boolean, short: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val bg by animateColorAsState(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent, label = "dock")
    Row(
        Modifier.clip(PillShape).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = if (short) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconTile(icon, if (selected) hue else hue, if (short) 30.dp else 36.dp)
        if (selected) Text(label, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectSection(
    narrow: Boolean,
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
    var method by rememberSaveable { mutableStateOf(if (bluetooth.active) Method.BLUETOOTH else Method.WIFI) }
    // Stacked tiles (portrait) or one row each beside the panel (landscape).
    val tiles: @Composable (Modifier) -> Unit = { tile ->
        Tile(Icons.Rounded.Wifi, Hue.TEAL, "Wi‑Fi", str(R.string.wifi_caption), method == Method.WIFI, { method = Method.WIFI }, tile, row = !narrow)
        Tile(Icons.Rounded.Usb, Hue.ORANGE, "USB", str(R.string.usb_caption), method == Method.USB, { method = Method.USB }, tile, row = !narrow)
        Tile(Icons.Rounded.Bluetooth, Hue.BLUE, "Bluetooth", str(R.string.bt_caption), method == Method.BLUETOOTH, { method = Method.BLUETOOTH }, tile, row = !narrow)
    }
    val panel: @Composable (Modifier) -> Unit = { modifier ->
        AnimatedContent(method, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(80)) }, label = "method", modifier = modifier) { m ->
            when (m) {
                Method.WIFI -> WifiPanel(initialTarget, onScan, found, searching, onSearch, onFound, onConnect)
                Method.USB -> UsbPanel(onUsb)
                Method.BLUETOOTH -> BluetoothPanelView(bluetooth)
            }
        }
    }
    if (narrow) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { tiles(Modifier.weight(1f)) }
        panel(Modifier.fillMaxWidth())
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(250.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { tiles(Modifier.fillMaxWidth()) }
            panel(Modifier.weight(1f))
        }
    }
}

@Composable
private fun WifiPanel(
    initialTarget: String,
    onScan: () -> Unit,
    found: List<io.github.pedropasinn.mobilepad.net.FoundHost>,
    searching: Boolean,
    onSearch: () -> Unit,
    onFound: (io.github.pedropasinn.mobilepad.net.FoundHost) -> Unit,
    onConnect: (String) -> Unit,
) {
    var target by remember { mutableStateOf(initialTarget) }
    Panel(str(R.string.wifi_subtitle)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onScan, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(str(R.string.scan_qr), maxLines = 1)
            }
            OutlinedButton(onClick = onSearch, enabled = !searching, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Rounded.Search, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text(if (searching) str(R.string.searching) else str(R.string.search), maxLines = 1)
            }
        }
        if (found.isNotEmpty()) {
            SectionLabel(str(R.string.found_on_network))
            found.forEach { host ->
                val switch = host.name.contains("Switch", true)
                ListRow(onClick = { onFound(host) }) {
                    IconTile(if (switch) Icons.Rounded.SportsEsports else Icons.Rounded.Computer, if (switch) Hue.ROSE else Hue.BLUE, 34.dp)
                    Column(Modifier.weight(1f)) {
                        Text(host.name, color = Ink, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(host.host, color = Muted, fontSize = 12.sp, fontFamily = Rounded)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Faint)
                }
            }
        }
        SectionLabel(str(R.string.manual_address))
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
    }
}

@Composable
private fun UsbPanel(onUsb: () -> Unit) {
    Panel(str(R.string.usb_subtitle)) {
        Button(onClick = onUsb, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(str(R.string.usb_connect)) }
        Text(str(R.string.usb_note), color = Faint, fontSize = 12.sp, fontFamily = Rounded)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BluetoothPanelView(bluetooth: BluetoothPanel) {
    Panel(str(R.string.bt_subtitle)) {
        Segmented(
            listOf(GenericGamepadProfile.ID to str(R.string.bt_generic), SwitchProProfile.ID to "Nintendo Switch"),
            bluetooth.profileId, bluetooth.onProfile, Modifier.fillMaxWidth(),
        )
        if (!bluetooth.active) {
            Button(onClick = bluetooth.onStart, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(str(R.string.bt_enable)) }
        } else {
            bluetooth.pairedHosts.forEach { (address, name) ->
                ListRow(onClick = { bluetooth.onConnect(address) }) {
                    IconTile(Icons.Rounded.Computer, Hue.BLUE, 34.dp)
                    Text(name, color = Ink, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Faint)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = bluetooth.onVisible) { Text(str(R.string.bt_visible)) }
                TextButton(onClick = bluetooth.onDisable) { Text(str(R.string.bt_disable), color = Danger) }
            }
        }
        if (bluetooth.status.isNotEmpty()) Text(bluetooth.status, color = Muted, fontSize = 12.sp, fontFamily = Rounded)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSection(
    narrow: Boolean,
    theme: AppTheme,
    onTheme: (AppTheme) -> Unit,
    skin: SkinId,
    onSkin: (SkinId) -> Unit,
    skins: SkinPanel?,
    language: String,
    onLanguage: (String) -> Unit,
    orientation: ScreenOrientation,
    onOrientation: (ScreenOrientation) -> Unit,
    saverMode: SaverMode,
    onSaverMode: (SaverMode) -> Unit,
    shizuku: ShizukuScreen.Availability,
    onShizukuPermission: () -> Unit,
) {
    // Two panels per row on wide screens (tablets, handhelds), one under the other on phones.
    FlowRow(
        // No shared height: intrinsic measuring of wrapping chips can clip the last row.
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        maxItemsInEachRow = if (narrow) 1 else 2,
    ) {
        val card = Modifier.weight(1f)
        Card(Icons.Rounded.SportsEsports, Hue.VIOLET, str(R.string.skin), str(R.string.skin_sub), card) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((id, name) in listOf(
                    SkinId.VIOLET to str(R.string.skin_violet),
                    SkinId.ROSE to str(R.string.skin_rose),
                    SkinId.GRAPHITE to str(R.string.skin_graphite),
                )) SkinSwatch(id, name, skins?.selected == null && skin == id, Modifier.weight(1f)) { onSkin(id); skins?.onSelect?.invoke(null) }
            }
            if (skins != null) ImportedSkins(skins)
        }
        Card(Icons.Rounded.Palette, Hue.ROSE, str(R.string.appearance), str(R.string.appearance_sub), card) {
            Segmented(
                listOf(AppTheme.DARK to str(R.string.theme_dark), AppTheme.OLED to str(R.string.theme_oled)),
                theme, onTheme, Modifier.fillMaxWidth(),
            )
            Text(str(R.string.theme_oled_note), color = Faint, fontSize = 12.sp, fontFamily = Rounded)
        }
        Card(Icons.Rounded.Language, Hue.TEAL, str(R.string.language), str(R.string.language_sub), card) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = language.isEmpty(), onClick = { onLanguage("") }, label = { Text(str(R.string.language_system)) })
                io.github.pedropasinn.mobilepad.MobilepadApp.LANGUAGES.forEach { (tag, name) ->
                    FilterChip(selected = language == tag, onClick = { onLanguage(tag) }, label = { Text(name) })
                }
            }
        }
        Card(Icons.Rounded.ScreenRotation, Hue.GREEN, str(R.string.orientation), str(R.string.orientation_sub), card) {
            OptionList(
                listOf(
                    ScreenOrientation.LANDSCAPE to str(R.string.orient_landscape),
                    ScreenOrientation.REVERSE to str(R.string.orient_reverse),
                    ScreenOrientation.SYSTEM to str(R.string.orient_system),
                    ScreenOrientation.PORTRAIT to str(R.string.orient_portrait),
                ),
                orientation, onOrientation,
            )
        }
        Card(Icons.Rounded.Bedtime, Hue.BLUE, str(R.string.saver), str(R.string.saver_sub), card) {
            Segmented(
                listOf(SaverMode.BLACK to str(R.string.saver_black), SaverMode.PANEL_OFF to str(R.string.saver_panel)),
                saverMode, onSaverMode, Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val (dot, text) = when (shizuku) {
                    ShizukuScreen.Availability.READY -> Success to str(R.string.shizuku_ready)
                    ShizukuScreen.Availability.NO_PERMISSION -> Warning to str(R.string.shizuku_perm)
                    ShizukuScreen.Availability.NOT_RUNNING -> Faint to str(R.string.shizuku_off)
                }
                Dot(dot)
                Text(text, color = Muted, fontSize = 13.sp, fontFamily = Rounded, modifier = Modifier.weight(1f, fill = false))
                if (shizuku == ShizukuScreen.Availability.NO_PERMISSION) {
                    TextButton(onClick = onShizukuPermission) { Text(str(R.string.grant)) }
                }
            }
            Text(str(R.string.saver_exit), color = Faint, fontSize = 12.sp, fontFamily = Rounded)
        }
        val uri = LocalUriHandler.current
        Card(Icons.Rounded.Info, Hue.SLATE, str(R.string.about), str(R.string.about_sub, BuildConfig.VERSION_NAME), card) {
            Text(str(R.string.about_note), color = Faint, fontSize = 12.sp, fontFamily = Rounded, modifier = Modifier.widthIn(max = 640.dp))
            OutlinedButton(onClick = { uri.openUri("https://github.com/pedropasinn/Mobilepad") }) { Text(str(R.string.about_source)) }
        }
    }
}

/** Miniature of a controller skin: shell gradient with a black button and a D-pad hint. */
@Composable
private fun SkinSwatch(id: SkinId, name: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val s = Skins.getValue(id)
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(s.shellTop, s.shellBottom)))
                .border(
                    if (selected) 2.5.dp else 1.dp,
                    if (selected) AccentBright else Color.White.copy(alpha = 0.15f),
                    RoundedCornerShape(16.dp),
                ),
        ) {
            // Plus sign on the left, two face buttons on the right.
            Box(Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(width = 18.dp, height = 6.dp).clip(RoundedCornerShape(2.dp)).background(s.buttonBottom))
            Box(Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(width = 6.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(s.buttonBottom))
            Row(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.padding(top = 10.dp).size(13.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(s.buttonTop, s.buttonBottom))))
                Box(Modifier.size(13.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(s.buttonTop, s.buttonBottom))))
            }
        }
        Text(name, color = if (selected) Ink else Muted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, maxLines = 1)
    }
}

/** Imported Delta skins: the list, the one in use (null: built-in) and the actions. */
class SkinPanel(
    val skins: List<io.github.pedropasinn.mobilepad.skin.DeltaSkin>,
    val selected: String?,
    val onSelect: (String?) -> Unit,
    val onImport: () -> Unit,
    val onDelete: (io.github.pedropasinn.mobilepad.skin.DeltaSkin) -> Unit,
)

@Composable
private fun ImportedSkins(panel: SkinPanel) {
    val uri = LocalUriHandler.current
    if (panel.skins.isNotEmpty()) {
        SectionLabel(str(R.string.skins_imported))
        panel.skins.forEach { s ->
            val on = panel.selected == s.dir.name
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp), on).clickable { panel.onSelect(s.dir.name) }
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconTile(Icons.Rounded.SportsEsports, Hue.VIOLET, 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(s.name, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rounded, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val modes = listOfNotNull(
                        str(R.string.skin_landscape).takeIf { s.hasLayout(true) },
                        str(R.string.skin_portrait).takeIf { s.hasLayout(false) },
                    ).joinToString(" · ")
                    Text("${s.console} · $modes", color = Muted, fontSize = 12.sp, fontFamily = Rounded, maxLines = 1)
                }
                androidx.compose.material3.IconButton(onClick = { panel.onDelete(s) }) {
                    Icon(Icons.Rounded.Delete, str(R.string.amiibo_remove), tint = Faint)
                }
            }
        }
    }
    Button(onClick = panel.onImport) {
        Icon(Icons.Rounded.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(str(R.string.skin_import))
    }
    Text(str(R.string.skin_import_note), color = Faint, fontSize = 12.sp, fontFamily = Rounded)
    TextButton(onClick = { uri.openUri("https://deltastyles.com") }) { Text("deltastyles.com ↗", maxLines = 1) }
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
