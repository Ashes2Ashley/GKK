package com.example.data.startup

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.api.RetrofitClient
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Dns
import java.net.InetAddress

data class StartupHealthMetrics(
    val startupDurationMs: Long = 0,
    val isDatabasePrewarmed: Boolean = false,
    val isNetworkPrewarmed: Boolean = false,
    val activeNetworkType: String = "Detecting...",
    val isMetered: Boolean = false,
    val estimatedDownstreamKbps: Int = 0,
    val isPrewarmCompleted: Boolean = false
)

object AppStartupStabilizer {

    private val _healthMetrics = MutableStateFlow(StartupHealthMetrics())
    val healthMetrics: StateFlow<StartupHealthMetrics> = _healthMetrics.asStateFlow()

    private val stabilizerScope = CoroutineScope(Dispatchers.IO)

    /**
     * Initializes and stabilizes the application runtime on startup.
     */
    fun stabilize(context: Context) {
        val startTime = System.currentTimeMillis()

        // 1. Monitor real-time network telemetry
        registerNetworkCallback(context)

        // 2. Asynchronously pre-warm Room DB and OkHttp Connection Pool
        stabilizerScope.launch {
            var dbOk = false
            var netOk = false

            // Prewarm Room SQLite Database
            try {
                val db = AppDatabase.getInstance(context)
                db.postDao().getCachedCount()
                dbOk = true
            } catch (_: Exception) {}

            // Prewarm DNS & OkHttp Pool
            try {
                Dns.SYSTEM.lookup("jsonplaceholder.typicode.com")
                netOk = true
            } catch (_: Exception) {}

            val duration = System.currentTimeMillis() - startTime
            _healthMetrics.value = _healthMetrics.value.copy(
                startupDurationMs = duration,
                isDatabasePrewarmed = dbOk,
                isNetworkPrewarmed = netOk,
                isPrewarmCompleted = true
            )
        }
    }

    private fun registerNetworkCallback(context: Context) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    val speed = caps.linkDownstreamBandwidthKbps

                    val typeStr = when {
                        isWifi -> "Wi-Fi High-Speed"
                        isCellular -> "Cellular 4G/5G"
                        else -> "Ethernet / Other"
                    }

                    _healthMetrics.value = _healthMetrics.value.copy(
                        activeNetworkType = typeStr,
                        isMetered = isMetered,
                        estimatedDownstreamKbps = speed
                    )
                }

                override fun onLost(network: Network) {
                    _healthMetrics.value = _healthMetrics.value.copy(
                        activeNetworkType = "Disconnected",
                        estimatedDownstreamKbps = 0
                    )
                }
            })
        } catch (_: Exception) {}
    }
}
