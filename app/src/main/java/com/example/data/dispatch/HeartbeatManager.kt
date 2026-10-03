package com.example.data.dispatch

import android.content.Context
import android.os.BatteryManager
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

/**
 * Fleet heartbeats: this device broadcasts an encrypted heartbeat to every
 * paired device on [intervalMs], and records inbound heartbeats on the live
 * board. A heartbeat is a signed Envelope v1 (`kind: "hb"`) carrying battery
 * level — proof of life, nothing more.
 */
data class HeartbeatInfo(
    val deviceIdHex: String,
    val name: String,
    val lastSeenAt: Long,
    val batteryPct: Int
)

object HeartbeatManager {

    private val _board = MutableStateFlow<List<HeartbeatInfo>>(emptyList())
    val board: StateFlow<List<HeartbeatInfo>> = _board.asStateFlow()

    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start(ctx: Context, intervalMs: Long = 60_000L) {
        if (job?.isActive == true) return
        val appCtx = ctx.applicationContext
        job = scope.launch {
            while (isActive) {
                try {
                    sendBeats(appCtx)
                } catch (e: Exception) {
                    // Next round. Heartbeats are best-effort by design.
                }
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun sendBeats(ctx: Context) {
        val reg = try {
            DispatchManager.registry()
        } catch (e: Exception) {
            return
        }
        val batt = batteryPct(ctx)
        for (dev in reg.all()) {
            DispatchManager.dispatchJson(dev, "hb", JSONObject().put("batt", batt))
        }
    }

    fun onHeartbeat(deviceIdHex: String, name: String, batteryPct: Int) {
        val now = System.currentTimeMillis()
        val cur = _board.value.toMutableList()
        val i = cur.indexOfFirst { it.deviceIdHex == deviceIdHex }
        val info = HeartbeatInfo(deviceIdHex, name, now, batteryPct)
        if (i >= 0) cur[i] = info else cur.add(info)
        _board.value = cur
        AutomationEngine.onHeartbeat(deviceIdHex)
    }

    private fun batteryPct(ctx: Context): Int {
        return try {
            val bm = ctx.getSystemService(BatteryManager::class.java)
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (e: Exception) {
            -1
        }
    }
}
