package com.example.data.dispatch

import android.content.Context
import android.util.Log
import com.example.data.secure.DeviceKeys
import com.example.data.secure.EnvelopeCrypto
import com.example.data.secure.PairedDevice
import org.json.JSONObject
import java.util.UUID

/**
 * Inbound envelope pipeline: decrypt → verify → route.
 *
 * Called by [com.example.data.sms.SmsEnvelopeReceiver] (SMS bearer) and, in
 * future, the IP bearer. Envelopes from UNPAIRED devices are logged and
 * dropped — never silently accepted, never executed.
 *
 * Plaintext is a JSON object: {"kind": ..., "tag": ..., "body": {...}}.
 * Kinds: msg (chat) | cmd (agent execution) | hb (heartbeat) |
 *         ack/result (round-trip) | sos (emergency) | raw (non-JSON fallback)
 */
object EnvelopeRouter {

    private const val TAG = "EnvelopeRouter"

    /**
     * Inbound rate limiter: max 20 envelopes/minute per sender. SMS is an
     * unauthenticated bearer - without this, anyone who knows the port can
     * burn battery/CPU by flooding us. Excess is dropped and logged.
     */
    private object InboundRateLimiter {
        private const val MAX_PER_MIN = 20
        private val hits = mutableMapOf<String, MutableList<Long>>()

        @Synchronized
        fun allow(senderHex: String): Boolean {
            val now = System.currentTimeMillis()
            val list = hits.getOrPut(senderHex) { mutableListOf() }
            list.removeIf { now - it > 60_000 }
            if (list.size >= MAX_PER_MIN) return false
            list.add(now)
            return true
        }
    }

    fun handleInbound(ctx: Context, senderPhone: String, envelope: ByteArray) {
        try {
            if (envelope.size < EnvelopeCrypto.HEADER_LEN + 2) {
                Log.w(TAG, "envelope too short; dropped")
                return
            }
            // Peek senderId (bytes 1..8) without decrypting — we need it to
            // look up the peer's public key first.
            val senderId = envelope.copyOfRange(1, 9)
            val senderHex = senderId.joinToString("") { "%02x".format(it) }
            if (!InboundRateLimiter.allow(senderHex)) {
                Log.w(TAG, "rate-limited inbound flood from $senderHex; dropped")
                return
            }
            val reg = try {
                DispatchManager.registry()
            } catch (e: Exception) {
                Log.w(TAG, "registry unavailable; dropped")
                return
            }
            val device = reg.get(senderHex)
            if (device == null) {
                InboxStore.add(
                    InboxMessage(
                        UUID.randomUUID().toString(), System.currentTimeMillis(),
                        senderHex, senderPhone, "dropped",
                        "envelope from unpaired device; dropped without decrypting"
                    )
                )
                Log.w(TAG, "dropped envelope from unpaired device $senderHex")
                return
            }
            val identity = DeviceKeys.getOrCreateIdentity()
            val myId = EnvelopeCrypto.deviceId(identity.public)
            val peerKey = DeviceKeys.importPeerPublicKey(device.publicKeyB64)
            val opened = EnvelopeCrypto.open(
                identity.private, myId, peerKey, senderId, envelope, device.lastSeqSeen
            )
            reg.recordSeqSeen(senderHex, opened.seq)
            routeMessage(ctx, device, opened.plaintext)
        } catch (e: SecurityException) {
            Log.w(TAG, "rejected inbound envelope: ${e.message}")
            com.example.data.sms.DataSmsTransport.logInbound(senderPhone, "REJECTED: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "inbound handling failed: ${e.message}")
        }
    }

    private fun routeMessage(ctx: Context, device: PairedDevice, plaintext: ByteArray) {
        val at = System.currentTimeMillis()
        val obj = try {
            JSONObject(String(plaintext, Charsets.UTF_8))
        } catch (e: Exception) {
            InboxStore.add(
                InboxMessage(
                    UUID.randomUUID().toString(), at, device.idHex, device.name,
                    "raw", String(plaintext, Charsets.UTF_8).take(500)
                )
            )
            return
        }
        val body = obj.optJSONObject("body") ?: JSONObject()
        when (obj.optString("kind")) {
            "msg" -> InboxStore.add(
                InboxMessage(
                    UUID.randomUUID().toString(), at, device.idHex, device.name,
                    "msg", body.optString("text", "(empty)")
                )
            )
            "cmd" -> {
                val cmd = body.optString("cmd")
                val result = AgentExecutor.execute(
                    ctx, cmd, body.optJSONObject("args") ?: JSONObject(), device.trustLevel
                )
                DispatchManager.dispatchJson(
                    device, "result",
                    JSONObject().put("forTag", obj.optString("tag")).put("result", result)
                )
                InboxStore.add(
                    InboxMessage(
                        UUID.randomUUID().toString(), at, device.idHex, device.name,
                        "cmd", "agent executed: $cmd"
                    )
                )
            }
            "hb" -> HeartbeatManager.onHeartbeat(
                device.idHex, device.name, body.optInt("batt", -1)
            )
            "ack", "result" -> {
                DispatchManager.onAckReceived(body.optString("forTag", obj.optString("tag")))
                if (obj.optString("kind") == "result") {
                    InboxStore.add(
                        InboxMessage(
                            UUID.randomUUID().toString(), at, device.idHex, device.name,
                            "result", body.optJSONObject("result")?.toString()?.take(500) ?: ""
                        )
                    )
                }
            }
            "signal" -> {
                // WebRTC signaling (offer/answer/ICE), encrypted like everything else.
                com.example.data.webrtc.WebRtcBearer.onSignal(device, body)
                InboxStore.add(
                    InboxMessage(
                        UUID.randomUUID().toString(), at, device.idHex, device.name,
                        "signal", "WebRTC ${body.optString("sig", "?")}"
                    )
                )
            }
            "attest" -> {
                // Peer attestation claim: store + change-detect.
                val claim = com.example.data.admin.DeviceAttestation.Claim.fromJson(body)
                val prev = com.example.data.admin.DeviceAttestation.getClaim(ctx, device.idHex)
                com.example.data.admin.DeviceAttestation.storeClaim(ctx, device.idHex, claim)
                if (prev != null && prev.identityKey() != claim.identityKey()) {
                    val text = "ATTESTATION CHANGE on ${device.name}: hardware identity differs " +
                        "from the stored claim. Possible new phone, reset, or cloning."
                    InboxStore.add(
                        InboxMessage(
                            UUID.randomUUID().toString(), at, device.idHex, device.name,
                            "attest", "MISMATCH — $text"
                        )
                    )
                    AutomationEngine.raise("CRIT", text)
                } else {
                    InboxStore.add(
                        InboxMessage(
                            UUID.randomUUID().toString(), at, device.idHex, device.name,
                            "attest", "attestation claim stored"
                        )
                    )
                }
            }
            "sos" -> {
                val text = "SOS from ${device.name}: ${body.optString("loc", "no fix")} " +
                    "(batt ${body.optInt("batt", -1)}%)"
                InboxStore.add(
                    InboxMessage(
                        UUID.randomUUID().toString(), at, device.idHex, device.name, "sos", text
                    )
                )
                AutomationEngine.raise("CRIT", text)
            }
            else -> InboxStore.add(
                InboxMessage(
                    UUID.randomUUID().toString(), at, device.idHex, device.name,
                    obj.optString("kind", "?"), obj.optString("tag", "")
                )
            )
        }
    }
}
