package com.kentaakakashi.memorygg

import android.system.Os
import java.io.File
import java.io.FileInputStream

/**
 * Reports Shizuku identity and tests procfs visibility only.
 * Cross-process memory access is not attempted here: this device denied it.
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
            FileInputStream(File("/proc/$appPid/mem")).use { }
            "OPEN ALLOWED; no bytes read or written"
        } catch (t: Throwable) {
            "DENIED / UNAVAILABLE (" + t.javaClass.simpleName + ": " + t.message + ")"
        }
        out.append("/proc/$appPid/mem open probe: ").append(memOpenResult).append('\n')
        out.append("External memory scan/edit is unavailable in this Shizuku shell context.")
        return out.toString()
    }

    override fun destroy() {
        System.exit(0)
    }
}
