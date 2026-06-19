package com.example.fiddler.subapps.Fidland.music.lyrics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * In-memory + on-disk lyrics state for the currently playing track, exposed
 * to [com.example.fiddler.subapps.Fidland.phs3.music.MusicPhs3Handler]'s State 5
 * lyrics panel (component 4).
 */
sealed class LyricsState {
    object Loading : LyricsState()
    object NotFound : LyricsState()
    object Instrumental : LyricsState()

    /** Time-synced lines available — drives the scrolling/highlight UI. */
    data class Synced(val lines: List<LyricLine>) : LyricsState()

    /** Only plain (un-synced) lyrics available — render as static text. */
    data class Plain(val text: String) : LyricsState()
}

/**
 * Cache-first lyrics lookup backed by LRCLIB, plus play-count tracking that
 * builds the "1000 most listened songs" offline database described in the
 * music phs3 spec.
 *
 * ── Lifecycle / call pattern ───────────────────────────────────────────────────
 *   1. On every new track, [com.example.fiddler.subapps.Fidland.music.MusicPhs3Trigger]
 *      calls [recordPlay] AND [prefetchLyrics] once, on its own long-lived
 *      service scope. This is what actually builds the cache: lyrics start
 *      downloading the moment a track starts playing, completely independent
 *      of whether the State 5 lyrics panel is ever opened, and survives
 *      [com.example.fiddler.subapps.Fidland.phs3.Phs3Manager]'s 5-second
 *      pill rotation — that scope is tied to the service, not to whether the
 *      Music handler is currently the one being displayed.
 *   2. When the user opens the lyrics panel (State 5), [SyncedLyricsView]
 *      calls [getLyrics] — by that point the background prefetch from step 1
 *      has very likely already finished, so this is normally an instant
 *      cache hit. If it's still in flight, [getLyrics] joins the same
 *      in-flight request via [inFlight] rather than starting a second one.
 *
 * ── Why this split exists ─────────────────────────────────────────────────────
 *   The lyrics panel composable is disposed every time the pill rotates away
 *   from the Music handler. A fetch driven only by that composable's
 *   LaunchedEffect gets cancelled mid-request on every rotation (LRCLIB calls
 *   can take several seconds — see [LrcLibApi.TIMEOUT_MS] — comfortably
 *   longer than [com.example.fiddler.subapps.Fidland.phs3.Phs3Manager.ROTATION_INTERVAL_MS]),
 *   so it never completes AND never gets to write the cache row. Driving the
 *   fetch from the trigger's service-scoped coroutine instead means it keeps
 *   running across rotations, completes, and actually populates the cache —
 *   which is what lets the "1000 most listened songs" database build up.
 *
 * All public functions are suspend and safe to call from the main thread —
 * DB and network work is dispatched to Dispatchers.IO internally.
 */
class LyricsRepository(context: Context) {

    private val dao = LyricsDatabase.get(context).lyricsDao()

    /**
     * Guards against fetching the same song twice concurrently — e.g. the
     * background prefetch from [MusicPhs3Trigger] and a [getLyrics] call from
     * a freshly (re)composed lyrics panel landing at the same time. Keyed by
     * [keyFor]; the [Mutex] is removed from the map once its fetch completes.
     */
    private val inFlight = mutableMapOf<String, Mutex>()
    private val inFlightLock = Mutex()

    companion object {
        /** Cache cap — "1000 most listened songs" per the spec. */
        const val MAX_CACHED_SONGS = 1000

        @Volatile private var instance: LyricsRepository? = null

        fun get(context: Context): LyricsRepository =
            instance ?: synchronized(this) {
                instance ?: LyricsRepository(context.applicationContext).also { instance = it }
            }

        /**
         * Stable cache key for a (track, artist) pair.
         *
         * Normalizes case/whitespace and strips common YT-Music suffixes like
         * "(Official Video)", "(Lyrics)", "[Audio]", "- Topic" so that minor
         * title variations from the same underlying song collapse to the same
         * key (and therefore the same cache entry / play-count bucket).
         */
        fun keyFor(trackName: String, artistName: String): String {
            val cleanTrack = normalize(trackName)
            val cleanArtist = normalize(artistName)
            val raw = "$cleanTrack|$cleanArtist"
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }

        private val NOISE_SUFFIXES = listOf(
            Regex("""\(official\s*(music\s*)?video\)""", RegexOption.IGNORE_CASE),
            Regex("""\(official\s*audio\)""", RegexOption.IGNORE_CASE),
            Regex("""\(lyrics?\)""", RegexOption.IGNORE_CASE),
            Regex("""\(lyric\s*video\)""", RegexOption.IGNORE_CASE),
            Regex("""\[official\s*(music\s*)?video]""", RegexOption.IGNORE_CASE),
            Regex("""\[audio]""", RegexOption.IGNORE_CASE),
            Regex("""-\s*topic$""", RegexOption.IGNORE_CASE),
            Regex("""\(audio\)""", RegexOption.IGNORE_CASE),
            Regex("""\(visualizer\)""", RegexOption.IGNORE_CASE),
        )

        private fun normalize(s: String): String {
            var out = s.trim().lowercase()
            for (re in NOISE_SUFFIXES) out = out.replace(re, "")
            return out.replace(Regex("""\s+"""), " ").trim()
        }
    }

    /**
     * Records one "listen" of [trackName]/[artistName] for the most-listened
     * ranking, then trims the cache back to [MAX_CACHED_SONGS] if it grew
     * beyond that. Cheap — call this once per track change.
     */
    suspend fun recordPlay(
        trackName: String,
        artistName: String,
        albumName: String,
        durationSec: Int
    ) = withContext(Dispatchers.IO) {
        if (trackName.isBlank()) return@withContext
        val key = keyFor(trackName, artistName)
        dao.recordPlay(
            songKey = key,
            trackName = trackName,
            artistName = artistName,
            albumName = albumName,
            durationSec = durationSec,
            now = System.currentTimeMillis()
        )
        dao.evictLeastPlayedBeyond(MAX_CACHED_SONGS)
    }

    /**
     * Fire-and-forget background fetch: if [trackName]/[artistName] isn't
     * already cached, fetch it from LRCLIB and cache the result (including a
     * negative "not found" entry) — exactly like [getLyrics], but without
     * returning a [LyricsState] to render, since nothing is on screen yet.
     *
     * Intended to be called from a long-lived scope (the music phs3 trigger's
     * service scope) as soon as a track starts playing, so the fetch has the
     * track's entire play duration to complete rather than a single 5-second
     * pill-rotation window. A no-op if the song is already cached with a
     * resolved [CachedLyricsEntity.lyricsFetchedAt].
     */
    suspend fun prefetchLyrics(
        trackName: String,
        artistName: String,
        albumName: String = "",
        durationSec: Int = 0
    ) = withContext(Dispatchers.IO) {
        if (trackName.isBlank()) return@withContext
        val key = keyFor(trackName, artistName)
        if ((dao.get(key)?.lyricsFetchedAt ?: 0L) > 0L) return@withContext // already cached either way

        fetchAndCache(key, trackName, artistName, albumName, durationSec)
    }

    /**
     * Returns lyrics for [trackName]/[artistName], serving from the local
     * cache when available (works fully offline) and falling back to a
     * LRCLIB network fetch otherwise.
     *
     * On a successful or "not found" network result, the outcome is cached
     * (subject to the [MAX_CACHED_SONGS] cap) so subsequent lookups — even
     * offline — return instantly. If a [prefetchLyrics] call for the same
     * song is already in flight (the common case — the background trigger
     * usually starts well before the panel is opened), this joins that same
     * fetch instead of starting a redundant one.
     */
    suspend fun getLyrics(
        trackName: String,
        artistName: String,
        albumName: String = "",
        durationSec: Int = 0
    ): LyricsState = withContext(Dispatchers.IO) {
        if (trackName.isBlank()) return@withContext LyricsState.NotFound

        val key = keyFor(trackName, artistName)
        val cached = dao.get(key)

        if (cached != null && cached.lyricsFetchedAt > 0L) {
            return@withContext cached.toLyricsState()
        }

        fetchAndCache(key, trackName, artistName, albumName, durationSec)
        dao.get(key)?.toLyricsState() ?: LyricsState.NotFound
    }

    /**
     * Performs the actual LRCLIB fetch + cache write for [key], joining an
     * already-in-flight fetch for the same key if one exists rather than
     * duplicating the network call. Used by both [getLyrics] and
     * [prefetchLyrics] so a background prefetch and a panel-open lookup for
     * the same song never race each other.
     */
    private suspend fun fetchAndCache(
        key: String,
        trackName: String,
        artistName: String,
        albumName: String,
        durationSec: Int
    ) {
        val mutex = inFlightLock.withLock {
            inFlight.getOrPut(key) { Mutex() }
        }

        mutex.withLock {
            try {
                // Re-check now that we hold the lock — whoever got here first
                // (prefetch or panel) may have already finished the fetch.
                val cached = dao.get(key)
                if (cached != null && cached.lyricsFetchedAt > 0L) return

                val result = try {
                    LrcLibApi.fetch(trackName, artistName, albumName, durationSec)
                } catch (e: Exception) {
                    null
                }

                if (result == null) {
                    // No network, or LRCLIB has nothing — cache a negative result so we
                    // don't hammer the API on every subsequent attempt for this song.
                    // If we're offline (and have no prior row), don't poison the cache —
                    // just leave it unresolved so a later online attempt can still try.
                    if (cached != null || isLikelyOffline()) {
                        dao.upsertLyrics(
                            songKey = key,
                            trackName = trackName,
                            artistName = artistName,
                            albumName = albumName,
                            durationSec = durationSec,
                            plainLyrics = null,
                            syncedLyrics = null,
                            isInstrumental = false,
                            hasLyrics = false,
                            now = System.currentTimeMillis()
                        )
                    }
                    return
                }

                val hasLyrics = result.isInstrumental ||
                        !result.syncedLyrics.isNullOrBlank() ||
                        !result.plainLyrics.isNullOrBlank()

                dao.upsertLyrics(
                    songKey = key,
                    trackName = result.trackName.ifBlank { trackName },
                    artistName = result.artistName.ifBlank { artistName },
                    albumName = result.albumName.ifBlank { albumName },
                    durationSec = if (result.durationSec > 0) result.durationSec else durationSec,
                    plainLyrics = result.plainLyrics,
                    syncedLyrics = result.syncedLyrics,
                    isInstrumental = result.isInstrumental,
                    hasLyrics = hasLyrics,
                    now = System.currentTimeMillis()
                )
                dao.evictLeastPlayedBeyond(MAX_CACHED_SONGS)
            } finally {
                inFlightLock.withLock {
                    // Only remove if no one re-inserted a new mutex for this key
                    // while we were running (harmless either way, just tidy-up).
                    if (inFlight[key] === mutex) inFlight.remove(key)
                }
            }
        }
    }

    private fun CachedLyricsEntity.toLyricsState(): LyricsState = when {
        !hasLyrics -> LyricsState.NotFound
        isInstrumental && syncedLyrics.isNullOrBlank() && plainLyrics.isNullOrBlank() ->
            LyricsState.Instrumental
        !syncedLyrics.isNullOrBlank() -> LyricsState.Synced(parseLrc(syncedLyrics!!))
        !plainLyrics.isNullOrBlank() -> LyricsState.Plain(plainLyrics!!)
        else -> LyricsState.NotFound
    }

    /**
     * Best-effort connectivity check so we avoid writing a permanent
     * "not found" cache entry just because the device was briefly offline.
     * Not wired to ConnectivityManager to keep this class permission-light;
     * a failed [LrcLibApi.fetch] with no prior cache row is treated the same
     * as "offline" — see [fetchAndCache].
     */
    private fun isLikelyOffline(): Boolean = false
}