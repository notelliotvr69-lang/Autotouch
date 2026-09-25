#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <cstdint>
#include <cstddef>
#include <mutex>
#include <string>
#include <iterator>

#if defined(_WIN32)
#define XRAPI_CALL __stdcall
#define XRAPI_PTR XRAPI_CALL
#else
#define XRAPI_CALL
#define XRAPI_PTR
#endif

using XrVersion = uint64_t;
using XrResult = int32_t;
struct XrInstance_T;
using XrInstance = XrInstance_T*;
using PFN_xrVoidFunction = void (XRAPI_PTR *)(void);
constexpr XrResult XR_ERROR_INITIALIZATION_FAILED = -6;

enum XrLoaderInterfaceStructs : int32_t {
    XR_LOADER_INTERFACE_STRUCT_UNINTIALIZED = 0,
    XR_LOADER_INTERFACE_STRUCT_LOADER_INFO = 1,
    XR_LOADER_INTERFACE_STRUCT_API_LAYER_REQUEST = 2,
    XR_LOADER_INTERFACE_STRUCT_RUNTIME_REQUEST = 3,
    XR_LOADER_INTERFACE_STRUCT_API_LAYER_CREATE_INFO = 4,
    XR_LOADER_INTERFACE_STRUCT_API_LAYER_NEXT_INFO = 5,
};

using PFN_xrGetInstanceProcAddr = XrResult (XRAPI_PTR *)(XrInstance, const char*, PFN_xrVoidFunction*);

struct XrNegotiateLoaderInfo {
    XrLoaderInterfaceStructs structType;
    uint32_t structVersion;
    size_t structSize;
    uint32_t minInterfaceVersion;
    uint32_t maxInterfaceVersion;
    XrVersion minApiVersion;
    XrVersion maxApiVersion;
};

struct XrNegotiateRuntimeRequest {
    XrLoaderInterfaceStructs structType;
    uint32_t structVersion;
    size_t structSize;
    uint32_t runtimeInterfaceVersion;
    XrVersion runtimeApiVersion;
    PFN_xrGetInstanceProcAddr getInstanceProcAddr;
};

using PFN_xrNegotiateLoaderRuntimeInterface = XrResult (XRAPI_PTR *)(
    const XrNegotiateLoaderInfo*, XrNegotiateRuntimeRequest*);

#define QUESTLINK_EXPORT extern "C" __declspec(dllexport)

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

    const std::string dll = get_env("QUESTLINK_UPSTREAM_RUNTIME_DLL");
    if (dll.empty()) return false;

    HMODULE selfModule = nullptr;
    if (!GetModuleHandleExA(
            GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
            reinterpret_cast<LPCSTR>(&load_upstream), &selfModule)) {
        return false;
    }

    char selfPath[32768]{};
    char upstreamPath[32768]{};
    char selfFull[32768]{};
    if (!GetModuleFileNameA(selfModule, selfPath, static_cast<DWORD>(std::size(selfPath)))) return false;
    if (!GetFullPathNameA(dll.c_str(), static_cast<DWORD>(std::size(upstreamPath)), upstreamPath, nullptr)) return false;
    if (!GetFullPathNameA(selfPath, static_cast<DWORD>(std::size(selfFull)), selfFull, nullptr)) return false;
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
