package com.example.fiddler.subapps.Fidland.service

import android.app.Service
import android.content.Context
import android.content.SharedPreferences
import android.os.IBinder
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.fiddler.subapps.Fidland.NotificationListenerService
import com.example.fiddler.subapps.Fidland.apps.AppsTopic
import com.example.fiddler.subapps.Fidland.manager.PlaylistTopicManager
import com.example.fiddler.subapps.Fidland.manager.QuickSettingsManager
import com.example.fiddler.subapps.Fidland.manager.SegmentSwitcher
import com.example.fiddler.subapps.Fidland.music.MusicPhs3Trigger
import com.example.fiddler.subapps.Fidland.music.MusicTopicCompose
import com.example.fiddler.subapps.Fidland.music.SpotifyListener
import com.example.fiddler.subapps.Fidland.music.YTMusicListener
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import com.example.fiddler.subapps.Fidland.phs3.Phs3Manager
import com.example.fiddler.subapps.Fidland.phs3.alarm.AlarmPhs3Trigger
import com.example.fiddler.subapps.Fidland.phs3.download.DownloadPhs3Trigger
import com.example.fiddler.subapps.Fidland.phs3.flashlight.FlashlightPhs3Trigger
import com.example.fiddler.subapps.Fidland.phs3.football.FootballPhs3Handler
import com.example.fiddler.subapps.Fidland.phs3.football.FootballPhs3Trigger
import com.example.fiddler.subapps.Fidland.phs3.football.FootballTopicCompose
import com.example.fiddler.subapps.Fidland.phs3.music.MusicPhs3Handler
import com.example.fiddler.subapps.Fidland.phs3.navigation.NavigationPhs3Trigger
import com.example.fiddler.subapps.Fidland.phs3.record.RecordPhs3Trigger
import com.example.fiddler.subapps.Fidland.phs3.record.RecorderNotificationSource
import com.example.fiddler.subapps.Fidland.playlist.PlaylistTopicCompose
import com.example.fiddler.subapps.Fidland.quicksettings.QuickSettingsTopicCompose
import com.example.fiddler.subapps.Fidland.ui.FidlandRootUI
import com.example.fiddler.subapps.Fidland.ui.PillPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.fiddler.subapps.Fidland.phs3.idle.IdleThoughtsHandler
import com.example.fiddler.subapps.Fidland.phs3.weather.WeatherPhs3Trigger
import com.example.fiddler.subapps.Fidland.phs3.call.CallPhs3Trigger
import com.example.fiddler.subapps.Fidland.phs3.ringmode.RingmodePhs3Trigger
import com.example.fiddler.subapps.Fidland.phs3.timer.TimerPhs3Trigger



class FidlandService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var overlayManager: OverlayManagerCompose

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var quickSettingsManager: QuickSettingsManager
    private lateinit var playlistManager: PlaylistTopicManager
    private lateinit var segmentSwitcher: SegmentSwitcher

    private lateinit var spotifyListener: SpotifyListener
    private lateinit var ytMusicListener: YTMusicListener
    private lateinit var musicPhs3Trigger: MusicPhs3Trigger

    private lateinit var flashlightTrigger: FlashlightPhs3Trigger
    private lateinit var navigationTrigger: NavigationPhs3Trigger
    private lateinit var alarmTrigger: AlarmPhs3Trigger
    private lateinit var footballTrigger: FootballPhs3Trigger
    private lateinit var downloadTrigger: DownloadPhs3Trigger

    // ── Recording ──────────────────────────────────────────────────────────────
    private lateinit var recorderSource: RecorderNotificationSource
    private lateinit var recordTrigger:  RecordPhs3Trigger

    private lateinit var weatherTrigger: WeatherPhs3Trigger

    private lateinit var callTrigger: CallPhs3Trigger

    private lateinit var ringmodeTrigger: RingmodePhs3Trigger

    private lateinit var timerTrigger: TimerPhs3Trigger


    val phs3Manager = Phs3Manager(serviceScope)

    private var hideJob: Job? = null

    // ── Pill state ─────────────────────────────────────────────────────────────
    //
    // pillPhase: the current structural phase of the island (States 1-5).
    // isExpanded: true while State 4 (dashboard) is open. Kept separate so
    //   FidlandRootUI can derive effectivePhase = DASHBOARD when true.
    //
    // gestureStartPhase: snapshot of pillPhase taken at the moment a drag
    //   gesture begins (onDragStart in PhaseTouchBox). Used by onSwipeUp to
    //   determine whether the gesture originated in States 1-2-3 (→ hide)
    //   or in State 5 (→ collapse only, no hide). Without this snapshot a
    //   State-5 collapse (→ 1-2-3) would continue to trigger the hide because
    //   by the time the threshold is crossed, pillPhase.value is already 1-2-3.
    private val pillPhase         = mutableStateOf(PillPhase.CIRCLE)
    private val isExpanded        = mutableStateOf(false)
    private val pillVisible       = mutableStateOf(true)   // drives slide-out/in animation
    private var gestureStartPhase = PillPhase.CIRCLE

    private var touchBoxViewRef: ComposeView? = null

    private fun setPillPhase(newPhase: PillPhase) {
        pillPhase.value = newPhase
        overlayManager.repositionTouchBoxForPhase(newPhase)
    }

    val activePhs3Handler = mutableStateOf<Phs3Handler?>(null)
    val qualifiedPhs3Handlers = mutableStateOf<List<Phs3Handler>>(emptyList())

    private lateinit var prefs: SharedPreferences

    override fun onBind(intent: android.content.Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        // TYPE_ACCESSIBILITY_OVERLAY only works through a WindowManager sourced
        // from the connected AccessibilityService's own Context — it carries the
        // accessibility window token. A WindowManager from this plain Service's
        // own context has no such token and addView() will throw
        // WindowManager.BadTokenException for that window type. See
        // FidlandAccessibilityService for details.
        //
        // If the accessibility service isn't connected yet (user hasn't enabled
        // it, or the system hasn't finished binding it), fall back to this
        // service's own WindowManager. OverlayManagerCompose.overlayType() also
        // falls back below O, but on O+ without the accessibility token this
        // fallback WindowManager can only safely add TYPE_APPLICATION_OVERLAY
        // windows (requires SYSTEM_ALERT_WINDOW), not TYPE_ACCESSIBILITY_OVERLAY.
        // PermissionsActivity should be gating pill startup on
        // hasFidlandAccessibilityPermission() already; this fallback just
        // prevents a hard crash if that gate is ever bypassed or racy.
        val accessibilityContext = FidlandAccessibilityService.instance
        windowManager = (accessibilityContext ?: this)
            .getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("fidland_prefs", Context.MODE_PRIVATE)

        quickSettingsManager = QuickSettingsManager(serviceScope)
        playlistManager      = PlaylistTopicManager(serviceScope)
        segmentSwitcher      = SegmentSwitcher(
            segmentCount     = 5,
            scope            = serviceScope,
            switchIntervalMs = 5000L
        )

        spotifyListener  = SpotifyListener(this, serviceScope).also { it.start() }
        ytMusicListener  = YTMusicListener(this, serviceScope).also { it.start() }
        if (prefs.getBoolean("music_phs3", false))
            musicPhs3Trigger = MusicPhs3Trigger(this, serviceScope, this).also { it.start() }

        flashlightTrigger = FlashlightPhs3Trigger(applicationContext, this)
        if (prefs.getBoolean("phs3_torch", false))
            flashlightTrigger.start()

        navigationTrigger = NavigationPhs3Trigger(serviceScope, this)
        if (prefs.getBoolean("phs3_nav", false))
            navigationTrigger.start()

        alarmTrigger = AlarmPhs3Trigger(this, serviceScope, this)
        if (prefs.getBoolean("phs3_alarm", false))
            alarmTrigger.start()

        // ── Weather trigger ────────────────────────────────────────────────────────
        weatherTrigger = WeatherPhs3Trigger(applicationContext, serviceScope, this)
        weatherTrigger.start()

        val footballEnabled  = prefs.getBoolean("football_enabled", false)
        val footballFdApiKey = prefs.getString("football_api_key", "") ?: ""
        val footballAfApiKey = prefs.getString("football_af_api_key", "") ?: ""
        footballTrigger = FootballPhs3Trigger(
            context  = this,
            scope    = serviceScope,
            service  = this,
            fdApiKey = footballFdApiKey,
            afApiKey = footballAfApiKey,
        )
        if (footballEnabled) footballTrigger.start()

        // ── Recording trigger ──────────────────────────────────────────────────
        recorderSource  = RecorderNotificationSource()
        NotificationListenerService.recorderSource = recorderSource
        recordTrigger   = RecordPhs3Trigger(
            scope   = serviceScope,
            source  = recorderSource,
            manager = phs3Manager,
            context = this,
        )
        if (prefs.getBoolean("phs3_recorder", false))
            recordTrigger.start()

        // ── Download trigger ───────────────────────────────────────────────────
        downloadTrigger = DownloadPhs3Trigger(this, serviceScope, this)
        NotificationListenerService.downloadSource = downloadTrigger.notificationSource
        downloadTrigger.start()

        callTrigger = CallPhs3Trigger(applicationContext, serviceScope, phs3Manager)
        callTrigger.start()

        ringmodeTrigger = RingmodePhs3Trigger(applicationContext, phs3Manager)
        ringmodeTrigger.start()

        timerTrigger = TimerPhs3Trigger(serviceScope, phs3Manager)
        timerTrigger.start()

        // ── Idle thoughts fallback ─────────────────────────────────────────────────
    // Always registered last so it sits at the back of the priority queue.
    // Visible only when every other phs3 handler has unregistered.
        phs3Manager.register(IdleThoughtsHandler())

        // ── Observe Phs3Manager → drive pill phase + activePhs3Handler ─────────
        serviceScope.launch {
            phs3Manager.qualifiedHandlers.collect { handlers ->
                qualifiedPhs3Handlers.value = handlers
            }
        }
        serviceScope.launch {
            phs3Manager.activeHandler.collect { handler ->
                activePhs3Handler.value = handler
                val netEnabled = prefs.getBoolean("network_traffic", false)
                // Only update the compact pill phase. Never override STATE5 or
                // DASHBOARD from here — those are driven by gesture events.
                if (pillPhase.value != PillPhase.STATE5 &&
                    pillPhase.value != PillPhase.DASHBOARD) {
                    setPillPhase(
                        when {
                            handler == null -> resolveInitialPhase()
                            netEnabled      -> PillPhase.BOTH_EXPANDED
                            else            -> PillPhase.RIGHT_EXPANDED
                        }
                    )
                }
            }
        }

        pillPhase.value = resolveInitialPhase()

        val tabs = buildTabList()

        // ── Pill view ──────────────────────────────────────────────────────────
        val pillView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FidlandService)
            setViewTreeSavedStateRegistryOwner(this@FidlandService)
            setContent {
                FidlandRootUI(
                    pillPhase             = pillPhase.value,
                    segmentSwitcher       = segmentSwitcher,
                    tabs                  = tabs,
                    isExpanded            = isExpanded.value,
                    activePhs3Handler     = activePhs3Handler.value,
                    qualifiedPhs3Handlers = qualifiedPhs3Handlers.value,
                    isRotationLocked  = phs3Manager.lockedState.collectAsState().value,
                    pillVisible       = pillVisible.value,
                    // Swipe-down on the pill itself (inside State 4) still works
                    // via DashboardTabHost's internal gesture — not needed here.
                    onSwipeDown       = { /* handled by touchbox */ },
                    onCollapse        = { collapseToCompact() },
                )
            }
        }

        // ── Touchbox view ──────────────────────────────────────────────────────
        val touchBoxView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FidlandService)
            setViewTreeSavedStateRegistryOwner(this@FidlandService)
            setContent {
                PhaseTouchBox(
                    onSwipeDown  = { handleSwipeDown() },
                    onSwipeUp    = { handleSwipeUp()   },
                    onLongPress  = { phs3Manager.lockRotation() },
                    onSwipeRight = { phs3Manager.cycleNext()     },
                    onSwipeLeft  = { phs3Manager.cyclePrevious() },
                    onDragStart  = { onTouchBoxDragStart()       },
                )
            }
        }

        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        overlayManager = OverlayManagerCompose(this, windowManager)
        overlayManager.addPillView(pillView)
        overlayManager.addTouchBoxView(touchBoxView, pillPhase.value)
        touchBoxViewRef = touchBoxView
    }

    // ── Gesture handlers ───────────────────────────────────────────────────────

    /**
     * Swipe-down logic (v1):
     *
     * From States 1-2-3:
     *   → Open State 5 if the active phs3 handler has State 5 content.
     *   → Otherwise open State 4 (dashboard) directly.
     *
     * From State 5:
     *   → Open State 4. Entity-aware: jump to the relevant dashboard tab
     *     based on which phs3 handler is active (music → Music tab,
     *     football → Football tab, etc.). FidlandRootUI / DashboardTabHost
     *     reads [initialDashboardTab] on expansion.
     *
     * State 4: touchbox is removed, this handler never fires from there.
     */
    private fun handleSwipeDown() {
        when (pillPhase.value) {
            PillPhase.STATE5 -> {
                // Entity-aware dashboard open — pick the right tab
                openDashboard(tabForActiveHandler())
            }
            else -> {
                // States 1-2-3: open State 5 if available, else dashboard
                val handler = activePhs3Handler.value
                if (handler != null && handler.hasState5Content()) {
                    setPillPhase(PillPhase.STATE5)
                } else {
                    openDashboard(tabForActiveHandler())
                }
            }
        }
    }

    /**
     * Swipe-up logic (v1):
     *
     * The gesture start phase is captured in [gestureStartPhase] by
     * [onTouchBoxDragStart], called from PhaseTouchBox's onDragStart.
     * This prevents a State-5 collapse (→ 1-2-3) from also triggering
     * the hide that should only happen when the drag began in States 1-2-3.
     *
     * State 5  → collapse to States 1-2-3. No hide.
     * States 1-2-3 (gesture started here) → hide pill for 5 s then restore.
     * State 4  → touchbox absent; swipe-up handled inside DashboardTabHost.
     */
    private fun handleSwipeUp() {
        when (gestureStartPhase) {
            PillPhase.STATE5 -> {
                // Collapse strip back to compact pill
                collapseToCompact()
            }
            PillPhase.CIRCLE,
            PillPhase.LEFT_EXPANDED,
            PillPhase.RIGHT_EXPANDED,
            PillPhase.BOTH_EXPANDED -> {
                // Animate pill off the top, pause 5 s, animate it back in.
                hideJob?.cancel()
                hideJob = serviceScope.launch {
                    pillVisible.value = false          // triggers slide-out animation
                    overlayManager.removeTouchBoxView()
                    delay(5_000L)
                    pillVisible.value = true           // triggers slide-in animation
                    val restorePhase = if (isExpanded.value) PillPhase.DASHBOARD else pillPhase.value
                    touchBoxViewRef?.let {
                        overlayManager.addTouchBoxView(it, restorePhase)
                    }
                }
            }
            // DASHBOARD: touchbox not present, should not reach here
            else -> Unit
        }
    }

    /**
     * Called from PhaseTouchBox's onDragStart to snapshot the phase at the
     * moment the finger touches down. Must be called before any drag delta
     * is processed so [gestureStartPhase] is always valid when [handleSwipeUp]
     * fires.
     *
     * Wired via the onDragStart lambda in the touchbox ComposeView above.
     * If PhaseTouchBox ever needs additional onDragStart work, add it there
     * and call this from the same lambda.
     */
    fun onTouchBoxDragStart() {
        gestureStartPhase = pillPhase.value
    }

    // ── Navigation helpers ─────────────────────────────────────────────────────

    /**
     * Opens State 4 (dashboard), optionally jumping to a specific tab.
     * Removes the touchbox for the duration of the expanded state.
     */
    private fun openDashboard(initialTab: String? = null) {
        // TODO: pass initialTab through to DashboardTabHost when tab-jump
        //       API is added. For now DashboardTabHost restores its last tab.
        isExpanded.value = true
        overlayManager.repositionTouchBoxForPhase(PillPhase.DASHBOARD)
        overlayManager.removeTouchBoxView()
    }

    /**
     * Collapses back to the appropriate compact pill state (1-2-3).
     * Re-attaches the touchbox at the correct position.
     * Called from DashboardTabHost (swipe-up in State 4) and from
     * [handleSwipeUp] (State 5 collapse).
     */
    private fun collapseToCompact() {
        isExpanded.value = false
        // Resolve which compact phase is correct right now
        val netEnabled = prefs.getBoolean("network_traffic", false)
        val hasPhs3    = activePhs3Handler.value != null
        val targetPhase = when {
            netEnabled && hasPhs3 -> PillPhase.BOTH_EXPANDED
            netEnabled            -> PillPhase.LEFT_EXPANDED
            hasPhs3               -> PillPhase.RIGHT_EXPANDED
            else                  -> PillPhase.CIRCLE
        }
        setPillPhase(targetPhase)
        touchBoxViewRef?.let { overlayManager.addTouchBoxView(it, targetPhase) }
    }

    /**
     * Returns the dashboard tab name that best matches the active phs3 handler,
     * so swipe-down from State 5 lands on the relevant tab.
     *
     * Returns null to let DashboardTabHost restore its last-used tab.
     */
    private fun tabForActiveHandler(): String? = when (activePhs3Handler.value) {
        is MusicPhs3Handler   -> "Music"
        is FootballPhs3Handler -> "Football"
        else                  -> null
    }

    // ── Phs3 API ──────────────────────────────────────────────────────────────

    fun activatePhs3(handler: Phs3Handler) = phs3Manager.register(handler)

    fun deactivatePhs3(label: String) = phs3Manager.unregister(label)

    fun deactivatePhs3() {
        activePhs3Handler.value?.label?.let { phs3Manager.unregister(it) }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun resolveInitialPhase(): PillPhase {
        val netEnabled = prefs.getBoolean("network_traffic", false)
        return if (netEnabled) PillPhase.LEFT_EXPANDED else PillPhase.CIRCLE
    }

    private fun buildTabList(): List<DashboardTab> = buildList {
        if (prefs.getBoolean("music_player", true))
            add(DashboardTab("Music")    { MusicTopicCompose(this@FidlandService).Content() })
        if (prefs.getBoolean("music_queue", true))
            add(DashboardTab("Queue")    { PlaylistTopicCompose(this@FidlandService).Content() })
        if (prefs.getBoolean("quick_settings", true))
            add(DashboardTab("Settings") { QuickSettingsTopicCompose(this@FidlandService).Content() })
        if (prefs.getInt("app_rows", 3) > 0)
            add(DashboardTab("Apps")     { AppsTopic(this@FidlandService).Content() })
        if (prefs.getBoolean("football_enabled", false))
            add(DashboardTab("Football") { FootballTopicCompose(this@FidlandService, footballTrigger.repo).Content() })
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        spotifyListener.stop()
        ytMusicListener.stop()
        if (::musicPhs3Trigger.isInitialized)   musicPhs3Trigger.stop()
        if (::callTrigger.isInitialized) callTrigger.stop()
        if (::ringmodeTrigger.isInitialized) ringmodeTrigger.stop()
        if (::flashlightTrigger.isInitialized)  flashlightTrigger.stop()
        if (::navigationTrigger.isInitialized)  navigationTrigger.stop()
        if (::alarmTrigger.isInitialized)       alarmTrigger.stop()
        if (::footballTrigger.isInitialized)    footballTrigger.stop()
        if (::recordTrigger.isInitialized) {
            recordTrigger.stop()
            NotificationListenerService.recorderSource = null
        if (::timerTrigger.isInitialized) timerTrigger.stop()

        if (::downloadTrigger.isInitialized) {
            downloadTrigger.stop()
            NotificationListenerService.downloadSource = null
        }
        if (::weatherTrigger.isInitialized) weatherTrigger.stop()
            hideJob?.cancel()
            overlayManager.removeAll()
            serviceScope.cancel()
        }  // closes onDestroy
    }  // closes FidlandService class
}