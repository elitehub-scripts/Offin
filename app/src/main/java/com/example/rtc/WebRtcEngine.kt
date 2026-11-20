package com.example.rtc

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.remote.IceServerDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer

enum class AudioOutputRoute { EARPIECE, SPEAKER }

enum class NetworkQualityLevel { EXCELLENT, GOOD, FAIR, POOR }

data class RtcTelemetry(
    val quality: NetworkQualityLevel = NetworkQualityLevel.GOOD,
    val rttMs: Int = 0,
    val videoBitrateKbps: Int = 0,
    val iceState: String = "NEW"
)

interface WebRtcCallbacks {
    fun onLocalSdpOffer(sdp: String)
    fun onLocalSdpAnswer(sdp: String)
    fun onLocalIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String)
    fun onPeerIceDisconnected()
    fun onPeerIceConnected()
    fun onScreenShareStoppedBySystem()
    fun onIceGatheringComplete(fullSdpWithCandidates: String) {}
    fun onDataChannelMessage(text: String) {}
}

class WebRtcEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val eglBase: EglBase = EglBase.create()
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var cameraVideoSource: VideoSource? = null
    private var cameraCapturer: CameraVideoCapturer? = null
    private var cameraTextureHelper: SurfaceTextureHelper? = null
    private var localCameraTrack: VideoTrack? = null

    private var screenVideoSource: VideoSource? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var screenTextureHelper: SurfaceTextureHelper? = null
    private var localScreenTrack: VideoTrack? = null

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _telemetry = MutableStateFlow(RtcTelemetry())
    val telemetry: StateFlow<RtcTelemetry> = _telemetry.asStateFlow()

    private val _availableAudioRoutes = MutableStateFlow(listOf(AudioOutputRoute.EARPIECE, AudioOutputRoute.SPEAKER))
    val availableAudioRoutes: StateFlow<List<AudioOutputRoute>> = _availableAudioRoutes.asStateFlow()

    private val _p2pMessages = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val p2pMessages: SharedFlow<String> = _p2pMessages.asSharedFlow()

    private var statsJob: Job? = null
    private var callbacks: WebRtcCallbacks? = null
    private var isFrontCamera = true
    private var lowDataMode = false
    private val pendingRemoteIce = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false

    private fun ensureFactory() {
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        val audioModule = JavaAudioDeviceModule.builder(context.applicationContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
        audioModule.release()
    }

    fun startSession(
        iceServerDtos: List<IceServerDto>,
        enableVideo: Boolean,
        lowData: Boolean,
        createDataChannel: Boolean = false,
        callbacks: WebRtcCallbacks
    ) {
        this.callbacks = callbacks
        this.lowDataMode = lowData
        this.remoteDescriptionSet = false
        pendingRemoteIce.clear()
        ensureFactory()

        val iceServers = iceServerDtos.flatMap { dto ->
            dto.urls.map { url ->
                val builder = PeerConnection.IceServer.builder(url)
                if (!dto.username.isNullOrBlank()) builder.setUsername(dto.username)
                if (!dto.credential.isNullOrBlank()) builder.setPassword(dto.credential)
                builder.createIceServer()
            }
        }.ifEmpty {
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        }

        val pcFactory = factory ?: return
        peerConnection = pcFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                callbacks.onLocalIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
            }

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                if (newState == PeerConnection.IceGatheringState.COMPLETE) {
                    val sdp = peerConnection?.localDescription?.description
                    if (!sdp.isNullOrBlank()) {
                        callbacks.onIceGatheringComplete(sdp)
                    }
                }
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                val label = newState?.name ?: "UNKNOWN"
                _telemetry.value = _telemetry.value.copy(iceState = label)
                when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> callbacks.onPeerIceConnected()
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED -> callbacks.onPeerIceDisconnected()
                    else -> Unit
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    _remoteVideoTrack.value = track
                }
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    _remoteVideoTrack.value = track
                }
            }

            override fun onDataChannel(dc: DataChannel?) {
                dc ?: return
                attachDataChannel(dc)
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
        })

        if (createDataChannel) {
            val init = DataChannel.Init().apply { ordered = true }
            peerConnection?.createDataChannel("worldlink_chat", init)?.let { attachDataChannel(it) }
        }

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
        audioSource = pcFactory.createAudioSource(audioConstraints)
        localAudioTrack = pcFactory.createAudioTrack("WL_AUDIO_0", audioSource).apply {
            setEnabled(true)
        }
        peerConnection?.addTrack(localAudioTrack, listOf("worldlink_stream"))

        if (enableVideo) {
            startCameraCapture()
        }

        setAudioRoute(if (enableVideo) AudioOutputRoute.SPEAKER else AudioOutputRoute.EARPIECE)
        startTelemetryLoop()
    }

    private fun attachDataChannel(dc: DataChannel) {
        dataChannel = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {}
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer?) {
                buffer ?: return
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                val text = bytes.toString(Charsets.UTF_8)
                _p2pMessages.tryEmit(text)
                callbacks?.onDataChannelMessage(text)
            }
        })
    }

    fun sendDataChannelText(text: String): Boolean {
        val dc = dataChannel ?: return false
        if (dc.state() != DataChannel.State.OPEN) return false
        val buf = ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8))
        return dc.send(DataChannel.Buffer(buf, false))
    }

    private fun startCameraCapture() {
        val pcFactory = factory ?: return
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        val chosen = deviceNames.firstOrNull {
            if (isFrontCamera) enumerator.isFrontFacing(it) else enumerator.isBackFacing(it)
        } ?: deviceNames.firstOrNull() ?: return

        cameraCapturer = enumerator.createCapturer(chosen, null) ?: return
        cameraTextureHelper = SurfaceTextureHelper.create("WLCameraThread", eglBase.eglBaseContext)
        cameraVideoSource = pcFactory.createVideoSource(cameraCapturer!!.isScreencast)
        cameraCapturer?.initialize(cameraTextureHelper, context.applicationContext, cameraVideoSource!!.capturerObserver)

        val (width, height, fps) = if (lowDataMode) Triple(640, 480, 15) else Triple(1280, 720, 30)
        try {
            cameraCapturer?.startCapture(width, height, fps)
        } catch (e: Exception) {
            Log.w("WebRtcEngine", "Camera capture start error: ${e.message}")
        }

        localCameraTrack = pcFactory.createVideoTrack("WL_VIDEO_0", cameraVideoSource).apply {
            setEnabled(true)
        }
        peerConnection?.addTrack(localCameraTrack, listOf("worldlink_stream"))
        _localVideoTrack.value = localCameraTrack
        applyAdaptiveBitrateCap()
    }

    fun enableCamera(enabled: Boolean) {
        if (enabled && localCameraTrack == null) {
            startCameraCapture()
            createOffer()
        } else {
            localCameraTrack?.setEnabled(enabled)
        }
    }

    fun switchCamera() {
        cameraCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                isFrontCamera = isFront
            }

            override fun onCameraSwitchError(error: String?) {
                Log.w("WebRtcEngine", "Camera switch error: $error")
            }
        })
    }

    fun setMicMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun setLowDataMode(lowData: Boolean) {
        lowDataMode = lowData
        val (w, h, fps) = if (lowData) Triple(640, 480, 15) else Triple(1280, 720, 30)
        try {
            cameraCapturer?.changeCaptureFormat(w, h, fps)
        } catch (_: Exception) {
        }
        applyAdaptiveBitrateCap()
    }

    fun startScreenShare(permissionIntent: Intent, highFps: Boolean) {
        val pcFactory = factory ?: return
        stopScreenShareInternal()

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                scope.launch {
                    stopScreenShareInternal()
                    callbacks?.onScreenShareStoppedBySystem()
                }
            }
        }

        screenCapturer = ScreenCapturerAndroid(permissionIntent, callback)
        screenTextureHelper = SurfaceTextureHelper.create("WLScreenThread", eglBase.eglBaseContext)
        screenVideoSource = pcFactory.createVideoSource(true)
        screenCapturer?.initialize(
            screenTextureHelper,
            context.applicationContext,
            screenVideoSource!!.capturerObserver
        )

        val width = if (lowDataMode) 720 else 1080
        val height = if (lowDataMode) 1280 else 1920
        val fps = if (lowDataMode || !highFps) 15 else 30

        try {
            screenCapturer?.startCapture(width, height, fps)
        } catch (e: Exception) {
            Log.e("WebRtcEngine", "Screen capture failed: ${e.message}")
            stopScreenShareInternal()
            return
        }

        localScreenTrack = pcFactory.createVideoTrack("WL_SCREEN_0", screenVideoSource).apply {
            setEnabled(true)
        }
        val videoSender = peerConnection?.senders?.firstOrNull {
            it.track()?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND
        }
        if (videoSender != null) {
            videoSender.setTrack(localScreenTrack, false)
        } else {
            peerConnection?.addTrack(localScreenTrack, listOf("worldlink_stream"))
            createOffer()
        }
        _localVideoTrack.value = localScreenTrack
        applyAdaptiveBitrateCap()
    }

    fun stopScreenShare() {
        stopScreenShareInternal()
        val videoSender = peerConnection?.senders?.firstOrNull {
            it.track()?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND
        }
        if (localCameraTrack != null && videoSender != null) {
            videoSender.setTrack(localCameraTrack, false)
            _localVideoTrack.value = localCameraTrack
        } else {
            _localVideoTrack.value = null
        }
    }

    private fun stopScreenShareInternal() {
        try {
            screenCapturer?.stopCapture()
        } catch (_: Exception) {
        }
        screenCapturer?.dispose()
        screenCapturer = null
        localScreenTrack?.dispose()
        localScreenTrack = null
        screenVideoSource?.dispose()
        screenVideoSource = null
        screenTextureHelper?.dispose()
        screenTextureHelper = null
    }

    fun createOffer() {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc ?: return
                pc.setLocalDescription(SdpObserverAdapter(), desc)
                callbacks?.onLocalSdpOffer(desc.description)
            }
        }, constraints)
    }

    fun handleRemoteOffer(sdp: String) {
        val pc = peerConnection ?: return
        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, sdp)
        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                drainPendingIce()
                pc.createAnswer(object : SdpObserverAdapter() {
                    override fun onCreateSuccess(answer: SessionDescription?) {
                        answer ?: return
                        pc.setLocalDescription(SdpObserverAdapter(), answer)
                        callbacks?.onLocalSdpAnswer(answer.description)
                    }
                }, MediaConstraints())
            }
        }, remoteDesc)
    }

    fun handleRemoteAnswer(sdp: String) {
        val pc = peerConnection ?: return
        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                drainPendingIce()
            }
        }, remoteDesc)
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val ice = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        if (remoteDescriptionSet) {
            peerConnection?.addIceCandidate(ice)
        } else {
            pendingRemoteIce.add(ice)
        }
    }

    private fun drainPendingIce() {
        val pc = peerConnection ?: return
        pendingRemoteIce.forEach { pc.addIceCandidate(it) }
        pendingRemoteIce.clear()
    }

    private fun applyAdaptiveBitrateCap() {
        val sender = peerConnection?.senders?.firstOrNull {
            it.track()?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND
        } ?: return
        val params = sender.parameters
        val maxBps = if (lowDataMode) 350_000 else 1_600_000
        params.encodings.forEach { it.maxBitrateBps = maxBps }
        sender.parameters = params
    }

    @Suppress("DEPRECATION")
    fun setAudioRoute(route: AudioOutputRoute) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val targetType = when (route) {
                AudioOutputRoute.SPEAKER -> AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                AudioOutputRoute.EARPIECE -> AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
            val device = audioManager.availableCommunicationDevices.firstOrNull { it.type == targetType }
            if (device != null) {
                audioManager.setCommunicationDevice(device)
            }
        } else {
            audioManager.isSpeakerphoneOn = route == AudioOutputRoute.SPEAKER
        }
    }

    private fun startTelemetryLoop() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                delay(2000)
                peerConnection?.getStats { report ->
                    var rttMs = 0
                    var packetsLost = 0L
                    report.statsMap.values.forEach { stats ->
                        if (stats.type == "candidate-pair" && stats.members["state"] == "succeeded") {
                            val rttSec = (stats.members["currentRoundTripTime"] as? Number)?.toDouble() ?: 0.0
                            rttMs = (rttSec * 1000).toInt()
                        }
                        if (stats.type == "inbound-rtp") {
                            packetsLost += (stats.members["packetsLost"] as? Number)?.toLong() ?: 0L
                        }
                    }
                    val quality = when {
                        rttMs in 1..90 && packetsLost < 10 -> NetworkQualityLevel.EXCELLENT
                        rttMs <= 200 && packetsLost < 40 -> NetworkQualityLevel.GOOD
                        rttMs <= 420 -> NetworkQualityLevel.FAIR
                        else -> NetworkQualityLevel.POOR
                    }
                    _telemetry.value = _telemetry.value.copy(
                        quality = quality,
                        rttMs = rttMs,
                        videoBitrateKbps = if (lowDataMode) 350 else 1400
                    )
                }
            }
        }
    }

    fun endSession() {
        statsJob?.cancel()
        stopScreenShareInternal()
        try {
            cameraCapturer?.stopCapture()
        } catch (_: Exception) {
        }
        cameraCapturer?.dispose()
        cameraCapturer = null
        localCameraTrack?.dispose()
        localCameraTrack = null
        cameraVideoSource?.dispose()
        cameraVideoSource = null
        cameraTextureHelper?.dispose()
        cameraTextureHelper = null

        dataChannel?.close()
        dataChannel = null

        localAudioTrack?.dispose()
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null

        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null

        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _telemetry.value = RtcTelemetry()
        callbacks = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    companion object {
        fun encodeSignalBundle(username: String, kind: String, sdp: String): String {
            val obj = JSONObject().apply {
                put("u", username)
                put("k", kind)
                put("s", sdp)
            }
            return "WL1:" + Base64.encodeToString(obj.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }

        fun decodeSignalBundle(raw: String): Triple<String, String, String>? {
            return try {
                val payload = raw.trim().removePrefix("WL1:")
                val json = JSONObject(String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8))
                Triple(json.getString("u"), json.getString("k"), json.getString("s"))
            } catch (_: Exception) {
                null
            }
        }
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {
            Log.w("WebRtcEngine", "SDP create failure: $p0")
        }
        override fun onSetFailure(p0: String?) {
            Log.w("WebRtcEngine", "SDP set failure: $p0")
        }
    }
}

@Composable
fun WebRtcVideoView(
    track: VideoTrack?,
    eglBase: EglBase,
    mirror: Boolean,
    modifier: Modifier = Modifier
) {
    val rendererHolder = remember { arrayOfNulls<SurfaceViewRenderer>(1) }

    DisposableEffect(track) {
        val view = rendererHolder[0]
        if (view != null && track != null) {
            track.addSink(view)
        }
        onDispose {
            val currentView = rendererHolder[0]
            if (currentView != null && track != null) {
                try {
                    track.removeSink(currentView)
                } catch (_: Exception) {
                }
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglBase.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(mirror)
                setEnableHardwareScaler(true)
                rendererHolder[0] = this
                track?.addSink(this)
            }
        },
        update = { view ->
            view.setMirror(mirror)
        },
        onRelease = { view ->
            try {
                track?.removeSink(view)
            } catch (_: Exception) {
            }
            view.release()
            rendererHolder[0] = null
        }
    )
}
