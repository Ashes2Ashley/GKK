package com.example.data.bearer

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bearer flags: how envelopes travel, and what "advanced" means.
 *
 * - advancedSmsSend (default ON): multi-SIM subscription walk + per-SIM
 *   retry with exponential backoff + delivery tracking. Turn off for the
 *   plain single-shot SmsManager path.
 * - webrtcFallback (default ON): when the SMS bearer fails and an IP path
 *   to the peer exists, fall back to the WebRTC data-channel bearer.
 * - bearerPriority: ordered bearer names tried by BearerManager.
 *
 * Persisted in SharedPreferences; observable via StateFlow so the console
 * and Live tab always show the live configuration. Prefilled with working
 * defaults — nothing here needs manual setup.
 */
object BearerConfig {

    private const val PREFS = "gkk_bearer_config"
    private const val K_ADV_SMS = "advanced_sms_send"
    private const val K_WEBRTC = "webrtc_fallback"
    private const val K_PRIORITY = "bearer_priority"

    const val BEARER_SMS = "sms"
    const val BEARER_WEBRTC = "webrtc"

    private val _advancedSmsSend = MutableStateFlow(true)
    val advancedSmsSend: StateFlow<Boolean> = _advancedSmsSend.asStateFlow()

    private val _webrtcFallback = MutableStateFlow(true)
    val webrtcFallback: StateFlow<Boolean> = _webrtcFallback.asStateFlow()

    private val _priority = MutableStateFlow(listOf(BEARER_SMS, BEARER_WEBRTC))
    val priority: StateFlow<List<String>> = _priority.asStateFlow()

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _advancedSmsSend.value = p.getBoolean(K_ADV_SMS, true)
        _webrtcFallback.value = p.getBoolean(K_WEBRTC, true)
        _priority.value = (p.getString(K_PRIORITY, "$BEARER_SMS,$BEARER_WEBRTC") ?: "")
            .split(",").map { it.trim() }.filter { it == BEARER_SMS || it == BEARER_WEBRTC }
            .ifEmpty { listOf(BEARER_SMS, BEARER_WEBRTC) }
    }

    fun setAdvancedSmsSend(on: Boolean) {
        _advancedSmsSend.value = on
        prefs?.edit()?.putBoolean(K_ADV_SMS, on)?.apply()
    }

    fun setWebrtcFallback(on: Boolean) {
        _webrtcFallback.value = on
        prefs?.edit()?.putBoolean(K_WEBRTC, on)?.apply()
    }

    fun setPriority(order: List<String>): Boolean {
        val clean = order.map { it.trim().lowercase() }
            .filter { it == BEARER_SMS || it == BEARER_WEBRTC }
            .distinct()
        if (clean.isEmpty()) return false
        _priority.value = clean
        prefs?.edit()?.putString(K_PRIORITY, clean.joinToString(","))?.apply()
        return true
    }

    fun report(): String = buildString {
        appendLine("advanced SMS send: ${if (_advancedSmsSend.value) "ON (multi-SIM + retry/backoff)" else "OFF (single-shot)"}")
        appendLine("WebRTC fallback:   ${if (_webrtcFallback.value) "ON" else "OFF"}")
        append("bearer priority:   ${_priority.value.joinToString(" -> ")}")
    }.toString()
}
