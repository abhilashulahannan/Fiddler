package com.example.fiddler.subapps.Fidland.music

import android.content.Context
import com.example.fiddler.subapps.Fidland.music.lyrics.LyricsRepository
import com.example.fiddler.subapps.Fidland.phs3.music.MusicPhs3Handler
import com.example.fiddler.subapps.Fidland.service.FidlandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MusicPhs3Trigger(
    private val context: Context,
    private val scope: CoroutineScope,
    private val service: FidlandService,
) {
    private var job: Job? = null
    private val lyricsRepo by lazy { LyricsRepository.get(context) }

    private var lastRecordedTrack: Triple<String, String, String>? = null

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
                        service.deactivatePhs3("Music")
                        return@collectLatest
                    }

                    service.activatePhs3(
                        MusicPhs3Handler(
                            packageName = playing.packageName,
                            context     = context,
                        )
                    )

                    val trackId = Triple(playing.packageName, playing.songTitle, playing.artistName)
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
    }
}