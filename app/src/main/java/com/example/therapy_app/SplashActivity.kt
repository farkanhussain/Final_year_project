package com.example.therapy_app
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.therapy_app.utils.PerfLogger

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        window.decorView.post {
            val coldStart = System.currentTimeMillis() - TherapyApp.coldStartTimestamp
            PerfLogger.log("Cold start", coldStart)
        }



        val logo = findViewById<ImageView>(R.id.logo)

        logo.post {
            logo.animate()
                .alpha(1f)
                .setDuration(3000)
                .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                .start()
        }





        // Delay for 5 seconds, then open LoginActivity
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }, 5000)
    }
}
