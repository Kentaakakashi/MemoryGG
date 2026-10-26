package com.kentaakakashi.memorygg.agenthost

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.security.SecureRandom

class AgentHostActivity : Activity() {
    private lateinit var valueLabel: TextView
    private lateinit var pairingToken: String
    private val uiHandler = Handler(Looper.getMainLooper())
    private val liveValueRefresh = object : Runnable {
        override fun run() {
            if (::valueLabel.isInitialized) {
                valueLabel.text = "Native probe value: " + nativeGetTestValue()
                valueLabel.contentDescription = "Live native probe value " + nativeGetTestValue()
            }
            uiHandler.postDelayed(this, 250L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(Color.rgb(12, 14, 22))
        val prefs = getSharedPreferences("memorygg_agent_pairing", MODE_PRIVATE)
        pairingToken = prefs.getString("pairing_token", null) ?: createToken().also {
            prefs.edit().putString("pairing_token", it).apply()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "MemoryGG Agent Test Host"
            textSize = 25f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "This is a separate target app. The native engine and probe live inside this process."
            textSize = 14f
            setTextColor(Color.rgb(180, 190, 210))
            setPadding(0, 8, 0, 16)
        })
        root.addView(TextView(this).apply {
            text = "Pairing token (treat it like a password)"
            textSize = 13f
            setTextColor(Color.rgb(140, 220, 175))
        })
        root.addView(TextView(this).apply {
            text = pairingToken
            setTextIsSelectable(true)
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.rgb(28, 32, 44))
        })
        root.addView(Button(this).apply {
            text = "Copy pairing token"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("MemoryGG pairing token", pairingToken))
                text = "Token copied"
            }
        })
        root.addView(TextView(this).apply {
            text = "Target PID: " + Process.myPid() + "   UID: " + Process.myUid()
            textSize = 12f
            setTextColor(Color.rgb(130, 190, 255))
            setPadding(0, 14, 0, 8)
        })
        valueLabel = TextView(this).apply {
            textSize = 19f
            setTextColor(Color.rgb(140, 220, 175))
            gravity = Gravity.CENTER_VERTICAL
            text = "Native probe value: " + nativeGetTestValue()
        }
        root.addView(valueLabel)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "−1"
            setOnClickListener { changeValue(-1) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "+1"
            setOnClickListener { changeValue(1) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "Reset"
            setOnClickListener {
                nativeSetTestValue(12345)
                valueLabel.text = "Native probe value: " + nativeGetTestValue()
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)
        root.addView(TextView(this).apply {
            text = "In MemoryGG, enter package " + packageName +
                ", paste the token, then connect. Scan Int32 12345; change the value here with +1; then refine using Increased or Changed."
            textSize = 13f
            setTextColor(Color.rgb(190, 198, 214))
            setPadding(0, 18, 0, 0)
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        uiHandler.removeCallbacks(liveValueRefresh)
        uiHandler.post(liveValueRefresh)
    }

    override fun onPause() {
        uiHandler.removeCallbacks(liveValueRefresh)
        super.onPause()
    }

    private fun changeValue(delta: Int) {
        val next = (nativeGetTestValue().toLong() + delta)
            .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        nativeSetTestValue(next)
        valueLabel.text = "Native probe value: " + nativeGetTestValue()
    }

    private fun createToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private external fun nativeGetTestValue(): Int
    private external fun nativeSetTestValue(value: Int): Int

    companion object {
        init { System.loadLibrary("memorygg") }
    }
}
