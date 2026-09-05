# 白い熊 猫管 — fork changelog

Everything built on top of stock [Catima](https://github.com/CatimaLoyalty/Android). Upstream owns
`CHANGELOG.md` (it was compiled into the app until Catima 2.45.0 dropped the embedded copy); fork
notes live here.

## 2.45.0+004 — current

**The app now contains no tracker at all.** A tracker scan of `2.45.0+003` reported one hit — ACRA,
upstream Catima's crash reporter — and this build removes the library rather than switching it off.
Built on Catima `v2.45.0` (versionCode 1002).

### ACRA removed, not disabled
- Upstream's crash reporter was already benign in behaviour: it never sent anything automatically,
  it opened a mail draft the user had to review and send by hand, and a settings switch turned even
  the asking off. What it could not stop being was **present** — a scanner matches the library's
  classes in the dex, so the only way to a clean report is for those classes not to be there.
- The dependency (`acra-mail` + `acra-dialog`) and its version-catalog entries are gone, so nothing
  ACRA-shaped is compiled into the APK. Verified on the built artefact: neither `classes.dex` nor
  `classes2.dex` contains a single occurrence of the string `acra`.
- The `ACRA.init(…)` block in `LoyaltyCardLockerApplication` is gone with it, as is the
  `useAcraCrashReporter` `BuildConfig` field it was guarded by (upstream set it false only for the
  Google Play flavor, which this fork never builds).
- The 「Ask to send crash reports」 switch is removed from the settings screen along with the code
  that used to hide it, and the **ACRA credit disappears from About → third-party libraries**,
  which would otherwise still name a library the app no longer carries.
- A comment now stands where the dependency was, so a future rebase onto a new Catima release does
  not quietly reinstate it.
- The five now-unused `acra_*` strings are deliberately **left** in the default locale and its 54
  translations. They are inert text with no code behind them, and deleting them would collide with
  every upstream Weblate sync — the tracker was the classes, not the words.

### The privacy policy says so
- The 「Crash reporting privacy」 section of `PRIVACY.md` — which the app shows verbatim under
  About → Privacy policy — described ACRA and Google Play crash reporting. It now states that
  白い熊 猫管 contains no crash reporter and no analytics of any kind, which is the reason a tracker
  scan of this APK finds nothing.

## 2.45.0+003

Sister-app automation moves to **contract v2**: the token stops being the gate and becomes an
opt-in extra, and a second, authenticated door is added so another app can back this one up **with
its cards** and put them back on a wiped phone. Built on Catima `v2.45.0` (versionCode 1002).

### The gate — a switch that is on, and a token that is off
- `automation_enabled` now defaults **on**; a new `automation_require_token` defaults **off**. The
  reason the default flipped is the restore case: a phone that has just been wiped has nothing
  configured on it, so a gate that had to be set up first was no use for setting the phone up.
- Both checks now live in a single `SkAutomation.refuse()`. Written out separately at each entry
  point, “disabled” and “bad token” drift apart; they stay distinct errors because they are
  diagnosed differently.
- **A token sent to the app while it is not asking for one is ignored, never refused.** Tokens
  outlive the setting they were pasted for, so refusing one would turn a single switch being off
  into half a backup batch mysteriously failing.
- New 「Use authorization token?」 row in the Export/Import section. The token row now appears
  **only when that switch is on** — a 48-character secret sitting under an off switch invites being
  pasted somewhere it will do nothing. The row says in plain words what leaving the token off means
  for this app in particular, whose backup is scannable card barcodes rather than a settings dump.

### The data door — a provider, a verified caller, and a file descriptor
- New `ContentProvider` at `shiroikuma.nekokan.automation` with `describe` / `export` / `import` /
  `cancel`. A broadcast cannot say who sent it, and the caller supplies the destination an export is
  written into, so identity had to come from the framework rather than from a shared secret.
- The caller is checked three ways: its **exact package name** (never a `shiroikuma.*` prefix —
  package names are not a namespace anyone owns, so any sideloaded app may take one that is
  currently uninstalled, which is precisely the clean-phone case), the **uid** the kernel reports
  for it, and its **pinned signing certificate**. Both pins were re-derived from the sister apps'
  signed release APKs rather than copied from the contract.
- The backup travels through a **caller-supplied `ParcelFileDescriptor`**, duplicated before it
  leaves the binder call and closed in a `finally`. This app therefore never writes into another
  app's directory, and the capability expires when the file is closed.
- **`import` exists only here.** The broadcast receiver is exported with no permission; an import
  action there would let any app on the phone overwrite the wallet.
- The work runs in a foreground service, since a full card archive can take minutes and a binder
  call cannot hold that. `describe` answers synchronously and touches nothing that needs the
  Application to have started, so it is correct on a freshly installed, never-launched app.
- Manifest: the provider, the service, three `shiroikuma.automation.*` `<meta-data>` entries that
  let a backup app discover this capability **without waking the app** (a frozen package cannot be
  asked anything), and a `<queries>` element naming both caller packages — without it
  `getPackageInfo` and `getPackagesForUid` are visibility-filtered, so the identity check fails
  outright rather than merely losing the reply.

### Card photographs become a selectable sub-option — defaulting on
- New `cards.images` category, reported as a **sub-option of `cards`** and rendered indented in the
  app's own panel as well as in an automation picker. Card photos are the bulk of an archive's
  bytes, so a barcodes-only backup is now expressible.
- **It defaults on, and the reasoning matters**: the test for starting a category unticked is that
  it is large, *derived* **and** re-creatable — a generated thumbnail, a re-downloadable tile. A
  photograph of a physical card is none of those, so it is offered rather than assumed away.
- Honoured in **both** directions: export skips the images via a new `CatimaExporter` hook, and
  import strips them out of the nested archive, so the checkbox is not quietly export-only.
  Selecting `cards.images` without `cards` implies `cards`, since the images are entries inside
  `cards.zip` keyed by the card ids in its CSV.

### Fixes
- **Imported preferences are now committed synchronously.** A restoring app force-stops this one the
  moment an import reports success — it has to, or this process would write its cached preferences
  back out and undo the import — but that force-stop is a `SIGKILL`, which discards an `apply()`
  still in flight. The restore would have reported success over settings that never reached disk.
- **A stale automation job id no longer crashes the app.** The data service's early-exit paths
  returned without ever calling `startForeground`, which the platform punishes by killing the whole
  process with `ForegroundServiceDidNotStartInTimeException`. A caller retrying with a job id this
  app had already finished would have killed the wallet mid-backup instead of being ignored.
- **A failed service start no longer leaks the caller's file.** If the foreground service cannot be
  started — a background start may simply be refused — the duplicated descriptor is closed and the
  job dropped before the refusal is returned, rather than being left open for the life of the
  process.
- **Restoring a large backup no longer holds it twice in memory.** The archive is spooled to the
  cache directory (and deleted afterwards, so no plaintext copy of the wallet lingers) and the
  nested `cards.zip` is streamed straight into Catima's importer instead of being materialised,
  roughly halving peak heap on the path where the phone has enough cards to be worth restoring.

### Wording
- Category labels now say what they actually hold, in the panel, in the automation category list and
  in the provider's header alike — “All cards (barcodes and card numbers — scannable)”, “Card images
  (photos of the physical cards)”, “App settings (preferences, not card data)”. A backup app renders
  those strings verbatim, so this is where you find out what a backup contains.
- Automation progress broadcasts now carry the **category id** being written, which is what moves
  the highlight in a caller's progress panel; previously it had to guess from the item count.

`2.45.0+002` was the same feature set without the synchronous-preference-commit fix; it was built
and delivered but never released.

## 2.45.0+001

Rebased onto Catima `v2.45.0` (versionCode 1002).

No new fork features — this release moves the customization layer onto upstream's Wear OS release
and re-seats the patches that upstream restructured underneath them.

### What upstream brings
- **Wear OS companion support**: the phone app gains a Bluetooth server (`wearos/`, a
  `connectedDevice` foreground service) that serves cards to Catima's new Wear OS watch app, with
  trusted-device pairing, allow/block device lists, and a warning when a known device presents a
  changed identity token. Settings' *Privacy* category is now **Smartwatch support**; the sync
  switch is **off** until turned on, and nothing runs until it is.
- Two new Gradle modules — `:shared` (Bluetooth protocol/security, `ForegroundColorHelper`) and
  `:wear` (the watch app, which this fork does not build or ship).
- **The embedded changelog is gone**: upstream stopped copying `CHANGELOG.md` into `res/raw`, so
  About → *Version history* now links to catima.app instead of opening an in-app dialog.
- Reworked notification icons, a foreground-service start-failure notification, an `fdroidLegacy`
  product flavor (we still ship **foss** only), and constraintlayout 2.2.2.

### Fork-side work
- **Rebranding of the new user-visible strings**: the three that name *this* app — the Wear
  permission prompt, the device-removal confirmation and the sync notification title — now say
  白い熊 猫管. The token-mismatch warning deliberately keeps “the Catima Wear OS app”: that names
  the watch-side app, which is upstream's and not forked here.
- **The 白い熊 猫管 UI entry follows upstream's refactor**: `onCreatePreferences` was split into
  per-preference `setup*` functions, so the fork's Settings entry became `setupShiroikumaUiPreference()`
  alongside them rather than an inline block wedged into the middle.
- **Lithuanian**: Weblate rewrote `importCatimaMessage`, `importCatima` and `permissionReadCardsLabel`
  in this release — the new translations are kept, with our branding re-applied on top.
- **Upstream's new versionCode scheme**: Android releases now start at 1002 and advance by even
  numbers (odd ones belong to `fdroidLegacy`). The fork line therefore jumps from `1680000 + N` to
  `10020000 + N`, which stays above every 2.44.0 build, so upgrades remain monotonic.

## 2.44.0+002

Rebased onto Catima `v2.44.0` (versionCode 168).

### 保存復元 automation — a stated default, and a real cancel
- **`LIST_CATEGORIES` now answers with all four positional fields** —
  `id<TAB>label<TAB>parent<TAB>on|off`. The fourth field is this app *stating* whether an item
  starts ticked in a caller's backup-item picker, rather than the picker assuming it. Our list is
  flat, so the third field is always empty; every category is `on`, because nothing this app
  exports is large, derived *and* re-creatable. The flag lives on the category itself
  (`SkEximport.Cat.defaultOn`), so a future category can answer differently by construction.
- **The app's own Export/Import panel seeds its checkboxes from that same flag**, so the in-app
  sheet and the automation picker can never drift apart.
- **`CANCEL_EXPORT` (new, third action)**: a token-gated, fire-and-forget stop for a running
  headless export, routed through the exported receiver — the only component a third-party caller
  can reach. It answers nothing itself. It raises a `@Volatile` flag that the export reads
  **between entries**, never mid-`write()` and never by interrupting a thread: the run unwinds at
  the next boundary, **deletes its partial file** so the backup directory is left exactly as it
  was found, and sends `ERROR:cancelled` as the one terminal reply to the *original* request,
  under the existing `AtomicBoolean` guard. Sending it when nothing is running, or after the ZIP
  is already complete, is a **silent no-op** — not an error, not a reply, not a crash.
- **One unwind path**: the panel's own Cancel button now feeds the same signal, so the interactive
  and headless stops behave identically, partial-file deletion included.

### Packaging
- **Rebased onto upstream Catima 2.44.0** — which brings an “Open in image gallery” overflow action
  in the card image viewer, AGP 9.3.1 and Kotlin 2.4.10. `VERSION_NAME`/`VERSION_CODE` follow
  upstream, so this line's versionCodes (`1680001`, …) stay above the 2.43.0 line's.
- **The build counter is zero-padded to three digits in the version name** — `2.44.0+002` — so
  builds sort in build order wherever they are read as text: the APK filenames, the phone's file
  manager, and the release tags. The padding is name-only; `versionCode` arithmetic is unchanged,
  so upgrade ordering cannot be affected. Earlier tags stay as published (`2.43.0+9`).
- The upstream export-filename test now asserts the fork's `shiroikuma-nekokan_<date>.zip` rather
  than stock's `catima_<date>.zip`, which it had been failing against since that rename.

## 2.43.0+9

Based on Catima `v2.43.0` (versionCode 167).

### 保存復元 automation — the sister-app state-export contract (new)
- **Headless backup on request**: a new exported broadcast receiver (`SkStateExportReceiver`)
  runs the very same category ZIP the Export/Import panel writes — no Activity, no interaction,
  exactly one ZIP per request — so 白い熊 自由作業盤's 保存復元 project can back this app up in
  its one-run batch alongside every sister app.
- **Two token-gated actions**: `shiroikuma.nekokan.action.EXPORT_STATE` (extras `token`, optional
  `path` / `items` / `progress_action`, plus `reply_action` / `reply_package` / `reply_id`) and
  `shiroikuma.nekokan.action.LIST_CATEGORIES`, which answers instantly with one `id<TAB>label`
  line per category — the ids `items` accepts and the entry names used inside the ZIP.
- **Directory precedence**: the `path` extra (an absolute directory, created if missing, which
  overrides the configured one) → the app's configured export directory → `ERROR:no-directory`.
- **Reply channel**: a fresh broadcast carrying `reply_id` + `result`, with
  `FLAG_INCLUDE_STOPPED_PACKAGES` so a backgrounded caller still hears it — no `ResultReceiver`,
  no `PendingIntent`, no `Messenger`, and while the ordered result is set for AOSP correctness it
  is never relied on (EMUI severs both between third-party apps). Exactly one terminal reply per
  request, `AtomicBoolean`-guarded, so an async success and a synchronous error can never both
  fire. Success reads `OK:<path>|<bytes>|<human size>|<n> categories` — both size forms computed
  here, since the caller cannot stat the file.
- **Distinct errors**: `automation disabled`, `bad token`, `no-directory`, `no-storage-access`,
  `unknown category in items: …` — they debug differently, so they never collapse into one
  message. A half-written ZIP is deleted rather than left behind as "the last export".
- **Progress in real numbers, never a percentage**: while exporting, plain broadcasts carrying
  `text` (`項目 123/456 — All cards`), structured `current`/`total`/`unit` and the app label,
  throttled to one per 500 ms plus an unthrottled final one at completion.
- **The gate** (`SkAutomation`): an `automation_enabled` master switch that is **OFF** until it
  is turned on, plus a 24-byte `SecureRandom` token, hex-encoded, generated lazily on first read
  and compared constant-time. Both live in their own device-local preference file — outside the
  exported preference set — so the token can never travel inside a backup ZIP.
- **UI, inside the Export/Import section** (directly below the existing export rows, never a
  section of its own): the master switch with a one-line description, a token row showing
  `80922d8c…4c49a87c` that copies the full token on tap and carries a warned **Regenerate**
  action, and — API 30+ — an **All-files access** row showing the grant state, since writing to
  an absolute `path` needs `MANAGE_EXTERNAL_STORAGE` (declared; used by nothing else).

### Backup filename — the family convention (白い熊, 2026-07-25)
- Every backup this app writes, from the panel and the automation path alike, is now
  `shiroikuma-nekokan_<yyyy-MM-dd_HH-mm-ss>.zip` — the English identifier, no version, no
  `-export` infix, no suffix — so all sister apps' backups sort and read uniformly in one
  directory. The previous `shiroikuma-nekokan-<version>-export_<stamp>.zip` names stay
  recognised by the "Last export" query.
- The ZIP manifest now also records `appVersion`.

## 2.43.0+8

Based on Catima `v2.43.0` (versionCode 167).

### Export / Import — full-app backup (new)
- **New first section on the UI page**: an Export/Import heading + row opening the panel, with a
  live **"Last export"** status line — the settable directory is queried on every page opening
  for the newest export; red warnings when no directory is set or no export exists yet.
- **One panel serves both directions** (Kōjiki flow): a settable **SAF export directory**
  (persisted device-locally, deliberately outside the exported settings; one-tap export once
  set, save-as fallback when unset), a "Select all" checkbox and per-category checkboxes.
- **Categories — everything settable in the app**: **All cards** (the complete Catima card
  export — cards, groups, images — nested as `cards.zip`), **白い熊 猫管 UI (colors · fonts)**
  (every `sk_` theme preference plus the imported font files), **App settings** (all remaining
  preferences).
- **Format**: one zip with `manifest.json`, type-tagged per-key JSON per preference category
  (`{"t","v"}` — missing keys keep their current value on import), font binaries under
  `fonts/`; filename `shiroikuma-nekokan-<version>-export_<yyyy-MM-dd_HH-mm-ss>.zip`.
- **Import is partial and merging**: only selected categories that exist in the zip are
  applied; preferences are merged (never cleared), cards go through Catima's own transactional
  importer; font caches invalidated; a per-category failure never aborts the rest.
- **Live progress dialog**: black-yellow box with a bold n/total counter and the current
  category; cards tick individually via a per-card `CatimaExporter` progress hook (a tiny fork
  patch — the slow part is Catima re-encoding each card image as PNG); a **Cancel** pill
  interrupts the worker between items and deletes the partial export file.
- **Owned black-yellow surfaces**: the panel, progress and finished-info dialogs are hand-drawn
  black boxes with 2 dp yellow borders on transparent dialog windows (Material's surface tints
  repainted them otherwise), with Arcanechat-style round pill buttons — Cancel alone on the
  left, Import/Export together on the right.
- **Success auto-close chain**: acknowledging the yellow-bordered info dialog closes info
  dialog → panel → UI page in one go; the import variant offers **Restart now** (full app
  restart) or **Later** (closes the chain); failures ("Export failed…", "No categories
  selected.", "No 白い熊 猫管 export found in that file.") are toasts that leave the panel open.

### UI page — kxkb visual format
- Section headings restyled to the kxkb construction: 20 sp bold with a **text-wide** 2.5 dp
  underline (underline exactly as wide as the heading text); subgroups 17 sp with a 1.5 dp
  text-wide rule; thin **1 px full-width hairlines** separate top-level sections (none above
  the first).
- kxkb indent ladder: headings 36 dp → subgroups 54 dp → rows 72 dp → nested rows 90 dp
  (18 dp steps); row text sizes 16 sp titles / 13 sp values / 15 sp slider values.

### Packaging
- New dependency `androidx.documentfile` (SAF tree access for the export directory).

## 2.43.0+3

Based on Catima `v2.43.0` (versionCode 167).

### Identity & packaging
- App id `shiroikuma.nekokan`, label **白い熊 猫管** (non-translatable `sk_app_name`; the
  `protect.card_locker` code namespace is untouched for rebase-friendliness).
- Fork versioning: `versionName = <upstream>+<build>` (`2.43.0+3`),
  `versionCode = upstream × 10000 + build` (`1670003`), `BUILD_NUMBER` auto-bumped per build.
- Release signing from a fork-own keystore via gitignored `keystore.properties`
  (`keystore.properties_sample` committed); `buildApk` Gradle task builds the signed foss
  release and copies `shiroikuma-nekokan_<version>_arm64-v8a.apk` to `~/tmp`.
- Claude skills: `build-apk`, `upstream-new-version` (with a proceed-gated upstream
  new-features briefing before any rebase), `publish-version`; repo `CLAUDE.md`.

### Icon & branding
- Black-yellow **traced launcher icon**: the Catima cat-card glyph as yellow `#FFFF00`
  line art on black with hidden-line removal — adaptive icon (black background, traced
  foreground, upstream monochrome kept) + regenerated legacy webp mipmaps at all 5 densities.
- Splash screen: black background + the traced glyph (was blue + red cat).
- Main-screen welcome logo and the widget-picker preview follow the traced icon.
- **Complete rebrand of user-visible strings**: every "Catima" in the default strings and all
  54 locale files replaced with 白い熊 猫管 — including transliterations (ক্যাটিমা, كاتيما,
  கேட்டிமா, कैटिमा, ಕ್ಯಾಟಿಮಾ, കേറ്റിമ, 卡提碼, کاتیما, …) and grammatical inflections
  (Catimě/Catimi/Catimában/Catime …); locale `app_name` overrides deleted so every language
  falls back to ours. The functional `/Catima/share` import-URL path is deliberately kept.
- Export/backup filename is now `shiroikuma-nekokan_<yyyyMMdd>.zip` (was `catima_…`).

### The 白い熊 猫管 UI (new settings page + theming layer)
- **Entry points**: "白い熊 猫管 UI" at the top of Settings, and **long-press on the main
  screen's ⋮ (overflow) button** opens the page directly.
- **Page construction** (sister-repo style, `SkUiActivity`): programmatic rows — big bold
  underlined section headings (Foundation / Top bar / Main screen / Buttons & controls),
  subgroup headers with short underlines, absolute per-level indentation (one full step per
  sub-level), tight row spacing with real padding only between top-level sections.
- **Color selectors** (`SkColorPickerDialog`): four RGBA sliders (0–255) with live hex and
  swatch preview, one-click boxes prefilled with prior-selected colors (persisted, shared
  across all pickers), and a per-slot **Default** reset.
- **Fonts** (`SkFonts`, `SkFontPickerDialog`): import external `ttf`/`otf` via the system
  file picker into app-private storage; per-role font family, weight (100–900) and size
  (slider, 0 = default); the font list renders each font name **in its own glyphs**; every
  text slot shows a live sample line.
- **Slot model with inheritance** (`SkTheme`): background / primary text / secondary text /
  accent as foundation; bar background/title/icons, welcome title/text, card name/note,
  card background/border, FAB background/icon, button text all derive from the foundation
  until individually overridden.
- **Size sliders to 0**: card border thickness (0–12 dp) and corner roundness (0–32 dp),
  with a live bordered preview box.
- **Black-yellow defaults everywhere**: an `SkBlackYellow` Material3 overlay replaces
  Material You dynamic colors — black surfaces, yellow text/accent/outline across all
  screens including dialogs and the Settings list; status/navigation bars follow.
- **Runtime application** (`SkStyler`): a central hook in the shared base activity styles
  every View screen (window background, toolbar background/title/icons + title font, FAB,
  welcome view, empty-state texts); loyalty-card rows (frame color/border/radius +
  name/note color+font) styled from the adapter; the Compose About screen and image viewer
  follow the same foundation colors.
