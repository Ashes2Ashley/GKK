package com.example.data.net

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom

/**
 * Minimal RFC 5389 STUN client: Binding Request → XOR-MAPPED-ADDRESS.
 *
 * Used for two real jobs:
 * 1. Public IP discovery without any HTTP dependency (pure UDP).
 * 2. NAT type classification: query TWO different STUN servers and compare
 *    the mapped addresses —
 *      mapped IP == local IP            -> "open (no NAT)"
 *      same mapped IP:port on both      -> "cone NAT"
 *      different mapped ports           -> "symmetric NAT"
 *    (Full/restricted/port-restricted cone need a second client; we report
 *    the honest coarse class instead of guessing.)
 *
 * Packet codec is pure Kotlin and unit-tested on the JVM.
 */
object StunClient {

    const val MAGIC_COOKIE = 0x2112A442
    private const val BINDING_REQUEST = 0x0001
    private const val BINDING_SUCCESS = 0x0101
    private const val ATTR_XOR_MAPPED = 0x0020
    private const val ATTR_MAPPED = 0x0001

    data class Mapping(val publicIp: String, val publicPort: Int)

    /** Build a 20-byte Binding Request for [txId] (12 bytes). Pure. */
    fun buildBindingRequest(txId: ByteArray): ByteArray {
        require(txId.size == 12) { "transaction id must be 12 bytes" }
        val out = ByteArray(20)
        out[0] = 0x00; out[1] = BINDING_REQUEST.toByte()
        out[2] = 0x00; out[3] = 0x00 // no attributes
        out[4] = 0x21; out[5] = 0x12; out[6] = 0xA4.toByte(); out[7] = 0x42
        txId.copyInto(out, 8)
        return out
    }

    /**
     * Parse a Binding Success Response, verifying magic cookie + [txId].
     * Returns (publicIp, publicPort) or null. Pure.
     */
    fun parseResponse(data: ByteArray, txId: ByteArray): Mapping? {
        if (data.size < 20) return null
        val type = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        if (type != BINDING_SUCCESS) return null
        val cookie = ((data[4].toLong() and 0xFF) shl 24) or
            ((data[5].toLong() and 0xFF) shl 16) or
            ((data[6].toLong() and 0xFF) shl 8) or
            (data[7].toLong() and 0xFF)
        if (cookie != MAGIC_COOKIE.toLong()) return null
        if (!data.copyOfRange(8, 20).contentEquals(txId)) return null
        val msgLen = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        var off = 20
        val end = minOf(20 + msgLen, data.size)
        while (off + 4 <= end) {
            val attrType = ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)
            val attrLen = ((data[off + 2].toInt() and 0xFF) shl 8) or (data[off + 3].toInt() and 0xFF)
            val v = off + 4
            if (v + attrLen > end) break
            if (attrType == ATTR_XOR_MAPPED && attrLen >= 8) {
                val family = data[v + 1].toInt() and 0xFF
                val xPort = ((data[v + 2].toInt() and 0xFF) shl 8) or (data[v + 3].toInt() and 0xFF)
                val port = xPort xor 0x2112
                val ip = if (family == 0x01 && attrLen >= 8) {
                    val raw = data.copyOfRange(v + 4, v + 8)
                    val mc = byteArrayOf(0x21, 0x12, 0xA4.toByte(), 0x42)
                    raw.indices.map { (raw[it].toInt() xor mc[it].toInt()) and 0xFF }.joinToString(".")
                } else if (family == 0x02 && attrLen >= 20) {
                    // IPv6: XOR with magic cookie + transaction id (16 bytes).
                    val key = byteArrayOf(0x21, 0x12, 0xA4.toByte(), 0x42) + txId
                    val raw = data.copyOfRange(v + 4, v + 20)
                    val dec = ByteArray(16) { (raw[it].toInt() xor key[it].toInt()).toByte() }
                    InetAddress.getByAddress(dec).hostAddress ?: return null
                } else return null
                return Mapping(ip, port)
            }
            if (attrType == ATTR_MAPPED && attrLen >= 8) {
                // Legacy non-XOR form (rare); family byte at v+1.
                val family = data[v + 1].toInt() and 0xFF
                val port = ((data[v + 2].toInt() and 0xFF) shl 8) or (data[v + 3].toInt() and 0xFF)
                if (family == 0x01) {
                    val ip = data.copyOfRange(v + 4, v + 8).map { it.toInt() and 0xFF }.joinToString(".")
                    return Mapping(ip, port)
                }
            }
            off = v + attrLen + (if (attrLen % 4 == 0) 0 else 4 - attrLen % 4) // 32-bit padding
        }
        return null
    }

    /**
     * One live STUN query. Returns the server-reflexive mapping or null on
     * timeout/failure. Blocking UDP with a socket timeout — call off the
     * main thread.
     */
    fun query(host: String, port: Int = 19302, timeoutMs: Int = 3000): Mapping? {
        return try {
            val txId = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val req = buildBindingRequest(txId)
            val addr = InetAddress.getByName(host)
            DatagramSocket().use { sock ->
                sock.soTimeout = timeoutMs
                sock.send(DatagramPacket(req, req.size, addr, port))
                val buf = ByteArray(512)
                val resp = DatagramPacket(buf, buf.size)
                sock.receive(resp)
                parseResponse(resp.data.copyOf(resp.length), txId)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * NAT classification from two independent STUN servers. Pass a custom
     * [probe] in tests; the default does live UDP.
     */
    fun classifyNat(
        localIp: String?,
        probe: (String) -> Mapping? = { h -> query(h) }
    ): NatReport {
        val a = probe("stun1.l.google.com")
        val b = probe("stun2.l.google.com")
        if (a == null && b == null) {
            return NatReport(null, null, "unknown", "both STUN servers unreachable — no IP path")
        }
        val m = a ?: b!!
        val natType = when {
            localIp != null && m.publicIp == localIp -> "open (no NAT)"
            a != null && b != null && a.publicIp == b.publicIp && a.publicPort == b.publicPort ->
                "cone NAT"
            a != null && b != null -> "symmetric NAT"
            else -> "NAT (single probe)"
        }
        return NatReport(m.publicIp, m.publicPort, natType, "via STUN binding")
    }

    data class NatReport(
        val publicIp: String?,
        val publicPort: Int?,
        val natType: String,
        val detail: String
    )
}
