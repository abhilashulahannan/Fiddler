package com.example.fiddler.subapps.Fidland.phs3.ride

import android.annotation.SuppressLint
import android.content.Context
import com.example.fiddler.subapps.Fidland.service.FidlandService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * RidePhs3Trigger
 *
 * Watches [RideRepository.flow] and:
 *  • activates / deactivates the Ride phs3 slot on FidlandService
 *  • drives GPS polling cadence: every 10s in PRE_RIDE, every 15s in IN_RIDE,
 *    stopped entirely once IDLE/ENDED
 *
 * No manual input anywhere in this flow — pickup is captured automatically
 * from the first GPS fix after a ride is detected; destination comes from
 * notification scraping (RideRepository) or stays unresolved, in which case
 * the pill simply omits the progress circle (see RideSnapshot.hasProgress).
 *
 * ── Wire-up in FidlandService ────────────────────────────────────────────
 *
 *   private lateinit var rideTrigger: RidePhs3Trigger
 *
 *   override fun onCreate() {
 *       ...
 *       RideRepository.attachScope(serviceScope)
 *       rideTrigger = RidePhs3Trigger(applicationContext, serviceScope, this)
 *       rideTrigger.start()
 *   }
 *
 *   override fun onDestroy() {
 *       rideTrigger.stop()
 *       ...
 *   }
 */
class RidePhs3Trigger(
    private val context: Context,
    private val scope: CoroutineScope,
    private val service: FidlandService,
) {
    companion object {
        private const val PRE_RIDE_POLL_MS = 10_000L
        private const val IN_RIDE_POLL_MS  = 15_000L
        /** How long ENDED is shown before the repository resets and we deregister. */
        private const val ENDED_LINGER_MS  = 4_000L
    }

    private val handler = RidePhs3Handler()
    private var watchJob: Job? = null
    private var pollJob: Job? = null

    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    private var locationCallback: LocationCallback? = null
    private var currentPollIntervalMs: Long = -1L

    fun start() {
        watchJob = scope.launch {
            RideRepository.flow.collect { snapshot ->
                when (snapshot.phase) {
                    RidePhase.IDLE -> {
                        stopLocationUpdates()
                        service.deactivatePhs3(handler.label)
                    }
                    RidePhase.PRE_RIDE -> {
                        service.activatePhs3(handler)
                        ensurePolling(PRE_RIDE_POLL_MS)
                    }
                    RidePhase.IN_RIDE -> {
                        service.activatePhs3(handler)
                        ensurePolling(IN_RIDE_POLL_MS)
                        RideRepository.fuseFromNavigationIfNeeded()
                    }
                    RidePhase.ENDED -> {
                        stopLocationUpdates()
                        service.activatePhs3(handler) // brief lingering display
                        scope.launch {
                            delay(ENDED_LINGER_MS)
                            service.deactivatePhs3(handler.label)
                            RideRepository.reset()
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        watchJob?.cancel()
        watchJob = null
        stopLocationUpdates()
        RideRepository.reset()
    }

    // ── GPS polling ──────────────────────────────────────────────────────

    @SuppressLint("MissingPermission") // caller must already hold ACCESS_FINE_LOCATION
    private fun ensurePolling(intervalMs: Long) {
        if (currentPollIntervalMs == intervalMs && locationCallback != null) return
        stopLocationUpdates()
        currentPollIntervalMs = intervalMs

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                RideRepository.onLocationUpdate(LatLon(loc.latitude, loc.longitude))
            }
        }
        locationCallback = callback
        fusedClient.requestLocationUpdates(request, callback, null)
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
        currentPollIntervalMs = -1L
        pollJob?.cancel()
        pollJob = null
    }
}