package com.example.fiddler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
    var isSidebarExpanded by remember { mutableStateOf(false) }
    val collapsedWidth: Dp = 60.dp
    val expandedWidth: Dp = 180.dp

    val sidebarWidth by animateDpAsState(targetValue = if (isSidebarExpanded) expandedWidth else collapsedWidth)
    val arrowRotation by animateFloatAsState(targetValue = if (isSidebarExpanded) 180f else 0f)

    val pagerState = rememberPagerState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Row(modifier = Modifier.fillMaxSize()) {
        // Sidebar
        Column(
            modifier = Modifier
                .width(sidebarWidth)
                .fillMaxHeight()
                .background(Color.LightGray, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (dragAmount > 50 && !isSidebarExpanded) isSidebarExpanded = true
                        if (dragAmount < -50 && isSidebarExpanded) isSidebarExpanded = false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.5f))

            apps.forEachIndexed { index, app ->
                SidebarItem(
                    item = app,
                    isExpanded = isSidebarExpanded,
                    isActive = pagerState.currentPage == index
                ) {
                    scope.launch { pagerState.animateScrollToPage(index) }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = { isSidebarExpanded = !isSidebarExpanded }) {
                Icon(
                    painter = painterResource(id = R.drawable.doodlearrow),
                    contentDescription = "Toggle",
                    modifier = Modifier.rotate(arrowRotation),
                    tint = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Pager content
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

@Composable
fun SidebarItem(item: AppItem, isExpanded: Boolean, isActive: Boolean, onClick: () -> Unit) {
    val iconAlpha by animateFloatAsState(if (isActive) 1f else 0.5f)
    val textAlpha by animateFloatAsState(if (isActive && isExpanded) 1f else 0f)
    val iconSize by animateDpAsState(if (isExpanded) 40.dp else 50.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = item.iconRes),
            contentDescription = item.label,
            modifier = Modifier.size(iconSize),
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = item.label,
            color = Color.Black.copy(alpha = textAlpha),
            fontSize = 20.sp
        )
    }
}

// Placeholder composables for other screens
@Composable
fun NtspdScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Internet / Ntspd Screen", fontSize = 32.sp)
    }
}

@Composable
fun RngtnsScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Audio / Ringtones Screen", fontSize = 32.sp)
    }
}

@Composable
fun SecGrpScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Security Groups Screen", fontSize = 32.sp)
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
        AppItem("Security Group", R.drawable.doodlesecgrp)
    )
    FiddlerTheme {
        MainScreen(apps = apps)
    }
}
