QuestLink OpenXR Runtime v0.4 - Windows x64 prototype

INSTALL
1. Extract QuestLinkRuntime-Windows-x64-v0.3.zip.
2. Run INSTALL_QUESTLINK_RUNTIME.bat as prompted.
3. The installer activates QuestLink as the OpenXR runtime and allows TCP 47991 on Private networks.
4. Keep QuestLink PC v7.9.1 open on TCP 47990 for launcher/control commands.

V0.3 TEST BRIDGE
- Captures submitted OpenXR projection-layer eye images from D3D11 swapchains.
- Downscales the two eyes into a side-by-side preview frame.
- Converts the captured side-by-side BGRA frame to NV12 and encodes H.264 with a Windows Media Foundation hardware video encoder.
- Sends H.264 access units over TCP 47991 using QLV2 packets (width, height, frame ID, payload length, Annex-B bitstream).
- The Quest Android client decodes the H.264 stream through MediaCodec onto a SurfaceView.
- Quest orientation data continues to use the independent QLH1 reverse-direction packet.
- Receives Quest orientation data and feeds it into xrLocateViews / VIEW pose.
- Intended as the first end-to-end bridge test.

CURRENT LIMITS
- This is NOT final Virtual Desktop-quality streaming yet.
- H.264 hardware encoder availability depends on the PC GPU and driver. This build currently requires a compatible Media Foundation hardware encoder; it does not yet include a software fallback.
- The current capture path still performs D3D11 staging readback and CPU BGRA-to-NV12 conversion. It is an integration step, not the final zero-copy GPU pipeline.
- The stream is capped at 45 FPS; actual throughput and latency require testing on the target PC and Quest 3.
- The Quest client displays the received stereo preview as a 2D full-screen view, not a final low-latency immersive compositor layer.
- Controller tracking is not wired yet.
- Position tracking is still fixed at standing height; orientation is the live portion of the test.
- If Gorilla Tag ignores OpenXR and chooses OpenVR, an OpenVR compatibility path will still be needed.

RESTORE
Run RESTORE_PREVIOUS_RUNTIME.bat to restore the prior OpenXR runtime.
