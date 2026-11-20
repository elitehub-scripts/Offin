package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val peerId: String,
    val peerUsername: String,
    val peerDisplayName: String,
    val peerAvatarColor: String,
    val peerIsOnline: Boolean,
    val peerLastSeenAt: String,
    val lastMessageBody: String,
    val lastMessageAt: String,
    val lastMessageSenderId: String,
    val lastMessageStatus: String,
    val unreadCount: Int = 0
)

@Entity(
    tableName = "messages",
    indices = [Index("peerId"), Index("createdAt")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val peerId: String,
    val senderId: String,
    val recipientId: String,
    val replyToMessageId: String? = null,
    val body: String,
    val status: String = "SENT", // SENDING, SENT, DELIVERED, READ, FAILED
    val deletedForAll: Boolean = false,
    val createdAt: String
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val userId: String,
    val username: String,
    val displayName: String,
    val bio: String,
    val avatarColor: String,
    val isOnline: Boolean,
    val lastSeenAt: String,
    val alias: String = ""
)

@Entity(
    tableName = "call_records",
    indices = [Index("startedAt")]
)
data class CallRecordEntity(
    @PrimaryKey val id: String,
    val peerId: String,
    val peerUsername: String,
    val peerDisplayName: String,
    val peerAvatarColor: String,
    val direction: String, // INCOMING, OUTGOING
    val callType: String, // AUDIO, VIDEO, SCREEN_SHARE
    val status: String, // RINGING, ACTIVE, ENDED, MISSED, DECLINED, FAILED
    val startedAt: String,
    val durationSeconds: Int = 0,
    val networkQuality: String = "GOOD"
)

@Entity(tableName = "blocked_users")
data class BlockedUserEntity(
    @PrimaryKey val userId: String,
    val username: String,
    val displayName: String,
    val reason: String,
    val blockedAt: String
)

@Entity(
    tableName = "nearby_messages",
    indices = [Index("peerKey"), Index("timestampMs")]
)
data class NearbyMessageEntity(
    @PrimaryKey val id: String,
    val peerKey: String,
    val peerUsername: String,
    val isOutgoing: Boolean,
    val body: String,
    val transport: String, // LAN_NSD, WIFI_DIRECT, BLE
    val timestampMs: Long
)
