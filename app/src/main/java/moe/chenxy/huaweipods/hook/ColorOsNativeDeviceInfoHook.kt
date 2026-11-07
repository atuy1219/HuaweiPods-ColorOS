package moe.chenxy.huaweipods.hook

import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * Hooks the OEM device model before native widgets build their presentation.
 *
 * Verified against Melody 17.6.6's com.oplus.mydevices.sdk.device.DeviceInfo
 * and BatteryInfo in the user-supplied APK. MyDevices can ship another SDK
 * version; incompatible classes are skipped without modifying the host.
 *
 * This does not add an OPPO product ID or pretend a FreeClip supports ANC.
 */
internal object ColorOsNativeDeviceInfoHook : HookContext() {
    private const val TAG = "HuaweiPods-DeviceInfo"
    private const val DEVICE_INFO = "com.oplus.mydevices.sdk.device.DeviceInfo"
    private const val BATTERY_INFO = "com.oplus.mydevices.sdk.device.BatteryInfo"
    private const val BATTERY_TYPE = "com.oplus.mydevices.sdk.device.BatteryType"

    override fun onHook() {
        val model = runCatching { findClass(DEVICE_INFO) }.getOrElse {
            Log.i(TAG, "DeviceInfo SDK unavailable in $packageName; keeping native widgets unchanged")
            return
        }
        model.declaredMethods.filter { method ->
            method.parameterCount == 0 && method.name in setOf(
                "getBatteryInfoList", "getBatteryList", "getIconUrl", "getMIconUrl",
                "isSupportNoiseCanceling",
            )
        }.forEach { method ->
            runCatching {
                method.isAccessible = true
                installGetter(method)
            }.onFailure { Log.w(TAG, "Skipped incompatible DeviceInfo method: ${method.name}", it) }
        }
    }

    private fun installGetter(method: Method) {
        hookAfter(method) {
            runCatching {
                val device = instance ?: return@runCatching
                val pod = identify(device) ?: return@runCatching
                when (method.name) {
                    "getBatteryInfoList", "getBatteryList" -> {
                        val items = batteryInfo(pod) ?: return@runCatching
                        if (items.isNotEmpty() && method.returnType.isAssignableFrom(ArrayList::class.java)) {
                            result = ArrayList(items)
                        } else if (items.isNotEmpty() && method.returnType.isAssignableFrom(List::class.java)) {
                            result = items
                        }
                    }
                    "getIconUrl", "getMIconUrl" -> {
                        if (method.returnType != String::class.java) return@runCatching
                        ColorOsNativeDeviceInfoPolicy.imageUri(pod)?.let { result = it }
                    }
                    "isSupportNoiseCanceling" -> {
                        // FreeClip 1 has no noise cancellation hardware. Do not
                        // expose the four OPPO ANC shortcuts from preview cards.
                        if (ColorOsNativeDeviceInfoPolicy.isOriginalFreeClip(pod.name) &&
                            method.returnType == Boolean::class.javaPrimitiveType
                        ) result = false
                    }
                }
            }.onFailure { Log.w(TAG, "OEM device model left unchanged after reflection failure", it) }
        }
        Log.i(TAG, "Native DeviceInfo getter ready: ${method.name}")
    }

    private fun identify(deviceInfo: Any): ColorOsPodBattery? {
        fun value(method: String): String? = runCatching {
            deviceInfo.javaClass.getMethod(method).invoke(deviceInfo) as? String
        }.getOrNull()
        val identities = listOf(value("getMacAddress"), value("getDeviceId"))
        return colorOsPodForIdentity(
            ColorOsPodBatteryStore.snapshot(),
            identities = identities,
        )
    }

    private fun batteryInfo(pod: ColorOsPodBattery): List<Any>? {
        val parts = ColorOsNativeDeviceInfoPolicy.batteryParts(pod)
        if (parts.isEmpty()) return null
        return runCatching {
            val batteryType = findClass(BATTERY_TYPE)
            val batteryClass = findClass(BATTERY_INFO)
            val ctor: Constructor<*> = batteryClass.getDeclaredConstructor(
                batteryType, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!,
            ).apply { isAccessible = true }
            parts.mapNotNull { (typeName, level) ->
                val type = batteryType.enumConstants?.firstOrNull {
                    (it as? Enum<*>)?.name == typeName
                } ?: return@mapNotNull null
                // Snapshot currently has level + presence, not per-ear charging;
                // do not invent charging state for the OEM SDK.
                ctor.newInstance(type, level, false)
            }.takeIf { it.size == parts.size }
        }.getOrNull()
    }
}
