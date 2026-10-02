package com.example.data.telecom

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager

object SmsGatewayManager {

    /**
     * Attempts direct SMS transmission via Android SmsManager if permission granted.
     */
    fun sendDirectSms(
        context: Context,
        recipient: String,
        message: String
    ): Result<String> {
        return try {
            @Suppress("DEPRECATION")
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            } else {
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(recipient, null, message, null, null)
            }
            Result.success("Message dispatched to $recipient via SmsManager (${parts.size} segment(s))")
        } catch (e: SecurityException) {
            Result.failure(Exception("SMS Permission not granted: ${e.localizedMessage}"))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to send SMS: ${e.localizedMessage}"))
        }
    }

    /**
     * Builds standard Android SMS Intent for opening messaging app with pre-filled content.
     */
    fun buildSmsIntent(recipient: String, message: String): Intent {
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(recipient)}")
            putExtra("sms_body", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
