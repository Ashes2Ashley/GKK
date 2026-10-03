package com.example.data.dispatch

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
 * Automation event bus + the first self-watch rule.
 *
 * The law: the system watches itself so the operator doesn't babysit it.
 * Components raise events here; the Live monitors render them in real time.
 * Built-in rule: if a paired device's heartbeat goes silent past
 * [heartbeatTimeoutMs], raise one WARN per device (cleared on next beat).
 */
data class AlertEvent(val at: Long, val severity: String, val text: String)

object AutomationEngine {

    private val _events = MutableStateFlow<List<AlertEvent>>(emptyList())
    val events: StateFlow<List<AlertEvent>> = _events.asStateFlow()

    private val lastBeat = mutableMapOf<String, Long>()
    private val alerted = mutableSetOf<String>()
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var heartbeatTimeoutMs: Long = 10 * 60 * 1000L

    fun raise(severity: String, text: String) {
        _events.value = (_events.value + AlertEvent(System.currentTimeMillis(), severity, text))
            .takeLast(100)
    }

    fun onHeartbeat(deviceIdHex: String) {
        synchronized(lastBeat) {
            lastBeat[deviceIdHex] = System.currentTimeMillis()
            if (alerted.remove(deviceIdHex)) {
                raise("OK", "heartbeat resumed: $deviceIdHex")
            }
        }
    }

    fun startWatchdog() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                delay(60_000)
                val now = System.currentTimeMillis()
                val silent = synchronized(lastBeat) {
                    lastBeat.filter { now - it.value > heartbeatTimeoutMs }.keys.toList()
                }
                for (id in silent) {
                    val first = synchronized(alerted) { alerted.add(id) }
                    if (first) raise("WARN", "heartbeat missed > ${heartbeatTimeoutMs / 60000}min: $id")
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
