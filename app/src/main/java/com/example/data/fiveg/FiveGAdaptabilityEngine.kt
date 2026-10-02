package com.example.data.fiveg

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

object FiveGAdaptabilityEngine {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var telemetryJob: Job? = null

    private val _linkState = MutableStateFlow(LinkPerformanceState())
    val linkState: StateFlow<LinkPerformanceState> = _linkState.asStateFlow()

    private val _adaptationHistory = MutableStateFlow<List<AdaptationEvent>>(
        listOf(
            AdaptationEvent(
                type = "INITIAL_REGISTRATION",
                reason = "5G NR gNodeB Standalone (SA) URLLC Handshake established",
                fromTopology = NetworkTopology.GROUND_CELLULAR_5G,
                toTopology = NetworkTopology.GROUND_CELLULAR_5G,
                resultingLatencyMs = 6.4
            )
        )
    )
    val adaptationHistory: StateFlow<List<AdaptationEvent>> = _adaptationHistory.asStateFlow()

    init {
        startTelemetryLoop()
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (true) {
                delay(1200)
                updateTelemetryTick()
            }
        }
    }

    private fun updateTelemetryTick() {
        val current = _linkState.value

        // Small realistic RF jitter
        val jitterVariation = (Random.nextDouble(-0.3, 0.4))
        val currentJitter = (current.jitterMs + jitterVariation).coerceIn(0.8, 12.0)

        // Random minor RF fluctuation
        val rsrpVariation = Random.nextInt(-2, 3)
        val newRsrp = (current.rfMetrics.rsrpDbm + rsrpVariation).coerceIn(-130, -50)
        val newSinr = (current.rfMetrics.sinrDb + Random.nextDouble(-0.5, 0.5)).coerceIn(-5.0, 30.0)

        // Calculate link health score based on RF and loss
        val health = calculateHealthScore(newRsrp, newSinr, current.packetLossPercent, current.latencyMs)

        var updatedMetrics = current.rfMetrics.copy(
            rsrpDbm = newRsrp,
            sinrDb = (newSinr * 10).roundToInt() / 10.0
        )

        // Downlink / Uplink throughput based on topology & CQI
        val baseDown = when (current.activeTopology) {
            NetworkTopology.GROUND_CELLULAR_5G -> 135.0 + Random.nextDouble(-8.0, 12.0)
            NetworkTopology.AIRBORNE_LOS_LINK -> 95.0 + Random.nextDouble(-5.0, 8.0)
            NetworkTopology.SATELLITE_NTN -> 28.0 + Random.nextDouble(-3.0, 4.0)
            NetworkTopology.HYBRID_MULTI_PATH -> 210.0 + Random.nextDouble(-10.0, 15.0)
        }

        val baseLatency = when (current.activeTopology) {
            NetworkTopology.GROUND_CELLULAR_5G -> 5.8 + Random.nextDouble(-0.4, 0.8)
            NetworkTopology.AIRBORNE_LOS_LINK -> 11.2 + Random.nextDouble(-0.8, 1.2)
            NetworkTopology.SATELLITE_NTN -> 38.0 + Random.nextDouble(-2.0, 3.5)
            NetworkTopology.HYBRID_MULTI_PATH -> 7.1 + Random.nextDouble(-0.6, 1.0)
        }

        // Auto-adaptation logic
        if (current.autoAdaptationEnabled) {
            if (current.packetLossPercent > 2.0 || newRsrp < -115) {
                // Trigger automated failover to alternate link
                val nextTopology = when (current.activeTopology) {
                    NetworkTopology.GROUND_CELLULAR_5G -> NetworkTopology.AIRBORNE_LOS_LINK
                    NetworkTopology.AIRBORNE_LOS_LINK -> NetworkTopology.HYBRID_MULTI_PATH
                    NetworkTopology.HYBRID_MULTI_PATH -> NetworkTopology.SATELLITE_NTN
                    NetworkTopology.SATELLITE_NTN -> NetworkTopology.GROUND_CELLULAR_5G
                }
                triggerAutomatedFailover(nextTopology, "RF degradation threshold triggered (RSRP: $newRsrp dBm, Loss: ${current.packetLossPercent}%)")
                return
            }
        }

        _linkState.value = current.copy(
            rfMetrics = updatedMetrics,
            downlinkMbps = (baseDown * 10).roundToInt() / 10.0,
            uplinkMbps = ((baseDown * 0.45) * 10).roundToInt() / 10.0,
            latencyMs = (baseLatency * 10).roundToInt() / 10.0,
            jitterMs = (currentJitter * 10).roundToInt() / 10.0,
            linkHealthScore = health
        )
    }

    private fun calculateHealthScore(rsrp: Int, sinr: Double, loss: Double, latency: Double): Int {
        var score = 100
        if (rsrp < -95) score -= 15
        if (rsrp < -110) score -= 25
        if (sinr < 10) score -= 15
        if (sinr < 3) score -= 20
        if (loss > 0.5) score -= 15
        if (loss > 2.0) score -= 30
        if (latency > 20) score -= 10
        if (latency > 50) score -= 20
        return score.coerceIn(5, 100)
    }

    fun setTopology(topology: NetworkTopology, manualReason: String = "Operator Manual Switch") {
        val prev = _linkState.value.activeTopology
        if (prev == topology) return

        val targetFreq = when (topology) {
            NetworkTopology.GROUND_CELLULAR_5G -> "n78 (3.5 GHz Sub-6)"
            NetworkTopology.AIRBORNE_LOS_LINK -> "Directional C-Band (5.8 GHz)"
            NetworkTopology.SATELLITE_NTN -> "S-Band NTN (2.1 GHz LEO)"
            NetworkTopology.HYBRID_MULTI_PATH -> "Aggregated n78 + C-Band"
        }

        val targetRsrp = when (topology) {
            NetworkTopology.GROUND_CELLULAR_5G -> -78
            NetworkTopology.AIRBORNE_LOS_LINK -> -72
            NetworkTopology.SATELLITE_NTN -> -94
            NetworkTopology.HYBRID_MULTI_PATH -> -69
        }

        _linkState.value = _linkState.value.copy(
            activeTopology = topology,
            packetLossPercent = 0.08,
            rfMetrics = _linkState.value.rfMetrics.copy(
                frequencyBand = targetFreq,
                rsrpDbm = targetRsrp,
                sinrDb = 21.0
            ),
            handoverLatencyMs = Random.nextLong(12, 28)
        )

        logAdaptation(
            type = "TOPOLOGY_HANDOVER",
            reason = manualReason,
            from = prev,
            to = topology,
            resultingLatency = _linkState.value.latencyMs
        )
    }

    fun setSlice(slice: FiveGSlice) {
        val prev = _linkState.value.activeSlice
        _linkState.value = _linkState.value.copy(activeSlice = slice)

        logAdaptation(
            type = "SLICE_RECONFIG",
            reason = "Allocated slice ${slice.label} (SST: ${slice.sst}, 5QI: ${slice.default5qi})",
            from = _linkState.value.activeTopology,
            to = _linkState.value.activeTopology,
            resultingLatency = if (slice == FiveGSlice.URLLC) 4.8 else 12.0
        )
    }

    fun toggleAutoAdaptation(enabled: Boolean) {
        _linkState.value = _linkState.value.copy(autoAdaptationEnabled = enabled)
    }

    fun setFecRedundancy(percent: Int) {
        _linkState.value = _linkState.value.copy(fecRedundancyPercent = percent.coerceIn(0, 50))
    }

    fun simulateRfDegradation() {
        // Degrade link intentionally to test automatic handoff
        _linkState.value = _linkState.value.copy(
            packetLossPercent = 4.2,
            rfMetrics = _linkState.value.rfMetrics.copy(
                rsrpDbm = -118,
                sinrDb = -2.4
            ),
            latencyMs = 38.5,
            linkHealthScore = 32
        )
        if (_linkState.value.autoAdaptationEnabled) {
            scope.launch {
                delay(800)
                val target = if (_linkState.value.activeTopology == NetworkTopology.GROUND_CELLULAR_5G) {
                    NetworkTopology.AIRBORNE_LOS_LINK
                } else {
                    NetworkTopology.GROUND_CELLULAR_5G
                }
                triggerAutomatedFailover(target, "Auto-Adaptive engine detected critical RF fade (Loss: 4.2%, RSRP: -118 dBm)")
            }
        }
    }

    private fun triggerAutomatedFailover(targetTopology: NetworkTopology, reason: String) {
        val from = _linkState.value.activeTopology
        _linkState.value = _linkState.value.copy(
            activeTopology = targetTopology,
            packetLossPercent = 0.05,
            latencyMs = if (targetTopology == NetworkTopology.AIRBORNE_LOS_LINK) 10.4 else 6.2,
            rfMetrics = _linkState.value.rfMetrics.copy(
                rsrpDbm = -75,
                sinrDb = 22.5,
                frequencyBand = if (targetTopology == NetworkTopology.AIRBORNE_LOS_LINK) "Directional C-Band (5.8 GHz)" else "n78 (3.5 GHz Sub-6)"
            ),
            linkHealthScore = 96,
            handoverLatencyMs = Random.nextLong(14, 25)
        )

        logAdaptation(
            type = "AUTO_ADAPTIVE_FAILOVER",
            reason = reason,
            from = from,
            to = targetTopology,
            resultingLatency = _linkState.value.latencyMs
        )
    }

    private fun logAdaptation(
        type: String,
        reason: String,
        from: NetworkTopology,
        to: NetworkTopology,
        resultingLatency: Double
    ) {
        val event = AdaptationEvent(
            type = type,
            reason = reason,
            fromTopology = from,
            toTopology = to,
            resultingLatencyMs = resultingLatency
        )
        _adaptationHistory.value = listOf(event) + _adaptationHistory.value.take(40)
    }
}
