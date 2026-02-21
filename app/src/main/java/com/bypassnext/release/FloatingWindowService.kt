package com.bypassnext.release

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var ivFloatingIcon: ImageView
    private val repository = DefaultPrivacyRepository()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        startForegroundService()

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)
        ivFloatingIcon = floatingView.findViewById(R.id.ivFloatingIcon)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 0
        layoutParams.y = 100

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(floatingView, layoutParams)

        setupTouchListener()
        setupClickListener()
        updateInitialState()
    }

    private fun startForegroundService() {
        val channelId = "FloatingServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.floating_service_running),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.floating_service_running))
            .setSmallIcon(R.drawable.ic_logo)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    private fun setupTouchListener() {
        floatingView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val Xdiff = (event.rawX - initialTouchX).toInt()
                        val Ydiff = (event.rawY - initialTouchY).toInt()

                        if (Math.abs(Xdiff) < 10 && Math.abs(Ydiff) < 10) {
                            v.performClick()
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, layoutParams)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun setupClickListener() {
        floatingView.setOnClickListener {
            togglePrivacy()
        }
    }

    private fun updateInitialState() {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE)
        val nextDnsId = prefs.getString(AppConstants.KEY_NEXTDNS_ID, "") ?: ""

        if (nextDnsId.isNotEmpty()) {
             serviceScope.launch {
                 val isActive = repository.isPrivacyModeEnabled(nextDnsId)
                 updateIcon(isActive)
             }
        }
    }

    private fun togglePrivacy() {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE)
        val nextDnsId = prefs.getString(AppConstants.KEY_NEXTDNS_ID, "") ?: ""

        if (nextDnsId.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_nextdns_id_required), Toast.LENGTH_SHORT).show()
            return
        }

        val tempDir = cacheDir.absolutePath + "/filtered_certs"

        serviceScope.launch {
            val isActive = repository.isPrivacyModeEnabled(nextDnsId)

            val result = if (isActive) {
                repository.disablePrivacyMode(tempDir)
            } else {
                repository.enablePrivacyMode(nextDnsId, tempDir)
            }

            result.onSuccess {
                updateIcon(!isActive)
                val message = if (!isActive) getString(R.string.status_active) else getString(R.string.status_inactive)
                withContext(Dispatchers.Main) {
                     Toast.makeText(this@FloatingWindowService, message, Toast.LENGTH_SHORT).show()
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingWindowService, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateIcon(isActive: Boolean) {
        ivFloatingIcon.setImageResource(
            if (isActive) R.drawable.bg_circle_active else R.drawable.bg_circle_inactive
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
