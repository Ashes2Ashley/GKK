package com.example

import com.example.data.net.StunClient
import org.junit.Assert.*
import org.junit.Test

/**
 * STUN codec tests: pure JVM, no Android, no network.
 * The crafted response below encodes public IP 203.0.113.45, port 54321
 * as XOR-MAPPED-ADDRESS (RFC 5389 §15.2), hand-computed:
 *   xPort = 54321 xor 0x2112 = 62755 (0xF523)
 *   xAddr = CB:00:71:2D xor 21:12:A4:42 = EA:12:D5:6F
 */
class StunClientTest {

    private val txId = ByteArray(12) { (it + 1).toByte() }

    private fun craftedResponse(): ByteArray {
        val attr = byteArrayOf(
            0x00, 0x20, 0x00, 0x08, // type XOR-MAPPED-ADDRESS, len 8
            0x00, 0x01, // reserved, IPv4
            0xF5.toByte(), 0x23, // xPort
            0xEA.toByte(), 0x12, 0xD5.toByte(), 0x6F // xAddr
        )
        val header = byteArrayOf(
            0x01, 0x01, 0x00, 0x0C, // Binding Success, len 12
            0x21, 0x12, 0xA4.toByte(), 0x42
        ) + txId
        return header + attr
    }

    @Test
    fun buildRequest_layout() {
        val req = StunClient.buildBindingRequest(txId)
        assertEquals(20, req.size)
        assertEquals(0x00, req[0].toInt())
        assertEquals(0x01, req[1].toInt()) // Binding Request
        assertEquals(0x00, req[2].toInt())
        assertEquals(0x00, req[3].toInt()) // length 0
        assertEquals(0x21, req[4].toInt())
        assertEquals(0x12, req[5].toInt())
        assertArrayEquals(txId, req.copyOfRange(8, 20))
    }

    @Test
    fun parseResponse_xorMapped() {
        val m = StunClient.parseResponse(craftedResponse(), txId)
        assertNotNull(m)
        assertEquals("203.0.113.45", m!!.publicIp)
        assertEquals(54321, m.publicPort)
    }

    @Test
    fun parseResponse_rejectsWrongTxId() {
        val badTx = ByteArray(12) { 0x77 }
        assertNull(StunClient.parseResponse(craftedResponse(), badTx))
    }

    @Test
    fun parseResponse_rejectsBadCookie() {
        val r = craftedResponse()
        r[4] = 0x00
        assertNull(StunClient.parseResponse(r, txId))
    }

    @Test
    fun parseResponse_rejectsShort() {
        assertNull(StunClient.parseResponse(ByteArray(10), txId))
    }

    @Test
    fun classifyNat_cone() {
        val m = StunClient.Mapping("9.9.9.9", 40000)
        val r = StunClient.classifyNat("192.168.1.5") { m }
        assertEquals("cone NAT", r.natType)
        assertEquals("9.9.9.9", r.publicIp)
    }

    @Test
    fun classifyNat_symmetric() {
        var n = 0
        val r = StunClient.classifyNat("192.168.1.5") {
            n++
            StunClient.Mapping("9.9.9.9", 40000 + n)
        }
        assertEquals("symmetric NAT", r.natType)
    }

    @Test
    fun classifyNat_open() {
        val m = StunClient.Mapping("1.2.3.4", 40000)
        val r = StunClient.classifyNat("1.2.3.4") { m }
        assertEquals("open (no NAT)", r.natType)
    }

    @Test
    fun classifyNat_unreachable() {
        val r = StunClient.classifyNat("192.168.1.5") { null }
        assertEquals("unknown", r.natType)
        assertNull(r.publicIp)
    }
}
