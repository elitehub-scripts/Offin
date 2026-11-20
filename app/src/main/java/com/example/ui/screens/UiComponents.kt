package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.AppPreferences
import com.example.data.remote.SignalingConnectionState
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldSignal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun parseHexColor(hex: String, fallback: Color = Color(0xFF00E5FF)): Color {
    return try {
        val clean = hex.removePrefix("#")
        Color(("FF$clean").toLong(16))
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun UserAvatar(
    username: String,
    avatarColorHex: String,
    isOnline: Boolean,
    size: Dp = 46.dp,
    showPresenceDot: Boolean = true
) {
    val accent = parseHexColor(avatarColorHex)
    val initial = username.trim().removePrefix("@").firstOrNull()?.uppercaseChar()?.toString() ?: "W"

    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f))
                .border(1.5.dp, accent.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
        if (showPresenceDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) EmeraldSignal else MaterialTheme.colorScheme.outline)
            )
        }
    }
}

@Composable
fun ConnectionStatusPill(
    signalingState: SignalingConnectionState,
    hasInternet: Boolean,
    useBackendServer: Boolean = false,
    onClick: () -> Unit
) {
    val (label, tint, icon) = when {
        !hasInternet -> Triple("Offline Nearby", CoralAlert, Icons.Default.CloudOff)
        !useBackendServer -> Triple("In-App + P2P", EmeraldSignal, Icons.Default.CloudDone)
        signalingState == SignalingConnectionState.CONNECTED -> Triple("Global Live", EmeraldSignal, Icons.Default.CloudDone)
        signalingState == SignalingConnectionState.CONNECTING ||
            signalingState == SignalingConnectionState.RECONNECTING -> Triple("Syncing", AmberWarn, Icons.Default.Sync)
        else -> Triple("Server Offline", CoralAlert, Icons.Default.CloudOff)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = 0.14f),
        modifier = Modifier.testTag("connection_status_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ServerConfigDialog(
    currentPrefs: AppPreferences,
    statusNote: String?,
    onDismiss: () -> Unit,
    onSaveAndTest: (serverUrl: String, stunUrl: String, turnUrl: String, turnUser: String, turnPass: String, useBackendServer: Boolean) -> Unit
) {
    var useBackendServer by remember(currentPrefs.useBackendServer) { mutableStateOf(currentPrefs.useBackendServer) }
    var serverUrl by remember(currentPrefs.serverBaseUrl) { mutableStateOf(currentPrefs.serverBaseUrl) }
    var stunUrl by remember(currentPrefs.stunUrl) { mutableStateOf(currentPrefs.stunUrl) }
    var turnUrl by remember(currentPrefs.turnUrl) { mutableStateOf(currentPrefs.turnUrl) }
    var turnUser by remember(currentPrefs.turnUsername) { mutableStateOf(currentPrefs.turnUsername) }
    var turnPass by remember(currentPrefs.turnCredential) { mutableStateOf(currentPrefs.turnCredential) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Dns, contentDescription = null) },
        title = { Text("Storage & WebRTC ICE Config") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use Optional Backend Server", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (useBackendServer) "Syncs with your self-hosted backend" else "Off: saves profile, contacts & chats in-app only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useBackendServer,
                        onCheckedChange = { useBackendServer = it }
                    )
                }
                if (useBackendServer) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Backend Server URL") },
                        placeholder = { Text("https://worldlink.yourdomain.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("server_url_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { serverUrl = "http://10.0.2.2:8080" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Emulator", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { serverUrl = "http://192.168.1.100:8080" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("LAN Preset", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                OutlinedTextField(
                    value = stunUrl,
                    onValueChange = { stunUrl = it },
                    label = { Text("Free Public STUN Server") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = turnUrl,
                    onValueChange = { turnUrl = it },
                    label = { Text("TURN Relay URL (optional)") },
                    placeholder = { Text("turn:your-server-ip:3478") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (turnUrl.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = turnUser,
                            onValueChange = { turnUser = it },
                            label = { Text("TURN User") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = turnPass,
                            onValueChange = { turnPass = it },
                            label = { Text("TURN Secret") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (!statusNote.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = statusNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveAndTest(serverUrl, stunUrl, turnUrl, turnUser, turnPass, useBackendServer) },
                modifier = Modifier.testTag("save_server_config_button")
            ) {
                Text(if (useBackendServer) "Save & Test" else "Save In-App")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())

fun formatIsoTime(iso: String, includeDate: Boolean = false): String {
    return try {
        val instant = Instant.parse(iso)
        if (includeDate) dateTimeFormatter.format(instant) else timeFormatter.format(instant)
    } catch (_: Exception) {
        iso.takeLast(8)
    }
}

fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

fun requiredCallPermissions(includeCamera: Boolean): Array<String> {
    val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
    if (includeCamera) list.add(Manifest.permission.CAMERA)
    return list.toTypedArray()
}

fun requiredNearbyPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
