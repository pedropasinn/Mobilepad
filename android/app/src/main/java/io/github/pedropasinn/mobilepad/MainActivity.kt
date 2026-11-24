package io.github.pedropasinn.mobilepad

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.pedropasinn.mobilepad.amiibo.AmiiboController
import io.github.pedropasinn.mobilepad.amiibo.AmiiboLibrary
import io.github.pedropasinn.mobilepad.amiibo.AmiiboMenu
import io.github.pedropasinn.mobilepad.bt.BluetoothGamepad
import io.github.pedropasinn.mobilepad.bt.BluetoothKeepAliveService
import io.github.pedropasinn.mobilepad.bt.GenericGamepadProfile
import io.github.pedropasinn.mobilepad.bt.SwitchProProfile
import io.github.pedropasinn.mobilepad.input.GamepadInput
import io.github.pedropasinn.mobilepad.input.LatencyTest
import io.github.pedropasinn.mobilepad.macro.MacroConfig
import io.github.pedropasinn.mobilepad.macro.MacroEngine
import io.github.pedropasinn.mobilepad.input.PhysicalGamepad
import io.github.pedropasinn.mobilepad.motion.AxisPreset
import io.github.pedropasinn.mobilepad.motion.MotionController
import io.github.pedropasinn.mobilepad.motion.MotionSettings
import io.github.pedropasinn.mobilepad.net.ConnectTarget
import io.github.pedropasinn.mobilepad.net.ConnectionStatus
import io.github.pedropasinn.mobilepad.net.WifiConnection
import io.github.pedropasinn.mobilepad.protocol.Buttons
import io.github.pedropasinn.mobilepad.protocol.Feedback
import io.github.pedropasinn.mobilepad.protocol.Wire
import io.github.pedropasinn.mobilepad.screen.ShizukuScreen
import io.github.pedropasinn.mobilepad.ui.BluetoothPanel
import io.github.pedropasinn.mobilepad.ui.HomeScreen
import io.github.pedropasinn.mobilepad.ui.StatusKind
import io.github.pedropasinn.mobilepad.ui.ControllerScreen
import io.github.pedropasinn.mobilepad.ui.MacroEditorScreen
import io.github.pedropasinn.mobilepad.ui.editor.MacroHomeScreen
import io.github.pedropasinn.mobilepad.protocol.ControllerState

class MainActivity : ComponentActivity() {
    private val input = GamepadInput()
    private val physical = PhysicalGamepad(input)
    private lateinit var prefs: Prefs
    private lateinit var connection: WifiConnection
    private lateinit var shizuku: ShizukuScreen
    private lateinit var bluetooth: BluetoothGamepad
    private lateinit var macros: MacroEngine
    private lateinit var motion: MotionController
    private lateinit var amiiboLibrary: AmiiboLibrary
    private lateinit var amiibo: AmiiboController

    private var showPad by mutableStateOf(false)
    private var rumbleOn by mutableStateOf(true)
    private var orientation by mutableStateOf(ScreenOrientation.LANDSCAPE)
    private var theme by mutableStateOf(io.github.pedropasinn.mobilepad.ui.AppTheme.DARK)
    private var skin by mutableStateOf(io.github.pedropasinn.mobilepad.ui.SkinId.VIOLET)
    private var deltaSkinId by mutableStateOf("")
    private var previewSkinId by mutableStateOf<String?>(null)
    private var remap by mutableStateOf<Map<Int, Int>>(emptyMap())
    private var layoutLand by mutableStateOf(io.github.pedropasinn.mobilepad.ui.PadLayout())
    private var layoutPort by mutableStateOf(io.github.pedropasinn.mobilepad.ui.PadLayout())
    private var editLayout by mutableStateOf(false)
    private var altLand by mutableStateOf(io.github.pedropasinn.mobilepad.ui.PadLayout())
    private var altPort by mutableStateOf(io.github.pedropasinn.mobilepad.ui.PadLayout())
    /** Bumped when the Bluetooth name changes, so the panel reads it again. */
    private var btNameTick by mutableStateOf(0)
    private var profiles by mutableStateOf<List<GameProfile>>(emptyList())
    private var activeProfile by mutableStateOf("")
    private var buttonNames by mutableStateOf(io.github.pedropasinn.mobilepad.ui.ButtonNames.AUTO)
    /** The editor was opened from the Controller page: Done goes back there. */
    private var editFromHome = false
    private val skinLibrary by lazy { io.github.pedropasinn.mobilepad.skin.DeltaSkinLibrary(filesDir) }
    private var language by mutableStateOf("")
    private var homeSection by mutableStateOf(0)
    private var showMacros by mutableStateOf(false)
    private var showJson by mutableStateOf(false)
    private var foundHosts by mutableStateOf(emptyList<io.github.pedropasinn.mobilepad.net.FoundHost>())
    private var searching by mutableStateOf(false)
    private var macroConfig by mutableStateOf(MacroConfig.EMPTY)
    private var shizukuState by mutableStateOf(ShizukuScreen.Availability.NOT_RUNNING)
    private var playerLed by mutableStateOf(0)
    private var switchPlayer by mutableStateOf(0)
    private var bluetoothProfileId by mutableStateOf(GenericGamepadProfile.ID)
    private var switchJobs: List<Job> = emptyList()

    private val skinFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) lifecycleScope.launch {
            val result = skinLibrary.import(contentResolver, uris)
            toast(str(R.string.skin_imported, result.added) +
                if (result.skipped > 0) " · " + str(R.string.skin_skipped, result.skipped) else "")
            // A single new skin opens in a preview, from which it can be put to use.
            if (result.added == 1 && uris.size == 1) skinLibrary.skins.value.maxByOrNull { it.dir.lastModified() }?.let {
                previewSkinId = it.dir.name
            }
        }
    }
    private val amiiboFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) lifecycleScope.launch { reportImport(amiiboLibrary.importFiles(contentResolver, uris)) }
    }
    private val amiiboFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) lifecycleScope.launch { reportImport(amiiboLibrary.importTree(contentResolver, tree)) }
    }

    private fun reportImport(result: AmiiboLibrary.ImportResult) {
        toast(
            str(R.string.amiibo_imported, result.added) +
                if (result.skipped > 0) " · " + str(R.string.amiibo_skipped, result.skipped) else "",
        )
    }

    private val scanner = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let(::connectTo)
    }

    private val bluetoothPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            // Notifications are optional: only the Bluetooth permissions are required.
            if (hasBluetoothPermission()) enableBluetooth()
            else toast(str(R.string.bt_perm_needed))
        }

    /**
     * Switch pairing: the console gives up if the "just works" request is not confirmed
     * within a few seconds, so in Switch mode it is confirmed at once through Shizuku.
     */
    private val pairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Any pairing change: the list of hosts on the Bluetooth page is read again.
            if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) btNameTick++
            if (bluetoothProfileId != SwitchProProfile.ID) return
            val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                ?: return
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
            val starting = intent.action == BluetoothDevice.ACTION_PAIRING_REQUEST || bondState == BluetoothDevice.BOND_BONDING
            val name = runCatching { device.name }.getOrNull().orEmpty()
            if (!(name.contains("Switch", true) || name.contains("Nintendo", true))) return
            if (bondState == BluetoothDevice.BOND_BONDED) {
                // A real controller opens the HID channels itself right after pairing;
                // the console closes the link if nothing arrives.
                android.util.Log.i("MainActivity", "paired with ${device.address}, connecting HID")
                bluetooth.connect(device)
                return
            }
            if (!starting) return
            if (shizukuState != ShizukuScreen.Availability.READY) {
                toast(str(R.string.confirm_pair))
                return
            }
            lifecycleScope.launch {
                // The request may not be ready yet when bonding starts: retry briefly.
                repeat(10) {
                    if (shizuku.confirmPairing(device.address)) {
                        // Android announces BOND_BONDED seconds later, after its own service
                        // discovery; the console drops the link long before. Open the HID
                        // channels right away, as a real controller would.
                        repeat(5) { attempt ->
                            delay(if (attempt == 0) 60L else 250L)
                            if (bluetooth.state.value is BluetoothGamepad.State.Connected) return@launch
                            android.util.Log.i("MainActivity", "confirmed ${device.address}, connecting HID (#$attempt)")
                            bluetooth.connect(device)
                        }
                        return@launch
                    }
                    delay(150)
                }
            }
        }
    }

    private val discoverable =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        connection = WifiConnection(prefs.deviceId)
        shizuku = ShizukuScreen(this)
        bluetooth = (application as MobilepadApp).bluetooth
        useBluetoothProfile(prefs.bluetoothProfile)
        macros = MacroEngine(lifecycleScope)
        macros.openedByTouch = { input.touchHeld() }
        applyMacros(
            runCatching { MacroConfig.parse(prefs.macroJson) }.getOrElse {
                toast(str(R.string.macro_invalid, it.message ?: ""))
                MacroConfig.EMPTY
            },
            save = false,
        )

        motion = MotionController(this, input, MotionSettings.parse(prefs.motionJson), { prefs.motionJson = it.toJson() }, switchAddress())
        // Gyro passthrough: type 17 to the Switch sysmodule, IMU fields of the Bluetooth Pro Controller.
        motion.onSample = { sample ->
            sample?.let {
                // The axis preset only concerns the Switch sysmodule (the Bluetooth Pro Controller
                // and DSU have their own conversions).
                val s = motion.settings.value
                connection.sendMotion(if (s.axes == AxisPreset.CAPPY) it.flippedAccelXY() else it.scaledGyro(s.kartGain))
            }
            bluetooth.sendMotion(sample)
        }

        // Amiibo wheel for the Switch sysmodule: a dynamic radial menu, mappable to any combo.
        amiiboLibrary = AmiiboLibrary(filesDir)
        amiibo = AmiiboController(amiiboLibrary, connection::sendControl)
        macros.registerDynamicRadial(AmiiboMenu.ID, amiibo::menu, amiibo::onPick)
        lifecycleScope.launch { amiiboLibrary.load() }
        lifecycleScope.launch { connection.hostMessages.collect(amiibo::onHostMessage) }
        lifecycleScope.launch { amiibo.messages.collect(::toast) }

        // The screen stays on only on the controller; elsewhere the phone locks as usual.
        rumbleOn = prefs.rumble
        orientation = prefs.orientation
        theme = prefs.theme
        skin = prefs.skin
        deltaSkinId = prefs.deltaSkin
        remap = prefs.remap
        layoutLand = prefs.layoutLandscape
        altLand = prefs.altLandscape
        altPort = prefs.altPortrait
        profiles = prefs.profiles
        activeProfile = prefs.activeProfile
        buttonNames = prefs.buttonNames
        layoutPort = prefs.layoutPortrait
        input.remap = remap
        lifecycleScope.launch { skinLibrary.load() }
        language = prefs.language
        lifecycleScope.launch {
            snapshotFlow { showPad to orientation }.collect { (pad, chosen) ->
                if (pad) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                requestedOrientation = if (pad) chosen.android else ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            }
        }
        hideSystemBars()

        // touch + controller -> macro engine -> PC
        // While editing macros the controller is used for recording combos: nothing reaches the PC.
        lifecycleScope.launch {
            input.state.collect { macros.onInput(if (showMacros) ControllerState.NEUTRAL else it) }
        }
        lifecycleScope.launch {
            macros.output.collect { state ->
                connection.send(state)
                bluetooth.send(state)
                motion.onPadState(state)
            }
        }
        lifecycleScope.launch {
            macros.keyboard.collect { keys ->
                connection.sendKeys(keys)
                bluetooth.sendKeys(keys.modifiers, keys.keys)
            }
        }
        lifecycleScope.launch {
            bluetooth.state.collect { state ->
                if (state is BluetoothGamepad.State.Connected) showPad = true
                if (state is BluetoothGamepad.State.Failed) toast("Bluetooth: ${state.reason}")
            }
        }
        lifecycleScope.launch { connection.feedback.collect(::onFeedback) }
        lifecycleScope.launch {
            connection.status.collect { status ->
                if (status is ConnectionStatus.Connected) wifiLock.acquire() else if (wifiLock.isHeld) wifiLock.release()
                if (status is ConnectionStatus.Connected) showPad = true
                // Stick moves reach the PC as soon as the screen samples them (not once per frame).
                io.github.pedropasinn.mobilepad.ui.TouchDispatch.unbuffered = status is ConnectionStatus.Connected
                amiibo.available = status is ConnectionStatus.Connected &&
                    status.pcOs.startsWith("Switch") && "Mobilepad" in status.pcOs
                if (status is ConnectionStatus.Failed) toast(str(R.string.connection_lost, status.reason))
            }
        }
        ContextCompat.registerReceiver(
            this,
            pairingReceiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        if (prefs.bluetoothEnabled && hasBluetoothPermission()) enableBluetooth()
        if (BuildConfig.DEBUG) applyTestExtras()
        startLatencyTest(intent)

        setContent {
            io.github.pedropasinn.mobilepad.ui.MobilepadTheme(theme) {
                // A new language rebuilds the screens, so every text is read again.
                androidx.compose.runtime.key(language) { Screens() }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Screens() {
        run {
            val status by connection.status.collectAsState()
            val btState by bluetooth.state.collectAsState()
            val statusText = (btState as? BluetoothGamepad.State.Connected)?.let {
                if (bluetoothProfileId == SwitchProProfile.ID) {
                    "Switch: ${it.name}" + if (switchPlayer > 0) " · " + str(R.string.player, switchPlayer) else ""
                } else {
                    "Bluetooth: ${it.name}"
                }
            } ?: describe(status)
            val statusKind = when {
                btState is BluetoothGamepad.State.Connected || status is ConnectionStatus.Connected -> StatusKind.CONNECTED
                status is ConnectionStatus.Failed -> StatusKind.ERROR
                status is ConnectionStatus.Connecting -> StatusKind.BUSY
                else -> StatusKind.IDLE
            }
            val onSwitch = (bluetoothProfileId == SwitchProProfile.ID && btState is BluetoothGamepad.State.Connected) ||
                (status as? ConnectionStatus.Connected)?.pcOs?.startsWith("Switch") == true
            val resolvedNames = when (buttonNames) {
                io.github.pedropasinn.mobilepad.ui.ButtonNames.AUTO ->
                    if (onSwitch) io.github.pedropasinn.mobilepad.ui.ButtonNames.NINTENDO else io.github.pedropasinn.mobilepad.ui.ButtonNames.XBOX
                else -> buttonNames
            }
            androidx.compose.runtime.SideEffect { io.github.pedropasinn.mobilepad.ui.ActiveButtonNames.value = resolvedNames }
            val link = when {
                btState is BluetoothGamepad.State.Connected -> io.github.pedropasinn.mobilepad.ui.Link.BLUETOOTH
                (status as? ConnectionStatus.Connected)?.target == "USB" -> io.github.pedropasinn.mobilepad.ui.Link.USB
                status is ConnectionStatus.Connected -> io.github.pedropasinn.mobilepad.ui.Link.WIFI
                else -> io.github.pedropasinn.mobilepad.ui.Link.NONE
            }
            // While a profile is in use, whatever changes is saved into it.
            val motionNow by motion.settings.collectAsState()
            androidx.compose.runtime.LaunchedEffect(activeProfile, skin, deltaSkinId, layoutLand, layoutPort, altLand, altPort, buttonNames, remap, macroConfig, motionNow) {
                val current = profiles.firstOrNull { it.id == activeProfile } ?: return@LaunchedEffect
                val now = snapshotProfile(current.id, current.name)
                if (now != current) saveProfiles(profiles.map { if (it.id == current.id) now else it })
            }
            val deltaSkins by skinLibrary.skins.collectAsState()
            val activeDelta = deltaSkins.firstOrNull { it.dir.name == deltaSkinId }
            val builtInName = str(skin.nameRes)
            val padStyle = activeDelta?.let { io.github.pedropasinn.mobilepad.ui.rememberDeltaStyle(it) }
                ?: io.github.pedropasinn.mobilepad.ui.builtInStyle(skin, builtInName)
            val amiiboEntries by amiiboLibrary.entries.collectAsState()
            val radial by macros.radial.collectAsState()
            val motionSettings by motion.settings.collectAsState()
            if (showMacros && showJson) {
                MacroEditorScreen(
                    initial = macroConfig.toJson(),
                    onSave = { json ->
                        try {
                            applyMacros(MacroConfig.parse(json), save = true)
                            null
                        } catch (e: IllegalArgumentException) {
                            e.message ?: str(R.string.invalid_config)
                        }
                    },
                    onClose = { showJson = false },
                )
            } else if (showMacros) {
                MacroHomeScreen(
                    config = macroConfig,
                    rawInput = input.state,
                    onConfig = { applyMacros(it, save = true) },
                    onAdvanced = { showJson = true },
                    dynamicRadials = macros.dynamicRadialIds,
                    onClose = { showMacros = false },
                )
            } else if (showPad) {
                ControllerScreen(
                    input = input,
                    status = statusText,
                    radial = radial,
                    style = padStyle,
                    statusKind = statusKind,
                    link = link,
                    nintendoLayout = resolvedNames == io.github.pedropasinn.mobilepad.ui.ButtonNames.NINTENDO,
                    onDisconnect = {
                        connection.disconnect()
                        bluetooth.disconnectHost()
                        input.resetTouch()
                        showPad = false
                    },
                    onRadialTap = macros::tapSlice,
                    onRadialBack = macros::radialBack,
                    steering = motionSettings.steering && motion.hasTilt,
                    onSteering = if (motion.hasTilt) ({ motion.update { it.copy(steering = !it.steering) } }) else null,
                    onRecenter = {
                        // Sensor: the console takes the current position as neutral; steering: the app does.
                        if (motionSettings.gyro) connection.sendControl(Wire.TYPE_MOTION_RECENTER, ByteArray(0))
                        else motion.recenter()
                    },
                    motionSensor = motionSettings.gyro,
                    onMotionSensor = if (motion.hasGyro) ({
                        motion.update { it.copy(gyro = !it.gyro) }
                        if (motion.settings.value.gyro && !prefs.motionHintShown) {
                            prefs.motionHintShown = true
                            toast(str(R.string.sensor_hint))
                        }
                    }) else null,
                    axisPreset = motionSettings.axes.takeIf { motionSettings.gyro },
                    onAxisPreset = { preset -> motion.update { it.copy(axes = preset) } },
                    rumble = rumbleOn,
                    onRumble = {
                        rumbleOn = !rumbleOn
                        prefs.rumble = rumbleOn
                        if (!rumbleOn) vibrator()?.cancel()
                    },
                    onAmiibo = if ((status as? ConnectionStatus.Connected)?.pcOs?.startsWith("Switch") == true) {
                        { macros.openRadial(AmiiboMenu.ID) }
                    } else null,
                    macroConfig = macroConfig,
                    onEditMacros = { showMacros = true },
                    onOpenWheel = { id -> macros.openRadial(id) },
                    landscapeLayout = layoutLand,
                    portraitLayout = layoutPort,
                    onLayout = { portrait, l ->
                        if (portrait) { layoutPort = l; prefs.layoutPortrait = l } else { layoutLand = l; prefs.layoutLandscape = l }
                    },
                    editingLayout = editLayout,
                    onEditLayout = { editLayout = true },
                    onSwapLayout = ::swapLayouts,
                    onEditLayoutDone = {
                        editLayout = false
                        if (editFromHome) { editFromHome = false; showPad = false }
                    },
                )
            } else {
                HomeScreen(
                    status = statusText,
                    statusKind = statusKind,
                    initialTarget = prefs.lastTarget,
                    onScan = ::scan,
                    found = foundHosts,
                    searching = searching,
                    onSearch = ::searchLan,
                    onFound = { host ->
                        prefs.lastTarget = "${host.host}:${host.port}"
                        connection.connect(host.target())
                    },
                    onUsb = { connection.connect(ConnectTarget.usb()) },
                    onConnect = ::connectTo,
                    onPlay = { showPad = true },
                    link = link,
                    bluetooth = bluetoothPanel(btState),
                    controller = io.github.pedropasinn.mobilepad.ui.ControllerPanel(
                        builtIn = skin,
                        onBuiltIn = { skin = it; prefs.skin = it },
                        skins = deltaSkins,
                        selected = activeDelta?.dir?.name,
                        onSelect = { id -> deltaSkinId = id ?: ""; prefs.deltaSkin = deltaSkinId },
                        onImport = { skinFiles.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        onDelete = { s ->
                            if (s.dir.name == deltaSkinId) { deltaSkinId = ""; prefs.deltaSkin = "" }
                            lifecycleScope.launch { skinLibrary.delete(s) }
                        },
                        preview = previewSkinId?.let { id -> deltaSkins.firstOrNull { it.dir.name == id } },
                        onPreviewDone = { use ->
                            if (use) previewSkinId?.let { deltaSkinId = it; prefs.deltaSkin = it }
                            previewSkinId = null
                        },
                        currentName = activeDelta?.name ?: builtInName,
                        macroSummary = str(R.string.macros_summary, macroConfig.macros.size, macroConfig.radials.size),
                        onMacros = { showMacros = true },
                        orientation = orientation,
                        onOrientation = { orientation = it; prefs.orientation = it },
                        remap = remap,
                        onRemap = { remap = it; prefs.remap = it; input.remap = it },
                        style = padStyle,
                        layout = layoutLand,
                        layoutChanges = layoutLand.tweaks.size + layoutPort.tweaks.size,
                        buttonNames = buttonNames,
                        onButtonNames = { buttonNames = it; prefs.buttonNames = it },
                        profiles = profiles,
                        activeProfile = activeProfile,
                        onApplyProfile = { id -> profiles.firstOrNull { it.id == id }?.let(::applyProfile) },
                        onNewProfile = { name ->
                            val p = snapshotProfile(GameProfile.newId(), name)
                            saveProfiles(profiles + p)
                            activeProfile = p.id; prefs.activeProfile = p.id
                        },
                        onRenameProfile = { id, name -> saveProfiles(profiles.map { if (it.id == id) it.copy(name = name) else it }) },
                        onDeleteProfile = { id ->
                            saveProfiles(profiles.filter { it.id != id })
                            if (activeProfile == id) { activeProfile = ""; prefs.activeProfile = "" }
                        },
                        onDetachProfile = { activeProfile = ""; prefs.activeProfile = "" },
                        onEditLayout = { editLayout = true; editFromHome = true; showPad = true },
                        onResetLayout = {
                            layoutLand = io.github.pedropasinn.mobilepad.ui.PadLayout(); prefs.layoutLandscape = layoutLand
                            layoutPort = io.github.pedropasinn.mobilepad.ui.PadLayout(); prefs.layoutPortrait = layoutPort
                        },
                        portraitLayout = layoutPort,
                        onLayoutPreset = { land, port ->
                            layoutLand = land; prefs.layoutLandscape = land
                            layoutPort = port; prefs.layoutPortrait = port
                        },
                    ),
                    settings = io.github.pedropasinn.mobilepad.ui.SettingsPanel(
                        theme = theme,
                        onTheme = { theme = it; prefs.theme = it },
                        language = language,
                        onLanguage = {
                            prefs.language = it
                            (application as MobilepadApp).applyLanguage(it)
                            language = it
                        },
                        shizuku = shizukuState,
                        onShizukuPermission = { shizuku.requestPermission { refreshShizuku() } },
                    ),
                    sectionIndex = homeSection,
                    onSection = { homeSection = it },
                    motion = { wide -> io.github.pedropasinn.mobilepad.ui.MotionPage(motion, wide) },
                    amiibo = {
                        io.github.pedropasinn.mobilepad.ui.AmiiboCard(
                            amiiboLibrary,
                            onImportFiles = { amiiboFiles.launch(arrayOf("application/octet-stream", "*/*")) },
                            onImportFolder = { amiiboFolder.launch(null) },
                            onDelete = { entry -> lifecycleScope.launch { amiiboLibrary.delete(entry) } },
                        )
                    },
                    amiiboSummary = if (amiiboEntries.isEmpty()) str(R.string.amiibo_none) else str(R.string.amiibo_count, amiiboEntries.size),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        btNameTick++ // pairings may have changed in the system settings
        refreshShizuku()
        hideSystemBars()
        motion.resume()
    }

    override fun onPause() {
        input.resetTouch()
        motion.pause()
        super.onPause()
    }

    /**
     * Keeps Wi-Fi out of power saving while connected: otherwise a state can wait for the next
     * beacon (tens to hundreds of ms) and input feels laggy or stuck.
     */
    private val wifiLock by lazy {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        else @Suppress("DEPRECATION") WifiManager.WIFI_MODE_FULL_HIGH_PERF
        applicationContext.getSystemService(WifiManager::class.java)
            .createWifiLock(mode, "Mobilepad:link")
            .apply { setReferenceCounted(false) }
    }

    override fun onDestroy() {
        if (wifiLock.isHeld) wifiLock.release()
        unregisterReceiver(pairingReceiver)
        connection.release()
        // With Bluetooth mode on, the registration stays alive in the foreground service.
        if (!prefs.bluetoothEnabled) bluetooth.stop()
        shizuku.release()
        motion.release()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (physical.onKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        physical.onMotion(event) || super.dispatchGenericMotionEvent(event)

    /** The current setup as a profile. */
    private fun snapshotProfile(id: String, name: String) = GameProfile(
        id, name, skin.name, deltaSkinId, layoutLand.encode(), layoutPort.encode(), buttonNames.name,
        remap.entries.joinToString(",") { "${it.key}:${it.value}" }, macroConfig.toJson(), motion.settings.value.toJson(),
        altLand.encode(), altPort.encode(),
    )

    /** Swaps the layouts in use with the other ones (both orientations), e.g. race ⇄ menus. */
    private fun swapLayouts() {
        val l = layoutLand; val p = layoutPort
        layoutLand = altLand; layoutPort = altPort
        altLand = l; altPort = p
        prefs.layoutLandscape = layoutLand; prefs.layoutPortrait = layoutPort
        prefs.altLandscape = altLand; prefs.altPortrait = altPort
        input.resetTouch()
    }

    /** Puts a profile's whole setup to use and makes it the active one. */
    private fun applyProfile(p: GameProfile) {
        skin = runCatching { io.github.pedropasinn.mobilepad.ui.SkinId.valueOf(p.skin) }.getOrDefault(skin); prefs.skin = skin
        deltaSkinId = p.deltaSkin; prefs.deltaSkin = deltaSkinId
        layoutLand = io.github.pedropasinn.mobilepad.ui.PadLayout.decode(p.layoutLand); prefs.layoutLandscape = layoutLand
        layoutPort = io.github.pedropasinn.mobilepad.ui.PadLayout.decode(p.layoutPort); prefs.layoutPortrait = layoutPort
        altLand = io.github.pedropasinn.mobilepad.ui.PadLayout.decode(p.altLand); prefs.altLandscape = altLand
        altPort = io.github.pedropasinn.mobilepad.ui.PadLayout.decode(p.altPort); prefs.altPortrait = altPort
        buttonNames = runCatching { io.github.pedropasinn.mobilepad.ui.ButtonNames.valueOf(p.buttonNames) }.getOrDefault(buttonNames); prefs.buttonNames = buttonNames
        remap = p.remap.split(',').mapNotNull { pair ->
            val (a, b) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            (a.toIntOrNull() ?: return@mapNotNull null) to (b.toIntOrNull() ?: return@mapNotNull null)
        }.toMap()
        prefs.remap = remap; input.remap = remap
        runCatching { applyMacros(MacroConfig.parse(p.macros), save = true) }
        val m = MotionSettings.parse(p.motion)
        motion.update { m }
        activeProfile = p.id; prefs.activeProfile = p.id
    }

    private fun saveProfiles(list: List<GameProfile>) {
        profiles = list
        prefs.profiles = list
    }

    private fun applyMacros(config: MacroConfig, save: Boolean) {
        macroConfig = config
        macros.config = config
        if (save) prefs.macroJson = config.toJson()
    }

    private fun bluetoothPanel(state: BluetoothGamepad.State): BluetoothPanel {
        @Suppress("UNUSED_VARIABLE") val tick = btNameTick
        val active = state !is BluetoothGamepad.State.Off &&
            state !is BluetoothGamepad.State.Unsupported && state !is BluetoothGamepad.State.Failed
        val status = when (state) {
            BluetoothGamepad.State.Off -> ""
            BluetoothGamepad.State.Unsupported -> str(R.string.bt_unsupported)
            BluetoothGamepad.State.Registering -> str(R.string.bt_registering)
            BluetoothGamepad.State.Ready -> if (bluetoothProfileId == SwitchProProfile.ID) {
                str(R.string.bt_switch_hint)
            } else {
                str(R.string.bt_ready)
            }
            is BluetoothGamepad.State.Connecting -> str(R.string.connecting_to, state.name)
            is BluetoothGamepad.State.Connected -> str(R.string.connected_to, state.name)
            is BluetoothGamepad.State.Failed -> str(R.string.error, state.reason)
        }
        val hosts = if (active && hasBluetoothPermission()) {
            runCatching { bluetooth.pairedHosts().map { it.address to (it.name ?: it.address) } }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        return BluetoothPanel(
            active = active,
            status = status,
            pairedHosts = hosts,
            onStart = ::startBluetooth,
            onVisible = {
                discoverable.launch(
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                        .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120),
                )
            },
            onDisable = ::disableBluetooth,
            profileId = bluetoothProfileId,
            btName = bluetooth.customName,
            phoneName = bluetooth.phoneName() ?: "",
            onBtName = { bluetooth.customName = it; btNameTick++ },
            onProfile = { id ->
                prefs.bluetoothProfile = id
                useBluetoothProfile(id)
            },
            onConnect = { address ->
                bluetooth.pairedHosts().firstOrNull { it.address == address }?.let(bluetooth::connect)
            },
        )
    }

    private fun hasBluetoothPermission() = Build.VERSION.SDK_INT < 31 ||
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun startBluetooth() {
        if (hasBluetoothPermission()) {
            enableBluetooth()
        } else {
            val permissions = mutableListOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
            if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
            bluetoothPermissions.launch(permissions.toTypedArray())
        }
    }

    /**
     * Selects what the phone emulates over Bluetooth. The Switch profile also forwards
     * rumble to the phone's vibrator and shows the player number.
     */
    private fun useBluetoothProfile(id: String) {
        switchJobs.forEach { it.cancel() }
        switchJobs = emptyList()
        switchPlayer = 0
        bluetoothProfileId = id
        if (id != SwitchProProfile.ID) {
            bluetooth.profile = GenericGamepadProfile()
            restoreDeviceClass()
            return
        }
        applyGamepadDeviceClass()
        val current = bluetooth.profile as? SwitchProProfile ?: SwitchProProfile(switchAddress())
        bluetooth.profile = current
        switchJobs = listOf(
            lifecycleScope.launch { current.player.collect { switchPlayer = it } },
            lifecycleScope.launch {
                current.rumble.collect { on -> onFeedback(Feedback(if (on) 160 else 0, 0, 0)) }
            },
        )
    }

    /**
     * The Switch drops anything that is not a gamepad right after pairing: in Switch mode
     * the phone advertises class 0x002508 through Shizuku, and gets its own back afterwards.
     */
    private fun applyGamepadDeviceClass() {
        if (shizuku.availability() != ShizukuScreen.Availability.READY) {
            toast(str(R.string.switch_needs_shizuku))
            return
        }
        lifecycleScope.launch {
            val previous = shizuku.setDeviceClass(GAMEPAD_DEVICE_CLASS)
            if (previous >= 0 && previous != GAMEPAD_DEVICE_CLASS && prefs.originalDeviceClass < 0) {
                prefs.originalDeviceClass = previous
            }
        }
    }

    private fun restoreDeviceClass() {
        val original = prefs.originalDeviceClass
        if (original < 0 || shizuku.availability() != ShizukuScreen.Availability.READY) return
        lifecycleScope.launch {
            if (shizuku.setDeviceClass(original) >= 0) prefs.originalDeviceClass = -1
        }
    }

    /** Stable controller address for the Switch: Nintendo prefix + bytes of the device id. */
    private fun switchAddress(): ByteArray {
        val id = prefs.deviceId
        return byteArrayOf(
            0x98.toByte(), 0xB6.toByte(), 0xE9.toByte(),
            (id shr 16).toByte(), (id shr 8).toByte(), id.toByte(),
        )
    }

    private fun enableBluetooth() {
        prefs.bluetoothEnabled = true
        BluetoothKeepAliveService.start(this)
        bluetooth.start()
    }

    private fun disableBluetooth() {
        restoreDeviceClass()
        prefs.bluetoothEnabled = false
        bluetooth.stop()
        BluetoothKeepAliveService.stop(this)
    }

    /** Looks for Mobilepad companions and Switch sysmodules on the local network. */
    private fun searchLan() {
        searching = true
        lifecycleScope.launch {
            foundHosts = runCatching { io.github.pedropasinn.mobilepad.net.Discovery.search() }
                .onFailure { android.util.Log.e("Discovery", "search failed", it) }
                .getOrDefault(emptyList())
            searching = false
            if (foundHosts.isEmpty()) toast(str(R.string.none_found))
        }
    }

    private fun scan() {
        scanner.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(str(R.string.qr_prompt))
                .setBeepEnabled(false)
                .setOrientationLocked(false),
        )
    }

    private fun connectTo(raw: String) {
        android.util.Log.i("MainActivity", "connect target: $raw")
        val target = ConnectTarget.parse(raw)
        if (target == null) {
            toast(str(R.string.bad_format, raw))
            return
        }
        if (!target.usb) prefs.lastTarget = "${target.hosts.first()}:${target.port}"
        connection.connect(target)
    }

    private fun onFeedback(feedback: Feedback) {
        // 255 = the host has no player numbers (Linux companion).
        if (feedback.ledNumber < 16) playerLed = feedback.ledNumber
        if (!rumbleOn) return
        val strength = maxOf(feedback.largeMotor, feedback.smallMotor)
        val vibrator = vibrator() ?: return
        if (strength == 0) {
            vibrator.cancel()
        } else if (vibrator.hasAmplitudeControl()) {
            vibrator.vibrate(VibrationEffect.createOneShot(RUMBLE_MAX_MS, strength.coerceIn(1, 255)))
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(RUMBLE_MAX_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

    /**
     * Debug builds only, for testing over ADB:
     *   --es target IP:PORT | --ez usb true | --es macrosFile /data/local/tmp/macros.json
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startLatencyTest(intent)
    }

    /** Latency test asked by the PC tool (tools/latency): `--ez latency true [--ei latencyCount N]`. */
    private fun startLatencyTest(intent: Intent?) {
        if (intent?.getBooleanExtra("latency", false) != true) return
        input.resetTouch()
        LatencyTest.start(
            this, input, lifecycleScope, intent.getIntExtra("latencyCount", 200),
            avoid = macroConfig.macros.filter { Integer.bitCount(it.trigger.buttons) > 1 }.fold(0) { m, x -> m or x.trigger.buttons },
        ) {
            runOnUiThread { toast("Latency test done") }
        }
    }

    private fun applyTestExtras() {
        intent.getStringExtra("target")?.let(::connectTo)
        if (intent.getBooleanExtra("usb", false)) connection.connect(ConnectTarget.usb())
        intent.getStringExtra("macrosFile")?.let { path ->
            runCatching { applyMacros(MacroConfig.parse(java.io.File(path).readText()), save = false) }
                .onFailure { toast("Macro di test non caricate: ${it.message}") }
        }
        // Screenshots: open the controller, optionally with a wheel already shown.
        if (intent.getBooleanExtra("pad", false)) showPad = true
        intent.getStringExtra("radial")?.let { macros.openRadial(it) }
    }

    private fun describe(status: ConnectionStatus): String = when (status) {
        ConnectionStatus.Idle -> str(R.string.not_connected)
        is ConnectionStatus.Connecting -> str(R.string.connecting_to, status.target.toString())
        is ConnectionStatus.Connected ->
            str(R.string.connected_os, status.target.toString(), status.pcOs) +
                if (playerLed > 0) " · " + str(R.string.player, playerLed) else ""
        is ConnectionStatus.Failed -> str(R.string.error, status.reason)
    }

    private fun refreshShizuku() {
        shizukuState = shizuku.availability()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        private const val RUMBLE_MAX_MS = 10_000L
        private const val GAMEPAD_DEVICE_CLASS = 0x002508 // peripheral, gamepad
    }
}
