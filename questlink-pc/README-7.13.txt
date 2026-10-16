QuestLink PC v7.13-dev

Close the old QuestLink PC app and Gorilla Tag before updating.
Extract this entire folder, including opencomposite, and run QuestLinkPC.exe.
Use Quest APK v7.13-dev and runtime v0.5-dev; update both for higher resolution.
Turn on the Quest and connect the QuestLink client to this PC over Wi-Fi.

This fixes the missing Unity OpenVR mirror export and checks for an early game
crash before reporting launch success. The original game openvr_api.dll backup
is preserved as openvr_api.dll.questlink-original. SteamVR is not required for
this Gorilla Tag launch path. Headset gameplay remains unverified.

VR Quality tab: Normal is 1680x1760 per eye. Restart GTAG after changing it.
Controller profile notification added in runtime 0.5-dev.
