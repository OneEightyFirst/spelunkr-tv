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
| Back | Closes menus / goes back a screen; on the first screen, press twice to exit |
| Play/Pause, Rewind, Fast-forward | Sent to the site as media keys |

The website already understands TV remotes. The app mostly just passes keys through.

## Why you can trust it

Everything the app does is in one file: [`MainActivity.java`](app/src/main/java/io/github/oneeightyfirst/spelunkrtv/MainActivity.java).

- **One permission: Internet.** See [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml).
- **Locked to spelunkr.tv.** Any link to another site is refused.
- **No JavaScript bridge.** The web page can't call into the app or the device.
- **No file access**, no location, no backups, no plain-HTTP traffic.
- **No dependencies.** Not even AndroidX. No ads, analytics or crash reporting.
- **No code shrinking/obfuscation**, so the APK is easy to decompile and compare.
- It adds `SpelunkrTV-Android/<version>` to its user agent so the site can see what it is.
- **Built in public.** Release APKs are built and signed by [GitHub Actions](.github/workflows/release.yml) from a tagged commit. Each release lists its SHA-256.

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

Bump `versionCode`/`versionName` in `app/build.gradle`, then push a tag:

```bash
git tag v1.0.1 && git push origin v1.0.1
```

CI builds, signs, publishes the GitHub Release and updates the download page.
