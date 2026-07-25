# 白い熊 猫管 — fork changelog

Everything built on top of stock [Catima](https://github.com/CatimaLoyalty/Android). Upstream owns
`CHANGELOG.md` (compiled into the app); fork notes live here.

## 2.43.0+9 — current

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
