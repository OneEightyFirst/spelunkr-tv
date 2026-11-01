# Spelunkr TV (unofficial)

A tiny Android app that plays [spelunkr.tv](https://spelunkr.tv/) full screen on **Fire TV** and **Android TV**, controlled with the TV remote.

**Download:** https://oneeightyfirst.github.io/spelunkr-tv/
**On a Fire TV, type this into the Downloader app:** `oneeightyfirst.github.io/spelunkr-tv/spelunkr-tv.apk`

> Not made by or affiliated with Spelunkr. It's just a remote-friendly window onto their website.

## Install on Fire TV

1. Install **Downloader** (by AFTVnews) from the Amazon Appstore.
2. Settings → My Fire TV → Developer Options → Install unknown apps → enable **Downloader**.
   (No Developer Options? Settings → My Fire TV → About → press OK on the device name 7 times.)
3. Open Downloader, enter `oneeightyfirst.github.io/spelunkr-tv/spelunkr-tv.apk`, choose **Install**.

Or from a computer on the same network (with ADB debugging on): `adb connect <tv-ip> && adb install spelunkr-tv.apk`

## Remote

| Button | Does |
|---|---|
| Arrows / OK | Same as the arrow keys and Enter on the website |
| Back | Goes back a screen; from the guide, asks "Leave Spelunkr?" |
| Play/Pause, Rewind, Fast-forward | Sent to the site as media keys |

The website already understands TV remotes. The app mostly just passes keys through.

## Why you can trust it

The whole app is three small files:

- [`MainActivity.java`](app/src/main/java/io/github/oneeightyfirst/spelunkrtv/MainActivity.java): the full-screen web view and remote buttons.
- [`tv.js`](app/src/main/assets/tv.js): added to the page; moves focus with the arrow keys where the site doesn't. Reads and sends nothing.
- [`Updater.java`](app/src/main/java/io/github/oneeightyfirst/spelunkrtv/Updater.java): the update check (below).

What it does and doesn't do:

- **Two permissions:** Internet, and permission to *ask* to install its own updates. See [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml).
- **Locked to spelunkr.tv.** Any link to another site is refused. It opens the site's own TV mode (`https://spelunkr.tv/?tv`).
- **The page can ask the app for one thing only:** `SpelunkrApp.leave()`, which closes the app. The site's "Leave Spelunkr?" button calls it.
- **No file access**, no location, no backups, no plain-HTTP traffic.
- **No dependencies.** Not even AndroidX. No ads, analytics or crash reporting.
- **No code shrinking/obfuscation**, so the APK is easy to decompile and compare.
- It adds `SpelunkrTV-Android/<version>` to its user agent so the site can see what it is.
- **Built in public.** Release APKs are built and signed by [GitHub Actions](.github/workflows/release.yml) from a tagged commit. Each release lists its SHA-256.

### Updates

On launch the app fetches one public file, `https://oneeightyfirst.github.io/spelunkr-tv/version.json`. Nothing about you or the device is sent. If it lists a newer version, the app asks *Update available, install now?* Only if you choose **Update** does it download the APK, check its SHA-256 against `version.json`, and hand it to Android's installer, which shows its normal Install screen. Android refuses any update that isn't signed with the same key as the installed app.

The first time, Fire TV may ask you to allow Spelunkr TV to install apps (Settings → My Fire TV → Developer Options → Install unknown apps).

### Verify a download

```bash
shasum -a 256 spelunkr-tv.apk        # compare with the release notes
apksigner verify --print-certs spelunkr-tv.apk
```

Signing certificate SHA-256:
`C5:7C:B5:F3:67:1B:C5:16:68:EB:99:9D:70:09:62:1A:C6:BB:0A:4E:6B:CE:8D:72:BA:78:47:2C:B4:18:62:10`

## Build it yourself

Needs JDK 17 and the Android SDK.

```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## Releasing

Bump `versionCode`/`versionName` in `app/build.gradle` (the app only offers an update when `versionCode` goes up), then push a tag:

```bash
git tag v1.0.1 && git push origin v1.0.1
```

CI builds, signs, publishes the GitHub Release and updates the download page and `version.json`. Installed apps offer the update next time they open.
