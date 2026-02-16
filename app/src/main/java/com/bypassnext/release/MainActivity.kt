package com.bypassnext.release

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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
        val savedId = prefs.getString(KEY_NEXTDNS_ID, "") ?: ""
        etNextDnsId.setText(savedId)

        val factory = MainViewModelFactory(DefaultPrivacyRepository(), AndroidStringProvider(this))
        viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[MainViewModel::class.java]

        btnToggle.setOnClickListener {
            val nextDnsId = etNextDnsId.text.toString().trim()
            if (nextDnsId.isEmpty()) {
                log("Error: NextDNS ID is required")
            } else {
                // Save ID
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(KEY_NEXTDNS_ID, nextDnsId)
                    .apply()

                val tempDir = cacheDir.absolutePath + "/filtered_certs"
                viewModel.togglePrivacy(nextDnsId, tempDir)
            }
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                render(state)
            }
        }

        // Initial check
        if (savedId.isNotEmpty()) {
            viewModel.checkPrivacyStatus(savedId)
        }
    }

    private fun render(state: MainUiState) {
        // Log updates
        // Join logs with newline
        val logsText = state.logs.joinToString("\n")
        if (tvLog.text.toString() != logsText) {
             tvLog.text = logsText
             // Scroll to bottom
             val scrollAmount = tvLog.layout?.let { layout ->
                 tvLog.scrollY + (tvLog.height) - layout.getLineBottom(tvLog.lineCount - 1)
             } ?: 0
             // Ideally use a ScrollView, but simple text update is fine for now
        }

        // Update UI based on state
        etNextDnsId.isEnabled = !state.isPrivacyActive
        if (state.isPrivacyActive) {
            btnToggle.text = getString(R.string.status_active)
            // Use try-catch or safe call if resources might be missing, but assume they exist
            btnToggle.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            try {
                btnToggle.setBackgroundResource(R.drawable.bg_circle_active)
            } catch (e: Exception) {
                // Fallback or ignore
            }

            tvDnsStatus.text = etNextDnsId.text.toString()
            tvDnsStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))

            tvCertStatus.text = getString(R.string.cert_blocked)
            tvCertStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        } else {
            btnToggle.text = getString(R.string.status_inactive)
            btnToggle.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            try {
                btnToggle.setBackgroundResource(R.drawable.bg_circle_inactive)
            } catch (e: Exception) {
                // Fallback
            }

            tvDnsStatus.text = getString(R.string.system_default)
            tvDnsStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))

            tvCertStatus.text = getString(R.string.system_default)
            tvCertStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }

        // Handle busy state
        btnToggle.isEnabled = !state.isBusy
    }

    private fun log(message: String) {
        val timestamp = logDateFormat.format(Date())
        tvLog.append("\n> [$timestamp] $message")
    }
}
