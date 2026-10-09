# Carf.INC

Design a **cutting-mat widget**: type a name, watch it spell itself out in cut-out paper letters on a
grid mat, decorate it with stickers and photos, then put it on your home screen as a widget.

## Features

| Area | What it does |
|---|---|
| **Name → cutout letters** | Every letter becomes a torn-paper ransom-note tile (random paper, font, tilt). |
| **Mat editor** | Drag, pinch-zoom and two-finger rotate any piece. Undo, duplicate, bring-to-front, delete. |
| **Color & size** | 11 mat colors plus a custom hue picker; *Medium* (square) and *Large* (wide) shapes. |
| **Sliders** | **Size** scales all letters, **Chaos** controls how crooked the letters sit. |
| **Paper edge** | Toggle the white cut-paper border and drop shadow. |
| **Decorations** | 32 built-in stickers (Dynamic, Cute, Flowers, Food), 8 ready-made **Notes**, + **My stickers** from your photos. |
| **Person cut-out stickers** | Pick a photo and only the **person** is lifted out (on-device ML Kit selfie segmentation): background removed, cropped to the person, with a white die-cut paper outline that follows the body. Photos with no person are rejected instead of adding the whole picture. |
| **Text notes** | *Note* tool: type anything on a pink / yellow sticky, typed slip, black quote card, blue or kraft scrap. Tap a note → pencil to edit it. |
| **Landscape editor** | Rotate the phone: the mat fills the left side and the controls move to a side panel. |
| **Remix** | Re-rolls the papers, fonts and tilt of the whole name. |
| **Home-screen widget** | 2×2 → 4×2, resizable. Always pixel-identical to the editor. Tap to open the mat. |
| **Share / Save / Wallpaper** | Share sheet, save to `Pictures/Carf.INC`, or set as wallpaper with a preview. |
| **Haptics** | Slider detents, selection, 45° rotation detents, confirm / reject patterns. Toggle in Settings. |
| **Setup flow** | 3-page onboarding ending in a one-tap *Add the widget* (launcher pin request) with a manual fallback guide. |

## Build

Requires JDK 17 and the Android SDK (API 34).

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/
./gradlew assembleRelease    # app/build/outputs/apk/release/
```

## Publish an APK on GitHub

1. Push this folder to a new GitHub repository.
2. (Recommended) create a signing key once and add it as repository secrets:
   ```bash
   ./scripts/make-keystore.sh          # prints the 4 secret values and writes keystore.b64
   ```
   Add `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` under
   *Settings → Secrets and variables → Actions*. Back up `carf-release.jks`.
   Without these secrets the release is signed with the debug key — it installs fine, but you won't be
   able to ship updates over it later.
3. Tag and push:
   ```bash
   git tag v1.0.0 && git push origin v1.0.0
   ```
   The **Release APK** workflow builds `Carf-INC-v1.0.0.apk`, writes `SHA256SUMS.txt` and attaches both to a
   new GitHub Release. You can also run it manually from the *Actions* tab.

`Build` runs on every push/PR and uploads a debug APK as a workflow artifact.

## Install

Download the APK from the Release page on your phone, allow *Install unknown apps* for your browser, open it.
Then: long-press the home screen → **Widgets** → **Carf.INC**, or use *Add the widget* inside the app.

## Project layout

```
app/src/main/java/com/carfinc/app/
  model/    Mat, MatItem, layout of name letters, color palette
  render/   MatRenderer (shared by editor/widget/export), LetterTile, StickerArt, StickerImages
  data/     JSON file store, prefs, sample mats
  widget/   AppWidgetProvider, config activity, bitmap updater
  ui/       Compose screens: onboarding, home, editor, sheets
  util/     Haptics, Export (share / photos / wallpaper)
```

## Notes

* The widget draws the mat to a bitmap (640 px, 840 px for Large) so it always matches the editor.
* Photo stickers use ML Kit **Selfie Segmentation** (bundled model, offline). Only the person is kept: the mask is
  thresholded, stray blobs are dropped, the image is cropped to the person's bounding box and saved as
  `files/stickers/cut_<time>.png`. Older whole-photo stickers still render as before.
* Notes are stored as sticker ids `note:<style>:<text>`, so they need no extra storage.
* All art (letters, stickers, icon) is drawn procedurally in code — no third-party assets.
