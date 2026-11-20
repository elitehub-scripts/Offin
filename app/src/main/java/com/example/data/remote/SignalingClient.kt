package com.example.data.remote

import android.util.Log
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
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

enum class SignalingConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    UNAVAILABLE
}

sealed interface SignalingEvent {
    data class ChatMessageReceived(val sender: UserDto, val message: MessageDto) : SignalingEvent
    data class MessageDelivered(val messageId: String, val peerId: String) : SignalingEvent
    data class ConversationRead(val peerId: String) : SignalingEvent
    data class MessageDeleted(val messageId: String, val peerId: String) : SignalingEvent
    data class TypingChanged(val fromUserId: String, val isTyping: Boolean) : SignalingEvent

    data class IncomingCall(
        val callId: String,
        val callType: String,
        val fromUser: UserDto
    ) : SignalingEvent

    data class CallRinging(val callId: String) : SignalingEvent
    data class CallAccepted(val callId: String, val fromUserId: String) : SignalingEvent
    data class CallDeclined(val callId: String, val fromUserId: String) : SignalingEvent
    data class CallEnded(val callId: String, val fromUserId: String) : SignalingEvent
    data class CallUnavailable(val callId: String, val reason: String) : SignalingEvent
    data class RemoteScreenShareChanged(val callId: String, val isSharing: Boolean) : SignalingEvent

    data class SdpOffer(val callId: String, val fromUserId: String, val sdp: String) : SignalingEvent
    data class SdpAnswer(val callId: String, val fromUserId: String, val sdp: String) : SignalingEvent
    data class IceCandidateReceived(
        val callId: String,
        val fromUserId: String,
        val sdpMid: String,
        val sdpMLineIndex: Int,
        val candidate: String
    ) : SignalingEvent
}

class SignalingClient(
    private val okHttpClient: OkHttpClient = NetworkModule.okHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(SignalingConnectionState.DISCONNECTED)
    val connectionState: StateFlow<SignalingConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<SignalingEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SignalingEvent> = _events.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var currentHttpUrl: String = ""
    private var currentToken: String = ""
    private var reconnectAttempt: Int = 0
    private var shouldStayConnected = false

    fun connect(httpBaseUrl: String, token: String) {
        if (token.isBlank()) {
            disconnect()
            return
        }
        val normalized = NetworkModule.normalizeHttpUrl(httpBaseUrl)
        if (shouldStayConnected && currentHttpUrl == normalized && currentToken == token &&
            (_connectionState.value == SignalingConnectionState.CONNECTED ||
                _connectionState.value == SignalingConnectionState.CONNECTING)
        ) {
            return
        }
        currentHttpUrl = normalized
        currentToken = token
        shouldStayConnected = true
        reconnectAttempt = 0
        openSocket()
    }

    fun disconnect() {
        shouldStayConnected = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnected")
        webSocket = null
        _connectionState.value = SignalingConnectionState.DISCONNECTED
    }

    private fun openSocket() {
        reconnectJob?.cancel()
        webSocket?.cancel()
        _connectionState.value = if (reconnectAttempt == 0) {
            SignalingConnectionState.CONNECTING
        } else {
            SignalingConnectionState.RECONNECTING
        }

        val wsUrl = NetworkModule.toWebSocketUrl(currentHttpUrl, currentToken)
        val request = Request.Builder().url(wsUrl).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                _connectionState.value = SignalingConnectionState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingJson(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (code == 4001) {
                    shouldStayConnected = false
                    _connectionState.value = SignalingConnectionState.DISCONNECTED
                } else {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("WorldLinkSignaling", "WebSocket failure: ${t.message}")
                _connectionState.value = SignalingConnectionState.UNAVAILABLE
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldStayConnected) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempt++
            val backoffMs = minOf(30_000L, 1_500L * reconnectAttempt)
            _connectionState.value = SignalingConnectionState.RECONNECTING
            delay(backoffMs)
            if (shouldStayConnected) openSocket()
        }
    }

    fun sendTyping(toUserId: String, isTyping: Boolean) {
        sendJson(JSONObject().apply {
            put("type", "chat.typing")
            put("toUserId", toUserId)
            put("isTyping", isTyping)
        })
    }

    fun sendReadReceipt(toUserId: String) {
        sendJson(JSONObject().apply {
            put("type", "chat.read")
            put("toUserId", toUserId)
        })
    }

    fun sendCallInvite(callId: String, toUserId: String, callType: String) {
        sendJson(JSONObject().apply {
            put("type", "call.invite")
            put("callId", callId)
            put("toUserId", toUserId)
            put("callType", callType)
        })
    }

    fun sendCallAccept(callId: String, toUserId: String) {
        sendJson(JSONObject().apply {
            put("type", "call.accept")
            put("callId", callId)
            put("toUserId", toUserId)
        })
    }

    fun sendCallDecline(callId: String, toUserId: String) {
        sendJson(JSONObject().apply {
            put("type", "call.decline")
            put("callId", callId)
            put("toUserId", toUserId)
        })
    }

    fun sendCallEnd(callId: String, toUserId: String, durationSeconds: Int, networkQuality: String) {
        sendJson(JSONObject().apply {
            put("type", "call.end")
            put("callId", callId)
            put("toUserId", toUserId)
            put("durationSeconds", durationSeconds)
            put("networkQuality", networkQuality)
        })
    }

    fun sendScreenShareState(callId: String, toUserId: String, isSharing: Boolean) {
        sendJson(JSONObject().apply {
            put("type", "call.screenshare")
            put("callId", callId)
            put("toUserId", toUserId)
            put("isSharing", isSharing)
        })
    }

    fun sendOffer(callId: String, toUserId: String, sdp: String) {
        sendJson(JSONObject().apply {
            put("type", "webrtc.offer")
            put("callId", callId)
            put("toUserId", toUserId)
            put("sdp", sdp)
        })
    }

    fun sendAnswer(callId: String, toUserId: String, sdp: String) {
        sendJson(JSONObject().apply {
            put("type", "webrtc.answer")
            put("callId", callId)
            put("toUserId", toUserId)
            put("sdp", sdp)
        })
    }

    fun sendIceCandidate(
        callId: String,
        toUserId: String,
        sdpMid: String,
        sdpMLineIndex: Int,
        candidate: String
    ) {
        sendJson(JSONObject().apply {
            put("type", "webrtc.ice")
            put("callId", callId)
            put("toUserId", toUserId)
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
            put("candidate", candidate)
        })
    }

    private fun sendJson(obj: JSONObject): Boolean {
        val ws = webSocket ?: return false
        return ws.send(obj.toString())
    }

    private fun handleIncomingJson(raw: String) {
        try {
            val json = JSONObject(raw)
            when (json.optString("type")) {
                "chat.message" -> {
                    val senderObj = json.optJSONObject("sender") ?: return
                    val msgObj = json.optJSONObject("message") ?: return
                    val sender = UserDto(
                        id = senderObj.optString("id"),
                        username = senderObj.optString("username"),
                        displayName = senderObj.optString("displayName"),
                        avatarColor = senderObj.optString("avatarColor", "#00E5FF"),
                        isOnline = senderObj.optBoolean("isOnline", true),
                        lastSeenAt = senderObj.optString("lastSeenAt")
                    )
                    val msg = MessageDto(
                        id = msgObj.optString("id"),
                        conversationId = msgObj.optString("conversationId"),
                        senderId = msgObj.optString("senderId"),
                        recipientId = msgObj.optString("recipientId"),
                        replyToMessageId = msgObj.optString("replyToMessageId").ifBlank { null },
                        body = msgObj.optString("body"),
                        status = msgObj.optString("status", "DELIVERED"),
                        deletedForAll = msgObj.optBoolean("deletedForAll", false),
                        createdAt = msgObj.optString("createdAt")
                    )
                    _events.tryEmit(SignalingEvent.ChatMessageReceived(sender, msg))
                }

                "chat.delivered" -> _events.tryEmit(
                    SignalingEvent.MessageDelivered(
                        messageId = json.optString("messageId"),
                        peerId = json.optString("peerId")
                    )
                )

                "chat.read" -> _events.tryEmit(
                    SignalingEvent.ConversationRead(peerId = json.optString("peerId"))
                )

                "chat.delete" -> _events.tryEmit(
                    SignalingEvent.MessageDeleted(
                        messageId = json.optString("messageId"),
                        peerId = json.optString("peerId")
                    )
                )

                "chat.typing" -> _events.tryEmit(
                    SignalingEvent.TypingChanged(
                        fromUserId = json.optString("fromUserId"),
                        isTyping = json.optBoolean("isTyping", false)
                    )
                )

                "call.invite" -> {
                    val u = json.optJSONObject("fromUser") ?: return
                    val fromUser = UserDto(
                        id = u.optString("id"),
                        username = u.optString("username"),
                        displayName = u.optString("displayName"),
                        avatarColor = u.optString("avatarColor", "#00E5FF"),
                        isOnline = true
                    )
                    _events.tryEmit(
                        SignalingEvent.IncomingCall(
                            callId = json.optString("callId"),
                            callType = json.optString("callType", "AUDIO"),
                            fromUser = fromUser
                        )
                    )
                }

                "call.ringing" -> _events.tryEmit(
                    SignalingEvent.CallRinging(json.optString("callId"))
                )

                "call.accept" -> _events.tryEmit(
                    SignalingEvent.CallAccepted(
                        callId = json.optString("callId"),
                        fromUserId = json.optString("fromUserId")
                    )
                )

                "call.decline" -> _events.tryEmit(
                    SignalingEvent.CallDeclined(
                        callId = json.optString("callId"),
                        fromUserId = json.optString("fromUserId")
                    )
                )

                "call.end" -> _events.tryEmit(
                    SignalingEvent.CallEnded(
                        callId = json.optString("callId"),
                        fromUserId = json.optString("fromUserId")
                    )
                )

                "call.unavailable" -> _events.tryEmit(
                    SignalingEvent.CallUnavailable(
                        callId = json.optString("callId"),
                        reason = json.optString("reason", "User unavailable")
                    )
                )

                "call.screenshare" -> _events.tryEmit(
                    SignalingEvent.RemoteScreenShareChanged(
                        callId = json.optString("callId"),
                        isSharing = json.optBoolean("isSharing", false)
                    )
                )

                "webrtc.offer" -> _events.tryEmit(
                    SignalingEvent.SdpOffer(
                        callId = json.optString("callId"),
                        fromUserId = json.optString("fromUserId"),
                        sdp = json.optString("sdp")
                    )
                )

                "webrtc.answer" -> _events.tryEmit(
                    SignalingEvent.SdpAnswer(
                        callId = json.optString("callId"),
                        fromUserId = json.optString("fromUserId"),
                        sdp = json.optString("sdp")
                    )
                )

                "webrtc.ice" -> _events.tryEmit(
                    SignalingEvent.IceCandidateReceived(
                        callId = json.optString("callId"),
                        fromUserId = json.optString("fromUserId"),
                        sdpMid = json.optString("sdpMid"),
                        sdpMLineIndex = json.optInt("sdpMLineIndex"),
                        candidate = json.optString("candidate")
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("WorldLinkSignaling", "Malformed WS payload: ${e.message}")
        }
    }
}
