# Changelog

All notable changes to Whiplash are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Pull Home down to refresh it. Both Speed dial and Quick Picks are refreshed together, and the spinner is held until the work genuinely finishes rather than released on a timer. The refresh button in the Quick Picks header is unchanged and still refreshes only Quick Picks, since that's the section it sits in — so a pull spins both the pull indicator and that button, while tapping the button spins only itself.
- The Queue sheet now opens scrolled to the track that's playing instead of back at the top. Once autoplay has extended a queue to a few hundred entries, the current song was far below the fold and had to be hunted for, even though the sheet already highlighted it.
- A one-tap Play all in the Quick Picks header, next to Refresh. Quick Picks rows could only be played one at a time — the shortcut Favorites and Playlists already had.
- Back from any tab other than Home now returns to Home rather than closing the app, which is the standard behaviour for a bottom-navigation app. Favorites also gains a matching back button, present whether or not you have any favorites yet.
- Separate audio quality ceilings for Wi-Fi and mobile data, so streaming can stay at the highest quality on Wi-Fi without spending mobile data at the same rate. Both fall back to the existing single Audio Quality value, so nothing changes until you set them.
- A Skip Silence toggle that removes silent stretches from playback, applied to the running player rather than only from the next track onward.
- Downloaded files now carry embedded title, artist, album and cover art in the file itself, so a downloaded track shows up correctly in other music players and file managers rather than as an untitled audio file.

### Changed
- **Settings has been rebuilt to look like the rest of the app.** It was the only screen using grouping cards and divider lines, and had more borders than any other screen — Home draws none at all. The cards and all eleven dividers are gone, every setting now carries a leading icon in the same position other list rows put artwork, and sections are separated by clear space instead of boxes. The backup category chips now use the same chip component as the Search and Library tabs, rather than a private copy that made a multi-select filter look identical to a primary button.
- The Search and Library tab chips (Songs / Albums / Artists / Downloads) no longer fill and outline every chip. Only the selected one is filled, so there's an obvious answer to which tab you're on, and their tap targets went from roughly 28dp to a full 48dp.

### Fixed
- The app title was indented 8dp further than the content beneath it on every screen, so "Whiplash" didn't line up with "Quick Picks", nor "Settings" with "Playback".
- The Wavy option in Progress Bar Style previewed as a flat line identical to Minimal. It was sampled at half-wavelength steps, which lands on a zero crossing every time, so the preview only ever showed the wave where it has no height. It now matches what the full player actually draws.
- Opening a playlist no longer animates. The list-to-detail transition was visibly jumping: opening a large imported playlist costs one long frame for its first composition, and because animations run on a clock rather than per frame, that lost frame skipped the animation up to 70% of the way through in a single step. Every animated property showed it — a slide jumped sideways, a fade flashed the background through. An instant swap has nothing to interpolate.
- Long playlist and artist names no longer wrap down the screen and shove the action buttons around; they clip with an ellipsis like every other title in the app.
- The Shuffle / Play all / Download icons in playlist and Favorites headers were 32dp apart instead of the standard 24dp, an accidental side effect of raising icon buttons to 48dp for accessibility.
- Scrolling a large playlist is smoother: the batch download button recomputed four derived lists on every recomposition, about 1,500 element visits per frame on a 250-track playlist.
- **The lockscreen's elapsed/remaining time and progress bar now update reliably** while the screen is off, instead of intermittently sitting blank and frozen until a pause/play or skip. The track duration was never published to the media session at all, so whenever playback began before the player had finished parsing the audio container, the lockscreen was handed a duration of -1 and had no scale to draw. Whether that happened varied with network speed and cached state, which is why it came and went and looked like a device-specific quirk. The duration is already known before playback starts and is now sent up front.
- **The lockscreen, notification and Bluetooth controls now switch to the new track the moment you skip**, instead of keeping the previous track's title and artwork for the three to four seconds it took to resolve the next stream — and reporting PAUSED while they did, so a skip looked like you had merely paused the current song.
- **A song YouTube has taken down no longer leaves a permanently dead Speed dial tile.** A play was recorded the instant a song was tapped, before anything knew whether it could actually play, so an unavailable track earned a tile it could never honour — and a song with two uploads on YouTube showed two tiles, one of which never worked.

- **Audio caching never saved anything at all.** The cache directory stayed empty no matter how much was played, so every replay of a song re-downloaded it in full and playback in airplane mode was impossible. Audio is now fetched in explicit byte ranges, which both fixes the caching and stops YouTube pacing the download to real-time playback speed — the reason a stalled buffer never recovered.
- **Music could stop dead in the middle of a session with no error, no retry and no skip.** Nothing in the app listened for player-level failures, so when a stream URL expired mid-playback ExoPlayer reported the failure into a void and simply went idle. A track now gets one automatic retry from where it stopped, which makes an expired URL recover on its own; anything else shows a real message instead of silence.
- **A partly-downloaded song could be saved with a completed checkmark.** The download was marked finished as soon as the data stopped arriving, which is indistinguishable from a connection dropping halfway. The file size is now checked against what the server said to expect, so a cut-off transfer retries instead of leaving an unplayable track behind a checkmark.
- **Downloading a whole album opened one connection per track simultaneously** — 50 tracks meant 50 parallel downloads, each holding a full copy of its file in memory to write metadata. Now capped at three at a time, with the rest queued and still cancellable.
- **The wrong song's lyrics could be displayed**, scrolling in sync with nothing to indicate they were wrong. Lyrics were taken from the first fuzzy search result without checking it was even the same song. A candidate now has to match on title, on artist, and on duration within five seconds, or lyrics are honestly reported as unavailable.
- **The first song played after a fresh install now appears in History and Speed dial immediately**, instead of staying invisible until a second song was played or the app was restarted.
- **The 200-entry History limit was only ever a display limit.** Nothing deleted old rows, so the table grew on every single play forever, getting slower the longer the app was used.
- **A failed search category looked identical to having no results.** If Albums, Playlists or Artists failed to load, the tab simply showed nothing, with no indication anything had gone wrong and no way to retry. Each tab now reports its own error and offers a retry, and "No results" is only shown when every category genuinely came back empty.
- **A restore that reported failure could still have overwritten half your library.** Restoring is now all-or-nothing, so a failure leaves everything exactly as it was. Backups from a newer version of the app are refused outright instead of being partly imported, and Skip Silence and the per-network quality settings are now included, having previously been lost silently on reinstall.
- **Restoring a backup could fill the Downloads tab with unplayable songs** — download records were recreated even though the audio files themselves aren't part of a backup. Downloads whose file isn't present are now skipped, and download records whose file has since been deleted are cleaned up on startup.
- **Duplicate results in a single search response could crash the results list.** YouTube legitimately returns the same video or playlist twice, and the first page of every search went on screen unchecked.
- **Tapping the GitHub link in Settings could kill the app** on a device or work profile with no browser installed. It now shows a message instead.
- Cancelling an in-flight download could delete a *different* download's file if the app happened to be starting up at the time.
- The on-device music library could be left inconsistent — songs removed while their albums still counted them — if a rescan was interrupted partway. It now applies as a single all-or-nothing update, and a permission revoked mid-scan reports a scan error instead of crashing.
- Turning off Audio Cache while a song was queued to play could leave that song unable to start.
- The search cache and the in-memory lyrics cache both grew without limit for the lifetime of the app, as did the queue when Autoplay was left running for hours.
- Icon buttons across the mini-player, queue and every song row were smaller than the 48dp minimum the app documents. The icons look the same; only the tappable area grew.
- Deleting a playlist now asks for confirmation first, matching every other irreversible action in the app.
- Adding a song to a playlist it's already in now says so, instead of claiming it was added.
- Tapping the Home, Search or Playlists tab while already on it now backs out of an open History, search detail or playlist, matching the back gesture.
- Shuffle and Repeat now confirm their new state, rather than only changing an icon tint — easy to miss on Repeat, where three states share one icon.

## [0.4.0] - 2026-09-03

### Added
- **Offline downloads** (Library > Downloads): download any YouTube track's audio for offline playback, with a dedicated Downloads tab (Play all / Shuffle / Clear all), an animated per-row progress/checkmark/failed badge everywhere a track appears (Search, Home, Library, full player), a one-tap Download action in every song-actions sheet and the full player's overflow menu, and bulk "Download album"/"Download playlist" entry points with a real batch progress state (Download → Downloading → Downloaded) on Album/Artist/Playlist detail pages. A separate Download Quality setting controls download bitrate independently from streaming Audio Quality.
- Playback Speed and "Add to playlist" are now directly reachable from the full player's overflow menu, not just Settings/other song-actions sheets.
- "Copy to other playlist" alongside the existing "Move to other playlist" and "Remove from playlist" — a playlist's own song-actions sheet now offers all three when viewing a song already inside that playlist, in place of the generic "Add to playlist". "Move" removes the song from the playlist you're viewing after adding it elsewhere; "Copy" adds it elsewhere without removing it from the one you're viewing.
- The Downloads tab is now first in Library and shown by default on entry — it's the only tab that never needs local-media permission, so it's usable immediately either way.
- A Share icon on search-result album/playlist detail pages that shares the real, resolvable YouTube playlist/album link — deliberately not added to manually-built/home Playlists, which have no single external link to share.
- Advanced Backup: Settings' "Local backup" now lets you choose exactly which categories to back up (Playlists, Favorites, History, Pinned, Downloads, Settings) via a chip picker, instead of only ever backing up everything. All categories are selected by default, so a single tap still backs up everything exactly as before. Restoring a selective backup merges it in immediately with no app restart; restoring an older full backup still works exactly as before.
- Smooth, directional transitions across the whole app: bottom-nav tab switches crossfade, drilling into a detail screen (Search → Album/Artist, Home → History, Playlists → playlist detail) slides in the correct direction depending on whether you're going deeper or back, and Album/Artist detail's loading state now crossfades into the loaded content instead of snapping.
- Redesigned app launcher icon (a single bold "W" mark, replacing the previous two-letter monogram).

### Fixed
- **Release builds are now properly signed with a real, dedicated release key.** Previously the release build type had no signing configuration at all, so the actual public APK on earlier GitHub releases had been built and shipped as an unsigned/debug build instead — signed with the shared, publicly-known Android debug key rather than a real one, and without release-build packing. From this release onward, `assembleRelease` produces a properly signed, genuine release build.
- A real crash: adding the same song to a playlist twice created duplicate rows, which crashed the track list the moment that playlist was opened. Duplicate inserts are now blocked, and an already-affected playlist is safe to view without needing a migration.
- Opening a playlist right after creating or importing a different one could show the wrong playlist's tracks under the wrong name, requiring a force-stop to fix.
- An already-downloaded song only showed a way to remove its download when viewed from the Downloads tab itself — every other screen (Search, Home, Favorites, Playlists) showed a dead-end "Downloaded" row with no removal option.
- Canceling an in-flight download could get stuck showing progress indefinitely instead of actually stopping; a download interrupted by the app being killed could also leave an orphaned partial file with no record of it, both now cleaned up correctly.
- The same song played from two different surfaces (e.g. streamed from Search vs. played from a download) could create duplicate History/Speed Dial entries or be pinned twice, since both are really the same underlying video; local on-device tracks no longer incorrectly show up in Speed Dial/History, which are meant to reflect online listening only.
- Moving a song to a playlist that already contained it deleted the song from the *source* playlist too, even though nothing was actually gained in the target — a "no-op" case now genuinely leaves the source untouched.
- Autoplay's recommendation filter now catches more mashup/compilation-style titles that were slipping through: mashups separated by the Unicode "×" character (not just the ASCII letter "x"), titles ending in a bare, unqualified "Mix", and compilation titles using "Hits <year>" or Spanish "Sin Anuncios" (ad-free) branding — found and verified via repeated real-world autoplay testing, not synthetic cases.
- The Home screen's Speed Dial grid now animates a tile smoothly to its new position when a play or a pin reorders it, instead of the whole grid snapping instantly; its row height is also now measured from the real screen width instead of a fixed guess, fixing song titles getting visually clipped against the Quick Picks section on some screens.
- The Back button on search-result Album and Artist detail screens no longer shows a visible border, matching the borderless style of every other icon button on those screens.
- **Gapless Playback's toggle in Settings did nothing at all** — the setting was stored and displayed but never actually consulted by playback, so turning it off had zero effect. It now genuinely gates the next-track prefetch that makes gapless transitions possible.
- **A real data-loss risk**: the local database previously fell back to destructively wiping all user data (playlists, favorites, history, downloads, settings) on any future schema change, since only a temporary fallback had ever been written. Real migrations now cover every version the app has ever shipped, verified against Room's own recorded schema history with a direct test proving existing data survives the upgrade.
- A rare but real crash/corruption risk in the download manager: two different threads could write to the same in-flight-downloads tracking data at once with no synchronization.
- Removing a single downloaded song deleted it instantly with no confirmation, unlike every other destructive download action (canceling an in-flight download, clearing all downloads) which already confirmed first.
- Tapping "New playlist" from an artist page's "Add to playlist" sheet silently did nothing; it now actually creates the playlist.
- Pressing Download again on a song that's already downloading now shows "Already downloading" instead of silently doing nothing.
- A few Settings and Album/Artist detail page titles were quietly falling back to a mismatched default text style because the app's own custom type scale was missing two sizes it referenced.

### Also included (shipped earlier, never documented in a release until now)
A rescan of the full commit history turned up real, substantial Search features that shipped before v0.3.0 but were missed from every release page since, including v0.3.0's own:
- YouTube-Music-style "Recent searches" on the idle Search screen (persisted separately from the short-lived result cache), with per-item removal and a "Clear all" confirmation.
- Live YouTube search suggestions while typing, filling what used to be a "No results found" flash during every keystroke's debounce window.
- Real trending-artist search suggestions (replacing hardcoded ones) and typing-only autocomplete behavior matching YouTube Music/Spotify, instead of autocompleting on every keystroke.
- Genuine cursor-based infinite scroll for Songs/Albums/Playlists/Artists search results (previously capped at the first ~20 results with no way to see more).
- App-wide toast feedback for actions that previously completed with zero confirmation: favoriting, pinning, creating/renaming/deleting a playlist, adding a song to a playlist, play next/add to queue, clearing the queue, removing from the queue, clearing history, removing from Speed Dial/Quick Picks, clearing recent searches, and clearing the audio cache.
- A per-item "Remove from history" action on the History screen (previously only removable by clearing the entire list).
- Several visual consistency fixes (removed heavy card borders on the Search error state and the Library permission prompt; smoothed the toast dismiss animation; removed a redundant empty-state label on Search).


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
