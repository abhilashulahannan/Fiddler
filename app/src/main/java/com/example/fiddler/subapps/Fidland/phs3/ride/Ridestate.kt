package com.example.fiddler.subapps.Fidland.phs3.ride

// ─────────────────────────────────────────────────────────────────────────────
//  App source
// ─────────────────────────────────────────────────────────────────────────────

enum class RideApp(
    val packageName: String,
    val displayName: String,
    /** Unicode emoji used as a fallback icon — branded icon preferred in Indicator. */
    val icon: String,
    /** Deep-link to open the app directly. */
    val launchAction: String,
) {
    UBER(
        packageName  = "com.ubercab",
        displayName  = "Uber",
        icon         = "🖤",
        launchAction = "uber://",
    ),
    OLA(
        packageName  = "com.olacabs.customer",
        displayName  = "Ola",
        icon         = "🟢",
        launchAction = "ola://",
    ),
    RAPIDO(
        packageName  = "com.rapido.passenger",
        displayName  = "Rapido",
        icon         = "🟡",
        launchAction = "rapido://",
    );

    companion object {
        private val byPackage = entries.associateBy { it.packageName }
        fun fromPackage(pkg: String): RideApp? = byPackage[pkg]
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Ride phase
// ─────────────────────────────────────────────────────────────────────────────

enum class RidePhase {
    /** No active ride — module not registered with Phs3Manager. */
    IDLE,

    /**
     * Ride booked, driver assigned, driver not yet arrived.
     * Pill (State 3) shows: OTP + driver ETA to pickup.
     */
    PRE_RIDE,

    /**
     * OTP consumed / ride started.
     * Pill (State 3) shows: ETA to destination + progress circle (only if
     * destination has been resolved — see [RideSnapshot.hasProgress]).
     */
    IN_RIDE,

    /**
     * Ride completed. Module shows briefly (ENDED) then RideRepository
     * resets and the trigger deregisters it.
     */
    ENDED,
}

// ─────────────────────────────────────────────────────────────────────────────
//  GPS coordinate
// ─────────────────────────────────────────────────────────────────────────────

data class LatLon(val lat: Double, val lon: Double)

// ─────────────────────────────────────────────────────────────────────────────
//  Full ride snapshot — emitted by RideRepository, consumed by RidePhs3Handler
// ─────────────────────────────────────────────────────────────────────────────

data class RideSnapshot(
    val phase: RidePhase,
    val app: RideApp?,

    // ── Pre-ride ─────────────────────────────────────────────────────────────
    /** OTP shown to driver at pickup, e.g. "4821". Blank if not yet known. */
    val otp: String,
    /** Human-readable driver ETA string, e.g. "3 min". Shown pre-ride. */
    val driverEtaText: String,

    // ── Driver info ──────────────────────────────────────────────────────────
    val driverName: String,
    val vehicleInfo: String,     // e.g. "Swift Dzire · KA 05 AB 1234"
    val driverRating: String,    // e.g. "4.8 ★"

    // ── In-ride ──────────────────────────────────────────────────────────────
    /** Human-readable ETA to destination, e.g. "12 min". */
    val destEtaText: String,
    /** Destination place name scraped from the notification, e.g. "Koramangala". */
    val destinationName: String,
    /** Coordinates resolved from [destinationName] via Nominatim geocoding. Null until resolved. */
    val destinationLatLon: LatLon?,
    /** Pickup GPS coordinates (captured at PRE_RIDE detection time). */
    val pickupLatLon: LatLon?,
    /** Total route distance in metres returned by OSRM. 0 until route is fetched. */
    val totalRouteMeters: Int,
    /** Progress 0f..1f. -1f means unknown (destination not resolved yet — no manual fallback). */
    val progressFraction: Float,
) {
    val isActive: Boolean get() = phase != RidePhase.IDLE

    /**
     * True only when we have enough info to draw the progress circle.
     * Per spec: progress is ONLY shown when destination is known — never a
     * manual/placeholder value.
     */
    val hasProgress: Boolean
        get() = progressFraction >= 0f && phase == RidePhase.IN_RIDE && totalRouteMeters > 0

    /** Short ETA string to show in the pill indicator depending on phase. */
    val pillEtaText: String
        get() = when (phase) {
            RidePhase.PRE_RIDE -> driverEtaText
            RidePhase.IN_RIDE  -> destEtaText
            else               -> ""
        }
}

val EmptyRideSnapshot = RideSnapshot(
    phase             = RidePhase.IDLE,
    app               = null,
    otp               = "",
    driverEtaText     = "",
    driverName        = "",
    vehicleInfo       = "",
    driverRating      = "",
    destEtaText       = "",
    destinationName   = "",
    destinationLatLon = null,
    pickupLatLon      = null,
    totalRouteMeters  = 0,
    progressFraction  = -1f,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Haversine distance helper
// ─────────────────────────────────────────────────────────────────────────────

/** Straight-line distance between two GPS coordinates in metres. */
fun haversineMeters(a: LatLon, b: LatLon): Double {
    val r = 6_371_000.0  // Earth radius in metres
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val sinLat = Math.sin(dLat / 2)
    val sinLon = Math.sin(dLon / 2)
    val h = sinLat * sinLat +
            Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) *
            sinLon * sinLon
    return 2 * r * Math.asin(Math.sqrt(h))
}