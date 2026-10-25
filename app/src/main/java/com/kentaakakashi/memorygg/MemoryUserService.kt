package com.kentaakakashi.memorygg

import android.system.Os
import java.io.File
import java.io.FileInputStream

/**
 * Runs inside Shizuku's separate shell/root UserService process.
 * All process-access tests in this prototype target MemoryGG's own PID.
 */
class MemoryUserService : IUserService.Stub() {
    override fun getDiagnostics(appPid: Int): String {
        val out = StringBuilder()
        val uid = Os.getuid()
        out.append("UserService PID: ").append(Os.getpid()).append('\n')
        out.append("UserService UID: ").append(uid)
        out.append(
            when (uid) {
                0 -> " (root)"
                2000 -> " (shell/ADB)"
                else -> " (other identity)"
            }
        ).append('\n')

        val selinux = try {
            File("/proc/self/attr/current").readText().trim()
        } catch (t: Throwable) {
            "unavailable (" + t.javaClass.simpleName + ": " + t.message + ")"
        }
        out.append("SELinux context: ").append(selinux).append('\n')

        if (appPid <= 0) {
            out.append("Target PID probe: invalid PID supplied\n")
            return out.toString()
        }

        out.append("Controlled target: MemoryGG app process PID ").append(appPid).append('\n')
        val mapsReadable = try {
            File("/proc/$appPid/maps").bufferedReader().use { it.readLine() != null }
        } catch (_: Throwable) {
            false
        }
        out.append("/proc/$appPid/maps readable: ")
            .append(if (mapsReadable) "YES (read-only probe)" else "NO / DENIED")
            .append('\n')

        val memOpenResult = try {
            // Open/close only: this never reads or writes bytes.
            FileInputStream(File("/proc/$appPid/mem")).use { }
            "OPEN ALLOWED; no bytes read or written"
        } catch (t: Throwable) {
            "DENIED / UNAVAILABLE (" + t.javaClass.simpleName + ": " + t.message + ")"
        }
        out.append("/proc/$appPid/mem open probe: ").append(memOpenResult).append('\n')
        out.append("This procfs probe targets MemoryGG only; not any game or other app.")
        return out.toString()
    }

    override fun probeRemoteValue(appPid: Int, address: Long): String {
        if (appPid <= 0 || address <= 0L) {
            return "Invalid test PID/address; no memory access attempted."
        }
        return try {
            nativeProbeRemoteValue(appPid, address)
        } catch (t: Throwable) {
            "Cross-process test failed safely: " + t.javaClass.simpleName + ": " + t.message
        }
    }

    override fun destroy() {
        System.exit(0)
    }

    private external fun nativeProbeRemoteValue(appPid: Int, address: Long): String

    companion object {
        init { System.loadLibrary("memorygg") }
    }
}
