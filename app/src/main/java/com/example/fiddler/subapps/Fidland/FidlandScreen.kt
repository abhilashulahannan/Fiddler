package com.example.fiddler.subapps.Fidland

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.example.fiddler.R
import com.example.fiddler.core.SubAppState
import com.example.fiddler.subapps.Fidland.phs3.football.FootballApiRequestLog
import com.example.fiddler.subapps.Fidland.service.FidlandService

// ---------------------------------------------------------------------------
// FidlandScreen
// ---------------------------------------------------------------------------

@Composable
fun FidlandScreen(context: Context) {
    val prefs = context.getSharedPreferences("fidland_prefs", Context.MODE_PRIVATE)
    val editor = prefs.edit()

    val bodyFont    = FontFamily(Font(R.font.font_body))
    val handFont    = FontFamily(Font(R.font.font_handwriting))
    val scrollState = rememberScrollState()

    // ── Master toggle ────────────────────────────────────────────────────────
    var enabled by remember { SubAppState.fidlandEnabled }

    // ── Music ────────────────────────────────────────────────────────────────
    var musicPlayer   by remember { mutableStateOf(prefs.getBoolean("music_player",   false)) }
    var musicPlaylist by remember { mutableStateOf(prefs.getBoolean("music_playlist", false)) }
    var musicPhs3     by remember { mutableStateOf(prefs.getBoolean("music_phs3",     false)) }

    // ── Football ─────────────────────────────────────────────────────────────
    var footballEnabled by remember { mutableStateOf(prefs.getBoolean("football_enabled", false)) }
    var footballApiKey  by remember { mutableStateOf(prefs.getString("football_api_key", "") ?: "") }
    var footballApiKeyVisible by remember { mutableStateOf(false) }
    var footballAfApiKey  by remember { mutableStateOf(prefs.getString("football_af_api_key", "") ?: "") }
    var footballAfApiKeyVisible by remember { mutableStateOf(false) }
    var footballRequestSnapshot by remember {
        mutableStateOf(FootballApiRequestLog(context).todayCounts())
    }

    // ── App Launcher ─────────────────────────────────────────────────────────
    var appRows    by remember { mutableStateOf(prefs.getInt("app_rows",    3)) }
    var appColumns by remember { mutableStateOf(prefs.getInt("app_columns", 4)) }
    var launcherApps: Set<String> by remember {
        mutableStateOf(prefs.getStringSet("launcher_apps", emptySet())?.toSet() ?: emptySet())
    }
    var showAppPicker by remember { mutableStateOf(false) }

    // ── All Things Island — System Utilities ─────────────────────────────────
    var netSpeed by remember { mutableStateOf(prefs.getBoolean("net_speed",  false)) }
    var call     by remember { mutableStateOf(prefs.getBoolean("phs3_call",  false)) }
    var comms    by remember { mutableStateOf(prefs.getBoolean("phs3_comms", false)) }
    var download by remember { mutableStateOf(prefs.getBoolean("phs3_download", false)) }

    // ── All Things Island — Personal Utilities ───────────────────────────────
    var alarm    by remember { mutableStateOf(prefs.getBoolean("phs3_alarm",    false)) }
    var timer    by remember { mutableStateOf(prefs.getBoolean("phs3_timer",    false)) }
    var torch    by remember { mutableStateOf(prefs.getBoolean("phs3_torch",    false)) }
    var recorder by remember { mutableStateOf(prefs.getBoolean("phs3_recorder", false)) }
    var nav      by remember { mutableStateOf(prefs.getBoolean("phs3_nav",      false)) }
    var calendar by remember { mutableStateOf(prefs.getBoolean("phs3_calendar", false)) }
    var weather  by remember { mutableStateOf(prefs.getBoolean("phs3_weather",  false)) }

    // ── All Things Island — Third-Party Integrations ─────────────────────────
    var ride     by remember { mutableStateOf(prefs.getBoolean("phs3_ride",     false)) }
    var delivery by remember { mutableStateOf(prefs.getBoolean("phs3_delivery", false)) }

    // ── Quick Settings ───────────────────────────────────────────────────────
    var quickSettings by remember { mutableStateOf(prefs.getBoolean("quick_settings", false)) }

    // ── App picker dialog ────────────────────────────────────────────────────
    if (showAppPicker) {
        AppPickerDialog(
            context          = context,
            currentlySelected = launcherApps,
            onDismiss        = { showAppPicker = false },
            onConfirm        = { selected ->
                launcherApps = selected
                editor.putStringSet("launcher_apps", selected).apply()
                restartFidlandService(context)
                showAppPicker = false
            }
        )
    }

    // ── Layout ───────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(50.dp))

        // Header
        Text(
            text       = "FidLand",
            fontSize   = 48.sp,
            fontFamily = bodyFont,
            color      = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text       = "My attempt at dynamic island",
            fontSize   = 18.sp,
            fontFamily = handFont,
            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier   = Modifier.padding(bottom = 16.dp)
        )

        // Master toggle
        FidlandToggleRow(
            label       = "Enable Fidland",
            description = "Starts the overlay service that renders the pill on top of all apps. " +
                    "Everything below depends on this being on.",
            checked     = enabled,
            font        = handFont,
            onChange    = { SubAppState.setFidlandEnabled(context, it) }
        )

        Spacer(modifier = Modifier.height(28.dp))
        FidlandDivider()

        // ── 1. Music Integration ─────────────────────────────────────────────
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(
            title    = "Music Integration",
            subtitle = "Controls for Spotify and YouTube Music. Three separate layers — " +
                    "player controls in the dashboard, queue access, and a compact live indicator in the pill.",
            bodyFont = bodyFont,
            handFont = handFont
        )

        FidlandToggleRow(
            label       = "Music Player",
            description = "Full player controls — track info, album art, playback buttons, progress bar, " +
                    "and lyrics. Shown as the first tab in the expanded dashboard. " +
                    "Reads metadata from Spotify and YT Music via MediaSession.",
            checked     = musicPlayer,
            font        = handFont,
            onChange    = {
                musicPlayer = it
                editor.putBoolean("music_player", it).apply()
                restartFidlandService(context)
            }
        )

        FidlandToggleRow(
            label       = "Queue / Playlist",
            description = "Shows the upcoming queue for the active music app. Spotify exposes queue " +
                    "items via MediaController; YT Music does not — a fallback message is shown " +
                    "for YT Music sessions. Shown as the second tab in the expanded dashboard.",
            checked     = musicPlaylist,
            font        = handFont,
            onChange    = {
                musicPlaylist = it
                editor.putBoolean("music_playlist", it).apply()
                restartFidlandService(context)
            }
        )

        FidlandToggleRow(
            label       = "Live Pill",
            description = "Compact music indicator in the pill when music is playing — album art spinner, " +
                    "track name, and a synced lyrics panel on long-press. Activates automatically " +
                    "when playback starts and deactivates on pause.",
            checked     = musicPhs3,
            font        = handFont,
            onChange    = {
                musicPhs3 = it
                editor.putBoolean("music_phs3", it).apply()
                restartFidlandService(context)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
        FidlandDivider()

        // ── 2. Football ──────────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(
            title    = "Football",
            subtitle = "Live match scores and events in the pill. Tracks eight competitions across " +
                    "up to three data sources. football-data.org is the schedule of record for all " +
                    "eight (requires a free key). OpenLigaDB supplements Bundesliga with near-real-time " +
                    "live data — no key needed. api-football is optional and adds richer live data " +
                    "(goal scorers, cards, substitutions) during active matches, within a 100-call-per-day " +
                    "free tier.",
            bodyFont = bodyFont,
            handFont = handFont
        )

        FidlandToggleRow(
            label       = "Enable Football",
            description = "Activates the live match tracker. The pill shows the current score, " +
                    "match clock, and flashes on goals and cards. Polls every 60 seconds " +
                    "during live matches; drops to every 5 minutes between fixtures.",
            checked     = footballEnabled,
            font        = handFont,
            onChange    = {
                footballEnabled = it
                editor.putBoolean("football_enabled", it).apply()
                restartFidlandService(context)
            }
        )

        if (footballEnabled) {
            Spacer(modifier = Modifier.height(16.dp))

            // OpenLigaDB — informational, no key needed
            Surface(
                shape    = RoundedCornerShape(10.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text       = "OpenLigaDB  —  No key required",
                        fontSize   = 14.sp,
                        fontFamily = handFont,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text       = "Champions League · Bundesliga · UEFA Euros · World Cup",
                        fontSize   = 13.sp,
                        fontFamily = handFont,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // football-data.org — requires API key
            Surface(
                shape    = RoundedCornerShape(10.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text       = "football-data.org  —  API key required",
                        fontSize   = 14.sp,
                        fontFamily = handFont,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text       = "Premier League",
                        fontSize   = 13.sp,
                        fontFamily = handFont,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text       = "Free tier is sufficient. Register at football-data.org to get your key.",
                        fontSize   = 12.sp,
                        fontFamily = handFont,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier   = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value         = footballApiKey,
                        onValueChange = {
                            footballApiKey = it
                            editor.putString("football_api_key", it).apply()
                        },
                        singleLine    = true,
                        placeholder   = {
                            Text("Paste API key here", fontFamily = handFont, fontSize = 14.sp)
                        },
                        visualTransformation = if (footballApiKeyVisible)
                            androidx.compose.ui.text.input.VisualTransformation.None
                        else
                            androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon  = {
                            TextButton(onClick = { footballApiKeyVisible = !footballApiKeyVisible }) {
                                Text(
                                    text       = if (footballApiKeyVisible) "Hide" else "Show",
                                    fontFamily = handFont,
                                    fontSize   = 13.sp
                                )
                            }
                        },
                        textStyle     = androidx.compose.ui.text.TextStyle(
                            fontFamily = handFont,
                            fontSize   = 14.sp
                        ),
                        modifier      = Modifier.fillMaxWidth()
                    )
                    if (footballApiKey.isBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text       = "Premier League data will not load without this key. " +
                                    "All other competitions work regardless.",
                            fontSize   = 12.sp,
                            fontFamily = handFont,
                            color      = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // api-football (api-sports.io) — optional, requires API key
            Surface(
                shape    = RoundedCornerShape(10.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text       = "api-football  —  Optional, API key required",
                        fontSize   = 14.sp,
                        fontFamily = handFont,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text       = "Richer live data — goal scorers, cards, substitutions",
                        fontSize   = 13.sp,
                        fontFamily = handFont,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text       = "Free tier allows 100 requests/day. Register at " +
                                "dashboard.api-football.com to get your key. Leave blank to run " +
                                "on football-data.org and OpenLigaDB only.",
                        fontSize   = 12.sp,
                        fontFamily = handFont,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier   = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value         = footballAfApiKey,
                        onValueChange = {
                            footballAfApiKey = it
                            editor.putString("football_af_api_key", it).apply()
                            restartFidlandService(context)
                        },
                        singleLine    = true,
                        placeholder   = {
                            Text("Paste API key here (optional)", fontFamily = handFont, fontSize = 14.sp)
                        },
                        visualTransformation = if (footballAfApiKeyVisible)
                            androidx.compose.ui.text.input.VisualTransformation.None
                        else
                            androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon  = {
                            TextButton(onClick = { footballAfApiKeyVisible = !footballAfApiKeyVisible }) {
                                Text(
                                    text       = if (footballAfApiKeyVisible) "Hide" else "Show",
                                    fontFamily = handFont,
                                    fontSize   = 13.sp
                                )
                            }
                        },
                        textStyle     = androidx.compose.ui.text.TextStyle(
                            fontFamily = handFont,
                            fontSize   = 14.sp
                        ),
                        modifier      = Modifier.fillMaxWidth()
                    )
                    if (footballAfApiKey.isBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text       = "Without this key, live matches fall back to " +
                                    "football-data.org / OpenLigaDB data only — still works, just " +
                                    "less detailed during play.",
                            fontSize   = 12.sp,
                            fontFamily = handFont,
                            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }
            }

            // Keep the request counter live while this section is visible —
            // re-reads the shared log file every 30 s so it reflects whatever
            // the background service most recently recorded.
            LaunchedEffect(footballEnabled) {
                while (true) {
                    footballRequestSnapshot = FootballApiRequestLog(context).todayCounts()
                    kotlinx.coroutines.delay(30_000L)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── APIs used by the Football tracker ──────────────────────────
            SubSectionHeader(title = "APIs used", font = handFont)
            FootballApiListing(handFont = handFont)

            Spacer(modifier = Modifier.height(16.dp))

            // ── Requests sent today, across all four APIs ───────────────────
            FootballRequestCounterCard(
                snapshot = footballRequestSnapshot,
                handFont = handFont
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        FidlandDivider()

        // ── 3. Application Launcher ──────────────────────────────────────────
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(
            title    = "Application Launcher",
            subtitle = "A paginated app grid in the expanded dashboard. Configure the grid layout " +
                    "and choose which apps appear. Pages are calculated automatically from the grid size.",
            bodyFont = bodyFont,
            handFont = handFont
        )

        // Grid size steppers
        Row(
            modifier            = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StepperItem(
                label    = "Columns",
                value    = appColumns,
                min      = 3,
                max      = 5,
                font     = handFont,
                modifier = Modifier.weight(1f),
                onChange = {
                    appColumns = it
                    editor.putInt("app_columns", it).apply()
                    restartFidlandService(context)
                }
            )
            StepperItem(
                label    = "Rows",
                value    = appRows,
                min      = 2,
                max      = 4,
                font     = handFont,
                modifier = Modifier.weight(1f),
                onChange = {
                    appRows = it
                    editor.putInt("app_rows", it).apply()
                    restartFidlandService(context)
                }
            )
        }

        Text(
            text       = "${appRows * appColumns} apps per page",
            fontSize   = 13.sp,
            fontFamily = handFont,
            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier   = Modifier.padding(bottom = 12.dp)
        )

        // Selected app chips
        if (launcherApps.isNotEmpty()) {
            Text(
                text       = "Selected apps",
                fontSize   = 15.sp,
                fontFamily = handFont,
                color      = MaterialTheme.colorScheme.onSurface,
                modifier   = Modifier.padding(bottom = 8.dp)
            )
            AppChipRow(
                context      = context,
                packageNames = launcherApps,
                font         = handFont,
                onRemove     = { pkg ->
                    launcherApps = launcherApps - pkg
                    editor.putStringSet("launcher_apps", launcherApps).apply()
                    restartFidlandService(context)
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        OutlinedButton(
            onClick  = { showAppPicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text       = if (launcherApps.isEmpty()) "Add Apps" else "Edit Apps",
                fontFamily = handFont,
                fontSize   = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        FidlandDivider()

        // ── 4. All Things Island ─────────────────────────────────────────────
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(
            title    = "All Things Island",
            subtitle = "Automated pill entities. Each one activates when its qualifying condition is met " +
                    "and deactivates when it isn't. Multiple active entities rotate in the pill " +
                    "every 5 seconds.",
            bodyFont = bodyFont,
            handFont = handFont
        )

        // System Utilities
        SubSectionHeader("System Utilities", handFont)
        Text(
            text     = "Always-on indicators tied to device and network state.",
            fontSize = 13.sp, fontFamily = handFont,
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 10.dp)
        )

        FidlandToggleRow(
            label       = "Net Speed",
            description = "Displays live upload and download speed in the left segment of the pill. " +
                    "Updates every second using Android's TrafficStats. No special permission required.",
            checked     = netSpeed,
            font        = handFont,
            onChange    = {
                netSpeed = it
                editor.putBoolean("net_speed", it).apply()
                restartFidlandService(context)
            }
        )

        FidlandToggleRow(
            label       = "Call",
            description = "Activates during an active phone call. Shows caller name or number, " +
                    "call duration, and quick action buttons in the pill.",
            permission  = "READ_PHONE_STATE, READ_CALL_LOG",
            checked     = call,
            font        = handFont,
            onChange    = {
                call = it
                editor.putBoolean("phs3_call", it).apply()
                restartFidlandService(context)
            }
        )

        FidlandToggleRow(
            label       = "Comms",
            description = "Shows which communication hardware is currently active — WiFi, Bluetooth, NFC. " +
                    "Indicator updates as connectivity state changes.",
            permission  = "ACCESS_WIFI_STATE, BLUETOOTH, NFC",
            checked     = comms,
            font        = handFont,
            onChange    = {
                comms = it
                editor.putBoolean("phs3_comms", it).apply()
                restartFidlandService(context)
            }
        )

        FidlandToggleRow(
            label       = "Download",
            description = "Activates when an active download is in progress. Shows the current network " +
                    "type (WiFi, 4G, 5G) and download progress in the pill.",
            checked     = download,
            font        = handFont,
            onChange    = {
                download = it
                editor.putBoolean("phs3_download", it).apply()
                restartFidlandService(context)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Personal Utilities
        SubSectionHeader("Personal Utilities", handFont)
        Text(
            text     = "Triggered by active user sessions or device states.",
            fontSize = 13.sp, fontFamily = handFont,
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 10.dp)
        )

        FidlandToggleRow(
            label       = "Alarm",
            description = "Shows the next scheduled alarm and countdown in the pill when an alarm is " +
                    "set and within the display window. Dismiss or snooze directly from the pill.",
            checked     = alarm,
            font        = handFont,
            onChange    = {
                alarm = it
                editor.putBoolean("phs3_alarm", it).apply()
                restartFidlandService(context)
            }
        )

        FidlandToggleRow(
            label       = "Timer",
            description = "Activates when a countdown timer is running. Shows remaining time " +
                    "and a progress arc in the pill. Pause and cancel from the expanded panel.",
            checked     = timer,
            font        = handFont,
            onChange    = {
                timer = it
                editor.putBoolean("phs3_timer", it).apply()
                restartFidlandService(context)
            }
        )

        FidlandToggleRow(
            label       = "Flashlight",
            description = "Shows a torch indicator in the pill when the flashlight is active. " +
                    "Brightness level visible in the expanded panel.",
            checked     = torch,
            font        = handFont,
            onChange    = {
                torch = it
                editor.putBoolean("phs3_torch", it).apply()
                restartFidlandService(context)
            }
        )

        FidlandToggleRow(
            label       = "Voice Recorder",
            description = "Activates when a recording session is in progress. Shows recording duration " +
                    "and a live waveform indicator. Stop the recording directly from the pill.",
            permission  = "RECORD_AUDIO",
            checked     = recorder,
            font        = handFont,
            onChange    = {
                recorder = it
                editor.putBoolean("phs3_recorder", it).apply()
                restartFidlandService(context)
            }
        )

        FidlandToggleRow(
            label       = "Navigation",
            description = "Activates when turn-by-turn navigation is running in Google Maps or similar apps. " +
                    "Shows the next maneuver, distance, and street name in the pill.",
            permission  = "ACCESS_FINE_LOCATION",
            checked     = nav,
            font        = handFont,
            onChange    = {
                nav = it
                editor.putBoolean("phs3_nav", it).apply()
                restartFidlandService(context)
            }
        )

        FidlandToggleRow(
            label       = "Calendar",
            description = "Shows upcoming calendar events in the pill. Activates when an event is " +
                    "approaching within a configurable lead time.",
            permission  = "READ_CALENDAR",
            checked     = calendar,
            font        = handFont,
            onChange    = {
                calendar = it
                editor.putBoolean("phs3_calendar", it).apply()
                restartFidlandService(context)
            }
        )

        FidlandToggleRow(
            label       = "Weather",
            description = "Shows current weather conditions and temperature in the pill. " +
                    "Updates periodically based on device location.",
            permission  = "ACCESS_COARSE_LOCATION",
            checked     = weather,
            font        = handFont,
            onChange    = {
                weather = it
                editor.putBoolean("phs3_weather", it).apply()
                restartFidlandService(context)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Third-Party Integrations
        SubSectionHeader("Third-Party Integrations", handFont)
        Text(
            text     = "Driven by notification scraping. These entities read notifications from " +
                    "supported apps to detect active sessions.",
            fontSize = 13.sp, fontFamily = handFont,
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 10.dp)
        )

        FidlandToggleRow(
            label       = "Ride Hailing",
            description = "Detects active ride sessions from Uber, Ola, and Rapido via notification " +
                    "scraping. Shows driver ETA, ride phase (pre-ride / in-ride), and a progress " +
                    "arc to destination in the pill. Uses GPS to track pickup location.",
            permission  = "BIND_NOTIFICATION_LISTENER_SERVICE, ACCESS_FINE_LOCATION",
            checked     = ride,
            font        = handFont,
            onChange    = {
                ride = it
                editor.putBoolean("phs3_ride", it).apply()
                restartFidlandService(context)
            }
        )

        FidlandToggleRow(
            label       = "Quick Commerce",
            description = "Detects active delivery orders from Swiggy, Zomato, Blinkit, and similar " +
                    "apps via notifications. Shows order status and ETA in the pill.",
            permission  = "BIND_NOTIFICATION_LISTENER_SERVICE",
            checked     = delivery,
            font        = handFont,
            onChange    = {
                delivery = it
                editor.putBoolean("phs3_delivery", it).apply()
                restartFidlandService(context)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
        FidlandDivider()

        // ── 5. Quick Settings ────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(
            title    = "Quick Settings",
            subtitle = "A swipeable tile panel in the expanded dashboard for deep-linking to system " +
                    "settings pages. Android 10+ removed the ability to toggle WiFi and Bluetooth " +
                    "programmatically, so this opens the relevant settings panel instead.",
            bodyFont = bodyFont,
            handFont = handFont
        )

        FidlandToggleRow(
            label       = "Enable Quick Settings",
            description = "Adds a Quick Settings tab to the expanded dashboard. Currently contains " +
                    "WiFi, Bluetooth, and Torch tiles. Developer options and other system links " +
                    "will be added here.",
            checked     = quickSettings,
            font        = handFont,
            onChange    = {
                quickSettings = it
                editor.putBoolean("quick_settings", it).apply()
                restartFidlandService(context)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
        FidlandDivider()

        // ── Permissions ──────────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(24.dp))
        PermissionsSection(bodyFont = bodyFont, handFont = handFont)

        Spacer(modifier = Modifier.height(80.dp))
    }
}

// ---------------------------------------------------------------------------
// Permissions section
// ---------------------------------------------------------------------------

@Composable
private fun PermissionsSection(bodyFont: FontFamily, handFont: FontFamily) {
    Text(
        text       = "Permissions",
        fontSize   = 26.sp,
        fontFamily = bodyFont,
        color      = MaterialTheme.colorScheme.onSurface,
        modifier   = Modifier.padding(bottom = 4.dp)
    )
    Text(
        text       = "A frank account of what Fidland asks for and why.",
        fontSize   = 15.sp,
        fontFamily = handFont,
        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier   = Modifier.padding(bottom = 16.dp)
    )

    val permissions = listOf(
        PermissionEntry(
            name       = "READ_PHONE_STATE / READ_CALL_LOG",
            usedBy     = listOf("Call"),
            sardonic   = "So that an app sitting on top of your screen can tell you're on a call. " +
                    "Android won't let you detect an incoming call without knowing your phone state, " +
                    "which apparently also comes with the ability to read your IMEI. The call log is " +
                    "there for caller name lookup. We're not interested in your 47 missed calls from mum."
        ),
        PermissionEntry(
            name       = "ACCESS_FINE_LOCATION",
            usedBy     = listOf("Navigation", "Ride Hailing"),
            sardonic   = "Navigation needs to know where you are to show where you're going — " +
                    "fair enough. Ride Hailing uses it to capture your pickup coordinates when a " +
                    "ride is detected, since the app itself won't tell us. Location is polled at " +
                    "10–15 second intervals during active sessions only and stopped the moment " +
                    "the session ends."
        ),
        PermissionEntry(
            name       = "ACCESS_COARSE_LOCATION",
            usedBy     = listOf("Weather"),
            sardonic   = "Weather needs to know what city you're in to tell you it's hot. " +
                    "Coarse location — cell tower / WiFi level — is enough for that. " +
                    "We don't need your exact coordinates to confirm that yes, it is 38°C and yes, " +
                    "you should have stayed inside."
        ),
        PermissionEntry(
            name       = "BIND_NOTIFICATION_LISTENER_SERVICE",
            usedBy     = listOf("Ride Hailing", "Quick Commerce"),
            sardonic   = "This is the big one. A Notification Listener reads every notification " +
                    "that arrives on your device — from every app — in real time. Fidland uses it " +
                    "exclusively to detect ride and delivery sessions by scanning notifications from " +
                    "Uber, Ola, Rapido, Swiggy, Zomato, and Blinkit. It does not store notification " +
                    "content, log it, or act on anything outside those specific package names. " +
                    "That said, we understand if you'd rather not grant an overlay app a firehose " +
                    "into your notification stream. You can leave these two disabled."
        ),
        PermissionEntry(
            name       = "RECORD_AUDIO",
            usedBy     = listOf("Voice Recorder"),
            sardonic   = "The microphone. Required to record audio, which is precisely what the voice " +
                    "recorder does. The permission is only active during an explicit recording session " +
                    "that you start. We are not running a background audio feed. That would be both " +
                    "illegal and deeply unoriginal."
        ),
        PermissionEntry(
            name       = "READ_CALENDAR",
            usedBy     = listOf("Calendar"),
            sardonic   = "Read access to your calendar events so the pill can tell you something " +
                    "important is in 10 minutes. Fidland reads event title, time, and location only. " +
                    "Your therapist appointments are safe. Probably."
        ),
        PermissionEntry(
            name       = "ACCESS_WIFI_STATE / BLUETOOTH / NFC",
            usedBy     = listOf("Comms"),
            sardonic   = "Needed to read the current state of your connectivity hardware — whether WiFi, " +
                    "Bluetooth, or NFC is on. That's it. We're not connecting to anything, " +
                    "pairing with anything, or scanning for networks. Just checking if the light is on."
        )
    )

    permissions.forEach { entry ->
        PermissionCard(entry = entry, handFont = handFont)
        Spacer(modifier = Modifier.height(12.dp))
    }
}

private data class PermissionEntry(
    val name     : String,
    val usedBy   : List<String>,
    val sardonic : String
)

@Composable
private fun PermissionCard(entry: PermissionEntry, handFont: FontFamily) {
    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text       = entry.name,
                fontSize   = 14.sp,
                fontFamily = handFont,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text       = "Used by: ${entry.usedBy.joinToString(", ")}",
                fontSize   = 12.sp,
                fontFamily = handFont,
                color      = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text       = entry.sardonic,
                fontSize   = 13.sp,
                fontFamily = handFont,
                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                lineHeight = 19.sp
            )
        }
    }
}

// ---------------------------------------------------------------------------
// App chips
// ---------------------------------------------------------------------------

@Composable
private fun AppChipRow(
    context      : Context,
    packageNames : Set<String>,
    font         : FontFamily,
    onRemove     : (String) -> Unit
) {
    val pm = context.packageManager
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(packageNames.toList()) { pkg ->
            val info = try { pm.getApplicationInfo(pkg, 0) } catch (e: Exception) { null }
            if (info != null) {
                val label = pm.getApplicationLabel(info).toString()
                val icon  = pm.getApplicationIcon(info)
                AppChip(
                    label    = label,
                    icon     = icon,
                    font     = font,
                    onRemove = { onRemove(pkg) }
                )
            }
        }
    }
}

@Composable
private fun AppChip(
    label    : String,
    icon     : android.graphics.drawable.Drawable,
    font     : FontFamily,
    onRemove : () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Image(
                bitmap             = icon.toBitmap(32, 32).asImageBitmap(),
                contentDescription = label,
                modifier           = Modifier.size(20.dp)
            )
            Text(
                text       = label,
                fontSize   = 13.sp,
                fontFamily = font,
                color      = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines   = 1
            )
            Icon(
                imageVector        = Icons.Default.Close,
                contentDescription = "Remove $label",
                modifier           = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable { onRemove() },
                tint               = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Stepper
// ---------------------------------------------------------------------------

@Composable
private fun StepperItem(
    label    : String,
    value    : Int,
    min      : Int,
    max      : Int,
    font     : FontFamily,
    modifier : Modifier = Modifier,
    onChange : (Int) -> Unit
) {
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier              = Modifier.padding(12.dp),
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Text(
                text       = label,
                fontSize   = 14.sp,
                fontFamily = font,
                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(
                    onClick  = { if (value > min) onChange(value - 1) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text(
                        text       = "−",
                        fontSize   = 20.sp,
                        fontFamily = font,
                        color      = if (value > min)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
                Text(
                    text       = "$value",
                    fontSize   = 22.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick  = { if (value < max) onChange(value + 1) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text(
                        text       = "+",
                        fontSize   = 20.sp,
                        fontFamily = font,
                        color      = if (value < max)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// App picker dialog — unchanged logic, cleaner layout
// ---------------------------------------------------------------------------

@Composable
private fun AppPickerDialog(
    context           : Context,
    currentlySelected : Set<String>,
    onDismiss         : () -> Unit,
    onConfirm         : (Set<String>) -> Unit
) {
    val installedApps = remember {
        context.packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { context.packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { context.packageManager.getApplicationLabel(it).toString().lowercase() }
    }

    var selected by remember { mutableStateOf(currentlySelected.toSet()) }
    val handFont = FontFamily(Font(R.font.font_handwriting))

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape    = RoundedCornerShape(16.dp),
            color    = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text       = "Select Apps",
                    fontSize   = 20.sp,
                    fontFamily = handFont,
                    color      = MaterialTheme.colorScheme.onSurface,
                    modifier   = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text       = "${selected.size} selected",
                    fontSize   = 13.sp,
                    fontFamily = handFont,
                    color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier   = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(installedApps) { appInfo ->
                        val pkg       = appInfo.packageName
                        val label     = context.packageManager.getApplicationLabel(appInfo).toString()
                        val icon      = context.packageManager.getApplicationIcon(appInfo)
                        val isChecked = pkg in selected

                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (isChecked) selected - pkg else selected + pkg
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap             = icon.toBitmap(36, 36).asImageBitmap(),
                                contentDescription = label,
                                modifier           = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text     = label,
                                fontSize = 14.sp,
                                fontFamily = handFont,
                                color    = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked         = isChecked,
                                onCheckedChange = {
                                    selected = if (it) selected + pkg else selected - pkg
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", fontFamily = handFont)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(selected) }) {
                        Text("Save", fontFamily = handFont)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable components
// ---------------------------------------------------------------------------

@Composable
private fun FidlandToggleRow(
    label       : String,
    description : String,
    checked     : Boolean,
    font        : FontFamily,
    permission  : String? = null,
    onChange    : (Boolean) -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Switch(
            checked         = checked,
            onCheckedChange = onChange,
            modifier        = Modifier.padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = label,
                fontSize   = 17.sp,
                fontFamily = font,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text       = description,
                fontSize   = 13.sp,
                fontFamily = font,
                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                lineHeight = 18.sp,
                modifier   = Modifier.padding(top = 2.dp)
            )
            if (permission != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text       = "⚠ Requires $permission",
                    fontSize   = 12.sp,
                    fontFamily = font,
                    color      = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title    : String,
    subtitle : String,
    bodyFont : FontFamily,
    handFont : FontFamily
) {
    Text(
        text       = title,
        fontSize   = 28.sp,
        fontFamily = bodyFont,
        color      = MaterialTheme.colorScheme.onSurface,
        modifier   = Modifier.padding(bottom = 4.dp)
    )
    Text(
        text       = subtitle,
        fontSize   = 14.sp,
        fontFamily = handFont,
        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        lineHeight = 20.sp,
        modifier   = Modifier.padding(bottom = 14.dp)
    )
}

@Composable
private fun SubSectionHeader(title: String, font: FontFamily) {
    Text(
        text       = title,
        fontSize   = 18.sp,
        fontFamily = font,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        modifier   = Modifier.padding(top = 4.dp, bottom = 6.dp)
    )
}

// ---------------------------------------------------------------------------
// Football — API listing + daily request counter
// ---------------------------------------------------------------------------

private data class FootballApiEntry(
    val sourceId : String,
    val name     : String,
    val role     : String,
    val keyNote  : String,
)

/** The four APIs the Football tracker can call, in the same order shown elsewhere in this section. */
private val FOOTBALL_APIS = listOf(
    FootballApiEntry(
        sourceId = FootballApiRequestLog.SOURCE_FOOTBALL_DATA_ORG,
        name     = "football-data.org",
        role     = "Schedule of record for all eight tracked competitions",
        keyNote  = "Requires a free API key"
    ),
    FootballApiEntry(
        sourceId = FootballApiRequestLog.SOURCE_OPENLIGADB,
        name     = "OpenLigaDB",
        role     = "Near-real-time live fallback, Bundesliga",
        keyNote  = "No key required"
    ),
    FootballApiEntry(
        sourceId = FootballApiRequestLog.SOURCE_API_FOOTBALL,
        name     = "api-football (api-sports.io)",
        role     = "Optional high-fidelity overlay — goal scorers, cards, substitutions",
        keyNote  = "Optional, requires a free API key"
    ),
    FootballApiEntry(
        sourceId = FootballApiRequestLog.SOURCE_THESPORTSDB,
        name     = "TheSportsDB",
        role     = "On-demand team badge / crest backfill only",
        keyNote  = "No key required"
    ),
)

/** Flat list of every API the Football tracker can call, with its role and key requirement. */
@Composable
private fun FootballApiListing(handFont: FontFamily) {
    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            FOOTBALL_APIS.forEachIndexed { index, api ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text       = "•",
                        fontSize   = 14.sp,
                        fontFamily = handFont,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier   = Modifier.padding(end = 8.dp)
                    )
                    Column {
                        Text(
                            text       = api.name,
                            fontSize   = 14.sp,
                            fontFamily = handFont,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text       = "${api.role} · ${api.keyNote}",
                            fontSize   = 12.sp,
                            fontFamily = handFont,
                            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                if (index != FOOTBALL_APIS.lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

/**
 * Shows how many requests have been sent to each football API today (UTC),
 * plus the combined total. Backed by [FootballApiRequestLog], which every
 * source (football-data.org, OpenLigaDB, api-football, TheSportsDB) writes to
 * right before firing an HTTP call — so this reflects real network activity,
 * not just polling-loop ticks.
 */
@Composable
private fun FootballRequestCounterCard(
    snapshot : FootballApiRequestLog.TodaySnapshot,
    handFont : FontFamily
) {
    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = "Requests sent today",
                    fontSize   = 14.sp,
                    fontFamily = handFont,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text       = "${snapshot.total}",
                    fontSize   = 20.sp,
                    fontFamily = handFont,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text       = "Across all four APIs · resets at 00:00 UTC (today: ${snapshot.dayUtcDate})",
                fontSize   = 11.sp,
                fontFamily = handFont,
                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            FOOTBALL_APIS.forEach { api ->
                val count = snapshot.perSource[api.sourceId] ?: 0
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text       = api.name,
                        fontSize   = 13.sp,
                        fontFamily = handFont,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Text(
                        text       = "$count",
                        fontSize   = 13.sp,
                        fontFamily = handFont,
                        fontWeight = FontWeight.Medium,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FidlandDivider() {
    HorizontalDivider(
        color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
        thickness = 1.dp
    )
}

fun restartFidlandService(context: Context) {
    val intent = Intent(context, FidlandService::class.java)
    context.stopService(intent)
    Handler(Looper.getMainLooper()).postDelayed({
        context.startService(intent)
    }, 300L)
}