# Roblox Classic APK input

The real Roblox APK is too large for GitHub's normal single-file upload limit.

Place these nine files in `RobloxOG/input/`:

- Roblox-Classic.apk.part-00
- Roblox-Classic.apk.part-01
- Roblox-Classic.apk.part-02
- Roblox-Classic.apk.part-03
- Roblox-Classic.apk.part-04
- Roblox-Classic.apk.part-05
- Roblox-Classic.apk.part-06
- Roblox-Classic.apk.part-07
- Roblox-Classic.apk.part-08

The GitHub Actions workflow concatenates them back into `Roblox-Classic.apk`, verifies the SHA-256, and then uses that exact APK as the modification input.

This project is for the OG-style UI modification only. It does not implement arbitrary Luau execution or runtime injection.
