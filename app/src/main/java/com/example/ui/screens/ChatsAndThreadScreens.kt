package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CallRecordEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.UserSession
import com.example.data.remote.SignalingConnectionState
import com.example.data.remote.UserDto
import com.example.rtc.CallKind
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldSignal

@Composable
fun ChatsTab(
    session: UserSession,
    conversations: List<ConversationEntity>,
    recentCalls: List<CallRecordEntity>,
    signalingState: SignalingConnectionState,
    hasInternet: Boolean,
    onOpenChat: (UserDto) -> Unit,
    onStartCall: (UserDto, CallKind) -> Unit,
    onOpenContactsSearch: () -> Unit,
    onRefresh: () -> Unit
) {
    var filterQuery by remember { mutableStateOf("") }
    val filtered = remember(conversations, filterQuery) {
        if (filterQuery.isBlank()) conversations
        else conversations.filter {
            it.peerUsername.contains(filterQuery, ignoreCase = true) ||
                it.peerDisplayName.contains(filterQuery, ignoreCase = true) ||
                it.lastMessageBody.contains(filterQuery, ignoreCase = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Identity & Quick Status Card
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
                        UserAvatar(
                            username = session.username,
                            avatarColorHex = session.avatarColor,
                            isOnline = signalingState == SignalingConnectionState.CONNECTED,
                            size = 50.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.displayName.ifBlank { session.username },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "@${session.username}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        ConnectionStatusPill(
                            signalingState = signalingState,
                            hasInternet = hasInternet,
                            onClick = onRefresh
                        )
                    }
                }
            }

            // Honest Server Unavailability Notice
            if (!hasInternet || signalingState == SignalingConnectionState.UNAVAILABLE) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = AmberWarn.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = AmberWarn)
                            Text(
                                text = if (!hasInternet) {
                                    "No internet connection. Global chats are cached; use Nearby tab for offline local wireless communication."
                                } else {
                                    "WorldLink signaling server unreachable. Configure your server URL in Settings or tap status to retry."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onRefresh) { Text("Retry") }
                        }
                    }
                }
            }

            // Search bar
            item {
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = { Text("Filter conversations or tap Contacts to find @users") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("chats_filter_input")
                )
            }

            // Quick Recent Calls Strip
            if (recentCalls.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Recent Calls",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(recentCalls.take(6), key = { it.id }) { call ->
                                val peer = UserDto(
                                    id = call.peerId,
                                    username = call.peerUsername,
                                    displayName = call.peerDisplayName,
                                    avatarColor = call.peerAvatarColor
                                )
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.clickable { onOpenChat(peer) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        UserAvatar(
                                            username = call.peerUsername,
                                            avatarColorHex = call.peerAvatarColor,
                                            isOnline = false,
                                            size = 34.dp,
                                            showPresenceDot = false
                                        )
                                        Column {
                                            Text("@${call.peerUsername}", style = MaterialTheme.typography.labelMedium)
                                            Text(
                                                text = call.callType.lowercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { onStartCall(peer, CallKind.AUDIO) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Call, contentDescription = "Voice call", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Conversations list or empty state
            if (filtered.isEmpty()) {
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
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(stringResource(R.string.empty_chats_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(R.string.empty_chats_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = onOpenContactsSearch,
                                modifier = Modifier.testTag("empty_chats_find_users")
                            ) {
                                Text("Find @username Globally")
                            }
                        }
                    }
                }
            } else {
                items(filtered, key = { it.peerId }) { conv ->
                    val peer = UserDto(
                        id = conv.peerId,
                        username = conv.peerUsername,
                        displayName = conv.peerDisplayName,
                        avatarColor = conv.peerAvatarColor,
                        isOnline = conv.peerIsOnline,
                        lastSeenAt = conv.peerLastSeenAt
                    )
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(peer) }
                            .testTag("conversation_card_${conv.peerUsername}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            UserAvatar(
                                username = conv.peerUsername,
                                avatarColorHex = conv.peerAvatarColor,
                                isOnline = conv.peerIsOnline,
                                size = 48.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "@${conv.peerUsername}",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatIsoTime(conv.lastMessageAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = conv.lastMessageBody,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (conv.unreadCount > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text("${conv.unreadCount}")
                                        }
                                    }
                                }
                            }
                            IconButton(
                                onClick = { onStartCall(peer, CallKind.AUDIO) },
                                modifier = Modifier.testTag("quick_voice_${conv.peerUsername}")
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Quick voice call")
                            }
                            IconButton(
                                onClick = { onStartCall(peer, CallKind.VIDEO) },
                                modifier = Modifier.testTag("quick_video_${conv.peerUsername}")
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = "Quick video call")
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onOpenContactsSearch,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("new_conversation_fab")
        ) {
            Icon(Icons.Default.AddComment, contentDescription = "New Conversation")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatThreadScreen(
    peer: UserDto,
    myUserId: String,
    messages: List<MessageEntity>,
    searchResults: List<MessageEntity>,
    isPeerTyping: Boolean,
    onBack: () -> Unit,
    onSendMessage: (body: String, replyToId: String?) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onClearConversation: (String) -> Unit,
    onSearchInThread: (String) -> Unit,
    onStartCall: (CallKind) -> Unit
) {
    BackHandler(onBack = onBack)

    var draft by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var showEmojiBar by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var selectedMsgForMenu by remember { mutableStateOf<MessageEntity?>(null) }

    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val quickEmojis = remember { listOf("👋", "🌍", "🔥", "😂", "❤️", "👍", "🎧", "📹", "🖥️", "✅") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                UserAvatar(
                    username = peer.username,
                    avatarColorHex = peer.avatarColor,
                    isOnline = peer.isOnline,
                    size = 40.dp
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "@${peer.username}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isPeerTyping -> "typing..."
                            peer.isOnline -> "Online"
                            else -> "Offline"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPeerTyping || peer.isOnline) EmeraldSignal else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onStartCall(CallKind.AUDIO) },
                    modifier = Modifier.testTag("thread_voice_call")
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Voice Call")
                }
                IconButton(
                    onClick = { onStartCall(CallKind.VIDEO) },
                    modifier = Modifier.testTag("thread_video_call")
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = "Video Call")
                }
                IconButton(
                    onClick = { onStartCall(CallKind.SCREEN_SHARE) },
                    modifier = Modifier.testTag("thread_screen_share")
                ) {
                    Icon(Icons.Default.PresentToAll, contentDescription = "Share Screen")
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Conversation options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Search messages") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showSearchBar = !showSearchBar
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear conversation") },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onClearConversation(peer.id)
                            }
                        )
                    }
                }
            }
        }

        // In-thread search bar
        AnimatedVisibility(visible = showSearchBar) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        onSearchInThread(it)
                    },
                    placeholder = { Text("Search messages in conversation...") },
                    trailingIcon = {
                        IconButton(onClick = {
                            showSearchBar = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (searchQuery.isNotBlank()) {
                    Text(
                        text = "${searchResults.size} matching messages",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Message list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMine = msg.senderId == myUserId
                val highlighted = searchQuery.isNotBlank() &&
                    msg.body.contains(searchQuery, ignoreCase = true)
                val repliedMsg = msg.replyToMessageId?.let { rid -> messages.firstOrNull { it.id == rid } }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isMine) 18.dp else 4.dp,
                            bottomEnd = if (isMine) 4.dp else 18.dp
                        ),
                        color = when {
                            highlighted -> MaterialTheme.colorScheme.tertiaryContainer
                            isMine -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainer
                        },
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { selectedMsgForMenu = msg }
                            )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            if (repliedMsg != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    Text(
                                        text = "Replying to: ${repliedMsg.body.take(60)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                text = msg.body,
                                style = MaterialTheme.typography.bodyLarge,
                                fontStyle = if (msg.deletedForAll) FontStyle.Italic else FontStyle.Normal,
                                color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = formatIsoTime(msg.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isMine) {
                                    when (msg.status) {
                                        "READ" -> Icon(
                                            Icons.Default.DoneAll,
                                            contentDescription = "Read",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        "DELIVERED" -> Icon(
                                            Icons.Default.DoneAll,
                                            contentDescription = "Delivered",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        "FAILED" -> Text(
                                            "Failed",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CoralAlert
                                        )
                                        else -> Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Sent",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = selectedMsgForMenu?.id == msg.id,
                        onDismissRequest = { selectedMsgForMenu = null }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Reply") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                            onClick = {
                                replyTarget = msg
                                selectedMsgForMenu = null
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                clipboard.setText(AnnotatedString(msg.body))
                                selectedMsgForMenu = null
                            }
                        )
                        if (isMine && !msg.deletedForAll) {
                            DropdownMenuItem(
                                text = { Text("Delete for everyone") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    onDeleteMessage(msg.id)
                                    selectedMsgForMenu = null
                                }
                            )
                        }
                    }
                }
            }
        }

        // Reply Preview Bar
        AnimatedVisibility(visible = replyTarget != null) {
            replyTarget?.let { target ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = target.body,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { replyTarget = null }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Quick Emoji Strip
        AnimatedVisibility(visible = showEmojiBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickEmojis.forEach { emoji ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clickable { draft += emoji }
                    ) {
                        Text(emoji, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
        }

        // Message Composer
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = { showEmojiBar = !showEmojiBar }) {
                    Icon(Icons.Default.EmojiEmotions, contentDescription = "Toggle emojis")
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        onTypingChanged(it.isNotBlank())
                    },
                    placeholder = { Text("Message @${peer.username}...") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f).testTag("chat_message_input")
                )
                IconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            onSendMessage(draft, replyTarget?.id)
                            draft = ""
                            replyTarget = null
                            onTypingChanged(false)
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("chat_send_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
