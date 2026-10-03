package com.example.data.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

/**
 * Binary data-SMS transport on a dedicated port ([SMS_PORT]).
 *
 * Why data SMS and not text SMS: payloads are encrypted binary (Envelope v1),
 * and data SMS carries raw bytes invisibly — nothing appears in the user's
 * messaging app. Each segment is sent with sent/delivery intents so every
 * hop is tracked in the live traffic log.
 *
 * Port 19841 is arbitrary and unassigned; both ends must use it (the
 * receiver's manifest filter matches it).
 */
data class SmsTrafficEvent(
    val at: Long,
    val direction: String, // OUT | IN
    val phone: String,
    val detail: String
)

object DataSmsTransport {

    const val SMS_PORT: Short = 19841
    private const val TAG = "DataSmsTransport"
    private const val ACTION_SENT = "com.example.GKK_SMS_SENT"
    private const val ACTION_DELIVERED = "com.example.GKK_SMS_DELIVERED"

    private val _events = MutableStateFlow<List<SmsTrafficEvent>>(emptyList())
    val events: StateFlow<List<SmsTrafficEvent>> = _events.asStateFlow()

    @Volatile
    private var appCtx: Context? = null
    private var tracker: BroadcastReceiver? = null

    fun init(ctx: Context) {
        if (appCtx != null) return
        val c = ctx.applicationContext
        appCtx = c
        tracker = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val phone = intent.getStringExtra("phone") ?: "?"
                val seg = intent.getIntExtra("seg", -1)
                val ok = resultCode == Activity.RESULT_OK
                val what = if (intent.action == ACTION_SENT) "sent" else "delivered"
                log("$what seg $seg -> $phone ${if (ok) "OK" else "FAILED(rc=$resultCode)"}", phone = phone)
            }
        }
        val f = IntentFilter().apply {
            addAction(ACTION_SENT)
            addAction(ACTION_DELIVERED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            c.registerReceiver(tracker, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            c.registerReceiver(tracker, f)
        }
    }

    fun logInbound(phone: String, detail: String) = log(detail, "IN", phone)

    private fun log(detail: String, direction: String = "OUT", phone: String = "") {
        _events.value = (_events.value + SmsTrafficEvent(System.currentTimeMillis(), direction, phone, detail))
            .takeLast(100)
        Log.i(TAG, detail)
    }

    private fun smsManager(ctx: Context): SmsManager {
        return if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    /**
     * Send raw bytes as segmented data SMS. Returns the segment count.
     * @throws SecurityException if SEND_SMS is not granted.
     * @throws IllegalArgumentException for bad numbers / oversize payloads.
     */
    fun sendSegments(phoneNumber: String, data: ByteArray): Int {
        val ctx = appCtx ?: throw IllegalStateException("DataSmsTransport not initialized")
        require(phoneNumber.isNotBlank()) { "empty destination number" }
        val msgId = ByteArray(8).also { SecureRandom().nextBytes(it) }
        val segments = SmsSegmenter.segment(msgId, data)
        val sm = smsManager(ctx)
        segments.forEachIndexed { i, seg ->
            val base = (System.currentTimeMillis() and 0xFFFFFFF).toInt() + i * 100003
            val sent = PendingIntent.getBroadcast(
                ctx, base, Intent(ACTION_SENT).apply {
                    putExtra("phone", phoneNumber); putExtra("seg", i)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val delivered = PendingIntent.getBroadcast(
                ctx, base + 50000, Intent(ACTION_DELIVERED).apply {
                    putExtra("phone", phoneNumber); putExtra("seg", i)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            sm.sendDataMessage(phoneNumber, null, SMS_PORT, seg, sent, delivered)
        }
        log("sent ${segments.size} segment(s) -> $phoneNumber (${data.size} bytes)", phone = phoneNumber)
        return segments.size
    }
}
