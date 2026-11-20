package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {
    const val CHANNEL_CALLS = "worldlink_calls"
    const val CHANNEL_MESSAGES = "worldlink_messages"
    const val CHANNEL_ACTIVE_SESSION = "worldlink_active_session"

    const val NOTIF_INCOMING_CALL = 2001
    const val NOTIF_ACTIVE_CALL = 2002
    const val NOTIF_MESSAGE_BASE = 3000

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val callsChannel = NotificationChannel(
            CHANNEL_CALLS,
            context.getString(R.string.channel_calls_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming voice, video, and screen share call alerts"
            enableVibration(true)
        }

        val messagesChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            context.getString(R.string.channel_messages_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Real-time text messages from WorldLink contacts"
        }

        val activeChannel = NotificationChannel(
            CHANNEL_ACTIVE_SESSION,
            context.getString(R.string.channel_screenshare_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing call and live screen sharing status"
        }

        manager.createNotificationChannels(listOf(callsChannel, messagesChannel, activeChannel))
    }

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showIncomingCallNotification(
        context: Context,
        username: String,
        callType: String
    ) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val typeLabel = when (callType) {
            "VIDEO" -> "Incoming Video Call"
            "SCREEN_SHARE" -> "Incoming Screen Share Call"
            else -> "Incoming Voice Call"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("@$username")
            .setContentText(typeLabel)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingOpen)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_INCOMING_CALL, notification)
    }

    fun cancelIncomingCallNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_INCOMING_CALL)
    }

    fun showMessageNotification(
        context: Context,
        senderUsername: String,
        body: String
    ) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            senderUsername.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("@$senderUsername")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingOpen)
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIF_MESSAGE_BASE + (senderUsername.hashCode() and 0x0FFF), notification)
    }
}
