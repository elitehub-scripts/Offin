package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AppPreferences
import com.example.data.local.ContactEntity
import com.example.data.local.NearbyMessageEntity
import com.example.data.remote.UserDto
import com.example.nearby.NearbyPeer
import com.example.nearby.NearbyRangeStatus
import com.example.nearby.NearbyTransport
import com.example.rtc.ActiveCallState
import com.example.rtc.CallKind
import com.example.rtc.CallPhase
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldSignal

@Composable
fun ScreenShareTab(
    contacts: List<ContactEntity>,
    activeCall: ActiveCallState,
    appPreferences: AppPreferences,
    onToggleHighFps: (Boolean) -> Unit,
    onToggleLowData: (Boolean) -> Unit,
    onStartScreenShareCall: (UserDto) -> Unit,
    onTriggerSystemScreenCapture: () -> Unit,
    onStopScreenShare: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Live Sharing Banner
        if (activeCall.isScreenSharing) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CoralAlert.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.CastConnected,
                            contentDescription = "Screen sharing active",
                            tint = CoralAlert,
                            modifier = Modifier.size(30.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Live Screen Sharing Active",
                                style = MaterialTheme.typography.titleMedium,
                                color = CoralAlert
                            )
                            Text(
                                text = "Streaming your display to @${activeCall.peer.username}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = onStopScreenShare,
                            colors = ButtonDefaults.buttonColors(containerColor = CoralAlert),
                            modifier = Modifier.testTag("screenshare_tab_stop_button")
                        ) {
                            Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop")
                        }
                    }
                }
            }
        } else if (activeCall.phase == CallPhase.ACTIVE) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.PresentToAll, contentDescription = null, modifier = Modifier.size(28.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("In Call with @${activeCall.peer.username}", style = MaterialTheme.typography.titleMedium)
                            Text("Share your Android screen right inside the active call.", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = onTriggerSystemScreenCapture,
                            modifier = Modifier.testTag("screenshare_active_call_start")
                        ) {
                            Text("Share Now")
                        }
                    }
                }
            }
        }

        // Privacy & Permission Card
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = EmeraldSignal)
                        Text("Android MediaProjection Privacy", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "WorldLink never captures your screen silently. Starting a screen share always shows Android's official system consent prompt, displays a persistent status bar chip & notification, and automatically pauses if you lock your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Adaptive Stream Quality Controls
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Adaptive Screen Stream Quality", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("High Frame Rate (30 FPS)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Disable for crisp 15 FPS battery & Wi-Fi bandwidth savings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = appPreferences.screenShareHighFps,
                            onCheckedChange = onToggleHighFps
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Low-Data Adaptive Cap", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Adapts capture to 720p @ 350 kbps for unstable Wi-Fi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = appPreferences.lowDataMode,
                            onCheckedChange = onToggleLowData
                        )
                    }
                }
            }
        }

        // Start Screen Share Call with a Contact
        item {
            Text(
                text = "Start Screen-Share Call With Contact",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (contacts.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Add a contact in the Contacts tab, or start a voice/video call and tap Share Screen directly from the call controls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(contacts, key = { it.userId }) { c ->
                val user = UserDto(
                    id = c.userId,
                    username = c.username,
                    displayName = c.displayName,
                    avatarColor = c.avatarColor,
                    isOnline = c.isOnline
                )
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        UserAvatar(c.username, c.avatarColor, c.isOnline, size = 44.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.displayName.ifBlank { c.username }, style = MaterialTheme.typography.titleMedium)
                            Text("@${c.username}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Button(
                            onClick = { onStartScreenShareCall(user) },
                            modifier = Modifier.testTag("start_screenshare_${c.username}")
                        ) {
                            Icon(Icons.Default.PresentToAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NearbyTab(
    isScanning: Boolean,
    statusBanner: String,
    peers: List<NearbyPeer>,
    selectedPeer: NearbyPeer?,
    nearbyMessages: List<NearbyMessageEntity>,
    onStartDiscovery: (hasWifi: Boolean) -> Unit,
    onStopDiscovery: () -> Unit,
    onSelectPeer: (NearbyPeer?) -> Unit,
    onConnectPeer: (NearbyPeer) -> Unit,
    onSendNearbyMessage: (NearbyPeer, String) -> Unit
) {
    val context = LocalContext.current
    val nearbyPermissions = remember { requiredNearbyPermissions() }

    val checkWifiPerm: () -> Boolean = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onStartDiscovery(checkWifiPerm())
    }

    if (selectedPeer != null) {
        BackHandler { onSelectPeer(null) }
        NearbyChatPanel(
            peer = selectedPeer,
            messages = nearbyMessages,
            onBack = { onSelectPeer(null) },
            onSend = { text -> onSendNearbyMessage(selectedPeer, text) }
        )
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "pulse"
    )
    val radarColor = MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Offline Range Honesty Banner
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.WifiOff, contentDescription = null, tint = EmeraldSignal)
                    Column {
                        Text("Offline Wi-Fi Nearby (Zero Bluetooth)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Communicate without internet or Bluetooth via Wi-Fi Direct and Local Wi-Fi mDNS (~30–100m range). To call another country without any server, connect over Wi-Fi using Direct P2P Link in Contacts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Radar Control Card
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = radarColor.copy(alpha = 0.16f),
                                radius = size.minDimension / 2f,
                                style = Stroke(width = 3f)
                            )
                            if (isScanning) {
                                drawCircle(
                                    color = radarColor.copy(alpha = 1f - pulse),
                                    radius = (size.minDimension / 2f) * pulse,
                                    style = Stroke(width = 5f)
                                )
                            }
                        }
                        Icon(
                            if (isScanning) Icons.Default.WifiTethering else Icons.Default.Radar,
                            contentDescription = "Nearby Radar",
                            tint = if (isScanning) EmeraldSignal else radarColor,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Text(statusBanner, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!isScanning) {
                            Button(
                                onClick = {
                                    if (checkWifiPerm()) {
                                        onStartDiscovery(true)
                                    } else {
                                        permLauncher.launch(nearbyPermissions)
                                    }
                                },
                                modifier = Modifier.testTag("nearby_start_scan_button")
                            ) {
                                Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Scan Wi-Fi Nearby")
                            }
                        } else {
                            OutlinedButton(
                                onClick = onStopDiscovery,
                                modifier = Modifier.testTag("nearby_stop_scan_button")
                            ) {
                                Text("Pause Discovery")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Discovered Local Devices (${peers.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (peers.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (isScanning) {
                                "Listening for WorldLink peers on Wi-Fi Direct & same Wi-Fi/Hotspot mDNS (Bluetooth not used)..."
                            } else {
                                "Tap 'Scan Wi-Fi Nearby' on two nearby Android devices to discover and chat offline."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(peers, key = { it.key }) { peer ->
                val (rangeLabel, rangeColor) = when (peer.rangeStatus) {
                    NearbyRangeStatus.IN_RANGE -> "In Range" to EmeraldSignal
                    NearbyRangeStatus.WEAK_SIGNAL -> "Weak Signal" to AmberWarn
                    NearbyRangeStatus.OUT_OF_RANGE -> "Out of Range" to CoralAlert
                }
                val transportLabel = when (peer.transport) {
                    NearbyTransport.LAN_NSD -> "Local Wi-Fi"
                    NearbyTransport.WIFI_DIRECT -> "Wi-Fi Direct"
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPeer(peer) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(rangeColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Devices, contentDescription = null, tint = rangeColor)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("@${peer.username}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "$transportLabel • $rangeLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = rangeColor
                            )
                        }
                        if (peer.rangeStatus != NearbyRangeStatus.OUT_OF_RANGE) {
                            if (peer.transport == NearbyTransport.WIFI_DIRECT && !peer.isConnected) {
                                OutlinedButton(onClick = { onConnectPeer(peer) }) {
                                    Text("Connect")
                                }
                            } else {
                                Button(onClick = { onSelectPeer(peer) }) {
                                    Text("Chat")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyChatPanel(
    peer: NearbyPeer,
    messages: List<NearbyMessageEntity>,
    onBack: () -> Unit,
    onSend: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("@${peer.username} (Nearby)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when (peer.rangeStatus) {
                            NearbyRangeStatus.IN_RANGE -> "Direct local link active • No internet used"
                            NearbyRangeStatus.WEAK_SIGNAL -> "Weak local wireless signal"
                            NearbyRangeStatus.OUT_OF_RANGE -> "Out of local wireless range"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (peer.rangeStatus == NearbyRangeStatus.OUT_OF_RANGE) CoralAlert else EmeraldSignal
                    )
                }
                if (peer.isConnected) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Connected", tint = EmeraldSignal)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (msg.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (msg.isOutgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(msg.body, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = msg.transport,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Send offline local message...") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            onSend(draft)
                            draft = ""
                        }
                    },
                    enabled = peer.rangeStatus != NearbyRangeStatus.OUT_OF_RANGE
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send nearby message")
                }
            }
        }
    }
}
