package com.example.data.webrtc

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

data class AirborneTelemetry(
    val callsign: String = "AERO-VALKYRIE-09",
    val altitudeMeters: Int = 1250,
    val groundSpeedKmh: Double = 184.2,
    val pitchDeg: Double = 2.4,
    val rollDeg: Double = -1.1,
    val yawHeadingDeg: Double = 274.5,
    val latitude: Double = 34.0522,
    val longitude: Double = -118.2437,
    val batteryPercent: Int = 88,
    val rfLinkQualityPercent: Int = 96,
    val groundRelayStation: String = "GOC-ALPHA-SECTOR-4"
)

enum class WebRtcLinkState {
    DISCONNECTED,
    ICE_GATHERING,
    DTLS_HANDSHAKE,
    CONNECTED_SECURE,
    CONGESTION_ADAPTATION,
    FAILED
}

data class WebRtcInfrastructureSession(
    val sessionId: String = UUID.randomUUID().toString().take(8),
    val state: WebRtcLinkState = WebRtcLinkState.CONNECTED_SECURE,
    val encryptionProtocol: String = "DTLS 1.3 / SRTP-AEAD-AES-256-GCM",
    val targetBitrateKbps: Int = 8500,
    val measuredRttMs: Long = 14,
    val packetLossFraction: Double = 0.002,
    val videoResolution: String = "1080p (60 FPS H.265 Tactical)",
    val dataChannelLabel: String = "airborne-telemetry-v2",
    val iceCandidatePair: String = "UDP 192.168.10.42:50004 <-> 10.5.0.18:49152 (Relayed)",
    val sdpOffer: String = "",
    val sdpAnswer: String = "",
    val transmittedBytes: Long = 42819200L,
    val receivedBytes: Long = 8912400L,
    val telemetryHistory: List<String> = emptyList()
)

object AirborneWebRtcManager {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var telemetryJob: Job? = null

    private val _telemetry = MutableStateFlow(AirborneTelemetry())
    val telemetry: StateFlow<AirborneTelemetry> = _telemetry.asStateFlow()

    private val _session = MutableStateFlow(
        WebRtcInfrastructureSession(
            sdpOffer = generateOfferSdp(),
            sdpAnswer = generateAnswerSdp()
        )
    )
    val session: StateFlow<WebRtcInfrastructureSession> = _session.asStateFlow()

    init {
        startTelemetrySimulation()
    }

    private fun startTelemetrySimulation() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (true) {
                delay(1000)
                tickTelemetry()
            }
        }
    }

    private fun tickTelemetry() {
        val curr = _telemetry.value
        val speedVariation = Random.nextDouble(-1.2, 1.5)
        val altitudeVariation = Random.nextInt(-3, 4)
        val pitchVariation = Random.nextDouble(-0.3, 0.3)
        val rollVariation = Random.nextDouble(-0.4, 0.4)
        val headingVariation = Random.nextDouble(-0.2, 0.5)

        val updated = curr.copy(
            altitudeMeters = (curr.altitudeMeters + altitudeVariation).coerceIn(800, 3000),
            groundSpeedKmh = ((curr.groundSpeedKmh + speedVariation) * 10).roundToInt() / 10.0,
            pitchDeg = ((curr.pitchDeg + pitchVariation) * 10).roundToInt() / 10.0,
            rollDeg = ((curr.rollDeg + rollVariation) * 10).roundToInt() / 10.0,
            yawHeadingDeg = ((curr.yawHeadingDeg + headingVariation) % 360 * 10).roundToInt() / 10.0,
            batteryPercent = (curr.batteryPercent - if (Random.nextInt(0, 15) == 0) 1 else 0).coerceAtLeast(15)
        )
        _telemetry.value = updated

        // WebRTC byte updates & GCC congestion adjustment
        val sess = _session.value
        if (sess.state == WebRtcLinkState.CONNECTED_SECURE) {
            val bytesDelta = (sess.targetBitrateKbps * 1024L / 8L / 2L)
            val rttVariance = Random.nextLong(-2, 3)
            val newRtt = (sess.measuredRttMs + rttVariance).coerceIn(6, 60)

            _session.value = sess.copy(
                transmittedBytes = sess.transmittedBytes + bytesDelta,
                receivedBytes = sess.receivedBytes + (bytesDelta / 8),
                measuredRttMs = newRtt,
                telemetryHistory = (listOf("[${System.currentTimeMillis()}] Telemetry Frame ALT=${updated.altitudeMeters}m HDG=${updated.yawHeadingDeg}° RTT=${newRtt}ms") + sess.telemetryHistory).take(30)
            )
        }
    }

    fun initiateSignaling() {
        scope.launch {
            _session.value = _session.value.copy(state = WebRtcLinkState.ICE_GATHERING)
            delay(400)
            _session.value = _session.value.copy(
                state = WebRtcLinkState.DTLS_HANDSHAKE,
                sdpOffer = generateOfferSdp()
            )
            delay(500)
            _session.value = _session.value.copy(
                state = WebRtcLinkState.CONNECTED_SECURE,
                sdpAnswer = generateAnswerSdp(),
                measuredRttMs = 12
            )
        }
    }

    fun disconnectSession() {
        _session.value = _session.value.copy(state = WebRtcLinkState.DISCONNECTED)
    }

    fun setBitrateCeiling(kbps: Int) {
        val resolution = when {
            kbps >= 12000 -> "4K Tactical (30 FPS H.265)"
            kbps >= 6000 -> "1080p (60 FPS H.265 Tactical)"
            kbps >= 2500 -> "720p (30 FPS VP9 Low-Latency)"
            else -> "480p Tactical Stream (24 FPS FEC Boosted)"
        }
        _session.value = _session.value.copy(
            targetBitrateKbps = kbps,
            videoResolution = resolution
        )
    }

    fun sendTacticalDataChannelMessage(commandText: String) {
        val log = "-> [TX DATA_CH] $commandText"
        _session.value = _session.value.copy(
            telemetryHistory = listOf(log) + _session.value.telemetryHistory.take(30)
        )
    }

    private fun generateOfferSdp(): String = """
v=0
o=AirborneGOC 2890844526 2890842807 IN IP4 10.5.0.1
s=Airborne-Ground-Tactical-Mesh
t=0 0
a=group:BUNDLE 0 1
a=msid-semantic: WMS airborne-feed
m=video 5004 UDP/TLS/RTP/SAVPF 96 97
c=IN IP4 10.5.0.1
a=rtcp:5005 IN IP4 10.5.0.1
a=ice-ufrag:K4l1
a=ice-pwd:TacticalAirGround5GPass
a=fingerprint:sha-256 4B:92:DF:88:EA:31:09:A1:FE:23:44:00:AB:CD:EF:12:34:56:78:90:12:34:56:78:90:AB:CD:EF:12:34:56:78
a=setup:actpass
a=mid:0
a=rtpmap:96 H265/90000
a=fmtp:96 profile-level-id=42e01f;packetization-mode=1
m=application 5006 UDP/DTLS/SCTP webrtc-datachannel
c=IN IP4 10.5.0.1
a=sctp-port:5000
a=mid:1
""".trimIndent()

    private fun generateAnswerSdp(): String = """
v=0
o=GroundStationGOC 3890844526 3890842807 IN IP4 10.5.0.18
s=Airborne-Ground-Tactical-Mesh
t=0 0
a=group:BUNDLE 0 1
m=video 49152 UDP/TLS/RTP/SAVPF 96
c=IN IP4 10.5.0.18
a=ice-ufrag:G0c8
a=ice-pwd:GroundStationAnswerKey99
a=fingerprint:sha-256 A1:B2:C3:D4:E5:F6:07:18:29:3A:4B:5C:6D:7E:8F:90:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD
a=setup:active
a=mid:0
a=rtpmap:96 H265/90000
m=application 49154 UDP/DTLS/SCTP webrtc-datachannel
c=IN IP4 10.5.0.18
a=sctp-port:5000
a=mid:1
""".trimIndent()
}
