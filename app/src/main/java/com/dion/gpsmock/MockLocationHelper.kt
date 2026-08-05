package com.dion.gpsmock

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat

class MockLocationHelper(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isMockLocationAppSelected(): Boolean {
        if (canUseMockLocationProbe()) return true
        return isSelectedViaAppOps()
    }

    private fun canUseMockLocationProbe(): Boolean {
        return try {
            val probeProvider = "dion_mock_probe"
            runCatching { locationManager.removeTestProvider(probeProvider) }
            locationManager.addTestProvider(
                probeProvider,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                android.location.Criteria.POWER_LOW,
                android.location.Criteria.ACCURACY_FINE
            )
            locationManager.removeTestProvider(probeProvider)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun isSelectedViaAppOps(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_MOCK_LOCATION,
                android.os.Process.myUid(),
                context.packageName
            ) == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun startMocking(): Result<Unit> {
        if (!hasLocationPermission()) {
            return Result.failure(SecurityException("위치 권한이 필요합니다."))
        }

        return try {
            setupTestProvider(LocationManager.GPS_PROVIDER)
            setupTestProvider(LocationManager.NETWORK_PROVIDER)
            pushMockLocation(LocationManager.GPS_PROVIDER)
            pushMockLocation(LocationManager.NETWORK_PROVIDER)
            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun stopMocking() {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            runCatching {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.setTestProviderEnabled(provider, false)
                }
            }
            runCatching {
                locationManager.removeTestProvider(provider)
            }
        }
    }

    fun refreshMockLocation() {
        if (!hasLocationPermission()) return
        runCatching {
            pushMockLocation(LocationManager.GPS_PROVIDER)
            pushMockLocation(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun setupTestProvider(provider: String) {
        runCatching { locationManager.removeTestProvider(provider) }

        locationManager.addTestProvider(
            provider,
            false,
            false,
            false,
            false,
            true,
            true,
            true,
            android.location.Criteria.POWER_LOW,
            android.location.Criteria.ACCURACY_FINE
        )
        locationManager.setTestProviderEnabled(provider, true)
    }

    private fun pushMockLocation(provider: String) {
        val location = Location(provider).apply {
            latitude = LocationConstants.TARGET_LAT
            longitude = LocationConstants.TARGET_LNG
            altitude = 10.0
            accuracy = 1.0f
            bearing = 0f
            speed = 0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                verticalAccuracyMeters = 1.0f
                bearingAccuracyDegrees = 0.1f
                speedAccuracyMetersPerSecond = 0.01f
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                isMock = true
            }
        }
        locationManager.setTestProviderLocation(provider, location)
    }
}
