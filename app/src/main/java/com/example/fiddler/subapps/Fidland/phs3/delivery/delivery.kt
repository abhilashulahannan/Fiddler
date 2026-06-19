package com.example.fiddler.subapps.Fidland.phs3.delivery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler

/**
 * Phs3 module — Delivery tracking (food, parcel, ride).
 *
 * Indicator : delivery icon + ETA or status label.
 * ControlsPanel : order details, contact driver, live map link — filled in when delivery logic is built.
 */
class DeliveryPhs3Handler : Phs3Handler {

    override val label: String = "Delivery"

    @Composable
    override fun Indicator() {
        // TODO: replace with live ETA / status from delivery notification parser
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "📦", fontSize = 12.sp, color = Color.White)
            Text(text = "12m", fontSize = 11.sp, color = Color.White)
        }
    }

    @Composable
    override fun State5Content() {
        // TODO: order summary, driver contact, open tracking map
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Delivery controls — coming soon",
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
}