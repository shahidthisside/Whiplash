# Changelog

All notable changes to Whiplash are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.2.0] - 2026-08-30

### Added
- Full History screen (up to 200 recently played tracks), reachable from a new icon next to Speed dial's Clear button on Home.
- Autoplay on/off shortcut directly in the Queue sheet, kept in sync with the existing Settings toggle.
- Four full-player seek bar visual styles — Classic, Wavy, Waveform, Minimal — with live mini-previews in Settings.
- Local backup and restore: saves playlists, favorites, history, pinned songs, and settings to a file you choose, and can restore from it later.
- "Rename playlist" option in the playlist long-press/3-dot menu (the underlying rename logic already existed but had no UI).

### Fixed
- Tracks played via the gapless-prefetch or already-cached-on-disk fast paths never got saved to the local library, so they could silently disappear from Speed dial, Favorites, and Playlists despite playing correctly.
- Clearing the queue (with Autoplay enabled) permanently stopped autoplay for the rest of that track instead of properly continuing.
- A stalled or hung network/extraction attempt could leave the mini-player stuck "buffering" indefinitely with no error ever shown; playback resolution is now time-bounded and always surfaces a real error.
- Search, Album detail, and Artist detail could show a raw technical error message (e.g. a DNS failure string) instead of a plain "No internet connection" a user can actually act on.
- The "Last backup" status text in Settings ran on the same line as the description above it instead of starting on its own line.
- Playlist rows used a heavier bordered card style inconsistent with the rest of the app; several icon buttons (Playlists' "+", the Queue sheet's controls, Speed dial's clear button) had unnecessarily heavy circular backgrounds — all flattened to match the app's established style, including a follow-up alignment fix between the Playlists header button and each row's own menu button.
- Search's selected result tab (Songs/Albums/Artists/Playlists) no longer resets to Songs after opening and returning from an album or artist detail page.

## [0.1.0] - 2026-08-28

Initial public release: background playback via a real Media3/ExoPlayer `MediaSessionService`, YouTube/YouTube Music search and streaming through NewPipeExtractor, a multi-provider fallback architecture, gapless playback, crossfade, sleep timer, persistent queue with YouTube-style autoplay, local/offline device music library, unified search, Album/Artist detail pages, History/Favorites/Playlists/Speed Dial, synchronized lyrics via LRCLIB, a custom frosted-glass design system with six selectable themes, and full haptic/accessibility support.
