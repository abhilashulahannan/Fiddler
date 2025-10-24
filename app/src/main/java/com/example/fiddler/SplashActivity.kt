package com.example.fiddler

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background

class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Optional: Hide system UI
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        setContent {
            SplashVideoScreen()
        }
    }

    @Composable
    fun SplashVideoScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Android VideoView inside Compose
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    VideoView(context).apply {
                        setVideoURI(Uri.parse("android.resource://${context.packageName}/${R.raw.splash_vid}"))
                        setOnCompletionListener {
                            startActivity(Intent(context, PermissionsActivity::class.java))
                            finish()
                        }
                        start()
                    }
                }
            )
        }
    }
}
