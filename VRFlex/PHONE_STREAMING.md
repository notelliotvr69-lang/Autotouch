# VRFlex phone-to-Quest streaming

## Target design

- Phone is the host: it runs supported Android apps/content and encodes their visual output.
- Quest is the client: VRFlex renders received video on a virtual screen in an immersive environment.
- Quest-native peripherals: controller tracking/input, microphone capture, and headset audio output remain on Quest. Input can be sent to the phone only when a supported host/game integration can consume it.
- Transport: local Wi-Fi is the default. USB-C is a future optional transport; it is not implemented yet.

## Current implementation

- Quest companion service listens on TCP port 47821 while the VRFlex Quest activity is open.
- VRFlex Mobile connects to the Quest IP and performs a STATUS handshake.
- This is connection setup only, not video streaming.
- Screen capture, video encoding/decoding, immersive video rendering, controller forwarding, and microphone forwarding are not implemented yet.

## Control protocol

TCP port 47821, newline-delimited UTF-8 messages:

- Phone to Quest: PING; Quest replies VRFLEX_READY.
- Phone to Quest: STATUS; Quest replies READY|<profile-id>|<profile-name>.

Future revisions need version negotiation, pairing/authentication, heartbeat/reconnect, and media capability negotiation.

## Media pipeline still required

1. Phone requests Android MediaProjection consent and captures user-approved display content.
2. Phone encodes frames with Android MediaCodec.
3. Video travels over local Wi-Fi with timestamps and bounded jitter buffering.
4. Quest decodes frames with MediaCodec and presents them as a texture in a native OpenXR scene.
5. Quest OpenXR input actions are mapped to a defined host input protocol only for apps that support those controls.
6. Quest microphone and audio playback remain native on the headset where platform and app routing allow it. Host-audio routing requires separate implementation.

## Constraints

- MediaProjection requires explicit user consent and may show a persistent capture indicator.
- Android does not allow a generic app to inject arbitrary touch/controller input into every other app. Host input needs supported APIs or app/game integrations.
- A phone cannot run arbitrary Quest-only VR titles. This design streams supported phone-side content; it does not make Android execute Quest binaries.
- Native Quest audio avoids an unnecessary phone audio round trip when audio is already available on the headset, but does not eliminate video/network latency.
- USB-C tethering is not enabled in this version.
