package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldLinkDao {

    // Conversations
    @Query("SELECT * FROM conversations ORDER BY lastMessageAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(conversations: List<ConversationEntity>)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE peerId = :peerId")
    suspend fun clearUnread(peerId: String)

    @Query("DELETE FROM conversations WHERE peerId = :peerId")
    suspend fun deleteConversation(peerId: String)

    // Messages
    @Query("SELECT * FROM messages WHERE peerId = :peerId ORDER BY createdAt ASC")
    fun observeMessages(peerId: String): Flow<List<MessageEntity>>

    @Query(
        "SELECT * FROM messages WHERE peerId = :peerId AND body LIKE '%' || :query || '%' " +
            "AND deletedForAll = 0 ORDER BY createdAt DESC"
    )
    suspend fun searchMessagesInThread(peerId: String, query: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<MessageEntity>)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("UPDATE messages SET status = 'READ' WHERE peerId = :peerId AND recipientId = :myUserId AND status != 'READ'")
    suspend fun markIncomingMessagesRead(peerId: String, myUserId: String)

    @Query("UPDATE messages SET deletedForAll = 1, body = 'Message deleted' WHERE id = :messageId")
    suspend fun markMessageDeleted(messageId: String)

    @Query("DELETE FROM messages WHERE peerId = :peerId")
    suspend fun clearMessagesForPeer(peerId: String)

    // Contacts
    @Query("SELECT * FROM contacts ORDER BY isOnline DESC, username ASC")
    fun observeContacts(): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContact(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContacts(contacts: List<ContactEntity>)

    @Query("DELETE FROM contacts WHERE userId = :userId")
    suspend fun deleteContact(userId: String)

    @Query("DELETE FROM contacts")
    suspend fun clearContacts()

    // Calls
    @Query("SELECT * FROM call_records ORDER BY startedAt DESC")
    fun observeCalls(): Flow<List<CallRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCall(call: CallRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCalls(calls: List<CallRecordEntity>)

    // Blocked users
    @Query("SELECT * FROM blocked_users ORDER BY blockedAt DESC")
    fun observeBlockedUsers(): Flow<List<BlockedUserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlockedUsers(users: List<BlockedUserEntity>)

    @Query("DELETE FROM blocked_users WHERE userId = :userId")
    suspend fun deleteBlockedUser(userId: String)

    @Query("DELETE FROM blocked_users")
    suspend fun clearBlockedUsers()

    // Nearby messages
    @Query("SELECT * FROM nearby_messages WHERE peerKey = :peerKey ORDER BY timestampMs ASC")
    fun observeNearbyMessages(peerKey: String): Flow<List<NearbyMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNearbyMessage(message: NearbyMessageEntity)

    @Query("DELETE FROM nearby_messages WHERE peerKey = :peerKey")
    suspend fun clearNearbyMessages(peerKey: String)

    // Clear all local cache
    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM call_records")
    suspend fun clearAllCalls()

    @Query("DELETE FROM nearby_messages")
    suspend fun clearAllNearbyMessages()
}
