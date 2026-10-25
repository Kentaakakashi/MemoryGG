# MemoryGG — diagnostic starter

Rootless Android memory-access feasibility prototype using Kotlin, JNI/C++, and Shizuku.

## What this first milestone proves
- The app can bind to Shizuku and report its availability/version.
- A native C++ diagnostic can read and change a known value **inside this app's own process**.
- The app can ask Shizuku to run a harmless shell identity check (`id`) and display the result.

**This does not prove arbitrary cross-app/game memory access.** Android/Linux process isolation and SELinux may block it even when Shizuku works. This starter deliberately does not attempt to bypass those controls.

## Build on GitHub (phone-only)
1. Open **Actions** and enable GitHub Actions if prompted.
2. Open **Actions → Build MemoryGG diagnostic APK → Run workflow**.
3. Wait for the run to finish, open the successful run, and download the `MemoryGG-debug-apk` artifact.
4. Extract the ZIP artifact on your phone and install the APK. Android may ask you to allow installs from the browser/file manager.

The workflow also runs on pushes to `main`.

## Shizuku setup
1. Install Shizuku from its official project page: https://shizuku.rikka.app/
2. Start it using Wireless debugging (no PC needed on Android 11+).
3. Open MemoryGG and grant its Shizuku request when prompted.
4. Tap **Run diagnostics**.

## Expected results
- `Native self-test: PASS` means JNI/C++ can modify a value in its own process.
- `Shizuku: CONNECTED` means the app can communicate with Shizuku.
- Shell identity output shows the identity of the command launched through Shizuku.

These results are separate from whether Android permits access to a different app's process. Do not interpret them as proof that game RAM can be edited.
