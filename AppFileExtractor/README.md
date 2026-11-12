# App File Extractor

Native Android starter app that lists launchable installed apps and exports the selected app's APK contents into a ZIP chosen by the user.

## Build
The GitHub Actions workflow in `.github/workflows/app-file-extractor-android.yml` builds a debug APK on pushes affecting this folder or when manually dispatched. Download the APK from the workflow run's Artifacts section.

## Limits
- Exports packaged APK entries, not another app's private data directory.
- Many resources are compiled Android XML or binary resource tables and are not plain-text editable.
- This starter exports only; it does not patch, re-sign, or install modified APKs.
- Only modify apps you own or have permission to modify.
