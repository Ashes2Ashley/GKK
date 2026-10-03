package com.example.data.dispatch

import android.content.Context
import com.example.data.secure.DeviceKeys
import com.example.data.secure.DeviceRegistry
import com.example.data.secure.EnvelopeCrypto
import com.example.data.secure.PairedDevice
import com.example.data.sms.DataSmsTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID

/**
 * Outbound dispatch: seal a JSON payload into an Envelope v1 and send it over
 * the data-SMS bearer, segmented. Every dispatch gets a unique tag so the
 * round-trip monitor can measure send→reply latency when the peer answers.
 *
 * All failures surface as Result.failure with the real cause — never silent.
 */
object DispatchManager {

    private val _lastRoundTripMs = MutableStateFlow<Long?>(null)
    val lastRoundTripMs: StateFlow<Long?> = _lastRoundTripMs.asStateFlow()

    private val sentAt = mutableMapOf<String, Long>()

    @Volatile
    private var registry: DeviceRegistry? = null

    fun init(ctx: Context) {
        if (registry != null) return
        val appCtx = ctx.applicationContext
        registry = DeviceRegistry(appCtx)
        InboxStore.init(appCtx)
        DataSmsTransport.init(appCtx)
    }

    fun registry(): DeviceRegistry =
        registry ?: throw IllegalStateException("DispatchManager not initialized")

    /**
     * Seal [body] as [kind] for [device] WITHOUT sending.
     * Returns (tag, envelope bytes). Used by alternate bearers (WebRTC)
     * that transport the same Envelope v1 bytes over a different medium.
     */
    fun sealJson(device: PairedDevice, kind: String, body: JSONObject): Result<Pair<String, ByteArray>> {
        return try {
            val reg = registry()
            val identity = DeviceKeys.getOrCreateIdentity()
            val myId = EnvelopeCrypto.deviceId(identity.public)
            val peerKey = DeviceKeys.importPeerPublicKey(device.publicKeyB64)
            val peerId = EnvelopeCrypto.deviceId(peerKey)
            val seq = reg.nextSeqToSend(device.idHex)
            val tag = UUID.randomUUID().toString().take(8)
            val plaintext = JSONObject()
                .put("kind", kind)
                .put("tag", tag)
                .put("body", body)
                .toString()
                .toByteArray(Charsets.UTF_8)
            val envelope = EnvelopeCrypto.seal(
                identity.private, myId, peerKey, peerId, seq, plaintext
            )
            Result.success(tag to envelope)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sealText(device: PairedDevice, text: String): Result<ByteArray> =
        sealJson(device, "msg", JSONObject().put("text", text)).map { it.second }

    /**
     * Seal [body] as [kind] for [device] and send over SMS. Returns the
     * segment count.
     */
    fun dispatchJson(device: PairedDevice, kind: String, body: JSONObject): Result<Int> {
        return try {
            val (tag, envelope) = sealJson(device, kind, body).getOrThrow()
            val t0 = System.currentTimeMillis()
            val n = DataSmsTransport.sendSegments(device.phoneNumber, envelope)
            synchronized(sentAt) { sentAt[tag] = t0 }
            Result.success(n)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun dispatchText(device: PairedDevice, text: String): Result<Int> =
        dispatchJson(device, "msg", JSONObject().put("text", text))

    /** Called when a peer's ack/result referencing [tag] arrives. */
    fun onAckReceived(tag: String) {
        if (tag.isBlank()) return
        val t0 = synchronized(sentAt) { sentAt.remove(tag) } ?: return
        _lastRoundTripMs.value = System.currentTimeMillis() - t0
    }
}
