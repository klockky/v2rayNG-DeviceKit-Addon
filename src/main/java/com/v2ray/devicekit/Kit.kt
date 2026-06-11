package com.v2ray.devicekit

import android.content.Context
import java.net.HttpURLConnection
import java.util.Locale

object Kit {

    fun resolveUserAgent(
        config: Config,
        subscriptionUserAgent: String?,
        defaultUserAgent: String,
    ): String {
        if (!subscriptionUserAgent.isNullOrBlank()) return subscriptionUserAgent

        if (!config.enabled) return defaultUserAgent

        val preset = config.userAgentPreset

        val presetUa = when (preset) {
            UserAgentPreset.HAPP -> {
                val v = config.happVersion?.trim().orEmpty().ifEmpty { Defaults.HAPP_VERSION }
                "Happ/$v"
            }
            UserAgentPreset.V2RAYNG -> {
                val v = config.v2rayngVersion?.trim().orEmpty()
                if (v.isEmpty()) "v2rayNG" else "v2rayNG/$v"
            }
            UserAgentPreset.V2RAYTUN -> {
                val p = config.v2raytunPlatform?.trim().orEmpty().ifEmpty { Defaults.V2RAYTUN_PLATFORM }
                "v2raytun/$p"
            }
            UserAgentPreset.FLCLASHX -> {
                val p = config.flclashxPlatform?.trim().orEmpty().ifEmpty { Defaults.FLCLASHX_PLATFORM }
                val v = config.flclashxVersion?.trim().orEmpty()
                if (v.isEmpty()) {
                    "FlClash X Platform/$p"
                } else {
                    "FlClash X/v$v Platform/$p"
                }
            }
            UserAgentPreset.CUSTOM -> config.customUserAgent?.takeIf { it.isNotBlank() }
            else -> null
        }

        return presetUa ?: defaultUserAgent
    }

    /**
     * Builds the full set of request headers (User-agent + optional HWID/device
     * headers) for the given [config], without binding to any HTTP client.
     *
     * Preserves insertion order so callers may apply them deterministically.
     */
    fun buildHeaders(
        context: Context,
        config: Config,
        subscriptionUserAgent: String?,
        defaultUserAgent: String,
    ): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        headers["User-agent"] = resolveUserAgent(config, subscriptionUserAgent, defaultUserAgent)

        if (!config.enabled) return headers

        val hwidToSend = config.customHwid?.trim().takeIf { !it.isNullOrEmpty() }
            ?: DeviceInfo.hardwareId(context)
        if (hwidToSend.isNullOrEmpty()) return headers

        headers["X-HWID"] = hwidToSend

        val osRaw = config.customOs?.trim().orEmpty().ifEmpty { DeviceInfo.osValue() }
        headers["X-Device-OS"] = hwidOsHeaderValue(osRaw)

        val osVer = config.customOsVersion?.trim().orEmpty().ifEmpty { DeviceInfo.osVersion() }
        headers["X-Ver-OS"] = osVer

        val locale = config.customLocale?.trim().orEmpty().ifEmpty { DeviceInfo.locale() }
        if (locale.isNotEmpty()) {
            headers["X-Device-Locale"] = locale
        }

        val model = config.customModel?.trim().orEmpty().ifEmpty { DeviceInfo.model() }
        headers["X-Device-Model"] = model

        return headers
    }

    /**
     * Loads the persisted DeviceKit settings and returns the resulting request
     * headers. Client-agnostic entry point for OkHttp / Ktor / etc. — apply each
     * entry with whatever header setter the caller's HTTP client provides.
     */
    fun headersFromSettings(
        context: Context,
        subscriptionUserAgent: String?,
        defaultUserAgent: String,
        appVersionName: String,
    ): Map<String, String> {
        val config = SettingsStore.loadConfig(appVersionName)
            ?: Config(enabled = false)
        return buildHeaders(context, config, subscriptionUserAgent, defaultUserAgent)
    }

    fun applyToConnection(
        conn: HttpURLConnection,
        context: Context,
        config: Config,
        subscriptionUserAgent: String?,
        defaultUserAgent: String,
    ) {
        buildHeaders(context, config, subscriptionUserAgent, defaultUserAgent).forEach { (name, value) ->
            conn.setRequestProperty(name, value)
        }
    }

    fun applyToConnectionFromSettings(
        conn: HttpURLConnection,
        context: Context,
        subscriptionUserAgent: String?,
        defaultUserAgent: String,
        appVersionName: String,
    ) {
        val config = SettingsStore.loadConfig(appVersionName)
            ?: Config(enabled = false)

        applyToConnection(
            conn = conn,
            context = context,
            config = config,
            subscriptionUserAgent = subscriptionUserAgent,
            defaultUserAgent = defaultUserAgent,
        )
    }

    private fun hwidOsHeaderValue(os: String?): String {
        val v = os?.trim().orEmpty()
        if (v.isEmpty()) return "Android"

        return when (v.lowercase(Locale.US)) {
            "android" -> "Android"
            "ios" -> "iOS"
            "windows" -> "Windows"
            "macos" -> "macOS"
            "linux" -> "Linux"
            else -> v
        }
    }
}
