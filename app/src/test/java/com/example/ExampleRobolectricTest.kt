package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppPreferences
import com.example.data.local.CallRecordEntity
import com.example.data.local.ContactEntity
import com.example.data.local.MessageEntity
import com.example.data.local.WorldLinkDatabase
import com.example.data.remote.NetworkModule
import com.example.ui.OnboardingUiState
import com.example.ui.UsernameAvailability
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.WorldLinkTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: WorldLinkDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WorldLinkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `app name is WorldLink`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("WorldLink", appName)
    }

    @Test
    fun `network module normalizes http and websocket urls`() {
        assertEquals("https://chat.worldlink.app", NetworkModule.normalizeHttpUrl("chat.worldlink.app/"))
        assertEquals("http://10.0.2.2:8080", NetworkModule.normalizeHttpUrl("http://10.0.2.2:8080/"))
        assertEquals(
            "wss://chat.worldlink.app/ws?token=abc",
            NetworkModule.toWebSocketUrl("https://chat.worldlink.app", "abc")
        )
        assertEquals(
            "ws://10.0.2.2:8080/ws?token=xyz",
            NetworkModule.toWebSocketUrl("http://10.0.2.2:8080", "xyz")
        )
    }

    @Test
    fun `room persists messages contacts and call records across queries`() = runTest {
        val dao = db.dao()
        dao.upsertContact(
            ContactEntity(
                userId = "u2",
                username = "Mahid_2026",
                displayName = "Mahid",
                bio = "Global Wi-Fi user",
                avatarColor = "#00E5FF",
                isOnline = true,
                lastSeenAt = "2026-11-20T12:00:00Z"
            )
        )
        dao.upsertMessage(
            MessageEntity(
                id = "m1",
                conversationId = "c1",
                peerId = "u2",
                senderId = "u1",
                recipientId = "u2",
                body = "Hello from another country over Wi-Fi!",
                status = "READ",
                createdAt = "2026-11-20T12:01:00Z"
            )
        )
        dao.upsertCall(
            CallRecordEntity(
                id = "call1",
                peerId = "u2",
                peerUsername = "Mahid_2026",
                peerDisplayName = "Mahid",
                peerAvatarColor = "#00E5FF",
                direction = "OUTGOING",
                callType = "VIDEO",
                status = "ENDED",
                startedAt = "2026-11-20T12:05:00Z",
                durationSeconds = 185,
                networkQuality = "EXCELLENT"
            )
        )

        val contacts = dao.observeContacts().first()
        val messages = dao.observeMessages("u2").first()
        val calls = dao.observeCalls().first()

        assertEquals(1, contacts.size)
        assertEquals("Mahid_2026", contacts[0].username)
        assertEquals(1, messages.size)
        assertEquals("READ", messages[0].status)
        assertEquals(1, calls.size)
        assertEquals(185, calls[0].durationSeconds)
    }

    @Test
    fun `onboarding renders username registration without phone or email and supports recovery toggle`() {
        var recoveryToggled = false
        composeTestRule.setContent {
            WorldLinkTheme(darkTheme = true) {
                OnboardingScreen(
                    state = OnboardingUiState(
                        usernameInput = "Mahid_2026",
                        availability = UsernameAvailability.AVAILABLE,
                        availabilityMessage = "@Mahid_2026 is available"
                    ),
                    appPreferences = AppPreferences(),
                    onUsernameChange = {},
                    onDisplayNameChange = {},
                    onToggleRecoveryMode = { recoveryToggled = it },
                    onRecoveryUsernameChange = {},
                    onRecoveryKeyChange = {},
                    onSubmitRegister = {},
                    onSubmitRecover = {},
                    onSaveServerConfig = { _, _, _, _, _ -> },
                    onOpenOfflineNearby = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("onboarding_username_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_register_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_nearby_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Recover Existing Identity").performScrollTo().performClick()
        assertTrue(recoveryToggled)
    }
}
