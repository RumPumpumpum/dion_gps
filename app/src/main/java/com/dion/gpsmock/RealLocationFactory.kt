package com.dion.gpsmock

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock

object RealLocationFactory {

    fun create(provider: String): Location {
        val tick = ((SystemClock.elapsedRealtime() / 3_000L) % 5L).toInt()
        val gpsLike = provider != LocationManager.NETWORK_PROVIDER

        return Location(provider).apply {
            latitude = LocationConstants.TARGET_LAT
            longitude = LocationConstants.TARGET_LNG
            altitude = 12.0 + tick * 0.35
            accuracy = if (gpsLike) 5.8f + tick * 0.4f else 22f + tick * 1.2f
            bearing = 0f
            speed = 0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                elapsedRealtimeUncertaintyNanos = 2_000.0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                verticalAccuracyMeters = if (gpsLike) 8.0f + tick * 0.3f else 24f
                bearingAccuracyDegrees = 12f
                speedAccuracyMetersPerSecond = 0.4f
            }
            extras = gnssExtras(gpsLike, tick)
        }.also {
            makeComplete(it)
            hideMockFlags(it)
        }
    }

    private fun gnssExtras(gpsLike: Boolean, tick: Int): Bundle {
        return Bundle().apply {
            if (gpsLike) {
                putInt("satellites", 11 + tick % 4)
                putInt("maxCn0", 36 + tick)
                putInt("meanCn0", 26 + tick)
            }
        }
    }

    fun hideMockFlags(location: Location) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock = false
        }
        runCatching {
            Location::class.java
                .getMethod("setIsFromMockProvider", Boolean::class.javaPrimitiveType)
                .invoke(location, false)
        }
        location.extras?.remove("mockLocation")
    }

    private fun makeComplete(location: Location) {
        runCatching {
            Location::class.java.getMethod("makeComplete").invoke(location)
        }
    }
}
