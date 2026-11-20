package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AppPreferences
import com.example.data.local.BlockedUserEntity
import com.example.data.local.ThemePreference
import com.example.data.local.UserSession
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldSignal

@Composable
fun SettingsTab(
    session: UserSession,
    appPreferences: AppPreferences,
    blockedUsers: List<BlockedUserEntity>,
    onUpdateProfile: (displayName: String, bio: String) -> Unit,
    onChangeUsername: (String) -> Unit,
    onSetTheme: (ThemePreference) -> Unit,
    onSetNotifications: (Boolean) -> Unit,
    onSetReadReceipts: (Boolean) -> Unit,
    onSetShowOnlineStatus: (Boolean) -> Unit,
    onSetLowData: (Boolean) -> Unit,
    onUnblockUser: (String) -> Unit,
    onSetUseBackendServer: (Boolean) -> Unit = {},
    onSaveServerConfig: (String, String, String, String, String, Boolean) -> Unit,
    onClearCache: () -> Unit,
    onDeleteAccount: () -> Unit,
    onCopyFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var showEditProfile by remember { mutableStateOf(false) }
    var showChangeUsername by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRecoveryKey by remember { mutableStateOf(false) }

    val openAppSystemSettings = {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Identity Card
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        UserAvatar(session.username, session.avatarColor, true, size = 54.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.displayName.ifBlank { session.username },
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "@${session.username}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (session.bio.isNotBlank()) {
                                Text(
                                    text = session.bio,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = { showEditProfile = true },
                            modifier = Modifier.testTag("settings_edit_profile")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit display name")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showChangeUsername = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AlternateEmail, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Change Handle")
                        }
                        OutlinedButton(
                            onClick = { showRecoveryKey = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Recovery Key")
                        }
                    }
                }
            }
        }

        // Appearance
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Theme", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.entries.forEach { pref ->
                            FilterChip(
                                selected = appPreferences.themePreference == pref,
                                onClick = { onSetTheme(pref) },
                                label = { Text(pref.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }
        }

        // Notifications & Privacy
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = EmeraldSignal)
                        Text("Notifications & Privacy", style = MaterialTheme.typography.titleMedium)
                    }
                    SettingSwitchRow(
                        title = "Call & Message Notifications",
                        subtitle = "Show alerts for incoming calls and real-time texts",
                        checked = appPreferences.notificationsEnabled,
                        onCheckedChange = onSetNotifications
                    )
                    SettingSwitchRow(
                        title = "Send Read Receipts",
                        subtitle = "Let contacts see when you read their messages",
                        checked = appPreferences.readReceiptsEnabled,
                        onCheckedChange = onSetReadReceipts
                    )
                    SettingSwitchRow(
                        title = "Broadcast Online Status",
                        subtitle = "Display live online presence on your profile",
                        checked = appPreferences.showOnlineStatus,
                        onCheckedChange = onSetShowOnlineStatus
                    )
                    OutlinedButton(
                        onClick = { showBlockedDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Blocked Users (${blockedUsers.size})")
                    }
                }
            }
        }

        // Permissions & Data Usage
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Permissions & Data Usage", style = MaterialTheme.typography.titleMedium)
                    }
                    PermissionStatusRow("Camera (Video Calls)", hasPermission(context, Manifest.permission.CAMERA))
                    PermissionStatusRow("Microphone (Voice & Video)", hasPermission(context, Manifest.permission.RECORD_AUDIO))
                    val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    } else true
                    PermissionStatusRow("Notifications", notifGranted)
                    PermissionStatusRow("Screen Sharing (MediaProjection)", true, customLabel = "On-Demand Consent")

                    OutlinedButton(onClick = openAppSystemSettings, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Manage System App Permissions")
                    }

                    SettingSwitchRow(
                        title = "Low-Data Adaptive Video",
                        subtitle = "Caps WebRTC video at 480p / 350 kbps on slow Wi-Fi",
                        checked = appPreferences.lowDataMode,
                        onCheckedChange = onSetLowData
                    )
                }
            }
        }

        // Infrastructure & Storage
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("In-App Storage, STUN/TURN & Server", style = MaterialTheme.typography.titleMedium)
                    }
                    SettingSwitchRow(
                        title = "Use Optional Backend Server",
                        subtitle = if (appPreferences.useBackendServer) {
                            "Syncing with ${appPreferences.serverBaseUrl}"
                        } else {
                            "Off: user profile, contacts & chats save in-app only (Zero Server)"
                        },
                        checked = appPreferences.useBackendServer,
                        onCheckedChange = onSetUseBackendServer
                    )
                    Text(
                        text = "STUN: ${appPreferences.stunUrl}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showServerDialog = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ICE & Server")
                        }
                        OutlinedButton(onClick = onClearCache, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clear Cache")
                        }
                    }
                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralAlert),
                        modifier = Modifier.fillMaxWidth().testTag("settings_delete_account")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete In-App Identity & Data")
                    }
                }
            }
        }

        // About WorldLink
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
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("WorldLink v1.0.0", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "100% Free, Zero Bluetooth, Zero Ads, In-App User Storage & Wi-Fi P2P / Nearby communication.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showEditProfile) {
        var displayName by remember { mutableStateOf(session.displayName) }
        var bio by remember { mutableStateOf(session.bio) }
        AlertDialog(
            onDismissRequest = { showEditProfile = false },
            title = { Text("Edit Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("Status / Bio") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateProfile(displayName, bio)
                    showEditProfile = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditProfile = false }) { Text("Cancel") } }
        )
    }

    if (showChangeUsername) {
        var newHandle by remember { mutableStateOf(session.username) }
        AlertDialog(
            onDismissRequest = { showChangeUsername = false },
            title = { Text("Change Unique @username") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (appPreferences.useBackendServer) {
                            "Your new username is verified against the optional backend database."
                        } else {
                            "Your new @username is saved locally inside this app."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newHandle,
                        onValueChange = { newHandle = it.replace("@", "").filter { ch -> ch.isLetterOrDigit() || ch == '_' } },
                        label = { Text("New @username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onChangeUsername(newHandle)
                    showChangeUsername = false
                }) { Text("Verify & Change") }
            },
            dismissButton = { TextButton(onClick = { showChangeUsername = false }) { Text("Cancel") } }
        )
    }

    if (showRecoveryKey) {
        AlertDialog(
            onDismissRequest = { showRecoveryKey = false },
            title = { Text("Device Recovery Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Keep this key safe to recover @${session.username} on another phone without email or SIM verification:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = session.recoveryKey.ifBlank { "Recovered session (stored on original device)" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            },
            confirmButton = {
                if (session.recoveryKey.isNotBlank()) {
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(session.recoveryKey))
                        onCopyFeedback("Recovery Key copied to clipboard")
                        showRecoveryKey = false
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Key")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showRecoveryKey = false }) { Text("Close") } }
        )
    }

    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = { Text("Blocked Users") },
            text = {
                if (blockedUsers.isEmpty()) {
                    Text("You haven't blocked any users.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        blockedUsers.forEach { b ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("@${b.username}", style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { onUnblockUser(b.userId) }) { Text("Unblock") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBlockedDialog = false }) { Text("Done") } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account Permanently?") },
            text = {
                Text("This permanently deletes @${session.username}, your device sessions, contacts, messages, and call records from the WorldLink server and this device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralAlert)
                ) { Text("Delete Everything") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showServerDialog) {
        ServerConfigDialog(
            currentPrefs = appPreferences,
            statusNote = null,
            onDismiss = { showServerDialog = false },
            onSaveAndTest = { srv, stun, turn, user, pass, useSrv ->
                onSaveServerConfig(srv, stun, turn, user, pass, useSrv)
                showServerDialog = false
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    granted: Boolean,
    customLabel: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            text = customLabel ?: if (granted) "Granted" else "Needs Permission",
            style = MaterialTheme.typography.labelSmall,
            color = if (granted) EmeraldSignal else CoralAlert
        )
    }
}
