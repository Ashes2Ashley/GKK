package com.example.data.bearer

import android.content.Context
import com.example.data.dispatch.DispatchManager
import com.example.data.secure.PairedDevice
import com.example.data.webrtc.WebRtcBearer

/**
 * Bearer selection: try bearers in [BearerConfig.priority] order, first
 * success wins. SMS first by default; when the SMS bearer throws, and the
 * WebRTC fallback flag is on, the sealed envelope goes over the open
 * WebRTC data channel instead.
 *
 * Every failure carries the real cause of every bearer tried — never a
 * bare "send failed".
 */
object BearerManager {

    fun init(ctx: Context) {
        val app = ctx.applicationContext
        BearerConfig.init(app)
        WebRtcBearer.init(app)
    }

    /**
     * Send [text] to [device]. Returns e.g. "sms (3 segments)" or
     * "webrtc (data channel)".
     */
    fun sendText(device: PairedDevice, text: String): Result<String> {
        if (text.isBlank()) return Result.failure(IllegalArgumentException("nothing to send"))
        if (text.toByteArray().size > 4000) {
            return Result.failure(IllegalArgumentException("message too long (max ~4000 bytes)"))
        }
        val errors = mutableListOf<String>()
        for (bearer in BearerConfig.priority.value) {
            when (bearer) {
                BearerConfig.BEARER_SMS -> {
                    val r = DispatchManager.dispatchText(device, text)
                    if (r.isSuccess) return Result.success("sms (${r.getOrNull()} segments)")
                    errors += "sms: ${r.exceptionOrNull()?.message}"
                }
                BearerConfig.BEARER_WEBRTC -> {
                    if (!BearerConfig.webrtcFallback.value) {
                        errors += "webrtc: fallback disabled (bearer webrtc on)"
                        continue
                    }
                    val env = DispatchManager.sealText(device, text).getOrElse {
                        errors += "webrtc seal: ${it.message}"
                        continue
                    }
                    if (WebRtcBearer.sendEnvelope(device.idHex, env)) {
                        return Result.success("webrtc (data channel)")
                    }
                    errors += "webrtc: not connected — run 'rtc connect ${device.name}'"
                }
            }
        }
        return Result.failure(
            IllegalStateException("all bearers failed: " + errors.joinToString("; "))
        )
    }
}
