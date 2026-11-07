# Native FreeClip 1 sound effects in the ColorOS MyDevices widget

Analyzed from the attached MyDevices APK's embedded SingleDeviceCard4x2 RPK.

The Quick App renders its bottom shortcut row from `deviceData.shortcuts` and uses
`deviceData.type` to select the component. On an explicitly identified original
FreeClip card only, this patch changes the generic `BT_DEVICE_A2DP` to
`HEADSET_OWS`, and replaces ANC actions with four native `SOUND_EFFECT_*`
shortcuts. It changes no other earphones.

| Huawei EQ setting | Huawei preset ID | OEM shortcut ID | Existing OEM label (English) |
|---|---|---|---|
| Default | 1 | SOUND_EFFECT_BALANCE | Balanced |
| Bass boost | 10 | SOUND_EFFECT_LOW_SOUND_ENHANCE | Bass boost |
| Treble boost | 3 | SOUND_EFFECT_BRIGHTNESS | Clear |
| Clear voice | 9 | SOUND_EFFECT_PURE_VOICE | Original sound |

The native Quick App translates labels by ID. The native labels for treble and
clear voice are *not exact*; overriding them requires a separate translation
patch to the OEM Quick App template. The UI never indicates an unconfirmed
preset as selected.

The verified MyDevices 17.25 `DeviceQuickAppCardWidgetProvider#handleClickEvent`
receives `deviceId`, `shortcutId` and `valueInt`. Only verified FreeClip 1
MAC/md5 identities and four known effect shortcut IDs are intercepted. No OPPO
proprietary control command is sent; a trusted system caller is required. The
existing FreeClipControlClient dispatches commands through the Bluetooth
process and uses device readback to confirm the selected preset.

When the OEM class or handler is absent, the new shortcuts are not injected.
This does NOT register a new widget or guarantee a card can be chosen in the
launcher. Firmware-specific MyDevices and widget binding still need testing.
