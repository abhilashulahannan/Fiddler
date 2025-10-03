package com.example.fiddler

import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.WindowCompat
import androidx.viewpager2.widget.ViewPager2

class MainActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2
    private lateinit var sidebar: LinearLayout
    private lateinit var arrowButton: AppCompatImageView

    private var startX = 0f
    private var isSidebarExpanded = false

    private val collapsedWidth = 60    // dp
    private val expandedWidth = 180    // dp
    private val iconSize = 60          // dp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(resources.getColor(R.color.white, theme))
        setContentView(R.layout.activity_main)

        pager = findViewById(R.id.mainPager)
        sidebar = findViewById(R.id.sidebar_container)
        pager.adapter = MainPagerAdapter(this)

        setupStatusBar()
        setupSidebar()
        setupSidebarSwipe()
    }

    private fun setupStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
    }

    private fun setupSidebar() {
        // Spacer to vertically center the app items
        val spacerTop = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.5f
            )
        }
        sidebar.addView(spacerTop)

        val apps = listOf(
            Triple("Home", R.drawable.doodlehome, 0),
            Triple("Internet", R.drawable.doodlenet, 1),
            Triple("Audio", R.drawable.doodlemusic, 2),
            Triple("Fidland", R.drawable.doodlefidland, 3),
            Triple("Security Group", R.drawable.doodlesecgrp, 4)
        )

        apps.forEach { (label, iconRes, index) ->
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (iconSize * resources.displayMetrics.density).toInt()
                ).apply { setMargins(8, 12, 8, 12) }
                gravity = Gravity.CENTER_VERTICAL
            }

            val icon = AppCompatImageView(this).apply {
                setImageResource(iconRes)
                contentDescription = label
                val sizePx = (iconSize * resources.displayMetrics.density * 0.75f).toInt()
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            }

            val text = AppCompatTextView(this).apply {
                this.text = label
                textSize = 20f
                setTextColor(resources.getColor(R.color.black, theme))
                setPadding(8, 0, 0, 0)
                alpha = 0f
                visibility = View.VISIBLE
            }

            itemLayout.addView(icon)
            itemLayout.addView(text)
            itemLayout.tag = Pair(icon, text)
            itemLayout.setOnClickListener { pager.setCurrentItem(index, true) }

            sidebar.addView(itemLayout)
        }

        // Spacer to push arrow to bottom
        val spacerBottom = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.5f
            )
        }
        sidebar.addView(spacerBottom)

        // Arrow button aligned to left
        arrowButton = AppCompatImageView(this).apply {
            setImageResource(R.drawable.doodlearrow)
            layoutParams = LinearLayout.LayoutParams(
                (iconSize * resources.displayMetrics.density * 0.75).toInt(),
                (iconSize * resources.displayMetrics.density * 0.75).toInt()
            ).apply {
                gravity = Gravity.START
                leftMargin = 8
                topMargin = 16
            }
            setColorFilter(resources.getColor(R.color.black, theme)) // always dark
            alpha = 1f
            setOnClickListener {
                if (isSidebarExpanded) collapseSidebar() else expandSidebar()
            }
        }
        sidebar.addView(arrowButton)

        highlightSidebarIcon(0)

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                highlightSidebarIcon(position)
            }
        })
    }

    private fun highlightSidebarIcon(activeIndex: Int) {
        var visibleIndex = 0
        for (i in 0 until sidebar.childCount) {
            val child = sidebar.getChildAt(i)
            val tag = child.tag
            if (tag is Pair<*, *>) {
                val icon = tag.first as AppCompatImageView
                val text = tag.second as AppCompatTextView
                icon.alpha = if (visibleIndex == activeIndex) 1f else 0.5f
                text.alpha = if (visibleIndex == activeIndex && isSidebarExpanded) 1f else 0f
                visibleIndex++
            }
        }
    }

    private fun setupSidebarSwipe() {
        sidebar.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> startX = event.x
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.x - startX
                    if (!isSidebarExpanded && deltaX > 50) expandSidebar()
                    else if (isSidebarExpanded && deltaX < -50) collapseSidebar()
                }
            }
            true
        }
    }

    private fun expandSidebar() {
        val widthPx = (expandedWidth * resources.displayMetrics.density).toInt()
        sidebar.layoutParams.width = widthPx
        sidebar.requestLayout()

        for (i in 0 until sidebar.childCount) {
            val child = sidebar.getChildAt(i)
            val tag = child.tag
            if (tag is Pair<*, *>) {
                val icon = tag.first as AppCompatImageView
                val text = tag.second as AppCompatTextView
                icon.animate().scaleX(0.5f).scaleY(0.5f).setDuration(200).start()
                text.animate().alpha(1f).setDuration(200).start()
            }
        }
        isSidebarExpanded = true
        arrowButton.animate().rotation(180f).setDuration(200).start()
        highlightSidebarIcon(pager.currentItem)
    }

    private fun collapseSidebar() {
        val widthPx = (collapsedWidth * resources.displayMetrics.density).toInt()
        sidebar.layoutParams.width = widthPx
        sidebar.requestLayout()

        for (i in 0 until sidebar.childCount) {
            val child = sidebar.getChildAt(i)
            val tag = child.tag
            if (tag is Pair<*, *>) {
                val icon = tag.first as AppCompatImageView
                val text = tag.second as AppCompatTextView
                icon.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                text.animate().alpha(0f).setDuration(200).start()
            }
        }
        isSidebarExpanded = false
        arrowButton.animate().rotation(0f).setDuration(200).start()
        highlightSidebarIcon(pager.currentItem)
    }
}
