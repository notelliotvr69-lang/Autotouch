QuestLink PC v7.12-dev

Close the old QuestLink PC app and Gorilla Tag before updating.
Extract this entire folder, including opencomposite, and run QuestLinkPC.exe.
Keep Quest APK v7.12-dev and runtime v0.4-dev; no new APK is needed for this fix.
Turn on the Quest and connect the QuestLink client to this PC over Wi-Fi.

This fixes the missing Unity OpenVR mirror export and checks for an early game
crash before reporting launch success. The original game openvr_api.dll backup
is preserved as openvr_api.dll.questlink-original. SteamVR is not required for
this Gorilla Tag launch path. Headset gameplay remains unverified.
