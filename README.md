# viyt

A lean YouTube client for Android. Sign in, browse your library, stream and download, with no ads and no trackers.

## Features
- **Sign in with Google** (or import a `cookies.txt`). Your session stays on the phone.
- **Your YouTube:** home feed, subscriptions feed, liked videos, Watch later, history, your playlists, subscribed channels.
- **Search,** or paste any YouTube link (video, playlist or channel).
- **Stream** at 360p to 1080p, with a mini player (picture-in-picture).
- **Download** video (1080p, 720p, 480p, 360p) or audio (M4A, MP3) into `Download/viyt`.
- **Share to viyt** from the YouTube app to play or download right away.
- **Self-updating engine:** yt-dlp updates itself every 6 hours (nightly channel by default), and QuickJS is bundled so YouTube's JavaScript challenges get solved.

## How it works
| Part | Library |
|---|---|
| Extraction, your library, downloads | [yt-dlp](https://github.com/yt-dlp/yt-dlp) via [youtubedl-android](https://github.com/yausername/youtubedl-android) |
| JavaScript challenges | [QuickJS-NG](https://github.com/quickjs-ng/quickjs), built for Android in CI |
| Merging and audio conversion | FFmpeg (youtubedl-android) |
| Player | AndroidX Media3 / ExoPlayer |
| UI | Jetpack Compose, Material 3 |

## Building
Push to `main`, and GitHub Actions does the rest: it builds QuickJS with the NDK, builds the APKs and publishes them as a **Release**. Install `viyt-*-arm64-v8a.apk` on any modern phone.

The signing key in `signing/` keeps every build installable as an update over the last one. Keep this repo **private**.

## Layout
```
app/src/main/java/com/viyt/app/
  core/       Engine (yt-dlp wrapper), Cookies (session), Prefs, Models
  download/   DownloadService (foreground queue), Downloads (state)
  ui/         MainActivity (tabs), PlayerActivity, LoginActivity, Settings, Components
```
