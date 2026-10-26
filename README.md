# MemoryGG — rootless target-process agent prototype

MemoryGG is restarting its memory-access architecture. Shizuku shell access is not the scan backend: the target phone denied shell access to other processes' maps and memory.

## Current architecture

- MemoryGG controller APK: scan controls, results, editing, and freeze UI.
- MemoryGG Agent Test Host APK: a separate Android app whose native scanner runs within its own process.
- Authenticated Binder bridge: the controller binds to the host service and must supply the random pairing token displayed by the host.
- Native engine: the same C++ scanner source is compiled into both APKs. When connected, calls execute inside the host process; otherwise, the controller only scans itself.

The first target is a test host, not arbitrary installed games. A game must explicitly contain or load the agent before the bridge can inspect its process. This does not bypass Android's sandbox for apps that have not integrated the agent. Rootless APK injection and compatibility research are a later milestone.

## Test the agent bridge

1. Download and extract the MemoryGG-rootless-test-apks artifact from a successful GitHub Actions run.
2. Install both MemoryGG-controller-debug.apk and MemoryGG-agent-host-debug.apk.
3. Open MemoryGG Agent Test Host. Copy its long pairing token and leave the app installed.
4. Open MemoryGG. The target package defaults to com.kentaakakashi.memorygg.agenthost.debug. Paste the token and tap Connect agent.
5. Ensure the probe shows target. Keep type on Int32, tap Reset and Use, then First.
6. Switch to Agent Test Host and tap +1. Return to MemoryGG, choose Increased or Changed, and tap Next. Try Edit or Freeze on a match.

The host displays its PID and UID. This test demonstrates that scan commands execute inside a distinct app process; it does not demonstrate support for unrelated third-party games.

## Important limitations

- No root is used for the test-host architecture.
- A service in an app's default process can access that process's own memory; a separate app cannot use this to gain unrestricted access to another app.
- Candidate addresses belong to the target process and must only be sent back to that same agent. Do not write them from the controller's local native library.
- Game integration may require APK modification and re-signing. Split APKs, signature/licensing checks, integrity checks, and anti-tamper mechanisms may make a game incompatible.
- Keep experiments to offline games you own or are authorized to modify.

## Build

GitHub Actions runs gradle assembleDebug, validates JNI entry points in both ARM64 native libraries, and uploads the two debug APKs as MemoryGG-rootless-test-apks.