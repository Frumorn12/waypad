package dev.waypad.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.waypad.android.BuildConfig
import dev.waypad.android.Screen
import dev.waypad.android.WaypadUiState
import dev.waypad.android.WaypadViewModel
import dev.waypad.android.R
import dev.waypad.android.core.externalinput.ExternalInputDeviceClass
import dev.waypad.android.core.input.RemoteGestureAction
import dev.waypad.android.core.input.RemoteGestureMode
import dev.waypad.android.core.input.RemotePointer
import dev.waypad.android.core.input.RemoteTouchpadGestureMachine
import dev.waypad.android.core.model.ButtonState
import dev.waypad.android.core.model.ConnectionState
import dev.waypad.android.core.model.DiscoveredHost
import dev.waypad.android.core.model.PointerButton
import dev.waypad.android.core.model.StreamProfile
import dev.waypad.android.core.model.TrustedHost
import dev.waypad.android.core.model.formatFps
import dev.waypad.android.core.screen.ScreenViewport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.hypot

@Composable
fun WaypadApp(viewModel: WaypadViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val remoteFullscreen = state.screen == Screen.RemoteDisplay && state.remoteScreenFullscreen
    val externalPointerCapture = state.connectionState == ConnectionState.Connected &&
        (state.screen == Screen.Remote || state.screen == Screen.RemoteDisplay)
    RemoteScreenOrientationEffect(state.screen == Screen.RemoteDisplay)
    FullscreenSystemUiEffect(remoteFullscreen)
    ExternalPointerCaptureEffect(externalPointerCapture)
    BackHandler(remoteFullscreen) {
        viewModel.setRemoteScreenFullscreen(false)
    }
    BackHandler(
        !remoteFullscreen &&
            (state.screen == Screen.Remote || state.screen == Screen.RemoteDisplay || state.screen == Screen.Keyboard || state.screen == Screen.Controls || state.screen == Screen.Troubleshooting || state.screen == Screen.Settings)
    ) {
        viewModel.go(Screen.Discovery)
    }
    WaypadTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Ink, Color(0xFF0D130D), Ink)))
        ) {
            if (!remoteFullscreen) AtmosphericBackground()
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    if (!remoteFullscreen) TopStatus(state, viewModel)
                },
                bottomBar = {
                    if (state.connectionState == ConnectionState.Connected && !remoteFullscreen) {
                        BottomRail(state.screen, viewModel)
                    }
                },
            ) { padding ->
                Box(
                    Modifier
                        .padding(if (remoteFullscreen) androidx.compose.foundation.layout.PaddingValues(0.dp) else padding)
                        .padding(horizontal = if (remoteFullscreen) 0.dp else 18.dp)
                        .fillMaxSize()
                ) {
                    when (state.screen) {
                        Screen.Onboarding -> OnboardingScreen(viewModel)
                        Screen.Discovery -> DiscoveryScreen(state, viewModel)
                        Screen.Pairing -> PairingScreen(state, viewModel)
                        Screen.Remote -> RemoteScreen(state, viewModel)
                        Screen.RemoteDisplay -> RemoteDisplayScreen(state, viewModel)
                        Screen.Keyboard -> KeyboardScreen(viewModel)
                        Screen.Controls -> ControlsScreen(state, viewModel)
                        Screen.Settings -> SettingsScreen(state, viewModel)
                        Screen.TrustedHosts -> TrustedHostsScreen(state, viewModel)
                        Screen.Troubleshooting -> TroubleshootingScreen(state, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExternalPointerCaptureEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (enabled) {
                Log.i("WaypadExternalInput", "pointer_capture_request")
                view.requestPointerCapture()
            } else if (view.hasPointerCapture()) {
                Log.i("WaypadExternalInput", "pointer_capture_release")
                view.releasePointerCapture()
            }
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && view.hasPointerCapture()) {
                view.releasePointerCapture()
            }
        }
    }
}

@Composable
private fun RemoteScreenOrientationEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val activity = view.context.findActivity()
        val previous = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = if (enabled) {
            Log.i("WaypadRemoteScreen", "orientation_policy=full_sensor")
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else {
            Log.i("WaypadRemoteScreen", "orientation_policy=portrait")
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose {
            activity?.requestedOrientation = previous
        }
    }
}

@Composable
private fun FullscreenSystemUiEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val window = view.context.findActivity()?.window
        if (enabled) {
            Log.i("WaypadRemoteScreen", "fullscreen_system_ui_hide")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window?.insetsController?.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                window?.insetsController?.hide(WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                window?.decorView?.systemUiVisibility =
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        } else {
            Log.i("WaypadRemoteScreen", "fullscreen_system_ui_show")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window?.insetsController?.show(WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window?.insetsController?.show(WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun AtmosphericBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawCircle(Acid.copy(alpha = 0.10f), radius = w * 0.42f, center = Offset(w * 0.82f, h * 0.12f))
        drawCircle(Color(0xFF9ED8FF).copy(alpha = 0.08f), radius = w * 0.34f, center = Offset(w * 0.1f, h * 0.85f))
        val path = Path().apply {
            moveTo(w * 0.06f, h * 0.18f)
            lineTo(w * 0.9f, h * 0.12f)
            lineTo(w * 0.76f, h * 0.17f)
            lineTo(w * 0.18f, h * 0.24f)
            close()
        }
        drawPath(path, Color.White.copy(alpha = 0.025f))
    }
}

@Composable
private fun TopStatus(state: WaypadUiState, viewModel: WaypadViewModel) {
    Column(
        Modifier
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            BrandMark()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("WAYPAD", color = Mist, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Text(
                    state.connectedHost?.hostName ?: state.status,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = { viewModel.go(Screen.Settings) }) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Mist)
            }
        }
        AnimatedVisibility(state.error != null) {
            ErrorStrip(state.error ?: "")
        }
    }
}

@Composable
private fun BrandMark() {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Transparent)
    ) {
        Image(
            painter = painterResource(R.drawable.waypad_brand_cutout),
            contentDescription = "Waypad",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun ErrorStrip(message: String) {
    Surface(
        color = Color(0xFF371A1A),
        contentColor = Color(0xFFFFB4B4),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BottomRail(active: Screen, viewModel: WaypadViewModel) {
    Surface(color = Graphite.copy(alpha = 0.94f), tonalElevation = 8.dp) {
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RailItem("Pad", Icons.Rounded.Mouse, active == Screen.Remote) { viewModel.go(Screen.Remote) }
            RailItem("Screen", Icons.Rounded.Computer, active == Screen.RemoteDisplay) { viewModel.go(Screen.RemoteDisplay) }
            RailItem("Keys", Icons.Rounded.Keyboard, active == Screen.Keyboard) { viewModel.go(Screen.Keyboard) }
            RailItem("Control", Icons.Rounded.Tune, active == Screen.Controls) { viewModel.go(Screen.Controls) }
            RailItem("Diag", Icons.Rounded.Shield, active == Screen.Troubleshooting) { viewModel.go(Screen.Troubleshooting) }
        }
    }
}

@Composable
private fun RailItem(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val alpha by animateFloatAsState(if (active) 1f else 0.55f, label = "rail-alpha")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Icon(icon, contentDescription = label, tint = if (active) Acid else Mist.copy(alpha = alpha))
        Text(label, color = if (active) Acid else Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OnboardingScreen(viewModel: WaypadViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(
            "Wayland control,\nwithout X11 shortcuts.",
            color = Mist,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Pair your Android phone with a Linux Wayland host. Waypad uses a pinned host identity, encrypted command channel, and portal-aware capability checks.",
            color = Muted,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = { viewModel.startDiscovery() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Discover hosts")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { viewModel.go(Screen.TrustedHosts) }, modifier = Modifier.fillMaxWidth()) {
            Text("Trusted hosts")
        }
    }
}

@Composable
private fun DiscoveryScreen(state: WaypadUiState, viewModel: WaypadViewModel) {
    var inviteText by remember { mutableStateOf("") }
    var scannerOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                SectionTitle("Host Discovery", "UDP LAN discovery with manual IP and QR invite fallback.")
                Button(onClick = { viewModel.startDiscovery() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan LAN")
                }
            }
            items(state.discoveredHosts) { host ->
                HostCard(host) { viewModel.selectHost(host) }
            }
            item {
                GlassCard {
                    Text("QR invite", color = Mist, fontWeight = FontWeight.Bold)
                    Text(
                        "Scan a terminal QR in-app. Invites can carry LAN and remote-direct endpoints.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { scannerOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan QR invite")
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inviteText,
                        onValueChange = { inviteText = it.trim() },
                        label = { Text("waypad://invite...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.applyInvite(inviteText) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = inviteText.startsWith("waypad://invite"),
                    ) {
                        Text("Use pasted invite")
                    }
                }
            }
            item {
                GlassCard {
                    Text("Manual connect", color = Mist, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.manualAddress,
                        onValueChange = viewModel::setManualAddress,
                        label = { Text("Host IP address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.manualPort,
                        onValueChange = viewModel::setManualPort,
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.useManualHost() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Continue")
                    }
                }
            }
        }
        if (scannerOpen) {
            WaypadQrScannerOverlay(
                onInviteScanned = { raw ->
                    scannerOpen = false
                    inviteText = raw
                    viewModel.applyInvite(raw)
                },
                onDismiss = { scannerOpen = false },
            )
        }
    }
}

@Composable
private fun HostCard(host: DiscoveredHost, onClick: () -> Unit) {
    GlassCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Computer, contentDescription = null, tint = Acid)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(host.hostName, color = Mist, fontWeight = FontWeight.Bold)
                Text("${host.address}:${host.port}", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text(shortFingerprint(host.fingerprint), color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            StatusPill(
                when {
                    host.inputSupported && host.captureSupported -> "Control + screen"
                    host.inputSupported -> host.inputBackend
                    host.captureSupported -> host.captureBackend
                    else -> "Limited"
                }
            )
        }
    }
}

@Composable
private fun PairingScreen(state: WaypadUiState, viewModel: WaypadViewModel) {
    val host = state.selectedHost
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        SectionTitle("Pair ${host?.hostName ?: "host"}", "Run `waypad-daemon pair-code` on the Linux host, then enter the code here.")
        GlassCard {
            Text("Host fingerprint", color = Muted, style = MaterialTheme.typography.labelMedium)
            Text(
                host?.fingerprint?.ifBlank { "Manual host: compare fingerprint printed by the daemon." } ?: "",
                color = Mist,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = state.pairingCode,
                onValueChange = viewModel::setPairingCode,
                label = { Text("6 digit pairing code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = { viewModel.pairSelectedHost() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Shield, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Pair securely")
            }
        }
    }
}

@Composable
private fun RemoteScreen(state: WaypadUiState, viewModel: WaypadViewModel) {
    val haptics = LocalHapticFeedback.current
    val tapSlop = with(LocalDensity.current) { 8.dp.toPx() }
    var dragLocked by remember { mutableStateOf(false) }
    val currentDragLocked by rememberUpdatedState(dragLocked)
    val currentHapticsEnabled by rememberUpdatedState(state.haptics)
    val padActive = state.remoteInputSessionActive
    val scrollMode = state.remoteGestureMode == RemoteGestureMode.TwoFingerScroll
    val padBorder = when {
        scrollMode -> Color(0xFF9ED8FF).copy(alpha = 0.85f)
        padActive -> Acid.copy(alpha = 0.75f)
        else -> Line
    }
    val padGradient = when {
        scrollMode -> listOf(Color(0xFF182527), Color(0xFF101716))
        padActive -> listOf(Color(0xFF20281E), Color(0xFF101610))
        else -> listOf(Color(0xFF1B211B), Color(0xFF101410))
    }

    fun setDragLocked(enabled: Boolean) {
        if (dragLocked == enabled) return
        if (enabled) {
            if (state.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.pointerButton(PointerButton.Left, ButtonState.Pressed)
        } else {
            viewModel.pointerButton(PointerButton.Left, ButtonState.Released)
        }
        dragLocked = enabled
    }

    fun handleGestureAction(action: RemoteGestureAction) {
        when (action) {
            is RemoteGestureAction.Move -> viewModel.pointerMove(action.dx, action.dy)
            is RemoteGestureAction.Scroll -> viewModel.scroll(action.dx, action.dy)
            RemoteGestureAction.FinishScroll -> viewModel.scroll(0f, 0f, finish = true)
            RemoteGestureAction.Click -> {
                if (!currentDragLocked) {
                    if (currentHapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.pointerButton(PointerButton.Left, ButtonState.Pressed)
                    viewModel.pointerButton(PointerButton.Left, ButtonState.Released)
                }
            }
            RemoteGestureAction.DragStart -> {
                if (!currentDragLocked) {
                    if (currentHapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.pointerButton(PointerButton.Left, ButtonState.Pressed)
                }
            }
            RemoteGestureAction.DragEnd -> {
                if (!currentDragLocked) {
                    viewModel.pointerButton(PointerButton.Left, ButtonState.Released)
                }
            }
            is RemoteGestureAction.ModeChanged -> {
                if (action.from != action.to) {
                    Log.d("WaypadTouchpad", "gesture_transition from=${action.from.label} to=${action.to.label} reason=${action.reason}")
                    if (action.to == RemoteGestureMode.TwoFingerScroll && currentHapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        setDragLocked(false)
        viewModel.releasePointerButtons()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        setDragLocked(false)
        viewModel.releasePointerButtons()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.releasePointerButtons()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(if (state.capabilities.inputSupported) "Input ready" else "Input blocked")
            TextButton(onClick = { viewModel.prepareInput() }) {
                Text(if (state.capabilities.inputBackend == "wayland-portal") "Approve portal" else "Refresh input")
            }
        }
        Spacer(Modifier.height(6.dp))
        val controllerConnected = state.externalInputDevices.any { it.classes.contains(dev.waypad.android.core.externalinput.ExternalInputDeviceClass.Gamepad) || it.classes.contains(dev.waypad.android.core.externalinput.ExternalInputDeviceClass.Joystick) }
        if (controllerConnected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill("Controller connected")
                Text("Open fullscreen or Game Mode to forward inputs to PC.", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(34.dp))
                .background(Brush.verticalGradient(padGradient))
                .border(1.dp, padBorder, RoundedCornerShape(34.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val gesture = RemoteTouchpadGestureMachine(
                            tapSlopPx = tapSlop,
                            longPressDragEnabled = !currentDragLocked,
                        )
                        var sessionId = 0L
                        var sessionStarted = false
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            sessionId = viewModel.beginRemoteInteraction()
                            sessionStarted = true
                            Log.d("WaypadTouchpad", "pointer_down id=${down.id.value} x=${down.position.x} y=${down.position.y}")
                            gesture.begin(
                                RemotePointer(down.id.value, down.position.x, down.position.y),
                                down.uptimeMillis,
                            ).forEach(::handleGestureAction)
                            viewModel.updateRemoteGesture(gesture.mode, 1)

                            var moveLogCounter = 0
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    when {
                                        change.changedToDown() -> Log.d(
                                            "WaypadTouchpad",
                                            "pointer_down id=${change.id.value} x=${change.position.x} y=${change.position.y}",
                                        )
                                        change.changedToUp() -> Log.d("WaypadTouchpad", "pointer_up id=${change.id.value}")
                                        change.positionChanged() && change.pressed -> {
                                            moveLogCounter += 1
                                            if (moveLogCounter % 24 == 0) {
                                                Log.v(
                                                    "WaypadTouchpad",
                                                    "pointer_move sample_count=$moveLogCounter pointers=${event.changes.count { it.pressed }}",
                                                )
                                            }
                                        }
                                    }
                                }

                                val pressed = event.changes
                                    .filter { it.pressed }
                                    .map { RemotePointer(it.id.value, it.position.x, it.position.y) }
                                gesture.update(
                                    activePointers = pressed,
                                    timeMillis = event.changes.maxOfOrNull { it.uptimeMillis } ?: down.uptimeMillis,
                                ).forEach(::handleGestureAction)
                                viewModel.updateRemoteGesture(gesture.mode, pressed.size)

                                event.changes.forEach { change ->
                                    if (change.changedToDown() || change.changedToUp() || change.positionChanged()) {
                                        change.consume()
                                    }
                                }
                                if (pressed.isEmpty()) break
                            }
                        } catch (cancelled: CancellationException) {
                            viewModel.notePointerCancellation("pointer_input_cancelled")
                            gesture.cancel("pointer_input_cancelled").forEach(::handleGestureAction)
                            throw cancelled
                        } catch (throwable: Throwable) {
                            viewModel.notePointerFailure(throwable)
                            gesture.cancel("pointer_input_error").forEach(::handleGestureAction)
                        } finally {
                            gesture.cancel("pointer_input_finally").forEach(::handleGestureAction)
                            viewModel.updateRemoteGesture(RemoteGestureMode.Idle, 0)
                            if (sessionStarted) viewModel.endRemoteInteraction(sessionId)
                            if (!currentDragLocked) viewModel.releasePointerButtons()
                        }
                    }
                }
        ) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.Mouse, contentDescription = null, tint = Acid, modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(10.dp))
                Text("Touchpad", color = Mist, fontWeight = FontWeight.Bold)
                Text("Tap, double tap, drag lock, two-finger scroll", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (BuildConfig.DEBUG) {
                RemoteInputDebugOverlay(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (dragLocked) {
            Button(onClick = { setDragLocked(false) }, modifier = Modifier.fillMaxWidth()) {
                Text("Release drag")
            }
        } else {
            OutlinedButton(onClick = { setDragLocked(true) }, modifier = Modifier.fillMaxWidth()) {
                Text("Drag lock")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClickButton("Left", Modifier.weight(1f)) {
                viewModel.pointerButton(PointerButton.Left, ButtonState.Pressed)
                viewModel.pointerButton(PointerButton.Left, ButtonState.Released)
            }
            ClickButton("Right", Modifier.weight(1f)) {
                viewModel.pointerButton(PointerButton.Right, ButtonState.Pressed)
                viewModel.pointerButton(PointerButton.Right, ButtonState.Released)
            }
            ClickButton("Middle", Modifier.weight(1f)) {
                viewModel.pointerButton(PointerButton.Middle, ButtonState.Pressed)
                viewModel.pointerButton(PointerButton.Middle, ButtonState.Released)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun RemoteInputDebugOverlay(state: WaypadUiState, modifier: Modifier = Modifier) {
    Surface(
        color = Graphite.copy(alpha = 0.78f),
        contentColor = Mist,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(state.remoteGestureMode.label, color = Acid, style = MaterialTheme.typography.labelSmall)
            Text("pointers ${state.remotePointerCount}", color = Muted, style = MaterialTheme.typography.labelSmall)
            Text("queue ${state.remoteInputBacklog}", color = Muted, style = MaterialTheme.typography.labelSmall)
            Text(state.connectionState.name, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RemoteDisplayScreen(state: WaypadUiState, viewModel: WaypadViewModel) {
    val frame = state.screenFrame
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var keyboardText by remember { mutableStateOf("") }
    var quickKeyboardVisible by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val tapSlop = with(LocalDensity.current) { 8.dp.toPx() }
    val fullscreen = state.remoteScreenFullscreen
    val gameMode = state.remoteScreenGameMode
    val showFullscreenControls = fullscreen && (!gameMode || state.remoteScreenControlsVisible)
    val activeSource = state.screenStreamInfo?.source
        ?: state.screenSources.firstOrNull { it.id == state.selectedScreenSourceId }

    LaunchedEffect(fullscreen, gameMode, state.remoteScreenControlsVisible) {
        if (fullscreen && gameMode && state.remoteScreenControlsVisible) {
            delay(3_000)
            viewModel.setRemoteScreenControlsVisible(false, "auto_hide")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(if (fullscreen) Color.Black else Color.Transparent)
    ) {
        if (!fullscreen) {
            // Host capability pills
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(if (state.capabilities.captureSupported) "Capture ready" else "Capture blocked")
                StatusPill(if (state.capabilities.inputSupported) "Input ready" else "Input blocked")
            }
            Spacer(Modifier.height(10.dp))

            // Stream Setup Card
            GlassCard {
                Text("Stream Setup", color = Mist, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (state.screenSources.isNotEmpty()) {
                    Text("Source", color = Muted, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        items(state.screenSources) { source ->
                            val selected = source.id == state.selectedScreenSourceId
                            val backendColor = when (source.backend) {
                                "x11-ffmpeg" -> Acid
                                "wayland-screencast-portal" -> Color(0xFF9ED8FF)
                                else -> Mist
                            }
                            Surface(
                                color = if (selected) backendColor.copy(alpha = 0.18f) else Graphite.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.selectScreenSource(source.id) },
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(source.label.ifBlank { source.id }, color = if (selected) backendColor else Mist, style = MaterialTheme.typography.bodySmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(source.backend, color = Muted, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.startScreenStream() },
                        modifier = Modifier.weight(1f),
                        enabled = !state.screenStreaming,
                    ) {
                        Icon(Icons.Rounded.Computer, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Start Stream")
                    }
                    OutlinedButton(
                        onClick = { viewModel.stopScreenStream() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Stop")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.loadScreenSources() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh sources")
                    }
                    OutlinedButton(
                        onClick = { viewModel.setRemoteScreenFullscreen(true) },
                        modifier = Modifier.width(120.dp),
                    ) {
                        Icon(Icons.Rounded.Fullscreen, contentDescription = "Fullscreen")
                        Spacer(Modifier.width(6.dp))
                        Text("Full")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Game Mode", color = Mist, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text("60 fps, hidden controls, controller-forwarding.", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = { viewModel.setRemoteScreenGameMode(true) },
                        enabled = !gameMode,
                    ) {
                        Text("Enter")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(if (fullscreen) 0.dp else 18.dp))
                .background(Color.Black)
                .border(
                    if (fullscreen) 0.dp else 1.dp,
                    if (state.screenStreaming) Acid.copy(alpha = 0.65f) else Line,
                    RoundedCornerShape(if (fullscreen) 0.dp else 18.dp),
                )
                .onSizeChanged { viewSize = it }
                .pointerInput(frame?.width, frame?.height, viewSize, activeSource?.id) {
                    val currentFrame = frame ?: return@pointerInput
                    if (viewSize.width <= 0 || viewSize.height <= 0) return@pointerInput
                    val viewport = ScreenViewport(
                        viewWidth = viewSize.width.toFloat(),
                        viewHeight = viewSize.height.toFloat(),
                        sourceWidth = currentFrame.width.toFloat(),
                        sourceHeight = currentFrame.height.toFloat(),
                    )
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val start = viewport.map(down.position.x, down.position.y)
                        var lastPoint = start
                        var moved = false
                        var dragActive = false
                        var scrollActive = false
                        var lastCentroid: Offset? = null
                        if (start != null) viewModel.remoteScreenPointerMove(start.x, start.y)

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                val now = event.changes.maxOfOrNull { it.uptimeMillis } ?: down.uptimeMillis
                                if (pressed.size >= 2) {
                                    if (!scrollActive) {
                                        if (dragActive) {
                                            viewModel.pointerButton(PointerButton.Left, ButtonState.Released)
                                            dragActive = false
                                        }
                                        scrollActive = true
                                        lastCentroid = pressed.centroid()
                                    } else {
                                        val centroid = pressed.centroid()
                                        lastCentroid?.let { previous ->
                                            viewModel.scroll(
                                                dx = (previous.x - centroid.x) * 0.75f,
                                                dy = (previous.y - centroid.y) * 0.75f,
                                            )
                                        }
                                        lastCentroid = centroid
                                    }
                                } else {
                                    if (scrollActive) {
                                        viewModel.scroll(0f, 0f, finish = true)
                                        scrollActive = false
                                        lastCentroid = null
                                    }
                                    val change = pressed.firstOrNull()
                                    if (change != null) {
                                        val distance = hypot(
                                            change.position.x - down.position.x,
                                            change.position.y - down.position.y,
                                        )
                                        if (distance > tapSlop) moved = true
                                        val mapped = viewport.map(change.position.x, change.position.y)
                                        if (mapped != null) {
                                            if (moved && !dragActive && now - down.uptimeMillis > 320L) {
                                                if (state.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.pointerButton(PointerButton.Left, ButtonState.Pressed)
                                                dragActive = true
                                            }
                                            viewModel.remoteScreenPointerMove(mapped.x, mapped.y)
                                            lastPoint = mapped
                                        }
                                    }
                                }
                                event.changes.forEach { change ->
                                    if (change.changedToDown() || change.changedToUp() || change.positionChanged()) {
                                        change.consume()
                                    }
                                }
                                if (pressed.isEmpty()) break
                            }
                        } finally {
                            if (scrollActive) viewModel.scroll(0f, 0f, finish = true)
                            if (dragActive) viewModel.pointerButton(PointerButton.Left, ButtonState.Released)
                        }
                        if (!moved && start != null && lastPoint != null) {
                            if (state.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.remoteScreenClick(start.x, start.y)
                        }
                    }
                }
        ) {
            if (frame != null) {
                Image(
                    bitmap = frame.bitmap.asImageBitmap(),
                    contentDescription = "Remote screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.Computer, contentDescription = null, tint = Acid, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(state.screenStatus, color = Mist, fontWeight = FontWeight.Bold)
                    Text(activeSource?.label ?: "Select a source and start streaming", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!fullscreen || !gameMode || state.remoteScreenControlsVisible) {
                StreamStatusOverlay(state, modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
            }
            if (showFullscreenControls) {
                RemoteScreenFullscreenBar(
                    status = state.screenStatus,
                    onExit = { viewModel.setRemoteScreenFullscreen(false) },
                    onReconnect = { viewModel.startScreenStream() },
                    onKeyboard = { quickKeyboardVisible = !quickKeyboardVisible },
                    gameMode = gameMode,
                    onGameMode = { viewModel.setRemoteScreenGameMode(!gameMode) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp, start = 10.dp, end = 10.dp),
                )
            } else if (fullscreen && gameMode) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(0.36f)
                        .height(34.dp)
                        .clickable { viewModel.revealRemoteScreenControls("touch_top_handle") },
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp)
                            .width(44.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Mist.copy(alpha = 0.32f)),
                    )
                }
            }
            if (fullscreen && state.remoteScreenControlsVisible) {
                if (quickKeyboardVisible) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(14.dp),
                    ) {
                        Surface(
                            color = Graphite.copy(alpha = 0.90f),
                            contentColor = Mist,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            OutlinedTextField(
                                value = keyboardText,
                                onValueChange = { next ->
                                    viewModel.sendLiveKeyboardEdit(keyboardText, next)
                                    keyboardText = next
                                },
                                label = { Text("Quick text") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                            )
                        }
                    }
                }
            }
        }
        if (!fullscreen) {
            AnimatedVisibility(state.screenError != null) {
                ErrorStrip(state.screenError ?: "")
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        viewModel.pointerButton(PointerButton.Right, ButtonState.Pressed)
                        viewModel.pointerButton(PointerButton.Right, ButtonState.Released)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Right click")
                }
                OutlinedButton(onClick = { quickKeyboardVisible = !quickKeyboardVisible }, modifier = Modifier.weight(1f)) {
                    Text("Quick keys")
                }
            }
            AnimatedVisibility(quickKeyboardVisible) {
                OutlinedTextField(
                    value = keyboardText,
                    onValueChange = { next ->
                        viewModel.sendLiveKeyboardEdit(keyboardText, next)
                        keyboardText = next
                    },
                    label = { Text("Quick text input") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StreamStatusOverlay(state: WaypadUiState, modifier: Modifier = Modifier) {
    val stats = state.screenStreamStats
    val backend = stats.backend.takeIf { it.isNotBlank() } ?: state.screenStreamInfo?.source?.backend ?: "unknown"
    Surface(
        color = Graphite.copy(alpha = 0.82f),
        contentColor = Mist,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(state.screenStatus, color = Mist, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state.screenStreaming && (stats.deliveredFps > 0 || stats.actualFps > 0)) {
                val fpsText = if (stats.actualFps > 0 && stats.actualFps != stats.targetFps) {
                    "${stats.deliveredFps.formatFps()}/${stats.actualFps} fps (asked ${stats.targetFps})"
                } else {
                    "${stats.deliveredFps.formatFps()}/${stats.targetFps} fps"
                }
                Text("$backend · $fpsText · ${stats.averageKib} KiB/f", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RemoteScreenFullscreenBar(
    status: String,
    onExit: () -> Unit,
    onReconnect: () -> Unit,
    onKeyboard: () -> Unit,
    gameMode: Boolean,
    onGameMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Graphite.copy(alpha = 0.85f),
        contentColor = Mist,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.Rounded.FullscreenExit, contentDescription = "Exit fullscreen", tint = Mist)
            }
            Text(
                status,
                modifier = Modifier.weight(1f),
                color = Mist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
            IconButton(onClick = onReconnect) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Reconnect", tint = Mist)
            }
            IconButton(onClick = onKeyboard) {
                Icon(Icons.Rounded.Keyboard, contentDescription = "Keyboard", tint = Mist)
            }
            TextButton(onClick = onGameMode) {
                Text(if (gameMode) "Game: ON" else "Game: OFF", color = if (gameMode) Acid else Mist)
            }
            IconButton(onClick = onExit) {
                Icon(Icons.Rounded.Close, contentDescription = "Close fullscreen", tint = Mist)
            }
        }
    }
}

private fun List<androidx.compose.ui.input.pointer.PointerInputChange>.centroid(): Offset {
    val pressed = filter { it.pressed }
    if (pressed.isEmpty()) return Offset.Zero
    val x = pressed.sumOf { it.position.x.toDouble() }.toFloat() / pressed.size
    val y = pressed.sumOf { it.position.y.toDouble() }.toFloat() / pressed.size
    return Offset(x, y)
}

@Composable
private fun KeyboardScreen(viewModel: WaypadViewModel) {
    var text by remember { mutableStateOf("") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionTitle("Keyboard", "Send text and common Wayland-safe shortcuts through the daemon.")
            GlassCard {
                OutlinedTextField(
                    value = text,
                    onValueChange = { next ->
                        viewModel.sendLiveKeyboardEdit(text, next)
                        text = next
                    },
                    label = { Text("Live keyboard input") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Typing here is forwarded immediately to the focused PC window. On Hyprland without RemoteDesktop portal, ASCII text uses IPC key events and unsupported text falls back to clipboard paste.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = { text = "" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear local buffer")
                }
            }
        }
        item {
            ShortcutGrid(
                listOf(
                    "Copy" to { viewModel.shortcut("ctrl", "c") },
                    "Paste" to { viewModel.shortcut("ctrl", "v") },
                    "Undo" to { viewModel.shortcut("ctrl", "z") },
                    "Redo" to { viewModel.shortcut("ctrl", "shift", "z") },
                    "Save" to { viewModel.shortcut("ctrl", "s") },
                    "Close" to { viewModel.shortcut("ctrl", "w") },
                    "Terminal" to { viewModel.shortcut("super", "enter") },
                    "Launcher" to { viewModel.shortcut("super", "space") },
                    "Esc" to { viewModel.shortcut("esc") },
                    "Tab" to { viewModel.shortcut("tab") },
                    "Enter" to { viewModel.shortcut("enter") },
                )
            )
        }
    }
}

@Composable
private fun ControlsScreen(state: WaypadUiState, viewModel: WaypadViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Controls", "Media, volume, brightness, and gated system actions.") }
        item {
            ControlGroup("Media", state.capabilities.media) {
                ActionButton("Play/Pause") { viewModel.media("play_pause") }
                ActionButton("Previous") { viewModel.media("previous") }
                ActionButton("Next") { viewModel.media("next") }
            }
        }
        item {
            ControlGroup("Volume", state.capabilities.volume) {
                ActionButton("Volume -") { viewModel.volume("down") }
                ActionButton("Mute") { viewModel.volume("mute_toggle") }
                ActionButton("Volume +") { viewModel.volume("up") }
            }
        }
        item {
            ControlGroup("Brightness", state.capabilities.brightness) {
                ActionButton("Brightness -") { viewModel.brightness("down") }
                ActionButton("Brightness +") { viewModel.brightness("up") }
            }
        }
        item {
            ControlGroup("System", state.capabilities.lock || state.capabilities.suspend) {
                ActionButton("Lock") { viewModel.system("lock") }
                ActionButton("Suspend") { viewModel.system("suspend") }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: WaypadUiState, viewModel: WaypadViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionTitle("Settings", "Stream quality, input feel, and app preferences.") }

        // Stream Performance
        item {
            GlassCard {
                Text("Stream Performance", color = Mist, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "These apply the next time you start a stream. Changing them does not affect a live stream until you restart it.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))

                Text("Profile", color = Mist, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                StreamProfile.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { profile ->
                            val selected = state.streamSettings.profile == profile
                            val color = if (selected) Acid else Mist
                            Surface(
                                color = if (selected) Acid.copy(alpha = 0.15f) else Graphite.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.setStreamProfile(profile) },
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(profile.label, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text("${profile.defaultFps} fps · ${profile.defaultMaxDimension}p · Q${profile.defaultQuality}", color = Muted, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }

                SettingChips(
                    label = "Target FPS",
                    options = listOf(30 to "30", 45 to "45", 60 to "60"),
                    selected = state.streamSettings.maxFps,
                    onSelect = { viewModel.setStreamMaxFps(it) },
                )
                SettingChips(
                    label = "Max Resolution",
                    options = listOf(1280 to "720p-1280", 1600 to "1080p-1600", 2400 to "1440p-2400", 3840 to "4K-3840"),
                    selected = state.streamSettings.maxDimension,
                    onSelect = { viewModel.setStreamMaxDimension(it) },
                )
                SettingChips(
                    label = "JPEG Quality",
                    options = listOf(35 to "Low 35", 52 to "Balanced 52", 70 to "Good 70", 86 to "High 86", 92 to "Best 92"),
                    selected = state.streamSettings.jpegQuality,
                    onSelect = { viewModel.setStreamJpegQuality(it) },
                )

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show live stats overlay", color = Mist, style = MaterialTheme.typography.bodySmall)
                        Text("FPS, backend, and bitrate on the stream screen.", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = state.streamSettings.showStats, onCheckedChange = { viewModel.toggleStreamStats() })
                }
            }
        }

        // Input Feel
        item {
            GlassCard {
                Text("Input Feel", color = Mist, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Haptic feedback", color = Mist, style = MaterialTheme.typography.bodySmall)
                        Text("Vibration on taps and drag lock.", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = state.haptics, onCheckedChange = { viewModel.toggleHaptics() })
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Game Mode", color = Mist, style = MaterialTheme.typography.bodySmall)
                        Text("Fullscreen, 60 fps, hidden UI, controller-forwarding active.", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(
                        checked = state.remoteScreenGameMode,
                        onCheckedChange = { viewModel.setRemoteScreenGameMode(it) },
                    )
                }
            }
        }

        // Connection
        item {
            GlassCard {
                Text("Connection", color = Mist, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { viewModel.go(Screen.TrustedHosts) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Trusted hosts")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.disconnect() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Disconnect")
                }
            }
        }
    }
}

@Composable
private fun <T> SettingChips(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) where T : Comparable<T> {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, color = Mist, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { (value, text) ->
                val isSelected = value == selected
                Surface(
                    color = if (isSelected) Acid.copy(alpha = 0.15f) else Graphite.copy(alpha = 0.6f),
                    contentColor = if (isSelected) Acid else Mist,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(value) },
                ) {
                    Text(
                        text,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrustedHostsScreen(state: WaypadUiState, viewModel: WaypadViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionTitle("Trusted Hosts", "Pinned host identities stored with Android Keystore protected encryption.")
            Button(onClick = { viewModel.startDiscovery() }, modifier = Modifier.fillMaxWidth()) { Text("Discover new host") }
        }
        items(state.trustedHosts) { host ->
            TrustedHostCard(host, onConnect = { viewModel.connect(host) }, onRemove = { viewModel.removeTrustedHost(host.id) })
        }
    }
}

@Composable
private fun TroubleshootingScreen(state: WaypadUiState, viewModel: WaypadViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionTitle("Diagnostics", "Wayland support is capability driven. Unsupported actions fail with a host reason.")
            Button(onClick = { viewModel.refreshCapabilities() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Refresh capabilities")
            }
        }
        item {
            GlassCard {
                DiagnosticLine("Connection", state.connectionState.name)
                DiagnosticLine("Input backend", state.capabilities.inputBackend)
                DiagnosticLine("Input status", state.capabilities.inputReason)
                DiagnosticLine("External pointer", yesNo(state.capabilities.externalPointerSupported))
                DiagnosticLine("External keyboard", yesNo(state.capabilities.externalKeyboardSupported))
                DiagnosticLine("Controller forwarding", yesNo(state.capabilities.externalControllerSupported))
                DiagnosticLine("External input status", state.externalInputStatus)
                DiagnosticLine("Route backend", state.capabilities.routeBackend)
                DiagnosticLine("LAN direct", yesNo(state.capabilities.lanDirectSupported))
                DiagnosticLine("Public direct", yesNo(state.capabilities.publicDirectSupported))
                DiagnosticLine("Relay", yesNo(state.capabilities.relaySupported))
                DiagnosticLine("Connectivity", state.capabilities.connectivityReason)
                DiagnosticLine("Capture backend", state.capabilities.captureBackend)
                DiagnosticLine("Capture status", state.capabilities.captureReason)
                DiagnosticLine("Volume", yesNo(state.capabilities.volume))
                DiagnosticLine("Brightness", yesNo(state.capabilities.brightness))
                DiagnosticLine("Clipboard", yesNo(state.capabilities.clipboard))
                DiagnosticLine("Lock", yesNo(state.capabilities.lock))
            }
        }
        item {
            GlassCard {
                Text("Stream diagnostics", color = Mist, fontWeight = FontWeight.Bold)
                val stats = state.screenStreamStats
                DiagnosticLine("Streaming", if (state.screenStreaming) "yes" else "no")
                DiagnosticLine("Backend", stats.backend)
                DiagnosticLine("Target FPS", "${stats.targetFps}")
                DiagnosticLine("Actual FPS (host)", "${stats.actualFps}")
                DiagnosticLine("Delivered FPS", stats.deliveredFps.formatFps())
                DiagnosticLine("Avg frame size", "${stats.averageKib} KiB")
                DiagnosticLine("Total frames", "${stats.receivedFrames}")
                DiagnosticLine("Stream status", state.screenStatus)
                DiagnosticLine("Stream error", state.screenError ?: "none")
            }
        }
        item {
            GlassCard {
                Text("Android input devices", color = Mist, fontWeight = FontWeight.Bold)
                if (state.externalInputDevices.isEmpty()) {
                    Text("No external keyboard, mouse, touchpad, or controller detected by Android.", color = Muted)
                } else {
                    state.externalInputDevices.forEach { device ->
                        DiagnosticLine(device.name, device.displayClasses)
                    }
                }
            }
        }
        item {
            GlassCard {
                Text("Host-side checks", color = Mist, fontWeight = FontWeight.Bold)
                Text("Run `waypad-daemon doctor` and inspect `journalctl --user -u waypad-daemon -f` on the Linux host.", color = Muted)
            }
        }
    }
}

@Composable
private fun TrustedHostCard(host: TrustedHost, onConnect: () -> Unit, onRemove: () -> Unit) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Computer, contentDescription = null, tint = Acid)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(host.hostName, color = Mist, fontWeight = FontWeight.Bold)
                Text("${host.address}:${host.port}", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text(shortFingerprint(host.fingerprint), color = Muted, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onConnect, modifier = Modifier.weight(1f)) { Text("Connect") }
            OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f)) { Text("Remove") }
        }
    }
}

@Composable
private fun ControlGroup(title: String, enabled: Boolean, content: @Composable RowScope.() -> Unit) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Mist, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            StatusPill(if (enabled) "available" else "unsupported")
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun RowScope.ActionButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text(label) }
}

@Composable
private fun ShortcutGrid(items: List<Pair<String, () -> Unit>>) {
    GlassCard {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, action) ->
                    OutlinedButton(onClick = action, modifier = Modifier.weight(1f)) { Text(label) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ClickButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ElevatedButton(onClick = onClick, modifier = modifier.height(56.dp)) {
        Text(label)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
        Text(title, color = Mist, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = 0.88f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun StatusPill(label: String) {
    Surface(color = Acid.copy(alpha = 0.13f), contentColor = Acid, shape = RoundedCornerShape(999.dp)) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = Muted, modifier = Modifier.weight(0.38f))
        Text(value, color = Mist, modifier = Modifier.weight(0.62f))
    }
}

private fun yesNo(value: Boolean) = if (value) "available" else "unsupported"

private fun shortFingerprint(value: String): String =
    if (value.length > 31) value.take(19) + "..." + value.takeLast(9) else value
