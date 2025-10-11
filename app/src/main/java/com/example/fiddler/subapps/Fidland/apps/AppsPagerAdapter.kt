package com.example.fiddler.subapps.Fidland.apps

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

// Placeholder adapter for AppsTopic
class AppsPagerAdapter(private val context: Context) : RecyclerView.Adapter<AppsPagerAdapter.AppsPageViewHolder>() {

    // Placeholder: empty list
    private val pages = listOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppsPageViewHolder {
        val view = View(context) // simple placeholder view
        return AppsPageViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppsPageViewHolder, position: Int) {
        // no-op
    }

    override fun getItemCount(): Int = pages.size

    class AppsPageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
