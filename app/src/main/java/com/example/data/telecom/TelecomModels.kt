package com.example.data.telecom

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

/**
 * WebRTC ICE Server configuration (STUN/TURN).
 */
@JsonClass(generateAdapter = true)
data class IceServerConfig(
    @param:Json(name = "urls")
    val urls: String,

    @param:Json(name = "username")
    val username: String? = null,

    @param:Json(name = "credential")
    val credential: String? = null
)

/**
 * WebRTC Peer Connection State.
 */
enum class WebRtcState {
    IDLE,
    GATHERING_CANDIDATES,
    OFFER_CREATED,
    ANSWER_RECEIVED,
    CONNECTED,
    FAILED,
    CLOSED
}

/**
 * WebRTC Session details.
 */
data class WebRtcSession(
    val sessionId: String = UUID.randomUUID().toString().take(8),
    val peerId: String = "peer_${(1000..9999).random()}",
    val state: WebRtcState = WebRtcState.IDLE,
    val iceServers: List<IceServerConfig> = listOf(
        IceServerConfig(urls = "stun:stun.l.google.com:19302"),
        IceServerConfig(urls = "stun:stun1.l.google.com:19302")
    ),
    val localSdpOffer: String = "",
    val remoteSdpAnswer: String = "",
    val iceCandidatesCount: Int = 0,
    val roundTripTimeMs: Long = 0
)

/**
 * SIP (Session Initiation Protocol) Configuration.
 */
@JsonClass(generateAdapter = true)
data class SipProfile(
    @param:Json(name = "username")
    val username: String = "alice",

    @param:Json(name = "domain")
    val domain: String = "sip.telecom-edge.net",

    @param:Json(name = "proxy")
    val proxy: String = "sip.telecom-edge.net:5060",

    @param:Json(name = "transport")
    val transport: String = "TLS", // UDP, TCP, TLS

    @param:Json(name = "port")
    val port: Int = 5061,

    @param:Json(name = "displayName")
    val displayName: String = "Alice Endpoint"
) {
    fun getSipUri(): String = "sip:$username@$domain"
}

enum class SipCallState {
    IDLE,
    REGISTERING,
    REGISTERED,
    CALLING,
    RINGING,
    IN_CALL,
    TERMINATED,
    ERROR
}

data class SipSession(
    val profile: SipProfile = SipProfile(),
    val state: SipCallState = SipCallState.IDLE,
    val activeCallTarget: String = "",
    val callDurationSeconds: Int = 0,
    val callLogs: List<String> = emptyList()
) {
    /**
     * Generates standard SIP REGISTER message packet string for inspection.
     */
    fun generateRegisterPacket(): String {
        val branch = UUID.randomUUID().toString().take(12)
        val callId = UUID.randomUUID().toString()
        return """
REGISTER ${profile.getSipUri()} SIP/2.0
Via: SIP/2.0/${profile.transport} 192.168.1.100:${profile.port};branch=z9hG4bK$branch
Max-Forwards: 70
To: <${profile.getSipUri()}>
From: "${profile.displayName}" <${profile.getSipUri()}>;tag=${(1000..9999).random()}
Call-ID: $callId@192.168.1.100
CSeq: 1 REGISTER
Contact: <sip:${profile.username}@192.168.1.100:${profile.port};transport=${profile.transport}>
Expires: 3600
User-Agent: ApiConnect-SIP-Engine/2.0
Content-Length: 0
""".trimIndent()
    }
}

/**
 * SMS Message model for Telephony integration.
 */
data class SmsMessageItem(
    val id: String = UUID.randomUUID().toString(),
    val recipient: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Sent",
    val characterCount: Int = messageText.length,
    val segmentCount: Int = if (messageText.length <= 160) 1 else (messageText.length / 153) + 1
)
