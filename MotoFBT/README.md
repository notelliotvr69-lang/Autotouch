# MotoFBT

Standalone Android FBT rebuild for VRChat.

Phone camera -> MediaPipe Pose Landmarker -> adaptive smoothing -> tracker solver -> OSC -> VRChat.

This is a clean Kotlin/Android Studio rebuild under the existing AutoTouch repository, while the original decompiled APK remains the reference implementation. GitHub Actions downloads the MediaPipe Pose Landmarker Full model during the build and publishes the debug APK as an artifact.

<!-- build trigger: 2026-10-05-2 -->
