package com.bypassnext.release

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
        log(getString(R.string.checking_root_access))
        lifecycleScope.launch {
            val hasRoot = RootUtil.isRootAvailable()
            if (hasRoot) {
                log(getString(R.string.root_access_granted))
                checkPrivacyStatus()
            } else {
                log(getString(R.string.root_access_denied))
                btnToggle.isEnabled = false
                btnToggle.text = getString(R.string.no_root)
            }
        }
    }

    private suspend fun checkPrivacyStatus() {
        // Run directly in the caller's scope (which is already lifecycle-aware)
        // RootUtil.isPrivacyModeEnabled() is suspend and handles its own dispatching (IO)
        val isActive = RootUtil.isPrivacyModeEnabled()
        isPrivacyActive = isActive
        updateUIState()
        if (isActive) {
            log(getString(R.string.privacy_mode_detected_active))
        }
    }

    private fun enablePrivacy() {
        log(getString(R.string.activating_privacy_mode))
        btnToggle.isEnabled = false // Prevent double clicks

        lifecycleScope.launch {
            val certsDir = "${cacheDir.absolutePath}/filtered_certs"
            val result = RootUtil.enablePrivacyMode(certsDir)
            log(result)
            if (!result.startsWith("Error")) {
                isPrivacyActive = true
                updateUIState()
            } else {
                log(getString(R.string.failed_to_activate))
            }
            btnToggle.isEnabled = true
        }
    }

    private fun disablePrivacy() {
        log(getString(R.string.deactivating_privacy_mode))
        btnToggle.isEnabled = false

        lifecycleScope.launch {
            val certsDir = "${cacheDir.absolutePath}/filtered_certs"
            val result = RootUtil.disablePrivacyMode(certsDir)
            log(result)
            if (!result.startsWith("Error")) {
                isPrivacyActive = false
                updateUIState()
            } else {
                log(getString(R.string.failed_to_deactivate))
            }
            btnToggle.isEnabled = true
        }
    }

    private fun updateUIState() {
        if (isPrivacyActive) {
            btnToggle.text = getString(R.string.status_active)
            btnToggle.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            btnToggle.setBackgroundResource(R.drawable.bg_circle_active)

            tvDnsStatus.text = getString(R.string.dns_nextdns)
            tvDnsStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))

            tvCertStatus.text = getString(R.string.cert_blocked)
            tvCertStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        } else {
            btnToggle.text = getString(R.string.status_inactive)
            btnToggle.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            btnToggle.setBackgroundResource(R.drawable.bg_circle_inactive)

            tvDnsStatus.text = getString(R.string.system_default)
            tvDnsStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))

            tvCertStatus.text = getString(R.string.system_default)
            tvCertStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
    }

    private fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        tvLog.append("\n> [$timestamp] $message")
        // Auto scroll could be added here
    }
}
