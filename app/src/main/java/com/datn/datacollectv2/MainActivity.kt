package com.datn.datacollectv2

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class MainActivity : AppCompatActivity() {

    companion object {
        private const val SPLASH_DELAY_MS = 1400L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val ivLogo    = findViewById<ImageView>(R.id.ivLogo)
        val tvAppName = findViewById<TextView>(R.id.tvAppName)
        val tvTagline = findViewById<TextView>(R.id.tvTagline)
        val progress  = findViewById<ProgressBar>(R.id.progressBar)

        val views = listOf(ivLogo, tvAppName, tvTagline, progress)
        views.forEach { v ->
            v.alpha        = 0f
            v.translationY = 40f
        }

        views.forEachIndexed { i, view ->
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(i * 150L)
                .setDuration(400L)
                .start()
        }

        ivLogo.postDelayed({ navigateNext() }, SPLASH_DELAY_MS)
    }

    private fun navigateNext() {
        val intent = if (UserSession.isLoggedIn(this)) {
            val profile = UserSession.getProfile(this)!!
            Intent(this, SensorCollectionActivity::class.java).apply {
                putExtra("USER_ID",    profile.userId)
                putExtra("SESSION_ID", "s1")
            }
        } else {
            Intent(this, RegistrationActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}