package com.example.fiddler

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.example.fiddler.subapps.home.FragmentHome
import com.example.fiddler.subapps.ntspd.FragmentNtspd
import com.example.fiddler.subapps.rngtns.RngtnsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var mainContent: LinearLayout
    private lateinit var mainScrollView: ScrollView
    private lateinit var sidebar: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainContent = findViewById(R.id.main_content)
        mainScrollView = findViewById(R.id.main_scrollview)
        sidebar = findViewById(R.id.sidebar_container)

        // Inflate Home and sub-app fragments as sections
        val inflater = LayoutInflater.from(this)
        mainContent.addView(inflater.inflate(R.layout.fragment_home, mainContent, false))
        mainContent.addView(inflater.inflate(R.layout.fragment_ntspd, mainContent, false))
        mainContent.addView(inflater.inflate(R.layout.fragment_rngtns, mainContent, false))

        setupSidebar()
    }

    private fun setupSidebar() {
        val apps = listOf(
            Triple("Home", R.drawable.doodlehome, 0),
            Triple("Net Speed", R.drawable.doodlenet, 1),
            Triple("Ringtones", R.drawable.doodlemusic, 2)
        )

        apps.forEach { (label, iconRes, index) ->
            val icon = androidx.appcompat.widget.AppCompatImageView(this).apply {
                setImageResource(iconRes)
                contentDescription = label
                setPadding(8, 8, 8, 8)
                setOnClickListener {
                    // Scroll to the corresponding section
                    val targetView = mainContent.getChildAt(index)
                    mainScrollView.smoothScrollTo(0, targetView.top)
                }
            }
            sidebar.addView(icon)
        }
    }
}
