package com.example.fiddler.subapps.Fidland.phs3.weather

import android.annotation.SuppressLint
import android.content.Context
import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.service.FidlandService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * WeatherPhs3Trigger
 *
 * Always-on trigger — activates [WeatherPhs3Handler] once on start and keeps
 * it active for the lifetime of the service. Weather is always relevant, so
 * there is no qualify/deactivate logic; the handler is registered immediately
 * and only removed when [stop] is called (i.e. service destroy).
 *
 * ── Location strategy ────────────────────────────────────────────────────────
 *   Uses [FusedLocationProviderClient.getCurrentLocation] (same client already
 *   used by RidePhs3Trigger) with PRIORITY_BALANCED_POWER_ACCURACY.
 *   Weather doesn't need precise GPS — cell/wifi accuracy is fine and cheaper
 *   on battery. Location is re-fetched on every refresh cycle so the pill
 *   stays accurate if the user has moved.
 *
 * ── Refresh cadence ──────────────────────────────────────────────────────────
 *   Fetches immediately on start, then every [WeatherRepository.REFRESH_INTERVAL_MS]
 *   (15 minutes). The loop runs for the lifetime of the service.
 *
 * ── Error handling ───────────────────────────────────────────────────────────
 *   If location is unavailable or the network call fails, the handler keeps
 *   showing whatever snapshot [WeatherRepository.flow] last emitted. On first
 *   start with no prior snapshot the handler shows a loading state — see
 *   [WeatherPhs3Handler.Indicator].
 *
 * ── Wire-up in FidlandService ────────────────────────────────────────────────
 *
 *   private lateinit var weatherTrigger: WeatherPhs3Trigger
 *
 *   override fun onCreate() {
 *       ...
 *       weatherTrigger = WeatherPhs3Trigger(applicationContext, serviceScope, this)
 *       weatherTrigger.start()
 *   }
 *
 *   override fun onDestroy() {
 *       weatherTrigger.stop()
 *       ...
 *   }
 */
class WeatherPhs3Trigger(
    private val context: Context,
    private val scope: CoroutineScope,
    private val service: FidlandService,
) {

    private val handler = WeatherPhs3Handler()
    private var refreshJob: Job? = null

    // Reuse the same FusedLocationProviderClient that RidePhs3Trigger uses —
    // no need for a separate client instance. Lazy so it's never created if
    // the trigger is constructed but never started.
    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun start() {
        Phs3DebugLog.onTriggerStart("Weather")

        // Register the handler immediately — weather is always-on.
        service.activatePhs3(handler)

        // Kick off the refresh loop.
        refreshJob = scope.launch {
            while (true) {
                tick()
                delay(WeatherRepository.REFRESH_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Weather")
        refreshJob?.cancel()
        refreshJob = null
        service.deactivatePhs3(handler.label)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission") // caller (FidlandService) already holds ACCESS_COARSE_LOCATION
    private suspend fun tick() {
        val location = try {
            fusedClient
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .await()
        } catch (e: Exception) {
            Phs3DebugLog.onPoll("Weather", "location unavailable: ${e.message}")
            null
        }

        if (location == null) {
            Phs3DebugLog.onPoll("Weather", "location null — skipping fetch, retaining last snapshot")
            return
        }

        Phs3DebugLog.onPoll(
            "Weather",
            "lat=%.4f lon=%.4f — fetching Open-Meteo".format(location.latitude, location.longitude)
        )

        WeatherRepository.refresh(location.latitude, location.longitude)

        val snap = WeatherRepository.flow.value
        if (snap != null) {
            Phs3DebugLog.onPoll(
                "Weather",
                "${snap.condition.toLabel()} ${snap.tempC}°C feels ${snap.feelsLikeC}°C"
            )
        }
    }
}