package com.bypassnext.release

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"
class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var btnFloatingMode: Button
    private val overlayPermissionRequestCode = 1234
    private lateinit var tvDnsStatus: TextView
    private lateinit var tvCertStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var etNextDnsId: EditText

    private lateinit var viewModel: MainViewModel
    private val logDiffer = LogDiffer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btnToggle)
        btnFloatingMode = findViewById(R.id.btnFloatingMode)
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
                viewModel.log(getString(R.string.error_nextdns_id_required))
            } else {
                // Save ID
                getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(AppConstants.KEY_NEXTDNS_ID, nextDnsId)
                    .apply()

                val tempDir = cacheDir.absolutePath + "/filtered_certs"
                viewModel.togglePrivacy(nextDnsId, tempDir)
            }
        }

        btnFloatingMode.setOnClickListener {
            if (checkOverlayPermission()) {
                startFloatingService()
            } else {
                requestOverlayPermission()
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
        renderLogs(state)
        renderPrivacyState(state)
        renderBusyState(state)
    }

    private fun renderLogs(state: MainUiState) {
        when (val update = logDiffer.computeUpdate(state.logs)) {
            is LogUpdate.NoChange -> return
            is LogUpdate.Append -> {
                if (update.addNewline) {
                    tvLog.append("\n")
                }
                tvLog.append(update.newText)
                scrollToBottom()
            }
            is LogUpdate.Replace -> {
                tvLog.text = update.fullText
                scrollToBottom()
            }
        }
    }

    private fun scrollToBottom() {
        tvLog.post {
            val scrollAmount = tvLog.layout?.let { layout ->
                layout.getLineBottom(tvLog.lineCount - 1) - tvLog.height
            } ?: 0
            if (scrollAmount > 0) {
                tvLog.scrollTo(0, scrollAmount)
            }
        }
    }

    private fun renderPrivacyState(state: MainUiState) {
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
    }

    private fun renderBusyState(state: MainUiState) {
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

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, overlayPermissionRequestCode)
            Toast.makeText(this, getString(R.string.grant_overlay_permission), Toast.LENGTH_SHORT).show()
        }
    }

    private fun startFloatingService() {
        val nextDnsId = etNextDnsId.text.toString().trim()
        if (nextDnsId.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_nextdns_id_required), Toast.LENGTH_SHORT).show()
            return
        }

        getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE).edit()
            .putString(AppConstants.KEY_NEXTDNS_ID, nextDnsId)
            .apply()

        val intent = Intent(this, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == overlayPermissionRequestCode) {
            if (checkOverlayPermission()) {
                startFloatingService()
            } else {
                Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
