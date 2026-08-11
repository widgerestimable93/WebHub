package com.widgerestimable.webhub.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.widgerestimable.webhub.R
import com.widgerestimable.webhub.utils.ThemeManager

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<android.widget.ImageView>(R.id.splash_logo)
        logo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.splash_fade_scale))

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, HubActivity::class.java))
            finish()
        }, 900)
    }
}

