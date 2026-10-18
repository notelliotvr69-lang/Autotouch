# Discord VOID — Android UI prototype

This is a standalone Android WebView prototype for the VOID purple cyberpunk UI concept. It is **not** a modified Discord APK and does not log in to Discord, connect to Discord APIs, or unlock Nitro/server-side features.

## Build with GitHub Actions

1. Open the repository's **Actions** tab.
2. Select **Discord VOID Android prototype**.
3. Select **Run workflow**.
4. After the run succeeds, open that run and download the `Discord-VOID-prototype-debug-apk` artifact.
5. Extract the ZIP and install `app-debug.apk` on an Android test device. Android may ask you to allow installation from the app used to open it.

A debug APK is unsigned with a release key and intended for testing. GitHub Actions builds the prototype only; it does not modify or redistribute the user's original Discord APK.

## Current prototype features

- Dark purple theme presets and accent color picker
- Demo favorite-friend list, local persistence, and search
- Local image/GIF selection for avatar and background previews
- Chat bubble styling, glow, and reduced-motion controls
- Local privacy-preview toggles

## Limitations

The friend list is demo data. Favorite friends do not sync with Discord, DM buttons are placeholders, and profile animations remain local previews. Discord's official upload and Nitro-gated features remain server-controlled. No Discord credentials, tokens, or private APK are stored in this repository.
