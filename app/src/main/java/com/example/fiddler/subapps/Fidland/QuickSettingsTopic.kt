package com.example.fiddler.subapps.Fidland

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fiddler.R

class QuickSettingsTopic(context: Context) : TopicPage(context) {
    private val view: View = LayoutInflater.from(context).inflate(R.layout.phase4_quicksettings, null)
    private val recyclerView: RecyclerView = view.findViewById(R.id.quicksettings_recycler)

    init {
        recyclerView.layoutManager = GridLayoutManager(context, 4)
        recyclerView.adapter = QuickSettingsAdapter(context)
    }

    override fun getView(): View = view
}
