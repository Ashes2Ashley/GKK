package com.example.data.secure

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Secure Envelope v1 — end-to-end encrypted payload protocol.
 *
 * Replaces the demo-grade envelope in data/sim (hardcoded keys, stored
 * plaintext) with real per-device cryptography:
 *
 *  - Identity: P-256 keypair per device (see [DeviceKeys]; Android Keystore
 *    backed, in-memory fallback).
 *  - Device ID: first 8 bytes of SHA-256 over the X.509-encoded public key.
 *  - Key agreement: ECDH(P-256) + HKDF-SHA256 -> AES-256 key. The HKDF salt
 *    binds the protocol version and BOTH device IDs, so a key derived for
 *    (A->B) cannot be reused for (A->C) or (B->A).
 *  - Authenticated encryption: AES-256-GCM with a 96-bit random nonce and a
 *    128-bit tag. The envelope header is passed as AAD, binding every
 *    metadata field to the ciphertext. (The old double-HMAC was redundant:
 *    GCM's tag already authenticates; the bytes it saved matter on SMS.)
 *  - Replay protection: monotonic per-sender sequence numbers. The receiver
 *    keeps the highest seen seq per sender and rejects anything <= it.
 *  - Freshness: 24h timestamp window bounds how long a captured envelope
 *    stays dangerous if a seq counter ever resets.
 *  - NOTHING secret is stored in the envelope. No plaintext field.
 *
 * Binary layout (all integers big-endian):
 *   [0]      version            0x01
 *   [1..8]   senderId           8 bytes
 *   [9..16]  recipientId        8 bytes
 *   [17..24] seq               uint64
 *   [25..32] timestampMs       uint64 (epoch millis)
 *   [33..44] nonce              12 bytes
 *   [45..46] ctLen              uint16 (ciphertext length incl. 16-byte tag)
 *   [47..]   ciphertext         ctLen bytes
 *
 * AAD = bytes [0..44]. Overhead: 47 bytes + 16-byte tag = 63 bytes, small
 * enough to fit useful payloads into segmented data SMS (~128 usable
 * bytes/segment).
 *
 * This object is pure java.* / javax.crypto — no Android imports — so it is
 * unit-testable on the JVM. The protocol is verified against an independent
 * Python reference implementation (cryptography lib): key agreement,
 * round-trip, tamper at multiple offsets, wrong-key, replay, stale
 * timestamp, monotonic sequence.
 */
object EnvelopeCrypto {

    const val VERSION: Byte = 0x01
    const val FRESHNESS_MS: Long = 24L * 3600L * 1000L
    const val ID_LEN = 8
    const val NONCE_LEN = 12
    const val GCM_TAG_BITS = 128
    const val HEADER_LEN = 45 // 1 + 8 + 8 + 8 + 8 + 12
    const val MAX_PLAINTEXT_BYTES = 4096

    data class OpenResult(val plaintext: ByteArray, val seq: Long)

    /** Device ID: first 8 bytes of SHA-256 over the X.509-encoded public key. */
    fun deviceId(publicKey: PublicKey): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(publicKey.encoded).copyOf(ID_LEN)
    }

    fun deviceIdHex(publicKey: PublicKey): String =
        deviceId(publicKey).joinToString("") { "%02x".format(it) }

    /**
     * RFC 5869 HKDF with SHA-256. Implemented by hand (no dependency) and
     * cross-checked against the Python `cryptography` library:
     * HKDF(salt="salt-value", info="envelope-aes-256-gcm", L=32)
     *   .derive("input-key-material")
     *   == e26b4c4a...558f  (see SecureEnvelopeTest.hkdfMatchesReference)
     */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..255 * 32) { "bad HKDF length" }
        val mac = Mac.getInstance("HmacSHA256")
        // Extract: PRK = HMAC-SHA256(salt, IKM)
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        // Expand: T(i) = HMAC-SHA256(PRK, T(i-1) | info | i)
        val okm = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < length) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val take = minOf(t.size, length - pos)
            t.copyInto(okm, pos, 0, take)
            pos += take
            counter++
        }
        // Wipe intermediates
        java.util.Arrays.fill(prk, 0)
        java.util.Arrays.fill(t, 0)
        return okm
    }

    /**
     * ECDH(P-256) shared secret -> HKDF-SHA256 -> AES-256 key.
     * Salt = "GKK-v1" || senderId || recipientId, so the derived key is bound
     * to this exact directed pair. Both sides derive the identical key.
     */
    fun deriveMessageKey(
        ownPrivateKey: PrivateKey,
        peerPublicKey: PublicKey,
        senderId: ByteArray,
        recipientId: ByteArray
    ): SecretKeySpec {
        require(senderId.size == ID_LEN && recipientId.size == ID_LEN)
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(ownPrivateKey)
        ka.doPhase(peerPublicKey, true)
        val shared = ka.generateSecret()
        try {
            val salt = "GKK-v1".toByteArray(Charsets.UTF_8) + senderId + recipientId
            val keyBytes = hkdfSha256(
                shared, salt,
                "envelope-aes-256-gcm".toByteArray(Charsets.UTF_8), 32
            )
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            java.util.Arrays.fill(shared, 0)
        }
    }

    /**
     * Seal a plaintext payload for [recipientId]. [seq] must be greater than
     * any previously used sequence number for this sender->recipient pair
     * (see [DeviceRegistry.nextSeqToSend]).
     */
    fun seal(
        senderPrivateKey: PrivateKey,
        senderId: ByteArray,
        recipientPublicKey: PublicKey,
        recipientId: ByteArray,
        seq: Long,
        plaintext: ByteArray,
        timestampMs: Long = System.currentTimeMillis()
    ): ByteArray {
        require(senderId.size == ID_LEN && recipientId.size == ID_LEN)
        require(seq > 0) { "seq must be positive" }
        require(plaintext.size in 1..MAX_PLAINTEXT_BYTES)
        val nonce = ByteArray(NONCE_LEN).also { SecureRandom().nextBytes(it) }
        val key = deriveMessageKey(senderPrivateKey, recipientPublicKey, senderId, recipientId)
        val header = ByteBuffer.allocate(HEADER_LEN)
            .put(VERSION)
            .put(senderId)
            .put(recipientId)
            .putLong(seq)
            .putLong(timestampMs)
            .put(nonce)
            .array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(header)
        val ct = cipher.doFinal(plaintext) // ciphertext || 16-byte auth tag
        require(ct.size <= 0xFFFF) { "ciphertext too large" }
        return header + ByteBuffer.allocate(2).putShort(ct.size.toShort()).array() + ct
    }

    /**
     * Open an envelope addressed to us. Verifies version, addressing,
     * freshness, replay ([lastSeenSeq]), and GCM authentication — in that
     * order, cheapest checks first. Returns plaintext + seq (persist the seq
     * via [DeviceRegistry.recordSeqSeen]).
     *
     * @throws SecurityException on ANY verification failure. No plaintext is
     * returned unless every check passes.
     */
    fun open(
        recipientPrivateKey: PrivateKey,
        recipientId: ByteArray,
        senderPublicKey: PublicKey,
        expectedSenderId: ByteArray,
        envelope: ByteArray,
        lastSeenSeq: Long,
        nowMs: Long = System.currentTimeMillis()
    ): OpenResult {
        if (envelope.size < HEADER_LEN + 2) throw SecurityException("envelope too short")
        val buf = ByteBuffer.wrap(envelope)
        val version = buf.get()
        if (version != VERSION) throw SecurityException("unsupported envelope version: $version")
        val senderId = ByteArray(ID_LEN).also { buf.get(it) }
        val recipId = ByteArray(ID_LEN).also { buf.get(it) }
        val seq = buf.long
        val timestamp = buf.long
        val nonce = ByteArray(NONCE_LEN).also { buf.get(it) }
        val ctLen = buf.short.toInt() and 0xFFFF
        if (!senderId.contentEquals(expectedSenderId)) throw SecurityException("sender identity mismatch")
        if (!recipId.contentEquals(recipientId)) throw SecurityException("envelope not addressed to this device")
        if (seq <= lastSeenSeq) throw SecurityException("replay rejected (seq=$seq <= $lastSeenSeq)")
        if (abs(nowMs - timestamp) > FRESHNESS_MS) throw SecurityException("stale envelope")
        if (envelope.size != HEADER_LEN + 2 + ctLen) throw SecurityException("truncated envelope")
        val header = envelope.copyOfRange(0, HEADER_LEN)
        val ct = envelope.copyOfRange(HEADER_LEN + 2, HEADER_LEN + 2 + ctLen)
        val key = deriveMessageKey(recipientPrivateKey, senderPublicKey, senderId, recipId)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(header)
            OpenResult(cipher.doFinal(ct), seq)
        } catch (e: Exception) {
            throw SecurityException("envelope authentication failed", e)
        }
    }
}
