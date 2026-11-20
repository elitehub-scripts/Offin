package com.example.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    @Volatile
    private var cachedBaseUrl: String = ""

    @Volatile
    private var cachedApi: WorldLinkApi? = null

    fun apiFor(baseUrl: String): WorldLinkApi {
        val normalized = normalizeHttpUrl(baseUrl)
        val existing = cachedApi
        if (existing != null && cachedBaseUrl == normalized) return existing

        val retrofit = Retrofit.Builder()
            .baseUrl("$normalized/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        val created = retrofit.create(WorldLinkApi::class.java)
        cachedBaseUrl = normalized
        cachedApi = created
        return created
    }

    fun normalizeHttpUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return "http://10.0.2.2:8080"
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    fun toWebSocketUrl(httpBaseUrl: String, token: String): String {
        val normalized = normalizeHttpUrl(httpBaseUrl)
        val wsBase = when {
            normalized.startsWith("https://") -> "wss://" + normalized.removePrefix("https://")
            normalized.startsWith("http://") -> "ws://" + normalized.removePrefix("http://")
            else -> "wss://$normalized"
        }
        return "$wsBase/ws?token=$token"
    }

    fun parseError(response: Response<*>, fallback: String): String {
        return try {
            val raw = response.errorBody()?.string().orEmpty()
            if (raw.isBlank()) return fallback
            moshi.adapter(ApiErrorResponse::class.java).fromJson(raw)?.error?.ifBlank { fallback }
                ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }
}

enum class TransportKind { WIFI, CELLULAR, ETHERNET, NONE }

data class ConnectivityState(
    val hasInternet: Boolean = false,
    val transportKind: TransportKind = TransportKind.NONE
)

class NetworkStatusMonitor(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow(readCurrent())
    val state: StateFlow<ConnectivityState> = _state.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _state.value = readCurrent()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _state.value = readCurrent()
        }

        override fun onLost(network: Network) {
            _state.value = readCurrent()
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            _state.value = readCurrent()
        }
    }

    private fun readCurrent(): ConnectivityState {
        val active = connectivityManager.activeNetwork ?: return ConnectivityState(false, TransportKind.NONE)
        val caps = connectivityManager.getNetworkCapabilities(active)
            ?: return ConnectivityState(false, TransportKind.NONE)
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val kind = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TransportKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TransportKind.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TransportKind.ETHERNET
            else -> TransportKind.NONE
        }
        return ConnectivityState(hasInternet, kind)
    }
}
