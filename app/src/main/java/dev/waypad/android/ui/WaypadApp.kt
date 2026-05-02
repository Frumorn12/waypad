package dev.waypad.android.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.waypad.android.Screen
import dev.waypad.android.WaypadUiState
import dev.waypad.android.WaypadViewModel
import dev.waypad.android.R
import dev.waypad.android.core.model.ButtonState
import dev.waypad.android.core.model.ConnectionState
import dev.waypad.android.core.model.DiscoveredHost
import dev.waypad.android.core.model.PointerButton
import dev.waypad.android.core.model.TrustedHost
import kotlin.math.abs

@Composable
fun WaypadApp(viewModel: WaypadViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WaypadTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Ink, Color(0xFF0D130D), Ink)))
        ) {
            AtmosphericBackground()
            Scaffold(
                containerColor = Color.Transparent,
                topBar = { TopStatus(state, viewModel) },
                bottomBar = {
                    if (state.connectionState == ConnectionState.Connected) {
                        BottomRail(state.screen, viewModel)
                    }
                },
            ) { padding ->
                Box(
                    Modifier
                        .padding(padding)
                        .padding(horizontal = 18.dp)
                        .fillMaxSize()
                ) {
                    when (state.screen) {
                        Screen.Onboarding -> OnboardingScreen(viewModel)
                        Screen.Discovery -> DiscoveryScreen(state, viewModel)
                        Screen.Pairing -> PairingScreen(state, viewModel)
                        Screen.Remote -> RemoteScreen(state, viewModel)
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
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionTitle("Host Discovery", "UDP LAN discovery with manual IP fallback.")
            Button(onClick = { viewModel.startDiscovery() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan again")
            }
        }
        items(state.discoveredHosts) { host ->
            HostCard(host) { viewModel.selectHost(host) }
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
            StatusPill(if (host.inputSupported) host.inputBackend else "Input blocked")
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusPill(if (state.capabilities.inputSupported) "Input: ${state.capabilities.inputBackend}" else "Input blocked")
            TextButton(onClick = { viewModel.prepareInput() }) {
                Text(if (state.capabilities.inputBackend == "wayland-portal") "Approve portal" else "Refresh input")
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(34.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1B211B), Color(0xFF101410))))
                .border(1.dp, Line, RoundedCornerShape(34.dp))
                .pointerInput(Unit) {
                    try {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var lastCentroid = down.position
                            var maxPointerCount = 1
                            var movedBeyondTap = false
                            var scrollActive = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break

                                maxPointerCount = maxOf(maxPointerCount, pressed.size)
                                val centroid = Offset(
                                    pressed.sumOf { it.position.x.toDouble() }.toFloat() / pressed.size,
                                    pressed.sumOf { it.position.y.toDouble() }.toFloat() / pressed.size,
                                )
                                val delta = centroid - lastCentroid
                                val total = centroid - down.position
                                if (abs(total.x) + abs(total.y) > tapSlop) {
                                    movedBeyondTap = true
                                }

                                if (pressed.size >= 2) {
                                    scrollActive = true
                                    if (abs(delta.y) > 0.05f) viewModel.scroll(0f, delta.y)
                                } else if (abs(delta.x) + abs(delta.y) > 0.05f) {
                                    viewModel.pointerMove(delta.x, delta.y)
                                }

                                lastCentroid = centroid
                            }

                            if (scrollActive) viewModel.scroll(0f, 0f, finish = true)
                            if (!movedBeyondTap && maxPointerCount == 1) {
                                if (state.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.pointerButton(PointerButton.Left, ButtonState.Pressed)
                                viewModel.pointerButton(PointerButton.Left, ButtonState.Released)
                            }
                            if (!currentDragLocked) {
                                viewModel.releasePointerButtons()
                            }
                        }
                    } finally {
                        viewModel.scroll(0f, 0f, finish = true)
                        viewModel.releasePointerButtons()
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
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Settings", "Local app preferences and trust management.") }
        item {
            GlassCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Haptic feedback", color = Mist, fontWeight = FontWeight.Bold)
                        Text("Subtle feedback for taps and drag mode.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.haptics, onCheckedChange = { viewModel.toggleHaptics() })
                }
            }
        }
        item {
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
                DiagnosticLine("Volume", yesNo(state.capabilities.volume))
                DiagnosticLine("Brightness", yesNo(state.capabilities.brightness))
                DiagnosticLine("Clipboard", yesNo(state.capabilities.clipboard))
                DiagnosticLine("Lock", yesNo(state.capabilities.lock))
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
