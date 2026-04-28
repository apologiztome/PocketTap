package com.pockettap

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.*
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class TapService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var audioManager: AudioManager
    private lateinit var wakeLock: PowerManager.WakeLock

    private val handler = Handler(Looper.getMainLooper())

    // --- Tuning values ---
    private val TAP_THRESHOLD = 11.5f      // How hard a tap needs to be (m/s² above gravity)
    private val DEBOUNCE_MS = 120L         // Ignore rapid noise after a tap
    private val DOUBLE_TAP_WINDOW_MS = 420L // Time window to catch a second tap

    private var lastTapTime = 0L
    private var pendingTapCount = 0

    // After the double-tap window expires, decide what to do
    private val decideTapRunnable = Runnable {
        when (pendingTapCount) {
            1 -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)      // Single tap → Next
            2 -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)  // Double tap → Previous
        }
        pendingTapCount = 0
    }

    companion object {
        const val CHANNEL_ID = "PocketTapChannel"
        const val NOTIF_ID = 1
        const val ACTION_STOP = "com.pockettap.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Wake lock keeps CPU alive so sensor works with screen off
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketTap::WakeLock")
        wakeLock.acquire()

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        // Register sensor — FASTEST gives best tap detection
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY // Restart if killed
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Total acceleration magnitude
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        // How much above normal gravity (9.8 m/s²)
        val delta = magnitude - SensorManager.GRAVITY_EARTH

        if (delta > TAP_THRESHOLD) {
            val now = System.currentTimeMillis()

            // Debounce: ignore if too soon after last tap
            if (now - lastTapTime < DEBOUNCE_MS) return

            lastTapTime = now
            pendingTapCount++

            // Reset the decision timer
            handler.removeCallbacks(decideTapRunnable)
            handler.postDelayed(decideTapRunnable, DOUBLE_TAP_WINDOW_MS)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun dispatchMediaKey(keyCode: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PocketTap Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps tap detection running"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, TapService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPending = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PocketTap Active 👆")
            .setContentText("1 tap = Next  •  2 taps = Previous")
            .setSmallIcon(android.R.drawable.ic_media_next)
            .setContentIntent(openAppPending)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        handler.removeCallbacksAndMessages(null)
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
