#pragma once
#include <stdint.h>
#include <stddef.h>

#ifdef _WIN32
#define XRAPI_ATTR
#define XRAPI_CALL __stdcall
#define XRAPI_PTR XRAPI_CALL
#else
#define XRAPI_ATTR
#define XRAPI_CALL
#define XRAPI_PTR
#endif

#define XR_MAKE_VERSION(major, minor, patch) ((((uint64_t)(major) & 0xffffULL) << 48) | (((uint64_t)(minor) & 0xffffULL) << 32) | ((uint64_t)(patch) & 0xffffffffULL))
#define XR_CURRENT_API_VERSION XR_MAKE_VERSION(1, 1, 0)
#define XR_MAX_RUNTIME_NAME_SIZE 128
#define XR_MAX_SYSTEM_NAME_SIZE 256
#define XR_MAX_EXTENSION_NAME_SIZE 128

using XrVersion = uint64_t;
using XrSystemId = uint64_t;
using XrBool32 = uint32_t;
using XrFlags64 = uint64_t;
using XrInstanceCreateFlags = XrFlags64;
using XrSystemGetInfoFlags = XrFlags64;
using XrSystemPropertiesFlags = XrFlags64;
using XrViewConfigurationType = int32_t;
using XrEnvironmentBlendMode = int32_t;
using XrFormFactor = int32_t;
using XrStructureType = int32_t;
using XrResult = int32_t;

struct XrInstance_T;
using XrInstance = XrInstance_T*;
using PFN_xrVoidFunction = void (XRAPI_PTR*)(void);

static constexpr XrResult XR_SUCCESS = 0;
static constexpr XrResult XR_EVENT_UNAVAILABLE = 4;
static constexpr XrResult XR_ERROR_VALIDATION_FAILURE = -1;
static constexpr XrResult XR_ERROR_RUNTIME_FAILURE = -2;
static constexpr XrResult XR_ERROR_API_VERSION_UNSUPPORTED = -4;
static constexpr XrResult XR_ERROR_INITIALIZATION_FAILED = -6;
static constexpr XrResult XR_ERROR_FUNCTION_UNSUPPORTED = -7;
static constexpr XrResult XR_ERROR_SIZE_INSUFFICIENT = -11;
static constexpr XrResult XR_ERROR_HANDLE_INVALID = -12;
static constexpr XrResult XR_ERROR_FORM_FACTOR_UNSUPPORTED = -34;
static constexpr XrResult XR_ERROR_API_LAYER_NOT_PRESENT = -36;
static constexpr XrResult XR_ERROR_GRAPHICS_DEVICE_INVALID = -38;

static constexpr XrStructureType XR_TYPE_INSTANCE_CREATE_INFO = 3;
static constexpr XrFormFactor XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY = 1;
static constexpr XrViewConfigurationType XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO = 2;
static constexpr XrEnvironmentBlendMode XR_ENVIRONMENT_BLEND_MODE_OPAQUE = 1;

struct XrApplicationInfo {
    char applicationName[128];
    uint32_t applicationVersion;
    char engineName[128];
    uint32_t engineVersion;
    XrVersion apiVersion;
};

struct XrInstanceCreateInfo {
    XrStructureType type;
    const void* next;
    XrInstanceCreateFlags createFlags;
    XrApplicationInfo applicationInfo;
    uint32_t enabledApiLayerCount;
    const char* const* enabledApiLayerNames;
    uint32_t enabledExtensionCount;
    const char* const* enabledExtensionNames;
};

struct XrExtensionProperties {
    XrStructureType type;
    void* next;
    char extensionName[XR_MAX_EXTENSION_NAME_SIZE];
    uint32_t extensionVersion;
};

struct XrInstanceProperties {
    XrStructureType type;
    void* next;
    XrVersion runtimeVersion;
    char runtimeName[XR_MAX_RUNTIME_NAME_SIZE];
};

struct XrSystemGetInfo {
    XrStructureType type;
    const void* next;
    XrFormFactor formFactor;
};

struct XrSystemGraphicsProperties {
    uint32_t maxSwapchainImageHeight;
    uint32_t maxSwapchainImageWidth;
    uint32_t maxLayerCount;
};

struct XrSystemTrackingProperties {
    XrBool32 orientationTracking;
    XrBool32 positionTracking;
};

struct XrSystemProperties {
    XrStructureType type;
    void* next;
    XrSystemId systemId;
    uint32_t vendorId;
    char systemName[XR_MAX_SYSTEM_NAME_SIZE];
    XrSystemGraphicsProperties graphicsProperties;
    XrSystemTrackingProperties trackingProperties;
};

struct XrViewConfigurationProperties {
    XrStructureType type;
    void* next;
    XrViewConfigurationType viewConfigurationType;
    XrBool32 fovMutable;
};

struct XrViewConfigurationView {
    XrStructureType type;
    void* next;
    uint32_t recommendedImageRectWidth;
    uint32_t maxImageRectWidth;
    uint32_t recommendedImageRectHeight;
    uint32_t maxImageRectHeight;
    uint32_t recommendedSwapchainSampleCount;
    uint32_t maxSwapchainSampleCount;
};

enum XrLoaderInterfaceStructs : int32_t {
    XR_LOADER_INTERFACE_STRUCT_UNINTIALIZED = 0,
    XR_LOADER_INTERFACE_STRUCT_LOADER_INFO = 1,
    XR_LOADER_INTERFACE_STRUCT_RUNTIME_REQUEST = 3
};

using PFN_xrGetInstanceProcAddr = XrResult (XRAPI_PTR*)(XrInstance instance, const char* name, PFN_xrVoidFunction* function);

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
