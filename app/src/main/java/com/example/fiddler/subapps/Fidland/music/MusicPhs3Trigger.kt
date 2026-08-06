package com.example.fiddler.subapps.Fidland.music

import android.content.Context
import com.example.fiddler.subapps.Fidland.music.lyrics.LyricsRepository
import com.example.fiddler.subapps.Fidland.phs3.Phs3Priority
import com.example.fiddler.subapps.Fidland.phs3.Phs3Scheduler
import com.example.fiddler.subapps.Fidland.phs3.PriorityClass
import com.example.fiddler.subapps.Fidland.phs3.music.MusicPhs3Handler
import com.example.fiddler.subapps.Fidland.service.FidlandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * ── §B6/§B7 wiring (this pass) — home Dominant/85 + Special-Condition/55 ────
 * Design doc §B7: home Dominant, sub-score **85** (confirmed, leaning-high —
 * ⚠ per §B8 #7, placed above Navigation's 80 as an editorial merge choice,
 * not an explicit original lean; kept as documented). Submitted on every
 * qualifying emission, continuous while Dominant — Music isn't wired into
 * [com.example.fiddler.subapps.Fidland.phs3.Phs3Manager.policyOf], so like
 * Ring Mode it just rides continuous rotation rather than EVENT_DRIVEN.
 * On top of that, a genuine **track change** promotes to SPECIAL_CONDITION
 * for [Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS] (the doc's 5-8s
 * convention, sub-score **55**), then automatically reverts to the home-85
 * bid via [Phs3Priority.fallback] — same shape as Ring Mode's mode-change
 * promotion.
 * • Change detection is **not new infra** — [trackId] already distinguishes
 *   "new track" from "same track, different flow emission" for the lyrics-
 *   prefetch/recordPlay logic below; this just reuses that same comparison
 *   against [lastRecordedTrack] to decide whether to promote, per §B7's
 *   "detected off the existing trigger signal, no new detection needed."
 * • The very first qualification (silence → playing) is deliberately *not*
 *   treated as a track change for promotion purposes — same guard Ring Mode
 *   uses for its first push — so playback starting doesn't itself trigger
 *   the 5-8s Special-Condition window; only an actual mid-session track
 *   change does.
 *
 * ── Block placement (§B2) ─────────────────────────────────────────────────
 * The two-block split (equalizer = dynamic, title/artist = right/fixed) is
 * wired on [MusicPhs3Handler] itself via [com.example.fiddler.subapps.Fidland
 * .phs3.Phs3Handler.hasSecondaryBlock] — see its class doc. Nothing in this
 * trigger needed to change for that; it only constructs the handler and
 * submits priority bids.
 */
class MusicPhs3Trigger(
    private val context: Context,
    private val scope: CoroutineScope,
    private val service: FidlandService,
) {
    private var job: Job? = null
    private val lyricsRepo by lazy { LyricsRepository.get(context) }

    private var lastRecordedTrack: Triple<String, String, String>? = null

    /** §B7's confirmed home-class bid, rebuilt fresh per emission with the current handler instance. */
    private fun homePriority(handler: MusicPhs3Handler) = Phs3Priority(
        handler       = handler,
        priorityClass = PriorityClass.DOMINANT,
        subScore      = 85,
    )

    fun start() {
        job = scope.launch {
            MusicAppsRepository.appsFlow
                .map { apps -> apps.firstOrNull { it.isPlaying && it.songTitle.isNotBlank() } }
                .distinctUntilChanged { old, new ->
                    old?.packageName == new?.packageName &&
                            old?.songTitle == new?.songTitle &&
                            old?.artistName == new?.artistName &&
                            old?.isPlaying == new?.isPlaying
                }
                .collectLatest { playing ->
                    if (playing == null) {
                        service.phs3Manager.scheduler.withdraw("Music")
                        service.deactivatePhs3("Music")
                        return@collectLatest
                    }

                    val handler = MusicPhs3Handler(
                        packageName = playing.packageName,
                        context     = context,
                    )
                    service.activatePhs3(handler)

                    val trackId = Triple(playing.packageName, playing.songTitle, playing.artistName)
                    val home = homePriority(handler)
                    if (lastRecordedTrack != null && trackId != lastRecordedTrack) {
                        // Genuine mid-session track change — promote to Special
                        // Condition for the standard 5-8s dwell, then auto-revert
                        // to the home-85 bid via fallback. See class doc.
                        service.phs3Manager.scheduler.submit(
                            Phs3Priority(
                                handler       = handler,
                                priorityClass = PriorityClass.SPECIAL_CONDITION,
                                subScore      = 55,
                                holdMs        = Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS,
                                fallback      = home,
                            )
                        )
                    } else {
                        service.phs3Manager.scheduler.submit(home)
                    }

                    if (trackId != lastRecordedTrack) {
                        lastRecordedTrack = trackId
                        lyricsRepo.recordPlay(
                            trackName   = playing.songTitle,
                            artistName  = playing.artistName,
                            albumName   = playing.albumName,
                            durationSec = playing.totalMs / 1000,
                        )

                        // Start loading lyrics now, in the background, on this
                        // trigger's own service-scoped coroutine — NOT from the
                        // State 5 lyrics panel's composable. The panel is torn
                        // down and rebuilt every time Phs3Manager rotates the
                        // pill away from Music (every ROTATION_INTERVAL_MS when
                        // other handlers are also qualified), which would cancel
                        // a fetch started there before LRCLIB ever responds.
                        // Launched as a separate child job (not collectLatest's
                        // own coroutine) so a slow/failed lyrics fetch can never
                        // delay or cancel processing of the next track change.
                        scope.launch {
                            lyricsRepo.prefetchLyrics(
                                trackName   = playing.songTitle,
                                artistName  = playing.artistName,
                                albumName   = playing.albumName,
                                durationSec = playing.totalMs / 1000,
                            )
                        }
                    }
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        service.phs3Manager.scheduler.withdraw("Music")
    }
}