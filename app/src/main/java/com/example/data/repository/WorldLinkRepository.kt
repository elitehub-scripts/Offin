package com.example.data.repository

import android.os.Build
import com.example.data.local.AppPreferences
import com.example.data.local.BlockedUserEntity
import com.example.data.local.CallRecordEntity
import com.example.data.local.ContactEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.PreferencesStore
import com.example.data.local.ThemePreference
import com.example.data.local.UserSession
import com.example.data.local.WorldLinkDao
import com.example.data.remote.AddContactRequest
import com.example.data.remote.BlockRequest
import com.example.data.remote.ChangeUsernameRequest
import com.example.data.remote.MessageDto
import com.example.data.remote.NetworkModule
import com.example.data.remote.RecoverRequest
import com.example.data.remote.RegisterRequest
import com.example.data.remote.ReportRequest
import com.example.data.remote.SendMessageRequest
import com.example.data.remote.SignalingClient
import com.example.data.remote.SignalingEvent
import com.example.data.remote.UpdateProfileRequest
import com.example.data.remote.UserDto
import com.example.data.remote.UsernameCheckResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class WorldLinkRepository(
    private val dao: WorldLinkDao,
    val prefs: PreferencesStore,
    val signaling: SignalingClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val userSession: Flow<UserSession> = prefs.userSession
    val appPreferences: Flow<AppPreferences> = prefs.appPreferences
    val conversations: Flow<List<ConversationEntity>> = dao.observeConversations()
    val contacts: Flow<List<ContactEntity>> = dao.observeContacts()
    val calls: Flow<List<CallRecordEntity>> = dao.observeCalls()
    val blockedUsers: Flow<List<BlockedUserEntity>> = dao.observeBlockedUsers()

    init {
        scope.launch {
            signaling.events.collect { event ->
                when (event) {
                    is SignalingEvent.ChatMessageReceived -> handleIncomingMessage(event.sender, event.message)
                    is SignalingEvent.MessageDelivered -> dao.updateMessageStatus(event.messageId, "DELIVERED")
                    is SignalingEvent.ConversationRead -> {
                        val myId = prefs.userSession.first().userId
                        if (myId.isNotBlank()) {
                            dao.markIncomingMessagesRead(event.peerId, myId)
                        }
                    }
                    is SignalingEvent.MessageDeleted -> dao.markMessageDeleted(event.messageId)
                    else -> Unit
                }
            }
        }
    }

    fun observeMessages(peerId: String): Flow<List<MessageEntity>> = dao.observeMessages(peerId)

    fun observeNearbyMessages(peerKey: String) = dao.observeNearbyMessages(peerKey)

    private suspend fun handleIncomingMessage(sender: UserDto, msg: MessageDto) {
        val myId = prefs.userSession.first().userId
        val peerId = if (msg.senderId == myId) msg.recipientId else msg.senderId
        dao.upsertMessage(
            MessageEntity(
                id = msg.id,
                conversationId = msg.conversationId,
                peerId = peerId,
                senderId = msg.senderId,
                recipientId = msg.recipientId,
                replyToMessageId = msg.replyToMessageId,
                body = msg.body,
                status = msg.status,
                deletedForAll = msg.deletedForAll,
                createdAt = msg.createdAt
            )
        )
        dao.upsertConversation(
            ConversationEntity(
                peerId = sender.id,
                peerUsername = sender.username,
                peerDisplayName = sender.displayName,
                peerAvatarColor = sender.avatarColor,
                peerIsOnline = sender.isOnline,
                peerLastSeenAt = sender.lastSeenAt,
                lastMessageBody = msg.body,
                lastMessageAt = msg.createdAt,
                lastMessageSenderId = msg.senderId,
                lastMessageStatus = msg.status,
                unreadCount = if (msg.senderId != myId) 1 else 0
            )
        )
    }

    suspend fun connectRealtimeIfSignedIn() {
        val session = prefs.userSession.first()
        val appPrefs = prefs.appPreferences.first()
        if (session.isSignedIn) {
            signaling.connect(appPrefs.serverBaseUrl, session.sessionToken)
        }
    }

    suspend fun testServerHealth(url: String): Result<String> = runCatching {
        val resp = NetworkModule.apiFor(url).health()
        if (!resp.isSuccessful || resp.body()?.ok != true) {
            error(NetworkModule.parseError(resp, "Server health check failed (${resp.code()})"))
        }
        val body = resp.body()!!
        if (body.turnConfigured) "Connected (STUN + TURN ready)" else "Connected (STUN ready; configure TURN for symmetric NAT)"
    }

    suspend fun checkUsernameAvailability(username: String): Result<UsernameCheckResponse> = runCatching {
        val appPrefs = prefs.appPreferences.first()
        val resp = NetworkModule.apiFor(appPrefs.serverBaseUrl).checkUsername(username.trim().removePrefix("@"))
        val body = resp.body()
        if (resp.isSuccessful && body != null) {
            body
        } else {
            val reason = NetworkModule.parseError(resp, "Could not verify username")
            UsernameCheckResponse(username = username, available = false, reason = reason)
        }
    }

    suspend fun registerUsername(username: String, displayName: String): Result<UserSession> = runCatching {
        val appPrefs = prefs.appPreferences.first()
        val clean = username.trim().removePrefix("@")
        val resp = NetworkModule.apiFor(appPrefs.serverBaseUrl).register(
            RegisterRequest(
                username = clean,
                displayName = displayName.trim().ifBlank { clean },
                deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            )
        )
        val body = resp.body()
        if (!resp.isSuccessful || body == null) {
            error(NetworkModule.parseError(resp, "Registration failed (${resp.code()})"))
        }
        val session = UserSession(
            userId = body.user.id,
            username = body.user.username,
            displayName = body.user.displayName,
            bio = body.user.bio,
            avatarColor = body.user.avatarColor,
            sessionToken = body.token,
            recoveryKey = body.recoveryKey.orEmpty()
        )
        prefs.saveSession(session)
        signaling.connect(appPrefs.serverBaseUrl, session.sessionToken)
        refreshAllFromBackend()
        session
    }

    suspend fun recoverIdentity(username: String, recoveryKey: String): Result<UserSession> = runCatching {
        val appPrefs = prefs.appPreferences.first()
        val clean = username.trim().removePrefix("@")
        val resp = NetworkModule.apiFor(appPrefs.serverBaseUrl).recover(
            RecoverRequest(
                username = clean,
                recoveryKey = recoveryKey.trim(),
                deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            )
        )
        val body = resp.body()
        if (!resp.isSuccessful || body == null) {
            error(NetworkModule.parseError(resp, "Recovery failed (${resp.code()})"))
        }
        val session = UserSession(
            userId = body.user.id,
            username = body.user.username,
            displayName = body.user.displayName,
            bio = body.user.bio,
            avatarColor = body.user.avatarColor,
            sessionToken = body.token,
            recoveryKey = recoveryKey.trim()
        )
        prefs.saveSession(session)
        signaling.connect(appPrefs.serverBaseUrl, session.sessionToken)
        refreshAllFromBackend()
        session
    }

    suspend fun refreshAllFromBackend(): Result<Unit> = runCatching {
        val session = prefs.userSession.first()
        if (!session.isSignedIn) return@runCatching
        val appPrefs = prefs.appPreferences.first()
        val api = NetworkModule.apiFor(appPrefs.serverBaseUrl)
        val auth = "Bearer ${session.sessionToken}"

        api.listConversations(auth).body()?.conversations?.let { convs ->
            dao.upsertConversations(
                convs.map {
                    ConversationEntity(
                        peerId = it.peer.id,
                        peerUsername = it.peer.username,
                        peerDisplayName = it.peer.displayName,
                        peerAvatarColor = it.peer.avatarColor,
                        peerIsOnline = it.peer.isOnline,
                        peerLastSeenAt = it.peer.lastSeenAt,
                        lastMessageBody = it.lastMessage?.body ?: "Conversation started",
                        lastMessageAt = it.lastMessageAt,
                        lastMessageSenderId = it.lastMessage?.senderId ?: "",
                        lastMessageStatus = it.lastMessage?.status ?: "SENT",
                        unreadCount = it.unreadCount
                    )
                }
            )
        }

        api.listContacts(auth).body()?.contacts?.let { list ->
            dao.clearContacts()
            dao.upsertContacts(
                list.map {
                    ContactEntity(
                        userId = it.id,
                        username = it.username,
                        displayName = it.displayName,
                        bio = it.bio,
                        avatarColor = it.avatarColor,
                        isOnline = it.isOnline,
                        lastSeenAt = it.lastSeenAt,
                        alias = it.alias.orEmpty()
                    )
                }
            )
        }

        api.listCalls(auth).body()?.calls?.let { list ->
            dao.upsertCalls(
                list.map {
                    CallRecordEntity(
                        id = it.id,
                        peerId = it.peer.id,
                        peerUsername = it.peer.username,
                        peerDisplayName = it.peer.displayName,
                        peerAvatarColor = it.peer.avatarColor,
                        direction = it.direction,
                        callType = it.callType,
                        status = it.status,
                        startedAt = it.startedAt,
                        durationSeconds = it.durationSeconds,
                        networkQuality = it.networkQuality
                    )
                }
            )
        }

        api.listBlocked(auth).body()?.blocked?.let { list ->
            dao.clearBlockedUsers()
            dao.upsertBlockedUsers(
                list.map {
                    BlockedUserEntity(
                        userId = it.id,
                        username = it.username,
                        displayName = it.displayName,
                        reason = it.reason.orEmpty(),
                        blockedAt = it.blockedAt.orEmpty()
                    )
                }
            )
        }
    }

    suspend fun searchUsers(query: String): Result<List<UserDto>> = runCatching {
        val session = prefs.userSession.first()
        val appPrefs = prefs.appPreferences.first()
        val resp = NetworkModule.apiFor(appPrefs.serverBaseUrl)
            .searchUsers("Bearer ${session.sessionToken}", query.trim())
        if (!resp.isSuccessful) {
            error(NetworkModule.parseError(resp, "Search failed (${resp.code()})"))
        }
        resp.body()?.users ?: emptyList()
    }

    suspend fun addContact(peer: UserDto): Result<Unit> = runCatching {
        val session = prefs.userSession.first()
        val appPrefs = prefs.appPreferences.first()
        val resp = NetworkModule.apiFor(appPrefs.serverBaseUrl)
            .addContact("Bearer ${session.sessionToken}", AddContactRequest(peer.id))
        if (!resp.isSuccessful) error(NetworkModule.parseError(resp, "Could not add contact"))
        dao.upsertContact(
            ContactEntity(
                userId = peer.id,
                username = peer.username,
                displayName = peer.displayName,
                bio = peer.bio,
                avatarColor = peer.avatarColor,
                isOnline = peer.isOnline,
                lastSeenAt = peer.lastSeenAt
            )
        )
    }

    suspend fun removeContact(userId: String): Result<Unit> = runCatching {
        val session = prefs.userSession.first()
        val appPrefs = prefs.appPreferences.first()
        val resp = NetworkModule.apiFor(appPrefs.serverBaseUrl)
            .removeContact("Bearer ${session.sessionToken}", userId)
        if (!resp.isSuccessful) error(NetworkModule.parseError(resp, "Could not remove contact"))
        dao.deleteContact(userId)
    }

    suspend fun blockUser(peer: UserDto, reason: String = "Blocked by user"): Result<Unit> = runCatching {
        val session = prefs.userSession.first()
        val appPrefs = prefs.appPreferences.first()
        val resp = NetworkModule.apiFor(appPrefs.serverBaseUrl)
            .blockUser("Bearer ${session.sessionToken}", BlockRequest(peer.id, reason))
        if (!resp.isSuccessful) error(NetworkModule.parseError(resp, "Block failed"))
        dao.deleteContact(peer.id)
        refreshAllFromBackend()
    }

    suspend fun unblockUser(userId: String): Result<Unit> = runCatching {
        val session = prefs.userSession.first()
        val appPrefs = prefs.appPreferences.first()
        val resp = NetworkModule.apiFor(appPrefs.serverBaseUrl)
            .unblockUser("Bearer ${session.sessionToken}", userId)
        if (!resp.isSuccessful) error(NetworkModule.parseError(resp, "Unblock failed"))
        dao.deleteBlockedUser(userId)
    }

    suspend fun reportUser(userId: String, category: String, details: String): Result<Unit> = runCatching {
        val session = prefs.userSession.first()
        val appPrefs = prefs.appPreferences.first()
        val resp = NetworkModule.apiFor(appPrefs.serverBaseUrl)
            .reportUser("Bearer ${session.sessionToken}", ReportRequest(userId, category, details))
        if (!resp.isSuccessful) error(NetworkModule.parseError(resp, "Report submission failed"))
    }

    suspend fun syncThreadMessages(peer: UserDto): Result<Unit> = runCatching {
        val session = prefs.userSession.first()
        val appPrefs = prefs.appPreferences.first()
        dao.clearUnread(peer.id)
        val resp = NetworkModule.apiFor(appPrefs.serverBaseUrl)
            .getMessages("Bearer ${session.sessionToken}", peer.id)
        if (!resp.isSuccessful) error(NetworkModule.parseError(resp, "Unable to sync messages"))
        val list = resp.body()?.messages.orEmpty()
        dao.upsertMessages(
            list.map {
                MessageEntity(
                    id = it.id,
                    conversationId = it.conversationId,
                    peerId = peer.id,
                    senderId = it.senderId,
                    recipientId = it.recipientId,
                    replyToMessageId = it.replyToMessageId,
                    body = it.body,
                    status = it.status,
                    deletedForAll = it.deletedForAll,
                    createdAt = it.createdAt
                )
            }
        )
        if (appPrefs.readReceiptsEnabled) {
            signaling.sendReadReceipt(peer.id)
        }
    }

    suspend fun sendMessage(
        peer: UserDto,
        body: String,
        replyToMessageId: String? = null
    ): Result<Unit> = runCatching {
        val session = prefs.userSession.first()
        val appPrefs = prefs.appPreferences.first()
        val clientId = UUID.randomUUID().toString()
        val now = Instant.now().toString()

        dao.upsertMessage(
            MessageEntity(
                id = clientId,
                conversationId = "",
                peerId = peer.id,
                senderId = session.userId,
                recipientId = peer.id,
                replyToMessageId = replyToMessageId,
                body = body,
                status = "SENDING",
                createdAt = now
            )
        )

        val resp = NetworkModule.apiFor(appPrefs.serverBaseUrl).sendMessage(
            auth = "Bearer ${session.sessionToken}",
            body = SendMessageRequest(
                id = clientId,
                recipientId = peer.id,
                body = body,
                replyToMessageId = replyToMessageId
            )
        )
        val saved = resp.body()?.message
        if (!resp.isSuccessful || saved == null) {
            dao.updateMessageStatus(clientId, "FAILED")
            error(NetworkModule.parseError(resp, "Message send failed"))
        }

        dao.upsertMessage(
            MessageEntity(
                id = saved.id,
                conversationId = saved.conversationId,
                peerId = peer.id,
                senderId = saved.senderId,
                recipientId = saved.recipientId,
                replyToMessageId = saved.replyToMessageId,
                body = saved.body,
                status = saved.status,
                deletedForAll = saved.deletedForAll,
                createdAt = saved.createdAt
            )
        )
        dao.upsertConversation(
            ConversationEntity(
                peerId = peer.id,
                peerUsername = peer.username,
                peerDisplayName = peer.displayName,
                peerAvatarColor = peer.avatarColor,
                peerIsOnline = peer.isOnline,
                peerLastSeenAt = peer.lastSeenAt,
                lastMessageBody = saved.body,
                lastMessageAt = saved.createdAt,
                lastMessageSenderId = saved.senderId,
                lastMessageStatus = saved.status,
                unreadCount = 0
            )
        )
    }

    suspend fun deleteMessage(messageId: String): Result<Unit> = runCatching {
        val session = prefs.userSession.first()
        val appPrefs = prefs.appPreferences.first()
        val resp = NetworkModule.apiFor(appPrefs.serverBaseUrl)
            .deleteMessage("Bearer ${session.sessionToken}", messageId)
        if (!resp.isSuccessful) error(NetworkModule.parseError(resp, "Delete failed"))
        dao.markMessageDeleted(messageId)
    }

    suspend fun clearConversation(peerId: String): Result<Unit> = runCatching {
        val session = prefs.userSession.first()
        val appPrefs = prefs.appPreferences.first()
        val resp = NetworkModule.apiFor(appPrefs.serverBaseUrl)
            .clearConversation("Bearer ${session.sessionToken}", peerId)
        if (!resp.isSuccessful) error(NetworkModule.parseError(resp, "Clear failed"))
        dao.clearMessagesForPeer(peerId)
        dao.deleteConversation(peerId)
    }

    suspend fun searchThreadMessages(peerId: String, query: String): List<MessageEntity> {
        return if (query.isBlank()) emptyList() else dao.searchMessagesInThread(peerId, query.trim())
    }

    suspend fun updateProfile(displayName: String, bio: String): Result<Unit> = runCatching {
        val session = prefs.userSession.first()
        val appPrefs = prefs.appPreferences.first()
        val resp = NetworkModule.apiFor(appPrefs.serverBaseUrl)
            .updateProfile("Bearer ${session.sessionToken}", UpdateProfileRequest(displayName, bio))
        val updated = resp.body()?.user
        if (!resp.isSuccessful || updated == null) {
            error(NetworkModule.parseError(resp, "Profile update failed"))
        }
        prefs.updateProfileIdentity(updated.username, updated.displayName, updated.bio)
    }

    suspend fun changeUsername(newUsername: String): Result<Unit> = runCatching {
        val session = prefs.userSession.first()
        val appPrefs = prefs.appPreferences.first()
        val clean = newUsername.trim().removePrefix("@")
        val resp = NetworkModule.apiFor(appPrefs.serverBaseUrl)
            .changeUsername("Bearer ${session.sessionToken}", ChangeUsernameRequest(clean))
        val updated = resp.body()?.user
        if (!resp.isSuccessful || updated == null) {
            error(NetworkModule.parseError(resp, "Username change failed"))
        }
        prefs.updateProfileIdentity(updated.username, updated.displayName, updated.bio)
    }

    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val session = prefs.userSession.first()
        val appPrefs = prefs.appPreferences.first()
        if (session.isSignedIn) {
            NetworkModule.apiFor(appPrefs.serverBaseUrl).deleteAccount("Bearer ${session.sessionToken}")
        }
        signaling.disconnect()
        clearLocalCache()
        prefs.clearSession()
    }

    suspend fun clearLocalCache() {
        dao.clearAllMessages()
        dao.clearAllConversations()
        dao.clearAllCalls()
        dao.clearAllNearbyMessages()
    }

    suspend fun saveServerConfig(
        serverBaseUrl: String,
        stunUrl: String,
        turnUrl: String,
        turnUsername: String,
        turnCredential: String
    ) {
        prefs.saveServerConfig(serverBaseUrl, stunUrl, turnUrl, turnUsername, turnCredential)
        val session = prefs.userSession.first()
        if (session.isSignedIn) {
            signaling.disconnect()
            signaling.connect(serverBaseUrl, session.sessionToken)
        }
    }

    suspend fun setTheme(theme: ThemePreference) = prefs.setThemePreference(theme)
    suspend fun setNotificationsEnabled(enabled: Boolean) = prefs.setNotificationsEnabled(enabled)
    suspend fun setReadReceiptsEnabled(enabled: Boolean) = prefs.setReadReceiptsEnabled(enabled)
    suspend fun setShowOnlineStatus(enabled: Boolean) = prefs.setShowOnlineStatus(enabled)
    suspend fun setLowDataMode(enabled: Boolean) = prefs.setLowDataMode(enabled)
    suspend fun setScreenShareHighFps(enabled: Boolean) = prefs.setScreenShareHighFps(enabled)
}
