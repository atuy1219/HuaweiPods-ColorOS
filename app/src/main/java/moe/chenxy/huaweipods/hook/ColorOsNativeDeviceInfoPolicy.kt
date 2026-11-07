package moe.chenxy.huaweipods.hook

import java.util.Locale
import moe.chenxy.huaweipods.BuildConfig

/**
 * A native OEM DeviceInfo can only be augmented when its explicit Bluetooth
 * identity matches an active HuaweiPods snapshot. Never match on a title alone.
 */
internal object ColorOsNativeDeviceInfoPolicy {
    private val originalFreeClip = Regex(
        "(?i)(?:^|[^a-z0-9])free[\\s-]*clip(?![\\s-]*2)(?=$|[^a-z0-9])",
    )

    fun isOriginalFreeClip(name: String?): Boolean =
        name != null && originalFreeClip.containsMatchIn(name)

    fun batteryParts(pod: ColorOsPodBattery): List<Pair<String, Int>> {
        val triple = colorOsBatteryTriple(pod) ?: return emptyList()
        return buildList {
            // The OEM BatteryType enum uses BOX, LEFT and RIGHT.
            triple.case?.let { add("BOX" to it) }
            triple.left?.let { add("LEFT" to it) }
            triple.right?.let { add("RIGHT" to it) }
        }
    }

    fun imageUri(pod: ColorOsPodBattery): String? {
        if (!isOriginalFreeClip(pod.name)) return null
        val address = pod.address.trim().uppercase(Locale.ROOT)
        if (!Regex("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}").matches(address)) return null
        return "content://${BuildConfig.APPLICATION_ID}.podimages/device/HUAWEI_FREECLIP/$address"
    }
}
