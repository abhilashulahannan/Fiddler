package com.example.fiddler.subapps.Fidland.quicksettings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import com.example.fiddler.subapps.Fidland.TopicPage
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.example.fiddler.R


data class QuickSettingItemCompose(
    val iconRes: Int,
    val title: String
)

class QuickSettingsTopicCompose(context: Context) : TopicPage(context) {

    private val items = listOf(
        QuickSettingItemCompose(R.drawable.wifi_on, "Wi-Fi"),
        QuickSettingItemCompose(R.drawable.blue, "Bluetooth"),
        QuickSettingItemCompose(R.drawable.flashlight_on, "Flashlight")
        // add more tiles as needed
    )

    private var currentPage by mutableStateOf(0)

    @Composable
    override fun Content() {
        val pagerState = rememberPagerState(initialPage = currentPage)

        HorizontalPager(
            count = items.size,
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp) // adjust height as needed
        ) { page ->
            val item = items[page]
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .background(Color.DarkGray, RoundedCornerShape(8.dp))
                    .clickable { onQuickSettingClicked(item) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(item.iconRes),
                    contentDescription = item.title,
                    modifier = Modifier.size(32.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }

        currentPage = pagerState.currentPage
    }

    override fun onSwipeLeft() {
        currentPage = (currentPage + 1).coerceAtMost(items.size - 1)
    }

    override fun onSwipeRight() {
        currentPage = (currentPage - 1).coerceAtLeast(0)
    }

    private fun onQuickSettingClicked(item: QuickSettingItemCompose) {
        // Handle click logic here
    }
}
