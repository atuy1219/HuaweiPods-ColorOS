package moe.chenxy.huaweipods.hook

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorOsNativeWidgetPayloadTest {
    private val freeClip = ColorOsPodBattery(
        "30:A9:98:CB:C4:54", 83, true, 47, true, 92, true, "HUAWEI FreeClip",
    )

    @Test fun existingCardKeepsLayoutAndUpdatesMatchedValues() {
        val input = """{"cards":[{"deviceId":"f68a5d82181e48edd39eb1824f43c1fa","data":{"batteryLeft":{"value":5,"charge":true},"batteryRight":{"value":5},"batteryMain":{"value":5},"batteryMin":{"value":5},"batteryProgress":5,"isSupportMutiBattery":false,"deviceType":"HEADSET_TWS"}},{"deviceId":"00:11:22:33:44:55","batteryProgress":11}]}"""
        val out = ColorOsNativeWidgetPayload.rewrite(input, listOf(freeClip))!!
        assertTrue(out.contains("\"batteryProgress\":47"))
        assertTrue(out.contains("\"batteryLeft\":{\"value\":83,\"charge\":true}"))
        assertTrue(out.contains("\"deviceType\":\"HEADSET_OWS\""))
        assertTrue(out.contains("\"batteryProgress\":11"))
    }

    @Test fun unknownIdentityOrMissingIdentityIsNotModified() {
        assertNull(ColorOsNativeWidgetPayload.rewrite("""{"batteryProgress":10}""", listOf(freeClip)))
        assertNull(ColorOsNativeWidgetPayload.rewrite("""{"deviceId":"00:11:22:33:44:55","batteryProgress":10}""", listOf(freeClip)))
    }

    @Test fun unknownChildNeverInheritsKnownParent() {
        val input = """{"mac":"30:A9:98:CB:C4:54","batteryProgress":5,"child":{"mac":"00:11:22:33:44:55","batteryProgress":3}}"""
        val out = ColorOsNativeWidgetPayload.rewrite(input, listOf(freeClip))!!
        assertTrue(out.contains("\"batteryProgress\":47"))
        assertTrue(out.contains("\"batteryProgress\":3"))
    }

    @Test fun zeroPercentAndUnknownDeviceAreHandledSafely() {
        val single = freeClip.copy(left = 0, rightConnected = false, caseConnected = false)
        val out = ColorOsNativeWidgetPayload.rewrite(
            """{"address":"30:A9:98:CB:C4:54","batteryLeft":{"value":20},"batteryProgress":20}""",
            listOf(single),
        )!!
        assertTrue(out.contains("\"batteryLeft\":{\"value\":0}"))
        assertTrue(out.contains("\"batteryProgress\":0"))
        assertNull(ColorOsNativeWidgetPayload.rewrite("{", listOf(freeClip)))
    }
}
