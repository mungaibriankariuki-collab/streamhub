# StreamHub

A small Android app for streaming and saving videos. Pick a site from the home screen — YouTube,
TikTok, Instagram, X, Twitch and more — and it opens inside the app. A built-in downloader turns the
current page into an **MP4 video** or **MP3 song** using [yt-dlp](https://github.com/yt-dlp/yt-dlp)
through [youtubedl-android](https://github.com/JunkFood02/youtubedl-android).

## Get the APK

Every push to `main` is built by GitHub Actions. Open the repo's **Releases** page and download
`StreamHub.apk` from the newest build. Allow "Install unknown apps" for your browser or file manager
when asked.

## What's inside

- **Site launcher**: YouTube, TikTok, Instagram, Facebook, X, Reddit, Twitch, Kick, Vimeo,
  Dailymotion, SoundCloud, Bandcamp, Rumble, Odysee, Bilibili and Internet Archive.
- **In-app browser**: address bar, back/forward, reload, desktop mode, fullscreen HTML5 video,
  file uploads, third-party cookies and a DuckDuckGo search fallback.
- **Downloader**: tap the download icon on any page (or use the share sheet with
  "Share → StreamHub"). Choose MP4 (up to 1080p), MP3, or a direct file.
- **Saves to** `Download/StreamHub`. The downloader engine can be updated from the menu.

## Good to know

- **DRM services won't work** (Netflix, Spotify, Disney+, etc.). Use their own apps.
- Some sites need a login; the WebView keeps cookies, but not every site plays nicely in a WebView.
- The APK is large because it bundles Python and ffmpeg for downloading and converting.
- Only download content you own or have permission to save.
