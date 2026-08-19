# MonosodiumPDC

An unofficial Android client for [e621](https://e621.net) and [e6AI](https://e6ai.net), built with Kotlin and Jetpack Compose.

This is a personal project, not affiliated with or endorsed by e621 or e6AI. It talks to them exclusively through their official public APIs.

## Features

- **e621 and e6AI, one app** - switch between e621 and e6AI (e621's AI-generated content sister site) with a single toggle in the hamburger menu. Each site keeps its own separate login, switching refreshes your current search immediately, and a small badge next to the search bar always shows which one you're browsing.
- **Browsing & search** - tag-chip search bar with live autocomplete suggestions (category-colored, include/exclude state shown at a glance), saved searches, and a rainbow-themed pull-to-refresh indicator. Pinch or spread the grid to resize thumbnails, from dense to large - handy on tablets and other big screens. A small, unobtrusive loading bar shows on any image still fetching over a slow connection.
- **Post viewer** - full-screen swipeable viewer for images, GIFs, APNGs, and video (via Media3/ExoPlayer, with custom loop/speed/gesture controls), with voting, favoriting, tag browsing (add to blacklist, search, exclude from search), comments, and a detailed info sheet (score, dimensions, MD5, status, sources, etc. - tap any value to copy it). Swipe down on an image to dismiss the viewer.
- **App Links** - tapping a link to e621.net, e926.net, or e6ai.net opens this app directly instead of a browser, and `/posts/{id}` links jump straight to that post.
- **Blacklist** - client-side blacklist filtering with a quick disable/re-enable toggle, plus import/export against your account's saved blacklist. Posts temporarily unhidden by the disable toggle get a thin caution-stripe border so it's obvious they'd normally be filtered.
- **Favorites**
- **Messages (Dmail)** - read, reply, and compose private messages.
- **Forum** - browse topics and reply to posts.
- **User profiles** - your own profile and any other user's, with stats, feedback, and bio, reachable by tapping a name/avatar anywhere one appears (comments, forum posts, messages). A profile's Activity section also opens a user's full comment history and moderation records, both with infinite scroll.
- **Connection status** - a health-check indicator at the bottom of the menu shows whether the active site is reachable (green/checking/red), including the specific error - Cloudflare included - when it isn't.
- **Downloads** - save a post's original file with one tap, into the device's shared Pictures/Movies folders by default or a folder of your choice (Settings > Downloads).
- **Cache management** - an adjustable on-disk image/thumbnail cache limit (or unlimited), with current usage shown and a one-tap clear.
- **Backup & Restore** (Settings) - export your account, blacklist, and preferences to a file, optionally protected with a password (AES-256-GCM, PBKDF2-derived key - the file is useless without it), and restore from one on a new device or after a reinstall.
- **Updates** (Settings) - a "What's New" dialog pops up automatically after each update with a short changelog, reopenable anytime; a manual "Check for Updates" button checks GitHub for a newer release on demand, with a status shield (checking/up to date/error/update available, tap to open its release page) and a live count of update checks left this hour.
- **Accent color theming**, a consistent rounded-corner visual language throughout, DText (e621's markup language) rendering, and a first-launch EULA gate.

## Requirements

- Android 14 (API 34) or newer
- An e621 and/or e6AI account (optional, and separate from each other) - required for posting, voting, favorites, messages, and forum replies; browsing works without one

## Building

```
git clone https://github.com/ProcioneDeConti/MonosodiumPDC.git
cd MonosodiumPDC
./gradlew assembleDebug
```

Or open the project directly in Android Studio and run it from there.

## Tech stack

Kotlin, Jetpack Compose (Material 3), Navigation Compose, Retrofit + OkHttp + kotlinx.serialization, Coil, Media3 ExoPlayer, and DataStore Preferences.

## License

The source code in this repository is available under the terms in [LICENSE](LICENSE) - free to use, modify, and redistribute, provided the original copyright and attribution notices are kept intact.

Using the compiled app itself is additionally governed by the End User License Agreement bundled with it (`app/src/main/res/raw/eula.txt`), shown on first launch.

## Disclaimer

Transparency is important. This app is a personal project. <ins>**Parts of this app, including some of the third-party tools, libraries, and dependencies we rely on behind the scenes, were written with the assistance of AI tools.**</ins> If this bothers you, please do not install, use, or otherwise disseminate its content. I have not, nor will I ever claim I am a developer: competent or otherwise. I use AI to do what I otherwise may not have been able to do on my own.
