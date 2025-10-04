package com.example.fiddler

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import android.view.View


class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Optional: Make status bar transparent or hidden
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        // Wait 3 seconds (adjust as needed)
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, PermissionsActivity::class.java))
            finish()
        }, 3000)
    }
}
