# Changelog

All notable changes to Whiplash are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.3.0] - 2026-08-30

### Added
- Import a playlist by pasting a YouTube or YouTube Music playlist link — a new Link icon next to Playlists' existing "+" button resolves the URL via NewPipeExtractor and creates a real local playlist with all its tracks, which then behaves exactly like a manually-built one (rename/delete/play/reorder all work the same way).
- Full History screen (up to 200 recently played tracks), reachable from a new icon next to Speed dial's Clear button on Home.
- Autoplay on/off shortcut directly in the Queue sheet, kept in sync with the existing Settings toggle.
- Four full-player seek bar visual styles — Classic, Wavy, Waveform, Minimal — with live mini-previews in Settings.
- Local backup and restore: saves playlists, favorites, history, pinned songs, and settings to a file you choose, and can restore from it later.
- "Rename playlist" option in the playlist long-press/3-dot menu (the underlying rename logic already existed but had no UI).
- Home screen artwork and data now preload in the background, with real loading skeletons shown on a cold start instead of a blank or half-populated screen.
- A small GitHub link footer at the end of Settings, linking out to the developer's profile.
- Favorites now has the same Shuffle + Play-all action row as Playlist detail, in the same place and aligned the same way.

### Fixed
- Turning Autoplay off, then back on again while the same track was already playing and sitting at the end of the queue, did nothing — the queue never extended until the user manually skipped away and back. Autoplay now reacts immediately to being re-enabled mid-track.
- The Search results list could freeze when scrolling quickly during pagination (fast-fling stress-tested afterward with zero repeat failures).
- Search's "Search failed" / no-internet-connection state was wrapped in a bordered card instead of the plain, centered layout used elsewhere in the app (e.g. Library's permission prompt).
- Tracks played via the gapless-prefetch or already-cached-on-disk fast paths never got saved to the local library, so they could silently disappear from Speed dial, Favorites, and Playlists despite playing correctly.
- Clearing the queue (with Autoplay enabled) permanently stopped autoplay for the rest of that track instead of properly continuing.
- A stalled or hung network/extraction attempt could leave the mini-player stuck "buffering" indefinitely with no error ever shown; playback resolution is now time-bounded and always surfaces a real error.
- Search, Album detail, and Artist detail could show a raw technical error message (e.g. a DNS failure string) instead of a plain "No internet connection" a user can actually act on.
- The "Last backup" status text in Settings ran on the same line as the description above it instead of starting on its own line.
- Playlist rows used a heavier bordered card style inconsistent with the rest of the app; several icon buttons (Playlists' "+", the Queue sheet's controls, Speed dial's clear button) had unnecessarily heavy circular backgrounds — all flattened to match the app's established style, including a follow-up alignment fix between the Playlists header button and each row's own menu button.
- Search's selected result tab (Songs/Albums/Artists/Playlists) no longer resets to Songs after opening and returning from an album or artist detail page.
- Autoplay's related-track deduplication missed several real cases found through targeted on-device testing: generic compilation-style titles ("Gym Motivational Music | ... | Bollywood | English") were incorrectly treated as a single song rather than a long-form compilation; "All Best Songs"-style compilation titles weren't recognized by the existing keyword check; and near-duplicate uploads of the same song with different bracketed video-type tags (e.g. a "[Choreography]" performance video vs. the official music video of the same track) weren't being caught as duplicates.


## [0.2.0] - 2026-08-29

### Added
- Real on-disk audio cache: a resolved YouTube stream that's already been played starts instantly from disk on replay instead of hitting the network again, backed by Media3's own `SimpleCache` with LRU eviction (300MB default cap).
- New "Storage" section in Settings: a "Cache Songs" toggle (genuinely stops both reads and writes when off, not just new writes), a real current cache size display, and a "Clear cache" button.

### Fixed
- Search's Playlists and Albums tabs kept showing the first-opened item's tracks instead of the newly selected one.
- Album/playlist detail screens could get stuck non-scrollable with artwork appearing frozen, since the artwork header lived outside the scrollable track list.
- General app choppiness on high-refresh-rate displays: the app now explicitly requests the display's highest available refresh rate, and a Compose recomposition-scope issue (the whole tab content re-evaluating on every playback position tick) is fixed.
- "Clear cache" rendered the same faded gray regardless of whether there was anything to clear.

## [0.1.0] - 2026-08-28

Initial public release: background playback via a real Media3/ExoPlayer `MediaSessionService`, YouTube/YouTube Music search and streaming through NewPipeExtractor, a multi-provider fallback architecture, gapless playback, crossfade, sleep timer, persistent queue with YouTube-style autoplay, local/offline device music library, unified search, Album/Artist detail pages, History/Favorites/Playlists/Speed Dial, synchronized lyrics via LRCLIB, a custom frosted-glass design system with six selectable themes, and full haptic/accessibility support.
