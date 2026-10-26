# MemoryGG — rootless target-process agent prototype

MemoryGG uses a controller plus an in-process native agent. It no longer depends on Shizuku.

## Current architecture

- MemoryGG controller APK: scan controls, results, editing, and freeze UI.
- MemoryGG Agent Test Host APK: a separate Android app whose native scanner runs within its own process.
- Authenticated Binder bridge: the controller binds to the host service and must supply the random pairing token displayed by the host.
- Native engine: the same C++ scanner source is compiled into both APKs. When connected, scan/edit calls execute inside the host process; otherwise, the controller only scans itself.

The first target is a test host, not arbitrary installed games. A game must explicitly contain or load the agent before the bridge can inspect its process. This does not bypass Android's sandbox for apps that have not integrated the agent. Rootless APK injection and compatibility research are later milestones.

## Rootless game integration (Path A)

Read the [integration guide](docs/ROOTLESS_APK_INTEGRATION.md) for the supported architecture, required target-app pieces, compatibility limits, and validation sequence. The current APK is a test harness; it is not yet a repackager for arbitrary games.

## Test the agent bridge

1. Open the successful [GitHub Actions run](https://github.com/Kentaakakashi/MemoryGG/actions/runs/40281172210) and download the `MemoryGG-rootless-test-apks` artifact.
2. Extract the ZIP and install both `MemoryGG-controller-debug.apk` and `MemoryGG-agent-host-debug.apk` on your phone.
3. Open **MemoryGG Agent Test Host**. Copy its long pairing token.
4. Open **MemoryGG**. The target package defaults to `com.kentaakakashi.memorygg.agenthost.debug`. Paste the token and tap **Connect agent**.
5. Make sure the probe label says `target`. Keep the type on **Int32**, tap **Reset**, **Use**, then **First**.
6. Switch to Agent Test Host and tap **+1**. Return to MemoryGG, choose **Increased** or **Changed**, and tap **Next**. Try **Edit** or **Freeze** on a match.

The host shows its PID and UID. This test demonstrates that scan requests execute inside a distinct app process; it does not prove support for unrelated third-party games. CI compilation and native-symbol checks pass, but the bridge still needs a real-device test.

## Important limitations

- No root or Shizuku is used for this prototype.
- An agent inside an app can inspect that process's accessible memory. A separate app cannot use this bridge to gain unrestricted access to unrelated apps.
- Candidate addresses belong to the target process and must only be sent back to that same agent.
- Game integration may require APK modification and re-signing. Split APKs, signature/licensing checks, integrity checks and anti-tamper mechanisms may make some games incompatible.
- Keep experiments to offline games you own or are authorized to modify.

## Build

GitHub Actions runs `gradle assembleDebug`, validates JNI entry points in both ARM64 native libraries, and uploads two debug APKs as `MemoryGG-rootless-test-apks`.
