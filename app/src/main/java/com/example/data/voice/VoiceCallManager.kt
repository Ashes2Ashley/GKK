package com.example.data.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * Real carrier voice calling.
 *
 * - Outbound: ACTION_CALL (the real phone call, user-confirmed by the
 *   CALL_PHONE runtime permission, not by us).
 * - State: live TelephonyCallback / PhoneStateListener → IDLE/RINGING/OFFHOOK.
 * - In-call: mute + speaker via AudioManager, hangup via TelecomManager.
 * - Log: the real call log (READ_CALL_LOG).
 *
 * Everything that needs a permission fails with the honest SecurityException
 * instead of pretending. The overlay service shows/hides with call state.
 */
enum class VoiceCallState { IDLE, RINGING, OFFHOOK }

data class CallLogEntry(
    val number: String,
    val type: String, // in | out | missed
    val date: Long,
    val durationSec: Long
)

object VoiceCallManager {

    private const val TAG = "VoiceCallManager"

    private val _callState = MutableStateFlow(VoiceCallState.IDLE)
    val callState: StateFlow<VoiceCallState> = _callState.asStateFlow()

    private val _activeNumber = MutableStateFlow<String?>(null)
    val activeNumber: StateFlow<String?> = _activeNumber.asStateFlow()

    private val _offhookAt = MutableStateFlow<Long?>(null)

    @Volatile
    private var appCtx: Context? = null
    private var callback31: TelephonyCallback? = null
    private var listenerLegacy: PhoneStateListener? = null

    fun init(ctx: Context) {
        if (appCtx != null) return
        appCtx = ctx.applicationContext
        startTracking(appCtx!!)
    }

    private fun granted(perm: String): Boolean {
        val ctx = appCtx ?: return false
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }

    /** Place a real carrier voice call. */
    fun placeCall(number: String): Result<Unit> {
        val ctx = appCtx ?: return Result.failure(IllegalStateException("not initialized"))
        val clean = number.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("empty number"))
        if (!granted(Manifest.permission.CALL_PHONE)) {
            return Result.failure(SecurityException("CALL_PHONE permission not granted — allow it in system settings"))
        }
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(clean))).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            _activeNumber.value = clean
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun endCall(): Result<Unit> {
        val ctx = appCtx ?: return Result.failure(IllegalStateException("not initialized"))
        return try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (tm.endCall()) Result.success(Unit)
            else Result.failure(IllegalStateException("endCall refused — needs ANSWER_PHONE_CALLS permission"))
        } catch (e: SecurityException) {
            Result.failure(SecurityException("endCall needs ANSWER_PHONE_CALLS: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setMuted(muted: Boolean): Result<Unit> {
        val ctx = appCtx ?: return Result.failure(IllegalStateException("not initialized"))
        return try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.isMicrophoneMute = muted
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setSpeaker(on: Boolean): Result<Unit> {
        val ctx = appCtx ?: return Result.failure(IllegalStateException("not initialized"))
        return try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.isSpeakerphoneOn = on
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isMuted(): Boolean = try {
        (appCtx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.isMicrophoneMute == true
    } catch (e: Exception) {
        false
    }

    fun isSpeakerOn(): Boolean = try {
        (appCtx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.isSpeakerphoneOn == true
    } catch (e: Exception) {
        false
    }

    fun callDurationSec(): Long {
        val t0 = _offhookAt.value ?: return 0
        return (System.currentTimeMillis() - t0) / 1000
    }

    /** Real call log, most recent first. */
    fun recentCalls(limit: Int = 10): Result<List<CallLogEntry>> {
        val ctx = appCtx ?: return Result.failure(IllegalStateException("not initialized"))
        if (!granted(Manifest.permission.READ_CALL_LOG)) {
            return Result.failure(SecurityException("READ_CALL_LOG permission not granted"))
        }
        return try {
            val out = mutableListOf<CallLogEntry>()
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER, CallLog.Calls.TYPE,
                    CallLog.Calls.DATE, CallLog.Calls.DURATION
                ),
                null, null, CallLog.Calls.DATE + " DESC"
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val type = when (c.getInt(1)) {
                        CallLog.Calls.INCOMING_TYPE -> "in"
                        CallLog.Calls.OUTGOING_TYPE -> "out"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        else -> "?"
                    }
                    out.add(CallLogEntry(c.getString(0) ?: "?", type, c.getLong(2), c.getLong(3)))
                }
            }
            Result.success(out)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun startTracking(ctx: Context) {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        onNativeState(state)
                    }
                }
                tm.registerTelephonyCallback(Executors.newSingleThreadExecutor(), cb)
                callback31 = cb
            } else {
                @Suppress("DEPRECATION")
                val l = object : PhoneStateListener() {
                    @Deprecated("deprecated")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        onNativeState(state)
                    }
                }
                @Suppress("DEPRECATION")
                tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
                listenerLegacy = l
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "call-state tracking needs READ_PHONE_STATE: ${e.message}")
        }
    }

    private fun onNativeState(state: Int) {
        val ctx = appCtx ?: return
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                _callState.value = VoiceCallState.RINGING
                CallOverlayService.show(ctx)
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                _callState.value = VoiceCallState.OFFHOOK
                if (_offhookAt.value == null) _offhookAt.value = System.currentTimeMillis()
                CallOverlayService.show(ctx)
            }
            else -> {
                _callState.value = VoiceCallState.IDLE
                _offhookAt.value = null
                _activeNumber.value = null
                CallOverlayService.hide(ctx)
            }
        }
    }
}
