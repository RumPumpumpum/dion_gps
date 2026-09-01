package com.dion.gpsmock

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

class MockLocationHelper(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val systemInjector = SystemLocationInjector(context)

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
            targetProviders().forEach { setupTestProvider(it) }
            pushAllProviders()
            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun stopMocking() {
        targetProviders().forEach { provider ->
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
        runCatching { pushAllProviders() }
    }

    private fun pushAllProviders() {
        val gps = RealLocationFactory.create(LocationManager.GPS_PROVIDER)
        systemInjector.inject(gps)

        targetProviders().forEach { provider ->
            val location = if (provider == LocationManager.GPS_PROVIDER) {
                gps
            } else {
                RealLocationFactory.create(provider)
            }
            locationManager.setTestProviderLocation(provider, location)
        }
    }

    private fun targetProviders(): List<String> {
        val providers = mutableListOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            providers += LocationManager.FUSED_PROVIDER
        }
        return providers
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
}
