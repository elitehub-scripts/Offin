package com.example.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val ok: Boolean = false,
    val service: String = "",
    val version: String = "",
    val turnConfigured: Boolean = false,
    val timestamp: String = ""
)

@JsonClass(generateAdapter = true)
data class UsernameCheckResponse(
    val username: String = "",
    val available: Boolean = false,
    val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val username: String,
    val displayName: String,
    val deviceLabel: String
)

@JsonClass(generateAdapter = true)
data class RecoverRequest(
    val username: String,
    val recoveryKey: String,
    val deviceLabel: String
)

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarColor: String = "#00E5FF",
    val isOnline: Boolean = false,
    val lastSeenAt: String = "",
    val createdAt: String = "",
    val alias: String? = null,
    val reason: String? = null,
    val blockedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val user: UserDto,
    val token: String,
    val recoveryKey: String? = null
)

@JsonClass(generateAdapter = true)
data class UserEnvelope(val user: UserDto)

@JsonClass(generateAdapter = true)
data class UpdateProfileRequest(
    val displayName: String? = null,
    val bio: String? = null
)

@JsonClass(generateAdapter = true)
data class ChangeUsernameRequest(val username: String)

@JsonClass(generateAdapter = true)
data class UsersSearchResponse(val users: List<UserDto> = emptyList())

@JsonClass(generateAdapter = true)
data class ContactsResponse(val contacts: List<UserDto> = emptyList())

@JsonClass(generateAdapter = true)
data class AddContactRequest(val userId: String, val alias: String = "")

@JsonClass(generateAdapter = true)
data class ContactEnvelope(val contact: UserDto)

@JsonClass(generateAdapter = true)
data class BlocksResponse(val blocked: List<UserDto> = emptyList())

@JsonClass(generateAdapter = true)
data class BlockRequest(val userId: String, val reason: String = "")

@JsonClass(generateAdapter = true)
data class ReportRequest(val userId: String, val category: String, val details: String)

@JsonClass(generateAdapter = true)
data class MessageDto(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val recipientId: String = "",
    val replyToMessageId: String? = null,
    val body: String = "",
    val status: String = "SENT",
    val deletedForAll: Boolean = false,
    val createdAt: String = ""
)

@JsonClass(generateAdapter = true)
data class LastMessageDto(
    val id: String = "",
    val senderId: String = "",
    val body: String = "",
    val status: String = "SENT",
    val createdAt: String = ""
)

@JsonClass(generateAdapter = true)
data class ConversationDto(
    val conversationId: String = "",
    val peer: UserDto = UserDto(),
    val lastMessage: LastMessageDto? = null,
    val unreadCount: Int = 0,
    val lastMessageAt: String = ""
)

@JsonClass(generateAdapter = true)
data class ConversationsResponse(val conversations: List<ConversationDto> = emptyList())

@JsonClass(generateAdapter = true)
data class MessagesResponse(val messages: List<MessageDto> = emptyList())

@JsonClass(generateAdapter = true)
data class SendMessageRequest(
    val id: String? = null,
    val recipientId: String,
    val body: String,
    val replyToMessageId: String? = null
)

@JsonClass(generateAdapter = true)
data class MessageEnvelope(val message: MessageDto)

@JsonClass(generateAdapter = true)
data class CallRecordDto(
    val id: String = "",
    val direction: String = "OUTGOING",
    val peer: UserDto = UserDto(),
    val callType: String = "AUDIO",
    val status: String = "ENDED",
    val startedAt: String = "",
    val endedAt: String? = null,
    val durationSeconds: Int = 0,
    val networkQuality: String = "GOOD"
)

@JsonClass(generateAdapter = true)
data class CallsResponse(val calls: List<CallRecordDto> = emptyList())

@JsonClass(generateAdapter = true)
data class SaveCallRequest(
    val id: String,
    val peerId: String,
    val direction: String,
    val callType: String,
    val status: String,
    val durationSeconds: Int,
    val networkQuality: String
)

@JsonClass(generateAdapter = true)
data class IceServerDto(
    val urls: List<String> = emptyList(),
    val username: String? = null,
    val credential: String? = null
)

@JsonClass(generateAdapter = true)
data class IceServersResponse(
    val iceServers: List<IceServerDto> = emptyList(),
    val turnConfigured: Boolean = false,
    val note: String = ""
)

@JsonClass(generateAdapter = true)
data class ApiErrorResponse(val error: String = "")
