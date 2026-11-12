# Frost Launcher 1.2

A lightweight Apple TV-style home screen for Android TV boxes and tablets.

* Android 6.0+ (API 23), pure Java, **no native code, no libraries** -> one APK runs on arm32, arm64 and x86.
* No real-time blur anywhere. Every "frosted glass" effect is blurred ONCE on a tiny bitmap and cached, so it is cheap even on old boxes.

## What's new in 1.2
* **Apple-style tiles**: tile corners now use Apple's continuous ("squircle") corner curve instead of a plain circular arc.
  The radius is one constant, `TileArt.RADIUS`, if you want it rounder or tighter.
* **Photos as icons**: a photo or banner (JPG, or a PNG with no transparency, wider than 4:3) is centre-cropped to 16:9 and
  becomes the whole tile, with the same Apple corners. Transparent logo PNGs work as before. A square opaque picture is
  clipped to the iOS app-icon shape and centred.
* **Fonts**: Launcher Settings -> Font (System, Condensed, Serif, Monospace, or Custom). *Choose font file* picks a
  .ttf / .otf from USB or local storage and copies it into the launcher, so it stays after you unplug the drive.
  On Android 11+ the launcher asks for "All files access" the first time, because the picture permission cannot read fonts.
* Removed: the soft shadow under the top panel, the drop shadow behind the focused tile, and the recent apps in the panel.

## What was new in 1.1
* **App grid**: 4 apps per row, then a 2nd row, 3rd row and so on (3-6 per row in Settings). The grid scrolls to keep the selected row on screen.
* **Background picture** from USB or local storage (copied into the launcher, so you can unplug the USB drive afterwards). Dim and blur adjustable.
* **Frosted selection bar** that glides behind the row you are on.
* **Top panel** (floating pill, independent of the corners). Press **Up** from the first row: it springs open into a control centre with the
  clock and date, free RAM, free storage, network and USB status, and quick buttons (Launcher Settings, Android Settings, Wi-Fi,
  **Free RAM** which stops background apps).
* **Launcher Settings** (panel -> Settings, or press Menu on the + tile / hold OK on +):
  * Font: system / condensed / serif / monospace / custom file from USB (kept locally)
  * Layout: apps per row, icon size, app names (off / on focus / always), name size, **interface scale** (scales everything)
  * Background: choose / remove background, dim, blur
  * Bars: top panel on/off, selection bar on/off, 12h/24h time
  * Effects: animation speed (Off / Fast / Normal / Relaxed), tile tilt, **screensaver** (clock + slow zoom over your wallpaper, 2-30 min)
  * Reset to defaults
  Changes apply when you close the menu, and the grid **morphs** smoothly from the old layout to the new one.
* Tiles tilt slightly in the direction focus moves.

## Existing features
* Add apps with the **+** tile: pick an app, then a PNG with a transparent background (or the app's own icon). The logo is trimmed,
  centred, and given a blurred 16:9 tile with highlighted border, built once.
* Hold OK (or Menu) on a tile: Open, Change Icon, Move (tiles jiggle; arrow keys reorder in the grid), App Info, Remove.
* Remote D-pad, touch and air-mouse all work. Home always returns to the first tile.

## Build the APK
### GitHub Actions (nothing to install)
Upload this folder to your repo (keep `.github/workflows/build.yml`), open **Actions -> Build APK -> Run workflow**, then download
the `FrostLauncher-apk` artifact. Updating? Upload the new files over the old ones and delete `GlyphButton.java` from
`app/src/main/java/com/frost/launcher/` (no longer used).

### Android Studio
Open the folder, accept the SDK 34 install, then Gradle panel -> `app > Tasks > build > assembleRelease`.
APK: `app/build/outputs/apk/release/app-release.apk`.

### Command line
JDK 17, Android SDK (platform 34, build-tools 34.0.0, `ANDROID_HOME`), Gradle 8.7+: `gradle assembleRelease`

## Install
`adb install -r app-release.apk` (updates in place and keeps your apps/settings), or copy the APK to a USB stick and open it.
Make it Home: press Home and pick Frost -> Always, or Settings -> Apps -> Default apps -> Home app.
Fallback: `adb shell cmd package set-home-activity com.frost.launcher/.HomeActivity`.

## Notes
* If you turn the top panel off, reach Settings with **Menu** on the + tile, or hold OK on the + tile.
* Reading pictures needs the storage/photos permission; it is requested the first time you add an app or background.
