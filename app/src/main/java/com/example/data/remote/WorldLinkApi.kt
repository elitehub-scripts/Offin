package com.example.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface WorldLinkApi {
    @GET("api/v1/health")
    suspend fun health(): Response<HealthResponse>

    @GET("api/v1/users/check-username")
    suspend fun checkUsername(@Query("username") username: String): Response<UsernameCheckResponse>

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("api/v1/auth/recover")
    suspend fun recover(@Body body: RecoverRequest): Response<AuthResponse>

    @GET("api/v1/me")
    suspend fun getMe(@Header("Authorization") auth: String): Response<UserEnvelope>

    @PATCH("api/v1/me/profile")
    suspend fun updateProfile(
        @Header("Authorization") auth: String,
        @Body body: UpdateProfileRequest
    ): Response<UserEnvelope>

    @POST("api/v1/me/username")
    suspend fun changeUsername(
        @Header("Authorization") auth: String,
        @Body body: ChangeUsernameRequest
    ): Response<UserEnvelope>

    @DELETE("api/v1/me")
    suspend fun deleteAccount(@Header("Authorization") auth: String): Response<Unit>

    @GET("api/v1/users/search")
    suspend fun searchUsers(
        @Header("Authorization") auth: String,
        @Query("q") query: String
    ): Response<UsersSearchResponse>

    @GET("api/v1/contacts")
    suspend fun listContacts(@Header("Authorization") auth: String): Response<ContactsResponse>

    @POST("api/v1/contacts")
    suspend fun addContact(
        @Header("Authorization") auth: String,
        @Body body: AddContactRequest
    ): Response<ContactEnvelope>

    @DELETE("api/v1/contacts/{userId}")
    suspend fun removeContact(
        @Header("Authorization") auth: String,
        @Path("userId") userId: String
    ): Response<Unit>

    @GET("api/v1/blocks")
    suspend fun listBlocked(@Header("Authorization") auth: String): Response<BlocksResponse>

    @POST("api/v1/blocks")
    suspend fun blockUser(
        @Header("Authorization") auth: String,
        @Body body: BlockRequest
    ): Response<Unit>

    @DELETE("api/v1/blocks/{userId}")
    suspend fun unblockUser(
        @Header("Authorization") auth: String,
        @Path("userId") userId: String
    ): Response<Unit>

    @POST("api/v1/reports")
    suspend fun reportUser(
        @Header("Authorization") auth: String,
        @Body body: ReportRequest
    ): Response<Unit>

    @GET("api/v1/conversations")
    suspend fun listConversations(@Header("Authorization") auth: String): Response<ConversationsResponse>

    @GET("api/v1/conversations/{peerId}/messages")
    suspend fun getMessages(
        @Header("Authorization") auth: String,
        @Path("peerId") peerId: String
    ): Response<MessagesResponse>

    @POST("api/v1/messages")
    suspend fun sendMessage(
        @Header("Authorization") auth: String,
        @Body body: SendMessageRequest
    ): Response<MessageEnvelope>

    @DELETE("api/v1/messages/{id}")
    suspend fun deleteMessage(
        @Header("Authorization") auth: String,
        @Path("id") messageId: String
    ): Response<Unit>

    @DELETE("api/v1/conversations/{peerId}")
    suspend fun clearConversation(
        @Header("Authorization") auth: String,
        @Path("peerId") peerId: String
    ): Response<Unit>

    @GET("api/v1/calls")
    suspend fun listCalls(@Header("Authorization") auth: String): Response<CallsResponse>

    @POST("api/v1/calls")
    suspend fun saveCall(
        @Header("Authorization") auth: String,
        @Body body: SaveCallRequest
    ): Response<Unit>

    @GET("api/v1/ice-servers")
    suspend fun getIceServers(@Header("Authorization") auth: String): Response<IceServersResponse>
}
