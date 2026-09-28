#include <windows.h>
#include <unknwn.h>
#include <d3d11.h>
#include <dxgi1_2.h>

#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>
#include <openxr/openxr_loader_negotiation.h>

#include <algorithm>
#include <atomic>
#include <cstring>
#include <deque>
#include <fstream>
#include <map>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace {

struct InstanceState {
    uint64_t marker = 0x51554553544C494EULL;
};

struct SessionState {
    ID3D11Device* device = nullptr;
    ID3D11DeviceContext* context = nullptr;
    bool running = false;
    bool frameBegun = false;
    XrSessionState state = XR_SESSION_STATE_IDLE;
    uint64_t frameIndex = 0;
};

struct SpaceState {
    XrReferenceSpaceType type = XR_REFERENCE_SPACE_TYPE_LOCAL;
    XrPosef pose{};
};

struct SwapchainState {
    XrSwapchainCreateInfo info{XR_TYPE_SWAPCHAIN_CREATE_INFO};
    std::vector<ID3D11Texture2D*> images;
    uint32_t nextAcquire = 0;
    int32_t acquired = -1;
    bool waited = false;
};

struct ActionSetState {};
struct ActionState {
    XrActionType type = XR_ACTION_TYPE_BOOLEAN_INPUT;
};

InstanceState g_instanceState;
XrInstance g_instance = XR_NULL_HANDLE;
std::mutex g_mutex;
std::mutex g_logMutex;
std::deque<XrEventDataSessionStateChanged> g_events;

std::unordered_map<XrSession, SessionState*> g_sessions;
std::unordered_map<XrSpace, SpaceState*> g_spaces;
std::unordered_map<XrSwapchain, SwapchainState*> g_swapchains;
std::unordered_map<XrActionSet, ActionSetState*> g_actionSets;
std::unordered_map<XrAction, ActionState*> g_actions;

std::map<std::string, XrPath> g_stringToPath;
std::map<XrPath, std::string> g_pathToString;
std::atomic<uint64_t> g_nextPath{1};

void logLine(const std::string& text) {
    std::lock_guard<std::mutex> lock(g_logMutex);
    char* local = nullptr;
    size_t len = 0;
    _dupenv_s(&local, &len, "LOCALAPPDATA");
    std::string path = local ? std::string(local) + "\\QuestLinkRuntime.log" : "QuestLinkRuntime.log";
    if (local) free(local);
    std::ofstream out(path, std::ios::app);
    if (out) out << text << "\n";
}

template <size_t N>
void copyText(char (&dst)[N], const char* src) {
    memset(dst, 0, N);
    if (src) strncpy_s(dst, N, src, _TRUNCATE);
}

bool validInstance(XrInstance instance) {
    return instance != XR_NULL_HANDLE && instance == g_instance;
}

SessionState* getSession(XrSession session) {
    auto it = g_sessions.find(session);
    return it == g_sessions.end() ? nullptr : it->second;
}

SpaceState* getSpace(XrSpace space) {
    auto it = g_spaces.find(space);
    return it == g_spaces.end() ? nullptr : it->second;
}

SwapchainState* getSwapchain(XrSwapchain swapchain) {
    auto it = g_swapchains.find(swapchain);
    return it == g_swapchains.end() ? nullptr : it->second;
}

void pushSessionState(XrSession session, XrSessionState state) {
    XrEventDataSessionStateChanged e{XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED};
    e.session = session;
    e.state = state;
    LARGE_INTEGER qpc{}, freq{};
    QueryPerformanceCounter(&qpc);
    QueryPerformanceFrequency(&freq);
    e.time = static_cast<XrTime>((qpc.QuadPart * 1000000000LL) / freq.QuadPart);
    g_events.push_back(e);
    if (auto* s = getSession(session)) s->state = state;
}

XrTime nowNs() {
    LARGE_INTEGER qpc{}, freq{};
    QueryPerformanceCounter(&qpc);
    QueryPerformanceFrequency(&freq);
    return static_cast<XrTime>((qpc.QuadPart * 1000000000LL) / freq.QuadPart);
}

bool isSupportedExtension(const char* name) {
    return name && strcmp(name, XR_KHR_D3D11_ENABLE_EXTENSION_NAME) == 0;
}

template <typename T>
const T* findInChain(const void* next, XrStructureType type) {
    auto* base = reinterpret_cast<const XrBaseInStructure*>(next);
    while (base) {
        if (base->type == type) return reinterpret_cast<const T*>(base);
        base = base->next;
    }
    return nullptr;
}

DXGI_FORMAT toFormat(int64_t fmt) {
    return static_cast<DXGI_FORMAT>(fmt);
}

} // namespace

extern "C" {

static XrResult XRAPI_CALL ql_xrEnumerateInstanceExtensionProperties(
    const char* layerName,
    uint32_t propertyCapacityInput,
    uint32_t* propertyCountOutput,
    XrExtensionProperties* properties) {
    if (!propertyCountOutput) return XR_ERROR_VALIDATION_FAILURE;
    if (layerName) return XR_ERROR_API_LAYER_NOT_PRESENT;

    *propertyCountOutput = 1;
    if (propertyCapacityInput == 0) return XR_SUCCESS;
    if (!properties || propertyCapacityInput < 1) return XR_ERROR_SIZE_INSUFFICIENT;

    copyText(properties[0].extensionName, XR_KHR_D3D11_ENABLE_EXTENSION_NAME);
    properties[0].extensionVersion = XR_KHR_D3D11_enable_SPEC_VERSION;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrCreateInstance(
    const XrInstanceCreateInfo* createInfo,
    XrInstance* instance) {
    if (!createInfo || !instance) return XR_ERROR_VALIDATION_FAILURE;
    if (createInfo->type != XR_TYPE_INSTANCE_CREATE_INFO) return XR_ERROR_VALIDATION_FAILURE;
    if (createInfo->applicationInfo.apiVersion > XR_CURRENT_API_VERSION)
        return XR_ERROR_API_VERSION_UNSUPPORTED;

    for (uint32_t i = 0; i < createInfo->enabledExtensionCount; ++i) {
        if (!isSupportedExtension(createInfo->enabledExtensionNames[i])) {
            logLine(std::string("Unsupported requested extension: ") + createInfo->enabledExtensionNames[i]);
            return XR_ERROR_EXTENSION_NOT_PRESENT;
        }
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_instance != XR_NULL_HANDLE) return XR_ERROR_LIMIT_REACHED;

    g_instance = reinterpret_cast<XrInstance>(&g_instanceState);
    *instance = g_instance;
    logLine(std::string("xrCreateInstance: ") + createInfo->applicationInfo.applicationName);
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrDestroyInstance(XrInstance instance) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_sessions.empty()) return XR_ERROR_CALL_ORDER_INVALID;
    g_instance = XR_NULL_HANDLE;
    g_events.clear();
    logLine("xrDestroyInstance");
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrGetInstanceProperties(
    XrInstance instance,
    XrInstanceProperties* properties) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (!properties) return XR_ERROR_VALIDATION_FAILURE;
    properties->runtimeVersion = XR_MAKE_VERSION(0, 2, 0);
    copyText(properties->runtimeName, "QuestLink OpenXR Runtime v0.2");
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrGetSystem(
    XrInstance instance,
    const XrSystemGetInfo* getInfo,
    XrSystemId* systemId) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (!getInfo || !systemId) return XR_ERROR_VALIDATION_FAILURE;
    if (getInfo->formFactor != XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY)
        return XR_ERROR_FORM_FACTOR_UNSUPPORTED;
    *systemId = 1;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrGetSystemProperties(
    XrInstance instance,
    XrSystemId systemId,
    XrSystemProperties* properties) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (systemId != 1 || !properties) return XR_ERROR_SYSTEM_INVALID;
    properties->systemId = 1;
    properties->vendorId = 0x514C;
    copyText(properties->systemName, "QuestLink Virtual HMD");
    properties->graphicsProperties.maxSwapchainImageHeight = 8192;
    properties->graphicsProperties.maxSwapchainImageWidth = 8192;
    properties->graphicsProperties.maxLayerCount = 16;
    properties->trackingProperties.orientationTracking = XR_TRUE;
    properties->trackingProperties.positionTracking = XR_TRUE;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrGetD3D11GraphicsRequirementsKHR(
    XrInstance instance,
    XrSystemId systemId,
    XrGraphicsRequirementsD3D11KHR* requirements) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (systemId != 1 || !requirements) return XR_ERROR_SYSTEM_INVALID;

    IDXGIFactory1* factory = nullptr;
    IDXGIAdapter1* adapter = nullptr;
    if (FAILED(CreateDXGIFactory1(__uuidof(IDXGIFactory1), reinterpret_cast<void**>(&factory))))
        return XR_ERROR_RUNTIME_FAILURE;

    HRESULT hr = factory->EnumAdapters1(0, &adapter);
    if (FAILED(hr)) {
        factory->Release();
        return XR_ERROR_RUNTIME_FAILURE;
    }

    DXGI_ADAPTER_DESC1 desc{};
    adapter->GetDesc1(&desc);
    requirements->adapterLuid = desc.AdapterLuid;
    requirements->minFeatureLevel = D3D_FEATURE_LEVEL_11_0;

    adapter->Release();
    factory->Release();
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrEnumerateViewConfigurations(
    XrInstance instance, XrSystemId systemId, uint32_t capacityInput,
    uint32_t* countOutput, XrViewConfigurationType* views) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (systemId != 1 || !countOutput) return XR_ERROR_SYSTEM_INVALID;
    *countOutput = 1;
    if (capacityInput == 0) return XR_SUCCESS;
    if (!views || capacityInput < 1) return XR_ERROR_SIZE_INSUFFICIENT;
    views[0] = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrGetViewConfigurationProperties(
    XrInstance instance, XrSystemId systemId, XrViewConfigurationType type,
    XrViewConfigurationProperties* properties) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (systemId != 1 || !properties) return XR_ERROR_SYSTEM_INVALID;
    if (type != XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO)
        return XR_ERROR_VIEW_CONFIGURATION_TYPE_UNSUPPORTED;
    properties->viewConfigurationType = type;
    properties->fovMutable = XR_FALSE;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrEnumerateViewConfigurationViews(
    XrInstance instance, XrSystemId systemId, XrViewConfigurationType type,
    uint32_t capacityInput, uint32_t* countOutput, XrViewConfigurationView* views) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (systemId != 1 || !countOutput) return XR_ERROR_SYSTEM_INVALID;
    if (type != XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO)
        return XR_ERROR_VIEW_CONFIGURATION_TYPE_UNSUPPORTED;

    *countOutput = 2;
    if (capacityInput == 0) return XR_SUCCESS;
    if (!views || capacityInput < 2) return XR_ERROR_SIZE_INSUFFICIENT;

    for (uint32_t i = 0; i < 2; ++i) {
        views[i].recommendedImageRectWidth = 1832;
        views[i].maxImageRectWidth = 4096;
        views[i].recommendedImageRectHeight = 1920;
        views[i].maxImageRectHeight = 4096;
        views[i].recommendedSwapchainSampleCount = 1;
        views[i].maxSwapchainSampleCount = 1;
    }
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrEnumerateEnvironmentBlendModes(
    XrInstance instance, XrSystemId systemId, XrViewConfigurationType type,
    uint32_t capacityInput, uint32_t* countOutput, XrEnvironmentBlendMode* modes) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (systemId != 1 || !countOutput) return XR_ERROR_SYSTEM_INVALID;
    if (type != XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO)
        return XR_ERROR_VIEW_CONFIGURATION_TYPE_UNSUPPORTED;

    *countOutput = 1;
    if (capacityInput == 0) return XR_SUCCESS;
    if (!modes || capacityInput < 1) return XR_ERROR_SIZE_INSUFFICIENT;
    modes[0] = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrCreateSession(
    XrInstance instance,
    const XrSessionCreateInfo* createInfo,
    XrSession* session) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (!createInfo || !session) return XR_ERROR_VALIDATION_FAILURE;
    if (createInfo->systemId != 1) return XR_ERROR_SYSTEM_INVALID;

    const auto* binding = findInChain<XrGraphicsBindingD3D11KHR>(
        createInfo->next, XR_TYPE_GRAPHICS_BINDING_D3D11_KHR);
    if (!binding || !binding->device)
        return XR_ERROR_GRAPHICS_DEVICE_INVALID;

    auto* state = new SessionState();
    state->device = binding->device;
    state->device->AddRef();
    state->device->GetImmediateContext(&state->context);

    XrSession handle = reinterpret_cast<XrSession>(state);

    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_sessions[handle] = state;
        *session = handle;
        pushSessionState(handle, XR_SESSION_STATE_READY);
    }

    logLine("xrCreateSession: D3D11 session created");
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrDestroySession(XrSession session) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_sessions.find(session);
    if (it == g_sessions.end()) return XR_ERROR_HANDLE_INVALID;

    auto* s = it->second;
    if (s->context) s->context->Release();
    if (s->device) s->device->Release();
    delete s;
    g_sessions.erase(it);
    logLine("xrDestroySession");
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrBeginSession(
    XrSession session,
    const XrSessionBeginInfo* beginInfo) {
    auto* s = getSession(session);
    if (!s) return XR_ERROR_HANDLE_INVALID;
    if (!beginInfo) return XR_ERROR_VALIDATION_FAILURE;
    if (s->running) return XR_ERROR_SESSION_RUNNING;

    s->running = true;
    pushSessionState(session, XR_SESSION_STATE_SYNCHRONIZED);
    pushSessionState(session, XR_SESSION_STATE_VISIBLE);
    pushSessionState(session, XR_SESSION_STATE_FOCUSED);
    logLine("xrBeginSession");
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrEndSession(XrSession session) {
    auto* s = getSession(session);
    if (!s) return XR_ERROR_HANDLE_INVALID;
    if (!s->running) return XR_ERROR_SESSION_NOT_RUNNING;

    s->running = false;
    pushSessionState(session, XR_SESSION_STATE_STOPPING);
    pushSessionState(session, XR_SESSION_STATE_IDLE);
    logLine("xrEndSession");
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrRequestExitSession(XrSession session) {
    auto* s = getSession(session);
    if (!s) return XR_ERROR_HANDLE_INVALID;
    pushSessionState(session, XR_SESSION_STATE_STOPPING);
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrPollEvent(
    XrInstance instance,
    XrEventDataBuffer* eventData) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (!eventData) return XR_ERROR_VALIDATION_FAILURE;

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_events.empty()) return XR_EVENT_UNAVAILABLE;

    auto e = g_events.front();
    g_events.pop_front();
    memset(eventData, 0, sizeof(*eventData));
    memcpy(eventData, &e, sizeof(e));
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrEnumerateReferenceSpaces(
    XrSession session,
    uint32_t capacityInput,
    uint32_t* countOutput,
    XrReferenceSpaceType* spaces) {
    if (!getSession(session)) return XR_ERROR_HANDLE_INVALID;
    if (!countOutput) return XR_ERROR_VALIDATION_FAILURE;

    constexpr XrReferenceSpaceType supported[] = {
        XR_REFERENCE_SPACE_TYPE_VIEW,
        XR_REFERENCE_SPACE_TYPE_LOCAL,
        XR_REFERENCE_SPACE_TYPE_STAGE
    };
    *countOutput = 3;
    if (capacityInput == 0) return XR_SUCCESS;
    if (!spaces || capacityInput < 3) return XR_ERROR_SIZE_INSUFFICIENT;
    memcpy(spaces, supported, sizeof(supported));
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrCreateReferenceSpace(
    XrSession session,
    const XrReferenceSpaceCreateInfo* createInfo,
    XrSpace* space) {
    if (!getSession(session)) return XR_ERROR_HANDLE_INVALID;
    if (!createInfo || !space) return XR_ERROR_VALIDATION_FAILURE;
    if (createInfo->referenceSpaceType != XR_REFERENCE_SPACE_TYPE_VIEW &&
        createInfo->referenceSpaceType != XR_REFERENCE_SPACE_TYPE_LOCAL &&
        createInfo->referenceSpaceType != XR_REFERENCE_SPACE_TYPE_STAGE)
        return XR_ERROR_REFERENCE_SPACE_UNSUPPORTED;

    auto* state = new SpaceState();
    state->type = createInfo->referenceSpaceType;
    state->pose = createInfo->poseInReferenceSpace;
    XrSpace handle = reinterpret_cast<XrSpace>(state);
    g_spaces[handle] = state;
    *space = handle;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrDestroySpace(XrSpace space) {
    auto it = g_spaces.find(space);
    if (it == g_spaces.end()) return XR_ERROR_HANDLE_INVALID;
    delete it->second;
    g_spaces.erase(it);
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrGetReferenceSpaceBoundsRect(
    XrSession session,
    XrReferenceSpaceType referenceSpaceType,
    XrExtent2Df* bounds) {
    if (!getSession(session)) return XR_ERROR_HANDLE_INVALID;
    if (!bounds) return XR_ERROR_VALIDATION_FAILURE;
    if (referenceSpaceType != XR_REFERENCE_SPACE_TYPE_STAGE)
        return XR_SPACE_BOUNDS_UNAVAILABLE;
    bounds->width = 2.0f;
    bounds->height = 2.0f;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrLocateSpace(
    XrSpace space,
    XrSpace baseSpace,
    XrTime,
    XrSpaceLocation* location) {
    auto* s = getSpace(space);
    auto* b = getSpace(baseSpace);
    if (!s || !b) return XR_ERROR_HANDLE_INVALID;
    if (!location) return XR_ERROR_VALIDATION_FAILURE;

    location->locationFlags =
        XR_SPACE_LOCATION_ORIENTATION_VALID_BIT |
        XR_SPACE_LOCATION_ORIENTATION_TRACKED_BIT |
        XR_SPACE_LOCATION_POSITION_VALID_BIT |
        XR_SPACE_LOCATION_POSITION_TRACKED_BIT;

    location->pose = s->pose;
    if (s->type == XR_REFERENCE_SPACE_TYPE_VIEW && b->type != XR_REFERENCE_SPACE_TYPE_VIEW)
        location->pose.position.y += 1.6f;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrLocateViews(
    XrSession session,
    const XrViewLocateInfo* locateInfo,
    XrViewState* viewState,
    uint32_t capacityInput,
    uint32_t* countOutput,
    XrView* views) {
    if (!getSession(session)) return XR_ERROR_HANDLE_INVALID;
    if (!locateInfo || !viewState || !countOutput) return XR_ERROR_VALIDATION_FAILURE;
    if (locateInfo->viewConfigurationType != XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO)
        return XR_ERROR_VIEW_CONFIGURATION_TYPE_UNSUPPORTED;

    *countOutput = 2;
    viewState->viewStateFlags =
        XR_VIEW_STATE_ORIENTATION_VALID_BIT |
        XR_VIEW_STATE_ORIENTATION_TRACKED_BIT |
        XR_VIEW_STATE_POSITION_VALID_BIT |
        XR_VIEW_STATE_POSITION_TRACKED_BIT;

    if (capacityInput == 0) return XR_SUCCESS;
    if (!views || capacityInput < 2) return XR_ERROR_SIZE_INSUFFICIENT;

    for (uint32_t i = 0; i < 2; ++i) {
        views[i].pose.orientation = {0, 0, 0, 1};
        views[i].pose.position = {(i == 0 ? -0.032f : 0.032f), 1.6f, 0.0f};
        views[i].fov.angleLeft = -0.9f;
        views[i].fov.angleRight = 0.9f;
        views[i].fov.angleUp = 0.9f;
        views[i].fov.angleDown = -0.9f;
    }
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrEnumerateSwapchainFormats(
    XrSession session,
    uint32_t capacityInput,
    uint32_t* countOutput,
    int64_t* formats) {
    if (!getSession(session)) return XR_ERROR_HANDLE_INVALID;
    if (!countOutput) return XR_ERROR_VALIDATION_FAILURE;

    constexpr int64_t supported[] = {
        DXGI_FORMAT_R8G8B8A8_UNORM_SRGB,
        DXGI_FORMAT_R8G8B8A8_UNORM,
        DXGI_FORMAT_B8G8R8A8_UNORM,
        DXGI_FORMAT_R16G16B16A16_FLOAT
    };
    *countOutput = static_cast<uint32_t>(std::size(supported));
    if (capacityInput == 0) return XR_SUCCESS;
    if (!formats || capacityInput < std::size(supported)) return XR_ERROR_SIZE_INSUFFICIENT;
    memcpy(formats, supported, sizeof(supported));
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrCreateSwapchain(
    XrSession session,
    const XrSwapchainCreateInfo* createInfo,
    XrSwapchain* swapchain) {
    auto* s = getSession(session);
    if (!s) return XR_ERROR_HANDLE_INVALID;
    if (!createInfo || !swapchain) return XR_ERROR_VALIDATION_FAILURE;
    if (createInfo->width == 0 || createInfo->height == 0 || createInfo->arraySize == 0)
        return XR_ERROR_SWAPCHAIN_RECT_INVALID;

    auto* state = new SwapchainState();
    state->info = *createInfo;

    D3D11_TEXTURE2D_DESC desc{};
    desc.Width = createInfo->width;
    desc.Height = createInfo->height;
    desc.MipLevels = createInfo->mipCount;
    desc.ArraySize = createInfo->arraySize;
    desc.Format = toFormat(createInfo->format);
    desc.SampleDesc.Count = createInfo->sampleCount;
    desc.SampleDesc.Quality = 0;
    desc.Usage = D3D11_USAGE_DEFAULT;
    desc.BindFlags = D3D11_BIND_RENDER_TARGET | D3D11_BIND_SHADER_RESOURCE;

    constexpr uint32_t imageCount = 3;
    for (uint32_t i = 0; i < imageCount; ++i) {
        ID3D11Texture2D* texture = nullptr;
        HRESULT hr = s->device->CreateTexture2D(&desc, nullptr, &texture);
        if (FAILED(hr) || !texture) {
            for (auto* t : state->images) if (t) t->Release();
            delete state;
            return XR_ERROR_RUNTIME_FAILURE;
        }
        state->images.push_back(texture);
    }

    XrSwapchain handle = reinterpret_cast<XrSwapchain>(state);
    g_swapchains[handle] = state;
    *swapchain = handle;
    logLine("xrCreateSwapchain");
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrDestroySwapchain(XrSwapchain swapchain) {
    auto it = g_swapchains.find(swapchain);
    if (it == g_swapchains.end()) return XR_ERROR_HANDLE_INVALID;
    for (auto* texture : it->second->images) if (texture) texture->Release();
    delete it->second;
    g_swapchains.erase(it);
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrEnumerateSwapchainImages(
    XrSwapchain swapchain,
    uint32_t capacityInput,
    uint32_t* countOutput,
    XrSwapchainImageBaseHeader* images) {
    auto* s = getSwapchain(swapchain);
    if (!s) return XR_ERROR_HANDLE_INVALID;
    if (!countOutput) return XR_ERROR_VALIDATION_FAILURE;

    *countOutput = static_cast<uint32_t>(s->images.size());
    if (capacityInput == 0) return XR_SUCCESS;
    if (!images || capacityInput < s->images.size()) return XR_ERROR_SIZE_INSUFFICIENT;

    auto* d3dImages = reinterpret_cast<XrSwapchainImageD3D11KHR*>(images);
    for (uint32_t i = 0; i < s->images.size(); ++i) {
        d3dImages[i].texture = s->images[i];
    }
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrAcquireSwapchainImage(
    XrSwapchain swapchain,
    const XrSwapchainImageAcquireInfo*,
    uint32_t* index) {
    auto* s = getSwapchain(swapchain);
    if (!s) return XR_ERROR_HANDLE_INVALID;
    if (!index) return XR_ERROR_VALIDATION_FAILURE;
    if (s->acquired >= 0) return XR_ERROR_CALL_ORDER_INVALID;

    const uint32_t chosen = s->nextAcquire++ % static_cast<uint32_t>(s->images.size());
    s->acquired = static_cast<int32_t>(chosen);
    s->waited = false;
    *index = chosen;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrWaitSwapchainImage(
    XrSwapchain swapchain,
    const XrSwapchainImageWaitInfo*) {
    auto* s = getSwapchain(swapchain);
    if (!s) return XR_ERROR_HANDLE_INVALID;
    if (s->acquired < 0) return XR_ERROR_CALL_ORDER_INVALID;
    s->waited = true;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrReleaseSwapchainImage(
    XrSwapchain swapchain,
    const XrSwapchainImageReleaseInfo*) {
    auto* s = getSwapchain(swapchain);
    if (!s) return XR_ERROR_HANDLE_INVALID;
    if (s->acquired < 0 || !s->waited) return XR_ERROR_CALL_ORDER_INVALID;
    s->acquired = -1;
    s->waited = false;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrWaitFrame(
    XrSession session,
    const XrFrameWaitInfo*,
    XrFrameState* frameState) {
    auto* s = getSession(session);
    if (!s) return XR_ERROR_HANDLE_INVALID;
    if (!s->running) return XR_ERROR_SESSION_NOT_RUNNING;
    if (!frameState) return XR_ERROR_VALIDATION_FAILURE;

    constexpr XrDuration period = 11111111;
    frameState->predictedDisplayPeriod = period;
    frameState->predictedDisplayTime = nowNs() + period;
    frameState->shouldRender = XR_TRUE;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrBeginFrame(
    XrSession session,
    const XrFrameBeginInfo*) {
    auto* s = getSession(session);
    if (!s) return XR_ERROR_HANDLE_INVALID;
    if (!s->running) return XR_ERROR_SESSION_NOT_RUNNING;
    s->frameBegun = true;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrEndFrame(
    XrSession session,
    const XrFrameEndInfo* frameEndInfo) {
    auto* s = getSession(session);
    if (!s) return XR_ERROR_HANDLE_INVALID;
    if (!s->running) return XR_ERROR_SESSION_NOT_RUNNING;
    if (!s->frameBegun) return XR_ERROR_CALL_ORDER_INVALID;
    if (!frameEndInfo) return XR_ERROR_VALIDATION_FAILURE;

    s->frameBegun = false;
    ++s->frameIndex;
    if ((s->frameIndex % 300) == 1) {
        logLine("xrEndFrame: accepted frame " + std::to_string(s->frameIndex) +
                ", layers=" + std::to_string(frameEndInfo->layerCount));
    }
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrStringToPath(
    XrInstance instance,
    const char* pathString,
    XrPath* path) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (!pathString || !path || pathString[0] != '/') return XR_ERROR_PATH_FORMAT_INVALID;

    auto it = g_stringToPath.find(pathString);
    if (it != g_stringToPath.end()) {
        *path = it->second;
        return XR_SUCCESS;
    }

    XrPath p = g_nextPath.fetch_add(1);
    g_stringToPath[pathString] = p;
    g_pathToString[p] = pathString;
    *path = p;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrPathToString(
    XrInstance instance,
    XrPath path,
    uint32_t capacityInput,
    uint32_t* countOutput,
    char* buffer) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (!countOutput) return XR_ERROR_VALIDATION_FAILURE;
    auto it = g_pathToString.find(path);
    if (it == g_pathToString.end()) return XR_ERROR_PATH_INVALID;

    const uint32_t required = static_cast<uint32_t>(it->second.size() + 1);
    *countOutput = required;
    if (capacityInput == 0) return XR_SUCCESS;
    if (!buffer || capacityInput < required) return XR_ERROR_SIZE_INSUFFICIENT;
    strcpy_s(buffer, capacityInput, it->second.c_str());
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrCreateActionSet(
    XrInstance instance,
    const XrActionSetCreateInfo*,
    XrActionSet* actionSet) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (!actionSet) return XR_ERROR_VALIDATION_FAILURE;
    auto* state = new ActionSetState();
    XrActionSet handle = reinterpret_cast<XrActionSet>(state);
    g_actionSets[handle] = state;
    *actionSet = handle;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrDestroyActionSet(XrActionSet actionSet) {
    auto it = g_actionSets.find(actionSet);
    if (it == g_actionSets.end()) return XR_ERROR_HANDLE_INVALID;
    delete it->second;
    g_actionSets.erase(it);
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrCreateAction(
    XrActionSet actionSet,
    const XrActionCreateInfo* createInfo,
    XrAction* action) {
    if (g_actionSets.find(actionSet) == g_actionSets.end()) return XR_ERROR_HANDLE_INVALID;
    if (!createInfo || !action) return XR_ERROR_VALIDATION_FAILURE;
    auto* state = new ActionState();
    state->type = createInfo->actionType;
    XrAction handle = reinterpret_cast<XrAction>(state);
    g_actions[handle] = state;
    *action = handle;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrDestroyAction(XrAction action) {
    auto it = g_actions.find(action);
    if (it == g_actions.end()) return XR_ERROR_HANDLE_INVALID;
    delete it->second;
    g_actions.erase(it);
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrSuggestInteractionProfileBindings(
    XrInstance instance,
    const XrInteractionProfileSuggestedBinding*) {
    return validInstance(instance) ? XR_SUCCESS : XR_ERROR_HANDLE_INVALID;
}

static XrResult XRAPI_CALL ql_xrAttachSessionActionSets(
    XrSession session,
    const XrSessionActionSetsAttachInfo*) {
    return getSession(session) ? XR_SUCCESS : XR_ERROR_HANDLE_INVALID;
}

static XrResult XRAPI_CALL ql_xrSyncActions(
    XrSession session,
    const XrActionsSyncInfo*) {
    return getSession(session) ? XR_SUCCESS : XR_ERROR_HANDLE_INVALID;
}

static XrResult XRAPI_CALL ql_xrGetActionStateBoolean(
    XrSession session,
    const XrActionStateGetInfo*,
    XrActionStateBoolean* state) {
    if (!getSession(session)) return XR_ERROR_HANDLE_INVALID;
    if (!state) return XR_ERROR_VALIDATION_FAILURE;
    state->currentState = XR_FALSE;
    state->changedSinceLastSync = XR_FALSE;
    state->lastChangeTime = 0;
    state->isActive = XR_TRUE;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrGetActionStateFloat(
    XrSession session,
    const XrActionStateGetInfo*,
    XrActionStateFloat* state) {
    if (!getSession(session)) return XR_ERROR_HANDLE_INVALID;
    if (!state) return XR_ERROR_VALIDATION_FAILURE;
    state->currentState = 0.0f;
    state->changedSinceLastSync = XR_FALSE;
    state->lastChangeTime = 0;
    state->isActive = XR_TRUE;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrGetActionStateVector2f(
    XrSession session,
    const XrActionStateGetInfo*,
    XrActionStateVector2f* state) {
    if (!getSession(session)) return XR_ERROR_HANDLE_INVALID;
    if (!state) return XR_ERROR_VALIDATION_FAILURE;
    state->currentState = {0.0f, 0.0f};
    state->changedSinceLastSync = XR_FALSE;
    state->lastChangeTime = 0;
    state->isActive = XR_TRUE;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrGetActionStatePose(
    XrSession session,
    const XrActionStateGetInfo*,
    XrActionStatePose* state) {
    if (!getSession(session)) return XR_ERROR_HANDLE_INVALID;
    if (!state) return XR_ERROR_VALIDATION_FAILURE;
    state->isActive = XR_TRUE;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrCreateActionSpace(
    XrSession session,
    const XrActionSpaceCreateInfo* createInfo,
    XrSpace* space) {
    if (!getSession(session)) return XR_ERROR_HANDLE_INVALID;
    if (!createInfo || !space) return XR_ERROR_VALIDATION_FAILURE;
    auto* state = new SpaceState();
    state->type = XR_REFERENCE_SPACE_TYPE_LOCAL;
    state->pose = createInfo->poseInActionSpace;
    XrSpace handle = reinterpret_cast<XrSpace>(state);
    g_spaces[handle] = state;
    *space = handle;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrApplyHapticFeedback(
    XrSession session,
    const XrHapticActionInfo*,
    const XrHapticBaseHeader*) {
    return getSession(session) ? XR_SUCCESS : XR_ERROR_HANDLE_INVALID;
}

static XrResult XRAPI_CALL ql_xrStopHapticFeedback(
    XrSession session,
    const XrHapticActionInfo*) {
    return getSession(session) ? XR_SUCCESS : XR_ERROR_HANDLE_INVALID;
}

static XrResult XRAPI_CALL ql_xrGetCurrentInteractionProfile(
    XrSession session,
    XrPath,
    XrInteractionProfileState* interactionProfile) {
    if (!getSession(session)) return XR_ERROR_HANDLE_INVALID;
    if (!interactionProfile) return XR_ERROR_VALIDATION_FAILURE;
    interactionProfile->interactionProfile = XR_NULL_PATH;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrResultToString(
    XrInstance instance,
    XrResult value,
    char buffer[XR_MAX_RESULT_STRING_SIZE]) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    const char* text = "XR_UNKNOWN";
    switch (value) {
        case XR_SUCCESS: text = "XR_SUCCESS"; break;
        case XR_EVENT_UNAVAILABLE: text = "XR_EVENT_UNAVAILABLE"; break;
        case XR_ERROR_RUNTIME_FAILURE: text = "XR_ERROR_RUNTIME_FAILURE"; break;
        case XR_ERROR_FUNCTION_UNSUPPORTED: text = "XR_ERROR_FUNCTION_UNSUPPORTED"; break;
        default: break;
    }
    strcpy_s(buffer, XR_MAX_RESULT_STRING_SIZE, text);
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrStructureTypeToString(
    XrInstance instance,
    XrStructureType,
    char buffer[XR_MAX_STRUCTURE_NAME_SIZE]) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    strcpy_s(buffer, XR_MAX_STRUCTURE_NAME_SIZE, "XR_TYPE_UNKNOWN");
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrGetInstanceProcAddr(
    XrInstance instance,
    const char* name,
    PFN_xrVoidFunction* function) {
    if (!name || !function) return XR_ERROR_VALIDATION_FAILURE;
    *function = nullptr;

#define QL_PROC(proc) else if (strcmp(name, #proc) == 0) *function = reinterpret_cast<PFN_xrVoidFunction>(ql_##proc)

    if (strcmp(name, "xrGetInstanceProcAddr") == 0)
        *function = reinterpret_cast<PFN_xrVoidFunction>(ql_xrGetInstanceProcAddr);
    QL_PROC(xrEnumerateInstanceExtensionProperties);
    QL_PROC(xrCreateInstance);
    else {
        if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
        if (false) {}
        QL_PROC(xrDestroyInstance);
        QL_PROC(xrGetInstanceProperties);
        QL_PROC(xrGetSystem);
        QL_PROC(xrGetSystemProperties);
        QL_PROC(xrGetD3D11GraphicsRequirementsKHR);
        QL_PROC(xrEnumerateViewConfigurations);
        QL_PROC(xrGetViewConfigurationProperties);
        QL_PROC(xrEnumerateViewConfigurationViews);
        QL_PROC(xrEnumerateEnvironmentBlendModes);
        QL_PROC(xrCreateSession);
        QL_PROC(xrDestroySession);
        QL_PROC(xrBeginSession);
        QL_PROC(xrEndSession);
        QL_PROC(xrRequestExitSession);
        QL_PROC(xrPollEvent);
        QL_PROC(xrEnumerateReferenceSpaces);
        QL_PROC(xrCreateReferenceSpace);
        QL_PROC(xrDestroySpace);
        QL_PROC(xrGetReferenceSpaceBoundsRect);
        QL_PROC(xrLocateSpace);
        QL_PROC(xrLocateViews);
        QL_PROC(xrEnumerateSwapchainFormats);
        QL_PROC(xrCreateSwapchain);
        QL_PROC(xrDestroySwapchain);
        QL_PROC(xrEnumerateSwapchainImages);
        QL_PROC(xrAcquireSwapchainImage);
        QL_PROC(xrWaitSwapchainImage);
        QL_PROC(xrReleaseSwapchainImage);
        QL_PROC(xrWaitFrame);
        QL_PROC(xrBeginFrame);
        QL_PROC(xrEndFrame);
        QL_PROC(xrStringToPath);
        QL_PROC(xrPathToString);
        QL_PROC(xrCreateActionSet);
        QL_PROC(xrDestroyActionSet);
        QL_PROC(xrCreateAction);
        QL_PROC(xrDestroyAction);
        QL_PROC(xrSuggestInteractionProfileBindings);
        QL_PROC(xrAttachSessionActionSets);
        QL_PROC(xrSyncActions);
        QL_PROC(xrGetActionStateBoolean);
        QL_PROC(xrGetActionStateFloat);
        QL_PROC(xrGetActionStateVector2f);
        QL_PROC(xrGetActionStatePose);
        QL_PROC(xrCreateActionSpace);
        QL_PROC(xrApplyHapticFeedback);
        QL_PROC(xrStopHapticFeedback);
        QL_PROC(xrGetCurrentInteractionProfile);
        QL_PROC(xrResultToString);
        QL_PROC(xrStructureTypeToString);
    }

#undef QL_PROC
    return *function ? XR_SUCCESS : XR_ERROR_FUNCTION_UNSUPPORTED;
}

__declspec(dllexport)
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

    logLine("xrNegotiateLoaderRuntimeInterface: QuestLink v0.2");
    return XR_SUCCESS;
}

} // extern "C"
