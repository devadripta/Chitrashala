# Contributing to Chitrashala

Thanks for wanting to help! This is a small, friendly project with no heavy process.

## Ground rules

1. **Keep it light.** The app is ~5 MB and fully on-device. No heavyweight dependencies,
   no bundled ML models over a few MB, no network calls, no analytics. This is the one
   non-negotiable.
2. **Keep it clean.** Match the existing Compose/Material 3 style. Follow the system
   theme. Prefer subtle spring animations over flashy ones.
3. **Test on a real phone** if you can. The primary target is Motorola (near-stock
   Android), but nothing should be Moto-exclusive.

## How to contribute

- **Bugs / ideas** → open an Issue.
- **Code** → fork, branch, PR against `main`. CI builds your PR automatically.
- **Translations, screenshots, docs** → extremely welcome, great first PRs.

## Building

```bash
./gradlew assembleDebug
```

That's all you need; debug builds require no signing setup.

## Project layout (30-second tour)

- `app/src/main/java/com/dripta/galleryformoto/`
  - `ui/` holds the Compose screens (`PhotosScreen`, `AlbumsScreen`, `ViewerScreen`, `EditorScreen`, …)
  - `ui/components/` holds shared pieces (grid, scrubber, selection bar, …)
  - `data/` holds MediaStore queries, the Room database, repositories, helpers
  - `workers/` holds background indexing (labeling, locations, duplicates, stories, …)
  - `widgets/` holds the home-screen widget
