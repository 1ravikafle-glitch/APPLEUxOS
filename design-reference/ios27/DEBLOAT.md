# Debloating AOSP 16 for a 4 GB POCO M5 (verify every item on your tree)

You asked to delete stock and replace it. Three tiers, from safest to riskiest. The tiers are decisions for you; each deletes more and risks more.

## Tier A: slim fork (recommended)
Remove what the iOS-style product does not need; keep every service layer.
1. **Product makefile:** build from a minimal product (`build/make/target/product/*.mk`), then add only what you use to `PRODUCT_PACKAGES`. Do not inherit full `aosp_base`/`handheld` lists.
2. **Remove stock apps** your replacements cover: Browser, Calendar, Camera2 (if you ship one), Contacts/Dialer only if replaced, Email, Gallery, Music, Messaging, DeskClock, Calculator, QuickSearchBox, LiveWallpapers/Wallpaper pickers, Traceur.
3. **Locales and assets:** `PRODUCT_LOCALES := en_US` (add only what you need); drop extra fonts/emoji sets, unused wallpapers, sounds, and `PRODUCT_AAPT_CONFIG` densities other than xxhdpi.
4. **Build flags:** `WITH_DEXPREOPT := true`, ship ART profiles, enable R8/minify for system apps where your tree allows it.
5. **Memory:** tune `lmkd` and zRAM for 4 GB; test with the heaviest app mix.
6. **Dead hardware paths:** remove HALs/features the POCO M5 does not have (e.g. NFC if absent).

## Tier B: rewrite the app layer, keep SystemUI services
Replace as new apps: Launcher, Settings, Dialer/Contacts UI, keyboard. Keep inside SystemUI: the service/controller classes (keyguard auth, notification listener, tiles, volume, status icons); replace only views/layouts/themes.
Launcher note: gesture navigation lives in the launcher's Quickstep `TouchInteractionService` and recents; a from-scratch launcher must reimplement or reuse these, otherwise gesture nav and app-switch animations break.

## Tier C: full SystemUI rewrite (not recommended on this timeline)
You would reimplement keyguard/auth, notification pipeline, quick settings/tile API, status bar icon policy, volume, screenshot/recording UI, and the shell animation hooks. That is a multi-person-year surface; expect boot loops and security regressions. Only do this after Tier B works.

## Must keep (breakage if removed; confirm for your tree)
Framework + `services`, SystemUI (services), Settings provider/Intelligence, Telephony/Telecom/TeleService, `PermissionController`, `PackageInstaller`, `DocumentsUI` (file picker), `KeyChain`, `CertInstaller`, `CellBroadcastReceiver`, `ProvisioningApp/SetupWizard` (or your own), `WebView` provider (many apps need it), `ExtServices`, `NetworkStack`, `Bluetooth`/`Wi-Fi` mainline modules, `Shell`, `ExternalStorage`/`MediaProvider`.

## How to debloat safely
- One removal per commit; boot-test and run a smoke list (call, SMS, Wi-Fi, camera, install APK, file picker, unlock, reboot) after each.
- Keep a "removed packages" list in git so removals are reversible.
- Measure: `dumpsys meminfo`, boot-time free RAM, and `/system` size before and after.
