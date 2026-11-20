package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CallRecordEntity
import com.example.data.local.ContactEntity
import com.example.data.remote.UserDto
import com.example.rtc.CallKind
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldSignal

@Composable
fun CallsTab(
    calls: List<CallRecordEntity>,
    onCallBack: (UserDto, CallKind) -> Unit
) {
    var filter by remember { mutableStateOf("ALL") }
    val filteredCalls = remember(calls, filter) {
        when (filter) {
            "INCOMING" -> calls.filter { it.direction == "INCOMING" }
            "OUTGOING" -> calls.filter { it.direction == "OUTGOING" }
            "MISSED" -> calls.filter { it.status == "MISSED" || it.status == "DECLINED" }
            else -> calls
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "All", "INCOMING" to "Incoming", "OUTGOING" to "Outgoing", "MISSED" to "Missed")
                    .forEach { (key, label) ->
                        item {
                            FilterChip(
                                selected = filter == key,
                                onClick = { filter = key },
                                label = { Text(label) }
                            )
                        }
                    }
            }
        }

        if (filteredCalls.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(stringResource(R.string.empty_calls_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.empty_calls_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredCalls, key = { it.id }) { call ->
                val isMissed = call.status == "MISSED" || call.status == "DECLINED"
                val peer = UserDto(
                    id = call.peerId,
                    username = call.peerUsername,
                    displayName = call.peerDisplayName,
                    avatarColor = call.peerAvatarColor
                )
                val kind = CallKind.entries.firstOrNull { it.name == call.callType } ?: CallKind.AUDIO

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
                        UserAvatar(
                            username = call.peerUsername,
                            avatarColorHex = call.peerAvatarColor,
                            isOnline = false,
                            size = 44.dp,
                            showPresenceDot = false
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "@${call.peerUsername}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isMissed) CoralAlert else MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val dirIcon = when {
                                    isMissed -> Icons.AutoMirrored.Filled.CallMissed
                                    call.direction == "INCOMING" -> Icons.AutoMirrored.Filled.CallReceived
                                    else -> Icons.AutoMirrored.Filled.CallMade
                                }
                                Icon(
                                    dirIcon,
                                    contentDescription = call.direction,
                                    tint = if (isMissed) CoralAlert else EmeraldSignal,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${call.callType.lowercase().replace('_', ' ')} • ${formatDuration(call.durationSeconds)} • ${formatIsoTime(call.startedAt, includeDate = true)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { onCallBack(peer, CallKind.AUDIO) }) {
                            Icon(Icons.Default.Call, contentDescription = "Voice Call")
                        }
                        IconButton(onClick = { onCallBack(peer, if (kind == CallKind.AUDIO) CallKind.VIDEO else kind) }) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Call")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsTab(
    contacts: List<ContactEntity>,
    searchResults: List<UserDto>,
    isSearching: Boolean,
    onSearchQuery: (String) -> Unit,
    onAddContact: (UserDto) -> Unit,
    onRemoveContact: (String) -> Unit,
    onOpenChat: (UserDto) -> Unit,
    onStartCall: (UserDto, CallKind) -> Unit,
    onBlockUser: (UserDto) -> Unit,
    onReportUser: (UserDto, String, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedProfile by remember { mutableStateOf<UserDto?>(null) }
    var reportTarget by remember { mutableStateOf<UserDto?>(null) }

    val contactIds = remember(contacts) { contacts.map { it.userId }.toSet() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onSearchQuery(it)
                },
                label = { Text("Discover Global Users by @username") },
                placeholder = { Text("Search @Mahid_2026...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (isSearching) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("global_user_search_input")
            )
        }

        if (query.isNotBlank()) {
            item {
                Text(
                    text = "Global Directory Results",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (searchResults.isEmpty() && !isSearching) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No users found matching \"$query\". Check exact spelling or ensure the WorldLink backend is reachable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(searchResults, key = { "search_${it.id}" }) { user ->
                    val isContact = user.id in contactIds
                    UserDirectoryRow(
                        user = user,
                        isContact = isContact,
                        onRowClick = { selectedProfile = user },
                        onToggleContact = {
                            if (isContact) onRemoveContact(user.id) else onAddContact(user)
                        },
                        onMessage = { onOpenChat(user) },
                        onVoiceCall = { onStartCall(user, CallKind.AUDIO) },
                        onVideoCall = { onStartCall(user, CallKind.VIDEO) }
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Saved Contacts (${contacts.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (contacts.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Search any exact @username above to add friends worldwide and start encrypted chats, voice, video, or screen-share calls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(contacts, key = { it.userId }) { c ->
                val user = UserDto(
                    id = c.userId,
                    username = c.username,
                    displayName = c.displayName,
                    bio = c.bio,
                    avatarColor = c.avatarColor,
                    isOnline = c.isOnline,
                    lastSeenAt = c.lastSeenAt
                )
                UserDirectoryRow(
                    user = user,
                    isContact = true,
                    onRowClick = { selectedProfile = user },
                    onToggleContact = { onRemoveContact(user.id) },
                    onMessage = { onOpenChat(user) },
                    onVoiceCall = { onStartCall(user, CallKind.AUDIO) },
                    onVideoCall = { onStartCall(user, CallKind.VIDEO) }
                )
            }
        }
    }

    selectedProfile?.let { profile ->
        val isContact = profile.id in contactIds
        ModalBottomSheet(onDismissRequest = { selectedProfile = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    UserAvatar(
                        username = profile.username,
                        avatarColorHex = profile.avatarColor,
                        isOnline = profile.isOnline,
                        size = 60.dp
                    )
                    Column {
                        Text(
                            text = profile.displayName.ifBlank { profile.username },
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "@${profile.username}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (profile.isOnline) "Online now" else "Offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (profile.isOnline) EmeraldSignal else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (profile.bio.isNotBlank()) {
                    Text(
                        text = profile.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            selectedProfile = null
                            onOpenChat(profile)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Message")
                    }
                    OutlinedButton(
                        onClick = {
                            selectedProfile = null
                            onStartCall(profile, CallKind.AUDIO)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Voice")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedProfile = null
                            onStartCall(profile, CallKind.VIDEO)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Video")
                    }
                    OutlinedButton(
                        onClick = {
                            selectedProfile = null
                            onStartCall(profile, CallKind.SCREEN_SHARE)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PresentToAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Screen")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        if (isContact) onRemoveContact(profile.id) else onAddContact(profile)
                        selectedProfile = null
                    }) {
                        Icon(
                            if (isContact) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isContact) "Remove Contact" else "Add Contact")
                    }
                    TextButton(onClick = {
                        onBlockUser(profile)
                        selectedProfile = null
                    }) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = CoralAlert, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Block", color = CoralAlert)
                    }
                    TextButton(onClick = {
                        reportTarget = profile
                        selectedProfile = null
                    }) {
                        Icon(Icons.Default.Flag, contentDescription = null, tint = CoralAlert, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Report", color = CoralAlert)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    reportTarget?.let { target ->
        var details by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reportTarget = null },
            title = { Text("Report @${target.username}") },
            text = {
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Describe the abusive behavior") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onReportUser(target, "ABUSE", details)
                    reportTarget = null
                }) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun UserDirectoryRow(
    user: UserDto,
    isContact: Boolean,
    onRowClick: () -> Unit,
    onToggleContact: () -> Unit,
    onMessage: () -> Unit,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            UserAvatar(
                username = user.username,
                avatarColorHex = user.avatarColor,
                isOnline = user.isOnline,
                size = 44.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName.ifBlank { user.username },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onToggleContact) {
                Icon(
                    if (isContact) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                    contentDescription = if (isContact) "Remove Contact" else "Add Contact"
                )
            }
            IconButton(onClick = onMessage) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Message")
            }
            IconButton(onClick = onVoiceCall) {
                Icon(Icons.Default.Call, contentDescription = "Voice Call")
            }
            IconButton(onClick = onVideoCall) {
                Icon(Icons.Default.Videocam, contentDescription = "Video Call")
            }
        }
    }
}
