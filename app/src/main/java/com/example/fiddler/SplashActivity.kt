package com.example.fiddler

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.fontResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Optional: Hide system UI
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        setContent {
            SplashScreen()
        }

        // Navigate after 3 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, PermissionsActivity::class.java))
            finish()
        }, 3000)
    }

    @Composable
    fun SplashScreen() {
        val fontBody = FontFamily(Font(R.font.font_body))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App name
                androidx.compose.material3.Text(
                    text = "Fiddler",
                    fontSize = 100.sp,
                    fontFamily = fontBody,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(50.dp))

                // Logo image
                Image(
                    painter = painterResource(id = R.drawable.s24_dooodle),
                    contentDescription = "Fiddler Logo",
                    modifier = Modifier
                        .width(388.dp)
                        .height(353.dp)
                )
            }
        }
    }
}
