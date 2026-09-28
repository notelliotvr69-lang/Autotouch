#include "openxr_minimal.h"
#include <algorithm>
#include <cstring>
#include <fstream>
#include <mutex>
#include <string>
#include <cstdlib>

#ifdef _WIN32
#include <windows.h>
#endif

namespace {
struct InstanceState { uint64_t marker = 0x51554553544C494EULL; };
InstanceState g_instance_state;
XrInstance g_instance = nullptr;
std::mutex g_state_mutex;
std::mutex g_log_mutex;

void log_line(const std::string& s) {
    std::lock_guard<std::mutex> lock(g_log_mutex);
    std::string path = "QuestLinkRuntime.log";
#ifdef _WIN32
    if (const char* local = std::getenv("LOCALAPPDATA")) {
        path = std::string(local) + "\\QuestLinkRuntime.log";
    }
#endif
    std::ofstream out(path, std::ios::app);
    if (out) out << s << "\n";
}

template <size_t N>
void copy_text(char (&dst)[N], const char* src) {
    std::memset(dst, 0, N);
    if (!src) return;
#ifdef _WIN32
    strncpy_s(dst, N, src, _TRUNCATE);
#else
    std::strncpy(dst, src, N - 1);
#endif
}

bool valid_instance(XrInstance instance) {
    return instance != nullptr && instance == g_instance;
}
}

extern "C" {

static XrResult XRAPI_CALL ql_xrEnumerateInstanceExtensionProperties(
    const char* layerName,
    uint32_t propertyCapacityInput,
    uint32_t* propertyCountOutput,
    XrExtensionProperties* properties) {
    if (!propertyCountOutput) return XR_ERROR_VALIDATION_FAILURE;
    if (layerName != nullptr) return XR_ERROR_API_LAYER_NOT_PRESENT;
    *propertyCountOutput = 0;
    (void)propertyCapacityInput;
    (void)properties;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrCreateInstance(
    const XrInstanceCreateInfo* createInfo,
    XrInstance* instance) {
    if (!createInfo || !instance) return XR_ERROR_VALIDATION_FAILURE;
    if (createInfo->type != XR_TYPE_INSTANCE_CREATE_INFO) return XR_ERROR_VALIDATION_FAILURE;
    if (createInfo->applicationInfo.apiVersion > XR_CURRENT_API_VERSION)
        return XR_ERROR_API_VERSION_UNSUPPORTED;

    {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        if (g_instance != nullptr) return XR_ERROR_RUNTIME_FAILURE;
        g_instance = reinterpret_cast<XrInstance>(&g_instance_state);
        *instance = g_instance;
    }

    log_line(std::string("xrCreateInstance: ") + createInfo->applicationInfo.applicationName);
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrDestroyInstance(XrInstance instance) {
    if (!valid_instance(instance)) return XR_ERROR_HANDLE_INVALID;
    {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        g_instance = nullptr;
    }
    log_line("xrDestroyInstance");
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrGetInstanceProperties(XrInstance instance, XrInstanceProperties* properties) {
    if (!valid_instance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (!properties) return XR_ERROR_VALIDATION_FAILURE;
    properties->runtimeVersion = XR_MAKE_VERSION(0, 1, 1);
    copy_text(properties->runtimeName, "QuestLink OpenXR Runtime Prototype");
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrGetSystem(XrInstance instance, const XrSystemGetInfo* getInfo, XrSystemId* systemId) {
    if (!valid_instance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (!getInfo || !systemId) return XR_ERROR_VALIDATION_FAILURE;
    if (getInfo->formFactor != XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY)
        return XR_ERROR_FORM_FACTOR_UNSUPPORTED;
    *systemId = 1;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrGetSystemProperties(XrInstance instance, XrSystemId systemId, XrSystemProperties* properties) {
    if (!valid_instance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (systemId != 1 || !properties) return XR_ERROR_VALIDATION_FAILURE;
    properties->systemId = 1;
    properties->vendorId = 0x514C;
    copy_text(properties->systemName, "QuestLink Virtual HMD Prototype");
    properties->graphicsProperties.maxSwapchainImageHeight = 8192;
    properties->graphicsProperties.maxSwapchainImageWidth = 8192;
    properties->graphicsProperties.maxLayerCount = 16;
    properties->trackingProperties.orientationTracking = 1;
    properties->trackingProperties.positionTracking = 1;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrEnumerateViewConfigurations(
    XrInstance instance, XrSystemId systemId, uint32_t capacityInput,
    uint32_t* countOutput, XrViewConfigurationType* views) {
    if (!valid_instance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (systemId != 1 || !countOutput) return XR_ERROR_VALIDATION_FAILURE;
    *countOutput = 1;
    if (capacityInput == 0) return XR_SUCCESS;
    if (!views || capacityInput < 1) return XR_ERROR_SIZE_INSUFFICIENT;
    views[0] = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrGetViewConfigurationProperties(
    XrInstance instance, XrSystemId systemId, XrViewConfigurationType type,
    XrViewConfigurationProperties* properties) {
    if (!valid_instance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (systemId != 1 || !properties) return XR_ERROR_VALIDATION_FAILURE;
    if (type != XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO) return XR_ERROR_VALIDATION_FAILURE;
    properties->viewConfigurationType = type;
    properties->fovMutable = 0;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrEnumerateViewConfigurationViews(
    XrInstance instance, XrSystemId systemId, XrViewConfigurationType type,
    uint32_t capacityInput, uint32_t* countOutput, XrViewConfigurationView* views) {
    if (!valid_instance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (systemId != 1 || !countOutput) return XR_ERROR_VALIDATION_FAILURE;
    if (type != XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO) return XR_ERROR_VALIDATION_FAILURE;
    *countOutput = 2;
    if (capacityInput == 0) return XR_SUCCESS;
    if (!views || capacityInput < 2) return XR_ERROR_SIZE_INSUFFICIENT;
    for (uint32_t i = 0; i < 2; ++i) {
        views[i].recommendedImageRectWidth = 2048;
        views[i].maxImageRectWidth = 4096;
        views[i].recommendedImageRectHeight = 2048;
        views[i].maxImageRectHeight = 4096;
        views[i].recommendedSwapchainSampleCount = 1;
        views[i].maxSwapchainSampleCount = 1;
    }
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrEnumerateEnvironmentBlendModes(
    XrInstance instance, XrSystemId systemId, XrViewConfigurationType type,
    uint32_t capacityInput, uint32_t* countOutput, XrEnvironmentBlendMode* modes) {
    if (!valid_instance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (systemId != 1 || !countOutput) return XR_ERROR_VALIDATION_FAILURE;
    if (type != XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO) return XR_ERROR_VALIDATION_FAILURE;
    *countOutput = 1;
    if (capacityInput == 0) return XR_SUCCESS;
    if (!modes || capacityInput < 1) return XR_ERROR_SIZE_INSUFFICIENT;
    modes[0] = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrCreateSession(XrInstance, const void*, void*) {
    log_line("xrCreateSession requested: compositor not implemented yet");
    return XR_ERROR_GRAPHICS_DEVICE_INVALID;
}

static XrResult XRAPI_CALL ql_xrPollEvent(XrInstance instance, void*) {
    if (!valid_instance(instance)) return XR_ERROR_HANDLE_INVALID;
    return XR_EVENT_UNAVAILABLE;
}

static XrResult XRAPI_CALL ql_xrGetInstanceProcAddr(
    XrInstance instance,
    const char* name,
    PFN_xrVoidFunction* function) {
    if (!name || !function) return XR_ERROR_VALIDATION_FAILURE;
    *function = nullptr;

    if (std::strcmp(name, "xrGetInstanceProcAddr") == 0)
        *function = reinterpret_cast<PFN_xrVoidFunction>(ql_xrGetInstanceProcAddr);
    else if (std::strcmp(name, "xrEnumerateInstanceExtensionProperties") == 0)
        *function = reinterpret_cast<PFN_xrVoidFunction>(ql_xrEnumerateInstanceExtensionProperties);
    else if (std::strcmp(name, "xrCreateInstance") == 0)
        *function = reinterpret_cast<PFN_xrVoidFunction>(ql_xrCreateInstance);
    else {
        if (!valid_instance(instance)) return XR_ERROR_HANDLE_INVALID;
        if (std::strcmp(name, "xrDestroyInstance") == 0)
            *function = reinterpret_cast<PFN_xrVoidFunction>(ql_xrDestroyInstance);
        else if (std::strcmp(name, "xrGetInstanceProperties") == 0)
            *function = reinterpret_cast<PFN_xrVoidFunction>(ql_xrGetInstanceProperties);
        else if (std::strcmp(name, "xrGetSystem") == 0)
            *function = reinterpret_cast<PFN_xrVoidFunction>(ql_xrGetSystem);
        else if (std::strcmp(name, "xrGetSystemProperties") == 0)
            *function = reinterpret_cast<PFN_xrVoidFunction>(ql_xrGetSystemProperties);
        else if (std::strcmp(name, "xrEnumerateViewConfigurations") == 0)
            *function = reinterpret_cast<PFN_xrVoidFunction>(ql_xrEnumerateViewConfigurations);
        else if (std::strcmp(name, "xrGetViewConfigurationProperties") == 0)
            *function = reinterpret_cast<PFN_xrVoidFunction>(ql_xrGetViewConfigurationProperties);
        else if (std::strcmp(name, "xrEnumerateViewConfigurationViews") == 0)
            *function = reinterpret_cast<PFN_xrVoidFunction>(ql_xrEnumerateViewConfigurationViews);
        else if (std::strcmp(name, "xrEnumerateEnvironmentBlendModes") == 0)
            *function = reinterpret_cast<PFN_xrVoidFunction>(ql_xrEnumerateEnvironmentBlendModes);
        else if (std::strcmp(name, "xrCreateSession") == 0)
            *function = reinterpret_cast<PFN_xrVoidFunction>(ql_xrCreateSession);
        else if (std::strcmp(name, "xrPollEvent") == 0)
            *function = reinterpret_cast<PFN_xrVoidFunction>(ql_xrPollEvent);
    }

    return *function ? XR_SUCCESS : XR_ERROR_FUNCTION_UNSUPPORTED;
}

#ifdef _WIN32
__declspec(dllexport)
#endif
XRAPI_ATTR XrResult XRAPI_CALL xrNegotiateLoaderRuntimeInterface(
    const XrNegotiateLoaderInfo* loaderInfo,
    XrNegotiateRuntimeRequest* runtimeRequest) {
    if (!loaderInfo || !runtimeRequest) return XR_ERROR_VALIDATION_FAILURE;
    if (loaderInfo->structType != XR_LOADER_INTERFACE_STRUCT_LOADER_INFO ||
        runtimeRequest->structType != XR_LOADER_INTERFACE_STRUCT_RUNTIME_REQUEST)
        return XR_ERROR_INITIALIZATION_FAILED;
    if (loaderInfo->minInterfaceVersion > 1 || loaderInfo->maxInterfaceVersion < 1)
        return XR_ERROR_INITIALIZATION_FAILED;

    runtimeRequest->runtimeInterfaceVersion = 1;
    runtimeRequest->runtimeApiVersion = XR_CURRENT_API_VERSION;
    runtimeRequest->getInstanceProcAddr = ql_xrGetInstanceProcAddr;
    log_line("xrNegotiateLoaderRuntimeInterface: success");
    return XR_SUCCESS;
}

} // extern "C"
