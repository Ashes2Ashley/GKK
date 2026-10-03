package com.example.data.sms

/**
 * Segmentation for binary data SMS.
 *
 * One 8-bit data SMS carries 140 bytes; with port-addressing UDH overhead
 * the safe user payload is ~133 bytes. We use 128-byte segments:
 *
 *   [0..7]   msgId    8 bytes random (links segments of one message)
 *   [8]      total    segment count (1..255)
 *   [9]      index    0-based
 *   [10..]   payload  up to 118 bytes
 *
 * Pure JVM logic — no Android imports — unit-tested and cross-verified
 * against a Python reference implementation.
 */
object SmsSegmenter {

    const val SEGMENT_BYTES = 128
    const val HEADER_BYTES = 10
    const val PAYLOAD_BYTES = SEGMENT_BYTES - HEADER_BYTES // 118
    const val MAX_SEGMENTS = 255

    fun segment(msgId: ByteArray, data: ByteArray): List<ByteArray> {
        require(msgId.size == 8) { "msgId must be 8 bytes" }
        require(data.isNotEmpty()) { "nothing to segment" }
        val total = (data.size + PAYLOAD_BYTES - 1) / PAYLOAD_BYTES
        require(total <= MAX_SEGMENTS) { "message too large: $total segments" }
        return (0 until total).map { i ->
            val start = i * PAYLOAD_BYTES
            val end = minOf(start + PAYLOAD_BYTES, data.size)
            msgId + byteArrayOf(total.toByte(), i.toByte()) + data.copyOfRange(start, end)
        }
    }

    fun parseHeader(segment: ByteArray): Triple<ByteArray, Int, Int>? {
        if (segment.size < HEADER_BYTES) return null
        val total = segment[8].toInt() and 0xFF
        val index = segment[9].toInt() and 0xFF
        if (total < 1 || total > MAX_SEGMENTS || index >= total) return null
        return Triple(segment.copyOfRange(0, 8), total, index)
    }

    /**
     * Stateful reassembler. Segments may arrive out of order or duplicated
     * (SMS delivery is not ordered). Returns the complete message the moment
     * its final distinct segment arrives, else null. Stale partial messages
     * expire after [maxAgeMs].
     */
    class Reassembler(private val maxAgeMs: Long = 10 * 60 * 1000L) {

        private data class Slot(
            val total: Int,
            val parts: MutableMap<Int, ByteArray>,
            var updatedAt: Long
        )

        private val slots = mutableMapOf<String, Slot>()

        @Synchronized
        fun feed(segment: ByteArray, nowMs: Long = System.currentTimeMillis()): ByteArray? {
            val header = parseHeader(segment) ?: return null
            val (msgId, total, index) = header
            val key = msgId.joinToString("") { "%02x".format(it) }
            expire(nowMs)
            val slot = slots.getOrPut(key) { Slot(total, mutableMapOf(), nowMs) }
            if (slot.total != total) return null // inconsistent header; drop
            slot.parts[index] = segment.copyOfRange(HEADER_BYTES, segment.size)
            slot.updatedAt = nowMs
            if (slot.parts.size == total) {
                slots.remove(key)
                var out = byteArrayOf()
                for (i in 0 until total) {
                    out += slot.parts[i] ?: return null // cannot happen; defensive
                }
                return out
            }
            return null
        }

        @Synchronized
        fun pendingCount(): Int {
            expire()
            return slots.size
        }

        private fun expire(nowMs: Long = System.currentTimeMillis()) {
            val it = slots.entries.iterator()
            while (it.hasNext()) {
                if (nowMs - it.next().value.updatedAt > maxAgeMs) it.remove()
            }
        }
    }
}
