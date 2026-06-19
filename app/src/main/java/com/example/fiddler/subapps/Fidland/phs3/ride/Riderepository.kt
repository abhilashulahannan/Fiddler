package com.example.fiddler.subapps.Fidland.phs3.ride

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.example.fiddler.subapps.Fidland.phs3.navigation.NavigationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * RideRepository
 *
 * Parses Ola/Uber/Rapido ride notifications and converts them into a
 * [RideSnapshot] that [RidePhs3Handler] consumes. No manual input — pickup
 * comes from GPS at detection time, destination comes from the notification
 * text (scraped + geocoded) or is fused from an active Google Maps nav
 * session. If neither resolves, progress simply stays unknown — the pill
 * never asks the user to type anything in.
 *
 * ── How to feed it ───────────────────────────────────────────────────────
 * In NotificationListenerService:
 *
 *   override fun onNotificationPosted(sbn: StatusBarNotification) {
 *       if (RideApp.fromPackage(sbn.packageName) != null) {
 *           RideRepository.onNotification(sbn)
 *       }
 *   }
 *
 *   override fun onNotificationRemoved(sbn: StatusBarNotification) {
 *       if (RideApp.fromPackage(sbn.packageName) != null) {
 *           RideRepository.onNotificationRemoved(sbn)
 *       }
 *   }
 *
 * ── Location flow ────────────────────────────────────────────────────────
 * Call [onLocationUpdate] from a FusedLocationProviderClient callback. The
 * repository decides polling cadence externally (RidePhs3Trigger); this
 * class is a pure state holder + computation engine, no location requests
 * of its own — keeps Android location APIs out of this module so it stays
 * testable.
 */
object RideRepository {

    private val _flow = MutableStateFlow(EmptyRideSnapshot)
    val flow: StateFlow<RideSnapshot> = _flow

    private val client = OkHttpClient()
    private var resolveJob: Job? = null
    private var currentScope: CoroutineScope? = null

    // ── Public API — notifications ────────────────────────────────────────

    /** Call once at service start so OSRM/geocoding coroutines have a scope. */
    fun attachScope(scope: CoroutineScope) {
        currentScope = scope
    }

    fun onNotification(sbn: StatusBarNotification) {
        val app = RideApp.fromPackage(sbn.packageName) ?: return
        val extras = sbn.notification.extras ?: return

        val title   = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text    = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val combined = listOf(title, text, subText, bigText).joinToString(" \n ")

        val otp = scrapeOtp(combined)
        val phase = inferPhase(combined, otp)
        val current = _flow.value

        val pickup = if (current.phase == RidePhase.IDLE || current.app != app) {
            // Fresh ride detected — capture pickup at this instant.
            // Real pickup coordinate is supplied by the trigger via onLocationUpdate
            // immediately after this; until then pickupLatLon is null.
            null
        } else current.pickupLatLon

        val scrapedDest = scrapeDestinationName(combined)
        val destinationName = scrapedDest.ifBlank { current.destinationName }

        _flow.value = current.copy(
            phase           = phase,
            app             = app,
            otp             = otp.ifBlank { current.otp },
            driverEtaText   = if (phase == RidePhase.PRE_RIDE) scrapeEta(combined) else current.driverEtaText,
            driverName      = scrapeDriverName(combined).ifBlank { current.driverName },
            vehicleInfo     = scrapeVehicleInfo(combined).ifBlank { current.vehicleInfo },
            driverRating    = scrapeRating(combined).ifBlank { current.driverRating },
            destEtaText     = if (phase == RidePhase.IN_RIDE) scrapeEta(combined) else current.destEtaText,
            destinationName = destinationName,
            pickupLatLon    = pickup,
        )

        maybeResolveDestination(destinationName)
        maybeFetchRoute()
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        val app = RideApp.fromPackage(sbn.packageName) ?: return
        val current = _flow.value
        if (current.app != app) return

        _flow.value = current.copy(phase = RidePhase.ENDED)
        // Repository fully resets shortly after; trigger handles deregistration timing.
    }

    fun reset() {
        resolveJob?.cancel()
        _flow.value = EmptyRideSnapshot
    }

    // ── Public API — location ───────────────────────────────────────────────

    /**
     * Feed current GPS position. Called every 10s in PRE_RIDE, every 15s in
     * IN_RIDE (cadence controlled by RidePhs3Trigger).
     */
    fun onLocationUpdate(current: LatLon) {
        val snap = _flow.value
        if (!snap.isActive) return

        // Capture pickup once, at the first location update after a ride is detected.
        if (snap.pickupLatLon == null && snap.phase == RidePhase.PRE_RIDE) {
            _flow.value = snap.copy(pickupLatLon = current)
            maybeFetchRoute()
            return
        }

        if (snap.phase != RidePhase.IN_RIDE) return
        val dest = snap.destinationLatLon ?: return
        if (snap.totalRouteMeters <= 0) return

        val remaining = haversineMeters(current, dest)
        val fraction = (1f - (remaining / snap.totalRouteMeters).toFloat()).coerceIn(0f, 1f)
        _flow.value = snap.copy(progressFraction = fraction)
    }

    // ── Navigation fusion ──────────────────────────────────────────────────

    /**
     * Borrow the destination from an active Google Maps navigation session
     * when the ride notification's own destination text didn't resolve.
     * Maps notifications don't expose a destination place name directly, but
     * the arrival time / step list combined with a manual one-time Nominatim
     * lookup of the nav target (if surfaced) can fill the gap. Wired as a
     * best-effort fallback only — never blocks PRE_RIDE/IN_RIDE display.
     */
    fun fuseFromNavigationIfNeeded() {
        val snap = _flow.value
        if (!snap.isActive || snap.destinationLatLon != null) return

        val nav = NavigationRepository.flow.value
        if (!nav.isActive) return

        // NavigationSnapshot doesn't carry a resolved LatLon for the final
        // destination today — this hook exists so that once Navigationstate.kt
        // is extended with a destination coordinate, fusion is a one-line wire:
        //   _flow.value = snap.copy(destinationLatLon = nav.destinationLatLon)
        //   maybeFetchRoute()
    }

    // ── Geocoding (Nominatim) ─────────────────────────────────────────────

    private fun maybeResolveDestination(name: String) {
        val scope = currentScope ?: return
        if (name.isBlank()) return
        val snap = _flow.value
        if (snap.destinationLatLon != null) return // already resolved

        resolveJob?.cancel()
        resolveJob = scope.launch {
            val latLon = geocode(name)
            if (latLon != null) {
                _flow.value = _flow.value.copy(destinationLatLon = latLon)
                maybeFetchRoute()
            }
        }
    }

    private fun geocode(place: String): LatLon? {
        return try {
            val encoded = URLEncoder.encode(place, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "FidlandRidePhs3/1.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val arr = org.json.JSONArray(body)
                if (arr.length() == 0) return null
                val obj = arr.getJSONObject(0)
                LatLon(obj.getString("lat").toDouble(), obj.getString("lon").toDouble())
            }
        } catch (_: IOException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    // ── Routing (OSRM) ─────────────────────────────────────────────────────

    private fun maybeFetchRoute() {
        val scope = currentScope ?: return
        val snap = _flow.value
        val pickup = snap.pickupLatLon ?: return
        val dest = snap.destinationLatLon ?: return
        if (snap.totalRouteMeters > 0) return // already fetched for this ride

        scope.launch {
            val meters = fetchOsrmRouteMeters(pickup, dest)
            if (meters != null && meters > 0) {
                _flow.value = _flow.value.copy(totalRouteMeters = meters)
            }
        }
    }

    private fun fetchOsrmRouteMeters(pickup: LatLon, dest: LatLon): Int? {
        return try {
            val url = "https://router.project-osrm.org/route/v1/driving/" +
                    "${pickup.lon},${pickup.lat};${dest.lon},${dest.lat}?overview=false"
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val routes = json.getJSONArray("routes")
                if (routes.length() == 0) return null
                routes.getJSONObject(0).getDouble("distance").toInt()
            }
        } catch (_: IOException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    // ── Notification text scraping ────────────────────────────────────────
    // Best-effort regex parsing. Ola/Uber/Rapido wording drifts across app
    // versions and locales — these patterns cover the common English forms.
    // Falls back to blank (never crashes, never blocks the rest of the snapshot).

    private fun scrapeOtp(text: String): String =
        Regex("""\bOTP\D{0,5}(\d{4,6})\b""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1) ?: ""

    private fun scrapeEta(text: String): String =
        Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE)
            .find(text)?.let { "${it.groupValues[1]} min" } ?: ""

    private fun scrapeRating(text: String): String =
        Regex("""(\d\.\d)\s*★|\brating\D{0,5}(\d\.\d)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.firstOrNull { it.isNotBlank() && it != text } ?: ""

    private fun scrapeDriverName(text: String): String =
        Regex("""(?:driver|with)\s+([A-Z][a-zA-Z]+)\b""")
            .find(text)?.groupValues?.get(1) ?: ""

    private fun scrapeVehicleInfo(text: String): String =
        Regex("""\b([A-Z]{2}\s?\d{2}\s?[A-Z]{1,2}\s?\d{3,4})\b""")
            .find(text)?.value ?: ""

    /**
     * Scrapes a destination place name from common phrasings:
     *   "Your Uber to Koramangala is confirmed"
     *   "Driver heading to MG Road"
     *   "Trip to Indiranagar started"
     */
    private fun scrapeDestinationName(text: String): String {
        val patterns = listOf(
            Regex("""\bto\s+([A-Za-z0-9 ,.'-]{3,40}?)\s+(?:is|has|started|confirmed|now)\b""", RegexOption.IGNORE_CASE),
            Regex("""\bheading to\s+([A-Za-z0-9 ,.'-]{3,40})""", RegexOption.IGNORE_CASE),
            Regex("""\btrip to\s+([A-Za-z0-9 ,.'-]{3,40})""", RegexOption.IGNORE_CASE),
        )
        for (p in patterns) {
            val m = p.find(text)
            if (m != null) return m.groupValues[1].trim()
        }
        return ""
    }

    /**
     * PRE_RIDE  → OTP present, no "arrived"/"started" language yet.
     * IN_RIDE   → OTP was consumed / trip start language detected.
     * Stays in whatever phase was last known if neither pattern matches,
     * so a notification update that doesn't repeat the OTP doesn't bounce
     * the state backward.
     */
    private fun inferPhase(text: String, otp: String): RidePhase {
        val lower = text.lowercase()
        return when {
            "trip started" in lower || "ride started" in lower || "otp verified" in lower -> RidePhase.IN_RIDE
            "arrived" in lower && otp.isBlank() -> RidePhase.IN_RIDE
            otp.isNotBlank() -> RidePhase.PRE_RIDE
            else -> _flow.value.phase.takeIf { it != RidePhase.IDLE } ?: RidePhase.PRE_RIDE
        }
    }
}