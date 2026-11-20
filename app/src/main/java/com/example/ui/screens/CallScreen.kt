package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.rtc.ActiveCallState
import com.example.rtc.AudioOutputRoute
import com.example.rtc.CallKind
import com.example.rtc.CallPhase
import com.example.rtc.NetworkQualityLevel
import com.example.rtc.RtcTelemetry
import com.example.rtc.WebRtcVideoView
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldSignal
import org.webrtc.EglBase
import org.webrtc.VideoTrack

@Composable
fun CallOverlayScreen(
    callState: ActiveCallState,
    telemetry: RtcTelemetry,
    localVideoTrack: VideoTrack?,
    remoteVideoTrack: VideoTrack?,
    eglBase: EglBase,
    serverlessSignalCode: String? = null,
    onCompleteServerlessAnswer: (String) -> Boolean = { false },
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onCycleAudioRoute: () -> Unit,
    onStartScreenShare: () -> Unit,
    onStopScreenShare: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onEnterPip: () -> Unit
) {
    if (callState.phase == CallPhase.IDLE) return

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var answerInput by remember(callState.callId) { mutableStateOf("") }
    var codeCopiedNotice by remember(callState.callId) { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050D15))
    ) {
        // Remote Video / Screen Share Stream
        if (remoteVideoTrack != null) {
            WebRtcVideoView(
                track = remoteVideoTrack,
                eglBase = eglBase,
                mirror = false,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Audio / Ringing Backdrop
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UserAvatar(
                    username = callState.peer.username,
                    avatarColorHex = callState.peer.avatarColor,
                    isOnline = true,
                    size = 110.dp,
                    showPresenceDot = false
                )
                Text(
                    text = callState.peer.displayName.ifBlank { "@${callState.peer.username}" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = "@${callState.peer.username}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (callState.phase == CallPhase.ACTIVE) {
                        formatDuration(callState.durationSeconds)
                    } else {
                        callState.statusMessage
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (callState.phase == CallPhase.RECONNECTING) AmberWarn else Color.White.copy(alpha = 0.85f)
                )
            }
        }

        // Local Preview Thumbnail (top right)
        if (localVideoTrack != null && !callState.isFullScreen) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 64.dp, end = 16.dp)
                    .size(width = 104.dp, height = 152.dp)
            ) {
                WebRtcVideoView(
                    track = localVideoTrack,
                    eglBase = eglBase,
                    mirror = !callState.isScreenSharing,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top Telemetry & Window Controls
        if (!callState.isFullScreen) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Network Quality Pill
                val qColor = when (telemetry.quality) {
                    NetworkQualityLevel.EXCELLENT, NetworkQualityLevel.GOOD -> EmeraldSignal
                    NetworkQualityLevel.FAIR -> AmberWarn
                    NetworkQualityLevel.POOR -> CoralAlert
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.SignalCellularAlt, contentDescription = "Network quality", tint = qColor, modifier = Modifier.size(16.dp))
                        Text(
                            text = if (callState.phase == CallPhase.ACTIVE) {
                                "${formatDuration(callState.durationSeconds)} • ${telemetry.quality.name} (${telemetry.rttMs}ms)"
                            } else {
                                callState.statusMessage
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (callState.isScreenSharing || callState.isRemoteScreenSharing) {
                        Surface(shape = RoundedCornerShape(999.dp), color = CoralAlert) {
                            Text(
                                text = if (callState.isScreenSharing) "SHARING" else "VIEWING SCREEN",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onEnterPip,
                        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Picture in Picture", tint = Color.White)
                    }
                    IconButton(
                        onClick = onToggleFullScreen,
                        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Full screen", tint = Color.White)
                    }
                }
            }
        } else {
            IconButton(
                onClick = onToggleFullScreen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Default.FullscreenExit, contentDescription = "Exit full screen", tint = Color.White)
            }
        }

        // Bottom Controls
        if (!callState.isFullScreen) {
            if (callState.phase == CallPhase.INCOMING_RINGING) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 42.dp, start = 36.dp, end = 36.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FloatingActionButton(
                            onClick = onDecline,
                            containerColor = CoralAlert,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(68.dp).testTag("incoming_decline_button")
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "Decline", modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Decline", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FloatingActionButton(
                            onClick = onAccept,
                            containerColor = EmeraldSignal,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(68.dp).testTag("incoming_accept_button")
                        ) {
                            Icon(
                                if (callState.kind == CallKind.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = "Accept",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Accept", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF0E1D2B).copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .fillMaxWidth()
                ) {
                        Column(
                            modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (callState.isServerlessP2p && callState.phase != CallPhase.ACTIVE) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = if (callState.direction == "OUTGOING") {
                                                "Direct Cross-Country P2P (Zero Server)"
                                            } else {
                                                "Send P2P Answer Back to @${callState.peer.username}"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = if (serverlessSignalCode == null) {
                                                "Gathering ICE network route..."
                                            } else {
                                                serverlessSignalCode
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.85f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    serverlessSignalCode?.let { code ->
                                                        clipboard.setText(AnnotatedString(code))
                                                        codeCopiedNotice = "Copied! Send this code to @${callState.peer.username}."
                                                    }
                                                },
                                                enabled = serverlessSignalCode != null,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Copy Code", style = MaterialTheme.typography.labelSmall)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    serverlessSignalCode?.let { code ->
                                                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(android.content.Intent.EXTRA_TEXT, code)
                                                        }
                                                        context.startActivity(android.content.Intent.createChooser(sendIntent, "Share WorldLink P2P Code"))
                                                    }
                                                },
                                                enabled = serverlessSignalCode != null,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Share", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        if (codeCopiedNotice != null) {
                                            Text(
                                                text = codeCopiedNotice.orEmpty(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldSignal
                                            )
                                        }
                                        if (callState.direction == "OUTGOING") {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = answerInput,
                                                    onValueChange = { answerInput = it },
                                                    placeholder = { Text("Paste friend's WL1 Answer code...") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Button(
                                                    onClick = {
                                                        val ok = onCompleteServerlessAnswer(answerInput)
                                                        if (ok) answerInput = ""
                                                    },
                                                    enabled = answerInput.isNotBlank()
                                                ) {
                                                    Text("Connect")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                            CallControlPill(
                                icon = if (callState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                label = if (callState.isMuted) "Unmute" else "Mute",
                                active = callState.isMuted,
                                onClick = onToggleMute
                            )
                            CallControlPill(
                                icon = when (callState.audioRoute) {
                                    AudioOutputRoute.SPEAKER -> Icons.Default.VolumeUp
                                    AudioOutputRoute.EARPIECE -> Icons.Default.Hearing
                                },
                                label = callState.audioRoute.name.lowercase().replaceFirstChar { it.uppercase() },
                                active = callState.audioRoute != AudioOutputRoute.EARPIECE,
                                onClick = onCycleAudioRoute
                            )
                            CallControlPill(
                                icon = if (callState.isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                label = if (callState.isCameraEnabled) "Cam On" else "Cam Off",
                                active = callState.isCameraEnabled,
                                onClick = onToggleCamera
                            )
                            if (callState.isCameraEnabled) {
                                CallControlPill(
                                    icon = Icons.Default.Cameraswitch,
                                    label = "Flip",
                                    active = false,
                                    onClick = onSwitchCamera
                                )
                            }
                            CallControlPill(
                                icon = if (callState.isScreenSharing) Icons.Default.StopScreenShare else Icons.Default.PresentToAll,
                                label = if (callState.isScreenSharing) "Stop Share" else "Share",
                                active = callState.isScreenSharing,
                                activeTint = CoralAlert,
                                onClick = {
                                    if (callState.isScreenSharing) onStopScreenShare() else onStartScreenShare()
                                }
                            )
                        }

                        FloatingActionButton(
                            onClick = onEndCall,
                            containerColor = CoralAlert,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(60.dp).testTag("active_end_call_button")
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "End Call", modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlPill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeTint: Color = Color(0xFF00E5FF),
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (active) activeTint.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f))
        ) {
            Icon(icon, contentDescription = label, tint = if (active) activeTint else Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
    }
}
