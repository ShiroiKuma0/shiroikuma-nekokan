---
name: build-apk
description: Build the signed release APK of shiroikuma-nekokan (the "白い熊 猫管" loyalty-card manager — a fork of Catima) with the `buildApk` Gradle task (foss flavor), then deliver it automatically via the global /after-build skill (adb push if a phone is connected, else scp to skhw — no prompt). Always build first without asking permission to build. Use whenever the user asks to build the app, build the APK, make a release build, or build and send to the phone.
---

# Build the nekokan release APK and deliver it

> **Never ask whether to build — just build.** When this skill applies (the user asked
> to build, or you've made changes ready to test), run the build immediately. Do **not**
> ask "shall I build?". There is **no** transfer question either: after a successful build,
> deliver the APK automatically via the global **`/after-build`** skill (see below) — no
> prompts at all.

> **The push destination is ALWAYS `/sdcard/tmp/`.** Every `adb push` of the APK goes to
> `/sdcard/tmp/<apk name>` — never `/sdcard/Download/`. Create `/sdcard/tmp` if needed.

> **Never run `adb install` (or `pm install`).** You may `adb push`; **the user installs
> the APK themselves** from the phone's file manager.

> **Never `git commit` or `git push` on your own.** Building does not include committing.
> After building (and the delivery), the user tests the build. **Only when the user
> explicitly says "Push"** do you `git commit` the changes and `git push origin custom`.
> The user's **"Push"** means *commit-and-push-to-the-fork* — unrelated to the `adb push`
> file copy.

> **ALWAYS end every build by delivering the APK via the global `/after-build`
> skill — never ask how to transfer it.** Mandatory for *every* successful build, even
> verification builds. `/after-build` runs `/adb-check` UNSANDBOXED, then `/adb-push` to
> `/sdcard/tmp/` if a phone is connected, otherwise `/scp` to `skhw:~/tmp/`, and announces
> the filename. Do **not** ask "scp or adb push?" / "phone connected?".

## Build environment (this machine)

- The default `java` is **JDK 11**, which **cannot** run modern Gradle. Always export JDK 21.
- The Android SDK is **not** on a default env var; export `ANDROID_HOME` explicitly.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/home/shiroikuma/android-sdk
```

## Steps

1. **Note the output filename / version.** Read the version + counter from `gradle.properties`:
   - `grep -E 'VERSION_NAME|VERSION_CODE|BUILD_NUMBER' gradle.properties`
   - The APK will be `shiroikuma-nekokan_<VERSION_NAME>+<BUILD_NUMBER>_arm64-v8a.apk`, using the
     `BUILD_NUMBER` value **before** the build (the `buildApk` task bumps it afterward).
   - versionCode for that build = `VERSION_CODE * 10000 + BUILD_NUMBER`.

2. **Build** (release, signed, **foss flavor**) — from the repo root:
   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk
   ./gradlew buildApk --console=plain < /dev/null
   ```
   - `buildApk` runs `assembleFossRelease`, copies the signed APK to `~/tmp/<apk name>`, and
     auto-increments `BUILD_NUMBER` in `gradle.properties`.
   - It prints `>>> <path>` and `>>> versionCode <n>` (cyan) — use those to confirm the exact
     filename/code; confirm `BUILD SUCCESSFUL`.
   - We ship the **foss** flavor only (no Google Play tweaks); `gplay` exists upstream but is
     never built here. Catima has no NDK → the APK is universal; `arm64-v8a` in the filename is
     just the house naming convention.
   - A cold first build downloads deps — run with `run_in_background` if it may exceed the
     foreground timeout; subsequent builds are fast.
   - **Fast dev iteration:** `./gradlew :app:assembleFossDebug` produces a debug APK at
     `app/build/outputs/apk/foss/debug/` (installs side-by-side — the debug build has the
     `.debug` applicationId suffix); the shippable build is `buildApk` (release-signed).

3. **At the end of every build, deliver the APK via `/after-build`** — no exceptions, no
   asking. As soon as `BUILD SUCCESSFUL` appears and the signed APK is in `~/tmp/`, invoke the
   global **`/after-build`** skill; it picks adb-push (phone connected) or scp-to-skhw on its
   own and announces what landed.

## Signing

Release signing is non-interactive: `app/build.gradle.kts` reads credentials from
`keystore.properties` (gitignored; a committed `keystore.properties_sample` documents the four
keys). This fork uses its own keystore `~/.android-keystores/shiroikuma-nekokan.jks` (alias
`nekokan`); the store/key password is recorded in 白い熊's keystore archive
(`~/〇/[666] 私資料/[666][27] 暗号/android-keystores.org`, jks backup alongside it). If
`keystore.properties` is absent the release build is unsigned and won't install — restore it
(pointing `storeFile` at the `.jks`, with `keyAlias`, `keyPassword`, `storePassword`).

## Versioning (how the numbers are formed)

- `VERSION_NAME` / `VERSION_CODE` in `gradle.properties` **track upstream Catima**
  (CatimaLoyalty/Android release tags, e.g. `v2.43.0` → `VERSION_NAME=2.43.0`, `VERSION_CODE=167`).
- `BUILD_NUMBER` is **our** fork increment, bumped on every `buildApk`, reset to `1` on each new
  upstream version (see the `upstream-new-version` skill).
- Fork `versionName = "<VERSION_NAME>+<BUILD_NUMBER>"`; `versionCode = VERSION_CODE * 10000 + BUILD_NUMBER`
  (Catima 167 → `1670001`, `1670002`, …). When upstream's code climbs, the new line's codes exceed
  the old, keeping upgrades monotonic.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` /
"Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line
of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
