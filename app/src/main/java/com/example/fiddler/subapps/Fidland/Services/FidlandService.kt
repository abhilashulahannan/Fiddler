package com.example.fiddler.subapps.Fidland.service

import android.app.Service
import android.content.Context
import android.content.SharedPreferences
import android.os.IBinder
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.fiddler.subapps.Fidland.apps.AppsTopic
import com.example.fiddler.subapps.Fidland.manager.PlaylistTopicManager
import com.example.fiddler.subapps.Fidland.manager.QuickSettingsManager
import com.example.fiddler.subapps.Fidland.manager.SegmentSwitcher
import com.example.fiddler.subapps.Fidland.music.MusicTopicCompose
import com.example.fiddler.subapps.Fidland.music.SpotifyListener
import com.example.fiddler.subapps.Fidland.music.YTMusicListener
import com.example.fiddler.subapps.Fidland.playlist.PlaylistTopicCompose
import com.example.fiddler.subapps.Fidland.quicksettings.QuickSettingsTopicCompose
import com.example.fiddler.subapps.Fidland.ui.FidlandRootUI
import com.example.fiddler.subapps.Fidland.ui.PillPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

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

    private val pillPhase  = mutableStateOf(PillPhase.CIRCLE)
    private val isExpanded = mutableStateOf(false)
    private var touchBoxViewRef: ComposeView? = null

    private lateinit var prefs: SharedPreferences

    override fun onBind(intent: android.content.Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("fidland_prefs", Context.MODE_PRIVATE)

        quickSettingsManager = QuickSettingsManager(serviceScope)
        playlistManager      = PlaylistTopicManager(serviceScope)
        segmentSwitcher      = SegmentSwitcher(
            segmentCount     = 5,
            scope            = serviceScope,
            switchIntervalMs = 5000L
        )

        // Pass serviceScope so SpotifyListener can run its polling retry coroutine
        spotifyListener  = SpotifyListener(this, serviceScope).also { it.start() }
        ytMusicListener  = YTMusicListener(this, serviceScope).also { it.start() }

        pillPhase.value  = resolveInitialPhase()

        val tabs = buildTabList()

        val pillView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FidlandService)
            setViewTreeSavedStateRegistryOwner(this@FidlandService)
            setContent {
                FidlandRootUI(
                    pillPhase       = pillPhase.value,
                    segmentSwitcher = segmentSwitcher,
                    tabs            = tabs,
                    isExpanded      = isExpanded.value,
                    onSwipeDown     = { isExpanded.value = true },
                    onCollapse      = {
                        isExpanded.value = false
                        touchBoxViewRef?.let { overlayManager.addTouchBoxView(it) }
                    }
                )
            }
        }

        val touchBoxView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FidlandService)
            setViewTreeSavedStateRegistryOwner(this@FidlandService)
            setContent {
                PhaseTouchBox(
                    onSwipeDown = {
                        isExpanded.value = true
                        overlayManager.removeTouchBoxView()
                    }
                )
            }
        }

        overlayManager = OverlayManagerCompose(this, windowManager)
        overlayManager.addPillView(pillView)
        overlayManager.addTouchBoxView(touchBoxView)
        touchBoxViewRef = touchBoxView

        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        spotifyListener.stop()
        ytMusicListener.stop()
        overlayManager.removeAll()
        serviceScope.cancel()
    }

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
    }
}