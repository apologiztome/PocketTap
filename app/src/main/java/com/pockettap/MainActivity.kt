package com.pockettap

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var toggleButton: Button
    private lateinit var statusText: TextView
    private lateinit var statusDot: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("pockettap", MODE_PRIVATE)
        toggleButton = findViewById(R.id.toggleButton)
        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)

        updateUI()

        toggleButton.setOnClickListener {
            val isRunning = prefs.getBoolean("running", false)
            if (isRunning) {
                stopTapService()
            } else {
                startTapService()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun startTapService() {
        val intent = Intent(this, TapService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        prefs.edit().putBoolean("running", true).apply()
        updateUI()
    }

    private fun stopTapService() {
        val stopIntent = Intent(this, TapService::class.java).apply {
            action = TapService.ACTION_STOP
        }
        startService(stopIntent)
        prefs.edit().putBoolean("running", false).apply()
        updateUI()
    }

    private fun updateUI() {
        val isRunning = prefs.getBoolean("running", false)
        if (isRunning) {
            statusDot.text = "🟢"
            statusText.text = "Active — Put it in your pocket!"
            toggleButton.text = "Turn OFF"
            toggleButton.setBackgroundColor(getColor(android.R.color.holo_red_dark))
        } else {
            statusDot.text = "🔴"
            statusText.text = "Inactive"
            toggleButton.text = "Turn ON"
            toggleButton.setBackgroundColor(getColor(android.R.color.holo_green_dark))
        }
    }
}
