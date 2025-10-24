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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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

// ------------------ Extension Function ------------------
fun Modifier.clipToBounds(enabled: Boolean): Modifier {
    return if (enabled) this.clipToBounds() else this
}
// --------------------------------------------------------

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

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val sidebarHeight = screenHeight * 0.65f
    val screenWidth = configuration.screenWidthDp.dp

    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sidebar
        Column(
            modifier = Modifier
                .width(sidebarWidth)
                .height(sidebarHeight)
                .background(Color.LightGray, RoundedCornerShape(50.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (dragAmount > 50 && !isSidebarExpanded) isSidebarExpanded = true
                        if (dragAmount < -50 && isSidebarExpanded) isSidebarExpanded = false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.05f))

            Column(
                modifier = Modifier.weight(0.7f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(0.05f))
                apps.forEachIndexed { index, app ->
                    Box(modifier = Modifier.height(50.dp), contentAlignment = Alignment.Center) {
                        SidebarItem(
                            item = app,
                            isExpanded = isSidebarExpanded,
                            isActive = pagerState.currentPage == index
                        ) {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                    }
                    if (index != apps.lastIndex) {
                        Spacer(modifier = Modifier.weight(if (isSidebarExpanded) 0.05f else 0.03f))
                    }
                }
                Spacer(modifier = Modifier.weight(0.05f))
            }

            Spacer(modifier = Modifier.weight(0.1f))

            IconButton(
                onClick = { isSidebarExpanded = !isSidebarExpanded },
                modifier = Modifier.weight(0.05f)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.doodlearrow),
                    contentDescription = "Toggle",
                    modifier = Modifier.rotate(arrowRotation),
                    tint = Color.Black
                )
            }

            Spacer(modifier = Modifier.weight(0.05f))
        }

        // Pager container
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .clipToBounds(false) // using normal Compose (extension not needed here)
        ) {
            VerticalPager(
                count = apps.size,
                state = pagerState,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(screenWidth)
                    .clipToBounds(false) // using extension now
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
fun SidebarItem(item: AppItem, isExpanded: Boolean, isActive: Boolean, onClick: () -> Unit) {
    val iconAlpha by animateFloatAsState(if (isActive) 1f else 0.5f)
    val textAlpha by animateFloatAsState(if (isActive && isExpanded) 1f else 0f)
    val iconSize by animateDpAsState(if (isExpanded) 40.dp else 28.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = item.iconRes),
            contentDescription = item.label,
            modifier = Modifier.size(iconSize),
            tint = Color.Unspecified
        )
        if (isExpanded) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.label,
                color = Color.Black.copy(alpha = textAlpha),
                fontSize = 20.sp
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
        AppItem("Security Group", R.drawable.doodlesecgrp)
    )
    FiddlerTheme {
        MainScreen(apps = apps)
    }
}
