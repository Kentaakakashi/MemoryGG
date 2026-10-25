# MemoryGG — in-process scanner prototype

MemoryGG is a phone-buildable Android prototype with a Kotlin scanner interface, a native C++ engine, and optional Shizuku privilege diagnostics.

## Included scanner features

- First Scan by exact value: Int32, Float32, Int64, or Float64.
- Next Scan refinements: exact value, increased, decreased, unchanged, or changed.
- Candidate addresses and current value snapshots.
- Edit/write a candidate and freeze/unfreeze it while MemoryGG remains open.
- A controlled integer fixture so the scan flow can be tested reproducibly.
- Bounded native scan budget (96 MiB and 3,000 stored matches), with writable mappings scanned first.
- A CI check verifies the scanner JNI entry points exist in the packaged ARM64 library.
- Shizuku diagnostics report the actual service UID and SELinux context.

## Quick self-test

1. Install the latest MemoryGG-debug-apk artifact from a successful Actions run.
2. Open MemoryGG. Keep the type on Int32 and press Reset beside the green Test value (it should show 12345).
3. Press Use to put the current fixture value into the scan input.
4. Tap First. The output should show matches; the current process contains the fixture.
5. Tap +1 to change the fixture, choose Increased or Changed, then tap Next.
6. Tap Edit or Freeze on a candidate to exercise those controls. Reset returns the known fixture to 12345.

The test fixture and scanner are part of MemoryGG's own process. They are a reproducible engine test, not evidence of game-memory access.

## Critical limitation: stock Android process isolation

Your device test showed Shizuku UserService running as UID 2000 (shell) with SELinux context u:r:shell:s0. It could not read MemoryGG's own process maps and could not open its proc-memory file. The kernel memory-read call returned EPERM. This is direct evidence that a Shizuku shell process is not an external game-memory backend on your current phone.

The scan/editor screen in this build operates inside MemoryGG itself. It does not scan another installed game's live RAM. The major next engineering step for rootless game-memory support is a compatible target-app agent running inside the target process plus a controller connection. That patch/agent bridge is not implemented yet. APK patching can require reinstalling a differently signed app and may fail for split APKs, native integrity checks, or online games. Keep experiments to offline apps you own/control.

## Build on phone

Open Actions → Build MemoryGG diagnostic APK → select a successful run → download MemoryGG-debug-apk, extract the ZIP, then install app-debug.apk.

## Shizuku

The UserService flow follows the official Shizuku API demo:
https://github.com/RikkaApps/Shizuku-API/tree/master/demo
