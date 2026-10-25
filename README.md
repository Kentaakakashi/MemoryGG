# MemoryGG — Shizuku diagnostics prototype

A phone-buildable Android prototype using Kotlin, JNI/C++, and Shizuku. This is a diagnostic milestone, not yet a game memory editor.

## Current diagnostics

- Native self-test: reads and changes a known integer inside MemoryGG's own process, then restores it.
- Shizuku binder and permission checks.
- Official Shizuku UserService pattern (not the private newProcess API).
- Reports the service process PID, actual UID (usually 2000 for Shizuku started through Wireless debugging), and SELinux context.
- Read-only access probes against MemoryGG's own process: whether /proc/<pid>/maps can be read and whether /proc/<pid>/mem can be opened. The memory file is never read from or written to.

These probes are not evidence that a third-party game process can be read or modified. Android process isolation, ptrace restrictions, kernel configuration, and SELinux can block it. No restrictions are bypassed by this prototype.

## Build without a PC

1. Open Actions → Build MemoryGG diagnostic APK.
2. Download the MemoryGG-debug-apk artifact from a successful run.
3. Extract the artifact ZIP on your phone and install the APK.

Pushes to main trigger the workflow automatically.

## Device setup

1. Install Shizuku from its official page: https://shizuku.rikka.app/
2. Start it with Wireless debugging (Android 11+ supports this without a PC).
3. Open MemoryGG, grant its Shizuku permission, and tap Run diagnostics.
4. Read the UserService UID, SELinux context, and procfs probe results. A denial is useful evidence, not a build failure.

## Upstream reference

The UserService implementation follows the pattern in the official Shizuku API demo: https://github.com/RikkaApps/Shizuku-API/tree/master/demo
