package com.example.data.realtime

import android.content.Context
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

/**
 * Real-time monitor framework.
 *
 * LAW: every monitor reports live data from a real API, or reports
 * UNAVAILABLE with the honest reason (permission denied, hardware absent,
 * radio off). A monitor never invents a reading.
 */
enum class MonitorStatus { OK, UNAVAILABLE, ERROR }

data class MonitorReading(
    val name: String,
    val summary: String,
    val detail: String,
    val status: MonitorStatus,
    val updatedAt: Long
)

interface RealtimeMonitor {
    val name: String
    val reading: StateFlow<MonitorReading>
    fun start(ctx: Context)
    fun stop()
}

abstract class BaseMonitor(override val name: String) : RealtimeMonitor {

    private val _reading = MutableStateFlow(
        MonitorReading(name, "—", "not started", MonitorStatus.UNAVAILABLE, 0L)
    )
    override val reading: StateFlow<MonitorReading> = _reading.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    protected fun emit(
        summary: String,
        detail: String = "",
        status: MonitorStatus = MonitorStatus.OK
    ) {
        _reading.value = MonitorReading(name, summary, detail, status, System.currentTimeMillis())
    }

    /** Polling loop; survives individual failures (reports ERROR, keeps going). */
    protected fun launchLoop(periodMs: Long, block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                try {
                    block()
                } catch (e: Exception) {
                    emit("error", e.message ?: e.javaClass.simpleName, MonitorStatus.ERROR)
                }
                delay(periodMs)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        onStop()
    }

    protected open fun onStop() {}
}

object MonitorRegistry {

    val all: List<RealtimeMonitor> by lazy {
        listOf(
            // comms (live secure-channel telemetry)
            ChatFeedMonitor,
            SmsTrafficMonitor,
            LinkTickerMonitor,
            ConsoleStreamMonitor,
            AlertFeedMonitor,
            RoundTripMonitor,
            FleetBoardMonitor,
            // system (live device telemetry)
            BatteryMonitor,
            SensorMonitor,
            GpsMonitor,
            TrafficMonitor,
            WifiMonitor,
            CellMonitor,
            BleMonitor,
            NtpMonitor
        )
    }

    init {
        check(all.size == 15) { "expected 15 monitors, got ${all.size}" }
    }

    fun get(name: String): RealtimeMonitor? =
        all.find { it.name.equals(name, ignoreCase = true) }

    fun startAll(ctx: Context) = all.forEach {
        try {
            it.start(ctx.applicationContext)
        } catch (e: Exception) {
            // One bad monitor never blocks the rest.
        }
    }

    fun stopAll() = all.forEach {
        try {
            it.stop()
        } catch (e: Exception) {
        }
    }
}
