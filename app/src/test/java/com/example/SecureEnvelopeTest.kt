package com.example

import com.example.data.secure.EnvelopeCrypto
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

/**
 * JVM unit tests for the Secure Envelope v1 protocol.
 *
 * The protocol itself is verified against an independent Python reference
 * implementation (cryptography lib): /tmp/envelope_ref.py — all 7 protocol
 * tests pass there. These Kotlin tests mirror the critical properties so
 * they run in CI / Android Studio on every build.
 */
class SecureEnvelopeTest {

    private fun genPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun ids(): Triple<ByteArray, ByteArray, Pair<java.security.KeyPair, java.security.KeyPair>> {
        val a = genPair()
        val b = genPair()
        val aId = EnvelopeCrypto.deviceId(a.public)
        val bId = EnvelopeCrypto.deviceId(b.public)
        return Triple(aId, bId, a to b)
    }

    @Test
    fun hkdfMatchesReferenceVector() {
        // Cross-checked against Python `cryptography` HKDF:
        // HKDF(salt="salt-value", info="envelope-aes-256-gcm", L=32).derive("input-key-material")
        val got = EnvelopeCrypto.hkdfSha256(
            "input-key-material".toByteArray(),
            "salt-value".toByteArray(),
            "envelope-aes-256-gcm".toByteArray(),
            32
        )
        val hex = got.joinToString("") { "%02x".format(it) }
        assertEquals(
            "e26b4c4aa3ea4083067847bb6157459293e5c897ba5af163b5c5b46cf76d558f",
            hex
        )
    }

    @Test
    fun keyAgreementIsSymmetric() {
        val (aId, bId, ab) = ids()
        val (a, b) = ab
        val k1 = EnvelopeCrypto.deriveMessageKey(a.private, b.public, aId, bId)
        val k2 = EnvelopeCrypto.deriveMessageKey(b.private, a.public, aId, bId)
        assertArrayEquals(k1.encoded, k2.encoded)
    }

    @Test
    fun keyIsBoundToDirectedPair() {
        val (aId, bId, ab) = ids()
        val (a, b) = ab
        val c = genPair()
        val cId = EnvelopeCrypto.deviceId(c.public)
        val kAB = EnvelopeCrypto.deriveMessageKey(a.private, b.public, aId, bId)
        val kAC = EnvelopeCrypto.deriveMessageKey(a.private, c.public, aId, cId)
        val kBA = EnvelopeCrypto.deriveMessageKey(b.private, a.public, bId, aId)
        assertFalse(kAB.encoded.contentEquals(kAC.encoded))
        assertFalse(kAB.encoded.contentEquals(kBA.encoded))
    }

    @Test
    fun roundTrip() {
        val (aId, bId, ab) = ids()
        val (a, b) = ab
        val env = EnvelopeCrypto.seal(a.private, aId, b.public, bId, 1, "hello field".toByteArray())
        val opened = EnvelopeCrypto.open(b.private, bId, a.public, aId, env, lastSeenSeq = 0)
        assertEquals("hello field", String(opened.plaintext))
        assertEquals(1L, opened.seq)
    }

    @Test
    fun tamperAnywhereIsRejected() {
        val (aId, bId, ab) = ids()
        val (a, b) = ab
        val env = EnvelopeCrypto.seal(a.private, aId, b.public, bId, 1, "secret".toByteArray())
        for (offset in listOf(0, 10, 30, 40, 50, env.size - 1)) {
            val bad = env.copyOf()
            bad[offset] = (bad[offset].toInt() xor 0x01).toByte()
            try {
                EnvelopeCrypto.open(b.private, bId, a.public, aId, bad, 0)
                fail("tamper at offset $offset was NOT detected")
            } catch (e: SecurityException) {
                // expected
            }
        }
    }

    @Test
    fun wrongRecipientKeyFails() {
        val (aId, bId, ab) = ids()
        val (a, b) = ab
        val c = genPair()
        val cId = EnvelopeCrypto.deviceId(c.public)
        val env = EnvelopeCrypto.seal(a.private, aId, b.public, bId, 1, "secret".toByteArray())
        try {
            EnvelopeCrypto.open(c.private, cId, a.public, aId, env, 0)
            fail("wrong-key envelope was NOT rejected")
        } catch (e: SecurityException) {
            // expected (addressing check fires before crypto)
        }
    }

    @Test
    fun replayIsRejected() {
        val (aId, bId, ab) = ids()
        val (a, b) = ab
        val env = EnvelopeCrypto.seal(a.private, aId, b.public, bId, 7, "x".toByteArray())
        val opened = EnvelopeCrypto.open(b.private, bId, a.public, aId, env, lastSeenSeq = 0)
        try {
            EnvelopeCrypto.open(b.private, bId, a.public, aId, env, lastSeenSeq = opened.seq)
            fail("replay was NOT rejected")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("replay"))
        }
    }

    @Test
    fun staleEnvelopeIsRejected() {
        val (aId, bId, ab) = ids()
        val (a, b) = ab
        val old = System.currentTimeMillis() - 25 * 3600 * 1000L
        val env = EnvelopeCrypto.seal(a.private, aId, b.public, bId, 1, "x".toByteArray(), timestampMs = old)
        try {
            EnvelopeCrypto.open(b.private, bId, a.public, aId, env, 0)
            fail("stale envelope was NOT rejected")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("stale"))
        }
    }

    @Test
    fun deviceIdsAreStable() {
        val a = genPair()
        val id1 = EnvelopeCrypto.deviceIdHex(a.public)
        val id2 = EnvelopeCrypto.deviceIdHex(a.public)
        assertEquals(id1, id2)
        assertEquals(16, id1.length)
        val b = genPair()
        assertNotEquals(id1, EnvelopeCrypto.deviceIdHex(b.public))
    }
}
