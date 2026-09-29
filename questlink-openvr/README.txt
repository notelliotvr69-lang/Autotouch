QuestLink OpenVR Driver v0.1

This is the Gorilla Tag / SteamVR path.

INSTALL
1. Extract the ZIP.
2. Run INSTALL_QUESTLINK_OPENVR.bat.
3. Start QuestLink PC and QuestLink Quest v7.10+.
4. Launch Gorilla Tag with QuestLink.

The installer registers this folder as an external SteamVR driver using SteamVR's vrpathreg.exe.

WHAT THIS TEST DOES
- Registers a virtual QuestLink HMD with SteamVR/OpenVR.
- Registers a display-redirect component so QuestLink can receive SteamVR's final composited frame.
- Streams a JPEG proof-of-life frame feed on TCP 47991 using the same QLF1 protocol as QuestLink Quest v7.10.
- Receives QLH1 Quest orientation packets and feeds the headset orientation into SteamVR.
- Gorilla Tag is launched with -vrmode openvr.

CURRENT LIMITS
- Prototype JPEG transport, not final low-latency H.264/H.265.
- Quest v7.10 shows the received frame as a full-screen preview, not final immersive compositor output.
- Controller tracking is not implemented yet.