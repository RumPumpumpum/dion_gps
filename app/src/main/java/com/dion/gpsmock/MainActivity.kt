package com.dion.gpsmock

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dion.gpsmock.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mockLocationHelper: MockLocationHelper
    private lateinit var appPreferences: AppPreferences
    private var pulseAnimator: AnimatorSet? = null

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateUi()
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestNotificationPermissionIfNeeded()
            handleToggleClick()
        } else {
                        Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mockLocationHelper = MockLocationHelper(this)
        appPreferences = AppPreferences(this)

        binding.btnToggle.setOnClickListener { onToggleClicked() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnAddWidget.setOnClickListener { requestPinWidget() }

        updateUi()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(MockLocationService.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }
        updateUi()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(stateReceiver)
        stopPulse()
    }

    private fun requestPinWidget() {
        val manager = getSystemService(AppWidgetManager::class.java)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(
                ComponentName(this, MockWidgetProvider::class.java),
                null,
                null
            )
        } else {
            Toast.makeText(this, R.string.widget_pin_unsupported, Toast.LENGTH_LONG).show()
        }
    }

    private fun startPulse() {
        if (pulseAnimator != null) return
        val ring = binding.pulseRing
        val scaleX = ObjectAnimator.ofFloat(ring, "scaleX", 1f, 1.3f)
        val scaleY = ObjectAnimator.ofFloat(ring, "scaleY", 1f, 1.3f)
        val alpha = ObjectAnimator.ofFloat(ring, "alpha", 0.45f, 0f)
        listOf(scaleX, scaleY, alpha).forEach {
            it.duration = 1500L
            it.repeatCount = ValueAnimator.INFINITE
            it.repeatMode = ValueAnimator.RESTART
        }
        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        binding.pulseRing.alpha = 0f
        binding.pulseRing.scaleX = 1f
        binding.pulseRing.scaleY = 1f
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun onToggleClicked() {
        if (!mockLocationHelper.hasLocationPermission()) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        requestNotificationPermissionIfNeeded()

        if (!mockLocationHelper.isMockLocationAppSelected()) {
            showMockLocationSetupDialog()
            return
        }

        handleToggleClick()
    }

    private fun handleToggleClick() {
        if (MockLocationService.isRunning(this)) {
            MockLocationService.stop(this)
            Toast.makeText(this, R.string.mock_stopped, Toast.LENGTH_SHORT).show()
        } else {
            MockLocationService.start(this)
            Toast.makeText(this, R.string.mock_started, Toast.LENGTH_SHORT).show()
        }
        updateUi()
    }

    private fun showMockLocationSetupDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.mock_setup_title)
            .setMessage(R.string.mock_setup_message)
            .setPositiveButton(R.string.open_developer_options) { _, _ ->
                openDeveloperOptions()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openDeveloperOptions() {
        // ":settings:fragment_args_key"를 넘기면 개발자 옵션이 열리면서
        // "모의 위치 앱 선택" 항목으로 스크롤·하이라이트된다 (AOSP 계열 지원).
        val mockLocationPrefKey = "mock_location_app"
        val devIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            putExtra(":settings:fragment_args_key", mockLocationPrefKey)
            putExtra(":settings:show_fragment_args", Bundle().apply {
                putString(":settings:fragment_args_key", mockLocationPrefKey)
            })
        }
        val intents = listOf(devIntent, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        for (intent in intents) {
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return
            }
        }
        Toast.makeText(this, R.string.developer_options_unavailable, Toast.LENGTH_LONG).show()
    }

    private fun updateUi() {
        val isRunning = MockLocationService.isRunning(this)

        binding.btnToggle.setBackgroundResource(
            if (isRunning) R.drawable.circle_toggle_on else R.drawable.circle_toggle_off
        )
        val toggleColor = getColor(
            if (isRunning) R.color.accent_active else R.color.toggle_content_off
        )
        binding.imgToggleIcon.setColorFilter(toggleColor)
        binding.tvToggleLabel.setTextColor(toggleColor)
        binding.tvToggleLabel.text = getString(
            if (isRunning) R.string.btn_stop_mock else R.string.btn_start_mock
        )

        if (isRunning) startPulse() else stopPulse()

        binding.statusIndicator.setBackgroundResource(
            if (isRunning) R.drawable.status_active else R.drawable.status_inactive
        )
        binding.tvStatus.text = getString(
            if (isRunning) R.string.status_active else R.string.status_inactive
        )

        val endRealtime = appPreferences.autoOffEndRealtime
        val showTimer = isRunning && endRealtime > android.os.SystemClock.elapsedRealtime()
        if (showTimer) {
            binding.chronoRemaining.base = endRealtime
            binding.chronoRemaining.isCountDown = true
            binding.chronoRemaining.start()
            binding.timerRow.visibility = android.view.View.VISIBLE
        } else {
            binding.chronoRemaining.stop()
            binding.timerRow.visibility = android.view.View.GONE
        }

        binding.tvAddress.text = LocationConstants.TARGET_ADDRESS
        binding.tvCoordinates.text = getString(
            R.string.coordinates_format,
            LocationConstants.TARGET_LAT,
            LocationConstants.TARGET_LNG
        )

        val autoOffLabel = AppPreferences.labelForMinutes(appPreferences.autoOffMinutes)
        binding.btnSettings.text = getString(R.string.auto_off_button, autoOffLabel)
    }
}
