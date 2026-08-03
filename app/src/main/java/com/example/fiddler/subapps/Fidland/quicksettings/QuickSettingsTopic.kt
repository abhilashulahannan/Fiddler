package com.example.fiddler.subapps.Fidland.quicksettings

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.example.fiddler.R
import com.example.fiddler.subapps.Fidland.TopicPage
import com.example.fiddler.subapps.Fidland.phs3.comms.QuickSettingsStateReader
import com.example.fiddler.ui.icons.LottieIconColors
import com.example.fiddler.ui.icons.MonoLottieIcon
import com.example.fiddler.ui.icons.MonoVectorIcon

// ── Tile model ────────────────────────────────────────────────────────────────

/**
 * Describes a single Quick Settings tile.
 *
 * @param rawRes              Lottie asset in res/raw for the icon. Leave null
 *                            for a tile using [vectorIcon] instead.
 * @param vectorIcon          Static Material Symbols fallback for tiles with
 *                            no Lottie asset yet — resolves the open "Dev
 *                            Options / Accessibility icon" decision per the
 *                            handoff's own recommendation (§3): don't block
 *                            the feature on a new Lottie export, use a vector
 *                            fallback and swap it in later when one ships.
 *                            Exactly one of [rawRes]/[vectorIcon] must be set.
 * @param label               Text below the icon.
 * @param onColor             Icon tint when the feature is on/enabled.
 * @param readEnabled         Returns the current enabled-state of the feature.
 *                            Called at composition time; must be fast (no I/O).
 * @param onTap               Primary tap action.
 * @param longPressAction     Settings.ACTION_* string opened on long-press.
 *                            Null for Torch (no relevant settings screen).
 */
data class QuickSettingItem(
    val rawRes: Int? = null,
    val vectorIcon: ImageVector? = null,
    val label: String,
    val onColor: Color,
    val readEnabled: (Context) -> Boolean,
    val onTap: (Context) -> Unit,
    val longPressAction: String? = null
) {
    init {
        require((rawRes == null) != (vectorIcon == null)) {
            "QuickSettingItem '$label' must set exactly one of rawRes/vectorIcon"
        }
    }
}

// ── Main composable class ─────────────────────────────────────────────────────

class QuickSettingsTopicCompose(context: Context) : TopicPage(context) {

    // ── Torch state — owned here so tap toggles properly ──────────────────
    // CameraManager.TorchCallback is the right long-term owner (matching
    // Flashlightstate.kt's pattern), but a local mutableStateOf is sufficient
    // for the Quick Settings tile: it survives recomposition within the same
    // service lifetime and is cheap to add without touching FlashlightPhs3.
    private var torchOn by mutableStateOf(false)
    private val cameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    // ── Tile list ─────────────────────────────────────────────────────────

    private val items: List<QuickSettingItem> = listOf(

        // 1. Wi-Fi ──────────────────────────────────────────────────────────
        // Tap  → Settings.Panel.ACTION_WIFI (inline OS panel, no app-switch).
        // Long → Settings.ACTION_WIFI_SETTINGS (full Wi-Fi screen).
        // Icon → comms_wifi24g.json; band-aware swap (24g/5g) is a nice-to-have
        //        follow-up once WifiCommsSource.band is exposed here.
        QuickSettingItem(
            rawRes       = R.raw.comms_wifi24g,
            label        = "Wi-Fi",
            onColor      = LottieIconColors.commsWifi24g,
            readEnabled  = { ctx ->
                val wm = ctx.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                wm.isWifiEnabled
            },
            onTap        = { ctx ->
                ctx.startActivity(
                    Intent(Settings.Panel.ACTION_WIFI)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            longPressAction = Settings.ACTION_WIFI_SETTINGS
        ),

        // 2. Bluetooth ──────────────────────────────────────────────────────
        // Direct toggle blocked on API 33+, and unlike Wi-Fi/NFC/Volume there
        // is no Settings.Panel entry for Bluetooth (Panel only ever shipped
        // ACTION_INTERNET_CONNECTIVITY / ACTION_NFC / ACTION_VOLUME /
        // ACTION_WIFI) — so tap opens the full Bluetooth settings screen.
        QuickSettingItem(
            rawRes       = R.raw.comms_bt,
            label        = "Bluetooth",
            onColor      = LottieIconColors.commsBluetooth,
            readEnabled  = { _ ->
                android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                    ?.isEnabled == true
            },
            onTap        = { ctx ->
                ctx.startActivity(
                    Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            longPressAction = Settings.ACTION_BLUETOOTH_SETTINGS
        ),

        // 3. NFC ────────────────────────────────────────────────────────────
        // No public toggle API exists at all — always opens settings.
        // Settings.Panel.ACTION_NFC is available API 29+ (S24 Ultra is fine).
        QuickSettingItem(
            rawRes       = R.raw.comms_nfc,
            label        = "NFC",
            onColor      = LottieIconColors.commsNfc,
            readEnabled  = { ctx ->
                android.nfc.NfcAdapter.getDefaultAdapter(ctx)?.isEnabled == true
            },
            onTap        = { ctx ->
                // Try the inline NFC panel first; fall back to full screen
                // if this OEM/API-level doesn't resolve the panel action.
                val panelIntent = Intent(Settings.Panel.ACTION_NFC)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val resolves = ctx.packageManager
                    .resolveActivity(panelIntent, 0) != null
                if (resolves) {
                    ctx.startActivity(panelIntent)
                } else {
                    ctx.startActivity(
                        Intent(Settings.ACTION_NFC_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            longPressAction = Settings.ACTION_NFC_SETTINGS
        ),

        // 4. Developer Options ──────────────────────────────────────────────
        // Cannot be enabled programmatically — must be unlocked by the user
        // (7 taps on Build Number). Tile tints grey when not unlocked,
        // purple when unlocked. Tap always opens the Dev Options screen.
        QuickSettingItem(
            vectorIcon   = Icons.Filled.DeveloperMode,   // static fallback — see kdoc above
            label        = "Dev Options",
            onColor      = LottieIconColors.quickSettingsDevOptions,
            readEnabled  = { ctx -> QuickSettingsStateReader.isDevOptionsEnabled(ctx) },
            onTap        = { ctx ->
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            longPressAction = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
        ),

        // 5. Accessibility ──────────────────────────────────────────────────
        // Tile reflects whether *this app's* FidlandAccessibilityService is on.
        // Tap deep-links to the service's own settings fragment (API 30+,
        // One UI honours this). Falls back to the general list if not resolved.
        QuickSettingItem(
            vectorIcon   = Icons.Filled.Accessibility,   // static fallback — see kdoc above
            label        = "Accessibility",
            onColor      = LottieIconColors.quickSettingsAccessibility,
            readEnabled  = { ctx -> QuickSettingsStateReader.isFidlandAccessibilityEnabled(ctx) },
            onTap        = { ctx ->
                // Attempt deep-link to Fidland's own accessibility toggle screen.
                // There is no dedicated "details" action in the public SDK —
                // the standard way apps deep-link to a specific accessibility
                // service's page is ACTION_ACCESSIBILITY_SETTINGS plus the
                // "extra_fragment_arg_key" extra naming the service component.
                // The extra is honoured on OEM skins that support jumping
                // straight to a service's row (e.g. One UI); on stock/other
                // skins that ignore it, this just opens the general
                // Accessibility settings list — which is itself a fine
                // fallback, so no separate resolve/fallback branch is needed.
                ctx.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(
                            "extra_fragment_arg_key",
                            "${ctx.packageName}/.subapps.Fidland.Services.FidlandAccessibilityService"
                        )
                )
            },
            longPressAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
        ),

        // 6. Torch ──────────────────────────────────────────────────────────
        // Only tile that is a true in-app toggle. State tracked above.
        QuickSettingItem(
            rawRes       = R.raw.flashlight,
            label        = "Torch",
            onColor      = LottieIconColors.quickSettingsTorch,
            readEnabled  = { _ -> torchOn },
            onTap        = { _ -> toggleTorch() },
            longPressAction = null   // no relevant settings screen
        ),

        // 7. Mobile Data ────────────────────────────────────────────────────
        // No public write API — always opens the internet connectivity panel.
        // Icon uses comms_4g; swap to comms_5g in a follow-up if/when
        // CellularCommsSource.generation is wired in here.
        QuickSettingItem(
            rawRes       = R.raw.comms_4g,
            label        = "Mobile Data",
            onColor      = LottieIconColors.quickSettingsMobileData,
            readEnabled  = { ctx -> QuickSettingsStateReader.isMobileDataEnabled(ctx) },
            onTap        = { ctx ->
                ctx.startActivity(
                    Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            longPressAction = Settings.ACTION_WIRELESS_SETTINGS
        )
    )

    // ── Content ───────────────────────────────────────────────────────────
    // All tiles are tiled together in a single grid (4 columns) — no
    // per-tile swiping. Matches the layout pattern used by AppsTopic.

    private val gridColumns = 4

    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.chunked(gridColumns).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowItems.forEach { item ->
                        QuickSettingTile(
                            item     = item,
                            context  = context,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                    // Pad out the last row so tiles stay a consistent width.
                    repeat(gridColumns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // ── Torch toggle ──────────────────────────────────────────────────────

    private fun toggleTorch() {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            val newState = !torchOn
            cameraManager.setTorchMode(cameraId, newState)
            torchOn = newState
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// ── Tile composable ───────────────────────────────────────────────────────────

@Composable
private fun QuickSettingTile(
    item: QuickSettingItem,
    context: Context,
    modifier: Modifier = Modifier
) {
    // Read enabled-state at composition time — this is fast (Settings.Global
    // read or BluetoothAdapter.isEnabled-style call, no network/disk).
    val isEnabled = remember(item) { item.readEnabled(context) }

    // On = item's designated colour; off = neutral grey — consistent across
    // all tiles (same as comms2g "present but inactive" convention).
    val iconColor = if (isEnabled) item.onColor else Color(0xFF9A9A9A)

    Column(
        modifier = modifier
            .padding(4.dp)
            .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { item.onTap(context) },
                onLongClick = {
                    item.longPressAction?.let { action ->
                        context.startActivity(
                            Intent(action)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (item.vectorIcon != null) {
            MonoVectorIcon(
                imageVector = item.vectorIcon,
                color       = iconColor,
                size        = 26.dp
            )
        } else {
            MonoLottieIcon(
                spec     = LottieCompositionSpec.RawRes(item.rawRes!!),
                color    = iconColor,
                size     = 26.dp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text     = item.label,
            color    = Color.White,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}