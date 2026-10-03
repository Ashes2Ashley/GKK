package com.example.data.webrtc

import android.content.Context
import android.util.Log
import com.example.data.dispatch.DispatchManager
import com.example.data.dispatch.EnvelopeRouter
import com.example.data.secure.DeviceKeys
import com.example.data.secure.EnvelopeCrypto
import com.example.data.secure.PairedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.nio.ByteBuffer

/**
 * WebRTC data-channel bearer: the sophisticated fallback when the SMS
 * bearer fails.
 *
 * Real org.webrtc PeerConnection (Stream's Maven Central build of upstream
 * WebRTC — the official artifact never shipped to Maven Central). No media:
 * a single ordered DataChannel ("gkk-envelopes") carries the exact same
 * Envelope v1 bytes the SMS bearer uses, so crypto, replay protection and
 * routing are identical on both bearers.
 *
 * Signaling (offer/answer/ICE) travels as encrypted "signal" envelopes over
 * UDP to the peer's mDNS-discovered address — authenticated by the same
 * P-256 keys as everything else, no new key infrastructure, no server.
 * STUN (stun.l.google.com) handles NAT traversal.
 *
 * Requirements, stated plainly: both devices need an IP path (same LAN via
 * mDNS, or any routable address). If there is no IP path, this bearer
 * reports "not connected" instead of pretending.
 */
object WebRtcBearer {

    private const val TAG = "WebRtcBearer"
    const val SIGNAL_PORT = 19842
    private const val DC_LABEL = "gkk-envelopes"
    private const val CONNECT_TIMEOUT_MS = 25_000L

    data class ConnInfo(val state: String, val detail: String)

    private val _conns = MutableStateFlow<Map<String, ConnInfo>>(emptyMap())
    val connections: StateFlow<Map<String, ConnInfo>> = _conns.asStateFlow()

    private data class Session(
        val pc: PeerConnection,
        var dc: DataChannel?,
        val device: PairedDevice
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var appCtx: Context? = null

    @Volatile
    private var initialized = false
    private var factory: PeerConnectionFactory? = null
    private var egl: EglBase? = null
    private var signalSocket: DatagramSocket? = null
    private val sessions = mutableMapOf<String, Session>()

    // ---- observer adapters (empty defaults; override what you need) ----

    private abstract class PcAdapter : PeerConnection.Observer {
        override fun onSignalingChange(s: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {}
        override fun onIceConnectionReceivingChange(r: Boolean) {}
        override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
        override fun onIceCandidate(c: IceCandidate) {}
        override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
        override fun onAddStream(s: MediaStream) {}
        override fun onRemoveStream(s: MediaStream) {}
        override fun onDataChannel(dc: DataChannel) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(r: RtpReceiver, s: Array<out MediaStream>) {}
        override fun onConnectionChange(s: PeerConnection.PeerConnectionState) {}
    }

    private abstract class SdpAdapter : SdpObserver {
        override fun onCreateSuccess(s: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(e: String) {}
        override fun onSetFailure(e: String) {}
    }

    private abstract class DcAdapter : DataChannel.Observer {
        override fun onBufferedAmountChange(p: Long) {}
        override fun onStateChange() {}
        override fun onMessage(b: DataChannel.Buffer) {}
    }

    // ---- lifecycle ----

    fun init(ctx: Context) {
        if (initialized) return
        val app = ctx.applicationContext
        appCtx = app
        try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(app)
                    .createInitializationOptions()
            )
            val eglBase = EglBase.create()
            egl = eglBase
            factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(
                    DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
                )
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
                .createPeerConnectionFactory()
            Log.i(TAG, "PeerConnectionFactory ready")
        } catch (e: Exception) {
            Log.w(TAG, "WebRTC unavailable on this device: ${e.message}")
            factory = null
        }
        try {
            signalSocket = DatagramSocket(SIGNAL_PORT)
            startReceiveLoop()
        } catch (e: Exception) {
            Log.w(TAG, "signaling socket unavailable: ${e.message}")
        }
        try {
            val idHex = EnvelopeCrypto.deviceIdHex(DeviceKeys.getOrCreateIdentity().public)
            MdnsDiscovery.init(app, idHex, SIGNAL_PORT)
        } catch (e: Exception) {
            Log.w(TAG, "mDNS unavailable: ${e.message}")
        }
        initialized = true
    }

    fun isConnected(idHex: String): Boolean =
        _conns.value[idHex]?.state == "connected" &&
            sessions[idHex]?.dc?.state() == DataChannel.State.OPEN

    fun connState(idHex: String): ConnInfo? = _conns.value[idHex]

    private fun setConn(idHex: String, state: String, detail: String) {
        _conns.value = _conns.value + (idHex to ConnInfo(state, detail))
        Log.i(TAG, "peer ${idHex.take(8)}… -> $state ($detail)")
    }

    // ---- outbound: establish ----

    /**
     * Begin connecting to [device] as the offerer. Async: returns once the
     * offer is sent; watch [connections] for connected/failed.
     */
    fun connect(device: PairedDevice): Result<String> {
        val f = factory ?: return Result.failure(IllegalStateException("WebRTC unavailable on this device"))
        val target = MdnsDiscovery.findFor(device.idHex)
            ?: return Result.failure(
                IllegalStateException(
                    "peer not discovered on the LAN — both devices need IP + mDNS; run 'rtc discover'"
                )
            )
        close(device.idHex)
        val pc = createPeerConnection(device, f) ?: return Result.failure(
            IllegalStateException("could not create PeerConnection")
        )
        val dc = pc.createDataChannel(DC_LABEL, DataChannel.Init())
        if (dc == null) {
            pc.close()
            return Result.failure(IllegalStateException("could not create data channel"))
        }
        val session = Session(pc, dc, device)
        synchronized(sessions) { sessions[device.idHex] = session }
        dc.registerObserver(dataObserver(device))
        setConn(device.idHex, "connecting", "offer -> ${target.ip}:${target.port}")
        pc.createOffer(object : SdpAdapter() {
            override fun onCreateSuccess(s: SessionDescription) {
                pc.setLocalDescription(object : SdpAdapter() {
                    override fun onSetSuccess() {
                        sendSignal(device, JSONObject()
                            .put("sig", "offer")
                            .put("sdp", s.description))
                    }

                    override fun onSetFailure(e: String) {
                        setConn(device.idHex, "failed", "setLocalDescription: $e")
                    }
                }, s)
            }

            override fun onCreateFailure(e: String) {
                setConn(device.idHex, "failed", "createOffer: $e")
            }
        }, MediaConstraints())
        scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (_conns.value[device.idHex]?.state != "connected") {
                setConn(device.idHex, "failed", "no answer / ICE timeout after 25s")
                close(device.idHex)
            }
        }
        return Result.success("offer sent to ${target.ip}:${target.port} — awaiting answer")
    }

    fun close(idHex: String) {
        val s = synchronized(sessions) { sessions.remove(idHex) }
        try {
            s?.dc?.close()
            s?.dc?.dispose()
        } catch (e: Exception) {
        }
        try {
            s?.pc?.close()
            s?.pc?.dispose()
        } catch (e: Exception) {
        }
        _conns.value = _conns.value - idHex
    }

    /** Send raw envelope bytes over the open data channel. */
    fun sendEnvelope(idHex: String, envelope: ByteArray): Boolean {
        return try {
            val dc = synchronized(sessions) { sessions[idHex]?.dc } ?: return false
            if (dc.state() != DataChannel.State.OPEN) return false
            dc.send(DataChannel.Buffer(ByteBuffer.wrap(envelope), true))
        } catch (e: Exception) {
            Log.w(TAG, "data-channel send failed: ${e.message}")
            false
        }
    }

    // ---- inbound: signaling (called by EnvelopeRouter, kind "signal") ----

    fun onSignal(device: PairedDevice, body: JSONObject) {
        val f = factory ?: run {
            Log.w(TAG, "signal from ${device.name} but WebRTC unavailable")
            return
        }
        when (body.optString("sig")) {
            "offer" -> onOffer(f, device, body.optString("sdp"))
            "answer" -> onAnswer(device, body.optString("sdp"))
            "ice" -> onIce(device, body)
            else -> Log.w(TAG, "unknown signal: ${body.optString("sig")}")
        }
    }

    private fun onOffer(f: PeerConnectionFactory, device: PairedDevice, sdp: String) {
        if (sdp.isBlank()) return
        close(device.idHex)
        val pc = createPeerConnection(device, f) ?: return
        synchronized(sessions) { sessions[device.idHex] = Session(pc, null, device) }
        setConn(device.idHex, "connecting", "answering offer")
        pc.setRemoteDescription(object : SdpAdapter() {
            override fun onSetSuccess() {
                pc.createAnswer(object : SdpAdapter() {
                    override fun onCreateSuccess(s: SessionDescription) {
                        pc.setLocalDescription(object : SdpAdapter() {
                            override fun onSetSuccess() {
                                sendSignal(device, JSONObject()
                                    .put("sig", "answer")
                                    .put("sdp", s.description))
                            }

                            override fun onSetFailure(e: String) {
                                setConn(device.idHex, "failed", "setLocalDescription: $e")
                            }
                        }, s)
                    }

                    override fun onCreateFailure(e: String) {
                        setConn(device.idHex, "failed", "createAnswer: $e")
                    }
                }, MediaConstraints())
            }

            override fun onSetFailure(e: String) {
                setConn(device.idHex, "failed", "setRemoteDescription(offer): $e")
            }
        }, SessionDescription(SessionDescription.Type.OFFER, sdp))
    }

    private fun onAnswer(device: PairedDevice, sdp: String) {
        if (sdp.isBlank()) return
        val pc = synchronized(sessions) { sessions[device.idHex]?.pc } ?: return
        pc.setRemoteDescription(object : SdpAdapter() {
            override fun onSetFailure(e: String) {
                setConn(device.idHex, "failed", "setRemoteDescription(answer): $e")
            }
        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    private fun onIce(device: PairedDevice, body: JSONObject) {
        val pc = synchronized(sessions) { sessions[device.idHex]?.pc } ?: return
        try {
            // optString() returns the literal "null" for JSON null — check first.
            val mid = if (body.isNull("mid")) null else body.optString("mid")
            val sdp = if (body.isNull("sdp")) null else body.optString("sdp")
            if (sdp.isNullOrBlank()) {
                Log.w(TAG, "ICE candidate without SDP; dropped")
                return
            }
            val cand = IceCandidate(mid, body.optInt("idx", 0), sdp)
            if (!pc.addIceCandidate(cand)) {
                Log.w(TAG, "addIceCandidate rejected for ${device.name}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "bad ICE candidate: ${e.message}")
        }
    }

    // ---- internals ----

    private fun createPeerConnection(device: PairedDevice, f: PeerConnectionFactory): PeerConnection? {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                .createIceServer()
        )
        val config = PeerConnection.RTCConfiguration(iceServers)
        return try {
            f.createPeerConnection(config, object : PcAdapter() {
                override fun onIceCandidate(c: IceCandidate) {
                    sendSignal(device, JSONObject()
                        .put("sig", "ice")
                        .put("mid", c.sdpMid)
                        .put("idx", c.sdpMLineIndex)
                        .put("sdp", c.sdp))
                }

                override fun onDataChannel(dc: DataChannel) {
                    // Answerer side: the channel arrives here.
                    synchronized(sessions) { sessions[device.idHex]?.dc = dc }
                    dc.registerObserver(dataObserver(device))
                    Log.i(TAG, "data channel open (answerer) for ${device.name}")
                }

                override fun onConnectionChange(s: PeerConnection.PeerConnectionState) {
                    when (s) {
                        PeerConnection.PeerConnectionState.CONNECTED ->
                            setConn(device.idHex, "connected", "DTLS/SCTP up")
                        PeerConnection.PeerConnectionState.DISCONNECTED ->
                            setConn(device.idHex, "disconnected", "ICE disconnected")
                        PeerConnection.PeerConnectionState.FAILED ->
                            setConn(device.idHex, "failed", "ICE/DTLS failed")
                        PeerConnection.PeerConnectionState.CLOSED ->
                            setConn(device.idHex, "closed", "peer connection closed")
                        else -> {}
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "createPeerConnection: ${e.message}")
            null
        }
    }

    private fun dataObserver(device: PairedDevice) = object : DcAdapter() {
        override fun onMessage(b: DataChannel.Buffer) {
            try {
                val bytes = ByteArray(b.data.remaining())
                b.data.get(bytes)
                val ctx = appCtx ?: return
                // Same pipeline as SMS: decrypt → verify → route.
                EnvelopeRouter.handleInbound(ctx, device.phoneNumber, bytes)
            } catch (e: Exception) {
                Log.w(TAG, "data-channel message handling: ${e.message}")
            }
        }

        override fun onStateChange() {
            val dc = synchronized(sessions) { sessions[device.idHex]?.dc }
            if (dc?.state() == DataChannel.State.OPEN) {
                setConn(device.idHex, "connected", "data channel open")
            }
        }
    }

    private fun sendSignal(device: PairedDevice, sig: JSONObject) {
        val sock = signalSocket ?: run {
            Log.w(TAG, "no signaling socket")
            return
        }
        val target = MdnsDiscovery.findFor(device.idHex) ?: run {
            Log.w(TAG, "signal target lost for ${device.name}")
            return
        }
        val env = DispatchManager.sealJson(device, "signal", sig).getOrNull() ?: run {
            Log.w(TAG, "could not seal signal envelope")
            return
        }
        try {
            val pkt = DatagramPacket(
                env.second, env.second.size,
                InetAddress.getByName(target.ip), target.port
            )
            sock.send(pkt)
        } catch (e: Exception) {
            Log.w(TAG, "signal send failed: ${e.message}")
        }
    }

    private fun startReceiveLoop() {
        scope.launch(Dispatchers.IO) {
            val buf = ByteArray(65535)
            while (isActive) {
                try {
                    val sock = signalSocket ?: break
                    val pkt = DatagramPacket(buf, buf.size)
                    sock.receive(pkt)
                    val bytes = pkt.data.copyOf(pkt.length)
                    val ctx = appCtx ?: continue
                    EnvelopeRouter.handleInbound(ctx, "udp:${pkt.address.hostAddress}", bytes)
                } catch (e: SocketException) {
                    break // socket closed
                } catch (e: Exception) {
                    Log.w(TAG, "signaling receive: ${e.message}")
                }
            }
        }
    }

    fun shutdown() {
        try {
            signalSocket?.close()
        } catch (e: Exception) {
        }
        signalSocket = null
        MdnsDiscovery.stop()
        synchronized(sessions) { sessions.keys.toList() }.forEach { close(it) }
        try {
            factory?.dispose()
        } catch (e: Exception) {
        }
        factory = null
        try {
            egl?.release()
        } catch (e: Exception) {
        }
        egl = null
        scope.cancel()
    }
}
