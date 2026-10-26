package com.kentaakakashi.memorygg

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private data class ScanMatch(val address: Long, val addressText: String, var value: String)

class MainActivity : Activity() {
    private lateinit var scanValue: EditText
    private lateinit var typeSpinner: Spinner
    private lateinit var conditionSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var diagnostics: TextView
    private lateinit var demoValue: TextView
    private lateinit var results: LinearLayout
    private lateinit var firstButton: Button
    private lateinit var nextButton: Button
    private lateinit var agentPackageInput: EditText
    private lateinit var agentTokenInput: EditText
    private lateinit var connectAgentButton: Button
    @Volatile private var agentService: IMemoryAgent? = null
    private var agentBound = false
    private val scanExecutor = Executors.newSingleThreadExecutor()
    private val frozen = ConcurrentHashMap<Long, Pair<String, Int>>()
    private val displayedMatches = mutableListOf<ScanMatch>()
    private var serviceBound = false

    private val agentConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val remote = IMemoryAgent.Stub.asInterface(binder)
            val suppliedToken = if (::agentTokenInput.isInitialized) agentTokenInput.text.toString().trim() else ""
            val auth = try { remote.authorize(suppliedToken) }
            catch (t: Throwable) { "DENIED: " + t.javaClass.simpleName + ": " + t.message }
            runOnUiThread {
                if (auth.startsWith("AUTHORIZED:")) {
                    agentService = remote
                    connectAgentButton.text = "Disconnect agent"
                    val info = try { remote.getInfo() } catch (t: Throwable) { t.message ?: "info unavailable" }
                    appendDiagnostic("Target agent connected: " + info)
                    status.text = "Connected to target process. Scan and edit requests now run inside that app."
                    refreshProbeDisplay()
                } else {
                    agentService = null
                    appendDiagnostic("Agent pairing failed: " + auth)
                    status.text = auth
                    if (agentBound) {
                        try { unbindService(this) } catch (_: Throwable) { }
                        agentBound = false
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            agentService = null
            frozen.clear()
            runOnUiThread {
                if (::connectAgentButton.isInitialized) connectAgentButton.text = "Connect agent"
                if (::status.isInitialized) status.text = "Agent disconnected; scans use MemoryGG's own process."
                refreshProbeDisplay()
            }
        }
    }

    private val freezeExecutor = Executors.newSingleThreadScheduledExecutor().apply {
        scheduleAtFixedRate({
            for ((address, pair) in frozen) {
                try { val agent = agentService; if (agent != null) agent.writeValue(address, pair.first, pair.second) else nativeWriteValue(address, pair.first, pair.second) } catch (_: Throwable) { }
            }
        }, 150, 150, TimeUnit.MILLISECONDS)
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == REQUEST_CODE) {
            appendDiagnostic("Shizuku permission: " +
                if (result == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED")
            refreshShizukuState()
        }
    }

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, MemoryUserService::class.java.name)
        ).daemon(false)
            .processNameSuffix("memorygg-diagnostics")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            serviceBound = true
            val report = try {
                if (!binder.pingBinder()) "Invalid Shizuku UserService binder."
                else IUserService.Stub.asInterface(binder).getDiagnostics(android.os.Process.myPid())
            } catch (t: Throwable) {
                "UserService failed: " + t.javaClass.simpleName + ": " + t.message
            }
            runOnUiThread {
                appendDiagnostic("== Shizuku UserService ==")
                report.lineSequence().forEach { appendDiagnostic(it) }
                appendDiagnostic("External game RAM is not scanned by this build. The scanner UI operates in MemoryGG itself.")
            }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            serviceBound = false
            runOnUiThread { appendDiagnostic("Shizuku UserService disconnected.") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 18, 16, 12)
            setBackgroundColor(Color.rgb(12, 14, 22))
        }
        root.addView(TextView(this).apply {
            text = "MemoryGG"
            textSize = 30f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Native memory scanner · editor · freeze"
            textSize = 14f
            setTextColor(Color.rgb(175, 187, 205))
            setPadding(0, 0, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "CURRENT MODE: in-process only. Your phone denied Shizuku shell access to other apps' RAM. Target-app agent/patching is still required for game memory."
            textSize = 12f
            setTextColor(Color.rgb(255, 209, 135))
            setPadding(10, 9, 10, 9)
            setBackgroundColor(Color.rgb(38, 31, 22))
        })

        agentPackageInput = EditText(this).apply {
            hint = "Target package (Agent Test Host)"
            setSingleLine(true)
            setText(if (BuildConfig.DEBUG) "com.kentaakakashi.memorygg.agenthost.debug" else "com.kentaakakashi.memorygg.agenthost")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        agentTokenInput = EditText(this).apply {
            hint = "Pairing token copied from Agent Test Host"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        connectAgentButton = Button(this).apply {
            text = "Connect agent"
            setOnClickListener { toggleAgentConnection() }
        }
        root.addView(agentPackageInput)
        root.addView(agentTokenInput)
        root.addView(connectAgentButton)

        typeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Int32", "Float32", "Int64", "Float64"))
        }
        conditionSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Exact value", "Increased", "Decreased", "Unchanged", "Changed"))
        }
        val selectRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(typeSpinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(conditionSpinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
        }
        scanValue = EditText(this).apply {
            hint = "Value · try 12345 / Int32"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val demoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            demoValue = TextView(this@MainActivity).apply {
                text = "Test value: " + getCurrentTestValue()
                textSize = 13f
                setTextColor(Color.rgb(140, 220, 175))
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(4, 0, 4, 0)
            }
            addView(demoValue, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
            addView(Button(this@MainActivity).apply {
                text = "−1"
                setOnClickListener { adjustTestValue(-1) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.45f))
            addView(Button(this@MainActivity).apply {
                text = "+1"
                setOnClickListener { adjustTestValue(1) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.45f))
            addView(Button(this@MainActivity).apply {
                text = "Reset"
                setOnClickListener {
                    setCurrentTestValue(12345)
                    refreshProbeDisplay()
                    status.text = "Test fixture reset to 12345. Select Int32 and use First Scan."
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f))
            addView(Button(this@MainActivity).apply {
                text = "Use"
                setOnClickListener { scanValue.setText(getCurrentTestValue().toString()) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.45f))
        }
        val scanRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(scanValue, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f))
            firstButton = Button(this@MainActivity).apply {
                text = "First"
                setOnClickListener { startScan(true) }
            }
            nextButton = Button(this@MainActivity).apply {
                text = "Next"
                isEnabled = false
                setOnClickListener { startScan(false) }
            }
            addView(firstButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.55f))
            addView(nextButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.55f))
        }
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(this@MainActivity).apply {
                text = "Reset"
                setOnClickListener { resetScan() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f))
            addView(Button(this@MainActivity).apply {
                text = "Shizuku"
                setOnClickListener { requestShizukuPermission() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f))
            addView(Button(this@MainActivity).apply {
                text = "Diagnostics"
                setOnClickListener { runDiagnostics() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        status = TextView(this).apply {
            text = "Open Agent Test Host, copy its token, and connect to scan that app. Without a connection, this only scans MemoryGG itself."
            textSize = 12f
            setTextColor(Color.rgb(218, 224, 238))
            setPadding(0, 8, 0, 8)
        }
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        diagnostics = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.rgb(150, 163, 185))
            setPadding(0, 8, 0, 4)
        }
        val scroll = ScrollView(this).apply { addView(results) }
        root.addView(demoRow)
        root.addView(selectRow)
        root.addView(scanRow)
        root.addView(actionRow)
        root.addView(status)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(diagnostics, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        refreshShizukuState()
    }

    private fun toggleAgentConnection() {
        if (agentService != null || agentBound) {
            agentService = null
            frozen.clear()
            if (agentBound) { try { unbindService(agentConnection) } catch (_: Throwable) { }; agentBound = false }
            connectAgentButton.text = "Connect agent"
            status.text = "Disconnected. Scanner returned to MemoryGG's own process."
            refreshProbeDisplay()
            return
        }
        val targetPackage = agentPackageInput.text.toString().trim()
        val token = agentTokenInput.text.toString().trim()
        if (targetPackage.isBlank() || token.isBlank()) {
            status.text = "Enter the target package and paste its pairing token."
            return
        }
        try {
            val component = ComponentName(targetPackage, AGENT_SERVICE_CLASS)
            agentBound = bindService(Intent().setComponent(component), agentConnection, Context.BIND_AUTO_CREATE)
            status.text = if (agentBound) "Connecting and verifying pairing token..." else
                "Could not bind. Check the package and that Agent Test Host is installed and opened."
        } catch (t: Throwable) {
            agentBound = false
            status.text = "Connection failed: " + t.javaClass.simpleName + ": " + t.message
        }
    }

    private fun getCurrentTestValue(): Int =
        try { agentService?.getProbeValue() ?: nativeGetTestValue() } catch (_: Throwable) { nativeGetTestValue() }

    private fun setCurrentTestValue(value: Int) {
        try {
            val agent = agentService
            if (agent != null) agent.setProbeValue(value) else nativeSetTestValue(value)
        } catch (t: Throwable) {
            status.text = "Could not update probe: " + t.javaClass.simpleName
        }
    }

    private fun refreshProbeDisplay() {
        if (!::demoValue.isInitialized) return
        demoValue.text = "Test value (" + (if (agentService != null) "target" else "local") + "): " + getCurrentTestValue()
    }

    private fun adjustTestValue(delta: Int) {
        val next = (getCurrentTestValue().toLong() + delta)
            .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        setCurrentTestValue(next)
        refreshProbeDisplay()
        status.text = "Test fixture is now " + next + ". Use Next Scan with Increased/Decreased/Changed to refine matches."
    }

    private fun startScan(first: Boolean) {
        val type = typeSpinner.selectedItemPosition
        val condition = conditionSpinner.selectedItemPosition
        val value = scanValue.text.toString().trim()
        if (first && value.isEmpty()) { status.text = "Enter a number for First Scan."; return }
        if (!first && condition == 0 && value.isEmpty()) { status.text = "Exact-value Next Scan needs a number."; return }
        if (first) frozen.clear()
        firstButton.isEnabled = false
        nextButton.isEnabled = false
        status.text = if (first) "Scanning this process's writable memory..." else "Refining matches..."
        scanExecutor.execute {
            val result = try {
                val agent = agentService
                if (agent != null) { if (first) agent.scanFirst(value, type) else agent.scanNext(value, type, condition) }
                else { if (first) nativeScanFirst(value, type) else nativeScanNext(value, type, condition) }
            } catch (t: Throwable) {
                "{\"ok\":false,\"message\":\"Native scan error: " + t.javaClass.simpleName + "\"}"
            }
            runOnUiThread {
                firstButton.isEnabled = true
                nextButton.isEnabled = true
                showResults(result, type)
            }
        }
    }

    private fun showResults(source: String, type: Int) {
        try {
            val data = JSONObject(source)
            val message = data.optString("message", "Scan finished.")
            val count = data.optInt("count", 0)
            val scanned = data.optLong("scannedBytes", 0)
            val capped = data.optBoolean("truncated", false)
            val rows = data.optJSONArray("matches")
            displayedMatches.clear()
            if (rows != null) for (i in 0 until minOf(rows.length(), 250)) {
                val row = rows.getJSONObject(i)
                val addressText = row.getString("address")
                displayedMatches.add(ScanMatch(
                    java.lang.Long.parseUnsignedLong(addressText.removePrefix("0x"), 16),
                    addressText,
                    row.getString("value")
                ))
            }
            results.removeAllViews()
            if (displayedMatches.isEmpty()) {
                results.addView(TextView(this).apply {
                    text = "No matches to show."
                    setTextColor(Color.LTGRAY)
                    setPadding(10, 12, 10, 12)
                })
            } else displayedMatches.forEach { addMatchRow(it, type) }
            val sizeText = if (scanned > 0) " · " + (scanned / (1024 * 1024)) + " MiB scanned" else ""
            val shownText = if (count > displayedMatches.size) " · showing " + displayedMatches.size + " of " + count else ""
            status.text = message + sizeText + shownText + if (capped) " · scan capped safely" else ""
            nextButton.isEnabled = count > 0
        } catch (t: Throwable) {
            status.text = "Could not read scan results: " + t.javaClass.simpleName + ": " + t.message
        }
    }

    private fun addMatchRow(match: ScanMatch, type: Int) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 7, 10, 7)
            setBackgroundColor(Color.rgb(28, 32, 44))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 5 }
        }
        card.addView(TextView(this).apply {
            text = match.addressText
            textSize = 12f
            setTextColor(Color.rgb(130, 190, 255))
        })
        val valueView = TextView(this).apply {
            text = "Value: " + match.value
            textSize = 16f
            setTextColor(Color.WHITE)
        }
        card.addView(valueView)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "Edit"
            setOnClickListener { editMatch(match, type, valueView) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val freezeButton = Button(this).apply {
            text = if (frozen.containsKey(match.address)) "Unfreeze" else "Freeze"
        }
        freezeButton.setOnClickListener {
            if (frozen.remove(match.address) != null) {
                freezeButton.text = "Freeze"
                status.text = "Unfrozen " + match.addressText
            } else {
                frozen[match.address] = Pair(match.value, type)
                freezeButton.text = "Unfreeze"
                status.text = "Freezing " + match.addressText + " at " + match.value + " while MemoryGG remains open."
            }
        }
        row.addView(freezeButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
        results.addView(card)
    }

    private fun editMatch(match: ScanMatch, type: Int, valueView: TextView) {
        val edit = EditText(this).apply {
            setSingleLine(true)
            setText(match.value)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        AlertDialog.Builder(this)
            .setTitle("Edit " + typeName(type))
            .setMessage(match.addressText)
            .setView(edit)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Write") { _, _ ->
                val newValue = edit.text.toString().trim()
                scanExecutor.execute {
                    val result = try { val agent = agentService; if (agent != null) agent.writeValue(match.address, newValue, type) else nativeWriteValue(match.address, newValue, type) }
                    catch (t: Throwable) { "Write failed: " + t.javaClass.simpleName + ": " + t.message }
                    runOnUiThread {
                        status.text = result
                        if (result.startsWith("PASS:")) {
                            match.value = newValue
                            valueView.text = "Value: " + newValue
                            if (frozen.containsKey(match.address)) frozen[match.address] = Pair(newValue, type)
                        }
                    }
                }
            }.show()
    }

    private fun typeName(type: Int) = listOf("Int32", "Float32", "Int64", "Float64").getOrElse(type) { "numeric" }

    private fun resetScan() {
        scanExecutor.execute {
            nativeClearScan()
            try { agentService?.clearScan() } catch (_: Throwable) { }
            frozen.clear()
            runOnUiThread {
                displayedMatches.clear()
                results.removeAllViews()
                nextButton.isEnabled = false
                status.text = "Scan results and freeze table cleared."
            }
        }
    }

    private fun requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) appendDiagnostic("Shizuku is not connected. Start it using Wireless debugging.")
            else if (Shizuku.isPreV11()) appendDiagnostic("Shizuku API v11 or newer is required.")
            else Shizuku.requestPermission(REQUEST_CODE)
        } catch (t: Throwable) {
            appendDiagnostic("Permission request failed: " + t.javaClass.simpleName + ": " + t.message)
        }
    }

    private fun runDiagnostics() {
        appendDiagnostic("== MemoryGG diagnostics ==")
        appendDiagnostic("App UID: " + android.os.Process.myUid())
        appendDiagnostic("App PID: " + android.os.Process.myPid())
        appendDiagnostic("Native self-test: " + nativeSelfTest())
        if (!Shizuku.pingBinder()) { appendDiagnostic("Shizuku: NOT CONNECTED."); return }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            appendDiagnostic("Shizuku connected but permission is not granted. Tap Shizuku.")
            return
        }
        appendDiagnostic("Shizuku binder and permission: PASS")
        if (Shizuku.getVersion() < 10) { appendDiagnostic("UserService requires Shizuku API 10+."); return }
        if (serviceBound) { appendDiagnostic("UserService already connected."); return }
        try {
            serviceBound = true
            Shizuku.bindUserService(userServiceArgs, userServiceConnection)
        } catch (t: Throwable) {
            serviceBound = false
            appendDiagnostic("UserService start failed: " + t.javaClass.simpleName + ": " + t.message)
        }
    }

    private fun refreshShizukuState() {
        val state = try {
            when {
                !Shizuku.pingBinder() -> "NOT CONNECTED"
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "CONNECTED; permission granted"
                else -> "CONNECTED; permission not granted"
            }
        } catch (t: Throwable) { "Unavailable: " + t.javaClass.simpleName + ": " + t.message }
        appendDiagnostic("Shizuku status: " + state)
    }

    private fun appendDiagnostic(line: String) {
        if (!::diagnostics.isInitialized) return
        diagnostics.append(if (diagnostics.text.isNullOrEmpty()) line else "\n" + line)
    }

    override fun onDestroy() {
        agentService = null
        if (agentBound) { try { unbindService(agentConnection) } catch (_: Throwable) { }; agentBound = false }
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        if (serviceBound) {
            try { Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true) } catch (_: Throwable) { }
            serviceBound = false
        }
        frozen.clear()
        freezeExecutor.shutdownNow()
        scanExecutor.shutdownNow()
        super.onDestroy()
    }

    private external fun nativeSelfTest(): String
    private external fun nativeGetTestValue(): Int
    private external fun nativeSetTestValue(value: Int): Int
    private external fun nativeScanFirst(value: String, type: Int): String
    private external fun nativeScanNext(value: String, type: Int, condition: Int): String
    private external fun nativeWriteValue(address: Long, value: String, type: Int): String
    private external fun nativeClearScan()

    companion object {
        private const val REQUEST_CODE = 1001
        private const val AGENT_SERVICE_CLASS = "com.kentaakakashi.memorygg.agenthost.AgentHostService"
        init { System.loadLibrary("memorygg") }
    }
}
