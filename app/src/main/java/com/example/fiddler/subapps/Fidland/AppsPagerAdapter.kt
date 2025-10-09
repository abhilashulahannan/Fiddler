package com.example.fiddler.subapps.Fidland

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fiddler.R

class AppsPagerAdapter(private val context: Context) : RecyclerView.Adapter<AppsPagerAdapter.PageVH>() {

    private val apps = mutableListOf<String>() // dynamically add apps from prefs

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val recyclerView = RecyclerView(context)
        val layoutManager = GridLayoutManager(context, 4) // Example columns, read from prefs
        recyclerView.layoutManager = layoutManager
        return PageVH(recyclerView)
    }

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        val recyclerView = holder.itemView as RecyclerView
        recyclerView.adapter = AppGridAdapter(context, apps)
    }

    override fun getItemCount(): Int {
        // number of pages = ceil(apps.size / (rows*columns))
        val rows = 3
        val columns = 4
        return Math.ceil(apps.size.toDouble() / (rows * columns)).toInt()
    }

    inner class PageVH(itemView: View) : RecyclerView.ViewHolder(itemView)
}
