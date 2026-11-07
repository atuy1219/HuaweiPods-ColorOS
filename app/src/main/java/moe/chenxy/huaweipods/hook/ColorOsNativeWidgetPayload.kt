package moe.chenxy.huaweipods.hook

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Mutates OEM card data, never OEM widget registration, identities or click actions. */
internal object ColorOsNativeWidgetPayload {
    private val ids = setOf("id", "deviceId", "device_id", "mac", "macAddress",
        "bluetoothAddress", "deviceAddress", "address", "btAddress")
    private val battery = setOf("batteryLeft", "batteryRight", "batteryMain", "batteryMin", "batteryProgress")
    private val mac = Regex("(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}")
    private val hash = Regex("[0-9a-fA-F]{32}")

    fun rewrite(
        raw: String,
        pods: List<ColorOsPodBattery>,
        soundControlsEnabled: Boolean = false,
        selectedEqId: (ColorOsPodBattery) -> Int? = { null },
        onFreeClipCard: (ColorOsPodBattery) -> Unit = {},
    ): String? {
        if (pods.isEmpty() || raw.length > 131_072 ||
            !(raw.trimStart().startsWith("{") || raw.trimStart().startsWith("["))) return null
        return runCatching {
            val before = Json.parseToJsonElement(raw)
            val after = rewriteElement(before, pods, null, 0, soundControlsEnabled, selectedEqId, onFreeClipCard)
            if (after == before) null else after.toString()
        }.getOrNull()
    }

    private fun rewriteElement(
        element: JsonElement,
        pods: List<ColorOsPodBattery>,
        parent: ColorOsPodBattery?,
        depth: Int,
        soundControlsEnabled: Boolean,
        selectedEqId: (ColorOsPodBattery) -> Int?,
        onFreeClipCard: (ColorOsPodBattery) -> Unit,
    ): JsonElement {
        if (depth >= 12) return element
        if (element is JsonArray) {
            val items = element.map { rewriteElement(it, pods, parent, depth + 1, soundControlsEnabled, selectedEqId, onFreeClipCard) }
            return if (items == element) element else JsonArray(items)
        }
        if (element !is JsonObject) return element
        val identity = ids.mapNotNull { key ->
            (element[key] as? JsonPrimitive)?.contentOrNull?.trim()
        }.filter { mac.matches(it) || hash.matches(it) }.distinct()
        val pod = if (identity.isEmpty()) parent else colorOsPodForIdentity(pods, identities = identity)
        if (identity.isNotEmpty() && pod == null) return element
        val fields = element.toMutableMap()
        if (pod != null && element.keys.any { it in battery }) {
            colorOsBatteryTriple(pod)?.let { triple ->
                val levels = mapOf(
                    "batteryLeft" to triple.left,
                    "batteryRight" to triple.right,
                    "batteryMain" to triple.main,
                    "batteryMin" to triple.minimum,
                    "batteryProgress" to (listOfNotNull(triple.left, triple.right).minOrNull()
                        ?: triple.case),
                )
                levels.forEach { (key, level) ->
                    if (level == null) return@forEach
                    when (val old = fields[key]) {
                        is JsonObject -> if ("value" in old)
                            fields[key] = JsonObject(old + ("value" to JsonPrimitive(level)))
                        is JsonPrimitive -> if (!old.isString && old.content.toIntOrNull() != null)
                            fields[key] = JsonPrimitive(level)
                        else -> Unit
                    }
                }
                if ("isSupportMutiBattery" in fields) {
                    fields["isSupportMutiBattery"] = JsonPrimitive(triple.isMulti)
                }
                if (pod.name?.contains("freeclip", ignoreCase = true) == true) {
                    for (key in listOf("deviceType", "headsetType")) {
                        if ((fields[key] as? JsonPrimitive)?.contentOrNull == "HEADSET_TWS") {
                            fields[key] = JsonPrimitive("HEADSET_OWS")
                        }
                    }
                }
            }
        }
        if (pod != null && soundControlsEnabled &&
            ColorOsNativeDeviceInfoPolicy.isOriginalFreeClip(pod.name) &&
            ("id" in element || "deviceId" in element) &&
            "type" in element && ("connectState" in element || "shortcuts" in element)
        ) {
            val rawType = (fields["type"] as? JsonPrimitive)?.contentOrNull
            val isOs17 = rawType?.startsWith("os17_") == true
            val baseType = rawType?.removePrefix("os17_")
            // The installed RPK uses os17_ prefixes for some native cards;
            // preserve the prefix to retain the correct OS17 icon palette.
            if (baseType in setOf(
                    "BT_DEVICE_A2DP", "BT_DEVICE_HEADSET", "BT_HEADSET",
                    "HEADSET_TWS", "HEADSET_OWS", "BT_AIRPODS_A2DP",
                    "headset", "bt-airpods",
                )
            ) {
                fields["type"] = JsonPrimitive(if (isOs17) "os17_HEADSET_OWS" else "HEADSET_OWS")
                fields["shortcuts"] = JsonArray(
                    ColorOsFreeClipWidgetEqShortcuts.items(selectedEqId(pod)).map { shortcut ->
                        JsonObject(mapOf(
                            "id" to JsonPrimitive(shortcut.id),
                            "state" to JsonPrimitive(shortcut.selected),
                        ))
                    },
                )
                onFreeClipCard(pod)
            }
        }
        fields.toMap().forEach { (key, child) ->
            if (child is JsonObject || child is JsonArray) {
                fields[key] = rewriteElement(child, pods, pod, depth + 1, soundControlsEnabled, selectedEqId, onFreeClipCard)
            }
        }
        return if (fields == element) element else JsonObject(fields)
    }
}
