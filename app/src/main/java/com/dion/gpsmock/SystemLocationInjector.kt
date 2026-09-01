package com.dion.gpsmock

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder

class SystemLocationInjector(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    init {
        allowHiddenApis()
    }

    fun inject(location: Location): Boolean {
        val gps = Location(location).apply { provider = LocationManager.GPS_PROVIDER }
        RealLocationFactory.hideMockFlags(gps)
        if (injectOnLocationManager(gps)) return true
        if (injectOnILocationManager(gps)) return true
        return false
    }

    private fun injectOnLocationManager(location: Location): Boolean {
        return invokeInject(locationManager, location)
    }

    private fun injectOnILocationManager(location: Location): Boolean {
        val manager = iLocationManager() ?: return false
        return invokeInject(manager, location)
    }

    private fun invokeInject(target: Any, location: Location): Boolean {
        val methods = target.javaClass.methods.filter { it.name == "injectLocation" }
        for (method in methods) {
            val injected = runCatching {
                method.isAccessible = true
                val args = argumentsFor(method.parameterTypes, location) ?: return@runCatching false
                val result = method.invoke(target, *args)
                result != false
            }.getOrDefault(false)
            if (injected) return true
        }
        return false
    }

    private fun argumentsFor(types: Array<Class<*>>, location: Location): Array<Any?>? {
        return when {
            types.size == 1 && types[0] == Location::class.java ->
                arrayOf(location)
            types.size == 2 && types[0] == Location::class.java && types[1] == String::class.java ->
                arrayOf(location, context.packageName)
            types.size == 3 && types[0] == Location::class.java ->
                arrayOf(location, context.packageName, attributionTag())
            else -> null
        }
    }

    private fun attributionTag(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.attributionTag else null
    }

    private fun iLocationManager(): Any? {
        return runCatching {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val binder = serviceManager
                .getMethod("getService", String::class.java)
                .invoke(null, Context.LOCATION_SERVICE) as? IBinder
                ?: return@runCatching null
            val stub = Class.forName("android.location.ILocationManager\$Stub")
            stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        }.getOrNull()
    }

    private fun allowHiddenApis() {
        runCatching {
            val vmRuntime = Class.forName("dalvik.system.VMRuntime")
            val getRuntime = vmRuntime.getDeclaredMethod("getRuntime")
            val setHiddenApiExemptions = vmRuntime.getDeclaredMethod(
                "setHiddenApiExemptions",
                Array<String>::class.java
            )
            getRuntime.isAccessible = true
            setHiddenApiExemptions.isAccessible = true
            setHiddenApiExemptions.invoke(getRuntime.invoke(null), arrayOf("L") as Any)
        }
    }
}
