// Unity XR uses this optional interface for SteamVR's desktop mirror.
// Its callers check for null; QuestLink does not provide the SteamVR mirror.
// All other exports forward unchanged to the pinned OpenComposite backend.
__declspec(dllexport) void *VRHeadsetView(void) { return 0; }

#ifdef QUESTLINK_NO_CRT
int _DllMainCRTStartup(void *module, unsigned long reason, void *reserved) { return 1; }
#endif
