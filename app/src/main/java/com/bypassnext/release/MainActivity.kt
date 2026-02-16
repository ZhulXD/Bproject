package com.bypassnext.release

import android.os.Bundle
import android.widget.Button
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

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btnToggle)
        tvDnsStatus = findViewById(R.id.tvDnsStatus)
        tvCertStatus = findViewById(R.id.tvCertStatus)
        tvLog = findViewById(R.id.tvLog)

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

    private fun render(state: MainUiState) {
        // Logs
        tvLog.text = state.logs.joinToString("\n")

        // Busy state
        btnToggle.isEnabled = !state.isBusy && state.isRootGranted

        // Root check handling
        if (!state.isCheckingRoot && !state.isRootGranted) {
            btnToggle.text = getString(R.string.no_root)
            // Keep style inactive
        } else if (state.isRootGranted) {
            // Privacy state
            if (state.isPrivacyActive) {
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
    }
}
