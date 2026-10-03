package com.example.data.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Floating voice overlay: appears over any app while a call is ringing or
 * active, showing number, state, live duration, and mute / speaker / end
 * controls. Plain framework views — no extra dependencies.
 *
 * Requires SYSTEM_ALERT_WINDOW ("display over other apps"). If it's not
 * granted, [show] does nothing and the call state stays visible in the
 * console and Live tab instead — honest degradation, not a crash.
 */
class CallOverlayService : Service() {

    companion object {
        private const val ACTION_SHOW = "com.example.GKK_CALL_OVERLAY_SHOW"
        private const val ACTION_HIDE = "com.example.GKK_CALL_OVERLAY_HIDE"
        private const val CH_ID = "gkk_call_overlay"

        fun show(ctx: Context) {
            if (!Settings.canDrawOverlays(ctx)) return
            val i = Intent(ctx, CallOverlayService::class.java).setAction(ACTION_SHOW)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun hide(ctx: Context) {
            ctx.startService(Intent(ctx, CallOverlayService::class.java).setAction(ACTION_HIDE))
        }
    }

    private var wm: WindowManager? = null
    private var root: View? = null
    private var numberView: TextView? = null
    private var stateView: TextView? = null
    private var timerView: TextView? = null
    private var muteBtn: Button? = null
    private var speakerBtn: Button? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> {
                removeOverlay()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        startForegroundNote()
        if (root != null) {
            refresh()
            return
        }
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        this.wm = wm

        val pad = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xE6111111.toInt())
        }
        numberView = TextView(this).apply {
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
        }
        stateView = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFAAAAAA.toInt())
        }
        timerView = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFAAAAAA.toInt())
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        muteBtn = Button(this).apply {
            text = "Mute"
            setOnClickListener {
                VoiceCallManager.setMuted(!VoiceCallManager.isMuted())
                refresh()
            }
        }
        speakerBtn = Button(this).apply {
            text = "Speaker"
            setOnClickListener {
                VoiceCallManager.setSpeaker(!VoiceCallManager.isSpeakerOn())
                refresh()
            }
        }
        val endBtn = Button(this).apply {
            text = "End"
            setOnClickListener { VoiceCallManager.endCall() }
        }
        row.addView(muteBtn)
        row.addView(speakerBtn)
        row.addView(endBtn)
        layout.addView(numberView)
        layout.addView(stateView)
        layout.addView(timerView)
        layout.addView(row)

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (64 * resources.displayMetrics.density).toInt()
        }
        root = layout
        try {
            wm.addView(layout, params)
        } catch (e: Exception) {
            root = null
            stopSelf()
            return
        }
        refresh()
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                timerView?.text = "duration ${VoiceCallManager.callDurationSec()}s"
                stateView?.text = "state: ${VoiceCallManager.callState.value}"
                delay(1000)
            }
        }
    }

    private fun refresh() {
        numberView?.text = VoiceCallManager.activeNumber.value ?: "unknown number"
        stateView?.text = "state: ${VoiceCallManager.callState.value}"
        muteBtn?.text = if (VoiceCallManager.isMuted()) "Unmute" else "Mute"
        speakerBtn?.text = if (VoiceCallManager.isSpeakerOn()) "Speaker off" else "Speaker"
    }

    private fun removeOverlay() {
        timerJob?.cancel()
        timerJob = null
        try {
            root?.let { wm?.removeView(it) }
        } catch (e: Exception) {
            // Already gone.
        }
        root = null
        wm = null
    }

    private fun startForegroundNote() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CH_ID, "Call overlay", NotificationManager.IMPORTANCE_LOW)
            )
            Notification.Builder(this, CH_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        startForeground(
            41,
            builder.setContentTitle("GKK call overlay active")
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .build()
        )
    }

    override fun onDestroy() {
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }
}
