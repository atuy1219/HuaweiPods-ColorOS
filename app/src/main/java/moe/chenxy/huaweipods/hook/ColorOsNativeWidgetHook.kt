package moe.chenxy.huaweipods.hook

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.util.concurrent.ConcurrentHashMap
import moe.chenxy.huaweipods.pods.FreeClipControlClient
import moe.chenxy.huaweipods.pods.FreeClipControlKey
import java.lang.reflect.Method

/**
 * Adapts native ColorOS/MyDevices and Melody widget data in place.
 * Non-matching cards and unknown host versions keep their stock behavior.
 * This does not register a HUAWEI device with the launcher/widget picker.
 */
internal object ColorOsNativeWidgetHook : HookContext() {
    private const val TAG = "HuaweiPods-NativeWidget"
    private val hostMethods = mapOf(
        "com.heytap.mydevices" to mapOf(
            "com.oplus.mydevices.quickapp.provider.DeviceQuickAppCardWidgetProvider" to setOf("handleGetCardData"),
            "com.oplus.mydevices.remotecards.provider.DeviceCardWidgetProvider" to setOf("processCall"),
            "com.oplus.mydevices.remotecards.provider.DeviceLiteCardWidgetProvider" to setOf("processCall"),
        ),
        "com.oplus.melody" to mapOf(
            "com.oplus.melody.card.MelodyCardWidgetProvider" to setOf("call"),
            // Confirmed in the supplied Melody 17.6.6 classes2.dex.
            "com.oplus.melody.mydevices.devicecard.MyDeviceProvider" to setOf("call"),
        ),
    )
    private var batterySource: ColorOsBatterySource? = null
    private val main = Handler(Looper.getMainLooper())
    private val clients = mutableMapOf<String, FreeClipControlClient>()
    private val confirmedEq = ConcurrentHashMap<String, Int>()
    private val cardInstances = ConcurrentHashMap<String, MutableSet<String>>()
    @Volatile private var clickHookReady = false
    private const val QUICKAPP_AUTHORITY =
        "content://com.oplus.mydevices.authority.quickappcards/com.oplus.mydevices.quickapp.provider.DeviceQuickAppCardWidgetProvider"
    private val validCardId = Regex("[A-Za-z0-9_&.-]{1,128}")

    override fun onHook() {
        for ((name, methods) in hostMethods[packageName].orEmpty()) {
            runCatching {
                val matching = findClass(name).declaredMethods.filter {
                    it.name in methods && it.returnType != Void.TYPE
                }
                for (method in matching) {
                    method.isAccessible = true
                    hookCardResult(method)
                }
                if (matching.isEmpty()) Log.i(TAG, "No matching native card method: $name")
            }.onFailure { Log.w(TAG, "Native card provider unavailable: $name", it) }
        }
        if (packageName == "com.heytap.mydevices") installEqClickHook()
        runCatching {
            hookAfter(Application::class.java.getDeclaredMethod("onCreate")) {
                (instance as? Context)?.let(::ensureBatterySource)
            }
            currentApplicationOrNull()?.let(::ensureBatterySource)
        }.onFailure { Log.w(TAG, "Widget battery source unavailable", it) }
    }

    private fun installEqClickHook() {
        runCatching {
            val method = findMethod(
                "com.oplus.mydevices.quickapp.provider.DeviceQuickAppCardWidgetProvider",
                "handleClickEvent", String::class.java, Bundle::class.java,
            )
            hookBefore(method) {
                val extras = args.getOrNull(1) as? Bundle ?: return@hookBefore
                val shortcutId = extras.getString("shortcutId")
                val preset = ColorOsFreeClipWidgetEqShortcuts.presetId(shortcutId)
                    ?: return@hookBefore
                if (!extras.containsKey("valueInt") || extras.getInt("valueInt") != 1) return@hookBefore
                val deviceId = extras.getString("deviceId") ?: return@hookBefore
                val pod = colorOsPodForIdentity(
                    ColorOsPodBatteryStore.snapshot(), identities = listOf(deviceId),
                ) ?: return@hookBefore
                if (!ColorOsNativeDeviceInfoPolicy.isOriginalFreeClip(pod.name)) return@hookBefore
                val host = currentApplicationOrNull() ?: return@hookBefore
                if (!isTrustedQuickAppCaller(host)) return@hookBefore
                val queued = main.post {
                    runCatching {
                        getEqClient(host, pod.address)
                            .request(FreeClipControlKey.EQUALIZER, preset.toString()) { ok ->
                                if (!ok) Log.w(TAG, "FreeClip EQ not confirmed by the earbuds")
                            }
                    }.onFailure { Log.w(TAG, "FreeClip EQ dispatch failed", it) }
                }
                if (queued) {
                    // Accepted for processing, not a device-confirmed success.
                    result = Bundle().apply { putInt("code", 0) }
                }
            }
            clickHookReady = true
            Log.i(TAG, "Native FreeClip shortcut click bridge installed")
        }.onFailure { Log.w(TAG, "No compatible native click handler; EQ shortcuts disabled", it) }
    }

    private fun isTrustedQuickAppCaller(context: Context): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid() || uid == Process.SYSTEM_UID) return true
        val packages = context.packageManager.getPackagesForUid(uid).orEmpty()
        return packages.any {
            it in setOf(
                "com.oplus.launcher", "com.coloros.launcher",
                "com.android.launcher3", "com.nearme.instant.platform",
                "com.heytap.mydevices", "com.oplus.mydevices",
            )
        }
    }

    private fun getEqClient(context: Context, address: String): FreeClipControlClient {
        // Only called on the process main thread.
        val normalized = ColorOsPodBatteryStore.key(address)
        clients[normalized]?.let { return it }
        lateinit var client: FreeClipControlClient
        client = FreeClipControlClient(context, normalized) {
            val selected = client.session.values[FreeClipControlKey.EQUALIZER]
                ?.toIntOrNull()?.takeIf { ColorOsFreeClipWidgetEqShortcuts.isValidPreset(it) }
            if (selected == null) confirmedEq.remove(normalized)
            else confirmedEq[normalized] = selected
            notifyCardInstances(context, normalized)
        }
        clients[normalized] = client
        client.start()
        return client
    }

    private fun observeCard(context: Context, address: String, instanceId: String?) {
        if (instanceId != null && validCardId.matches(instanceId)) {
            cardInstances.computeIfAbsent(ColorOsPodBatteryStore.key(address)) {
                ConcurrentHashMap.newKeySet<String>()
            }.add(instanceId)
        }
        main.post {
            runCatching { getEqClient(context, address) }
                .onFailure { Log.w(TAG, "FreeClip EQ state reader unavailable", it) }
        }
    }

    private fun notifyCardInstances(context: Context, address: String) {
        cardInstances[address]?.forEach { instance ->
            runCatching {
                context.contentResolver.notifyChange(
                    Uri.parse("$QUICKAPP_AUTHORITY/$instance"), null,
                )
            }
        }
    }

    private fun hookCardResult(method: Method) {
        hookAfter(method) {
            runCatching {
                val pods = ColorOsPodBatteryStore.snapshot()
                if (pods.isEmpty()) return@runCatching
                val context = currentApplicationOrNull()
                val instanceId = args.firstOrNull() as? String
                val onFreeClip: (ColorOsPodBattery) -> Unit = { pod ->
                    if (context != null && packageName == "com.heytap.mydevices") {
                        observeCard(context, pod.address, instanceId)
                    }
                }
                val selected: (ColorOsPodBattery) -> Int? = { pod ->
                    confirmedEq[ColorOsPodBatteryStore.key(pod.address)]
                }
                when (val original = result) {
                    is String ->
                        ColorOsNativeWidgetPayload.rewrite(
                            original, pods, clickHookReady, selected, onFreeClip,
                        )?.let { result = it }
                    is Bundle -> patchBundle(original, pods, clickHookReady, selected, onFreeClip)
                    else -> Unit
                }
            }.onFailure { Log.w(TAG, "Original vendor widget preserved", it) }
        }
        Log.i(TAG, "Installed $method")
    }

    private fun patchBundle(
        bundle: Bundle, pods: List<ColorOsPodBattery>,
        enableShortcuts: Boolean,
        selected: (ColorOsPodBattery) -> Int?,
        onFreeClip: (ColorOsPodBattery) -> Unit,
    ) {
        // Process only plain JSON strings; do not deserialize proprietary Parcelables.
        for (key in runCatching { bundle.keySet().toList() }.getOrDefault(emptyList())) {
            val value = runCatching { bundle.getString(key) }.getOrNull() ?: continue
            ColorOsNativeWidgetPayload.rewrite(value, pods, enableShortcuts, selected, onFreeClip)
                ?.let { bundle.putString(key, it) }
        }
    }

    @Synchronized
    private fun ensureBatterySource(context: Context) {
        if (batterySource != null) return
        batterySource = ColorOsBatterySource.forContext(context.applicationContext, TAG) {
            // The host owns its update cadence and card lifecycle.
        }.also { it.start() }
    }

    override fun onClose() {
        runCatching {
            runOnMainThreadBlocking {
                clients.values.toList().forEach { client -> runCatching { client.close() } }
                clients.clear()
            }
        }.onFailure { Log.w(TAG, "Unable to close native EQ clients", it) }
        synchronized(this) {
            runCatching { batterySource?.close() }
            batterySource = null
        }
        main.removeCallbacksAndMessages(null)
        confirmedEq.clear()
        cardInstances.clear()
        clickHookReady = false
    }
}
