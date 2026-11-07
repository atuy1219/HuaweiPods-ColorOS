# Native device model adapter / uploaded ColorOS APK (2026-11-07)

The launcher screenshots show a **generic Bluetooth headset** widget for HUAWEI FreeClip (only a single 100% battery) whereas the OEM **Wireless Earphones** preview cards use a real product image and BOX/LEFT/RIGHT battery slots plus optional noise controls.

The uploaded `base.apk` identifies as `com.oplus.melody` 17.6.6; its DEX contains:

- `com.oplus.melody.card.MelodyCardWidgetProvider#call(String,String,Bundle):Bundle`
- `com.oplus.melody.mydevices.devicecard.MyDeviceProvider#call(String,String,String,Bundle):Bundle` **(previously not hooked)**
- `com.oplus.mydevices.sdk.device.DeviceInfo` with `getMacAddress`, `getDeviceId`, `getBatteryInfoList`, `getBatteryList`, `getIconUrl`, `getMIconUrl`, `isSupportNoiseCanceling`
- `com.oplus.mydevices.sdk.device.BatteryInfo(BatteryType,int,boolean)`

The OEM provider model was missing from the previous widget hook. This update adds its provider call to the existing bundle adapter. It also adds a **guarded** OEM DeviceInfo getter adapter in both MyDevices and Melody processes. It only touches objects whose MAC or verified md5(uppercase MAC) matches an *active* HuaweiPods battery snapshot, then returns BOX/LEFT/RIGHT battery lists in the native SDK type and a device-specific image URI (initial FreeClip only). The image URL uses the module's existing per-MAC, read-only PodImageProvider and honors its standard custom/official/built-in priority. For original FreeClip it explicitly returns `false` for ANC capability.

**Limitations**: This DOES NOT register HuaweiPods as an OPPO/Melody product, implement an OPPO vendor transport, invent ANC controls or guarantee that the launcher uses these DeviceInfo getter methods. Device information may be provided by different code in ColorOS variants. The actual installed `com.heytap.mydevices` APK and launcher APK were not provided. Their widget picker, device registration, rendered bitmap/image URI transport, refresh and action dispatch must be traced on the OnePlus 13 before full native parity can be claimed.

If the widget still shows the generic headset after this build, collect the precise MyDevices package splits and its widget-host package for another audit; do not emulate a vendor product ID without the native protocol evidence.
