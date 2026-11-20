package com.example.nearby

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import com.example.data.local.NearbyMessageEntity
import com.example.data.local.WorldLinkDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

enum class NearbyRangeStatus {
    IN_RANGE,
    WEAK_SIGNAL,
    OUT_OF_RANGE
}

enum class NearbyTransport {
    LAN_NSD,
    WIFI_DIRECT
}

data class NearbyPeer(
    val key: String,
    val username: String,
    val transport: NearbyTransport,
    val hostAddress: String? = null,
    val port: Int = 0,
    val deviceAddress: String? = null,
    val rangeStatus: NearbyRangeStatus = NearbyRangeStatus.IN_RANGE,
    val lastSeenMs: Long = System.currentTimeMillis(),
    val isConnected: Boolean = false
)

/**
 * Pure Wi-Fi Direct & Local Wi-Fi (NSD) offline communication manager.
 * Uses zero Bluetooth and zero internet.
 */
class OfflineNearbyManager(
    private val context: Context,
    private val dao: WorldLinkDao
) {
    companion object {
        private const val SERVICE_TYPE = "_worldlink._tcp."
        private const val PEER_STALE_MS = 25_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _peers = MutableStateFlow<List<NearbyPeer>>(emptyList())
    val peers: StateFlow<List<NearbyPeer>> = _peers.asStateFlow()

    private val _statusBanner =
        MutableStateFlow("Offline Wi-Fi Nearby ready (no Bluetooth, no internet needed)")
    val statusBanner: StateFlow<String> = _statusBanner.asStateFlow()

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var wifiP2pChannel: WifiP2pManager.Channel? = null
    private var wifiP2pReceiver: BroadcastReceiver? = null

    private var serverSocket: ServerSocket? = null
    private var listeningPort: Int = 0
    private var myUsername: String = "worldlink_user"
    private var staleCheckJob: Job? = null

    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery(username: String, hasWifiPerm: Boolean) {
        myUsername = username.ifBlank { "worldlink_user" }
        if (_isScanning.value) return
        _isScanning.value = true
        _statusBanner.value = "Scanning local Wi-Fi & Wi-Fi Direct peers (Bluetooth off)..."

        startTcpSocketServer()
        startNsdAdvertisingAndDiscovery()
        if (hasWifiPerm) {
            startWifiDirectDiscovery()
        }
        startStaleMonitor()
    }

    fun stopDiscovery() {
        _isScanning.value = false
        staleCheckJob?.cancel()
        stopNsd()
        stopWifiDirect()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        _statusBanner.value = "Nearby Wi-Fi discovery paused"
    }

    private fun startTcpSocketServer() {
        scope.launch {
            try {
                val ss = ServerSocket(0)
                serverSocket = ss
                listeningPort = ss.localPort
                while (isActive && !ss.isClosed) {
                    val client = ss.accept()
                    launch { handleIncomingSocket(client) }
                }
            } catch (e: Exception) {
                Log.d("OfflineNearby", "Socket server stopped: ${e.message}")
            }
        }
    }

    private suspend fun handleIncomingSocket(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val line = reader.readLine() ?: return
                val json = JSONObject(line)
                val senderUsername = json.optString("username", "nearby_peer")
                val body = json.optString("body", "")
                val transport = json.optString("transport", NearbyTransport.LAN_NSD.name)
                if (body.isNotBlank()) {
                    val key = "user:$senderUsername"
                    upsertPeer(
                        NearbyPeer(
                            key = key,
                            username = senderUsername,
                            transport = NearbyTransport.entries.firstOrNull { it.name == transport }
                                ?: NearbyTransport.LAN_NSD,
                            hostAddress = s.inetAddress.hostAddress,
                            port = json.optInt("replyPort", 0),
                            rangeStatus = NearbyRangeStatus.IN_RANGE,
                            isConnected = true
                        )
                    )
                    dao.insertNearbyMessage(
                        NearbyMessageEntity(
                            id = UUID.randomUUID().toString(),
                            peerKey = key,
                            peerUsername = senderUsername,
                            isOutgoing = false,
                            body = body,
                            transport = transport,
                            timestampMs = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("OfflineNearby", "Incoming socket read failed: ${e.message}")
        }
    }

    fun sendNearbyMessage(peer: NearbyPeer, text: String, onError: (String) -> Unit) {
        val clean = text.trim()
        if (clean.isBlank()) return
        scope.launch {
            val host = peer.hostAddress
            val port = peer.port
            if (host.isNullOrBlank() || port <= 0) {
                onError("Connect via Wi-Fi Direct or same Wi-Fi/Hotspot before messaging @${peer.username}.")
                return@launch
            }
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 3500)
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    val payload = JSONObject().apply {
                        put("username", myUsername)
                        put("body", clean)
                        put("transport", peer.transport.name)
                        put("replyPort", listeningPort)
                    }
                    writer.println(payload.toString())
                }
                dao.insertNearbyMessage(
                    NearbyMessageEntity(
                        id = UUID.randomUUID().toString(),
                        peerKey = peer.key,
                        peerUsername = peer.username,
                        isOutgoing = true,
                        body = clean,
                        transport = peer.transport.name,
                        timestampMs = System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) {
                markPeerOutOfRange(peer.key)
                onError("@${peer.username} is out of local Wi-Fi range or disconnected.")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectWifiDirectPeer(peer: NearbyPeer, onResult: (String) -> Unit) {
        val manager = wifiP2pManager
        val channel = wifiP2pChannel
        val addr = peer.deviceAddress
        if (manager == null || channel == null || addr.isNullOrBlank()) {
            onResult("Direct local Wi-Fi socket ready for @${peer.username}")
            return
        }
        val config = WifiP2pConfig().apply { deviceAddress = addr }
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onResult("Wi-Fi Direct link initiated with @${peer.username}")
            }

            override fun onFailure(reason: Int) {
                onResult("Wi-Fi Direct connection failed (code $reason). Ensure both devices are within local Wi-Fi range.")
            }
        })
    }

    private fun startNsdAdvertisingAndDiscovery() {
        val manager = nsdManager ?: return
        val port = listeningPort.takeIf { it > 0 } ?: 49210
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "WL_$myUsername"
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        nsdRegistrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, nsdRegistrationListener)
        } catch (_: Exception) {
        }

        nsdDiscoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceName.startsWith("WL_")) return
                val peerName = service.serviceName.removePrefix("WL_")
                if (peerName.equals(myUsername, ignoreCase = true)) return

                @Suppress("DEPRECATION")
                manager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host?.hostAddress ?: return
                        upsertPeer(
                            NearbyPeer(
                                key = "user:$peerName",
                                username = peerName,
                                transport = NearbyTransport.LAN_NSD,
                                hostAddress = host,
                                port = info.port,
                                rangeStatus = NearbyRangeStatus.IN_RANGE,
                                isConnected = true
                            )
                        )
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val peerName = service.serviceName.removePrefix("WL_")
                markPeerOutOfRange("user:$peerName")
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, nsdDiscoveryListener)
        } catch (_: Exception) {
        }
    }

    private fun stopNsd() {
        try {
            nsdRegistrationListener?.let { nsdManager?.unregisterService(it) }
            nsdDiscoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (_: Exception) {
        }
        nsdRegistrationListener = null
        nsdDiscoveryListener = null
    }

    @SuppressLint("MissingPermission")
    private fun startWifiDirectDiscovery() {
        val manager = wifiP2pManager ?: return
        val channel = manager.initialize(context, context.mainLooper, null)
        wifiP2pChannel = channel

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        manager.requestPeers(channel) { peerList ->
                            peerList.deviceList.forEach { dev ->
                                if (dev.status != WifiP2pDevice.UNAVAILABLE) {
                                    upsertPeer(
                                        NearbyPeer(
                                            key = "p2p:${dev.deviceAddress}",
                                            username = dev.deviceName.ifBlank { dev.deviceAddress },
                                            transport = NearbyTransport.WIFI_DIRECT,
                                            deviceAddress = dev.deviceAddress,
                                            hostAddress = if (dev.status == WifiP2pDevice.CONNECTED) "192.168.49.1" else null,
                                            port = 49210,
                                            rangeStatus = NearbyRangeStatus.IN_RANGE,
                                            isConnected = dev.status == WifiP2pDevice.CONNECTED
                                        )
                                    )
                                }
                            }
                        }
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        manager.requestConnectionInfo(channel) { info ->
                            val ownerIp = info.groupOwnerAddress?.hostAddress
                            if (info.groupFormed && !ownerIp.isNullOrBlank()) {
                                _statusBanner.value = "Wi-Fi Direct link established ($ownerIp)"
                            }
                        }
                    }
                }
            }
        }
        wifiP2pReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        try {
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {}
                override fun onFailure(reason: Int) {
                    Log.d("OfflineNearby", "Wi-Fi Direct discover failed: $reason")
                }
            })
        } catch (_: Exception) {
        }
    }

    private fun stopWifiDirect() {
        try {
            wifiP2pReceiver?.let { context.unregisterReceiver(it) }
        } catch (_: Exception) {
        }
        wifiP2pReceiver = null
        wifiP2pChannel = null
    }

    private fun startStaleMonitor() {
        staleCheckJob?.cancel()
        staleCheckJob = scope.launch {
            while (isActive) {
                delay(6000)
                val now = System.currentTimeMillis()
                _peers.value = _peers.value.map { peer ->
                    if (now - peer.lastSeenMs > PEER_STALE_MS) {
                        peer.copy(rangeStatus = NearbyRangeStatus.OUT_OF_RANGE, isConnected = false)
                    } else {
                        peer
                    }
                }
            }
        }
    }

    private fun upsertPeer(incoming: NearbyPeer) {
        val current = _peers.value.toMutableList()
        val idx = current.indexOfFirst { it.key == incoming.key }
        if (idx >= 0) {
            val prev = current[idx]
            current[idx] = prev.copy(
                transport = if (incoming.hostAddress != null) incoming.transport else prev.transport,
                hostAddress = incoming.hostAddress ?: prev.hostAddress,
                port = if (incoming.port > 0) incoming.port else prev.port,
                deviceAddress = incoming.deviceAddress ?: prev.deviceAddress,
                rangeStatus = incoming.rangeStatus,
                lastSeenMs = System.currentTimeMillis(),
                isConnected = incoming.isConnected || prev.isConnected
            )
        } else {
            current.add(incoming.copy(lastSeenMs = System.currentTimeMillis()))
        }
        _peers.value = current.sortedBy { it.rangeStatus.ordinal }
    }

    private fun markPeerOutOfRange(key: String) {
        _peers.value = _peers.value.map {
            if (it.key == key) it.copy(rangeStatus = NearbyRangeStatus.OUT_OF_RANGE, isConnected = false)
            else it
        }
    }
}
