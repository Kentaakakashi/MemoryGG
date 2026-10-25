# MemoryGG — Shizuku diagnostics prototype

A phone-buildable Android prototype using Kotlin, JNI/C++, and Shizuku. This is a capability-diagnostics milestone, not yet a full game memory editor.

## Current diagnostics

- Native self-test reads and changes a known integer inside MemoryGG's own process, then restores it.
- Checks the Shizuku binder and permission.
- Uses the official Shizuku UserService pattern instead of the private newProcess API.
- Reports the service process PID, actual UID, and SELinux context.
- Probes whether MemoryGG's own /proc/<pid>/maps is readable and whether /proc/<pid>/mem can be opened (open/close only; no bytes read or written).
- Uses process_vm_readv/process_vm_writev from the UserService to read a known integer in MemoryGG's own process, temporarily change it, verify it, and restore it. The test uses no user-supplied PID or address and does not target a game or other app.

A denied operation is useful evidence about Android's permission boundary, not a build failure. These self-targeted tests do not prove third-party game access. Android process isolation, ptrace restrictions, kernel configuration, and SELinux may block access. This prototype does not bypass those restrictions.

## Build without a PC

1. Open Actions → Build MemoryGG diagnostic APK.
2. Download the MemoryGG-debug-apk artifact from a successful run.
3. Extract the artifact ZIP on your phone and install the APK.

Pushes to main trigger the workflow automatically.

## Device setup

1. Install Shizuku from its official page: https://shizuku.rikka.app/
2. Start it with Wireless debugging (Android 11+ supports this without a PC).
3. Open MemoryGG, grant its Shizuku permission, and tap Run diagnostics.
4. Read the service UID/SELinux context and the controlled process-memory test result.

## Upstream reference

The UserService implementation follows the official Shizuku API demo: https://github.com/RikkaApps/Shizuku-API/tree/master/demo
