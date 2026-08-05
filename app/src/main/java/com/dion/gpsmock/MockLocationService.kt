package com.dion.gpsmock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MockLocationService : Service() {

    private lateinit var mockLocationHelper: MockLocationHelper
    private lateinit var appPreferences: AppPreferences

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var refreshJob: Job? = null
    private var autoOffJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        mockLocationHelper = MockLocationHelper(this)
        appPreferences = AppPreferences(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMockingInternal()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val result = mockLocationHelper.startMocking()
                if (result.isFailure) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                startForeground(NOTIFICATION_ID, buildNotification())
                startRefreshLoop()
                scheduleAutoOff()
                sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        refreshJob?.cancel()
        autoOffJob?.cancel()
        appPreferences.autoOffEndRealtime = 0L
        mockLocationHelper.stopMocking()
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
        super.onDestroy()
    }

    private fun stopMockingInternal() {
        refreshJob?.cancel()
        autoOffJob?.cancel()
        appPreferences.autoOffEndRealtime = 0L
        mockLocationHelper.stopMocking()
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
    }

    private fun startRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = serviceScope.launch {
            while (isActive) {
                mockLocationHelper.refreshMockLocation()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun scheduleAutoOff() {
        autoOffJob?.cancel()
        val minutes = appPreferences.autoOffMinutes
        if (minutes <= 0) {
            appPreferences.autoOffEndRealtime = 0L
            return
        }

        val durationMs = minutes * 60_000L
        appPreferences.autoOffEndRealtime = android.os.SystemClock.elapsedRealtime() + durationMs

        autoOffJob = serviceScope.launch {
            delay(durationMs)
            stopMockingInternal()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(LocationConstants.TARGET_ADDRESS)
            .setSmallIcon(R.drawable.ic_location)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_stop, getString(R.string.stop_mock), stopIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.dion.gpsmock.action.START"
        const val ACTION_STOP = "com.dion.gpsmock.action.STOP"
        const val ACTION_STATE_CHANGED = "com.dion.gpsmock.action.STATE_CHANGED"

        private const val CHANNEL_ID = "mock_location_channel"
        private const val NOTIFICATION_ID = 1001
        private const val REFRESH_INTERVAL_MS = 3_000L

        fun start(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            return manager.getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == MockLocationService::class.java.name }
        }
    }
}
