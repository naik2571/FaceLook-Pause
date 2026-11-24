package com.facelook.pause

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mlkit.vision.face.Face
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class FaceLookDetectionService : LifecycleService() {

    companion object {
        const val TAG = "FaceLookService"
        const val CHANNEL_ID = "facelook_pause_channel"
        const val NOTIFICATION_ID = 4040

        const val ACTION_START = "com.facelook.pause.ACTION_START"
        const val ACTION_STOP_SERVICE = "com.facelook.pause.ACTION_STOP_SERVICE"
        const val ACTION_TOGGLE_PAUSE = "com.facelook.pause.ACTION_TOGGLE_PAUSE"
        const val ACTION_STATUS_UPDATE = "com.facelook.pause.ACTION_STATUS_UPDATE"

        const val EXTRA_IS_MONITORING = "extra_is_monitoring"
        const val EXTRA_LOOKING_AWAY = "extra_looking_away"
        const val EXTRA_MEDIA_PAUSED = "extra_media_paused"

        var isRunning = false
            private set
    }

    private lateinit var prefs: PreferencesManager
    private lateinit var mediaController: MediaPlaybackController
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    private var isMonitoringPaused = false
    private var wasAutoPausedByFaceLook = false
    private var isLookingAwayState = false

    private var lookAwayStartTime = 0L
    private var lookBackStartTime = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen off: pausing camera detection to save battery")
                    unbindCamera()
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen on: resuming camera detection")
                    if (!isMonitoringPaused) {
                        bindCameraAnalysis()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        mediaController = MediaPlaybackController(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                Log.d(TAG, "Stop service requested via Notification Kill-Switch")
                stopDetectionService()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                isMonitoringPaused = !isMonitoringPaused
                Log.d(TAG, "Toggled monitoring paused: $isMonitoringPaused")
                if (isMonitoringPaused) {
                    unbindCamera()
                } else {
                    bindCameraAnalysis()
                }
                updateNotification()
                broadcastStatus()
                return START_STICKY
            }
        }

        // Start Foreground Service with persistent Notification
        startForegroundWithNotification()

        if (!isMonitoringPaused) {
            bindCameraAnalysis()
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FaceLookDetectionService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, FaceLookDetectionService::class.java).apply { action = ACTION_TOGGLE_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleActionTitle = if (isMonitoringPaused) "▶ RESUME" else "⏸ PAUSE"
        val statusText = when {
            isMonitoringPaused -> "Status: Monitoring Paused"
            wasAutoPausedByFaceLook -> "Status: Auto-Paused (Looked Away)"
            else -> "Status: Active • Monitoring Gaze"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FaceLook Pause Active")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_notification, toggleActionTitle, toggleIntent)
            .addAction(R.drawable.ic_notification, "🛑 STOP / CLOSE", stopIntent)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun bindCameraAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                cameraProvider?.unbindAll()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor, FaceTrackerAnalyzer { face ->
                    processFaceState(face)
                })

                cameraProvider?.bindToLifecycle(this, cameraSelector, imageAnalysis)
                Log.d(TAG, "Front Camera Analysis bound successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CameraX lifecycle", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun unbindCamera() {
        try {
            cameraProvider?.unbindAll()
            Log.d(TAG, "Front camera unbound")
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding camera", e)
        }
    }

    private fun processFaceState(face: Face?) {
        if (isMonitoringPaused) return

        val now = System.currentTimeMillis()
        val isLookingAtScreen = if (face != null) {
            val yaw = abs(face.headEulerAngleY)
            val pitch = abs(face.headEulerAngleX)
            yaw < prefs.yawThreshold && pitch < prefs.pitchThreshold
        } else {
            false
        }

        if (!isLookingAtScreen) {
            // User is looking away or face is absent
            lookBackStartTime = 0L
            if (lookAwayStartTime == 0L) {
                lookAwayStartTime = now
            } else if (now - lookAwayStartTime >= prefs.lookAwayDelayMs) {
                if (!isLookingAwayState) {
                    isLookingAwayState = true
                    handleLookAwayTrigger()
                }
            }
        } else {
            // User is looking directly at screen
            lookAwayStartTime = 0L
            if (lookBackStartTime == 0L) {
                lookBackStartTime = now
            } else if (now - lookBackStartTime >= prefs.lookBackDelayMs) {
                if (isLookingAwayState || wasAutoPausedByFaceLook) {
                    isLookingAwayState = false
                    handleLookBackTrigger()
                }
            }
        }
    }

    private fun handleLookAwayTrigger() {
        mainHandler.post {
            val isPlaying = mediaController.isMediaPlaying()
            Log.i(TAG, "Look-Away confirmed! Is media actively playing? $isPlaying")

            if (isPlaying) {
                mediaController.pausePlayback()
                wasAutoPausedByFaceLook = true
                vibrateFeedback()
                updateNotification()
                broadcastStatus()
            }
        }
    }

    private fun handleLookBackTrigger() {
        mainHandler.post {
            Log.i(TAG, "Look-Back confirmed! Was auto-paused previously? $wasAutoPausedByFaceLook")

            if (wasAutoPausedByFaceLook && prefs.isAutoResumeEnabled) {
                mediaController.resumePlayback()
                wasAutoPausedByFaceLook = false
                vibrateFeedback()
                updateNotification()
                broadcastStatus()
            }
        }
    }

    private fun vibrateFeedback() {
        if (!prefs.isVibrateOnAction) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(40)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    private fun broadcastStatus() {
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_MONITORING, !isMonitoringPaused)
            putExtra(EXTRA_LOOKING_AWAY, isLookingAwayState)
            putExtra(EXTRA_MEDIA_PAUSED, wasAutoPausedByFaceLook)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun stopDetectionService() {
        prefs.isServiceEnabled = false
        isRunning = false
        unbindCamera()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        broadcastStatus()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FaceLook Gaze Detection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors user gaze and automatically pauses media when looking away"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Screen receiver already unregistered", e)
        }
        unbindCamera()
        cameraExecutor.shutdown()
        Log.d(TAG, "FaceLookDetectionService destroyed")
    }
}
