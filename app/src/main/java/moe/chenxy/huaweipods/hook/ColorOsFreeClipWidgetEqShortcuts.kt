package moe.chenxy.huaweipods.hook

/**
 * Shortcut IDs from MyDevices' SingleDeviceCard4x2 RPK. The device command
 * numbers are FreeClip 1's verified Huawei equalizer presets.
 *
 * Note: native widget labels are owned by MyDevices, so the OEM text for
 * treble/clear voice is only an approximation.
 */
internal object ColorOsFreeClipWidgetEqShortcuts {
    data class Shortcut(val id: String, val presetId: Int, val selected: Boolean)

    private val ids = linkedMapOf(
        "SOUND_EFFECT_BALANCE" to 0x01,
        "SOUND_EFFECT_LOW_SOUND_ENHANCE" to 0x0A,
        "SOUND_EFFECT_BRIGHTNESS" to 0x03,
        "SOUND_EFFECT_PURE_VOICE" to 0x09,
    )

    fun items(selected: Int?): List<Shortcut> =
        ids.map { (id, preset) -> Shortcut(id, preset, selected == preset) }

    fun presetId(shortcutId: String?): Int? = ids[shortcutId]
    fun isValidPreset(id: Int?): Boolean = id != null && id in ids.values
}
