# Existing ColorOS earbud widgets: guarded compatibility adapter

Uploaded samples (2026-11-07): Melody 17.6.6, AccessoryFramework 17.6.17 and Bluetooth Android 17.
The audited upstream had Melody 17.5.1 and AccessoryFramework 17.6.15.
Verified in uploaded DEX: MelodyCardWidgetProvider.call(String,String,Bundle), the native Seedling and MyDevice providers, SceneService/DialogParams, and the OPLUS Bluetooth battery notification methods. No installed MyDevices or launcher APK was supplied for this OnePlus firmware.

This patch hooks already-existing MyDevices / Melody providers. It adapts JSON fields only if the returned widget content explicitly identifies one connected HUAWEI device by Bluetooth MAC or md5(uppercase MAC). It updates existing batteryLeft, batteryRight, batteryMain, batteryMin, batteryProgress, and multi-battery flag fields; on FreeClip, existing HEADSET_TWS string headset category can become HEADSET_OWS. Unknown fields and native layout/clicks are preserved. Missing methods, opaque Parcelable results and unrelated devices are left untouched. The project does not register a new Android widget.

**Not yet implemented or proven**: native widget picker registration for a HUAWEI device, vendor protected callbacks, forced host refresh, original Enco Clip2 control operations, live launcher behavior, native icons for missing Huawei product metadata. Device picker registration needs a separate audit of MyDevices and the launcher on the actual handset; do not spoof OPPO hardware IDs or send +OBUDS commands to Huawei devices.
