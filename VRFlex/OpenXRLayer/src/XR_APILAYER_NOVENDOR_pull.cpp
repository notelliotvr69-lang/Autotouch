// Android/Quest port of XR_APILAYER_NOVENDOR_pull.
// This is a real Khronos OpenXR API-layer entry point. It must be packaged
// inside an APK whose OpenXR loader discovers this layer manifest.
#include <openxr/openxr.h>
#include <openxr/openxr_loader_negotiation.h>

#include <algorithm>
#include <cmath>
#include <cstring>
#include <mutex>

namespace {
constexpr char kLayerName[] = "XR_APILAYER_NOVENDOR_pull";

// Preserve the original prototype's pull feel, but bound speed and total offset
// so a bad frame or repeated locate call cannot create an unlimited displacement.
constexpr float kPullStrength = 14.0f;
constexpr float kPullDecayPer90HzFrame = 0.90f;
constexpr float kMaxSpeed = 1.5f;      // metres/second
constexpr float kMaxOffset = 3.0f;    // metres

PFN_xrGetInstanceProcAddr g_NextGetInstanceProcAddr = nullptr;
PFN_xrLocateSpace g_NextLocateSpace = nullptr;
std::mutex g_StateMutex;
XrTime g_LastUpdateTime = 0;
XrVector3f g_Velocity{0.0f, 0.0f, 0.0f};
XrVector3f g_Offset{0.0f, 0.0f, 0.0f};

float ClampMagnitude(float x, float y, float z, float maxMagnitude) {
    const float length = std::sqrt(x * x + y * y + z * z);
    if (length <= maxMagnitude || length <= 0.000001f) return 1.0f;
    return maxMagnitude / length;
}

void UpdatePull(const XrSpaceLocation& location, XrTime time) {
    if ((location.locationFlags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT) == 0)
        return;

    // xrLocateSpace can be called several times for the same display time.
    // Advance the simulation only once per distinct XrTime.
    if (time <= 0 || time == g_LastUpdateTime) return;

    float dt = 1.0f / 90.0f;
    if (g_LastUpdateTime > 0 && time > g_LastUpdateTime) {
        dt = static_cast<float>(time - g_LastUpdateTime) / 1.0e9f;
    }
    dt = std::clamp(dt, 0.0f, 0.05f);
    g_LastUpdateTime = time;
    if (dt <= 0.0f) return;

    const XrQuaternionf& q = location.pose.orientation;
    const float sinYaw = 2.0f * (q.w * q.y + q.x * q.z);
    const float cosYaw = 1.0f - 2.0f * (q.y * q.y + q.z * q.z);
    const float yaw = std::atan2(sinYaw, cosYaw);

    const float forwardX = std::sin(yaw);
    const float forwardZ = -std::cos(yaw);
    g_Velocity.x += forwardX * kPullStrength * dt;
    g_Velocity.z += forwardZ * kPullStrength * dt;

    const float scale = ClampMagnitude(
        g_Velocity.x, g_Velocity.y, g_Velocity.z, kMaxSpeed);
    g_Velocity.x *= scale;
    g_Velocity.y *= scale;
    g_Velocity.z *= scale;

    g_Offset.x += g_Velocity.x * dt;
    g_Offset.y += g_Velocity.y * dt;
    g_Offset.z += g_Velocity.z * dt;

    const float decay = std::pow(kPullDecayPer90HzFrame, dt * 90.0f);
    g_Velocity.x *= decay;
    g_Velocity.y *= decay;
    g_Velocity.z *= decay;

    const float offsetScale = ClampMagnitude(
        g_Offset.x, g_Offset.y, g_Offset.z, kMaxOffset);
    g_Offset.x *= offsetScale;
    g_Offset.y *= offsetScale;
    g_Offset.z *= offsetScale;
}

XrResult XRAPI_CALL Layer_xrLocateSpace(
    XrSpace space,
    XrSpace baseSpace,
    XrTime time,
    XrSpaceLocation* location) {
    if (g_NextLocateSpace == nullptr)
        return XR_ERROR_FUNCTION_UNSUPPORTED;

    const XrResult result =
        g_NextLocateSpace(space, baseSpace, time, location);

    if (XR_FAILED(result) || location == nullptr ||
        (location->locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT) == 0) {
        return result;
    }

    std::lock_guard<std::mutex> lock(g_StateMutex);
    UpdatePull(*location, time);
    location->pose.position.x += g_Offset.x;
    location->pose.position.y += g_Offset.y;
    location->pose.position.z += g_Offset.z;
    return result;
}

XrResult XRAPI_CALL Layer_xrGetInstanceProcAddr(
    XrInstance instance,
    const char* name,
    PFN_xrVoidFunction* function) {
    if (name == nullptr || function == nullptr)
        return XR_ERROR_VALIDATION_FAILURE;

    if (std::strcmp(name, "xrGetInstanceProcAddr") == 0) {
        *function = reinterpret_cast<PFN_xrVoidFunction>(
            Layer_xrGetInstanceProcAddr);
        return XR_SUCCESS;
    }
    if (std::strcmp(name, "xrLocateSpace") == 0) {
        *function = reinterpret_cast<PFN_xrVoidFunction>(Layer_xrLocateSpace);
        return XR_SUCCESS;
    }
    if (g_NextGetInstanceProcAddr == nullptr)
        return XR_ERROR_INITIALIZATION_FAILED;

    return g_NextGetInstanceProcAddr(instance, name, function);
}

XrResult XRAPI_CALL Layer_xrCreateApiLayerInstance(
    const XrInstanceCreateInfo* info,
    const XrApiLayerCreateInfo* layerInfo,
    XrInstance* instance) {
    if (info == nullptr || layerInfo == nullptr || instance == nullptr ||
        layerInfo->nextInfo == nullptr ||
        layerInfo->nextInfo->nextGetInstanceProcAddr == nullptr ||
        layerInfo->nextInfo->nextCreateApiLayerInstance == nullptr) {
        return XR_ERROR_INITIALIZATION_FAILED;
    }

    // The loader supplies a linked list of remaining layers. Pass a copy with
    // this layer removed so the next layer/runtime receives the correct chain.
    XrApiLayerCreateInfo nextLayerInfo = *layerInfo;
    nextLayerInfo.nextInfo = layerInfo->nextInfo->next;

    const XrResult result = layerInfo->nextInfo->nextCreateApiLayerInstance(
        info, &nextLayerInfo, instance);
    if (XR_FAILED(result)) return result;

    g_NextGetInstanceProcAddr = layerInfo->nextInfo->nextGetInstanceProcAddr;
    PFN_xrVoidFunction nextFunction = nullptr;
    const XrResult lookup = g_NextGetInstanceProcAddr(
        *instance, "xrLocateSpace", &nextFunction);
    if (XR_FAILED(lookup) || nextFunction == nullptr)
        return XR_ERROR_INITIALIZATION_FAILED;

    g_NextLocateSpace = reinterpret_cast<PFN_xrLocateSpace>(nextFunction);
    {
        std::lock_guard<std::mutex> lock(g_StateMutex);
        g_LastUpdateTime = 0;
        g_Velocity = {0.0f, 0.0f, 0.0f};
        g_Offset = {0.0f, 0.0f, 0.0f};
    }
    return XR_SUCCESS;
}
}  // namespace

extern "C" __attribute__((visibility("default"))) XrResult XRAPI_CALL
xrNegotiateLoaderApiLayerInterface(
    const XrNegotiateLoaderInfo* loaderInfo,
    const char* layerName,
    XrNegotiateApiLayerRequest* apiLayerRequest) {
    if (loaderInfo == nullptr || layerName == nullptr ||
        apiLayerRequest == nullptr) {
        return XR_ERROR_INITIALIZATION_FAILED;
    }
    if (loaderInfo->structType != XR_LOADER_INTERFACE_STRUCT_LOADER_INFO ||
        loaderInfo->structVersion != XR_LOADER_INFO_STRUCT_VERSION ||
        loaderInfo->structSize != sizeof(XrNegotiateLoaderInfo) ||
        apiLayerRequest->structType != XR_LOADER_INTERFACE_STRUCT_API_LAYER_REQUEST ||
        apiLayerRequest->structVersion != XR_API_LAYER_INFO_STRUCT_VERSION ||
        apiLayerRequest->structSize != sizeof(XrNegotiateApiLayerRequest) ||
        std::strcmp(layerName, kLayerName) != 0) {
        return XR_ERROR_INITIALIZATION_FAILED;
    }

    if (loaderInfo->minInterfaceVersion > XR_CURRENT_LOADER_API_LAYER_VERSION ||
        loaderInfo->maxInterfaceVersion < XR_CURRENT_LOADER_API_LAYER_VERSION ||
        loaderInfo->minApiVersion > XR_CURRENT_API_VERSION) {
        return XR_ERROR_INITIALIZATION_FAILED;
    }

    apiLayerRequest->layerInterfaceVersion =
        XR_CURRENT_LOADER_API_LAYER_VERSION;
    // Negotiate the newest API version supported by both this layer and loader.
    apiLayerRequest->layerApiVersion =
        std::min<XrVersion>(loaderInfo->maxApiVersion, static_cast<XrVersion>(XR_CURRENT_API_VERSION));
    if (apiLayerRequest->layerApiVersion < loaderInfo->minApiVersion)
        return XR_ERROR_INITIALIZATION_FAILED;
    apiLayerRequest->getInstanceProcAddr = Layer_xrGetInstanceProcAddr;
    apiLayerRequest->createApiLayerInstance =
        Layer_xrCreateApiLayerInstance;
    return XR_SUCCESS;
}
