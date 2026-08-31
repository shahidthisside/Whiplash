# Whiplash

**Whiplash** is a premium, native Android music player focused on fast, resilient YouTube Music playback with full offline/local library support. Built entirely with Kotlin, Jetpack Compose, and Media3/ExoPlayer.

> This project was built as a hands-on exploration of modern Android media architecture — a real, working Media3 session pipeline, a multi-provider YouTube extraction/fallback system, and a custom dark, frosted-surface design system — rather than a wrapper around an existing SDK.

---

## Features

### Playback
- Background playback via a genuine `MediaSessionService` + `ExoPlayer` pipeline (survives Activity recreation, screen-off, and app backgrounding)
- Real YouTube / YouTube Music search and streaming through [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) — no official API key required
- Multi-provider fallback architecture (`PlaybackManager` + `ProviderHealthTracker`) designed to add additional extraction providers without touching call sites
- Gapless playback, crossfade, and adjustable playback speed
- Sleep timer (fixed durations, end-of-song, end-of-queue)
- Persistent queue with reordering, "play next," and "add to queue," plus an Autoplay on/off shortcut right in the Queue sheet
- YouTube-style autoplay: automatically extends the queue with related, music-only tracks when Autoplay is enabled (a video's YouTube category is checked so non-music results never sneak in)
- Local/offline device music library (MediaStore-backed), including automatic library refresh via a `ContentObserver` when files change on disk

### Library & Discovery
- Unified local + YouTube search (Songs, Albums, Artists, Playlists — each loads and fails independently)
- Album and Artist detail pages with real metadata, track listings, and playback actions
- A full History screen (up to 200 recently played tracks), Favorites, Playlists (create/rename/delete, plus importing a whole playlist by pasting a YouTube or YouTube Music playlist link), and a Speed Dial / Quick Picks home surface
- Synchronized lyrics via [LRCLIB](https://lrclib.net) (a free, open lyrics database) with an honest "unavailable" state — lyrics are never fabricated

### Design & UX
- A custom dark, frosted-surface design system built on Jetpack Compose + Material 3 (translucent tinted surfaces, soft borders, layered elevation)
- Six selectable color themes with instant, persisted switching
- Four full-player seek bar visual styles (Classic, Wavy, Waveform, Minimal), picked live with mini-previews in Settings
- Local backup and restore: saves playlists, favorites, history, pinned songs, and settings to a single file you choose via the system file picker, and can restore from it later
- Haptic feedback and micro-interactions on stateful actions (play/pause, favorite, queue reorder, toggles)
- Swipe gestures on the mini-player (next/previous), always paired with accessible on-screen buttons
- Accessibility-conscious touch targets (48dp+) and reduced-motion support that respects the system's animator duration scale

---

## Architecture

```
UI (Compose)  →  ViewModel  →  PlaybackController  →  MediaController  →  MediaSessionService (ExoPlayer)
                                       │
                                       ├── PlaybackManager (provider fallback)
                                       │      └── NewPipePlaybackProvider (NewPipeExtractor)
                                       │
                                       ├── LibraryRepository / LocalLibraryRepository (Room + MediaStore)
                                       ├── SettingsRepository (DataStore Preferences)
                                       └── LrcLibProvider (lyrics)
```

- **UI layer**: Jetpack Compose screens, one `ViewModel` per screen, no direct access to Media3 types.
- **`PlaybackController`**: the single source of truth for playback state (`StateFlow<PlaybackState>`), queue management, shuffle/repeat, sleep timer, and autoplay. Talks to the playback service through a `MediaController`.
- **`WhiplashPlaybackService`**: a `MediaSessionService` owning the real `ExoPlayer` instance and `MediaSession`. Wraps the player in a `QueueAwareForwardingPlayer` so the system (notification, lock screen, Bluetooth/AVRCP) can correctly report Next/Previous availability even though YouTube tracks are resolved and loaded one at a time (each requires an async network resolve before a playable `MediaItem` can exist).
- **`PlaybackManager` / `ProviderHealthTracker`**: an extraction-provider abstraction with automatic health-based fallback, designed so a second provider can be added as a list entry with no changes to calling code.
- **Persistence**: Room for library/queue/history/playlists/provider health, DataStore Preferences for user settings.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Playback | AndroidX Media3 (ExoPlayer, MediaSession) |
| YouTube extraction | [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) |
| Local persistence | Room, DataStore Preferences |
| Local media | MediaStore |
| Networking | OkHttp |
| Image loading | Coil |
| Lyrics | [LRCLIB](https://lrclib.net) public API |
| Build | Gradle (Kotlin DSL), KSP |

**Minimum SDK:** 26 (Android 8.0) · **Target/Compile SDK:** 36

---

## Download

A prebuilt debug APK is available on the [Releases page](https://github.com/shahidthisside/Whiplash/releases/latest) as `Whiplash.apk`. It is a standard debug build (not signed for release), intended for personal/educational use — see [Building from Source](#building-from-source) below if you'd rather build it yourself.

---

## Building from Source

### Prerequisites
- JDK 17
- Android SDK (Platform 36, Build-Tools matching `compileSdk`)
- Android Studio (recommended) or the command line

### Build

```bash
git clone https://github.com/shahidthisside/Whiplash.git
cd Whiplash
./gradlew assembleDebug
```

The debug APK will be produced at `app/build/outputs/apk/debug/app-debug.apk`.

### Install to a connected device/emulator

```bash
./gradlew installDebug
```

No API keys, secrets, or `local.properties` entries beyond the standard Android SDK path are required to build this project.

---

## Project Structure

```
app/src/main/java/com/whiplash/music/
├── data/            # Room entities/DAOs, repositories (library, settings, search cache)
├── domain/          # Plain Kotlin domain models (PlayableItem, LyricsResult, etc.)
├── localmedia/       # MediaStore scanning for the offline/local library
├── playback/
│   ├── controller/  # PlaybackController — the single source of truth for playback state
│   ├── provider/    # NewPipeExtractor integration, provider fallback, lyrics provider
│   └── service/     # MediaSessionService, ForwardingPlayer, session callback
└── ui/              # Compose screens, ViewModels, and the frosted-surface design system
```

---

## Known Limitations

Being transparent about what isn't (yet) fully solved:

- **OEM "island" / live-activity style notifications** (e.g., Vivo/iQOO OriginOS's "Origin Island") are a vendor-OS reskin of the standard Android media notification, not a public API third-party apps can opt into. Whiplash uses the correct, standard `MediaSessionService` + `MediaStyle` architecture; whether the OS elevates it to an island view is outside the app's control, and is a documented inconsistency affecting other third-party media apps as well.
- **Vivo/iQOO OriginOS's "Origin Player" quick-switch audio-source picker** only lists a small, hardcoded set of partner apps (confirmed on-device: it shows the system Music app plus Spotify as "installable," with no public, documented way for a third-party app to be added). Whiplash's `MediaSession` is verified fully correct and active via `dumpsys media_session` (real live `PlaybackState`, correct session flags, Bluetooth/AVRCP routing all confirmed working) — this omission is a closed vendor allowlist, not a gap in the app's own media-session implementation.
- The in-app notification's own Next/Previous buttons may not appear on every OEM skin, even though the underlying session correctly reports Next/Previous availability to the system (verified via the legacy `PlaybackState` bridge that Bluetooth/AVRCP and most lock screens read). This stems from ExoPlayer only ever holding one resolved track at a time — YouTube streams require an async network resolve before they can be loaded — rather than a real multi-item timeline.
- YouTube/YouTube Music access relies on NewPipeExtractor's unofficial extraction. YouTube can change its internal APIs at any time, which may require an extractor library update.

---

## Disclaimer

This project uses [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) to access publicly available YouTube/YouTube Music content and is intended for personal, educational use. It is not affiliated with, endorsed by, or sponsored by YouTube, Google, or LRCLIB. Users are responsible for complying with YouTube's Terms of Service in their jurisdiction.

## License

Licensed under the [MIT License](LICENSE).

## Acknowledgements

- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) — YouTube/YouTube Music extraction
- [LRCLIB](https://lrclib.net) — free, open synced-lyrics database
- [AndroidX Media3](https://github.com/androidx/media) — ExoPlayer and MediaSession
