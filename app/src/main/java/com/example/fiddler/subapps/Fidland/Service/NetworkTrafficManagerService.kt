package com.example.fiddler.subapps.Fidland.service

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.fiddler.subapps.Fidland.phs2.NetSpeedUpdater

class NetworkTrafficManagerService(
    private val context: Context,
    private val txtUpload: TextView,
    private val txtDownload: TextView,
    private val leftSegment: LinearLayout,
    private val overlayView: View
) {
    private var netSpeedUpdater: NetSpeedUpdater? = null

    /** Enable or disable network traffic display */
    fun toggle(enabled: Boolean) {
        if (enabled) {
            if (netSpeedUpdater == null) {
                netSpeedUpdater = NetSpeedUpdater(context, txtUpload, txtDownload, leftSegment, overlayView)
            }
            netSpeedUpdater?.start()
        } else {
            netSpeedUpdater?.stop()
        }

        // Adjust text size slightly
        txtUpload.setTextSize(TypedValue.COMPLEX_UNIT_PX, txtUpload.textSize * 0.9f)
        txtDownload.setTextSize(TypedValue.COMPLEX_UNIT_PX, txtDownload.textSize * 0.9f)

        // Toggle visibility
        txtUpload.visibility = if (enabled) View.VISIBLE else View.GONE
        txtDownload.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    /** Stop updater and cleanup */
    fun stop() {
        netSpeedUpdater?.stop()
    }
}
