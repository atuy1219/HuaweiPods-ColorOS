package moe.chenxy.huaweipods.hook

import android.app.Application
import android.content.Context
import android.os.Bundle
import java.lang.reflect.Method

/**
 * Adapts native ColorOS/MyDevices and Melody widget data in place.
 * Non-matching cards and unknown host versions keep their stock behavior.
 * This does not register a HUAWEI device with the launcher/widget picker.
 */
internal object ColorOsNativeWidgetHook : HookContext() {
    private const val TAG = "HuaweiPods-NativeWidget"
    private val hostMethods = mapOf(
        "com.heytap.mydevices" to mapOf(
            "com.oplus.mydevices.quickapp.provider.DeviceQuickAppCardWidgetProvider" to setOf("handleGetCardData"),
            "com.oplus.mydevices.remotecards.provider.DeviceCardWidgetProvider" to setOf("processCall"),
            "com.oplus.mydevices.remotecards.provider.DeviceLiteCardWidgetProvider" to setOf("processCall"),
        ),
        "com.oplus.melody" to mapOf(
            "com.oplus.melody.card.MelodyCardWidgetProvider" to setOf("call"),
            // Confirmed in the supplied Melody 17.6.6 classes2.dex.
            "com.oplus.melody.mydevices.devicecard.MyDeviceProvider" to setOf("call"),
        ),
    )
    private var batterySource: ColorOsBatterySource? = null

    override fun onHook() {
        for ((name, methods) in hostMethods[packageName].orEmpty()) {
            runCatching {
                val matching = findClass(name).declaredMethods.filter {
                    it.name in methods && it.returnType != Void.TYPE
                }
                for (method in matching) {
                    method.isAccessible = true
                    hookCardResult(method)
                }
                if (matching.isEmpty()) Log.i(TAG, "No matching native card method: $name")
            }.onFailure { Log.w(TAG, "Native card provider unavailable: $name", it) }
        }
        runCatching {
            hookAfter(Application::class.java.getDeclaredMethod("onCreate")) {
                (instance as? Context)?.let(::ensureBatterySource)
            }
            currentApplicationOrNull()?.let(::ensureBatterySource)
        }.onFailure { Log.w(TAG, "Widget battery source unavailable", it) }
    }

    private fun hookCardResult(method: Method) {
        hookAfter(method) {
            runCatching {
                val pods = ColorOsPodBatteryStore.snapshot()
                if (pods.isEmpty()) return@runCatching
                when (val original = result) {
                    is String ->
                        ColorOsNativeWidgetPayload.rewrite(original, pods)?.let { result = it }
                    is Bundle -> patchBundle(original, pods)
                    else -> Unit
                }
            }.onFailure { Log.w(TAG, "Original vendor widget preserved", it) }
        }
        Log.i(TAG, "Installed $method")
    }

    private fun patchBundle(bundle: Bundle, pods: List<ColorOsPodBattery>) {
        // Process only plain JSON strings; do not deserialize proprietary Parcelables.
        for (key in runCatching { bundle.keySet().toList() }.getOrDefault(emptyList())) {
            val value = runCatching { bundle.getString(key) }.getOrNull() ?: continue
            ColorOsNativeWidgetPayload.rewrite(value, pods)?.let { bundle.putString(key, it) }
        }
    }

    @Synchronized
    private fun ensureBatterySource(context: Context) {
        if (batterySource != null) return
        batterySource = ColorOsBatterySource.forContext(context.applicationContext, TAG) {
            // The host owns its update cadence and card lifecycle.
        }.also { it.start() }
    }

    override fun onClose() {
        synchronized(this) {
            runCatching { batterySource?.close() }
            batterySource = null
        }
    }
}
