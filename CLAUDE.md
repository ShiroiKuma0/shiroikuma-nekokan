# shiroikuma-nekokan

**白い熊 猫管** — a fork of [Catima](https://github.com/CatimaLoyalty/Android) (GPL-3.0), the
loyalty-card & membership-card wallet. Package `shiroikuma.nekokan`, label **"白い熊 猫管"**,
installable side-by-side with upstream Catima.

## Branch & remote model (same as the sister forks)

- `origin` = `git@github.com:ShiroiKuma0/shiroikuma-nekokan.git` (ssh) — our fork.
- `upstream` = `https://github.com/CatimaLoyalty/Android.git` (https).
- **`main`** tracks the latest upstream **release tag** (current base `v2.43.0`).
- **`custom`** carries all our work, rebased onto `main` on each new upstream release. **All
  development happens on `custom`.**
- **Do not rename the `protect.card_locker` code namespace** — only the installed `APP_ID` differs
  (`shiroikuma.nekokan`). Renaming would make every rebase a mass-conflict.

## Skills (`.claude/skills/`)

- **`build-apk`** — build the signed release APK (foss flavor) via the `buildApk` Gradle task, then
  deliver it automatically via the global `/after-build` skill (adb push to `/sdcard/tmp/` if a
  phone is connected, else scp to skhw) — **no transfer prompt**; never pause to ask how to
  transfer.
- **`upstream-new-version`** — check upstream Catima for a newer release tag; **⛔ before any
  rebase, present a proceed-gated descriptive table of the new upstream version's features and wait
  for 白い熊's explicit go-ahead**; then advance `main`, rebase `custom`, reset `BUILD_NUMBER`,
  build the new `+1`.
- **`publish-version`** — publish the latest tested APK as a GitHub release of the fork: tag
  `<version>` (no `v` prefix), attach the APK, refresh the fork README + `CHANGELOG-shiroikuma.md`,
  keep the default branch on `custom`. Pin `gh` with `-R ShiroiKuma0/shiroikuma-nekokan` (the
  `upstream` remote otherwise wins).

## Build, versioning, signing

- **Build env (this machine):** default `java` is JDK 11 (can't run modern Gradle). Always:
  `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk`.
- **Build:** `./gradlew buildApk` (foss-flavor release, signed; copies the APK to `~/tmp` and bumps
  `BUILD_NUMBER`). Fast dev iteration: `./gradlew :app:assembleFossDebug` (side-by-side installable
  — debug has a `.debug` applicationId suffix). We never build the `gplay` flavor.
- **Versioning** (`gradle.properties`): `VERSION_NAME`/`VERSION_CODE` track upstream release tags;
  `BUILD_NUMBER` is our increment (bumped every build, reset to 1 on each new upstream version).
  Fork `versionName = "<VERSION_NAME>+<BUILD_NUMBER zero-padded to 3 digits>"` (`2.44.0+002`) — the
  padding is applied when the name is built, `BUILD_NUMBER` stays a plain integer in
  `gradle.properties`, and it keeps builds in order wherever they are read as text.
  `versionCode = VERSION_CODE * 10000 + BUILD_NUMBER`, unpadded (Catima 168 → `1680001`, …). APK
  filename: `shiroikuma-nekokan_<versionName>_arm64-v8a.apk` (no NDK → universal APK; `arm64-v8a`
  is just the filename convention).
- **Signing:** release signed from gitignored `keystore.properties` (committed
  `keystore.properties_sample` documents the keys) →
  `~/.android-keystores/shiroikuma-nekokan.jks` (alias `nekokan`). Password recorded in
  `~/〇/[666] 私資料/[666][27] 暗号/android-keystores.org` (jks backup in `android-keystores/`
  next to it). Losing it loses the signing identity.
- **Delivery:** APK to `~/tmp`, then `/after-build` (adb push to `/sdcard/tmp/` or scp to skhw);
  **the user installs from the on-device file manager** (never `adb install`).

## Working rules (override harness defaults where noted)

- **No `Co-Authored-By: Claude` / "Generated with Claude" trailer** in commits or PR bodies — end
  the message at the last line of the body. (Overrides the harness default; global rule in
  `~/.claude/CLAUDE.md`.)
- **Never commit or push until the user says "Push".** Treat the working tree as scratch between
  "Push" commands; multiple uncommitted fixes can stack. "Push" = `git commit` + `git push origin
  custom` (and `main` after an upstream sync). The user tests each build on-device first.
- **After every successful build, deliver the APK automatically via `/after-build`** — never ask
  how to transfer it, never pause.
- **Commit subjects:** plain descriptive summary, no prefix.
- Upstream owns `CHANGELOG.md` (compiled into the app via the `copyRawResFiles` task) — fork
  changelog notes go to `CHANGELOG-shiroikuma.md` only.

## Repo layout (upstream Catima)

- `app/src/main/java/protect/card_locker/` — sources (mixed Java + Kotlin, some Compose):
  `MainActivity`, `LoyaltyCardViewActivity`, `LoyaltyCardEditActivity`, barcode handling via ZXing
  (`com.journeyapps:zxing-android-embedded`), import/export (`importexport/`), `DBHelper`/SQLite
  persistence, widgets (`ListWidget`), ACRA crash reporting (foss flavor only).
- `app/src/main/res/` — View-based layouts + some Compose; ~55 translated locales (label change
  therefore lives in the **non-translatable `sk_app_name`** string + manifest, never in the
  translated `app_name`).
- Flavors: `foss` (default, we ship this) and `gplay`. minSdk 23, targetSdk 36, JDK 21.
- Tests: `./gradlew :app:testFossReleaseUnitTest` (Robolectric).

## Fork identity (the standing customization layer)

| What | Value | Where |
| --- | --- | --- |
| App id | `shiroikuma.nekokan` | `gradle.properties` → `APP_ID` |
| Namespace | `protect.card_locker` (never rename) | `gradle.properties` → `APP_NAMESPACE` |
| Label | `白い熊 猫管` | `sk_app_name` in `values/strings.xml` + manifest `android:label` |
| Icon | black-yellow traced Catima glyph (yellow `#FFFF00` line-art on black, adaptive) | `drawable/ic_launcher_*`, `mipmap-anydpi-v26/` |
| Version logic | `forkVersionName`/`forkVersionCode` + `base { archivesName }` + `buildApk` task | `app/build.gradle.kts` |
| Signing | `keystore.properties` (gitignored) → `~/.android-keystores/shiroikuma-nekokan.jks` | `app/build.gradle.kts` |

## Current status

**Released `2.43.0+9`** (2026-07-25; tag `2.43.0+9`, APK attached, default branch `custom`;
`README.md` + `CHANGELOG-shiroikuma.md` track it). The fork so far: identity + fork versioning +
signing + skills; the black-yellow traced launcher icon (adaptive + legacy mipmaps, splash,
welcome logo, widget preview); the complete string rebrand (default + 54 locales, incl.
transliterations/inflections; card-export filename `shiroikuma-nekokan_<date>.zip`); the
**白い熊 猫管 UI** theming layer (`protect.card_locker.shiroikuma.*` — SkUiActivity page with
Settings entry + long-press-⋮ shortcut, RGBA slider color pickers with recent-color boxes,
external fonts with glyph-rendered list + per-role weight/size, border/roundness sliders to 0,
SkBlackYellow overlay replacing Material You, SkStyler runtime application incl. card rows and
the Compose About screen); the **Export/Import full-app backup** (`SkEximport` +
`SkEximportPanel` — first UI-page section with a last-export status line, settable SAF
directory, categories All cards / UI / App settings as a manifest zip with nested `cards.zip` +
type-tagged prefs JSON + fonts, merging partial import, live n/total progress with cancel via a
per-card `CatimaExporter` hook, owned black-yellow dialog surfaces with pill buttons, success
auto-close chain with Restart now / Later); the **kxkb page format** (1 px section
hairlines, text-wide 20 sp/2.5 dp + 17 sp/1.5 dp underlined headings, 36/54/72/90 dp indent
ladder); and the **保存復元 automation contract** (`SkStateExportReceiver` + `SkAutomation` —
exported token-gated `EXPORT_STATE` / `LIST_CATEGORIES`, the same category ZIP run headlessly via
`SkEximport.headlessTarget`, `path` → configured dir → `ERROR:no-directory`, single-fire plain
broadcast reply `OK:<path>|<bytes>|<human>|<n> categories` with no binder and no ordered-result
reliance, 500 ms-throttled real-count progress, an OFF-by-default switch + copy/regenerate token
row + All-files-access row inside the Export/Import section, `MANAGE_EXTERNAL_STORAGE` declared
for absolute-path writes, and the family backup name
`shiroikuma-nekokan_<yyyy-MM-dd_HH-mm-ss>.zip`).

**Untested on-device as of the 2.43.0+9 publish**: the automation acceptance checklist (gate,
category list, real export with `path` override, items subset, unknown id, no-directory,
progress broadcasts, token absent from the ZIP) still needs an adb run once the build is
installed and the switch is on. The app is **not in 自由作業盤's 保存復元 roster** yet — a
wrapper task plus the `%BR_Token_…` settings lines need adding there.
