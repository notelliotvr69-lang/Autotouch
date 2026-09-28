#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <cstdint>
#include <fstream>
#include <mutex>
#include <string>
#include <vector>

namespace {
std::mutex gLock;
HMODULE gOriginal = nullptr;
std::string gDir;

std::string moduleDir() {
    if (!gDir.empty()) return gDir;
    char path[MAX_PATH]{};
    HMODULE self = nullptr;
    GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS |
                       GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                       reinterpret_cast<LPCSTR>(&moduleDir), &self);
    GetModuleFileNameA(self, path, MAX_PATH);
    std::string s(path);
    auto slash = s.find_last_of("\\/");
    gDir = slash == std::string::npos ? "." : s.substr(0, slash);
    return gDir;
}

void logLine(const std::string& msg) {
    std::lock_guard<std::mutex> guard(gLock);
    SYSTEMTIME st{};
    GetLocalTime(&st);
    std::ofstream f(moduleDir() + "\\QuestLinkOpenVR.log", std::ios::app);
    if (!f) return;
    char ts[64]{};
    sprintf_s(ts, "%02u:%02u:%02u.%03u", st.wHour, st.wMinute, st.wSecond, st.wMilliseconds);
    f << "[" << ts << "] " << msg << "\n";
}

HMODULE original() {
    if (gOriginal) return gOriginal;
    auto path = moduleDir() + "\\openvr_api.questlink-original.dll";
    gOriginal = LoadLibraryA(path.c_str());
    if (gOriginal) logLine("Loaded original OpenVR API: " + path);
    else logLine("ERROR: original OpenVR API backup not found: " + path);
    return gOriginal;
}

template<typename T>
T proc(const char* name) {
    auto mod = original();
    return mod ? reinterpret_cast<T>(GetProcAddress(mod, name)) : nullptr;
}

template<typename T>
std::string ptrResult(T p) {
    return p ? "non-null" : "null";
}
}

extern "C" {

__declspec(dllexport) uint32_t VR_InitInternal(int* peError, int appType) {
    using Fn = uint32_t(__cdecl*)(int*, int);
    logLine("VR_InitInternal appType=" + std::to_string(appType));
    auto fn = proc<Fn>("VR_InitInternal");
    if (!fn) { if (peError) *peError = 105; return 0; }
    auto token = fn(peError, appType);
    logLine("VR_InitInternal -> token=" + std::to_string(token) +
            " error=" + std::to_string(peError ? *peError : -1));
    return token;
}

__declspec(dllexport) uint32_t VR_InitInternal2(int* peError, int appType, const char* startupInfo) {
    using Fn = uint32_t(__cdecl*)(int*, int, const char*);
    logLine("VR_InitInternal2 appType=" + std::to_string(appType));
    auto fn = proc<Fn>("VR_InitInternal2");
    if (fn) {
        auto token = fn(peError, appType, startupInfo);
        logLine("VR_InitInternal2 -> token=" + std::to_string(token) +
                " error=" + std::to_string(peError ? *peError : -1));
        return token;
    }
    return VR_InitInternal(peError, appType);
}

__declspec(dllexport) void VR_ShutdownInternal() {
    using Fn = void(__cdecl*)();
    logLine("VR_ShutdownInternal");
    if (auto fn = proc<Fn>("VR_ShutdownInternal")) fn();
}

__declspec(dllexport) void* VR_GetGenericInterface(const char* version, int* peError) {
    using Fn = void*(__cdecl*)(const char*, int*);
    const std::string v = version ? version : "<null>";
    logLine("VR_GetGenericInterface " + v);
    auto fn = proc<Fn>("VR_GetGenericInterface");
    if (!fn) { if (peError) *peError = 105; return nullptr; }
    auto result = fn(version, peError);
    logLine("  -> " + ptrResult(result) + " error=" +
            std::to_string(peError ? *peError : -1));
    return result;
}

__declspec(dllexport) bool VR_IsInterfaceVersionValid(const char* version) {
    using Fn = bool(__cdecl*)(const char*);
    const std::string v = version ? version : "<null>";
    auto fn = proc<Fn>("VR_IsInterfaceVersionValid");
    const bool ok = fn ? fn(version) : false;
    logLine("VR_IsInterfaceVersionValid " + v + " -> " + (ok ? "true" : "false"));
    return ok;
}

__declspec(dllexport) bool VR_IsHmdPresent() {
    using Fn = bool(__cdecl*)();
    auto fn = proc<Fn>("VR_IsHmdPresent");
    const bool present = fn ? fn() : false;
    logLine(std::string("VR_IsHmdPresent -> ") + (present ? "true" : "false"));
    return present;
}

__declspec(dllexport) bool VR_IsRuntimeInstalled() {
    using Fn = bool(__cdecl*)();
    auto fn = proc<Fn>("VR_IsRuntimeInstalled");
    const bool installed = fn ? fn() : false;
    logLine(std::string("VR_IsRuntimeInstalled -> ") + (installed ? "true" : "false"));
    return installed;
}

__declspec(dllexport) bool VR_GetRuntimePath(char* buffer, uint32_t bufferSize, uint32_t* requiredSize) {
    using Fn = bool(__cdecl*)(char*, uint32_t, uint32_t*);
    auto fn = proc<Fn>("VR_GetRuntimePath");
    const bool ok = fn ? fn(buffer, bufferSize, requiredSize) : false;
    logLine(std::string("VR_GetRuntimePath -> ") + (ok ? "true" : "false"));
    return ok;
}

__declspec(dllexport) const char* VR_RuntimePath() {
    using Fn = const char*(__cdecl*)();
    auto fn = proc<Fn>("VR_RuntimePath");
    const char* p = fn ? fn() : nullptr;
    logLine(std::string("VR_RuntimePath -> ") + (p ? p : "<null>"));
    return p;
}

__declspec(dllexport) uint32_t VR_GetInitToken() {
    using Fn = uint32_t(__cdecl*)();
    auto fn = proc<Fn>("VR_GetInitToken");
    return fn ? fn() : 0;
}

__declspec(dllexport) const char* VR_GetVRInitErrorAsSymbol(int error) {
    using Fn = const char*(__cdecl*)(int);
    auto fn = proc<Fn>("VR_GetVRInitErrorAsSymbol");
    return fn ? fn(error) : "VRInitError_Unknown";
}

__declspec(dllexport) const char* VR_GetVRInitErrorAsEnglishDescription(int error) {
    using Fn = const char*(__cdecl*)(int);
    auto fn = proc<Fn>("VR_GetVRInitErrorAsEnglishDescription");
    return fn ? fn(error) : "QuestLink OpenVR probe could not load the original runtime.";
}

__declspec(dllexport) const char* VR_GetStringForHmdError(int error) {
    using Fn = const char*(__cdecl*)(int);
    auto fn = proc<Fn>("VR_GetStringForHmdError");
    return fn ? fn(error) : VR_GetVRInitErrorAsEnglishDescription(error);
}

}

BOOL APIENTRY DllMain(HMODULE, DWORD reason, LPVOID) {
    if (reason == DLL_PROCESS_ATTACH) logLine("QuestLink OpenVR probe loaded");
    if (reason == DLL_PROCESS_DETACH) {
        logLine("QuestLink OpenVR probe unloaded");
        if (gOriginal) FreeLibrary(gOriginal);
        gOriginal = nullptr;
    }
    return TRUE;
}
