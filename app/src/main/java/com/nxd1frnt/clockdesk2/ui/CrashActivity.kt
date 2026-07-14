package com.nxd1frnt.clockdesk2.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.transition.TransitionManager
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.google.android.material.card.MaterialCardView
import com.nxd1frnt.clockdesk2.R

class CrashActivity : AppCompatActivity() {

    private lateinit var crashRoot: ConstraintLayout
    private lateinit var leftPane: View
    private lateinit var rightPaneCard: MaterialCardView
    private lateinit var tvCrashDetails: TextView
    private lateinit var btnToggleDetails: Button
    private lateinit var btnRestart: Button
    private lateinit var btnCopy: Button

    private var isDetailsVisible = false
    private var fullLogReport: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash)

        initViews()
        setupCrashDetails()
        setupListeners()
    }

    private fun initViews() {
        crashRoot = findViewById(R.id.crash_root)
        leftPane = findViewById(R.id.left_pane)
        rightPaneCard = findViewById(R.id.right_pane_card)
        tvCrashDetails = findViewById(R.id.tv_crash_details)
        btnToggleDetails = findViewById(R.id.btn_toggle_details)
        btnRestart = findViewById(R.id.btn_restart)
        btnCopy = findViewById(R.id.btn_copy)
    }

    private fun setupCrashDetails() {
        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "No stack trace provided"
        val threadName = intent.getStringExtra(EXTRA_THREAD_NAME) ?: "Unknown"
        val exceptionClass = intent.getStringExtra(EXTRA_EXCEPTION_CLASS) ?: "Unknown"
        val exceptionMessage = intent.getStringExtra(EXTRA_EXCEPTION_MESSAGE) ?: "No message"

        val appVersion = getAppVersionInfo()
        val deviceInfo = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"

        fullLogReport = buildString {
            append("ClockDesk Crash Report\n")
            append("App Version: $appVersion\n")
            append("Device:      $deviceInfo\n")
            append("Thread:      $threadName\n")
            append("Exception:   $exceptionClass: $exceptionMessage\n")
            append("\n\n")
            append("Stack Trace:\n")
            append(stackTrace)
        }

        tvCrashDetails.text = fullLogReport
    }

    private fun getAppVersionInfo(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                packageManager.getPackageInfo(packageName, 0)
            }
            val version = packageInfo.versionName ?: "Unknown"
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            "$version (build $code)"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun setupListeners() {
        btnRestart.setOnClickListener {
            val restartIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (restartIntent != null) {
                restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(restartIntent)
            }
            finish()
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(0)
        }

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("ClockDesk Crash Log", fullLogReport)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.crash_log_copied, Toast.LENGTH_SHORT).show()
        }

        btnToggleDetails.setOnClickListener {
            isDetailsVisible = !isDetailsVisible

            val constraintSet = ConstraintSet()
            constraintSet.clone(crashRoot)

            if (isDetailsVisible) {
                // Constraints when details are visible: leftPane right side connects to pane_guideline
                constraintSet.clear(R.id.left_pane, ConstraintSet.END)
                constraintSet.connect(R.id.left_pane, ConstraintSet.END, R.id.pane_guideline, ConstraintSet.START)
                constraintSet.setVisibility(R.id.right_pane_card, View.VISIBLE)
                btnToggleDetails.setText(R.string.crash_btn_details_hide)
            } else {
                // Constraints when details are hidden: leftPane right side connects to parent end (centered)
                constraintSet.clear(R.id.left_pane, ConstraintSet.END)
                constraintSet.connect(R.id.left_pane, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                constraintSet.setVisibility(R.id.right_pane_card, View.GONE)
                btnToggleDetails.setText(R.string.crash_btn_details_show)
            }

            TransitionManager.beginDelayedTransition(crashRoot)
            constraintSet.applyTo(crashRoot)
        }
    }

    companion object {
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
        const val EXTRA_THREAD_NAME = "extra_thread_name"
        const val EXTRA_EXCEPTION_CLASS = "extra_exception_class"
        const val EXTRA_EXCEPTION_MESSAGE = "extra_exception_message"
    }
}
