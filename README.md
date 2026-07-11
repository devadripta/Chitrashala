<p align="center">
  <img src="art/icon.png" alt="Chitrashala icon" width="160" />
</p>

# चित्रशाला · Chitrashala

**A clean, featherweight, on-device photo gallery, built for Motorola phones.**

[![Download APK](https://img.shields.io/badge/Download-APK-C8553D?style=for-the-badge&logo=android&logoColor=white)](https://github.com/devadripta/Chitrashala/releases/latest)
![Size](https://img.shields.io/badge/size-~5%20MB-2E3A59?style=for-the-badge)
![License](https://img.shields.io/badge/license-MIT-E8A33D?style=for-the-badge)

---

## Why this exists

Motorola phones don't ship an in-house gallery app. You get near-stock Android (which is
exactly *why* many of us choose Moto), but for your photos you're pushed straight into
Google Photos, with its cloud nudges, upsells, and weight.

**Chitrashala** fills that gap the Moto way: clean, fast, private, and entirely on your
device. No account. No cloud. No ads. No analytics. Your photos never leave your phone.

> **चित्रशाला (chitraśālā)** is Sanskrit: *chitra* (चित्र) means "picture / painting" and
> *shālā* (शाला) means "hall / house". A *chitrashala* is a picture hall: the room in old
> Indian palaces where paintings were hung. That's what this app tries to be for your
> phone: a quiet, beautiful hall for your pictures.

The UI is deliberately minimal and smooth, in the spirit of the stock-Android experience
Motorola users already love. It's built with Jetpack Compose and Material 3, follows your
system light/dark theme, and the whole app is **around 5 MB**.

## Download

Grab the latest APK from **[Releases](https://github.com/devadripta/Chitrashala/releases/latest)**
and install it. That's it, no Play Store needed.

> Requires Android 8.0+ (API 26). Built and tested primarily on a Moto Edge 60 Pro, but it
> runs on any Android phone; nothing in it is Motorola-exclusive.

## What it does

**Everyday gallery**
- Photos timeline grouped by day, with a fast-scroll timeline scrubber
- Albums, real nested folder browsing, and album management (create / move / copy / rename)
- Favorites, star ratings, color labels, and custom tags
- Pinch to change grid density · double-tap to favorite · swipe to hide
- Full-screen viewer with buttery pinch-zoom, video playback, and video → photo frame extraction

**Private by design**
- Hidden vault behind your fingerprint (biometric)
- Trash with 30-day safety net before anything is really gone
- EXIF (location & metadata) stripping when you share
- All ML runs on-device; the app has no server

**Smart, without the cloud**
- On-device photo labeling → searchable photos and auto "smart albums"
- Places: your photos on a map of where they were taken (from EXIF GPS)
- Smart Cleanup: finds duplicates, blurry shots, screenshots, and burst clutter
- Auto-generated Stories from trips and events
- Memories: "on this day" style resurfacing

**Create**
- Editor: crop, rotate, filters, color adjustments, enhance, markup
- Collage maker with layout templates
- Batch edit: apply the same adjustments to many photos at once
- Random Memory home-screen widget

## Screenshots

*(coming soon, PRs welcome!)*

## Building from source

```bash
git clone https://github.com/devadripta/Chitrashala.git
cd Chitrashala
./gradlew assembleDebug     # debug build, no signing needed
./gradlew assembleRelease   # unsigned release (or add your own keystore.properties)
```

To sign a release, create a `keystore.properties` in the project root (it's gitignored):

```properties
storeFile=/path/to/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## Contributing

Chitrashala is open source under the MIT license, and contributions are very welcome:
features, fixes, translations, screenshots, anything. The one house rule:

**Keep it light.** The whole point of this app is that it stays tiny (~5 MB today) and
fully on-device. PRs that add heavyweight dependencies, bundle large ML models, or phone
home will not be merged. If a feature can't be done under that constraint, it probably
belongs in a different app.

Good first contributions: translations, small UX polish, more collage layouts, more filter
presets, screenshot section for this README.

## The story

Developed and vibe coded by **Devadripta**, a Moto user who got tired of not having a
proper gallery app.

## License

[MIT](LICENSE). Do whatever you want, just keep the notice.
