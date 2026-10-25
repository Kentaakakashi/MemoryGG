# MemoryGG — in-process scanner prototype

MemoryGG is a phone-buildable Android prototype with a Kotlin scanner interface, a native C++ engine, and optional Shizuku privilege diagnostics.

## Implemented in this build

- First Scan for Int32, Float32, Int64, or Float64 exact values.
- Next Scan refinements: exact value, increased, decreased, unchanged, or changed.
- Scan result addresses and current value snapshots.
- Edit/write a candidate value.
- Freeze/unfreeze a candidate while MemoryGG remains open.
- Bounded scan budget (96 MiB and 3,000 recorded matches) with writable map filtering.
- Shizuku UserService diagnostics showing actual UID and SELinux context.
- The scan/edit engine only accesses the process hosting the engine.

## Critical limitation: stock Android process isolation

Your device test showed Shizuku UserService running as UID 2000 (shell) with SELinux context u:r:shell:s0. It could not read MemoryGG's own process maps and could not open its proc memory file. The kernel call returned EPERM. This is direct evidence that a Shizuku shell process is not an external game-memory backend on your current phone.

The scan/editor screen is a native engine milestone, **not yet a GameGuardian replacement for other apps**. It operates inside MemoryGG itself. To scan a separate game's live memory without root, the next major engineering step is an in-process agent inside the target application (for example, patching a compatible offline APK and loading the agent there) plus a controller connection. That APK patching/agent bridge is not implemented in this build.

APK patching can require uninstalling the developer-signed original, can fail on split APKs or native integrity checks, and may violate a game's rules. Do not test this with online/multiplayer games or anything handling real money.

## Build on phone

1. Open Actions → Build MemoryGG diagnostic APK.
2. Pick the newest successful run on the main branch.
3. Download the MemoryGG-debug-apk artifact, extract the ZIP, and install app-debug.apk.
4. Open MemoryGG and run First Scan with value 12345 and type Int32 to check the scanner engine fixture.

## Shizuku

The optional UserService follows the official Shizuku API demo:
https://github.com/RikkaApps/Shizuku-API/tree/master/demo
