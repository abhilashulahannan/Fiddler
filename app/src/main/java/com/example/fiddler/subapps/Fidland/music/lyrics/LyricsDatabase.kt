package com.example.fiddler.subapps.Fidland.music.lyrics

import android.content.Context
import androidx.room.*

/**
 * Cached lyrics + listen-count for one (track, artist) pair.
 *
 * [songKey] is the primary key — see [LyricsRepository.keyFor] for how it's
 * derived. It must be stable across app restarts and (reasonably) across the
 * minor title variations YT Music / Spotify report for the same song, since
 * it's the cache hit/miss key.
 *
 * [syncedLyrics] / [plainLyrics] store the raw LRC / plain text exactly as
 * returned by LRCLIB so re-parsing is cheap and the original is preserved.
 * Both may be null if LRCLIB has no lyrics for this track (a "negative"
 * cache entry) — [hasLyrics] distinguishes "we checked, there are none" from
 * "we haven't checked yet" (the row simply won't exist in that case).
 *
 * [playCount] drives the "1000 most listened songs" cache cap — see
 * [LyricsDao.evictLeastPlayedBeyond]. [lastPlayedAt] is a tiebreaker for
 * eviction (least-recently-played goes first when play counts are equal).
 */
@Entity(tableName = "cached_lyrics")
data class CachedLyricsEntity(
    @PrimaryKey val songKey: String,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSec: Int,
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val isInstrumental: Boolean,
    val hasLyrics: Boolean,
    val playCount: Int,
    val lastPlayedAt: Long,
    val lyricsFetchedAt: Long
)

@Dao
interface LyricsDao {

    @Query("SELECT * FROM cached_lyrics WHERE songKey = :songKey LIMIT 1")
    suspend fun get(songKey: String): CachedLyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedLyricsEntity)

    @Query("SELECT COUNT(*) FROM cached_lyrics")
    suspend fun count(): Int

    /**
     * Increments the play count / updates lastPlayedAt for a song.
     * If the song has no cached-lyrics row yet, creates a placeholder row with
     * hasLyrics = false so it still participates in the "most listened" ranking
     * — its lyrics can be filled in later by [upsertLyrics] once fetched.
     */
    @Query(
        """
        UPDATE cached_lyrics
        SET playCount = playCount + 1, lastPlayedAt = :now
        WHERE songKey = :songKey
        """
    )
    suspend fun bumpPlayCount(songKey: String, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaceholder(entity: CachedLyricsEntity): Long

    /**
     * Records one "listen" for [songKey]. Creates a placeholder row (playCount=1)
     * if none exists yet, otherwise increments the existing row's playCount.
     */
    @Transaction
    suspend fun recordPlay(
        songKey: String,
        trackName: String,
        artistName: String,
        albumName: String,
        durationSec: Int,
        now: Long
    ) {
        val updated = bumpPlayCount(songKey, now)
        if (updated == 0) {
            insertPlaceholder(
                CachedLyricsEntity(
                    songKey = songKey,
                    trackName = trackName,
                    artistName = artistName,
                    albumName = albumName,
                    durationSec = durationSec,
                    plainLyrics = null,
                    syncedLyrics = null,
                    isInstrumental = false,
                    hasLyrics = false,
                    playCount = 1,
                    lastPlayedAt = now,
                    lyricsFetchedAt = 0L
                )
            )
        }
    }

    /**
     * Stores fetched lyrics for an existing row (created via [recordPlay]),
     * preserving its playCount/lastPlayedAt. If the row somehow doesn't exist
     * (e.g. lyrics fetched before any play was recorded), inserts a fresh row
     * with playCount = 1.
     */
    @Transaction
    suspend fun upsertLyrics(
        songKey: String,
        trackName: String,
        artistName: String,
        albumName: String,
        durationSec: Int,
        plainLyrics: String?,
        syncedLyrics: String?,
        isInstrumental: Boolean,
        hasLyrics: Boolean,
        now: Long
    ) {
        val existing = get(songKey)
        upsert(
            CachedLyricsEntity(
                songKey = songKey,
                trackName = trackName,
                artistName = artistName,
                albumName = albumName,
                durationSec = durationSec,
                plainLyrics = plainLyrics,
                syncedLyrics = syncedLyrics,
                isInstrumental = isInstrumental,
                hasLyrics = hasLyrics,
                playCount = existing?.playCount ?: 1,
                lastPlayedAt = existing?.lastPlayedAt ?: now,
                lyricsFetchedAt = now
            )
        )
    }

    /**
     * Caps the cache at [maxRows] entries by deleting the least-listened
     * (then least-recently-played) rows once that limit is exceeded.
     * Called after every [recordPlay] so the cache self-trims as the app
     * builds its "most listened 1000 songs" database over time.
     */
    @Query(
        """
        DELETE FROM cached_lyrics
        WHERE songKey IN (
            SELECT songKey FROM cached_lyrics
            ORDER BY playCount ASC, lastPlayedAt ASC
            LIMIT MAX(0, (SELECT COUNT(*) FROM cached_lyrics) - :maxRows)
        )
        """
    )
    suspend fun evictLeastPlayedBeyond(maxRows: Int)
}

/**
 * Single Room database for the lyrics cache.
 *
 * Opened lazily via [get] using the application context, so any caller
 * (LyricsRepository, MusicPhs3Trigger, etc.) can obtain the same instance
 * without an Application class / DI framework.
 */
@Database(entities = [CachedLyricsEntity::class], version = 1, exportSchema = false)
abstract class LyricsDatabase : RoomDatabase() {
    abstract fun lyricsDao(): LyricsDao

    companion object {
        @Volatile private var instance: LyricsDatabase? = null

        fun get(context: Context): LyricsDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LyricsDatabase::class.java,
                    "fidland_lyrics_cache.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
