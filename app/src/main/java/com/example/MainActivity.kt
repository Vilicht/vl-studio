package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.StreamingService
import com.example.service.StreamingStatus
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupImmersiveMode()

        setContent {
            VilichtStudioTheme {
                StudioAppScreen(
                    viewModel = viewModel,
                    onRequestScreenCapture = { launchScreenCaptureConsent() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupImmersiveMode()
        // Freeze-frame editing (§4.5.2): When returning while streaming, freeze preview for canvas editing
        val currentLive = viewModel.liveState.value
        if (currentLive.status == StreamingStatus.LIVE) {
            StreamingService.activeServiceInstance?.setPreviewFrozen(true)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersiveMode()
        }
    }

    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private var screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startPreviewService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission is required to stream games", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchScreenCaptureConsent() {
        // Capture sudah jalan (mode siap): tombol GO LIVE langsung memulai siaran
        if (viewModel.liveState.value.status == StreamingStatus.PREVIEW) {
            sendGoLive()
            return
        }
        // Check floating window permission (§4.5.1)
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Enable 'Display over other apps' to use the in-game floating live control", Toast.LENGTH_LONG).show()
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(overlayIntent)
        }

        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mpManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(intent)
    }

    /** Izin capture diberikan: nyalakan mesin dalam mode siap (belum siaran), lalu ke game. */
    private fun startPreviewService(resultCode: Int, data: Intent) {
        val preset = viewModel.currentPreset.value
        val portrait = viewModel.selectedScene.value?.isPortrait ?: false
        val serviceIntent = Intent(this, StreamingService::class.java).apply {
            action = StreamingService.ACTION_START_PREVIEW
            putExtra(StreamingService.EXTRA_RESULT_CODE, resultCode)
            putExtra(StreamingService.EXTRA_DATA, data)
            putExtra(StreamingService.EXTRA_WIDTH, preset.width)
            putExtra(StreamingService.EXTRA_HEIGHT, preset.height)
            putExtra(StreamingService.EXTRA_FPS, preset.fps)
            putExtra(StreamingService.EXTRA_BITRATE, preset.defaultBitrateKbps)
            putExtra(StreamingService.EXTRA_PORTRAIT, portrait)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        Toast.makeText(
            this,
            "Siap. Buka game, lalu tekan 'Atur Layout' di tombol melayang.",
            Toast.LENGTH_LONG
        ).show()
        // Ke game: tombol melayang muncul di atasnya
        moveTaskToBack(true)
    }

    /** Layout sudah diatur: mulai siaran RTMP dengan profil tujuan yang dipilih. */
    private fun sendGoLive() {
        val profile = viewModel.selectedProfile.value
        if (profile == null) {
            Toast.makeText(this, "Configure an RTMP destination profile first", Toast.LENGTH_SHORT).show()
            viewModel.isSettingsOpen.value = true
            return
        }
        val plainKey = viewModel.getDecryptedKey(profile.encryptedStreamKey)
        val endpoint = if (profile.serverUrl.endsWith("/")) {
            "${profile.serverUrl}$plainKey"
        } else {
            "${profile.serverUrl}/$plainKey"
        }
        val goLiveIntent = Intent(this, StreamingService::class.java).apply {
            action = StreamingService.ACTION_GO_LIVE
            putExtra(StreamingService.EXTRA_ENDPOINT, endpoint)
            putExtra(StreamingService.EXTRA_AUDIO_BITRATE, profile.audioBitrateKbps)
        }
        startService(goLiveIntent)
        moveTaskToBack(true)
    }
}

@Composable
fun StudioAppScreen(
    viewModel: StudioViewModel,
    onRequestScreenCapture: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val scenes by viewModel.allScenes.collectAsStateWithLifecycle()
    val selectedScene by viewModel.selectedScene.collectAsStateWithLifecycle()
    val sources by viewModel.currentSources.collectAsStateWithLifecycle()
    val selectedSource by viewModel.selectedSource.collectAsStateWithLifecycle()
    val profiles by viewModel.allProfiles.collectAsStateWithLifecycle()
    val selectedProfile by viewModel.selectedProfile.collectAsStateWithLifecycle()
    val audioSettings by viewModel.audioSettings.collectAsStateWithLifecycle()
    val liveState by viewModel.liveState.collectAsStateWithLifecycle()

    val micLevel by viewModel.audioMixer.micLevel.collectAsStateWithLifecycle()
    val systemLevel by viewModel.audioMixer.systemLevel.collectAsStateWithLifecycle()
    val currentPreset by viewModel.currentPreset.collectAsStateWithLifecycle()

    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()
    val isDiagnosticsOpen by viewModel.isDiagnosticsOpen.collectAsStateWithLifecycle()
    val isAddSourceOpen by viewModel.isAddSourceOpen.collectAsStateWithLifecycle()
    val isAddSceneOpen by viewModel.isAddSceneOpen.collectAsStateWithLifecycle()

    // Runtime Permissions Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (micGranted) {
            viewModel.audioMixer.startMonitoring()
        }
    }

    LaunchedEffect(Unit) {
        val permsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permsToRequest.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permsToRequest.toTypedArray())
        }
    }

    // Error toast for streaming
    LaunchedEffect(liveState.errorMessage) {
        liveState.errorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("studio_scaffold"),
        containerColor = CharcoalMain,
        topBar = {
            StudioTopBar(
                status = liveState.status,
                stats = liveState.stats,
                isPortraitCanvas = selectedScene?.isPortrait ?: false,
                onToggleRatio = { viewModel.toggleCanvasOrientation() },
                onOpenDiagnostics = { viewModel.isDiagnosticsOpen.value = true },
                onOpenSettings = { viewModel.isSettingsOpen.value = true }
            )
        },
        bottomBar = {
            AudioMixerPanel(
                status = liveState.status,
                micLevel = micLevel,
                systemLevel = systemLevel,
                micVolume = audioSettings?.micVolume ?: 1.0f,
                isMicMuted = audioSettings?.isMicMuted ?: false,
                systemVolume = audioSettings?.systemVolume ?: 0.8f,
                isSystemMuted = audioSettings?.isSystemMuted ?: false,
                currentPreset = currentPreset,
                isAutoMode = selectedProfile?.isAutoMode ?: true,
                onToggleMicMute = { viewModel.toggleMicMute() },
                onUpdateMicVolume = { viewModel.updateMicVolume(it) },
                onToggleSystemMute = {
                    val currentSys = audioSettings?.isSystemMuted ?: false
                    viewModel.updateSystemVolume(if (currentSys) 0.8f else 0.0f)
                },
                onUpdateSystemVolume = { viewModel.updateSystemVolume(it) },
                onOpenPresetSelector = { viewModel.isSettingsOpen.value = true },
                onGoLive = { onRequestScreenCapture() },
                onStopStream = {
                    val stopIntent = Intent(context, StreamingService::class.java).apply {
                        action = StreamingService.ACTION_STOP
                    }
                    context.startService(stopIntent)
                },
                onTogglePause = {
                    StreamingService.activeServiceInstance?.togglePause()
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CharcoalMain)
        ) {
            if (isLandscape) {
                // Desktop Professional 3-Pane Studio Layout
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Left Panel: Scene Manager + Source Manager
                    LeftPanel(
                        scenes = scenes,
                        selectedScene = selectedScene,
                        sources = sources,
                        selectedSource = selectedSource,
                        onSelectScene = { viewModel.selectScene(it) },
                        onAddScene = { viewModel.isAddSceneOpen.value = true },
                        onDuplicateScene = { viewModel.duplicateScene(it) },
                        onDeleteScene = { viewModel.deleteScene(it) },
                        onSelectSource = { viewModel.selectSource(it) },
                        onAddSource = { viewModel.isAddSourceOpen.value = true },
                        onToggleSourceVisibility = { src ->
                            viewModel.updateSelectedSource { it.copy(isVisible = !src.isVisible) }
                        },
                        onToggleSourceLock = { src ->
                            viewModel.updateSelectedSource { it.copy(isLocked = !src.isLocked) }
                        },
                        onReorderSource = { src, up -> viewModel.reorderSource(src, up) },
                        onDuplicateSource = { viewModel.duplicateSource(it) },
                        onDeleteSource = { viewModel.deleteSource(it) }
                    )

                    // Center: Composed Live Preview Canvas
                    StudioCanvas(
                        sources = sources,
                        selectedSource = selectedSource,
                        isPortrait = selectedScene?.isPortrait ?: false,
                        isPreviewFrozen = liveState.isPreviewFrozen,
                        frozenBitmap = liveState.frozenSnapshot,
                        onResumePreview = { viewModel.resumePreview() },
                        onSelectSource = { viewModel.selectSource(it) },
                        onUpdateSourceTransform = { updated ->
                            viewModel.updateSelectedSource { updated }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Right Panel: Transform & Video Filter Controls
                    PropertiesPanel(
                        selectedSource = selectedSource,
                        onUpdateSource = { updated ->
                            viewModel.updateSelectedSource { updated }
                        }
                    )
                }
            } else {
                // Responsive Portrait Studio Layout
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    StudioCanvas(
                        sources = sources,
                        selectedSource = selectedSource,
                        isPortrait = selectedScene?.isPortrait ?: false,
                        isPreviewFrozen = liveState.isPreviewFrozen,
                        frozenBitmap = liveState.frozenSnapshot,
                        onResumePreview = { viewModel.resumePreview() },
                        onSelectSource = { viewModel.selectSource(it) },
                        onUpdateSourceTransform = { updated ->
                            viewModel.updateSelectedSource { updated }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.55f)
                    )

                    // Bottom Tabbed Manager for portrait
                    var portraitTab by remember { mutableStateOf(0) }
                    TabRow(
                        selectedTabIndex = portraitTab,
                        containerColor = CharcoalPanel,
                        contentColor = ChampagneGold
                    ) {
                        Tab(
                            selected = portraitTab == 0,
                            onClick = { portraitTab = 0 },
                            text = { Text("Scenes & Sources", fontSize = 11.sp) }
                        )
                        Tab(
                            selected = portraitTab == 1,
                            onClick = { portraitTab = 1 },
                            text = { Text("Properties", fontSize = 11.sp) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.45f)
                    ) {
                        if (portraitTab == 0) {
                            LeftPanel(
                                scenes = scenes,
                                selectedScene = selectedScene,
                                sources = sources,
                                selectedSource = selectedSource,
                                onSelectScene = { viewModel.selectScene(it) },
                                onAddScene = { viewModel.isAddSceneOpen.value = true },
                                onDuplicateScene = { viewModel.duplicateScene(it) },
                                onDeleteScene = { viewModel.deleteScene(it) },
                                onSelectSource = { viewModel.selectSource(it) },
                                onAddSource = { viewModel.isAddSourceOpen.value = true },
                                onToggleSourceVisibility = { src ->
                                    viewModel.updateSelectedSource { it.copy(isVisible = !src.isVisible) }
                                },
                                onToggleSourceLock = { src ->
                                    viewModel.updateSelectedSource { it.copy(isLocked = !src.isLocked) }
                                },
                                onReorderSource = { src, up -> viewModel.reorderSource(src, up) },
                                onDuplicateSource = { viewModel.duplicateSource(it) },
                                onDeleteSource = { viewModel.deleteSource(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            PropertiesPanel(
                                selectedSource = selectedSource,
                                onUpdateSource = { updated -> viewModel.updateSelectedSource { updated } },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Dialogs
    SettingsDialog(
        isOpen = isSettingsOpen,
        profiles = profiles,
        selectedProfile = selectedProfile,
        autoDetection = viewModel.autoDetection,
        currentPreset = currentPreset,
        getDecryptedKey = { viewModel.getDecryptedKey(it) },
        onSaveProfile = { prof, key -> viewModel.saveProfile(prof, key) },
        onSelectProfile = { viewModel.selectProfile(it) },
        onSelectPreset = { viewModel.selectPreset(it) },
        onSetAutoMode = { viewModel.setAutoPresetMode() },
        onDismiss = { viewModel.isSettingsOpen.value = false }
    )

    DiagnosticsDialog(
        isOpen = isDiagnosticsOpen,
        stats = liveState.stats,
        onDismiss = { viewModel.isDiagnosticsOpen.value = false }
    )

    AddSourceDialog(
        isOpen = isAddSourceOpen,
        onAddSource = { name, type, text -> viewModel.addSource(name, type, text) },
        onDismiss = { viewModel.isAddSourceOpen.value = false }
    )

    AddSceneDialog(
        isOpen = isAddSceneOpen,
        onAddScene = { name, isPortrait -> viewModel.createScene(name, isPortrait) },
        onDismiss = { viewModel.isAddSceneOpen.value = false }
    )
}
