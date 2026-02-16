package com.bypassnext.release

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var tvDnsStatus: TextView
    private lateinit var tvCertStatus: TextView
    private lateinit var tvLog: TextView

    private var isPrivacyActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btnToggle)
        tvDnsStatus = findViewById(R.id.tvDnsStatus)
        tvCertStatus = findViewById(R.id.tvCertStatus)
        tvLog = findViewById(R.id.tvLog)

        checkRoot()

        btnToggle.setOnClickListener {
            if (isPrivacyActive) {
                disablePrivacy()
            } else {
                enablePrivacy()
            }
        }
    }

    private fun checkRoot() {
        log("Checking root access...")
        Thread {
            val hasRoot = RootUtil.isRootAvailable()
            runOnUiThread {
                if (hasRoot) {
                    log("Root access GRANTED.")
                    checkPrivacyStatus()
                } else {
                    log("Root access DENIED. App will not function.")
                    btnToggle.isEnabled = false
                    btnToggle.text = "NO ROOT"
                }
            }
        }.start()
    }

    private fun checkPrivacyStatus() {
        Thread {
            val isActive = RootUtil.isPrivacyModeEnabled()
            runOnUiThread {
                isPrivacyActive = isActive
                updateUIState()
                if (isActive) {
                    log("Privacy Mode detected: ACTIVE")
                }
            }
        }.start()
    }

    private fun enablePrivacy() {
        log("Activating Privacy Mode...")
        btnToggle.isEnabled = false // Prevent double clicks

        Thread {
            val result = RootUtil.enablePrivacyMode()
            runOnUiThread {
                log(result)
                if (!result.startsWith("Error")) {
                    isPrivacyActive = true
                    updateUIState()
                } else {
                    log("Failed to activate.")
                }
                btnToggle.isEnabled = true
            }
        }.start()
    }

    private fun disablePrivacy() {
        log("Deactivating Privacy Mode...")
        btnToggle.isEnabled = false

        Thread {
            val result = RootUtil.disablePrivacyMode()
            runOnUiThread {
                log(result)
                if (!result.startsWith("Error")) {
                    isPrivacyActive = false
                    updateUIState()
                } else {
                    log("Failed to deactivate.")
                }
                btnToggle.isEnabled = true
            }
        }.start()
    }

    private fun updateUIState() {
        if (isPrivacyActive) {
            btnToggle.text = "ACTIVE"
            btnToggle.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            btnToggle.setBackgroundResource(R.drawable.bg_circle_active)

            tvDnsStatus.text = "a4f5f2.dns.nextdns.io"
            tvDnsStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))

            tvCertStatus.text = "Blocked: Digicert, GlobalSign, SSL"
            tvCertStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        } else {
            btnToggle.text = "INACTIVE"
            btnToggle.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            btnToggle.setBackgroundResource(R.drawable.bg_circle_inactive)

            tvDnsStatus.text = "System Default"
            tvDnsStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))

            tvCertStatus.text = "System Default"
            tvCertStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
    }

    private fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        tvLog.append("\n> [$timestamp] $message")
        // Auto scroll could be added here
    }
}
