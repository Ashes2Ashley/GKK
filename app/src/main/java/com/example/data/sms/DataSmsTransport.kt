package com.example.data.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import android.os.Looper
import com.example.data.bearer.BearerConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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

    /** Per-SIM send stats for the advanced path: subId -> "sent=X failed=Y". */
    private val _simStats = MutableStateFlow<Map<Int, String>>(emptyMap())
    val simStats: StateFlow<Map<Int, String>> = _simStats.asStateFlow()

    private val simSent = mutableMapOf<Int, Int>()
    private val simFailed = mutableMapOf<Int, Int>()

    private fun noteSimResult(subId: Int, ok: Boolean) {
        synchronized(simSent) {
            if (ok) simSent[subId] = (simSent[subId] ?: 0) + 1
            else simFailed[subId] = (simFailed[subId] ?: 0) + 1
            _simStats.value = simSent.keys.union(simFailed.keys)
                .associateWith { id -> "sent=${simSent[id] ?: 0} failed=${simFailed[id] ?: 0}" }
        }
    }
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
        require(PhoneNumberUtils.isGlobalPhoneNumber(phoneNumber)) {
            "not a valid phone number: $phoneNumber"
        }
        // Fail fast with a clear reason instead of a silent radio error.
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (tm != null) {
            try {
                val st = tm.simState
                if (st != TelephonyManager.SIM_STATE_READY) {
                    throw IllegalStateException(
                        "SIM not ready (state=$st) - cannot send SMS. Insert a SIM / disable airplane mode."
                    )
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "cannot read SIM state (no READ_PHONE_STATE); attempting send anyway")
            }
        }
        val msgId = ByteArray(8).also { SecureRandom().nextBytes(it) }
        val segments = SmsSegmenter.segment(msgId, data)
        return if (BearerConfig.advancedSmsSend.value) {
            sendSegmentsAdvanced(ctx, phoneNumber, segments, data.size)
        } else {
            sendSegmentsPlain(ctx, smsManager(ctx), phoneNumber, segments, data.size)
        }
    }

    /**
     * Advanced send: walk every active SIM subscription; per SIM, retry up to
     * 3 attempts with exponential backoff (2s/4s/8s) on radio errors before
     * moving to the next SIM. Real multi-SIM resilience, not a longer timeout.
     */
    fun sendSegmentsAdvanced(
        ctx: Context, phoneNumber: String, segments: List<ByteArray>, totalBytes: Int
    ): Int {
        val managers = activeSmsManagers(ctx)
        var lastErr: Exception? = null
        for ((subId, sm) in managers) {
            var attempt = 0
            while (attempt < 3) {
                try {
                    val n = sendSegmentsPlain(ctx, sm, phoneNumber, segments, totalBytes)
                    noteSimResult(subId, true)
                    if (managers.size > 1 || attempt > 0) {
                        Log.i(TAG, "advanced send ok via subId=$subId attempt=${attempt + 1}")
                    }
                    return n
                } catch (e: Exception) {
                    lastErr = e
                    noteSimResult(subId, false)
                    attempt++
                    if (attempt < 3) {
                        val backoff = 2000L shl (attempt - 1) // 2s, 4s
                        // Never sleep the main thread (agent replies dispatch from the
                        // SMS receiver): retry immediately there, back off elsewhere.
                        if (Looper.myLooper() == Looper.getMainLooper()) {
                            Log.w(TAG, "send via subId=$subId failed (attempt $attempt): ${e.message}; retrying immediately (main thread)")
                        } else {
                            Log.w(TAG, "send via subId=$subId failed (attempt $attempt): ${e.message}; retry in ${backoff}ms")
                            runBlocking { delay(backoff) }
                        }
                    }
                }
            }
            Log.w(TAG, "subId=$subId exhausted; trying next SIM")
        }
        throw lastErr ?: IllegalStateException("no SMS subscription available")
    }

    /** Active SIM subscriptions -> (subId, SmsManager). Falls back to default. */
    private fun activeSmsManagers(ctx: Context): List<Pair<Int, SmsManager>> {
        return try {
            val sm = ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val subs = sm?.activeSubscriptionInfoList
            if (subs.isNullOrEmpty()) {
                listOf(-1 to smsManager(ctx))
            } else {
                subs.map { info ->
                    val id = info.subscriptionId
                    id to try {
                        SmsManager.getSmsManagerForSubscriptionId(id)
                    } catch (e: Exception) {
                        smsManager(ctx)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "no READ_PHONE_STATE; single-SIM advanced path")
            listOf(-1 to smsManager(ctx))
        }
    }

    private fun sendSegmentsPlain(
        ctx: Context, sm: SmsManager, phoneNumber: String,
        segments: List<ByteArray>, totalBytes: Int
    ): Int {
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
        log("sent ${segments.size} segment(s) -> $phoneNumber ($totalBytes bytes)", phone = phoneNumber)
        return segments.size
    }
}
