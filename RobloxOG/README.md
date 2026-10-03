# Roblox OG UI

This is a separate Android project inside the Autotouch monorepo. It is intentionally isolated from the existing QuestLink project.

The first build is a standalone UI shell that demonstrates the floating-menu concept without injecting arbitrary Luau into Roblox or modifying Roblox's native engine.

## Build

GitHub Actions builds the release APK and uploads it as an artifact.

## Base Roblox APK

The prior Roblox-OGish APK is not committed into Git because the original package is over 100 MB. If a future build step needs to process that package, the workflow should fetch a separately hosted artifact or release asset rather than rewriting the package in-place.
