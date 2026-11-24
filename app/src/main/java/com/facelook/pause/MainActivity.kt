package com.facelook.pause

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.facelook.pause.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var mediaController: MediaPlaybackController

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }

        if (cameraGranted) {
            startFaceLookService()
        } else {
            binding.switchMaster.isChecked = false
            prefs.isServiceEnabled = false
            Toast.makeText(this, "Camera permission is required for gaze detection", Toast.LENGTH_LONG).show()
            updateStatusUi(isActive = false, isLookingAway = false, isPaused = false)
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FaceLookDetectionService.ACTION_STATUS_UPDATE) {
                val isMonitoring = intent.getBooleanExtra(FaceLookDetectionService.EXTRA_IS_MONITORING, false)
                val isLookingAway = intent.getBooleanExtra(FaceLookDetectionService.EXTRA_LOOKING_AWAY, false)
                val isPaused = intent.getBooleanExtra(FaceLookDetectionService.EXTRA_MEDIA_PAUSED, false)

                binding.switchMaster.isChecked = FaceLookDetectionService.isRunning && isMonitoring
                updateStatusUi(isActive = isMonitoring && FaceLookDetectionService.isRunning, isLookingAway = isLookingAway, isPaused = isPaused)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)
        mediaController = MediaPlaybackController(this)

        setupUI()
    }

    private fun setupUI() {
        // Dormant by default
        binding.switchMaster.isChecked = FaceLookDetectionService.isRunning && prefs.isServiceEnabled
        updateStatusUi(
            isActive = FaceLookDetectionService.isRunning && prefs.isServiceEnabled,
            isLookingAway = false,
            isPaused = false
        )

        binding.switchMaster.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkPermissionsAndStart()
            } else {
                stopFaceLookService()
            }
        }

        // Delay Slider
        val currentDelay = prefs.lookAwayDelayMs.toFloat()
        binding.sliderDelay.value = currentDelay.coerceIn(400f, 2000f)
        binding.tvDelayValue.text = "${currentDelay.toInt()} ms"
        binding.sliderDelay.addOnChangeListener { _, value, _ ->
            prefs.lookAwayDelayMs = value.toLong()
            binding.tvDelayValue.text = "${value.toInt()} ms"
        }

        // Auto Resume Switch
        binding.switchAutoResume.isChecked = prefs.isAutoResumeEnabled
        binding.switchAutoResume.setOnCheckedChangeListener { _, isChecked ->
            prefs.isAutoResumeEnabled = isChecked
        }

        // Haptic Feedback Switch
        binding.switchVibrate.isChecked = prefs.isVibrateOnAction
        binding.switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            prefs.isVibrateOnAction = isChecked
        }

        // Test Media Buttons
        binding.btnTestPause.setOnClickListener {
            mediaController.pausePlayback()
            Toast.makeText(this, "Dispatched Media Pause ⏸", Toast.LENGTH_SHORT).show()
        }

        binding.btnTestPlay.setOnClickListener {
            mediaController.resumePlayback()
            Toast.makeText(this, "Dispatched Media Play ▶", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionsAndStart() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            permissionLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            startFaceLookService()
        }
    }

    private fun startFaceLookService() {
        prefs.isServiceEnabled = true
        val serviceIntent = Intent(this, FaceLookDetectionService::class.java).apply {
            action = FaceLookDetectionService.ACTION_START
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        updateStatusUi(isActive = true, isLookingAway = false, isPaused = false)
        Toast.makeText(this, "FaceLook Gaze Monitor Activated", Toast.LENGTH_SHORT).show()
    }

    private fun stopFaceLookService() {
        prefs.isServiceEnabled = false
        val serviceIntent = Intent(this, FaceLookDetectionService::class.java).apply {
            action = FaceLookDetectionService.ACTION_STOP_SERVICE
        }
        startService(serviceIntent)
        updateStatusUi(isActive = false, isLookingAway = false, isPaused = false)
        Toast.makeText(this, "FaceLook Monitor Stopped (Dormant)", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatusUi(isActive: Boolean, isLookingAway: Boolean, isPaused: Boolean) {
        when {
            !isActive -> {
                binding.viewStatusDot.setBackgroundResource(R.drawable.bg_dot_inactive)
                binding.tvStatusText.text = "Service Inactive (Dormant)"
                binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            }
            isPaused -> {
                binding.viewStatusDot.setBackgroundResource(R.drawable.bg_dot_alert)
                binding.tvStatusText.text = "Status: Looked Away • Media Paused"
                binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_alert))
            }
            isLookingAway -> {
                binding.viewStatusDot.setBackgroundResource(R.drawable.bg_dot_alert)
                binding.tvStatusText.text = "Status: Gaze Away Detected"
                binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_alert))
            }
            else -> {
                binding.viewStatusDot.setBackgroundResource(R.drawable.bg_dot_active)
                binding.tvStatusText.text = "Status: Monitoring Gaze • Looking at Screen"
                binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_active))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(FaceLookDetectionService.ACTION_STATUS_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
        binding.switchMaster.isChecked = FaceLookDetectionService.isRunning && prefs.isServiceEnabled
        updateStatusUi(
            isActive = FaceLookDetectionService.isRunning && prefs.isServiceEnabled,
            isLookingAway = false,
            isPaused = false
        )
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(statusReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }
}
