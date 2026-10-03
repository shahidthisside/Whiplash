# Whiplash

**Whiplash** is a native Android music player for YouTube Music and your own songs, built from scratch by **Shahid Ansari** with Kotlin, Jetpack Compose and Media3/ExoPlayer.

Version **1.1.0** is the biggest update so far: a redesigned app, eight themes including Liquid Glass, smarter autoplay and Quick Picks that learn what you finish, full-screen synced lyrics, Monthly Replay, optional Google Drive sync, and a much more reliable playback engine.

> **Proprietary software. All rights reserved.** The source code is published so it can be read. It may not be copied, modified, renamed, re-skinned, rebuilt, redistributed, or used to train or prompt AI models. See [LICENSE](LICENSE).

---

## Download

Get the signed APK from the [Releases page](https://github.com/shahidthisside/Whiplash/releases/latest) or the [Whiplash website](https://shahidthisside.github.io/Whiplash/). Android 8.0 or newer.

Only APKs from these two places are official.

---

## Features

### Playback
- Background playback through a real `MediaSessionService` + ExoPlayer pipeline, with lock screen, notification, headset and Bluetooth controls. Play from the notification or a headset always resumes, even after the app was closed.
- Two stream sources: NewPipe, and YouTube direct as an automatic fallback. Choose Automatic, NewPipe only or YouTube direct only in Settings › Stream source.
- Built for weak connections: a lighter stream is picked automatically on a slow link, a dropped connection is retried while the song keeps playing, and likely next songs are looked up before you tap them.
- The queue, and where you were in the current song, are kept after the app closes.
- Gapless playback, fade between tracks, playback speed 0.5x–2x, Skip Silence, and a sleep timer (fixed times, end of song, end of queue).
- Separate audio quality for Wi-Fi and mobile data, plus an audio cache for instant replays.
- Offline downloads with embedded tags and cover art, Wi-Fi-only downloads, Save to device, and Download album/playlist.
- Your own on-device music (MediaStore), refreshed automatically when files change.

### Autoplay and recommendations
- Autoplay starts a YouTube Music radio for the song you play, falling back to NewPipe.
- Songs are ranked on the device by mood, genre, energy, era and language. The radio learns from what you finish and skip, which artists go together, and what you play at each time of day.
- Re-uploads, lyric videos and music videos of a song you already have are dropped, and the official audio is preferred.
- Quick Picks are built from the radios of songs you finish and YouTube Music's "You might also like". They show instantly from the last list and refresh only when they're stale, on a new day, or when your listening has moved.
- Settings › Reset recommendations forgets what the radio learned.

### Now Playing and lyrics
- Redesigned player with colours from the album art, an optional full-bleed cover, swipe the artwork to skip, and drag down to close.
- Five seek bar styles: Classic, Wavy, Waveform, Minimal and Hairline.
- Full-screen synced lyrics, opened by swiping up on the player, with word-by-word highlighting, optional blur, per-song timing, and the current line shown above the seek bar if you want it.
- Lyrics come from LRCLIB, with lyrics.ovh as a fallback; they're cached for offline use and never made up.
- The queue sheet has sections (played, now playing, from autoplay), drag to reorder, swipe to remove with undo, and shuffle up next.

### Home, search and library
- Home: Speed dial (pinned and recent songs, in pages, as a grid or list), Quick Picks with Play all, optional album and playlist shelves, and pull to refresh. Both show instantly on launch.
- Explore in Search: new releases, charts, and mood and genre pages.
- Search across songs, albums, artists and playlists, with suggestions, recent searches, infinite scroll and modern album, artist and playlist pages.
- Library start page with Downloads, Songs, Albums, Artists, History and Shuffle all.
- Playlists with custom covers (photo crop), pinning, grid or list view, and import from a YouTube or YouTube Music link.
- Favorites, including adding a whole album or playlist.
- Multi-select across song, album and playlist lists.

### Monthly Replay
- A recap of your month: top songs, top artists and listening time, shown as a story, with a poster you can share and Play top songs.

### Look and feel
- Eight themes: Dark, OLED Black, Light, Liquid Glass, Catppuccin Mocha, Nord, Rosé Pine Dawn and Custom, plus accent colours with a colour picker.
- Liquid Glass: see-through glass with adjustable tint and lens strength, and a glass player and tab bar.
- A redesigned navigation bar and mini player, and smooth page transitions.
- Reduce animations, for less motion.
- First-run onboarding: pick your languages, genres and artists, so Home is personal from the first launch.

### Settings, backup and sync
- Searchable Settings, organised into sections.
- Local backup and restore by category: playlists, favorites, history, pinned songs, downloads and settings.
- Optional Google Drive sync of playlists, favorites, history, Speed dial and settings. Your data is kept in a private folder in your own Drive.
- Reset app and Quit Whiplash.

---

## Architecture

```
UI (Compose)  →  ViewModel  →  PlaybackController  →  MediaController  →  MediaSessionService (ExoPlayer)
                                      │
                                      ├── PlaybackManager (stream sources with fallback)
                                      │      ├── NewPipe (NewPipeExtractor)
                                      │      └── YouTube direct
                                      ├── RadioEngine + InnerTube client (autoplay and Quick Picks)
                                      ├── LibraryRepository / LocalLibraryRepository (Room + MediaStore)
                                      ├── SettingsRepository (DataStore)
                                      ├── Lyrics providers (LRCLIB, lyrics.ovh)
                                      └── Backup and Google Drive sync
```

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Playback | AndroidX Media3 (ExoPlayer, MediaSession) |
| YouTube | NewPipeExtractor, plus a small YouTube Music (InnerTube) client |
| Storage | Room, DataStore |
| Networking | OkHttp |
| Images | Coil |
| Lyrics | LRCLIB, lyrics.ovh |
| Sync | Google Drive (Google Play services sign-in) |
| Performance | Baseline profile |

**Minimum SDK:** 26 (Android 8.0) · **Target SDK:** 36

---

## Known limitations

- YouTube access relies on unofficial extraction. YouTube can change its systems at any time, which may need an app update; the second stream source makes this less likely to stop playback.
- Some vendor skins (for example Vivo/iQOO OriginOS) limit which apps appear in their own island or quick-switch media pickers. Whiplash uses the standard Android media session, so this is outside the app's control.
- Networks with YouTube Restricted Mode (common on school and office Wi-Fi) block some songs.
- The app is portrait only.

---

## Disclaimer

Whiplash uses NewPipeExtractor to access publicly available YouTube and YouTube Music content and is intended for personal use. It is not affiliated with, endorsed by or sponsored by YouTube, Google, LRCLIB or lyrics.ovh. Users are responsible for following YouTube's Terms of Service where they live.

## License

**Copyright (c) 2026 Shahid Ansari. All rights reserved.**

Whiplash is proprietary software under the [Whiplash Proprietary License](LICENSE). You may read the code and install the official APK for personal use. You may not copy, modify, rename, re-skin, build, redistribute or sell it, or use it to train or prompt AI models, without written permission. Versions before 1.1.0 were published under the MIT License, and copies obtained under it stay under it.

## Acknowledgements

- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor): YouTube and YouTube Music extraction
- [LRCLIB](https://lrclib.net) and [lyrics.ovh](https://lyrics.ovh): lyrics
- [AndroidX Media3](https://github.com/androidx/media): ExoPlayer and MediaSession
