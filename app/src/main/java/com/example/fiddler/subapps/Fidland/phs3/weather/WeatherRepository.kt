package com.example.fiddler.subapps.Fidland.phs3.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * WeatherRepository
 *
 * Singleton that owns the live [WeatherSnapshot] StateFlow and knows how to
 * refresh it from Open-Meteo. All UI and trigger code reads [flow]; only
 * [WeatherPhs3Trigger] calls [refresh].
 *
 * ── API choice: Open-Meteo ────────────────────────────────────────────────────
 *   • Completely free — no API key, no quota cap.
 *   • Uses ECMWF + regional models; accurate for Indian cities.
 *   • Single endpoint returns current conditions AND hourly forecast.
 *   • Docs: https://open-meteo.com/en/docs
 *
 * ── Endpoint ──────────────────────────────────────────────────────────────────
 *   GET https://api.open-meteo.com/v1/forecast
 *     ?latitude={lat}
 *     &longitude={lon}
 *     &current=temperature_2m,apparent_temperature,weathercode,
 *              windspeed_10m,winddirection_10m,relativehumidity_2m
 *     &hourly=temperature_2m,weathercode
 *     &wind_speed_unit=kmh
 *     &forecast_days=1
 *     &timezone=Asia/Kolkata
 *
 * ── Refresh cadence ───────────────────────────────────────────────────────────
 *   WeatherPhs3Trigger calls [refresh] every [REFRESH_INTERVAL_MS] (15 min).
 *   The repository itself is stateless about timing — it just fetches when asked
 *   and emits the result. The trigger owns the polling loop.
 *
 * ── Hourly slice ─────────────────────────────────────────────────────────────
 *   Open-Meteo returns 24 hourly slots for forecast_days=1. We pick the next
 *   5 upcoming hours relative to the current IST time, skipping any hour that
 *   has already passed. This is done entirely from the returned `time` array —
 *   no device clock arithmetic needed beyond finding "now".
 *
 * ── Error handling ────────────────────────────────────────────────────────────
 *   On any network or parse error, [flow] retains its last good value so the
 *   pill keeps showing stale data rather than going blank. Errors are logged
 *   to [WeatherDebugLog] (same pattern as Phs3DebugLog).
 */
object WeatherRepository {

    const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L   // 15 minutes

    private val _flow = MutableStateFlow<WeatherSnapshot?>(null)
    val flow: StateFlow<WeatherSnapshot?> = _flow

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fetches fresh weather data for the given coordinates and emits a new
     * [WeatherSnapshot] on [flow]. Safe to call from any coroutine — switches
     * to [Dispatchers.IO] internally.
     *
     * Does nothing on network error; last good snapshot is retained.
     */
    suspend fun refresh(lat: Double, lon: Double) {
        val snapshot = withContext(Dispatchers.IO) {
            try {
                fetch(lat, lon)
            } catch (e: Exception) {
                android.util.Log.w("WeatherRepo", "Fetch failed: ${e.message}")
                null
            }
        }
        if (snapshot != null) {
            _flow.value = snapshot
        }
    }

    // ── Fetch + parse ─────────────────────────────────────────────────────────

    private fun fetch(lat: Double, lon: Double): WeatherSnapshot {
        val url = buildUrl(lat, lon)
        val json = httpGet(url)
        return parse(json)
    }

    private fun buildUrl(lat: Double, lon: Double): String {
        val currentFields = listOf(
            "temperature_2m",
            "apparent_temperature",
            "weathercode",
            "windspeed_10m",
            "winddirection_10m",
            "relativehumidity_2m",
        ).joinToString(",")

        val hourlyFields = listOf(
            "temperature_2m",
            "weathercode",
        ).joinToString(",")

        return "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${"%.4f".format(lat)}" +
                "&longitude=${"%.4f".format(lon)}" +
                "&current=$currentFields" +
                "&hourly=$hourlyFields" +
                "&wind_speed_unit=kmh" +
                "&forecast_days=1" +
                "&timezone=Asia%2FKolkata"
    }

    private fun httpGet(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod  = "GET"
            connectTimeout = 10_000
            readTimeout    = 10_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            reader.use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(raw: String): WeatherSnapshot {
        val root    = JSONObject(raw)
        val current = root.getJSONObject("current")
        val hourly  = root.getJSONObject("hourly")

        // ── Current conditions ────────────────────────────────────────────────
        val tempC       = current.getDouble("temperature_2m").toInt()
        val feelsLikeC  = current.getDouble("apparent_temperature").toInt()
        val humidity    = current.getInt("relativehumidity_2m")
        val windSpeed   = current.getDouble("windspeed_10m").toInt()
        val windDirDeg  = current.getDouble("winddirection_10m").toFloat()
        val wmoCode     = current.getInt("weathercode")

        val condition   = wmoCode.toWeatherCondition()
        val windDir     = windDirDeg.toCompassLabel()

        // ── Hourly strip — next 5 upcoming hours ──────────────────────────────
        val times     = hourly.getJSONArray("time")
        val hTemps    = hourly.getJSONArray("temperature_2m")
        val hCodes    = hourly.getJSONArray("weathercode")

        // "Now" in IST — Open-Meteo time strings are "YYYY-MM-DDTHH:MM"
        val istFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        val nowCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val nowHour = nowCal.get(Calendar.HOUR_OF_DAY)

        val slots = mutableListOf<HourlySlot>()
        for (i in 0 until times.length()) {
            if (slots.size == 5) break
            val timeStr = times.getString(i)               // e.g. "2025-06-19T14:00"
            val slotHour = timeStr.substringAfter("T").substringBefore(":").toIntOrNull() ?: continue
            if (slotHour <= nowHour) continue              // skip past hours

            val slotTempC     = hTemps.getDouble(i).toInt()
            val slotCondition = hCodes.getInt(i).toWeatherCondition()
            val displayHour   = formatHour(slotHour)

            slots.add(HourlySlot(displayHour, slotCondition, slotTempC))
        }

        return WeatherSnapshot(
            condition    = condition,
            tempC        = tempC,
            feelsLikeC   = feelsLikeC,
            humidityPct  = humidity,
            windSpeedKmh = windSpeed,
            windDir      = windDir,
            nextHours    = slots,
            sarcasm      = condition.pickSarcasm(),
        )
    }

    /**
     * Converts a 24h hour int to a display string, e.g. 14 → "2 PM", 9 → "9 AM".
     */
    private fun formatHour(hour24: Int): String {
        return when {
            hour24 == 0  -> "12 AM"
            hour24 < 12  -> "$hour24 AM"
            hour24 == 12 -> "12 PM"
            else         -> "${hour24 - 12} PM"
        }
    }
}