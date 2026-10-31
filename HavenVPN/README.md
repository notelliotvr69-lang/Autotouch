# Haven VPN — Android starter

Haven VPN is an Android WireGuard client starter with a native branded interface.

## Current functionality
- Import a WireGuard `.conf` profile through Android's document picker.
- Request Android's VPN permission.
- Start and stop a WireGuard tunnel using the WireGuard tunnel library.

## Not included yet
- Haven-operated VPN servers or worldwide server locations.
- Accounts, subscriptions, automatic server selection, or a management backend.
- Production release signing, security audit, or independent leak testing.

A valid profile must come from a VPN server you trust. Do not share its private key. This app does not create free VPN servers.

## Build
The repository workflow `.github/workflows/haven-vpn-apk.yml` builds a debug APK when HavenVPN files change, or when manually started from GitHub Actions.

Open the workflow run in the repository's Actions tab and download the `Haven-VPN-debug-APK` artifact. Extract the downloaded artifact ZIP to obtain `app-debug.apk`.

This debug APK is for personal testing, not a Play Store release.
