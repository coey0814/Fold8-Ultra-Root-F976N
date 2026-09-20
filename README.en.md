# Fold8-Ultra-Root-F976N

<p align="right">
  <a href="README.md">한국어</a> · <b>English</b>
</p>

<p align="center">
  <img src="docs/icon.png" width="132" height="132" alt="Fold8 Ultra Root icon">
</p>

<p align="center">
  One-tap <b>KernelSU</b> installer for the Samsung Galaxy Z Fold 8 Ultra (<b>SM-F976N</b>, codename <b>q8q</b>)<br>
  powered by the <b>CVE-2026-43499</b> late-load exploit via <b>Shizuku</b>.
</p>

<p align="center">
  <a href="../../releases/latest">Download latest APK</a>
</p>

> ## ⚠️ WARNING
> **I am not responsible for bricked phones.**
> - This tool uses a temporary (late-load) root that does not flash any partition, so it does **not**
>   trip the KNOX warranty bit.
> - A wrong firmware match or a bad run can still cause a **bootloop or hard brick**. **Only run this
>   on a device you own**, and only on the firmware revisions below.
> - While root is active, some apps (banking / government / DRM) may detect root and refuse to run
>   (resolved after a reboot).
> - No warranty; use at your own risk.

---

## Screenshot

<p align="center">
  <img src="docs/screenshot.png" width="320" alt="Application screenshot">
</p>

---

## Supported firmware

| Revision | PDA | Exploit payload | ksud |
|---|---|---|---|
| rev1 | `F976NKSU1AZGI` | `preload.so` | `ksud` |
| rev2 | `F976NKSS2AZH7` | `preload-azh7.so` | `ksud-azh7` |
| rev3 | `F976NKSU3AZI5` | `preload-azi5.so` | `ksud-azi5` |

The app auto-detects the running firmware from `Build.FINGERPRINT` / `Build.DISPLAY` / `Build.ID`
and picks the matching payload. You can also select the target manually from the **TARGET FIRMWARE** card.

## Requirements

- Samsung Galaxy Z Fold 8 Ultra **SM-F976N (q8q)** on one of the firmwares above.
- **[Shizuku](https://github.com/RikkaApps/Shizuku)** installed and running (wireless debugging / adb mode is enough).
- The **KernelSU manager** app installed (to use the root once the module is late-loaded).
- This app does **not** flash or modify any partition; it loads the KernelSU module at runtime,
  so root must be re-applied after each reboot (or use the built-in auto-root).

## Usage

1. Install the APK from [Releases](../../releases/latest).
2. Start **Shizuku** (wireless debugging pairing), then open this app.
3. Tap **GRANT PERMISSION** and approve the Shizuku dialog. The ring goes `READY`.
4. Tap **RUN EXPLOIT**. The log shows the run; on success the ring shows `LIVE` (KernelSU active).
5. Open the **KernelSU manager** to grant root to your apps.

### Auto-root on boot

Enable **Auto-root on boot**. After a reboot a foreground service waits for Shizuku (up to 5 minutes)
and re-applies root once per boot. It checks whether KernelSU is already loaded and **skips** if so.

### Notes and limitations

- Each run retries up to 3 times (60 s apart, 60 s logcat watch per attempt) and aborts on reboot detection.
- On this ROM an unprivileged app cannot read `/proc/modules`, so KernelSU is detected through Shizuku
  (or via `su` if this app has been granted root in KernelSU). Without Shizuku, the app cannot report `LIVE`.
- Run only one at a time — the runner holds a global lock.

## Build

Requirements: **JDK 17** and **Android SDK 34** (no NDK needed; the payloads are prebuilt assets).

```sh
JAVA_HOME=/path/to/jdk-17 ./gradlew :app:assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

`local.properties` and the signing keystore are intentionally **not** committed. To build a release,
point `sdk.dir` at your SDK and either create your own keystore or use the debug signing config.
The published APK is signed with the AOSP **testkey** (`alias testkey`, password `android`), so
updates install in place over an existing testkey-signed build.

### Bundled asset integrity (SHA-256)

| Asset | SHA-256 (prefix) |
|---|---|
| `preload.so` | `947099db3862efcc…` |
| `preload-azh7.so` | `0408d45a1ba33701…` |
| `preload-azi5.so` | `db1b56b942ff03e6…` |
| `ksud` | `c4830698accaa951…` |
| `ksud-azh7` | `b140d354cffed359…` |
| `ksud-azi5` | `784e4ea7ddee2f8c…` |

## Credits

This project would not exist without the work of others:

- [YuKongA/ghostlock-app](https://github.com/YuKongA/ghostlock-app) — GhostLock app (UI / logic base, Apache-2.0)
- [diabl0w/ghostlock-q8q](https://github.com/diabl0w/ghostlock-q8q) — q8q exploit payload
- [NebuSec/CyberMeowfia](https://github.com/NebuSec/CyberMeowfia) & [polygraphene/CyberMeowfia](https://github.com/polygraphene/CyberMeowfia) — original CVE-2026-43499 research
- [tiann/KernelSU](https://github.com/tiann/KernelSU) — KernelSU (GPL-3.0)
- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) — Shizuku (Apache-2.0)
- [BuSung-dev/Root-My-Galaxy](https://github.com/BuSung-dev/Root-My-Galaxy) — project structure / release reference
- [kuuky29/UniRoot](https://github.com/kuuky29/UniRoot) — reference for the auto-root on boot feature

See [NOTICE](NOTICE) for details.

## License

Licensed under the **Apache License 2.0**. See [LICENSE](LICENSE).

The bundled `ksud` binaries and KernelSU kernel module remain under **GPL-3.0** (see [NOTICE](NOTICE)).
