package com.kentaakakashi.memorygg

import android.app.Activity
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private lateinit var output: TextView
    private var bindingRequested = false

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            append("Shizuku permission result: " +
                if (grantResult == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED")
            refreshShizukuState()
        }
    }

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, MemoryUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("memorygg-diagnostics")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            bindingRequested = true
            val result = try {
                if (!binder.pingBinder()) {
                    "UserService connected with an invalid binder."
                } else {
                    IUserService.Stub.asInterface(binder)
                        .getDiagnostics(android.os.Process.myPid())
                }
            } catch (t: Throwable) {
                "UserService call failed: " + t.javaClass.simpleName + ": " + t.message
            }
            runOnUiThread {
                append("")
                append("== Shizuku UserService ==")
                result.lineSequence().forEach { append(it) }
                append("MemoryGG process only; this is not a test of game-process access.")
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            bindingRequested = false
            runOnUiThread { append("Shizuku UserService disconnected.") }
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
        val permissionButton = Button(this).apply {
            text = "Request Shizuku permission"
            setOnClickListener {
                try {
                    if (Shizuku.isPreV11()) append("This Shizuku version is too old.")
                    else Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                } catch (t: Throwable) {
                    append("Permission request error: " + t.javaClass.simpleName + ": " + t.message)
                }
            }
        }
        val runButton = Button(this).apply {
            text = "Run diagnostics"
            setOnClickListener { runDiagnostics() }
        }
        val stopButton = Button(this).apply {
            text = "Stop diagnostic service"
            setOnClickListener { stopUserService() }
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(permissionButton, matchWrap())
        root.addView(runButton, matchWrap())
        root.addView(stopButton, matchWrap())
        val scroll = ScrollView(this).apply { addView(output) }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
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
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "CONNECTED; permission granted"
                else -> "CONNECTED; permission not granted"
            }
        } catch (t: Throwable) {
            "Unavailable: " + t.javaClass.simpleName + ": " + t.message
        }
        append("Shizuku status: " + state)
    }

    private fun runDiagnostics() {
        output.text = ""
        append("== MemoryGG diagnostics ==")
        append("App UID: " + android.os.Process.myUid())
        append("App PID: " + android.os.Process.myPid())
        append("Native self-test: " + nativeSelfTest())
        append("Native test changes only a value inside this app's own process.")
        if (!Shizuku.pingBinder()) {
            append("Shizuku: NOT CONNECTED. Start it via Wireless debugging and retry.")
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            append("Shizuku is connected but permission is not granted. Tap Request Shizuku permission.")
            return
        }
        append("Shizuku binder and permission checks: PASS")
        if (Shizuku.getVersion() < 10) {
            append("UserService requires Shizuku API version 10 or newer.")
            return
        }
        if (bindingRequested) {
            append("UserService is already starting or connected. See its results below.")
            return
        }
        try {
            append("Starting supported Shizuku UserService...")
            bindingRequested = true
            Shizuku.bindUserService(userServiceArgs, userServiceConnection)
        } catch (t: Throwable) {
            bindingRequested = false
            append("UserService start failed: " + t.javaClass.simpleName + ": " + t.message)
        }
    }

    private fun stopUserService() {
        if (!bindingRequested) {
            append("No diagnostic UserService is currently bound.")
            return
        }
        try {
            Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true)
            bindingRequested = false
            append("UserService unbind requested.")
        } catch (t: Throwable) {
            append("UserService stop failed: " + t.javaClass.simpleName + ": " + t.message)
        }
    }

    private fun append(line: String) {
        if (!::output.isInitialized) return
        output.append("\n" + line)
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        if (bindingRequested) {
            try {
                Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true)
            } catch (_: Throwable) {
            }
            bindingRequested = false
        }
        super.onDestroy()
    }

    private external fun nativeSelfTest(): String

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 1001
        init { System.loadLibrary("memorygg") }
    }
}
