package com.example.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.data.dispatch.EnvelopeRouter

/**
 * Receives data SMS on [DataSmsTransport.SMS_PORT], reassembles multi-segment
 * messages, and hands complete envelopes to [EnvelopeRouter] for
 * decryption/verification/routing.
 *
 * Declared in AndroidManifest with android:permission=BROADCAST_SMS so only
 * the telephony stack can deliver to it. Unknown senders are dropped by the
 * router (never silently accepted).
 */
object ReassemblyHub {
    val reassembler = SmsSegmenter.Reassembler()
}

class SmsEnvelopeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsEnvelopeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.intent.action.DATA_SMS_RECEIVED") return
        val port = try {
            intent.data?.port ?: -1
        } catch (e: Exception) {
            -1
        }
        if (port != DataSmsTransport.SMS_PORT.toInt()) return

        val msgs = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            Log.w(TAG, "could not parse PDUs: ${e.message}")
            return
        }
        if (msgs.isNullOrEmpty()) return
        val sender = msgs[0].originatingAddress ?: "unknown"
        val payload = msgs.flatMap { it.userData?.asList() ?: emptyList() }.toByteArray()
        if (payload.isEmpty()) return

        DataSmsTransport.logInbound(sender, "segment ${payload.size}b")
        val complete = ReassemblyHub.reassembler.feed(payload) ?: return
        DataSmsTransport.logInbound(sender, "envelope reassembled (${complete.size}b)")
        try {
            EnvelopeRouter.handleInbound(context.applicationContext, sender, complete)
        } catch (e: Exception) {
            Log.w(TAG, "router failed: ${e.message}")
        }
    }
}
