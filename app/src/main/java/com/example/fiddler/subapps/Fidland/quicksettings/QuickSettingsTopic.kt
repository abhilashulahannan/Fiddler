package com.example.fiddler.subapps.Fidland.quicksettings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
    val label: String,
    val systemSettingsAction: String? = null, // long-press destination
    val onToggle: (Context) -> Unit = {}
)

class QuickSettingsTopicCompose(context: Context) : TopicPage(context) {

    // Tiles — additions/removals will eventually come from FidlandScreen prefs.
    // For now the list is hardcoded here; wire to prefs in a follow-up.
    private val items = listOf(
        QuickSettingItemCompose(
            iconRes = R.drawable.wifi_on,
            label = "Wi-Fi",
            systemSettingsAction = Settings.ACTION_WIFI_SETTINGS,
            onToggle = { ctx -> toggleWifi(ctx) }
        ),
        QuickSettingItemCompose(
            iconRes = R.drawable.blue,
            label = "Bluetooth",
            systemSettingsAction = Settings.ACTION_BLUETOOTH_SETTINGS,
            onToggle = { ctx -> toggleBluetooth(ctx) }
        ),
        QuickSettingItemCompose(
            iconRes = R.drawable.flashlight_on,
            label = "Torch",
            systemSettingsAction = null,
            onToggle = { ctx -> toggleTorch(ctx) }
        )
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
                .height(120.dp)
        ) { page ->
            val item = items[page]
            QuickSettingTile(
                item = item,
                context = context
            )
        }

        currentPage = pagerState.currentPage
    }

    override fun onSwipeLeft() {
        currentPage = (currentPage + 1).coerceAtMost(items.size - 1)
    }

    override fun onSwipeRight() {
        currentPage = (currentPage - 1).coerceAtLeast(0)
    }

    // --- Toggle implementations ---
    // Wi-Fi: direct toggle removed in Android 10+, open settings instead
    private fun toggleWifi(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    private fun toggleBluetooth(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    private fun toggleTorch(ctx: Context) {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE)
                as android.hardware.camera2.CameraManager
        try {
            val cameraId = cm.cameraIdList[0]
            // Simple toggle — track state in a companion val if you need
            // to know current torch state
            cm.setTorchMode(cameraId, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
private fun QuickSettingTile(
    item: QuickSettingItemCompose,
    context: Context
) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxHeight()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { item.onToggle(context) },
                onLongClick = {
                    // Long-press → open relevant system settings page
                    item.systemSettingsAction?.let { action ->
                        context.startActivity(
                            Intent(action).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }
                }
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(item.iconRes),
            contentDescription = item.label,
            modifier = Modifier.size(32.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.label,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}