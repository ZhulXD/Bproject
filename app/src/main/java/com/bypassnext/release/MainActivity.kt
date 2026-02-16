package com.bypassnext.release

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var tvDnsStatus: TextView
    private lateinit var tvCertStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var etNextDnsId: EditText

    private lateinit var viewModel: MainViewModel

    private val PREFS_NAME = "BypassNextPrefs"
    private val KEY_NEXTDNS_ID = "nextdns_id"

    private val logDateFormat by lazy {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btnToggle)
        tvDnsStatus = findViewById(R.id.tvDnsStatus)
        tvCertStatus = findViewById(R.id.tvCertStatus)
        tvLog = findViewById(R.id.tvLog)
        etNextDnsId = findViewById(R.id.etNextDnsId)

        // Load saved ID
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedId = prefs.getString(KEY_NEXTDNS_ID, "")
        etNextDnsId.setText(savedId)

        val factory = MainViewModelFactory(DefaultPrivacyRepository(), AndroidStringProvider(this))
        viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[MainViewModel::class.java]

        btnToggle.setOnClickListener {
            viewModel.togglePrivacy()
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                render(state)
            }
        }
    }

    private suspend fun checkPrivacyStatus() {
        // Run directly in the caller's scope (which is already lifecycle-aware)
        // RootUtil.isPrivacyModeEnabled() is suspend and handles its own dispatching (IO)
        val nextDnsId = etNextDnsId.text.toString().trim()
        val isActive = RootUtil.isPrivacyModeEnabled(nextDnsId)
        isPrivacyActive = isActive
        updateUIState()
        if (isActive) {
            log(getString(R.string.privacy_mode_detected_active))
        }
    }

    private fun enablePrivacy() {
        val nextDnsId = etNextDnsId.text.toString().trim()
        if (nextDnsId.isEmpty()) {
            log("Error: NextDNS ID is required")
            return
        }

        // Save ID
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_NEXTDNS_ID, nextDnsId)
            .apply()

        log(getString(R.string.activating_privacy_mode))
        btnToggle.isEnabled = false // Prevent double clicks

        lifecycleScope.launch {
            val result = RootUtil.enablePrivacyMode(nextDnsId)
            log(result)
            if (!result.startsWith("Error")) {
                isPrivacyActive = true
                updateUIState()
            } else {
                btnToggle.text = getString(R.string.status_inactive)
                btnToggle.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                btnToggle.setBackgroundResource(R.drawable.bg_circle_inactive)

                tvDnsStatus.text = getString(R.string.system_default)
                tvDnsStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))

                tvCertStatus.text = getString(R.string.system_default)
                tvCertStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            }
            btnToggle.isEnabled = true
        }
    }

    private fun updateUIState() {
        etNextDnsId.isEnabled = !isPrivacyActive
        if (isPrivacyActive) {
            btnToggle.text = getString(R.string.status_active)
            btnToggle.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            btnToggle.setBackgroundResource(R.drawable.bg_circle_active)

            tvDnsStatus.text = etNextDnsId.text.toString()
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
        val timestamp = logDateFormat.format(Date())
        tvLog.append("\n> [$timestamp] $message")
        // Auto scroll could be added here
    }
}
