package com.example.fiddler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.ui.theme.FiddlerTheme
import com.google.accompanist.pager.*
import kotlinx.coroutines.launch
import com.example.fiddler.subapps.home.HomeScreen
import com.example.fiddler.subapps.ntspd.NtspdScreen
import com.example.fiddler.subapps.rngtns.RngtnsScreen
import com.example.fiddler.subapps.SecGrp.SecGrpScreen
import com.example.fiddler.subapps.Fidland.FidlandScreen

data class AppItem(val label: String, val iconRes: Int)

@OptIn(ExperimentalPagerApi::class)
class MainActivity : ComponentActivity() {

    private val apps = listOf(
        AppItem("Home", R.drawable.doodlehome),
        AppItem("Internet", R.drawable.doodlenet),
        AppItem("Audio", R.drawable.doodlemusic),
        AppItem("Fidland", R.drawable.doodlefidland),
        AppItem("Security Group", R.drawable.doodlesecgrp)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FiddlerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                    MainScreen(apps = apps)
                }
            }
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun MainScreen(apps: List<AppItem>) {
    val sidebarWidth = 70.dp
    val pagerState = rememberPagerState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val sidebarHeight = screenHeight * 0.65f

    Row(modifier = Modifier.fillMaxSize()) {

        // Sidebar (fixed collapsed version)
        Column(
            modifier = Modifier
                .width(sidebarWidth)
                .height(sidebarHeight)
                .align(Alignment.CenterVertically)
                .background(Color.LightGray, RoundedCornerShape(50.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            apps.forEachIndexed { index, app ->
                SidebarItem(
                    item = app,
                    isActive = pagerState.currentPage == index
                ) {
                    scope.launch { pagerState.animateScrollToPage(index) }
                }
                if (index != apps.lastIndex) {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        // Main content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            VerticalPager(
                count = apps.size,
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (apps[page].label) {
                    "Home" -> HomeScreen()
                    "Internet" -> NtspdScreen()
                    "Audio" -> RngtnsScreen()
                    "Fidland" -> FidlandScreen(context = context)
                    "Security Group" -> SecGrpScreen()
                    else -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Page: ${apps[page].label}", fontSize = 32.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SidebarItem(item: AppItem, isActive: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = item.iconRes),
            contentDescription = item.label,
            modifier = Modifier.size(if (isActive) 45.dp else 35.dp),
            tint = if (isActive) Color.Black else Color.DarkGray
        )
        if (isActive) {
            Text(
                text = item.label,

                color = Color.Black,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(55.dp),
                softWrap = true,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewMainScreen() {
    val apps = listOf(
        AppItem("Home", R.drawable.doodlehome),
        AppItem("Internet", R.drawable.doodlenet),
        AppItem("Audio", R.drawable.doodlemusic),
        AppItem("Fidland", R.drawable.doodlefidland),
        AppItem("Secure Group", R.drawable.doodlesecgrp)
    )
    FiddlerTheme {
        MainScreen(apps = apps)
    }
}
