# SubPlayer Offline & Online (Android)

Android video player built with **Kotlin**, **Jetpack Compose (Material 3)**, **Media3 ExoPlayer**, and **Google ML Kit Translate** featuring automatic `.srt` subtitle translation across Offline, Online, and Hybrid modes.

## Key Features

- **Video Playback & Gesture Controls**: Plays local video files via Android Storage Access Framework (`OpenDocument`) with Media3 ExoPlayer, tap-to-toggle overlay controls, horizontal drag to seek, left-side vertical drag for screen brightness, and right-side vertical drag for media volume.
- **Subtitle Loading & Exporting (`.srt`)**: Parses `.srt` subtitle files (`SrtParser`), displays synchronized cues over video playback, and exports translated subtitles back to `.srt` via `CreateDocument` (`SrtExporter`), reloading the exported file automatically.
- **3 Translation Modes (`TranslationMode`)**:
  - **Hybrid (Online → Offline)**: Prefers online cloud translation and falls back automatically to on-device ML Kit models when offline.
  - **Online (Cloud)**: Uses standard cloud translation or an optional user-provided Gemini API key in translation settings.
  - **Offline (ML Kit)**: Runs 100% on-device translation using downloaded ML Kit language packs.
- **Language Catalog & Pack Manager**: Search and swap among all ML Kit supported languages (`LanguageCatalog`), inspect downloaded status, download or remove offline language packs (`RemoteModelManager`), and copy or open direct ML Kit archive links.
