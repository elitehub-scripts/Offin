package com.example

import android.app.Application
import com.example.data.local.PreferencesStore
import com.example.data.local.WorldLinkDatabase
import com.example.data.remote.NetworkStatusMonitor
import com.example.data.remote.SignalingClient
import com.example.data.repository.WorldLinkRepository
import com.example.nearby.OfflineNearbyManager
import com.example.rtc.CallManager
import com.example.rtc.WebRtcEngine
import com.example.service.NotificationHelper

class WorldLinkApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val database: WorldLinkDatabase = WorldLinkDatabase.getInstance(app)
    val dao = database.dao()
    val prefs = PreferencesStore(app)
    val signaling = SignalingClient()
    val networkMonitor = NetworkStatusMonitor(app)
    val repository = WorldLinkRepository(dao, prefs, signaling)
    val webRtcEngine = WebRtcEngine(app)
    val callManager = CallManager(app, signaling, prefs, dao, webRtcEngine)
    val nearbyManager = OfflineNearbyManager(app, dao)
}
