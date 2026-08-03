package com.example.fiddler.subapps.Fidland.phs3.comms

import android.content.Context
import android.provider.Settings
import com.example.fiddler.subapps.Fidland.service.FidlandAccessibilityService

/**
 * Readable (no special permission needed) enabled-state helpers for the three
 * Quick Settings tiles that have no existing CommsSource:
 *   • Mobile Data
 *   • Developer Options
 *   • Accessibility (scoped to this app's own FidlandAccessibilityService)
 *
 * None of these can be *written* by a normal app — they are read-only state
 * feeds used solely to colour the tile icon on/off in QuickSettingsTopic.
 *
 * Why a separate file?
 * These don't fit cleanly into the existing phs3/comms CommsSource pattern
 * (which is designed for continuous StateFlow sources tied to system broadcast
 * receivers). These three values change rarely and are read on-demand at
 * composition time, so a lightweight object with suspend-free getters is
 * intentionally simpler than a full CommsSource.
 */
object QuickSettingsStateReader {

    /**
     * Returns true if the device's mobile data switch is currently on.
     *
     * Reads [Settings.Global.MOBILE_DATA] — value 1 = on, 0 = off.
     * No permission required to read this key.
     *
     * Note: on dual-SIM devices this reflects slot 0 / the default SIM.
     * There is no public API to query per-slot state without
     * READ_PHONE_STATE, which this app already holds for call detection.
     */
    fun isMobileDataEnabled(context: Context): Boolean =
        Settings.Global.getInt(
            context.contentResolver,
            "mobile_data", // Settings.Global.MOBILE_DATA is @hide, not in the public SDK — key name is stable, though
            0
        ) == 1

    /**
     * Returns true if Developer Options is currently unlocked and enabled.
     *
     * Reads [Settings.Global.DEVELOPMENT_SETTINGS_ENABLED].
     * No permission required to read this key.
     *
     * Important nuance: this can return true even if the toggle inside
     * Developer Options is disabled — it reflects whether the section is
     * *unlocked*, not an individual option within it. That's the correct
     * semantic for this tile (we're linking to the Dev Options screen, not
     * toggling a specific sub-option).
     */
    fun isDevOptionsEnabled(context: Context): Boolean =
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) == 1

    /**
     * Returns true if *this app's own* [FidlandAccessibilityService] is
     * currently active in the system's enabled accessibility services list.
     *
     * Reads [Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES] — a colon-
     * separated list of "package/ServiceClass" strings. No permission required
     * to read it.
     *
     * Scoped to Fidland's own service (not "any accessibility service") so the
     * tile clearly communicates *this app's* accessibility permission, which
     * aligns with what [PermissionsActivity] already asks the user to grant.
     */
    fun isFidlandAccessibilityEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

// Service component string as registered in AndroidManifest.xml:
// "com.example.fiddler/com.example.fiddler.subapps.Fidland.Services.FidlandAccessibilityService"
        val targetComponent =
            "${context.packageName}/${FidlandAccessibilityService::class.java.name}"

        return enabledServices
            .split(":")
            .any { it.equals(targetComponent, ignoreCase = true) }
    }
}