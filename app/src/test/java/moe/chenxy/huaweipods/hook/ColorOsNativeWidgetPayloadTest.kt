package moe.chenxy.huaweipods.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun nativeFreeClipCardGetsFourSoundControls() {
        val json = """{"id":"f68a5d82181e48edd39eb1824f43c1fa","type":"BT_DEVICE_A2DP","name":"FreeClip","connectState":"CONNECTED","shortcuts":[{"id":"NOISE_REDUCTION_OPEN","state":true}]}"""
        val out = ColorOsNativeWidgetPayload.rewrite(
            json, listOf(freeClip), soundControlsEnabled = true,
            selectedEqId = { 10 },
        )!!
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(out)
            as kotlinx.serialization.json.JsonObject
        assertEquals("HEADSET_OWS", (parsed["type"] as kotlinx.serialization.json.JsonPrimitive).content)
        val shortcuts = parsed["shortcuts"] as kotlinx.serialization.json.JsonArray
        assertEquals(4, shortcuts.size)
        assertEquals(listOf(
            "SOUND_EFFECT_BALANCE",
            "SOUND_EFFECT_LOW_SOUND_ENHANCE",
            "SOUND_EFFECT_BRIGHTNESS",
            "SOUND_EFFECT_PURE_VOICE",
        ), shortcuts.map {
            ((it as kotlinx.serialization.json.JsonObject)["id"] as kotlinx.serialization.json.JsonPrimitive).content
        })
        assertEquals(listOf(false,true,false,false), shortcuts.map {
            ((it as kotlinx.serialization.json.JsonObject)["state"] as kotlinx.serialization.json.JsonPrimitive).content.toBoolean()
        })
    }

    @Test fun noClickBridgeMeansNoNativeButtons() {
        val json = """{"id":"f68a5d82181e48edd39eb1824f43c1fa","type":"BT_DEVICE_A2DP","connectState":"CONNECTED","shortcuts":[]}"""
        assertNull(ColorOsNativeWidgetPayload.rewrite(json, listOf(freeClip)))
    }

    @Test fun unrelatedDeviceAndFreeClip2RemainUnchanged() {
        val json = """{"id":"f68a5d82181e48edd39eb1824f43c1fa","type":"BT_DEVICE_A2DP","connectState":"CONNECTED","shortcuts":[]}"""
        assertNull(ColorOsNativeWidgetPayload.rewrite(
            json, listOf(freeClip.copy(name = "HUAWEI FreeClip 2")), soundControlsEnabled = true,
        ))
        assertNull(ColorOsNativeWidgetPayload.rewrite(
            json.replace("f68a5d82181e48edd39eb1824f43c1fa", "00:11:22:33:44:55"),
            listOf(freeClip), soundControlsEnabled = true,
        ))
    }

    @Test fun shortcutIdsAreMappedOnlyToKnownFreeClipPresets() {
        assertEquals(listOf(1,10,3,9),
            ColorOsFreeClipWidgetEqShortcuts.items(null).map { it.presetId })
        assertEquals(3, ColorOsFreeClipWidgetEqShortcuts.presetId("SOUND_EFFECT_BRIGHTNESS"))
        assertNull(ColorOsFreeClipWidgetEqShortcuts.presetId("NOISE_REDUCTION_OPEN"))
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
