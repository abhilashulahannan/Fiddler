package com.example.fiddler.subapps.Fidland.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.fiddler.subapps.Fidland.TopicPage
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState

/**
 * Phase 3 dashboard — Category 3: App Launcher.
 *
 * App list and grid dimensions (rows/columns) are read from SharedPrefs
 * set by the FidlandScreen config UI. Adding/removing apps is done in
 * the Fiddler main app — this composable is read-only.
 *
 * Each page holds (rows × columns) app icons. Tapping an icon launches
 * the app. If the app is no longer installed the icon is hidden.
 *
 * Pref keys expected:
 *   "app_rows"    Int  — default 3
 *   "app_columns" Int  — default 4
 *   "launcher_apps" StringSet — set of package names to show
 */
class AppsTopic(context: Context) : TopicPage(context) {

    private val prefs = context.getSharedPreferences("fidland_prefs", Context.MODE_PRIVATE)
    private var currentPage by mutableStateOf(0)

    @Composable
    override fun Content() {
        val rows = prefs.getInt("app_rows", 3)
        val cols = prefs.getInt("app_columns", 4)
        val iconsPerPage = rows * cols

        // Read saved package names — empty set means no apps configured yet
        val packageNames = prefs.getStringSet("launcher_apps", emptySet())
            ?.toList() ?: emptyList()

        // Filter to only installed apps and resolve their icons
        val pm = context.packageManager
        val installedApps = packageNames.mapNotNull { pkg ->
            resolveApp(pm, pkg)
        }

        if (installedApps.isEmpty()) {
            EmptyState()
            return
        }

        // Split into pages
        val pages = installedApps.chunked(iconsPerPage)
        val pagerState = rememberPagerState(
            initialPage = currentPage.coerceAtMost(pages.size - 1)
        )

        HorizontalPager(
            count = pages.size,
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) { pageIndex ->
            AppGrid(
                apps = pages[pageIndex],
                columns = cols,
                context = context
            )
        }

        currentPage = pagerState.currentPage
    }

    @Composable
    private fun AppGrid(
        apps: List<AppEntry>,
        columns: Int,
        context: Context
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D))
                .padding(top = 10.dp)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            apps.chunked(columns).forEach { rowApps ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowApps.forEach { app ->
                        AppIcon(
                            app = app,
                            modifier = Modifier.weight(1f),
                            onClick = { launchApp(context, app.packageName) }
                        )
                    }
                    // Fill remaining slots in last row with empty space
                    repeat(columns - rowApps.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    @Composable
    private fun AppIcon(
        app: AppEntry,
        modifier: Modifier,
        onClick: () -> Unit
    ) {
        Column(
            modifier = modifier
                .clickable { onClick() }
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                bitmap = app.icon.toBitmap(48, 48).asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 9.sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    @Composable
    private fun EmptyState() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No apps added yet.\nAdd apps in the Fidland settings.",
                color = Color(0xFF555555),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }

    private fun resolveApp(pm: PackageManager, packageName: String): AppEntry? {
        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            AppEntry(
                packageName = packageName,
                label = pm.getApplicationLabel(info).toString(),
                icon = pm.getApplicationIcon(info)
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null // App uninstalled — skip silently
        }
    }

    private fun launchApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    override fun onSwipeLeft() {
        currentPage = currentPage + 1 // upper bound enforced by pager
    }

    override fun onSwipeRight() {
        currentPage = (currentPage - 1).coerceAtLeast(0)
    }

    private data class AppEntry(
        val packageName: String,
        val label: String,
        val icon: Drawable
    )
}