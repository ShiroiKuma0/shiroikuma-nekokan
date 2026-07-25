<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="120" alt="白い熊 猫管 icon" />

# 白い熊 猫管 (shiroikuma-nekokan)

**Loyalty cards, membership cards, coupons — in the black-yellow 白い熊 house style.**

A fork of [Catima](https://github.com/CatimaLoyalty/Android) with **major additions**: one-tap full export/import (cards + theme + fonts + settings, by category), headless backup on request for automation, a fully settable 白い熊 猫管 UI page (RGBA color pickers, external fonts, border/roundness sliders — everything live-previewed), the black-yellow traced icon and theme, and complete rebranding across all 54 locales.

Installs **side-by-side** with Catima (app id `shiroikuma.nekokan`).

**📥 Latest release: [`2.43.0+9`](https://github.com/ShiroiKuma0/shiroikuma-nekokan/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-nekokan/releases)

</div>

---

## 📦 One-tap full backup — export / import everything
The first section of the UI page. Pick a backup directory once and one tap exports **everything** as a single zip — all cards (the complete Catima export with images), every 白い熊 猫管 UI setting including your imported fonts, and the app settings — split into selectable categories. The page itself shows a "Last export" freshness line (red until you've backed up), the run has a live n/total progress dialog with per-card ticks and a working Cancel, and import merges category-wise — never wipes — with a restart offer at the end.

## 🤖 Backup on request — the automation contract
The same backup, headlessly. A token-gated broadcast makes the app export itself with no window ever opening — one ZIP, written either to its own directory or to an absolute path the caller names — and reply with the real path and byte size, so a task on the phone can back this app up unattended alongside its sister apps. Progress comes back as **real counts, never a percentage** (`項目 123/456 — All cards`). The switch is **off** until you turn it on, in the Export/Import section right below the export rows; the tap-to-copy token is what any caller must present, and regenerating it locks out every pasted copy.

## 🎨 The 白い熊 猫管 UI page
One page controls the whole look — reachable from Settings or by **long-pressing the ⋮ button** on the main screen. Sister-repo construction: big bold underlined section headings (Foundation / Top bar / Main screen / Buttons & controls), deeply indented items per level, tight rows. Every color opens a picker with **four RGBA sliders**, a live hex + swatch preview, and one-click boxes prefilled with your prior-selected colors. Foundation colors cascade: change the accent once and the toolbar icons, FAB, card borders and section rules follow.

## 🔤 External fonts, rendered in their own glyphs
Import any `ttf`/`otf` and assign it per role — primary text, secondary text, bar title, welcome view, card name/note, buttons — each with its own weight (100–900) and size slider, and a live sample line. The font list draws every font's name **in that font**.

## 🖤💛 Black-yellow everywhere, borders to 0
The whole app boots into the house look: black surfaces, yellow text, yellow borders — dialogs, Settings and the Compose About screen included (Material You is retired). Card frames have color, **thickness and corner-roundness sliders that go all the way to 0**, with a live preview box.

## 🐱 The traced icon
The Catima cat-card glyph re-traced as pure-yellow line art on black — hidden edges properly removed — as an adaptive icon, the splash screen, the welcome logo and the widget preview.

---

## Built on Catima
A fork of [Catima](https://github.com/CatimaLoyalty/Android) (app id `shiroikuma.nekokan`, so it coexists with the official build). Catima is Sylvia van Os's copylefted libre loyalty-card wallet — all card handling, barcode scanning and import/export machinery is hers. The code remains under **GPL-3.0-or-later**.

## Building
```bash
git clone git@github.com:ShiroiKuma0/shiroikuma-nekokan.git
cd shiroikuma-nekokan
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=~/android-sdk
./gradlew buildApk        # signed foss release → ~/tmp/shiroikuma-nekokan_<version>+<n>_arm64-v8a.apk
```
Versioning: `versionName = <upstream>+<build>`, `versionCode = upstreamCode * 10000 + build`. `main` tracks upstream release tags; all fork work lives on `custom`.
