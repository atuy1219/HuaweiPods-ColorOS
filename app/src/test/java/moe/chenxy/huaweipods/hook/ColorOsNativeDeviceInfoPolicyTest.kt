package moe.chenxy.huaweipods.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorOsNativeDeviceInfoPolicyTest {
    private val pod = ColorOsPodBattery(
        address = "30:A9:98:CB:C4:54",
        left = 76, leftConnected = true,
        right = 90, rightConnected = true,
        case = 24, caseConnected = true,
        name = "HUAWEI FreeClip",
    )

    @Test fun originalFreeClipIsNotConfusedWithSecondGeneration() {
        assertTrue(ColorOsNativeDeviceInfoPolicy.isOriginalFreeClip("HUAWEI FreeClip"))
        assertTrue(ColorOsNativeDeviceInfoPolicy.isOriginalFreeClip("Free-Clip"))
        assertFalse(ColorOsNativeDeviceInfoPolicy.isOriginalFreeClip("HUAWEI FreeClip 2"))
        assertFalse(ColorOsNativeDeviceInfoPolicy.isOriginalFreeClip("FreeClip2"))
        assertFalse(ColorOsNativeDeviceInfoPolicy.isOriginalFreeClip("FreeBuds Pro"))
    }

    @Test fun sdkOrderIsBoxLeftRight() {
        assertEquals(listOf("BOX" to 24, "LEFT" to 76, "RIGHT" to 90),
            ColorOsNativeDeviceInfoPolicy.batteryParts(pod))
    }

    @Test fun absentPartsAreNotMadeUp() {
        assertEquals(listOf("RIGHT" to 90),
            ColorOsNativeDeviceInfoPolicy.batteryParts(
                pod.copy(leftConnected = false, caseConnected = false)))
    }

    @Test fun imageUrlIsIdentityScoped() {
        val uri = ColorOsNativeDeviceInfoPolicy.imageUri(pod)
        assertTrue(uri!!.endsWith("/device/HUAWEI_FREECLIP/30:A9:98:CB:C4:54"))
        assertNull(ColorOsNativeDeviceInfoPolicy.imageUri(pod.copy(name = "HUAWEI FreeClip 2")))
        assertNull(ColorOsNativeDeviceInfoPolicy.imageUri(pod.copy(address = "invalid")))
    }
}
