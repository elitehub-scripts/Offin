package com.example

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.PresentToAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.ThemePreference
import com.example.data.remote.UserDto
import com.example.rtc.CallKind
import com.example.rtc.CallPhase
import com.example.ui.WorldLinkViewModel
import com.example.ui.screens.CallOverlayScreen
import com.example.ui.screens.CallsTab
import com.example.ui.screens.ChatThreadScreen
import com.example.ui.screens.ChatsTab
import com.example.ui.screens.ContactsTab
import com.example.ui.screens.NearbyTab
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ScreenShareTab
import com.example.ui.screens.SettingsTab
import com.example.ui.screens.requiredCallPermissions
import com.example.ui.theme.WorldLinkTheme

enum class WorldLinkTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CHATS("Chats", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    CALLS("Calls", Icons.Filled.Call, Icons.Outlined.Call),
    CONTACTS("Contacts", Icons.Filled.Contacts, Icons.Outlined.Contacts),
    SCREEN_SHARE("Screen", Icons.Filled.PresentToAll, Icons.Outlined.PresentToAll),
    NEARBY("Nearby", Icons.Filled.WifiTethering, Icons.Outlined.WifiTethering),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: WorldLinkViewModel = viewModel()
            val prefs by vm.appPreferences.collectAsStateWithLifecycle()
            val darkTheme = when (prefs.themePreference) {
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
            }
            WorldLinkTheme(darkTheme = darkTheme) {
                WorldLinkRoot(vm = vm, activity = this)
            }
        }
    }
}

@Composable
fun WorldLinkRoot(
    vm: WorldLinkViewModel,
    activity: ComponentActivity
) {
    val context = LocalContext.current
    val session by vm.session.collectAsStateWithLifecycle()
    val appPreferences by vm.appPreferences.collectAsStateWithLifecycle()
    val onboardingState by vm.onboardingState.collectAsStateWithLifecycle()
    val connectivity by vm.connectivity.collectAsStateWithLifecycle()
    val signalingState by vm.signalingState.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val calls by vm.calls.collectAsStateWithLifecycle()
    val blockedUsers by vm.blockedUsers.collectAsStateWithLifecycle()
    val activeCall by vm.activeCall.collectAsStateWithLifecycle()
    val serverlessSignalCode by vm.serverlessSignalCode.collectAsStateWithLifecycle()
    val telemetry by vm.rtcTelemetry.collectAsStateWithLifecycle()
    val localVideoTrack by vm.webRtcEngine.localVideoTrack.collectAsStateWithLifecycle()
    val remoteVideoTrack by vm.webRtcEngine.remoteVideoTrack.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val isSearching by vm.isSearchingUsers.collectAsStateWithLifecycle()
    val selectedChatPeer by vm.selectedChatPeer.collectAsStateWithLifecycle()
    val threadMessages by vm.threadMessages.collectAsStateWithLifecycle()
    val threadSearchResults by vm.threadSearchResults.collectAsStateWithLifecycle()
    val typingMap by vm.peerTypingMap.collectAsStateWithLifecycle()
    val nearbyPeers by vm.nearbyPeers.collectAsStateWithLifecycle()
    val nearbyScanning by vm.nearbyScanning.collectAsStateWithLifecycle()
    val nearbyBanner by vm.nearbyBanner.collectAsStateWithLifecycle()
    val selectedNearbyPeer by vm.selectedNearbyPeer.collectAsStateWithLifecycle()
    val nearbyMessages by vm.nearbyMessages.collectAsStateWithLifecycle()
    val feedback by vm.bannerFeedback.collectAsStateWithLifecycle()

    var guestNearbyMode by rememberSaveable { mutableStateOf(false) }
    var activeTab by rememberSaveable { mutableStateOf(WorldLinkTab.CHATS) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedback) {
        val msg = feedback ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vm.clearFeedback()
    }

    // Pending call launch awaiting audio/camera runtime permissions
    var pendingCallRequest by remember { mutableStateOf<Pair<UserDto, CallKind>?>(null) }
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioGranted = results[android.Manifest.permission.RECORD_AUDIO] == true
        val req = pendingCallRequest
        pendingCallRequest = null
        if (audioGranted && req != null) {
            vm.startCall(req.first, req.second)
        }
    }

    val initiateCall: (UserDto, CallKind) -> Unit = { peer, kind ->
        pendingCallRequest = peer to kind
        callPermissionLauncher.launch(requiredCallPermissions(includeCamera = kind == CallKind.VIDEO))
    }

    // MediaProjection Screen Capture Launcher
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            vm.startScreenShareWithPermission(data)
        }
    }

    val launchScreenCaptureConsent: () -> Unit = {
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    // PiP auto-enter on Android 12+ during active video/screen calls
    LaunchedEffect(activeCall.phase, activeCall.isCameraEnabled, activeCall.isScreenSharing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val shouldPip = activeCall.phase == CallPhase.ACTIVE &&
                (activeCall.isCameraEnabled || activeCall.isScreenSharing)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(9, 16))
                .setAutoEnterEnabled(shouldPip)
                .build()
            activity.setPictureInPictureParams(params)
        }
    }

    val enterPipNow: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(9, 16))
                .build()
            activity.enterPictureInPictureMode(params)
        }
    }

    // Guest Offline Nearby Mode (when user hasn't registered a global username yet)
    if (!session.isSignedIn && guestNearbyMode) {
        BackHandler { guestNearbyMode = false }
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                NearbyTab(
                    isScanning = nearbyScanning,
                    statusBanner = nearbyBanner,
                    peers = nearbyPeers,
                    selectedPeer = selectedNearbyPeer,
                    nearbyMessages = nearbyMessages,
                    onStartDiscovery = vm::startNearbyDiscovery,
                    onStopDiscovery = vm::stopNearbyDiscovery,
                    onSelectPeer = vm::selectNearbyPeer,
                    onConnectPeer = vm::connectNearbyPeer,
                    onSendNearbyMessage = vm::sendNearbyMessage
                )
            }
        }
        return
    }

    // Onboarding Gate
    if (!session.isSignedIn) {
        OnboardingScreen(
            state = onboardingState,
            appPreferences = appPreferences,
            onUsernameChange = vm::updateOnboardingUsername,
            onDisplayNameChange = vm::updateOnboardingDisplayName,
            onToggleRecoveryMode = vm::setRecoveryMode,
            onRecoveryUsernameChange = vm::updateRecoveryUsername,
            onRecoveryKeyChange = vm::updateRecoveryKey,
            onSubmitRegister = vm::submitRegistration,
            onSubmitRecover = vm::submitRecovery,
            onSaveServerConfig = vm::testAndSaveServerConfig,
            onOpenOfflineNearby = { guestNearbyMode = true }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Chat Thread View (Full Screen over Dashboard)
        val currentPeer = selectedChatPeer
        if (currentPeer != null) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    ChatThreadScreen(
                        peer = currentPeer,
                        myUserId = session.userId,
                        messages = threadMessages,
                        searchResults = threadSearchResults,
                        isPeerTyping = typingMap[currentPeer.id] == true,
                        onBack = vm::closeChat,
                        onSendMessage = vm::sendChatMessage,
                        onTypingChanged = vm::sendTyping,
                        onDeleteMessage = vm::deleteMessage,
                        onClearConversation = { peerId ->
                            vm.clearConversation(peerId)
                            vm.closeChat()
                        },
                        onSearchInThread = vm::searchInThread,
                        onStartCall = { kind -> initiateCall(currentPeer, kind) }
                    )
                }
            }
        } else {
            // Main 6-tab Adaptive Dashboard
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 600.dp

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (!useNavRail) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                                WorldLinkTab.entries.forEach { tab ->
                                    val selected = activeTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { activeTab = tab },
                                        icon = {
                                            Icon(
                                                if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = tab.label,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useNavRail) {
                            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                                WorldLinkTab.entries.forEach { tab ->
                                    val selected = activeTab == tab
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { activeTab = tab },
                                        icon = {
                                            Icon(
                                                if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label, maxLines = 1) },
                                        modifier = Modifier.testTag("nav_rail_${tab.name.lowercase()}")
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            when (activeTab) {
                                WorldLinkTab.CHATS -> ChatsTab(
                                    session = session,
                                    conversations = conversations,
                                    recentCalls = calls,
                                    signalingState = signalingState,
                                    hasInternet = connectivity.hasInternet,
                                    useBackendServer = appPreferences.useBackendServer,
                                    onOpenChat = vm::openChat,
                                    onStartCall = initiateCall,
                                    onOpenContactsSearch = { activeTab = WorldLinkTab.CONTACTS },
                                    onRefresh = vm::refreshData
                                )

                                WorldLinkTab.CALLS -> CallsTab(
                                    calls = calls,
                                    onCallBack = initiateCall
                                )

                                WorldLinkTab.CONTACTS -> ContactsTab(
                                    contacts = contacts,
                                    searchResults = searchResults,
                                    isSearching = isSearching,
                                    useBackendServer = appPreferences.useBackendServer,
                                    onSearchQuery = vm::searchGlobalUsers,
                                    onAddLocalContact = vm::addLocalContact,
                                    onAddContact = vm::addContact,
                                    onRemoveContact = vm::removeContact,
                                    onOpenChat = vm::openChat,
                                    onStartCall = initiateCall,
                                    onCreateServerlessOffer = vm::createServerlessP2pOffer,
                                    onAcceptServerlessOffer = vm::acceptServerlessP2pOffer,
                                    onBlockUser = vm::blockUser,
                                    onReportUser = vm::reportUser
                                )

                                WorldLinkTab.SCREEN_SHARE -> ScreenShareTab(
                                    contacts = contacts,
                                    activeCall = activeCall,
                                    appPreferences = appPreferences,
                                    onToggleHighFps = vm::setScreenShareHighFps,
                                    onToggleLowData = vm::setLowDataMode,
                                    onStartScreenShareCall = { peer -> initiateCall(peer, CallKind.SCREEN_SHARE) },
                                    onTriggerSystemScreenCapture = launchScreenCaptureConsent,
                                    onStopScreenShare = { vm.callManager.stopScreenShare() }
                                )

                                WorldLinkTab.NEARBY -> NearbyTab(
                                    isScanning = nearbyScanning,
                                    statusBanner = nearbyBanner,
                                    peers = nearbyPeers,
                                    selectedPeer = selectedNearbyPeer,
                                    nearbyMessages = nearbyMessages,
                                    onStartDiscovery = vm::startNearbyDiscovery,
                                    onStopDiscovery = vm::stopNearbyDiscovery,
                                    onSelectPeer = vm::selectNearbyPeer,
                                    onConnectPeer = vm::connectNearbyPeer,
                                    onSendNearbyMessage = vm::sendNearbyMessage
                                )

                                WorldLinkTab.SETTINGS -> SettingsTab(
                                    session = session,
                                    appPreferences = appPreferences,
                                    blockedUsers = blockedUsers,
                                    onUpdateProfile = vm::updateProfile,
                                    onChangeUsername = vm::changeUsername,
                                    onSetTheme = vm::setTheme,
                                    onSetNotifications = vm::setNotifications,
                                    onSetReadReceipts = vm::setReadReceipts,
                                    onSetShowOnlineStatus = vm::setShowOnlineStatus,
                                    onSetLowData = vm::setLowDataMode,
                                    onUnblockUser = vm::unblockUser,
                                    onSetUseBackendServer = vm::setUseBackendServer,
                                    onSaveServerConfig = vm::testAndSaveServerConfig,
                                    onClearCache = vm::clearLocalCache,
                                    onDeleteAccount = vm::deleteAccount,
                                    onCopyFeedback = { /* shown via banner */ }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Full-Screen Active / Incoming Call Overlay
        AnimatedVisibility(
            visible = activeCall.phase != CallPhase.IDLE,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CallOverlayScreen(
                callState = activeCall,
                telemetry = telemetry,
                localVideoTrack = localVideoTrack,
                remoteVideoTrack = remoteVideoTrack,
                eglBase = vm.webRtcEngine.eglBase,
                serverlessSignalCode = serverlessSignalCode,
                onCompleteServerlessAnswer = vm::completeServerlessP2pAnswer,
                onAccept = { vm.callManager.acceptIncomingCall() },
                onDecline = { vm.callManager.declineIncomingCall() },
                onEndCall = { vm.callManager.endCall() },
                onToggleMute = { vm.callManager.toggleMute() },
                onToggleCamera = { vm.callManager.toggleCamera() },
                onSwitchCamera = { vm.callManager.switchCamera() },
                onCycleAudioRoute = { vm.callManager.cycleAudioRoute() },
                onStartScreenShare = launchScreenCaptureConsent,
                onStopScreenShare = { vm.callManager.stopScreenShare() },
                onToggleFullScreen = { vm.callManager.toggleFullScreen() },
                onEnterPip = enterPipNow
            )
        }
    }
}
