package com.kentaakakashi.memorygg

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import android.view.ViewGroup
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private lateinit var output: TextView

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            append("\nShizuku permission result: " + if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED")
            refreshShizukuState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(Color.rgb(16, 16, 24))
        }
        val title = TextView(this).apply {
            text = "MemoryGG"
            textSize = 30f
            setTextColor(Color.WHITE)
        }
        val subtitle = TextView(this).apply {
            text = "Rootless memory-access diagnostics"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 4, 0, 18)
        }
        output = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(220, 220, 235))
            text = "Ready. Start Shizuku, then run diagnostics."
            setPadding(0, 8, 0, 16)
        }
        val runButton = Button(this).apply {
            text = "Run diagnostics"
            setOnClickListener { runDiagnostics() }
        }
        val permissionButton = Button(this).apply {
            text = "Request Shizuku permission"
            setOnClickListener {
                try {
                    if (Shizuku.isPreV11()) append("This Shizuku version is too old.")
                    else Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                } catch (t: Throwable) {
                    append("Permission request error: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(permissionButton, matchWrap())
        root.addView(runButton, matchWrap())
        val scroll = ScrollView(this).apply { addView(output) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        refreshShizukuState()
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = 8 }

    private fun refreshShizukuState() {
        val state = try {
            when {
                !Shizuku.pingBinder() -> "NOT CONNECTED (start Shizuku)"
                Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED -> "CONNECTED; permission granted"
                else -> "CONNECTED; permission not granted"
            }
        } catch (t: Throwable) {
            "Unavailable: ${t.javaClass.simpleName}: ${t.message}"
        }
        append("Shizuku status: $state")
    }

    private fun runDiagnostics() {
        output.text = ""
        append("== MemoryGG diagnostics ==")
        append("App UID: ${android.os.Process.myUid()}")
        append("Native self-test: ${nativeSelfTest()}")
        append("Note: native test changes only a value inside this app's own process.")
        if (!Shizuku.pingBinder()) {
            append("Shizuku: NOT CONNECTED. Start it via Wireless debugging and retry.")
            return
        }
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            append("Shizuku: connected, but permission is not granted. Tap Request Shizuku permission.")
            return
        }
        append("Shizuku: CONNECTED; permission granted.")
        append("Shizuku binder and permission checks passed.")
        append("Shell execution is not tested in this build: Shizuku newProcess is private in the installed public API.")
        append("Next step: configure a Shizuku UserService for a supported shell diagnostic.")
        append("Important: Shizuku permission alone does not prove arbitrary third-party process memory access.")
    }

    private fun append(line: String) {
        if (!::output.isInitialized) return
        output.append("\n$line")
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private external fun nativeSelfTest(): String

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 1001
        init { System.loadLibrary("memorygg") }
    }
}
