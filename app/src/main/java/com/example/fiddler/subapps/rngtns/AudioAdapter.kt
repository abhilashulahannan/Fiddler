package com.example.fiddler.subapps.rngtns

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fiddler.R

class AudioAdapter(private val audioFiles: List<AudioFile>) :
    RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {

    inner class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkBox: CheckBox = itemView.findViewById(R.id.checkbox)
        val fileName: TextView = itemView.findViewById(R.id.filename)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_audio, parent, false)
        return AudioViewHolder(view)
    }

    override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
        val audio = audioFiles[position]
        holder.fileName.text = audio.docFile.name
        holder.checkBox.isChecked = audio.keepInRotation

        holder.fileName.typeface = if (audio.keepInRotation) {
            holder.fileName.context.resources.getFont(R.font.font_handwriting)
        } else {
            holder.fileName.context.resources.getFont(R.font.font_body)
        }

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            audio.keepInRotation = isChecked
            holder.fileName.typeface = if (isChecked) {
                holder.fileName.context.resources.getFont(R.font.font_handwriting)
            } else {
                holder.fileName.context.resources.getFont(R.font.font_body)
            }
        }
    }

    override fun getItemCount(): Int = audioFiles.size
}
