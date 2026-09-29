QuestLink OpenXR Runtime v0.3 - Windows x64 prototype

INSTALL
1. Extract QuestLinkRuntime-Windows-x64-v0.3.zip.
2. Run INSTALL_QUESTLINK_RUNTIME.bat as prompted.
3. The installer activates QuestLink as the OpenXR runtime and allows TCP 47991 on Private networks.
4. Keep QuestLink PC v7.9.1 open on TCP 47990 for launcher/control commands.

V0.3 TEST BRIDGE
- Captures submitted OpenXR projection-layer eye images from D3D11 swapchains.
- Downscales the two eyes into a side-by-side preview frame.
- Sends JPEG preview frames over TCP 47991 to QuestLink Quest v7.10.
- Receives Quest orientation data and feeds it into xrLocateViews / VIEW pose.
- Intended as the first end-to-end bridge test.

CURRENT LIMITS
- This is NOT final Virtual Desktop-quality streaming yet.
- Preview uses JPEG and a low test frame rate instead of hardware H.264/H.265.
- The Quest client displays the received stereo preview as a 2D full-screen view, not a final low-latency immersive compositor layer.
- Controller tracking is not wired yet.
- Position tracking is still fixed at standing height; orientation is the live portion of the test.
- If Gorilla Tag ignores OpenXR and chooses OpenVR, an OpenVR compatibility path will still be needed.

RESTORE
Run RESTORE_PREVIOUS_RUNTIME.bat to restore the prior OpenXR runtime.
