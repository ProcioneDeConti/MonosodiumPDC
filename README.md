# MonosodiumPDC

An unofficial Android client for [e621](https://e621.net), built with Kotlin and Jetpack Compose.

This is a personal project, not affiliated with or endorsed by e621. It talks to e621 exclusively through its official public API.

## Features

- **Browsing & search** - tag-chip search bar with live autocomplete suggestions (category-colored, include/exclude state shown at a glance), saved searches, and a rainbow-themed pull-to-refresh indicator.
- **Post viewer** - full-screen swipeable viewer for images, GIFs, APNGs, and video (via Media3/ExoPlayer), with voting, favoriting, tag browsing (add to blacklist, search, exclude from search), comments, and a detailed info sheet (score, dimensions, MD5, status, sources, etc. - tap any value to copy it).
- **Blacklist** - client-side blacklist filtering with a quick disable/re-enable toggle, plus import/export against your e621 account's saved blacklist.
- **Favorites**
- **Messages (Dmail)** - read, reply, and compose private messages.
- **Forum** - browse topics and reply to posts.
- **User profiles** - your own profile and any other user's, with stats, feedback, and bio, reachable by tapping a name/avatar anywhere one appears (comments, forum posts, messages).
- **Accent color theming**, DText (e621's markup language) rendering, and a first-launch EULA gate.

## Requirements

- Android 14 (API 34) or newer
- An e621 account (optional) - required for posting, voting, favorites, messages, and forum replies; browsing works without one

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
