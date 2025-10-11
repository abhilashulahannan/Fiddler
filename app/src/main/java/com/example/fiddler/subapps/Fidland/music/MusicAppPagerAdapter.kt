package com.example.fiddler.subapps.Fidland.music

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fiddler.R

/**
 * Adapter for ViewPager2 to show multiple music apps in MusicTopic
 * Fully compatible with MusicAppsRepository and dynamic updates
 */
class MusicAppPagerAdapter(
    private val context: Context
) : RecyclerView.Adapter<MusicAppPagerAdapter.MusicAppViewHolder>() {

    // Backing list to ensure updateApp() works correctly
    private val musicApps = MusicAppsRepository.getAllApps().toMutableList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicAppViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.phase4_music, parent, false)
        return MusicAppViewHolder(view)
    }

    override fun onBindViewHolder(holder: MusicAppViewHolder, position: Int) {
        val app = musicApps[position]

        holder.songName.text = app.songTitle
        holder.artistName.text = app.artistName
        holder.albumName.text = app.albumName
        app.albumArtResId?.let { holder.albumArt.setImageResource(it) }

        // Play/Pause button
        holder.playPauseButton.setImageResource(
            if (app.isPlaying) R.drawable.pause else R.drawable.play
        )

        // SeekBar
        holder.seekBar.max = app.totalMs
        holder.seekBar.progress = app.currentMs

        // Click listeners are delegated to MusicTopic via parent fragment or activity
    }

    override fun getItemCount(): Int = musicApps.size

    class MusicAppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val songName: TextView = itemView.findViewById(R.id.txt_song_name)
        val artistName: TextView = itemView.findViewById(R.id.txt_artist_name)
        val albumName: TextView = itemView.findViewById(R.id.txt_album_name)
        val albumArt: ImageView = itemView.findViewById(R.id.img_album_art)
        val seekBar: SeekBar = itemView.findViewById(R.id.music_seekbar)
        val playPauseButton: ImageButton = itemView.findViewById(R.id.btn_play_pause)
        val nextButton: ImageButton = itemView.findViewById(R.id.btn_next)
        val prevButton: ImageButton = itemView.findViewById(R.id.btn_previous)
    }

    /**
     * Update a specific app and refresh its item in the ViewPager2
     */
    fun updateApp(updatedApp: MusicApp) {
        val index = musicApps.indexOfFirst { it.appPackage == updatedApp.appPackage }
        if (index != -1) {
            musicApps[index] = updatedApp
            notifyItemChanged(index)
        }
    }

    /**
     * Optional: refresh all apps (e.g., if list changed)
     */
    fun refreshAllApps() {
        musicApps.clear()
        musicApps.addAll(MusicAppsRepository.getAllApps())
        notifyDataSetChanged()
    }
}
