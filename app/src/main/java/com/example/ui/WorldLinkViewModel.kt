package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WorldLinkApp
import com.example.data.local.AppPreferences
import com.example.data.local.BlockedUserEntity
import com.example.data.local.CallRecordEntity
import com.example.data.local.ContactEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.NearbyMessageEntity
import com.example.data.local.ThemePreference
import com.example.data.local.UserSession
import com.example.data.remote.ConnectivityState
import com.example.data.remote.SignalingConnectionState
import com.example.data.remote.SignalingEvent
import com.example.data.remote.UserDto
import com.example.nearby.NearbyPeer
import com.example.rtc.ActiveCallState
import com.example.rtc.CallKind
import com.example.rtc.RtcTelemetry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class UsernameAvailability { IDLE, CHECKING, AVAILABLE, TAKEN, INVALID, SERVER_ERROR }

data class OnboardingUiState(
    val usernameInput: String = "",
    val displayNameInput: String = "",
    val recoveryUsernameInput: String = "",
    val recoveryKeyInput: String = "",
    val isRecoveryMode: Boolean = false,
    val availability: UsernameAvailability = UsernameAvailability.IDLE,
    val availabilityMessage: String? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val serverCheckStatus: String? = null
)

class WorldLinkViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as WorldLinkApp).container
    private val repo = container.repository
    val callManager = container.callManager
    val webRtcEngine = container.webRtcEngine
    val nearbyManager = container.nearbyManager

    val session: StateFlow<UserSession> = repo.userSession.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UserSession()
    )

    val appPreferences: StateFlow<AppPreferences> = repo.appPreferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppPreferences()
    )

    val connectivity: StateFlow<ConnectivityState> = container.networkMonitor.state

    val signalingState: StateFlow<SignalingConnectionState> = container.signaling.connectionState

    val conversations: StateFlow<List<ConversationEntity>> = repo.conversations.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val contacts: StateFlow<List<ContactEntity>> = repo.contacts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val calls: StateFlow<List<CallRecordEntity>> = repo.calls.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val blockedUsers: StateFlow<List<BlockedUserEntity>> = repo.blockedUsers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val activeCall: StateFlow<ActiveCallState> = callManager.callState
    val rtcTelemetry: StateFlow<RtcTelemetry> = webRtcEngine.telemetry

    val nearbyPeers: StateFlow<List<NearbyPeer>> = nearbyManager.peers
    val nearbyScanning: StateFlow<Boolean> = nearbyManager.isScanning
    val nearbyBanner: StateFlow<String> = nearbyManager.statusBanner

    private val _onboardingState = MutableStateFlow(OnboardingUiState())
    val onboardingState: StateFlow<OnboardingUiState> = _onboardingState.asStateFlow()

    private val _selectedChatPeer = MutableStateFlow<UserDto?>(null)
    val selectedChatPeer: StateFlow<UserDto?> = _selectedChatPeer.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val threadMessages: StateFlow<List<MessageEntity>> = _selectedChatPeer
        .flatMapLatest { peer ->
            if (peer == null) flowOf(emptyList()) else repo.observeMessages(peer.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedNearbyPeer = MutableStateFlow<NearbyPeer?>(null)
    val selectedNearbyPeer: StateFlow<NearbyPeer?> = _selectedNearbyPeer.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val nearbyMessages: StateFlow<List<NearbyMessageEntity>> = _selectedNearbyPeer
        .flatMapLatest { peer ->
            if (peer == null) flowOf(emptyList()) else repo.observeNearbyMessages(peer.key)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<UserDto>>(emptyList())
    val searchResults: StateFlow<List<UserDto>> = _searchResults.asStateFlow()

    private val _isSearchingUsers = MutableStateFlow(false)
    val isSearchingUsers: StateFlow<Boolean> = _isSearchingUsers.asStateFlow()

    private val _peerTypingMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val peerTypingMap: StateFlow<Map<String, Boolean>> = _peerTypingMap.asStateFlow()

    private val _bannerFeedback = MutableStateFlow<String?>(null)
    val bannerFeedback: StateFlow<String?> = _bannerFeedback.asStateFlow()

    private val _threadSearchResults = MutableStateFlow<List<MessageEntity>>(emptyList())
    val threadSearchResults: StateFlow<List<MessageEntity>> = _threadSearchResults.asStateFlow()

    private var usernameCheckJob: Job? = null
    private val usernameRegex = Regex("^[A-Za-z0-9_]{3,24}$")

    init {
        viewModelScope.launch {
            repo.userSession.collect { user ->
                if (user.isSignedIn) {
                    repo.connectRealtimeIfSignedIn()
                    repo.refreshAllFromBackend()
                }
            }
        }
        viewModelScope.launch {
            container.signaling.events.collect { event ->
                if (event is SignalingEvent.TypingChanged) {
                    val map = _peerTypingMap.value.toMutableMap()
                    map[event.fromUserId] = event.isTyping
                    _peerTypingMap.value = map
                }
            }
        }
    }

    fun clearFeedback() {
        _bannerFeedback.value = null
    }

    fun updateOnboardingUsername(raw: String) {
        val clean = raw.replace("@", "").filter { it.isLetterOrDigit() || it == '_' }.take(24)
        _onboardingState.value = _onboardingState.value.copy(
            usernameInput = clean,
            errorMessage = null
        )
        usernameCheckJob?.cancel()
        if (clean.isEmpty()) {
            _onboardingState.value = _onboardingState.value.copy(
                availability = UsernameAvailability.IDLE,
                availabilityMessage = null
            )
            return
        }
        if (!usernameRegex.matches(clean)) {
            _onboardingState.value = _onboardingState.value.copy(
                availability = UsernameAvailability.INVALID,
                availabilityMessage = "Use 3–24 letters, numbers, or underscores."
            )
            return
        }
        _onboardingState.value = _onboardingState.value.copy(
            availability = UsernameAvailability.CHECKING,
            availabilityMessage = "Checking global availability..."
        )
        usernameCheckJob = viewModelScope.launch {
            delay(350)
            repo.checkUsernameAvailability(clean)
                .onSuccess { res ->
                    _onboardingState.value = _onboardingState.value.copy(
                        availability = if (res.available) UsernameAvailability.AVAILABLE else UsernameAvailability.TAKEN,
                        availabilityMessage = if (res.available) "@$clean is available" else (res.reason ?: "@$clean is already taken")
                    )
                }
                .onFailure { err ->
                    _onboardingState.value = _onboardingState.value.copy(
                        availability = UsernameAvailability.SERVER_ERROR,
                        availabilityMessage = "Backend unreachable: ${err.message ?: "Configure server URL"}"
                    )
                }
        }
    }

    fun updateOnboardingDisplayName(value: String) {
        _onboardingState.value = _onboardingState.value.copy(displayNameInput = value.take(64))
    }

    fun setRecoveryMode(enabled: Boolean) {
        _onboardingState.value = _onboardingState.value.copy(isRecoveryMode = enabled, errorMessage = null)
    }

    fun updateRecoveryUsername(value: String) {
        _onboardingState.value = _onboardingState.value.copy(recoveryUsernameInput = value.replace("@", "").take(24))
    }

    fun updateRecoveryKey(value: String) {
        _onboardingState.value = _onboardingState.value.copy(recoveryKeyInput = value.uppercase().take(40))
    }

    fun submitRegistration() {
        val state = _onboardingState.value
        if (!usernameRegex.matches(state.usernameInput)) {
            _onboardingState.value = state.copy(errorMessage = "Enter a valid 3–24 character username.")
            return
        }
        viewModelScope.launch {
            _onboardingState.value = state.copy(isSubmitting = true, errorMessage = null)
            repo.registerUsername(state.usernameInput, state.displayNameInput)
                .onSuccess {
                    _onboardingState.value = OnboardingUiState()
                }
                .onFailure { err ->
                    _onboardingState.value = _onboardingState.value.copy(
                        isSubmitting = false,
                        errorMessage = err.message ?: "Could not connect to WorldLink backend."
                    )
                }
        }
    }

    fun submitRecovery() {
        val state = _onboardingState.value
        if (state.recoveryUsernameInput.isBlank() || state.recoveryKeyInput.isBlank()) {
            _onboardingState.value = state.copy(errorMessage = "Enter both your @username and recovery key.")
            return
        }
        viewModelScope.launch {
            _onboardingState.value = state.copy(isSubmitting = true, errorMessage = null)
            repo.recoverIdentity(state.recoveryUsernameInput, state.recoveryKeyInput)
                .onSuccess {
                    _onboardingState.value = OnboardingUiState()
                }
                .onFailure { err ->
                    _onboardingState.value = _onboardingState.value.copy(
                        isSubmitting = false,
                        errorMessage = err.message ?: "Recovery failed."
                    )
                }
        }
    }

    fun testAndSaveServerConfig(
        serverBaseUrl: String,
        stunUrl: String,
        turnUrl: String,
        turnUsername: String,
        turnCredential: String
    ) {
        viewModelScope.launch {
            repo.saveServerConfig(serverBaseUrl, stunUrl, turnUrl, turnUsername, turnCredential)
            repo.testServerHealth(serverBaseUrl)
                .onSuccess { status ->
                    _onboardingState.value = _onboardingState.value.copy(serverCheckStatus = status)
                    _bannerFeedback.value = status
                    if (_onboardingState.value.usernameInput.isNotBlank()) {
                        updateOnboardingUsername(_onboardingState.value.usernameInput)
                    }
                }
                .onFailure { err ->
                    val msg = "Server unreachable: ${err.message}"
                    _onboardingState.value = _onboardingState.value.copy(serverCheckStatus = msg)
                    _bannerFeedback.value = msg
                }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            repo.connectRealtimeIfSignedIn()
            repo.refreshAllFromBackend()
                .onFailure { _bannerFeedback.value = "Sync failed: ${it.message}" }
        }
    }

    fun searchGlobalUsers(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearchingUsers.value = true
            repo.searchUsers(query)
                .onSuccess { _searchResults.value = it }
                .onFailure { _bannerFeedback.value = "Search unavailable: ${it.message}" }
            _isSearchingUsers.value = false
        }
    }

    fun openChat(peer: UserDto) {
        _selectedChatPeer.value = peer
        _threadSearchResults.value = emptyList()
        viewModelScope.launch {
            repo.syncThreadMessages(peer)
        }
    }

    fun closeChat() {
        _selectedChatPeer.value = null
        _threadSearchResults.value = emptyList()
    }

    fun sendChatMessage(text: String, replyToMessageId: String? = null) {
        val peer = _selectedChatPeer.value ?: return
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repo.sendMessage(peer, clean, replyToMessageId)
                .onFailure { _bannerFeedback.value = it.message }
        }
    }

    fun sendTyping(isTyping: Boolean) {
        val peer = _selectedChatPeer.value ?: return
        container.signaling.sendTyping(peer.id, isTyping)
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repo.deleteMessage(messageId).onFailure { _bannerFeedback.value = it.message }
        }
    }

    fun clearConversation(peerId: String) {
        viewModelScope.launch {
            repo.clearConversation(peerId).onFailure { _bannerFeedback.value = it.message }
        }
    }

    fun searchInThread(query: String) {
        val peer = _selectedChatPeer.value ?: return
        viewModelScope.launch {
            _threadSearchResults.value = repo.searchThreadMessages(peer.id, query)
        }
    }

    fun addContact(peer: UserDto) {
        viewModelScope.launch {
            repo.addContact(peer)
                .onSuccess { _bannerFeedback.value = "Added @${peer.username} to Contacts" }
                .onFailure { _bannerFeedback.value = it.message }
        }
    }

    fun removeContact(userId: String) {
        viewModelScope.launch {
            repo.removeContact(userId)
                .onSuccess { _bannerFeedback.value = "Removed contact" }
                .onFailure { _bannerFeedback.value = it.message }
        }
    }

    fun blockUser(peer: UserDto) {
        viewModelScope.launch {
            repo.blockUser(peer)
                .onSuccess {
                    if (_selectedChatPeer.value?.id == peer.id) closeChat()
                    _bannerFeedback.value = "Blocked @${peer.username}"
                }
                .onFailure { _bannerFeedback.value = it.message }
        }
    }

    fun unblockUser(userId: String) {
        viewModelScope.launch {
            repo.unblockUser(userId)
                .onSuccess { _bannerFeedback.value = "User unblocked" }
                .onFailure { _bannerFeedback.value = it.message }
        }
    }

    fun reportUser(peer: UserDto, category: String, details: String) {
        viewModelScope.launch {
            repo.reportUser(peer.id, category, details)
                .onSuccess { _bannerFeedback.value = "Abuse report submitted for @${peer.username}" }
                .onFailure { _bannerFeedback.value = it.message }
        }
    }

    fun startCall(peer: UserDto, kind: CallKind) {
        if (signalingState.value != SignalingConnectionState.CONNECTED) {
            _bannerFeedback.value = "Signaling server disconnected. Reconnecting before calling @${peer.username}..."
            viewModelScope.launch { repo.connectRealtimeIfSignedIn() }
            return
        }
        callManager.startOutgoingCall(peer, kind)
    }

    fun startScreenShareWithPermission(permissionIntent: Intent) {
        callManager.startScreenShare(permissionIntent)
    }

    // Offline Nearby Actions
    fun startNearbyDiscovery(hasWifiPerm: Boolean, hasBlePerm: Boolean) {
        val name = session.value.username.ifBlank { "WorldLink_Guest" }
        nearbyManager.startDiscovery(name, hasWifiPerm, hasBlePerm)
    }

    fun stopNearbyDiscovery() {
        nearbyManager.stopDiscovery()
    }

    fun selectNearbyPeer(peer: NearbyPeer?) {
        _selectedNearbyPeer.value = peer
    }

    fun connectNearbyPeer(peer: NearbyPeer) {
        nearbyManager.connectWifiDirectPeer(peer) { msg ->
            _bannerFeedback.value = msg
        }
    }

    fun sendNearbyMessage(peer: NearbyPeer, text: String) {
        nearbyManager.sendNearbyMessage(peer, text) { errorMsg ->
            _bannerFeedback.value = errorMsg
        }
    }

    // Settings
    fun updateProfile(displayName: String, bio: String) {
        viewModelScope.launch {
            repo.updateProfile(displayName, bio)
                .onSuccess { _bannerFeedback.value = "Profile updated" }
                .onFailure { _bannerFeedback.value = it.message }
        }
    }

    fun changeUsername(newUsername: String) {
        viewModelScope.launch {
            repo.changeUsername(newUsername)
                .onSuccess { _bannerFeedback.value = "Username updated to @$newUsername" }
                .onFailure { _bannerFeedback.value = it.message }
        }
    }

    fun setTheme(theme: ThemePreference) = viewModelScope.launch { repo.setTheme(theme) }
    fun setNotifications(enabled: Boolean) = viewModelScope.launch { repo.setNotificationsEnabled(enabled) }
    fun setReadReceipts(enabled: Boolean) = viewModelScope.launch { repo.setReadReceiptsEnabled(enabled) }
    fun setShowOnlineStatus(enabled: Boolean) = viewModelScope.launch { repo.setShowOnlineStatus(enabled) }
    fun setLowDataMode(enabled: Boolean) = viewModelScope.launch {
        repo.setLowDataMode(enabled)
        webRtcEngine.setLowDataMode(enabled)
    }
    fun setScreenShareHighFps(enabled: Boolean) = viewModelScope.launch { repo.setScreenShareHighFps(enabled) }

    fun clearLocalCache() = viewModelScope.launch {
        repo.clearLocalCache()
        _bannerFeedback.value = "Local cache cleared"
    }

    fun deleteAccount() = viewModelScope.launch {
        repo.deleteAccount()
            .onSuccess { _bannerFeedback.value = "Account and associated data deleted" }
            .onFailure { _bannerFeedback.value = it.message }
    }
}
