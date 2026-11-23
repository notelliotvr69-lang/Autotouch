# VRFlex Rootless Toolkit

## Product goal

Provide useful Quest customization and developer-tool convenience without root access, bootloader unlocking, firmware flashing, Horizon OS partition writes, system APK replacement, or patching other apps.

## Capability layers

1. **Core app (implemented):** local per-game preferences, launcher-visible app discovery, game profile selection, and a local Wi-Fi status endpoint for the phone companion.
2. **Profile portability (next):** versioned JSON export/import with validation, explicit confirmation, and safe defaults for unknown fields.
3. **Overlay (conditional):** a floating VRFlex panel only where the Quest OS grants the required overlay/window capability. Detect and report permission availability; never claim it works universally.
4. **Game integrations:** separate, versioned adapters for games that expose a documented plugin, mod, or OpenXR extension interface. Each adapter declares supported game versions and the exact controls it implements.
5. **OpenXR app-local transforms:** supported apps can adjust their own reference-space/controller presentation. These transforms do not alter other apps or Quest-wide tracking.

## Integration contract

A game adapter must declare its game/package ID, supported version range, adapter version, and supported capabilities. The host must check compatibility before enabling a control. Missing adapters leave settings in profile-only mode. An adapter must not modify another app's files, bypass anti-cheat, or write to protected system partitions.

## Capability labels

- **Available:** implemented and usable by the current app.
- **Requires permission:** possible only after the operating system grants a documented permission.
- **Requires game adapter:** can work only in a compatible game integration.
- **Unsupported system-wide:** ordinary third-party apps cannot change Quest-wide headset/controller tracking or another app's tracking origin.

## Safety and reliability requirements

- No root, bootloader unlock, firmware modification, or system partition writes.
- No replacing or deleting Horizon OS files or other apps' APKs.
- No hidden background command execution from the phone companion.
- Keep the phone protocol limited to explicitly supported, validated commands.
- Store profile data under the VRFlex app's private storage and validate imported data before applying it.
- Display capability status honestly; saved settings are not proof that a game is applying them.

## Current implementation status

The Quest app stores profile preferences and exposes a read-only local TCP `PING`/`STATUS` endpoint. The mobile app reads status over the same Wi-Fi network. The current release does not provide a game adapter, live trigger locomotion, a Y-button overlay, or Quest-wide tracking transforms. Those features must not be advertised as working until implemented and tested on hardware.
