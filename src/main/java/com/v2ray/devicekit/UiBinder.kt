package com.v2ray.devicekit

import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlin.random.Random

object UiBinder {

    private data class Prefs(
        val hwidEnabled: CheckBoxPreference?,
        val hwidVal: EditTextPreference?,
        val hwidRandomize: Preference?,
        val hwidOs: ListPreference?,
        val hwidOsVer: EditTextPreference?,
        val hwidModel: EditTextPreference?,
        val hwidLocale: EditTextPreference?,
        val uaPreset: ListPreference?,
        val uaCustom: EditTextPreference?,
        val uaHappVersion: EditTextPreference?,
        val uaV2rayngVersion: EditTextPreference?,
        val uaV2raytunPlatform: ListPreference?,
        val uaFlclashxVersion: EditTextPreference?,
        val uaFlclashxPlatform: ListPreference?,
    )

    fun bind(fragment: PreferenceFragmentCompat) {
        val prefs = prefs(fragment)
        val context = fragment.requireContext()

        prefs.hwidEnabled?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as? Boolean ?: false
            updateVisibility(prefs, enabled, prefs.uaPreset?.value)
            if (enabled) {
                applyDefaults(context, prefs)
            }
            true
        }

        prefs.hwidRandomize?.setOnPreferenceClickListener {
            val newHwid = ByteArray(8).also { Random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
            prefs.hwidVal?.text = newHwid
            if (prefs.hwidVal?.summaryProvider == null) {
                prefs.hwidVal?.summary = newHwid
            }
            true
        }

        prefs.uaPreset?.setOnPreferenceChangeListener { pref, newValue ->
            val lp = pref as ListPreference
            val valueStr = newValue?.toString().orEmpty()
            updateUaPresetSummary(lp, valueStr)
            updateVisibility(prefs, prefs.hwidEnabled?.isChecked == true, valueStr)
            if (prefs.hwidEnabled?.isChecked == true) {
                applyDefaults(context, prefs)
            }
            true
        }

        updateUaPresetSummary(prefs.uaPreset, prefs.uaPreset?.value)
        updateVisibility(prefs, prefs.hwidEnabled?.isChecked == true, prefs.uaPreset?.value)
        if (prefs.hwidEnabled?.isChecked == true) {
            applyDefaults(context, prefs)
        }
    }

    private fun prefs(fragment: PreferenceFragmentCompat): Prefs {
        return Prefs(
            hwidEnabled = fragment.findPreference(PrefKeys.HWID_ENABLED),
            hwidVal = fragment.findPreference(PrefKeys.HWID_VAL),
            hwidRandomize = fragment.findPreference(PrefKeys.HWID_RANDOMIZE),
            hwidOs = fragment.findPreference(PrefKeys.HWID_OS),
            hwidOsVer = fragment.findPreference(PrefKeys.HWID_OS_VER),
            hwidModel = fragment.findPreference(PrefKeys.HWID_MODEL),
            hwidLocale = fragment.findPreference(PrefKeys.HWID_LOCALE),
            uaPreset = fragment.findPreference(PrefKeys.UA_PRESET),
            uaCustom = fragment.findPreference(PrefKeys.UA_CUSTOM),
            uaHappVersion = fragment.findPreference(PrefKeys.UA_HAPP_VERSION),
            uaV2rayngVersion = fragment.findPreference(PrefKeys.UA_V2RAYNG_VERSION),
            uaV2raytunPlatform = fragment.findPreference(PrefKeys.UA_V2RAYTUN_PLATFORM),
            uaFlclashxVersion = fragment.findPreference(PrefKeys.UA_FLCLASHX_VERSION),
            uaFlclashxPlatform = fragment.findPreference(PrefKeys.UA_FLCLASHX_PLATFORM),
        )
    }

    private fun updateUaPresetSummary(pref: ListPreference?, value: String?) {
        if (pref == null) return
        if (pref.summaryProvider != null) return
        val valueStr = value?.toString().orEmpty()
        val idx = pref.findIndexOfValue(valueStr)
        pref.summary = if (idx >= 0) pref.entries[idx] else valueStr
    }

    private fun updateVisibility(prefs: Prefs, enabled: Boolean, preset: String?) {
        val showGroup = enabled

        prefs.hwidVal?.isVisible = showGroup
        prefs.hwidRandomize?.isVisible = showGroup
        prefs.hwidOs?.isVisible = showGroup
        prefs.hwidOsVer?.isVisible = showGroup
        prefs.hwidModel?.isVisible = showGroup
        prefs.hwidLocale?.isVisible = showGroup
        prefs.uaPreset?.isVisible = showGroup

        val uaPreset = UserAgentPreset.fromKey(preset)
        val showHapp = showGroup && uaPreset.isHapp
        val showV2rayng = showGroup && uaPreset == UserAgentPreset.V2RAYNG
        val showV2raytun = showGroup && uaPreset == UserAgentPreset.V2RAYTUN
        val showFlclashx = showGroup && uaPreset == UserAgentPreset.FLCLASHX
        val showCustom = showGroup && uaPreset == UserAgentPreset.CUSTOM

        prefs.uaHappVersion?.isVisible = showHapp
        prefs.uaV2rayngVersion?.isVisible = showV2rayng
        prefs.uaV2raytunPlatform?.isVisible = showV2raytun
        prefs.uaFlclashxPlatform?.isVisible = showFlclashx
        prefs.uaFlclashxVersion?.isVisible = showFlclashx
        prefs.uaCustom?.isVisible = showCustom
    }

    private fun applyDefaults(context: android.content.Context, prefs: Prefs) {
        setTextIfBlank(prefs.hwidVal, DeviceInfo.hardwareId(context))
        setListIfBlank(prefs.hwidOs, DeviceInfo.osValue())
        setTextIfBlank(prefs.hwidOsVer, DeviceInfo.osVersion())
        setTextIfBlank(prefs.hwidModel, DeviceInfo.model())
        setTextIfBlank(prefs.hwidLocale, DeviceInfo.locale())

        setTextIfBlank(prefs.uaHappVersion, Defaults.HAPP_VERSION)
        setTextIfBlank(prefs.uaV2rayngVersion, DeviceInfo.appVersionName(context))
        setListIfBlank(prefs.uaV2raytunPlatform, Defaults.V2RAYTUN_PLATFORM)
        setTextIfBlank(prefs.uaFlclashxVersion, Defaults.FLCLASHX_VERSION)
        setListIfBlank(prefs.uaFlclashxPlatform, Defaults.FLCLASHX_PLATFORM)
    }

    private fun setTextIfBlank(pref: EditTextPreference?, value: String) {
        if (pref == null || value.isBlank()) return
        if (pref.text.isNullOrBlank()) {
            pref.text = value
            if (pref.summaryProvider == null) {
                pref.summary = value
            }
        }
    }

    private fun setListIfBlank(pref: ListPreference?, value: String) {
        if (pref == null || value.isBlank()) return
        if (pref.value.isNullOrBlank()) {
            pref.value = value
        }
        val idx = pref.findIndexOfValue(pref.value)
        if (pref.summaryProvider == null) {
            pref.summary = if (idx >= 0) pref.entries[idx] else pref.value
        }
    }
}
