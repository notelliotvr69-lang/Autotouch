# VOID for Discord Android

VOID is a customization project for the **real Discord Android client**. It is not a standalone imitation of Discord.

## Current deliverable

The current milestone is a set of three Vendetta spec-2 themes compatible with clients/loaders that support legacy Vendetta themes:

- `void-theme.json` — Purple Night
- `void-neon.json` — Neon Violet
- `void-orchid.json` — Orchid Glow

These are theme files, **not an APK**. The theme changes colors; it does not add animated profile pictures, animated backgrounds, favorites, or custom chat-bubble behavior yet.

## Install a theme

1. Use a compatible Discord Android client-mod loader that supports legacy Vendetta themes.
2. In the loader's settings, enable its legacy theme importer if needed.
3. Add the raw URL for one theme:
   - Purple Night: `https://raw.githubusercontent.com/notelliotvr69-lang/Autotouch/main/Discord-VOID-Mod/void-theme.json`
   - Neon Violet: `https://raw.githubusercontent.com/notelliotvr69-lang/Autotouch/main/Discord-VOID-Mod/void-neon.json`
   - Orchid Glow: `https://raw.githubusercontent.com/notelliotvr69-lang/Autotouch/main/Discord-VOID-Mod/void-orchid.json`
4. Select the theme and reload the client if prompted.

Loader screens and compatibility vary by version. If your loader does not offer legacy theme importing, do not assume these URLs will work in its native theme picker.

## Build

GitHub Actions validates all three JSON theme manifests and packages them into a downloadable ZIP artifact. The artifact is a theme bundle, not an installable Android APK.

## Roadmap

1. Verify the theme import against the exact Discord build supplied for testing.
2. Build a proper Revenge-native theme plugin if needed, using the current documented plugin API.
3. Investigate safe, supported customizations for favorites, friend colors, quick DM access, and visual effects.

## Limits and safety

- This project does not unlock Nitro or override Discord's paid-feature restrictions.
- It does not collect Discord credentials or account tokens.
- Modified clients can break after updates and may violate Discord's terms. Keep a clean official install available.
- The original Discord APK package is not committed to this public repository.
