# VOID for Discord Android

VOID is a customization project for the **real Discord Android client**. It is not a standalone imitation of Discord.

## Current deliverable

The current milestone is three Vendetta spec-2 theme manifests:

- `void-theme.json` — Purple Night
- `void-neon.json` — Neon Violet
- `void-orchid.json` — Orchid Glow

These are theme files, **not an APK**. The theme changes colors; it does not currently add animated profile pictures, animated backgrounds, favorites, friend-color labels, quick-DM buttons, or custom chat-bubble behavior.

## Getting it into the real Discord app

The documented route is to use a compatible Discord Android client mod such as [Revenge](https://github.com/revenge-mod/revenge-bundle), installed with its official [Revenge Manager](https://github.com/revenge-mod/revenge-manager). Revenge's current source supports Vendetta spec-2 themes, which matches these files.

1. Install Revenge through its official Manager, which installs a separate modified Discord app using the Discord version supported by that Revenge release.
2. Open the modified Discord app and find its Themes settings.
3. Add the raw URL for one theme, then select it:
   - Purple Night: `https://raw.githubusercontent.com/notelliotvr69-lang/Autotouch/main/Discord-VOID-Mod/void-theme.json`
   - Neon Violet: `https://raw.githubusercontent.com/notelliotvr69-lang/Autotouch/main/Discord-VOID-Mod/void-neon.json`
   - Orchid Glow: `https://raw.githubusercontent.com/notelliotvr69-lang/Autotouch/main/Discord-VOID-Mod/void-orchid.json`

Exact screen labels can vary by version. Revenge pins the Discord version it supports, so use its Manager rather than assuming an arbitrary Discord APK will work.

## Build

GitHub Actions validates the three theme manifests and packages them into a downloadable ZIP artifact. The artifact is a theme bundle, not an installable Android APK.

## Roadmap

1. Test theme import in the compatible modified Discord client.
2. Add and test a background using the theme format's supported background field.
3. Investigate additional client customizations—favorites, friend colors, quick DM access, and visual effects—against the documented plugin API.
4. Only consider a custom APK build if a supported, reproducible build route is established.

## Limits and safety

- This project does not unlock Nitro or override Discord's paid-feature restrictions.
- It does not collect Discord credentials or account tokens.
- Modified clients can break after updates and may violate Discord's terms. Keep the official app available.
- The original Discord APK archive is only a local test input and is not committed to this public repository.
