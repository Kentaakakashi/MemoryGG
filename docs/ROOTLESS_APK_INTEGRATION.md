# Rootless game APK integration (Path A)

## What this milestone establishes

MemoryGG's supported rootless model is **an agent compiled into the target app's own process**. The current Agent Test Host proves that the JNI scanner and authenticated Binder endpoint can run in a separate target process. It is a harness, not a universal injector.

For the next real target, use a small offline game that you own and can rebuild. Integrate the agent at source/build time first; only then experiment with repackaging a compatible APK. This isolates Android sandbox limitations from APK-repackaging problems.

## Integration contract

A compatible target APK needs all of the following:

1. The native memorygg library compiled for the target ABI(s).
2. JNI entry points that call the bounded scanner in the target process.
3. An exported Binder service implementing IMemoryAgent and checking a per-install pairing token before every scan, write, or probe operation.
4. A UI or game state variable in that same process that can be changed to verify first scan, next scan, edit, and freeze.
5. A package name that MemoryGG connects to, plus an explicit user-driven launch of the target so it can create its token.

The current reference implementation lives in agenthost/. Its native bridge includes the scanner from app/src/main/cpp/native-lib.cpp; its AIDL contract is agenthost/src/main/aidl/com/kentaakakashi/memorygg/IMemoryAgent.aidl. Keep the controller and target on the same AIDL method contract when adding a new target.

## Why APK patching is not implemented as a universal button

A separate app cannot simply attach to arbitrary app memory on stock Android. Modifying an APK may be feasible only when the target is compatible and you are authorized to modify it. APKs may be split into multiple packages, use native libraries with ABI requirements, or depend on signature checks, licensing, integrity validation, or anti-tamper code. Re-signing can break those dependencies. This project does not bypass those protections or promise compatibility with arbitrary games.

## Validation sequence for a controlled offline game

- Build and install the target and controller APKs.
- Open the target and pair using its displayed token.
- Search a known integer value, change it through normal gameplay, then refine with Increased/Changed.
- Edit a result and verify the game's own UI/state reflects the change.
- Freeze it briefly and verify normal gameplay does not overwrite it.
- Repeat after process death and app restart; pairing tokens and scan state should behave as documented.
- Test only offline games you own or are explicitly authorized to modify. Do not use this prototype to tamper with multiplayer games, payments, or other users' data.

## Current status

- [x] Native in-process scanner and edit/freeze proof in a separate test-host process.
- [x] Controller-to-agent token pairing and Binder bridge.
- [x] CI build and native JNI symbol checks.
- [ ] Dedicated sample-game APK with gameplay-owned state (next implementation milestone).
- [ ] Device validation against that sample game.
- [ ] APK repackaging workflow for a specifically selected, compatible APK.

The test-host APK is not yet a modified third-party game, and the current CI checks do not claim otherwise.
