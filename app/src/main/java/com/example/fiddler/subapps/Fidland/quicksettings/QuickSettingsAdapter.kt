package com.example.fiddler.subapps.fidland.quicksettings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fiddler.R

// Data model for each quick setting tile
data class QuickSettingItem(
    val iconRes: Int,
    val title: String
)

// Callback interface to forward clicks to QuickSettingsTopic
interface QuickSettingsCallback {
    fun onQuickSettingClicked(item: QuickSettingItem)
}

class QuickSettingsAdapter(
    private val items: List<QuickSettingItem>,
    private val callback: QuickSettingsCallback
) : RecyclerView.Adapter<QuickSettingsAdapter.QuickSettingsViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickSettingsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.quicksetting_item, parent, false)
        return QuickSettingsViewHolder(view)
    }

    override fun onBindViewHolder(holder: QuickSettingsViewHolder, position: Int) {
        val item = items[position]
        holder.icon.setImageResource(item.iconRes)
        holder.title.text = item.title

        holder.itemView.setOnClickListener {
            callback.onQuickSettingClicked(item)
        }
    }

    override fun getItemCount(): Int = items.size

    class QuickSettingsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.quicksetting_icon)
        val title: TextView = itemView.findViewById(R.id.quicksetting_title)
    }
}
