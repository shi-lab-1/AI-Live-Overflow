package com.aeli.overflow.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.core.app.NotificationCompat
import com.aeli.overflow.sync.SupabaseSync
import kotlinx.coroutines.*
import org.json.JSONObject

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var params: WindowManager.LayoutParams? = null
    private var sync: SupabaseSync? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "pet_overlay"
        const val NOTIFICATION_ID = 1001
        const val PET_W = 80
        const val PET_H = 100

        // -- configure these --
        const val SUPABASE_URL = "https://wsqucjvfcwigoicgznzd.supabase.co"
        const val SUPABASE_KEY = "sb_publishable_z_p7qNpYNSt2F5JC8J5Yiw_CrMhhbT3"
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sync = SupabaseSync(SUPABASE_URL, SUPABASE_KEY)
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("…"))
        setupOverlay()
    }

    // ========== OVERLAY ==========

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            dp(PET_W), dp(PET_H),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        overlayView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/pet.html")
            setOnTouchListener(createTouchListener())
        }

        windowManager?.addView(overlayView, params)
    }

    // ========== TOUCH & GESTURE ==========

    private var ix = 0; private var iy = 0
    private var tx = 0f; private var ty = 0f
    private var t0 = 0L; private var lastTap = 0L
    private var moved = false

    private fun createTouchListener(): View.OnTouchListener = View.OnTouchListener { _, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                ix = params?.x ?: 0; iy = params?.y ?: 0
                tx = e.rawX; ty = e.rawY
                t0 = System.currentTimeMillis(); moved = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.rawX - tx).toInt()
                val dy = (e.rawY - ty).toInt()
                if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                    moved = true
                    params?.x = ix + dx
                    params?.y = iy + dy
                    windowManager?.updateViewLayout(overlayView, params)
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                val d = System.currentTimeMillis() - t0
                if (!moved) {
                    val type = when {
                        d > 600 -> { js("petEngine.onLongPress()"); "long_press" }
                        System.currentTimeMillis() - lastTap < 300 -> {
                            lastTap = 0; js("petEngine.onDoubleTap()"); "double_tap"
                        }
                        else -> { lastTap = System.currentTimeMillis(); js("petEngine.onTap()"); "tap" }
                    }
                    logGesture(type)
                }
                true
            }
            else -> false
        }
    }

    private fun js(code: String) {
        overlayView?.evaluateJavascript(code, null)
    }

    private fun logGesture(type: String) {
        val body = JSONObject().apply {
            put("gesture_type", type)
            put("x", params?.x ?: 0)
            put("y", params?.y ?: 0)
        }
        sync?.post("gesture_log", body)
    }

    // ========== NOTIFICATION ==========

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("\uD83D\uDC3E")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(CHANNEL_ID, "Pet", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
                .let { getSystemService(NotificationManager::class.java).createNotificationChannel(it) }
        }
    }

    // ========== UTILS ==========

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        scope.cancel()
        overlayView?.let { windowManager?.removeView(it); it.destroy() }
        overlayView = null
        super.onDestroy()
    }
}