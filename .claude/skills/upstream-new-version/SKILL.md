---
name: upstream-new-version
description: Rebase the shiroikuma-nekokan fork onto a new upstream release of CatimaLoyalty/Android (Catima, the loyalty-card app this is forked from). Use when the user says a new upstream Catima version/tag is out, asks to update/sync to upstream, bump to the new Catima release, check for a new version, or rebase custom onto the latest upstream tag.
---

# Rebase the fork onto a new upstream Catima release

This codifies the "new upstream version" half of the fork workflow. Goal: move `main` to the new
upstream release tag, replay our `custom` customizations on top, and produce a fresh `+1` build.

> **Never `git push` or `git commit` unprompted, and never `adb install`.** Same hard rules as
> everyday development (see CLAUDE.md). After the rebase + build you stop and let the user test; you
> only `git push` when they explicitly say **"Push"**.

> **HARD GATE — the new-features briefing.** Before ANY rebasing (step 3 below), you MUST present
> 白い熊 a **descriptive table of what the new upstream version(s) introduce** and **wait for an
> explicit "proceed"**. Never skip it, never fold it into the rebase turn.

## Background — branch model & versioning

- `upstream` = `https://github.com/CatimaLoyalty/Android.git` (https). `origin` =
  `git@github.com:ShiroiKuma0/shiroikuma-nekokan.git` (ssh).
- **`main` tracks upstream releases by TAG** (Catima tags every release, e.g. `v2.43.0`). We base on
  the latest **release tag**, not bleeding `upstream/main`.
- **`custom`** carries all our work, rebased onto `main` on each new release.
- `VERSION_NAME` / `VERSION_CODE` in `gradle.properties` **track upstream** (drop any suffix like
  `-beta` from `VERSION_NAME` should one ever appear).
- `BUILD_NUMBER` is **our** fork increment; it **resets to `1`** on each new upstream version.
- Fork `versionName = "<VERSION_NAME>+<BUILD_NUMBER zero-padded to 3 digits>"` (`2.44.0+002`),
  `versionCode = VERSION_CODE * 10000 + BUILD_NUMBER` (unpadded — the padding is name-only).
  So when upstream's `versionCode` climbs (167 → 168), the new line's codes (`1680001`, …) all exceed
  the previous line's (`1670001`, …), keeping upgrades monotonic.

## Steps

1. **Check for a newer upstream release:**
   - `git fetch upstream --tags`
   - `git tag --sort=-version:refname | head` — newest Catima tag. Compare against our current base
     (the commit `main` points at).
   - Read the new tag's declared version:
     `git show <tag>:app/build.gradle.kts | grep -E 'versionCode|versionName'`.
   - If nothing newer than our base, stop and report "already current".

2. **⛔ PROCEED GATE — new-features briefing (mandatory, BEFORE any rebase):**
   - Gather what changed between our base and the new tag:
     - `git diff <oldtag>..<newtag> -- CHANGELOG.md` — Catima keeps a real per-version
       `CHANGELOG.md`; the added section(s) are the authoritative feature list.
     - Skim `git log --oneline <oldtag>..<newtag>` (and `fastlane/metadata/android/en-US/changelogs/`
       if present) for anything the changelog undersells.
   - Present 白い熊 a **descriptive table** of the new upstream version's changes — one row per
     feature/change, e.g.:

     | Area | Change | What it means for us |
     | --- | --- | --- |
     | Import/export | … | … |

     Cover features, fixes, and anything touching our patched files (flag those rows). If several
     upstream versions are being jumped at once, cover each.
   - **STOP and ask whether to proceed with the rebase.** Only an explicit go-ahead ("proceed",
     "go", …) continues; otherwise stay on the current base.

3. **Advance `main` to the new release tag** (no fork work lives on `main`):
   - `git checkout -B main <newtag>`

4. **Rebase `custom` onto the new `main`:**
   - `git checkout custom`
   - `git rebase main`
   - Resolve conflicts so **all** our customizations survive (see the table below). Reconcile,
     don't drop. If upstream restructured a file we patch, port our change to the new structure
     rather than forcing the old diff. **If conflicts are significant, stop and plan with the
     user** before continuing.

5. **Update versioning in `gradle.properties`:**
   - Set `VERSION_NAME` (new upstream `versionName`) and `VERSION_CODE` (new upstream `versionCode`).
   - **Reset `BUILD_NUMBER` to `1`.**

6. **Verify our customizations are intact** after resolving the rebase:

   | What | Expected value | Where |
   | --- | --- | --- |
   | Installed app id | `shiroikuma.nekokan` | `gradle.properties` → `APP_ID` |
   | Code namespace | `protect.card_locker` (unchanged from upstream — never rename) | `gradle.properties` → `APP_NAMESPACE` |
   | App label | `白い熊 猫管` | `sk_app_name` in `app/src/main/res/values/strings.xml` + `android:label` in `AndroidManifest.xml` |
   | Launcher icon | black-yellow traced Catima glyph | `drawable/ic_launcher_*`, `mipmap-anydpi-v26/` |
   | Fork version logic + signing | `forkVersionName`/`forkVersionCode`, keystore.properties signing, `base { archivesName }`, `buildApk` task | `app/build.gradle.kts` |
   | `namespace = APP_NAMESPACE`, `applicationId = APP_ID` | property-driven, not hardcoded | `app/build.gradle.kts` |
   | Fork props | `VERSION_NAME`/`VERSION_CODE`/`BUILD_NUMBER`/`APP_ID`/`APP_NAMESPACE` block | `gradle.properties` |
   | keystore ignore + sample | `/keystore.properties` ignored; `keystore.properties_sample` present | `.gitignore`, repo root |
   | Feature patches | every shipped fork feature (see CLAUDE.md "Current status") | their source files |

   Conflict-prone files: `gradle.properties`, `app/build.gradle.kts`,
   `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, and — as feature
   work lands — the sources we patch.

   Sanity check the build script still evaluates:
   `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk ./gradlew :app:tasks --console=plain | head`.

7. **Build the new `+1`** via the **build-apk** skill
   (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk ./gradlew buildApk < /dev/null`);
   build-apk then delivers the APK automatically via `/after-build` (adb push if a phone is
   connected, else scp to skhw — no prompt). This is the first build of the new upstream line
   (`<newVersion>+1`).

8. **Stop.** Let the user test. Commit/push only on their explicit **"Push"**. Because the rebase
   rewrites `custom`'s history: `git push --force-with-lease origin custom`; `main` is
   `git push origin main` (fast-forward / new tag base). Run upstream-side tests too:
   `./gradlew :app:testFossReleaseUnitTest` (or `:app:test`) should stay green.

## Notes

- Keep our changes a **small, legible layer** on top of upstream — prefer rebasing (linear history)
  over merging, so the customization set stays easy to audit and replay.
- Do **not** rename the `protect.card_locker` code namespace (only `APP_ID` differs) — renaming
  would make every rebase a mass-conflict.
- Upstream owns `CHANGELOG.md` (it is even compiled into the app via `copyRawResFiles`) — our fork
  changelog lives in `CHANGELOG-shiroikuma.md` instead; never move fork notes into upstream's file.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` /
"Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of
the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
