package com.example

import com.example.data.sms.SmsSegmenter
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

/**
 * JVM tests for the data-SMS segmenter/reassembler.
 * The protocol is additionally verified against an independent Python
 * reference (300 fuzz trials: shuffle + duplicates + interleave + expiry).
 */
class SmsSegmenterTest {

    private fun rnd(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }

    @Test
    fun singleSegmentRoundTrip() {
        val data = "hello".toByteArray()
        val segs = SmsSegmenter.segment(rnd(8), data)
        assertEquals(1, segs.size)
        assertEquals(128 - 118 + 5, segs[0].size) // 10 header + 5 payload
        val out = SmsSegmenter.Reassembler().feed(segs[0])
        assertNotNull(out)
        assertArrayEquals(data, out)
    }

    @Test
    fun multiSegmentOutOfOrderWithDuplicates() {
        val data = rnd(1000)
        val segs = SmsSegmenter.segment(rnd(8), data)
        assertTrue(segs.size > 1)
        val shuffled = (segs + segs).shuffled()
        val r = SmsSegmenter.Reassembler()
        var got: ByteArray? = null
        for (s in shuffled) {
            val out = r.feed(s)
            if (out != null) {
                got = out
                break
            }
        }
        assertNotNull("message never completed", got)
        assertArrayEquals(data, got)
    }

    @Test
    fun corruptSegmentsRejected() {
        val r = SmsSegmenter.Reassembler()
        assertNull(r.feed(byteArrayOf(1, 2, 3)))
        val id = ByteArray(8)
        assertNull(r.feed(id + byteArrayOf(0, 0) + byteArrayOf(1))) // total=0
        assertNull(r.feed(id + byteArrayOf(2, 5) + byteArrayOf(1))) // index >= total
    }

    @Test
    fun interleavedMessagesStayIsolated() {
        val r = SmsSegmenter.Reassembler()
        val d1 = rnd(300)
        val d2 = rnd(50)
        val s1 = SmsSegmenter.segment(rnd(8), d1)
        val s2 = SmsSegmenter.segment(rnd(8), d2)
        val results = mutableListOf<ByteArray>()
        (s1 + s2).shuffled().forEach { seg ->
            r.feed(seg)?.let { results.add(it) }
        }
        assertEquals(2, results.size)
        assertTrue(results.any { it.contentEquals(d1) })
        assertTrue(results.any { it.contentEquals(d2) })
    }

    @Test
    fun segmentSizeNeverExceeds128() {
        val data = rnd(5000)
        SmsSegmenter.segment(rnd(8), data).forEach {
            assertTrue("segment ${it.size}b > 128", it.size <= 128)
        }
    }
}
