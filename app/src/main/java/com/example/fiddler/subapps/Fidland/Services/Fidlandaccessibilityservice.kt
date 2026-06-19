package com.example.fiddler.subapps.Fidland.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Minimal, intentionally no-op AccessibilityService.
 *
 * Its only purpose is to give this app's process an active accessibility
 * connection, which is what the system checks before honoring
 * WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY windows — the only
 * overlay window type that is allowed to draw above SystemUI's status bar
 * layer (clock/battery/signal icons), used by OverlayManagerCompose for the
 * Fidland pill.
 *
 * IMPORTANT: TYPE_ACCESSIBILITY_OVERLAY is only honored when addView() is
 * called through a WindowManager obtained from THIS service's own Context —
 * the accessibility window token lives on this Context, not on the app
 * process generally and not on any other Service/Application context. A
 * plain Service calling getSystemService(WINDOW_SERVICE) on itself will get
 * a token-less WindowManager and addView() will throw
 * WindowManager.BadTokenException for this window type, even while this
 * service is enabled and connected.
 *
 * To let other components (FidlandService) use the right WindowManager, we
 * expose a static reference to the live, connected instance — set in
 * onServiceConnected() and cleared in onDestroy(). Callers must null-check:
 * the instance is null until the user has enabled the service AND the
 * system has finished binding it, and it goes null again if the user
 * disables it or the system kills/rebinds it.
 *
 * We deliberately do not read or act on any AccessibilityEvent content —
 * this service exists purely for the window-type grant, not for actual
 * accessibility features. Keep accessibility_service_config.xml's
 * canRetrieveWindowContent and event types as minimal as possible to reflect
 * that and to ease store-review/privacy concerns.
 *
 * The user must enable this manually once under
 * Settings > Accessibility > [Fiddler] > Fidland Overlay.
 * Without it enabled, the system silently denies the elevated z-order and
 * the pill falls back to rendering under the status bar icons.
 */
class FidlandAccessibilityService : AccessibilityService() {

    companion object {
        /**
         * Live instance, non-null only while the service is connected.
         * Use this (not applicationContext) as the Context source for any
         * WindowManager that needs to add a TYPE_ACCESSIBILITY_OVERLAY view.
         */
        var instance: FidlandAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty — this service is not used for event handling.
    }

    override fun onInterrupt() {
        // Intentionally empty.
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }
}