package com.kentaakakashi.memorygg.agenthost

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import com.kentaakakashi.memorygg.IMemoryAgent
import java.security.MessageDigest

class AgentHostService : Service() {
    @Volatile private var authorizedUid: Int = -1

    private val bridge = object : IMemoryAgent.Stub() {
        override fun getInfo(): String =
            "PID=" + Process.myPid() + " UID=" + Process.myUid() +
                " package=" + packageName + " mode=in-process"

        override fun authorize(token: String): String {
            val expected = getSharedPreferences(PREFS, MODE_PRIVATE).getString(TOKEN_KEY, null)
                ?: return "DENIED: Open Agent Test Host once to generate a pairing token."
            if (token.isBlank() || !MessageDigest.isEqual(
                    token.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))) {
                return "DENIED: Pairing token did not match."
            }
            authorizedUid = Binder.getCallingUid()
            return "AUTHORIZED: caller UID " + authorizedUid + "; target PID " + Process.myPid()
        }

        override fun selfTest(): String =
            guarded { nativeSelfTest() } ?: "DENIED: Pair the controller first."

        override fun scanFirst(value: String, type: Int): String =
            guarded { nativeScanFirst(value, type) } ?: deniedResult()

        override fun scanNext(value: String, type: Int, condition: Int): String =
            guarded { nativeScanNext(value, type, condition) } ?: deniedResult()

        override fun writeValue(address: Long, value: String, type: Int): String =
            guarded { nativeWriteValue(address, value, type) } ?: "DENIED: Pair the controller first."

        override fun clearScan(): String {
            if (!isAuthorizedCaller()) return "DENIED: Pair the controller first."
            nativeClearScan()
            return "PASS: target-process scan state cleared."
        }

        override fun getProbeValue(): Int =
            if (isAuthorizedCaller()) nativeGetTestValue() else Int.MIN_VALUE

        override fun setProbeValue(value: Int): Int =
            if (isAuthorizedCaller()) nativeSetTestValue(value) else Int.MIN_VALUE
    }

    override fun onBind(intent: Intent?): IBinder = bridge

    private fun isAuthorizedCaller(): Boolean = Binder.getCallingUid() == authorizedUid

    private inline fun guarded(block: () -> String): String? {
        if (!isAuthorizedCaller()) return null
        return try { block() } catch (t: Throwable) {
            "ERROR: " + t.javaClass.simpleName + ": " + (t.message ?: "unknown")
        }
    }

    private fun deniedResult() =
        "{\"ok\":false,\"message\":\"Pair the controller with the target host first.\",\"count\":0,\"matches\":[]}"

    private external fun nativeSelfTest(): String
    private external fun nativeScanFirst(value: String, type: Int): String
    private external fun nativeScanNext(value: String, type: Int, condition: Int): String
    private external fun nativeWriteValue(address: Long, value: String, type: Int): String
    private external fun nativeClearScan()
    private external fun nativeGetTestValue(): Int
    private external fun nativeSetTestValue(value: Int): Int

    companion object {
        private const val PREFS = "memorygg_agent_pairing"
        private const val TOKEN_KEY = "pairing_token"
        init { System.loadLibrary("memorygg") }
    }
}
