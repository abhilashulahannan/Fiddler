package com.example.fiddler.subapps.Fidland.phs3.weather

/**
 * WMO weather interpretation code → structured condition.
 *
 * Open-Meteo returns a `weathercode` integer in every current/hourly block.
 * The full WMO table lives at:
 *   https://open-meteo.com/en/docs#weathervariables
 *
 * We collapse the ~30 raw codes into 8 app-level conditions that drive
 * emoji, label, and sarcastic pun selection.
 */
enum class WeatherCondition {
    CLEAR,          // 0
    PARTLY_CLOUDY,  // 1, 2
    OVERCAST,       // 3
    FOG,            // 45, 48
    DRIZZLE,        // 51–57
    RAIN,           // 61–67, 80–82
    THUNDERSTORM,   // 95–99
    SNOW,           // 71–77, 85–86
}

fun Int.toWeatherCondition(): WeatherCondition = when (this) {
    0            -> WeatherCondition.CLEAR
    1, 2         -> WeatherCondition.PARTLY_CLOUDY
    3            -> WeatherCondition.OVERCAST
    45, 48       -> WeatherCondition.FOG
    in 51..57    -> WeatherCondition.DRIZZLE
    in 61..67,
    in 80..82    -> WeatherCondition.RAIN
    in 71..77,
    85, 86       -> WeatherCondition.SNOW
    in 95..99    -> WeatherCondition.THUNDERSTORM
    else         -> WeatherCondition.OVERCAST
}

fun WeatherCondition.toEmoji(): String = when (this) {
    WeatherCondition.CLEAR          -> "☀️"
    WeatherCondition.PARTLY_CLOUDY  -> "⛅"
    WeatherCondition.OVERCAST       -> "☁️"
    WeatherCondition.FOG            -> "🌫️"
    WeatherCondition.DRIZZLE        -> "🌦️"
    WeatherCondition.RAIN           -> "🌧️"
    WeatherCondition.THUNDERSTORM   -> "⛈️"
    WeatherCondition.SNOW           -> "❄️"
}

fun WeatherCondition.toLabel(): String = when (this) {
    WeatherCondition.CLEAR          -> "Clear"
    WeatherCondition.PARTLY_CLOUDY  -> "Partly cloudy"
    WeatherCondition.OVERCAST       -> "Overcast"
    WeatherCondition.FOG            -> "Foggy"
    WeatherCondition.DRIZZLE        -> "Drizzle"
    WeatherCondition.RAIN           -> "Rain"
    WeatherCondition.THUNDERSTORM   -> "Thunderstorm"
    WeatherCondition.SNOW           -> "Snow"
}

/**
 * Converts a wind direction in degrees (0–360, meteorological, i.e. the
 * direction the wind is coming FROM) to an 8-point compass label.
 */
fun Float.toCompassLabel(): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[((this / 45f).toInt()) % 8]
}

// ── Hourly forecast slot ──────────────────────────────────────────────────────

/**
 * A single hour in the hourly forecast strip shown in State 5.
 *
 * @param hour        Display hour in 12h format, e.g. "3 PM".
 * @param condition   WMO-derived condition for emoji.
 * @param tempC       Temperature in °C rounded to nearest integer.
 */
data class HourlySlot(
    val hour: String,
    val condition: WeatherCondition,
    val tempC: Int,
)

// ── Top-level snapshot ────────────────────────────────────────────────────────

/**
 * Everything the weather UI needs, sourced from a single Open-Meteo call.
 *
 * @param condition         Current WMO-derived condition.
 * @param tempC             Current temperature °C (rounded).
 * @param feelsLikeC        Apparent temperature °C (rounded).
 * @param humidityPct       Relative humidity 0–100.
 * @param windSpeedKmh      Wind speed in km/h (rounded).
 * @param windDir           Wind direction as compass label, e.g. "NW".
 * @param nextHours         Next 5 hourly slots for the forecast strip.
 * @param sarcasm           Condition-appropriate pun to display in State 5.
 * @param fetchedAtMs       Epoch-ms when this snapshot was built — used by
 *                          the trigger to decide when to refresh.
 */
data class WeatherSnapshot(
    val condition: WeatherCondition,
    val tempC: Int,
    val feelsLikeC: Int,
    val humidityPct: Int,
    val windSpeedKmh: Int,
    val windDir: String,
    val nextHours: List<HourlySlot>,
    val sarcasm: String,
    val fetchedAtMs: Long = System.currentTimeMillis(),
)

// ── Sarcastic puns ────────────────────────────────────────────────────────────
//
// Keyed by WeatherCondition. pickSarcasm() chooses one at random so the user
// doesn't see the same line every refresh. Add more freely — the more the better.
//
// Tone guide: dry, affectionate, mildly roasting. Avoid anything mean-spirited.
// India-aware references are welcome (samosa, auto-rickshaw, etc.).

private val SARCASMS: Map<WeatherCondition, List<String>> = mapOf(

    WeatherCondition.CLEAR to listOf(
        "Great day to touch grass. You won't, but it's there.",
        "Sun's out. Your excuse to stay inside just evaporated.",
        "Perfect weather. Shame you'll spend it staring at this screen.",
        "It's so nice outside even your plants are judging you.",
        "Ideal conditions for a walk. This notification counts as one.",
    ),

    WeatherCondition.PARTLY_CLOUDY to listOf(
        "Partly cloudy — like your productivity today.",
        "Clouds can't commit. Neither can you. Kindred spirits.",
        "Half sun, half clouds. The weather is also confused about its plans.",
        "Could go either way. Much like your to-do list.",
    ),

    WeatherCondition.OVERCAST to listOf(
        "Grey skies. At least the weather matches your Monday energy.",
        "Overcast. The sky is as enthusiastic about today as you are.",
        "No sun today. Your vitamin D levels have filed a complaint.",
        "Gloomy out there. Perfect for blaming the weather for everything.",
    ),

    WeatherCondition.FOG to listOf(
        "Can't see 10 metres ahead. Relatable life planning.",
        "Foggy outside. Your GPS is as lost as your life goals.",
        "Visibility near zero. Just like your career roadmap.",
        "The fog rolled in. Your plans remain similarly unclear.",
    ),

    WeatherCondition.DRIZZLE to listOf(
        "Drizzling. Not enough to cancel plans, just enough to ruin hair.",
        "Light drizzle — the weather equivalent of a passive-aggressive note.",
        "Barely raining. The sky couldn't commit to a full effort either.",
        "Drizzle: nature's way of saying 'maybe stay in, coward.'",
    ),

    WeatherCondition.RAIN to listOf(
        "It's raining. Your umbrella is, as always, somewhere else.",
        "Heavy rain. Auto-rickshaw drivers have vanished into another dimension.",
        "Raining cats and dogs. Mostly the kind that splash you.",
        "Great day to order in, call it productivity, and feel zero guilt.",
        "The rain doesn't care about your plans. Neither does your boss, but still.",
    ),

    WeatherCondition.THUNDERSTORM to listOf(
        "Thunderstorm. Unplug everything and question your life choices indoors.",
        "Lightning outside. Please do not hold your phone up to see it better.",
        "Full storm mode. Nature is having the breakdown you've been suppressing.",
        "Thunderstorm advisory: stay inside, eat something, panic quietly.",
    ),

    WeatherCondition.SNOW to listOf(
        "It's snowing. Everything will grind to a halt — including you.",
        "Snow day! Unless you're one of those people who still commutes. Condolences.",
        "Snowing outside. The one time being cold is considered an achievement.",
        "White-out conditions. Perfect for rethinking every decision that led here.",
    ),
)

/**
 * Returns a random sarcastic pun for the given condition.
 * Falls back to a generic line if the map somehow has no entry.
 */
fun WeatherCondition.pickSarcasm(): String =
    SARCASMS[this]?.random()
        ?: "Weather exists. So do you. Somehow both are your problem."