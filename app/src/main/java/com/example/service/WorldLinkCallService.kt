package com.example.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class WorldLinkCallService : Service() {

    companion object {
        const val ACTION_START = "com.example.worldlink.ACTION_START_CALL_SERVICE"
        const val ACTION_STOP = "com.example.worldlink.ACTION_STOP_CALL_SERVICE"
        const val EXTRA_PEER_USERNAME = "extra_peer_username"
        const val EXTRA_IS_SCREEN_SHARING = "extra_is_screen_sharing"
        const val EXTRA_IS_VIDEO = "extra_is_video"

        var onStopRequestedFromNotification: (() -> Unit)? = null

        fun start(
            context: Context,
            peerUsername: String,
            isVideo: Boolean,
            isScreenSharing: Boolean
        ) {
            val intent = Intent(context, WorldLinkCallService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PEER_USERNAME, peerUsername)
                putExtra(EXTRA_IS_VIDEO, isVideo)
                putExtra(EXTRA_IS_SCREEN_SHARING, isScreenSharing)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorldLinkCallService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            onStopRequestedFromNotification?.invoke()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val peerUsername = intent?.getStringExtra(EXTRA_PEER_USERNAME) ?: "peer"
        val isVideo = intent?.getBooleanExtra(EXTRA_IS_VIDEO, false) ?: false
        val isScreenSharing = intent?.getBooleanExtra(EXTRA_IS_SCREEN_SHARING, false) ?: false

        NotificationHelper.ensureChannels(this)

        val openIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val openPending = PendingIntent.getActivity(
            this,
            301,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WorldLinkCallService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            302,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isScreenSharing) {
            "Screen sharing live with @$peerUsername"
        } else if (isVideo) {
            "Video call with @$peerUsername"
        } else {
            "Voice call with @$peerUsername"
        }

        val text = if (isScreenSharing) {
            "Your Android display is being transmitted securely over WebRTC."
        } else {
            "WorldLink encrypted media session active"
        }

        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ACTIVE_SESSION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                if (isScreenSharing) "Stop Sharing" else "End Call",
                stopPending
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (isVideo) {
                fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            if (isScreenSharing) {
                fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(NotificationHelper.NOTIF_ACTIVE_CALL, notification, fgsType)
        } else {
            startForeground(NotificationHelper.NOTIF_ACTIVE_CALL, notification)
        }

        return START_NOT_STICKY
    }
}
