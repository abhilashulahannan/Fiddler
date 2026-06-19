package com.example.fiddler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.ui.theme.FiddlerTheme
import com.example.fiddler.subapps.home.HomeScreen
import com.example.fiddler.subapps.ntspd.NtspdScreen
import com.example.fiddler.subapps.rngtns.RngtnsScreen
import com.example.fiddler.subapps.SecGrp.SecGrpScreen
import com.example.fiddler.subapps.Fidland.FidlandScreen
import com.example.fiddler.subapps.debugging.DebuggingScreen
import com.example.fiddler.core.SubAppState
import kotlinx.coroutines.launch

data class AppItem(val label: String, val iconRes: Int)

class MainActivity : ComponentActivity() {

    private val apps = listOf(
        AppItem("Home",           R.drawable.doodlehome),
        AppItem("Internet",       R.drawable.doodlenet),
        AppItem("Audio",          R.drawable.doodlemusic),
        AppItem("Fidland",        R.drawable.doodlefidland),
        AppItem("Security Group", R.drawable.doodlesecgrp),
        AppItem("Debugging",      R.drawable.doodlebug)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SubAppState.init(this) // seed fidlandEnabled from SharedPreferences
        setContent {
            FiddlerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                    MainScreen(apps = apps)
                }
            }
        }
    }
}

@Composable
fun MainScreen(apps: List<AppItem>) {
    val pagerState = rememberPagerState(pageCount = { apps.size })
    val scope = rememberCoroutineScope()

    // Sidebar height calculated once, only recomputed on configuration change (rotation etc.)
    val configuration = LocalConfiguration.current
    val sidebarHeight: Dp = remember(configuration) {
        (configuration.screenHeightDp * 0.65f).dp
    }

    // Only recompose sidebar when the settled page actually changes,
    // not on every fractional scroll offset during a swipe
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }

    Row(modifier = Modifier.fillMaxSize()) {

        // Sidebar
        Column(
            modifier = Modifier
                .width(70.dp)
                .height(sidebarHeight)
                .align(Alignment.CenterVertically)
                .background(Color.LightGray, RoundedCornerShape(50.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            apps.forEachIndexed { index, app ->
                SidebarItem(
                    item = app,
                    isActive = currentPage == index,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                )
                if (index != apps.lastIndex) {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        // Pager — only keeps 1 neighbour page alive on either side
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) { page ->
            when (apps[page].label) {
                "Home"           -> HomeScreen()
                "Internet"       -> NtspdScreen()
                "Audio"          -> RngtnsScreen()
                "Fidland"        -> FidlandScreen(context = LocalContext.current)
                "Security Group" -> SecGrpScreen()
                "Debugging"      -> DebuggingScreen()
                else -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = apps[page].label, fontSize = 32.sp)
                }
            }
        }
    }
}

@Composable
fun SidebarItem(item: AppItem, isActive: Boolean, onClick: () -> Unit) {

    // Icon size animates smoothly instead of jumping
    val iconSize: Dp by animateDpAsState(
        targetValue = if (isActive) 45.dp else 35.dp,
        animationSpec = tween(durationMillis = 200),
        label = "sidebar_icon_size_${item.label}"
    )

    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = item.iconRes),
            contentDescription = item.label,
            modifier = Modifier.size(iconSize),
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