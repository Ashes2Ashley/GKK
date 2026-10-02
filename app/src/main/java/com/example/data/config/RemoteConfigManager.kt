package com.example.data.config

import com.example.data.fiveg.FiveGAdaptabilityEngine
import com.example.data.fiveg.FiveGSlice
import com.example.data.fiveg.NetworkTopology
import com.example.data.webrtc.AirborneWebRtcManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class TacticalPreset(val title: String, val desc: String) {
    AUTONOMOUS_TACTICAL("Autonomous Tactical", "Dynamic 5G/Airborne auto-handoff with URLLC QoS"),
    AIRBORNE_PRIORITY("Airborne LOS Priority", "Optimized for UAV drone video & sensor stream"),
    GROUND_LOW_POWER("Ground Low-Power", "Conservative power consumption via Ground 5G Macro"),
    HIGH_SECURITY_LOCKDOWN("High-Security Lockdown", "Isolated private slice, strict cert pinning & DTLS")
}

data class SystemLogItem(
    val id: String = UUID.randomUUID().toString().take(6),
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()),
    val level: String, // INFO, SUCCESS, WARN, SECURE
    val category: String, // 5G-NR, WEBRTC, SIM-OTA, KALI-AUDIT, SECURITY
    val message: String
)

data class ModularRemoteConfig(
    val activePreset: TacticalPreset = TacticalPreset.AUTONOMOUS_TACTICAL,
    val webrtcBitrateCeilingKbps: Int = 12000,
    val slicePreference: FiveGSlice = FiveGSlice.URLLC,
    val autoAdaptation: Boolean = true,
    val fecRedundancyPercent: Int = 15,
    val strictCertPinning: Boolean = true,
    val samplingIntervalMs: Long = 500,
    val verboseLogging: Boolean = true
)

object RemoteConfigManager {

    private val _config = MutableStateFlow(ModularRemoteConfig())
    val config: StateFlow<ModularRemoteConfig> = _config.asStateFlow()

    private val _logs = MutableStateFlow<List<SystemLogItem>>(
        listOf(
            SystemLogItem(level = "SUCCESS", category = "5G-NR", message = "5G NR Standalone cell gNB-7491 synchronized (SST: 1 URLLC)"),
            SystemLogItem(level = "SECURE", category = "SECURITY", message = "DTLS 1.3 / SRTP-AEAD-AES-256-GCM cipher suite negotiated"),
            SystemLogItem(level = "INFO", category = "WEBRTC", message = "Airborne UAV Node AERO-VALKYRIE-09 P2P data channel ready"),
            SystemLogItem(level = "SECURE", category = "SIM-OTA", message = "Administrative SCP03 secure channel verified for physical UICC Slot 1")
        )
    )
    val logs: StateFlow<List<SystemLogItem>> = _logs.asStateFlow()

    fun applyPreset(preset: TacticalPreset) {
        when (preset) {
            TacticalPreset.AUTONOMOUS_TACTICAL -> {
                _config.value = _config.value.copy(
                    activePreset = preset,
                    webrtcBitrateCeilingKbps = 12000,
                    slicePreference = FiveGSlice.URLLC,
                    autoAdaptation = true,
                    fecRedundancyPercent = 15,
                    strictCertPinning = true
                )
                FiveGAdaptabilityEngine.toggleAutoAdaptation(true)
                FiveGAdaptabilityEngine.setSlice(FiveGSlice.URLLC)
                FiveGAdaptabilityEngine.setFecRedundancy(15)
                AirborneWebRtcManager.setBitrateCeiling(12000)
            }
            TacticalPreset.AIRBORNE_PRIORITY -> {
                _config.value = _config.value.copy(
                    activePreset = preset,
                    webrtcBitrateCeilingKbps = 24000,
                    slicePreference = FiveGSlice.EMBB,
                    autoAdaptation = false,
                    fecRedundancyPercent = 25,
                    strictCertPinning = true
                )
                FiveGAdaptabilityEngine.setTopology(NetworkTopology.AIRBORNE_LOS_LINK, "Preset Airborne Priority Applied")
                FiveGAdaptabilityEngine.setSlice(FiveGSlice.EMBB)
                FiveGAdaptabilityEngine.setFecRedundancy(25)
                AirborneWebRtcManager.setBitrateCeiling(24000)
            }
            TacticalPreset.GROUND_LOW_POWER -> {
                _config.value = _config.value.copy(
                    activePreset = preset,
                    webrtcBitrateCeilingKbps = 2000,
                    slicePreference = FiveGSlice.MMTC,
                    autoAdaptation = false,
                    fecRedundancyPercent = 5,
                    strictCertPinning = false
                )
                FiveGAdaptabilityEngine.setTopology(NetworkTopology.GROUND_CELLULAR_5G, "Preset Ground Low-Power Applied")
                FiveGAdaptabilityEngine.setSlice(FiveGSlice.MMTC)
                FiveGAdaptabilityEngine.setFecRedundancy(5)
                AirborneWebRtcManager.setBitrateCeiling(2000)
            }
            TacticalPreset.HIGH_SECURITY_LOCKDOWN -> {
                _config.value = _config.value.copy(
                    activePreset = preset,
                    webrtcBitrateCeilingKbps = 8000,
                    slicePreference = FiveGSlice.TACTICAL_ISOLATED,
                    autoAdaptation = true,
                    fecRedundancyPercent = 30,
                    strictCertPinning = true
                )
                FiveGAdaptabilityEngine.setSlice(FiveGSlice.TACTICAL_ISOLATED)
                FiveGAdaptabilityEngine.setFecRedundancy(30)
                AirborneWebRtcManager.setBitrateCeiling(8000)
            }
        }
        addLog("WARN", "CONFIG", "Applied modular configuration preset: ${preset.title}")
    }

    fun setBitrateCeiling(kbps: Int) {
        _config.value = _config.value.copy(webrtcBitrateCeilingKbps = kbps)
        AirborneWebRtcManager.setBitrateCeiling(kbps)
        addLog("INFO", "WEBRTC", "Updated WebRTC bitrate ceiling to $kbps Kbps")
    }

    fun toggleStrictCertPinning(enabled: Boolean) {
        _config.value = _config.value.copy(strictCertPinning = enabled)
        addLog("SECURE", "SECURITY", "SPKI Certificate Pinning set to: $enabled")
    }

    fun toggleAutoAdaptation(enabled: Boolean) {
        _config.value = _config.value.copy(autoAdaptation = enabled)
        FiveGAdaptabilityEngine.toggleAutoAdaptation(enabled)
        addLog("INFO", "5G-NR", "Automated 5G link adaptability toggled: $enabled")
    }

    fun addLog(level: String, category: String, message: String) {
        val item = SystemLogItem(level = level, category = category, message = message)
        _logs.value = (listOf(item) + _logs.value).take(100)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
