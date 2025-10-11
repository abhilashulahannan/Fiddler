package com.example.fiddler.subapps.Fidland.playlist

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.fiddler.R

class PlaylistPagerAdapter(private val context: Context) : RecyclerView.Adapter<PlaylistPagerAdapter.PageVH>() {

    private val apps = listOf("Spotify", "YT Music") // Replace with dynamic apps list

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val view = LayoutInflater.from(context).inflate(R.layout.page_playlist, parent, false)
        return PageVH(view)
    }

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        // Bind playlist of apps[position]
    }

    override fun getItemCount(): Int = apps.size

    inner class PageVH(itemView: View) : RecyclerView.ViewHolder(itemView)
}
