package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "worldlink_prefs")

enum class ThemePreference { DARK, LIGHT, SYSTEM }

data class UserSession(
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarColor: String = "#00E5FF",
    val sessionToken: String = "",
    val recoveryKey: String = ""
) {
    val isSignedIn: Boolean get() = userId.isNotBlank() && sessionToken.isNotBlank()
}

data class AppPreferences(
    val useBackendServer: Boolean = false,
    val serverBaseUrl: String = DEFAULT_SERVER_URL,
    val stunUrl: String = DEFAULT_STUN_URL,
    val turnUrl: String = "",
    val turnUsername: String = "",
    val turnCredential: String = "",
    val themePreference: ThemePreference = ThemePreference.DARK,
    val notificationsEnabled: Boolean = true,
    val readReceiptsEnabled: Boolean = true,
    val showOnlineStatus: Boolean = true,
    val lowDataMode: Boolean = false,
    val screenShareHighFps: Boolean = true
) {
    companion object {
        // Optional self-hosted endpoint; WorldLink works locally in-app + P2P without any server
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"
        const val DEFAULT_STUN_URL = "stun:stun.l.google.com:19302"
    }
}

class PreferencesStore(private val context: Context) {

    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val BIO = stringPreferencesKey("bio")
        val AVATAR_COLOR = stringPreferencesKey("avatar_color")
        val SESSION_TOKEN = stringPreferencesKey("session_token")
        val RECOVERY_KEY = stringPreferencesKey("recovery_key")

        val USE_BACKEND_SERVER = booleanPreferencesKey("use_backend_server")
        val SERVER_BASE_URL = stringPreferencesKey("server_base_url")
        val STUN_URL = stringPreferencesKey("stun_url")
        val TURN_URL = stringPreferencesKey("turn_url")
        val TURN_USERNAME = stringPreferencesKey("turn_username")
        val TURN_CREDENTIAL = stringPreferencesKey("turn_credential")

        val THEME_PREF = stringPreferencesKey("theme_pref")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val READ_RECEIPTS_ENABLED = booleanPreferencesKey("read_receipts_enabled")
        val SHOW_ONLINE_STATUS = booleanPreferencesKey("show_online_status")
        val LOW_DATA_MODE = booleanPreferencesKey("low_data_mode")
        val SCREEN_SHARE_HIGH_FPS = booleanPreferencesKey("screen_share_high_fps")
    }

    val userSession: Flow<UserSession> = context.dataStore.data.map { prefs ->
        UserSession(
            userId = prefs[Keys.USER_ID] ?: "",
            username = prefs[Keys.USERNAME] ?: "",
            displayName = prefs[Keys.DISPLAY_NAME] ?: "",
            bio = prefs[Keys.BIO] ?: "",
            avatarColor = prefs[Keys.AVATAR_COLOR] ?: "#00E5FF",
            sessionToken = prefs[Keys.SESSION_TOKEN] ?: "",
            recoveryKey = prefs[Keys.RECOVERY_KEY] ?: ""
        )
    }

    val appPreferences: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_PREF] ?: ThemePreference.DARK.name
        val theme = ThemePreference.entries.firstOrNull { it.name == themeStr } ?: ThemePreference.DARK
        AppPreferences(
            useBackendServer = prefs[Keys.USE_BACKEND_SERVER] ?: false,
            serverBaseUrl = prefs[Keys.SERVER_BASE_URL]?.ifBlank { AppPreferences.DEFAULT_SERVER_URL }
                ?: AppPreferences.DEFAULT_SERVER_URL,
            stunUrl = prefs[Keys.STUN_URL]?.ifBlank { AppPreferences.DEFAULT_STUN_URL }
                ?: AppPreferences.DEFAULT_STUN_URL,
            turnUrl = prefs[Keys.TURN_URL] ?: "",
            turnUsername = prefs[Keys.TURN_USERNAME] ?: "",
            turnCredential = prefs[Keys.TURN_CREDENTIAL] ?: "",
            themePreference = theme,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            readReceiptsEnabled = prefs[Keys.READ_RECEIPTS_ENABLED] ?: true,
            showOnlineStatus = prefs[Keys.SHOW_ONLINE_STATUS] ?: true,
            lowDataMode = prefs[Keys.LOW_DATA_MODE] ?: false,
            screenShareHighFps = prefs[Keys.SCREEN_SHARE_HIGH_FPS] ?: true
        )
    }

    suspend fun saveSession(session: UserSession) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.USERNAME] = session.username
            prefs[Keys.DISPLAY_NAME] = session.displayName
            prefs[Keys.BIO] = session.bio
            prefs[Keys.AVATAR_COLOR] = session.avatarColor
            prefs[Keys.SESSION_TOKEN] = session.sessionToken
            if (session.recoveryKey.isNotBlank()) {
                prefs[Keys.RECOVERY_KEY] = session.recoveryKey
            }
        }
    }

    suspend fun updateProfileIdentity(username: String, displayName: String, bio: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USERNAME] = username
            prefs[Keys.DISPLAY_NAME] = displayName
            prefs[Keys.BIO] = bio
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.DISPLAY_NAME)
            prefs.remove(Keys.BIO)
            prefs.remove(Keys.AVATAR_COLOR)
            prefs.remove(Keys.SESSION_TOKEN)
            prefs.remove(Keys.RECOVERY_KEY)
        }
    }

    suspend fun setUseBackendServer(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_BACKEND_SERVER] = enabled }
    }

    suspend fun saveServerConfig(
        serverBaseUrl: String,
        stunUrl: String,
        turnUrl: String,
        turnUsername: String,
        turnCredential: String,
        useBackendServer: Boolean? = null
    ) {
        context.dataStore.edit { prefs ->
            if (useBackendServer != null) {
                prefs[Keys.USE_BACKEND_SERVER] = useBackendServer
            }
            prefs[Keys.SERVER_BASE_URL] = serverBaseUrl.trim().trimEnd('/')
            prefs[Keys.STUN_URL] = stunUrl.trim().ifBlank { AppPreferences.DEFAULT_STUN_URL }
            prefs[Keys.TURN_URL] = turnUrl.trim()
            prefs[Keys.TURN_USERNAME] = turnUsername.trim()
            prefs[Keys.TURN_CREDENTIAL] = turnCredential.trim()
        }
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME_PREF] = theme.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setReadReceiptsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.READ_RECEIPTS_ENABLED] = enabled }
    }

    suspend fun setShowOnlineStatus(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_ONLINE_STATUS] = enabled }
    }

    suspend fun setLowDataMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOW_DATA_MODE] = enabled }
    }

    suspend fun setScreenShareHighFps(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SCREEN_SHARE_HIGH_FPS] = enabled }
    }
}
