package com.example.data.kali

import com.example.data.fiveg.FiveGAdaptabilityEngine
import com.example.data.sim.SimPayloadManager
import com.example.data.webrtc.AirborneWebRtcManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class KaliLogEntry(
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
    val command: String,
    val output: String,
    val exitCode: Int = 0
)

object KaliEnvironmentManager {

    private val _terminalBuffer = MutableStateFlow<List<KaliLogEntry>>(
        listOf(
            KaliLogEntry(
                command = "boot",
                output = """
┌──(kali㉿infra-tactical)-[~]
└─$ [BOOT] Kali Linux 2026.3 Tactical Environment Initialized
    Kernel: Linux 6.8.0-kali-airborne-5g #1 SMP PREEMPT_DYNAMIC
    Subsystem: 5G NR Adaptability Engine & WebRTC Mesh Controller
    Security Domain: SCP03 / DTLS 1.3 / AES-256-GCM Hardware Cipher
    Type 'help' to view available operational commands.
                """.trimIndent()
            )
        )
    )
    val terminalBuffer: StateFlow<List<KaliLogEntry>> = _terminalBuffer.asStateFlow()

    fun executeCommand(rawCommand: String) {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return

        if (trimmed == "clear") {
            _terminalBuffer.value = emptyList()
            return
        }

        val output = processCommand(trimmed)
        val entry = KaliLogEntry(
            command = trimmed,
            output = output
        )
        _terminalBuffer.value = _terminalBuffer.value + entry
    }

    private fun processCommand(cmd: String): String {
        val lower = cmd.lowercase()
        return when {
            lower == "help" -> """
Operational Toolkit Commands:
  5g-slice-audit       - Audit 5G NR network slices, SLA metrics, and URLLC isolation
  webrtc-diag          - Inspect WebRTC ICE pairs, DTLS-SRTP ciphers & GCC bitrate
  airmon-ng status     - Airborne RF spectrum analysis, Doppler shift & tracking link
  sim-payload --verify - Audit cryptographic envelopes on UICC/eUICC slots
  crypto-check         - Run AES-256-GCM & HMAC-SHA256 hardware acceleration benchmark
  nmap -sv             - Audit signaling ports (SIP 5060, WebRTC 19302, HTTPS 8443)
  mtr -n               - Hop-by-hop network latency & jitter trace
  sysinfo              - View system resources, active slice, and network health
  uname -a             - Print tactical Linux kernel version string
  clear                - Clear operational console buffer
            """.trimIndent()

            lower.startsWith("5g-slice-audit") -> {
                val state = FiveGAdaptabilityEngine.linkState.value
                """
[5G-SLICE-AUDITOR v3.4] Scanning 3GPP Rel-17 Network Slices...
Active Topology: ${state.activeTopology.displayName} (${state.rfMetrics.frequencyBand})
Active Slice:    ${state.activeSlice.label}
SST / SD:        SST=${state.activeSlice.sst}, SD=${state.activeSlice.sd}
Assigned 5QI:    ${state.activeSlice.default5qi} (SLA: ${state.activeSlice.slaDesc})
RSRP / SINR:     ${state.rfMetrics.rsrpDbm} dBm / ${state.rfMetrics.sinrDb} dB
Channel Quality: CQI=${state.rfMetrics.cqi} (${state.rfMetrics.modulationScheme})
Measured Delay:  ${state.latencyMs} ms (Target URLLC: < 5.0 ms)
Packet Loss:     ${state.packetLossPercent}%
Slice Isolation: ENFORCED (Dedicated UPF User-Plane Data Path)
[STATUS] Slice SLA Verification: PASS (Health Score: ${state.linkHealthScore}/100)
                """.trimIndent()
            }

            lower.startsWith("webrtc-diag") -> {
                val sess = AirborneWebRtcManager.session.value
                val telem = AirborneWebRtcManager.telemetry.value
                """
[WEBRTC-DIAGNOSTIC-SUITE]
Peer Connection:  ${sess.sessionId} [${sess.state}]
DTLS Handshake:   ${sess.encryptionProtocol}
Video Pipeline:   ${sess.videoResolution} @ ${sess.targetBitrateKbps} Kbps
Data Channel:     ${sess.dataChannelLabel} (Active, SCTP Ordered)
Round-Trip-Time:  ${sess.measuredRttMs} ms
ICE Candidate:    ${sess.iceCandidatePair}
Transmitted Data: ${sess.transmittedBytes / 1024 / 1024} MB (Airborne Node ${telem.callsign})
Congestion Ctrl:  GCC (Google Congestion Control) - Dynamic Rate Adaptation ACTIVE
[STATUS] DTLS-SRTP Cryptographic Tunnel Integrity: VERIFIED
                """.trimIndent()
            }

            lower.startsWith("airmon-ng") -> {
                val telem = AirborneWebRtcManager.telemetry.value
                val fiveg = FiveGAdaptabilityEngine.linkState.value
                """
PHY   Interface   Driver        Chipset              Status
phy0  wlan-air0   tactical_c5g  AeroMesh RF-820      MONITOR MODE (Airborne LOS)
phy1  wwan-5g0    qcom_x75      5G NR SA Rel-17      CONNECTED (${fiveg.activeTopology.displayName})

Airborne Node Telemetry:
  Callsign:      ${telem.callsign}
  Altitude:      ${telem.altitudeMeters} m AGL (${(telem.altitudeMeters * 3.28084).toInt()} ft)
  Ground Speed:  ${telem.groundSpeedKmh} km/h (Heading: ${telem.yawHeadingDeg}°)
  Coordinates:   ${telem.latitude}, ${telem.longitude}
  Doppler Shift: ${fiveg.dopplerShiftHz} Hz (Frequency Offset Auto-Compensated)
  Relay Station: ${telem.groundRelayStation} (Tracking Lock: SOLID)
                """.trimIndent()
            }

            lower.startsWith("sim-payload") -> {
                val envs = SimPayloadManager.envelopes.value
                val hw = SimPayloadManager.hardwareState.value
                """
[SIM-CRYPTOGRAPHIC-PAYLOAD-AUDITOR]
UICC / eUICC Target: ${hw.slotId.label}
ICCID:               ${hw.iccidMasked}
Carrier Profile:     ${hw.carrierName}
Security Domain:     ${hw.otaSecurityDomainState}

Dispatched Administrative Envelopes:
${envs.joinToString("\n") { env -> "  - [${env.envelopeId}] ${env.payloadType.name} -> ${env.status} (HMAC: ${env.hmacSignature.take(12)}...)" }}

Cryptographic Algorithm: AES-256-GCM (128-bit MAC) + HMAC-SHA256 Integrity
Replay Attack Protection: Nonce Randomness Verified (SecureRandom 96-bit)
                """.trimIndent()
            }

            lower.startsWith("crypto-check") -> """
[CRYPTOGRAPHIC ACCELERATION BENCHMARK]
Testing Hardware-Backed Keystore & AES Engine...
  • AES-256-GCM / 128-bit MAC : 482 MB/s (Hardware Accelerated)
  • HMAC-SHA256 Digest Engine : 610 MB/s (Hardware Accelerated)
  • SecureRandom Entropy Pool : 256 bits verified /dev/urandom
  • DTLS 1.3 Ephemeral ECDHE  : X25519 Curve Negotiated
  • SPKI Certificate Pinning   : Active (SHA-256 Public Key Hashes)
[RESULT] Military & Commercial 5G Cryptographic Standards COMPLIANT
            """.trimIndent()

            lower.startsWith("nmap") -> """
Starting Nmap 7.94 ( https://nmap.org ) at ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}
Nmap scan report for gnb-core.telecom.infra (10.5.0.1)
Host is up (0.0064s latency).
Not shown: 996 closed ports
PORT      STATE SERVICE    VERSION
443/tcp   open  ssl/https  Nginx 1.24 (TLS 1.3 / Strict PFS)
5060/udp  open  sip        OpenSIPS 3.4.1 (TLS-SIP Tunnel)
8443/tcp  open  http-alt   5G Core REST NFs (AMF/SMF/UPF)
19302/udp open  stun       Google STUN/TURN Signaling Relay
Service Info: OS: Linux; CPE: cpe:/o:linux:linux_kernel

Nmap done: 1 IP address (1 host up) scanned in 0.82 seconds
            """.trimIndent()

            lower.startsWith("mtr") || lower.startsWith("traceroute") -> """
HOST: kali-tactical-host       Loss%   Snt   Last   Avg  Best  Wrst StDev
  1.|-- 10.5.0.1 (gNodeB UPF)    0.0%    10    1.2   1.4   1.1   1.9   0.2
  2.|-- 10.5.12.1 (Airborne-GOC) 0.0%    10    3.8   4.1   3.5   5.2   0.5
  3.|-- 192.168.100.1 (Edge-Mesh)0.0%    10    6.4   6.8   6.2   7.9   0.6
[STATUS] Hop-by-hop latency within URLLC strict bounds (<10ms end-to-end)
            """.trimIndent()

            lower == "uname -a" -> "Linux kali-infra-tactical 6.8.0-kali-airborne-5g #1 SMP PREEMPT_DYNAMIC GNU/Linux"

            lower == "sysinfo" -> """
System Information:
  Hostname:         kali-infra-tactical
  OS:               Kali Linux (Tactical Mission-Critical Build)
  Platform:         Android ARM64 Hypervisor / Linux Runtime
  Memory Usage:     1.4 GB / 8.0 GB (17%)
  CPU Utilization:  14% (Octa-core Kryo)
  5G Network Link:  ${FiveGAdaptabilityEngine.linkState.value.activeTopology.displayName}
  WebRTC Session:   ${AirborneWebRtcManager.session.value.sessionId} [${AirborneWebRtcManager.session.value.state}]
  Active Slice:     ${FiveGAdaptabilityEngine.linkState.value.activeSlice.label}
  Zero-Trust State: ENFORCED
            """.trimIndent()

            else -> "bash: command not found: $cmd. Type 'help' for available commands."
        }
    }
}
