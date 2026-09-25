#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <mutex>
#include <string>
#include <openxr/openxr.h>
#include <openxr/openxr_loader_negotiation.h>

#ifndef QUESTLINK_EXPORT
#define QUESTLINK_EXPORT extern "C" __declspec(dllexport)
#endif

namespace {
std::mutex g_mutex;
HMODULE g_upstream = nullptr;
PFN_xrNegotiateLoaderRuntimeInterface g_negotiate = nullptr;

std::string get_env(const char* name) {
    DWORD n = GetEnvironmentVariableA(name, nullptr, 0);
    if (!n) return {};
    std::string s(n, '\0');
    DWORD written = GetEnvironmentVariableA(name, s.data(), n);
    if (!written || written >= n) return {};
    s.resize(written);
    return s;
}

bool load_upstream() {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_negotiate) return true;

    std::string dll = get_env("QUESTLINK_UPSTREAM_RUNTIME_DLL");
    if (dll.empty()) return false;

    HMODULE selfModule = nullptr;
    if (!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                            reinterpret_cast<LPCSTR>(&load_upstream), &selfModule)) return false;

    char selfPath[MAX_PATH]{};
    GetModuleFileNameA(selfModule, selfPath, MAX_PATH);

    char upstreamPath[MAX_PATH]{};
    if (GetFullPathNameA(dll.c_str(), MAX_PATH, upstreamPath, nullptr) == 0) return false;
    char selfFull[MAX_PATH]{};
    if (GetFullPathNameA(selfPath, MAX_PATH, selfFull, nullptr) == 0) return false;
    if (_stricmp(upstreamPath, selfFull) == 0) return false;

    g_upstream = LoadLibraryExA(upstreamPath, nullptr, LOAD_WITH_ALTERED_SEARCH_PATH);
    if (!g_upstream) return false;

    g_negotiate = reinterpret_cast<PFN_xrNegotiateLoaderRuntimeInterface>(
        GetProcAddress(g_upstream, "xrNegotiateLoaderRuntimeInterface"));
    if (!g_negotiate) {
        FreeLibrary(g_upstream);
        g_upstream = nullptr;
        return false;
    }
    return true;
}
}

QUESTLINK_EXPORT XrResult XRAPI_CALL xrNegotiateLoaderRuntimeInterface(
    const XrNegotiateLoaderInfo* loaderInfo,
    XrNegotiateRuntimeRequest* runtimeRequest) {
    if (!loaderInfo || !runtimeRequest) return XR_ERROR_INITIALIZATION_FAILED;
    if (!load_upstream()) return XR_ERROR_INITIALIZATION_FAILED;
    return g_negotiate(loaderInfo, runtimeRequest);
}
