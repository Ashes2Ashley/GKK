package com.example.data.fiveg

import com.squareup.moshi.JsonClass
import java.util.UUID

enum class NetworkTopology(val displayName: String, val frequencyDesc: String) {
    GROUND_CELLULAR_5G("Ground 5G NR", "Sub-6GHz / mmWave Macro"),
    AIRBORNE_LOS_LINK("Airborne LOS Mesh", "Directional C-Band 5.8 GHz"),
    SATELLITE_NTN("Satellite NTN", "3GPP Rel-17 LEO S-Band"),
    HYBRID_MULTI_PATH("Hybrid Multi-Path", "Ground + Airborne Aggregated")
}

enum class FiveGSlice(val label: String, val sst: Int, val sd: String, val default5qi: Int, val slaDesc: String) {
    URLLC("URLLC Mission-Critical", 1, "0x000001", 1, "< 5ms Latency, 99.999% Reliability"),
    EMBB("eMBB High-Throughput", 2, "0x000002", 8, "Tactical HD Video & Sensor Payloads"),
    MMTC("mMTC Drone Swarm Telemetry", 3, "0x000003", 9, "Ultra-dense low-power airborne telemetry"),
    TACTICAL_ISOLATED("Tactical Air-Ground Encrypted", 4, "0x000004", 65, "Zero-Trust Air-to-Ground Secure Slice")
}

@JsonClass(generateAdapter = true)
data class RfSignalMetrics(
    val rsrpDbm: Int = -82,
    val rsrqDb: Double = -9.5,
    val sinrDb: Double = 18.2,
    val cqi: Int = 12,
    val frequencyBand: String = "n78 (3.5 GHz)",
    val bandwidthMhz: Int = 100,
    val modulationScheme: String = "256-QAM",
    val cellId: String = "gNB-7491-Sector-A"
) {
    val signalStrengthPercent: Int
        get() = ((rsrpDbm + 140) * 100 / 96).coerceIn(0, 100)

    val signalQualityCategory: String
        get() = when {
            rsrpDbm >= -85 && sinrDb >= 15 -> "EXCELLENT"
            rsrpDbm >= -100 && sinrDb >= 8 -> "GOOD"
            rsrpDbm >= -112 && sinrDb >= 0 -> "FAIR"
            else -> "CRITICAL_DEGRADED"
        }
}

@JsonClass(generateAdapter = true)
data class LinkPerformanceState(
    val activeTopology: NetworkTopology = NetworkTopology.GROUND_CELLULAR_5G,
    val activeSlice: FiveGSlice = FiveGSlice.URLLC,
    val rfMetrics: RfSignalMetrics = RfSignalMetrics(),
    val downlinkMbps: Double = 142.5,
    val uplinkMbps: Double = 68.0,
    val latencyMs: Double = 6.4,
    val jitterMs: Double = 1.8,
    val packetLossPercent: Double = 0.12,
    val airborneAltitudeMeters: Int = 420,
    val airborneGroundDistanceKm: Double = 3.8,
    val dopplerShiftHz: Double = 42.0,
    val autoAdaptationEnabled: Boolean = true,
    val fecRedundancyPercent: Int = 15,
    val linkHealthScore: Int = 94,
    val handoverLatencyMs: Long = 18
)

data class AdaptationEvent(
    val id: String = UUID.randomUUID().toString().take(8),
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,
    val reason: String,
    val fromTopology: NetworkTopology,
    val toTopology: NetworkTopology,
    val resultingLatencyMs: Double,
    val resolvedStatus: String = "SUCCESS"
)
