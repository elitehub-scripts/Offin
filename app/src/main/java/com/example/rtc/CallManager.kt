package com.example.rtc

import android.content.Context
import android.content.Intent
import com.example.data.local.AppPreferences
import com.example.data.local.CallRecordEntity
import com.example.data.local.PreferencesStore
import com.example.data.local.WorldLinkDao
import com.example.data.remote.IceServerDto
import com.example.data.remote.NetworkModule
import com.example.data.remote.SignalingClient
import com.example.data.remote.SignalingEvent
import com.example.data.remote.UserDto
import com.example.data.remote.SaveCallRequest
import com.example.service.NotificationHelper
import com.example.service.WorldLinkCallService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

enum class CallPhase {
    IDLE,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    CONNECTING,
    ACTIVE,
    RECONNECTING,
    ENDED
}

enum class CallKind { AUDIO, VIDEO, SCREEN_SHARE }

data class ActiveCallState(
    val callId: String = "",
    val peer: UserDto = UserDto(),
    val kind: CallKind = CallKind.AUDIO,
    val direction: String = "OUTGOING",
    val phase: CallPhase = CallPhase.IDLE,
    val durationSeconds: Int = 0,
    val isMuted: Boolean = false,
    val isCameraEnabled: Boolean = false,
    val isScreenSharing: Boolean = false,
    val isRemoteScreenSharing: Boolean = false,
    val isFullScreen: Boolean = false,
    val audioRoute: AudioOutputRoute = AudioOutputRoute.EARPIECE,
    val statusMessage: String = ""
)

class CallManager(
    private val context: Context,
    private val signaling: SignalingClient,
    private val prefs: PreferencesStore,
    private val dao: WorldLinkDao,
    val rtcEngine: WebRtcEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _callState = MutableStateFlow(ActiveCallState())
    val callState: StateFlow<ActiveCallState> = _callState.asStateFlow()

    private var timerJob: Job? = null

    init {
        WorldLinkCallService.onStopRequestedFromNotification = {
            if (_callState.value.isScreenSharing) {
                stopScreenShare()
            } else {
                endCall()
            }
        }
        observeSignaling()
    }

    private fun observeSignaling() {
        scope.launch {
            signaling.events.collect { event ->
                when (event) {
                    is SignalingEvent.IncomingCall -> handleIncomingCall(event)
                    is SignalingEvent.CallRinging -> {
                        if (_callState.value.callId == event.callId) {
                            _callState.value = _callState.value.copy(statusMessage = "Ringing...")
                        }
                    }
                    is SignalingEvent.CallAccepted -> handleCallAccepted(event)
                    is SignalingEvent.CallDeclined -> handleCallTerminated(event.callId, "Declined", "DECLINED")
                    is SignalingEvent.CallEnded -> handleCallTerminated(event.callId, "Call ended", "ENDED")
                    is SignalingEvent.CallUnavailable -> handleCallTerminated(event.callId, event.reason, "MISSED")
                    is SignalingEvent.RemoteScreenShareChanged -> {
                        if (_callState.value.callId == event.callId) {
                            _callState.value = _callState.value.copy(isRemoteScreenSharing = event.isSharing)
                        }
                    }
                    is SignalingEvent.SdpOffer -> {
                        if (_callState.value.callId == event.callId) {
                            rtcEngine.handleRemoteOffer(event.sdp)
                        }
                    }
                    is SignalingEvent.SdpAnswer -> {
                        if (_callState.value.callId == event.callId) {
                            rtcEngine.handleRemoteAnswer(event.sdp)
                        }
                    }
                    is SignalingEvent.IceCandidateReceived -> {
                        if (_callState.value.callId == event.callId) {
                            rtcEngine.addRemoteIceCandidate(event.sdpMid, event.sdpMLineIndex, event.candidate)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun startOutgoingCall(peer: UserDto, kind: CallKind) {
        if (_callState.value.phase != CallPhase.IDLE) return
        val callId = UUID.randomUUID().toString()
        val isVideo = kind == CallKind.VIDEO
        _callState.value = ActiveCallState(
            callId = callId,
            peer = peer,
            kind = kind,
            direction = "OUTGOING",
            phase = CallPhase.OUTGOING_RINGING,
            isCameraEnabled = isVideo,
            audioRoute = if (isVideo) AudioOutputRoute.SPEAKER else AudioOutputRoute.EARPIECE,
            statusMessage = "Calling @${peer.username}..."
        )
        signaling.sendCallInvite(callId, peer.id, kind.name)
    }

    private fun handleIncomingCall(event: SignalingEvent.IncomingCall) {
        if (_callState.value.phase != CallPhase.IDLE) {
            signaling.sendCallDecline(event.callId, event.fromUser.id)
            return
        }
        val kind = CallKind.entries.firstOrNull { it.name == event.callType } ?: CallKind.AUDIO
        _callState.value = ActiveCallState(
            callId = event.callId,
            peer = event.fromUser,
            kind = kind,
            direction = "INCOMING",
            phase = CallPhase.INCOMING_RINGING,
            isCameraEnabled = kind == CallKind.VIDEO,
            audioRoute = if (kind == CallKind.VIDEO) AudioOutputRoute.SPEAKER else AudioOutputRoute.EARPIECE,
            statusMessage = "Incoming ${kind.name.lowercase().replace('_', ' ')} call"
        )
        scope.launch {
            if (prefs.appPreferences.first().notificationsEnabled) {
                NotificationHelper.showIncomingCallNotification(context, event.fromUser.username, event.callType)
            }
        }
    }

    fun acceptIncomingCall() {
        val current = _callState.value
        if (current.phase != CallPhase.INCOMING_RINGING) return
        NotificationHelper.cancelIncomingCallNotification(context)
        _callState.value = current.copy(phase = CallPhase.CONNECTING, statusMessage = "Connecting...")

        scope.launch {
            val ice = fetchIceServers()
            val appPrefs = prefs.appPreferences.first()
            rtcEngine.startSession(
                iceServerDtos = ice,
                enableVideo = current.kind == CallKind.VIDEO,
                lowData = appPrefs.lowDataMode,
                callbacks = createRtcCallbacks(current.callId, current.peer.id)
            )
            WorldLinkCallService.start(
                context = context,
                peerUsername = current.peer.username,
                isVideo = current.kind == CallKind.VIDEO,
                isScreenSharing = false
            )
            signaling.sendCallAccept(current.callId, current.peer.id)
        }
    }

    fun declineIncomingCall() {
        val current = _callState.value
        if (current.phase != CallPhase.INCOMING_RINGING) return
        NotificationHelper.cancelIncomingCallNotification(context)
        signaling.sendCallDecline(current.callId, current.peer.id)
        persistCallRecord(current, "DECLINED")
        _callState.value = ActiveCallState()
    }

    private fun handleCallAccepted(event: SignalingEvent.CallAccepted) {
        val current = _callState.value
        if (current.callId != event.callId) return
        _callState.value = current.copy(phase = CallPhase.CONNECTING, statusMessage = "Establishing WebRTC...")

        scope.launch {
            val ice = fetchIceServers()
            val appPrefs = prefs.appPreferences.first()
            rtcEngine.startSession(
                iceServerDtos = ice,
                enableVideo = current.kind == CallKind.VIDEO,
                lowData = appPrefs.lowDataMode,
                callbacks = createRtcCallbacks(current.callId, current.peer.id)
            )
            WorldLinkCallService.start(
                context = context,
                peerUsername = current.peer.username,
                isVideo = current.kind == CallKind.VIDEO,
                isScreenSharing = false
            )
            rtcEngine.createOffer()
        }
    }

    private fun createRtcCallbacks(callId: String, peerId: String): WebRtcCallbacks {
        return object : WebRtcCallbacks {
            override fun onLocalSdpOffer(sdp: String) = signaling.sendOffer(callId, peerId, sdp)
            override fun onLocalSdpAnswer(sdp: String) = signaling.sendAnswer(callId, peerId, sdp)
            override fun onLocalIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) =
                signaling.sendIceCandidate(callId, peerId, sdpMid, sdpMLineIndex, candidate)

            override fun onPeerIceConnected() {
                if (_callState.value.callId == callId) {
                    _callState.value = _callState.value.copy(phase = CallPhase.ACTIVE, statusMessage = "Connected")
                    startDurationTimer()
                }
            }

            override fun onPeerIceDisconnected() {
                if (_callState.value.callId == callId && _callState.value.phase == CallPhase.ACTIVE) {
                    _callState.value = _callState.value.copy(
                        phase = CallPhase.RECONNECTING,
                        statusMessage = "Reconnecting..."
                    )
                }
            }

            override fun onScreenShareStoppedBySystem() {
                stopScreenShare()
            }
        }
    }

    private fun startDurationTimer() {
        if (timerJob?.isActive == true) return
        timerJob = scope.launch {
            while (isActive && _callState.value.phase != CallPhase.IDLE) {
                delay(1000)
                if (_callState.value.phase == CallPhase.ACTIVE || _callState.value.phase == CallPhase.RECONNECTING) {
                    _callState.value = _callState.value.copy(
                        durationSeconds = _callState.value.durationSeconds + 1
                    )
                }
            }
        }
    }

    fun toggleMute() {
        val next = !_callState.value.isMuted
        rtcEngine.setMicMuted(next)
        _callState.value = _callState.value.copy(isMuted = next)
    }

    fun toggleCamera() {
        val next = !_callState.value.isCameraEnabled
        rtcEngine.enableCamera(next)
        _callState.value = _callState.value.copy(isCameraEnabled = next)
    }

    fun switchCamera() {
        rtcEngine.switchCamera()
    }

    fun cycleAudioRoute() {
        rtcEngine.refreshAudioRoutes()
        val routes = rtcEngine.availableAudioRoutes.value
        val currentIdx = routes.indexOf(_callState.value.audioRoute)
        val nextRoute = routes[(currentIdx + 1) % routes.size]
        rtcEngine.setAudioRoute(nextRoute)
        _callState.value = _callState.value.copy(audioRoute = nextRoute)
    }

    fun toggleFullScreen() {
        _callState.value = _callState.value.copy(isFullScreen = !_callState.value.isFullScreen)
    }

    fun startScreenShare(permissionIntent: Intent) {
        val current = _callState.value
        if (current.phase == CallPhase.IDLE) return
        scope.launch {
            val highFps = prefs.appPreferences.first().screenShareHighFps
            WorldLinkCallService.start(
                context = context,
                peerUsername = current.peer.username,
                isVideo = current.isCameraEnabled,
                isScreenSharing = true
            )
            rtcEngine.startScreenShare(permissionIntent, highFps)
            _callState.value = current.copy(isScreenSharing = true)
            signaling.sendScreenShareState(current.callId, current.peer.id, true)
        }
    }

    fun stopScreenShare() {
        val current = _callState.value
        if (!current.isScreenSharing) return
        rtcEngine.stopScreenShare()
        _callState.value = current.copy(isScreenSharing = false)
        if (current.phase != CallPhase.IDLE) {
            WorldLinkCallService.start(
                context = context,
                peerUsername = current.peer.username,
                isVideo = current.isCameraEnabled,
                isScreenSharing = false
            )
            signaling.sendScreenShareState(current.callId, current.peer.id, false)
        }
    }

    fun endCall() {
        val current = _callState.value
        if (current.phase == CallPhase.IDLE) return
        NotificationHelper.cancelIncomingCallNotification(context)
        val quality = rtcEngine.telemetry.value.quality.name
        signaling.sendCallEnd(current.callId, current.peer.id, current.durationSeconds, quality)
        persistCallRecord(current, if (current.durationSeconds > 0) "ENDED" else "MISSED")
        cleanupRtc()
    }

    private fun handleCallTerminated(callId: String, message: String, finalStatus: String) {
        val current = _callState.value
        if (current.callId != callId) return
        NotificationHelper.cancelIncomingCallNotification(context)
        persistCallRecord(current, finalStatus)
        cleanupRtc()
        _callState.value = ActiveCallState(statusMessage = message)
        scope.launch {
            delay(1800)
            if (_callState.value.phase == CallPhase.IDLE) {
                _callState.value = ActiveCallState()
            }
        }
    }

    private fun cleanupRtc() {
        timerJob?.cancel()
        timerJob = null
        rtcEngine.endSession()
        WorldLinkCallService.stop(context)
        _callState.value = ActiveCallState()
    }

    private fun persistCallRecord(state: ActiveCallState, finalStatus: String) {
        val quality = rtcEngine.telemetry.value.quality.name
        scope.launch(Dispatchers.IO) {
            dao.upsertCall(
                CallRecordEntity(
                    id = state.callId,
                    peerId = state.peer.id,
                    peerUsername = state.peer.username,
                    peerDisplayName = state.peer.displayName.ifBlank { state.peer.username },
                    peerAvatarColor = state.peer.avatarColor,
                    direction = state.direction,
                    callType = if (state.isScreenSharing) "SCREEN_SHARE" else state.kind.name,
                    status = finalStatus,
                    startedAt = Instant.now().toString(),
                    durationSeconds = state.durationSeconds,
                    networkQuality = quality
                )
            )
            try {
                val session = prefs.userSession.first()
                val appPrefs = prefs.appPreferences.first()
                if (session.isSignedIn) {
                    NetworkModule.apiFor(appPrefs.serverBaseUrl).saveCall(
                        auth = "Bearer ${session.sessionToken}",
                        body = SaveCallRequest(
                            id = state.callId,
                            peerId = state.peer.id,
                            direction = state.direction,
                            callType = if (state.isScreenSharing) "SCREEN_SHARE" else state.kind.name,
                            status = finalStatus,
                            durationSeconds = state.durationSeconds,
                            networkQuality = quality
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun fetchIceServers(): List<IceServerDto> {
        val appPrefs: AppPreferences = prefs.appPreferences.first()
        val session = prefs.userSession.first()
        val fallback = buildList {
            add(IceServerDto(urls = listOf(appPrefs.stunUrl)))
            if (appPrefs.turnUrl.isNotBlank()) {
                add(
                    IceServerDto(
                        urls = listOf(appPrefs.turnUrl),
                        username = appPrefs.turnUsername,
                        credential = appPrefs.turnCredential
                    )
                )
            }
        }
        if (!session.isSignedIn) return fallback
        return try {
            val resp = NetworkModule.apiFor(appPrefs.serverBaseUrl)
                .getIceServers("Bearer ${session.sessionToken}")
            val body = resp.body()
            if (resp.isSuccessful && body != null && body.iceServers.isNotEmpty()) {
                body.iceServers
            } else {
                fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }
}
