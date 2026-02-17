package com.bypassnext.release

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "MainActivity"
class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var tvDnsStatus: TextView
    private lateinit var tvCertStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var etNextDnsId: EditText

    private lateinit var viewModel: MainViewModel
    private var lastRenderedLogCount = 0

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
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE)
        val savedId = prefs.getString(AppConstants.KEY_NEXTDNS_ID, "") ?: ""
        etNextDnsId.setText(savedId)

        val factory = MainViewModelFactory(DefaultPrivacyRepository(), AndroidStringProvider(this))
        viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[MainViewModel::class.java]

        btnToggle.setOnClickListener {
            val nextDnsId = etNextDnsId.text.toString().trim()
            if (nextDnsId.isEmpty()) {
                log("Error: NextDNS ID is required")
            } else {
                // Save ID
                getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(AppConstants.KEY_NEXTDNS_ID, nextDnsId)
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
        if (state.logs.size < lastRenderedLogCount) {
            // Logs were cleared or reset
            tvLog.text = ""
            lastRenderedLogCount = 0
        }

        if (state.logs.size > lastRenderedLogCount) {
            val newLogs = state.logs.subList(lastRenderedLogCount, state.logs.size)
            val newText = newLogs.joinToString("\n")

            if (lastRenderedLogCount > 0) {
                tvLog.append("\n")
            }
            tvLog.append(newText)
            lastRenderedLogCount = state.logs.size

            // Scroll to bottom
            tvLog.post {
                val scrollAmount = tvLog.layout?.let { layout ->
                    layout.getLineBottom(tvLog.lineCount - 1) - tvLog.height
                } ?: 0
                if (scrollAmount > 0) {
                    tvLog.scrollTo(0, scrollAmount)
                }
            }
        }

        // Update UI based on state
        etNextDnsId.isEnabled = !state.isPrivacyActive
        if (state.isPrivacyActive) {
            btnToggle.text = getString(R.string.status_active)
            // Use try-catch or safe call if resources might be missing, but assume they exist
            btnToggle.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            btnToggle.safeSetBackgroundResource(R.drawable.bg_circle_active)

            tvDnsStatus.text = etNextDnsId.text.toString()
            tvDnsStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))

            tvCertStatus.text = getString(R.string.cert_blocked)
            tvCertStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        } else {
            btnToggle.text = getString(R.string.status_inactive)
            btnToggle.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            btnToggle.safeSetBackgroundResource(R.drawable.bg_circle_inactive)

            tvDnsStatus.text = getString(R.string.system_default)
            tvDnsStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))

            tvCertStatus.text = getString(R.string.system_default)
            tvCertStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }

        // Handle busy state
        btnToggle.isEnabled = !state.isBusy
    }

    private fun Button.safeSetBackgroundResource(@DrawableRes resId: Int) {
        try {
            setBackgroundResource(resId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set background resource", e)
        }
    }

    private fun log(message: String) {
        val timestamp = logDateFormat.format(Date())
        tvLog.append("\n> [$timestamp] $message")
    }
}
