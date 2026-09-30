#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <unknwn.h>
#include <d3d11.h>
#include <dxgi1_2.h>
#include <wincodec.h>

#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>
#include <openxr/openxr_loader_negotiation.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstring>
#include <deque>
#include <fstream>
#include <map>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>
#include "../questlink-common/protocol.h"
#include "h264_encoder.h"

namespace {

struct InstanceState {
    uint64_t marker = 0x51554553544C494EULL;
};

struct SessionState {
    ID3D11Device* device = nullptr;
    ID3D11DeviceContext* context = nullptr;
    bool running = false;
    bool frameBegun = false;
    bool profileChanged = false;
    XrSessionState state = XR_SESSION_STATE_IDLE;
    uint64_t frameIndex = 0;
    XrTime nextFrame = 0;
    std::vector<XrActionSet> attached;
};

struct SpaceState {
    XrReferenceSpaceType type = XR_REFERENCE_SPACE_TYPE_LOCAL;
    XrPosef pose{};
    XrAction action = XR_NULL_HANDLE;
    XrPath subaction = XR_NULL_PATH;
};

struct SwapchainState {
    XrSwapchainCreateInfo info{XR_TYPE_SWAPCHAIN_CREATE_INFO};
    std::vector<ID3D11Texture2D*> images;
    uint32_t nextAcquire = 0;
    int32_t acquired = -1;
    int32_t lastReleased = -1;
    bool waited = false;
    ID3D11Texture2D* staging = nullptr;
    uint32_t stagingWidth=0,stagingHeight=0;
};

struct ActionSetState {};
struct InputValue { bool active=false, boolean=false, changed=false; float scalar=0; XrVector2f vector{}; XrTime time=0; };
struct ActionState {
    XrActionType type = XR_ACTION_TYPE_BOOLEAN_INPUT;
    XrActionSet owner=XR_NULL_HANDLE;
    std::vector<XrPath> subactions;
    std::vector<std::string> bindings;
    InputValue values[3]; // left, right, aggregate
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

struct StreamFrame {
    uint64_t id = 0;
    uint32_t width = 0;
    uint32_t height = 0;
    std::vector<uint8_t> bgra;
    std::array<ql::Eye,2> eyes;
};

std::mutex g_streamMutex;
std::condition_variable g_streamCv;
StreamFrame g_latestFrame;
uint64_t g_latestFrameVersion = 0;

std::mutex g_trackingMutex;
ql::Tracking g_tracking;
std::chrono::steady_clock::time_point g_trackingTime{};
std::atomic<bool> g_clientConnected{false};
ql::Tracking trackingSnapshot() {
    std::lock_guard<std::mutex> lock(g_trackingMutex);
    auto t=g_tracking;
    if(!g_clientConnected || std::chrono::steady_clock::now()-g_trackingTime>std::chrono::milliseconds(250)) {
        t.headFlags=0;t.focused=0;for(auto& h:t.hands){h.gripFlags=0;h.aimFlags=0;h.buttons=0;h.trigger=0;h.squeeze=0;h.stick={};}
    }
    return t;
}
int handForPath(XrPath path) {
    auto it=g_pathToString.find(path);if(it==g_pathToString.end())return -1;
    if(it->second=="/user/hand/left")return 0;if(it->second=="/user/hand/right")return 1;return -1;
}
bool worldSpace(SpaceState* s,const ql::Tracking& t,XrPosef& pose) {
    if(!s)return false;
    pose=s->pose;
    if(s->action!=XR_NULL_HANDLE) {
        auto a=g_actions.find(s->action);if(a==g_actions.end())return false;
        int requested=handForPath(s->subaction);
        for(auto& binding:a->second->bindings) {
            int h=binding.find("/user/hand/left/")==0?0:binding.find("/user/hand/right/")==0?1:-1;
            if(h<0||(requested>=0&&h!=requested))continue;
            bool aim=binding.find("/aim/pose")!=std::string::npos;
            if(binding.find("/pose")==std::string::npos)continue;
            const auto& hand=t.hands[h];
            if(!t.focused||(aim?hand.aimFlags:hand.gripFlags)!=ql::Tracked)return false;
            pose=ql::compose(aim?hand.aim:hand.grip,s->pose);return true;
        }
        return false;
    }
    if(s->type==XR_REFERENCE_SPACE_TYPE_VIEW){pose=ql::compose(t.head,s->pose);return t.headFlags==ql::Tracked;}
    // Both LOCAL and STAGE share the Quest's stage origin in protocol 2.
    return true;
}

std::atomic<bool> g_bridgeRunning{false};
std::atomic<bool> g_bridgeStarted{false};
std::thread g_bridgeThread;
std::atomic<SOCKET> g_bridgeListen{INVALID_SOCKET};
SOCKET g_bridgeClient = INVALID_SOCKET;
std::mutex g_socketMutex;

constexpr uint16_t kStreamPort = 47991;
uint32_t kEyeStreamWidth = 1680;
uint32_t kEyeStreamHeight = 1760;
uint32_t g_videoBitrate = 36000000;

void loadStreamQuality() {
    kEyeStreamWidth=1680;kEyeStreamHeight=1760;g_videoBitrate=36000000;
    char* local=nullptr;size_t length=0;_dupenv_s(&local,&length,"LOCALAPPDATA");
    std::string mode;
    if(local){std::ifstream file(std::string(local)+"\\QuestLink\\stream-quality.txt");file>>mode;free(local);}
    if(mode=="smooth"){kEyeStreamWidth=1280;kEyeStreamHeight=1344;g_videoBitrate=24000000;}
    if(mode=="native"){kEyeStreamWidth=2064;kEyeStreamHeight=2208;g_videoBitrate=55000000;}
}


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
    e.time = static_cast<XrTime>((qpc.QuadPart / freq.QuadPart)*1000000000LL + (qpc.QuadPart % freq.QuadPart)*1000000000LL/freq.QuadPart);
    g_events.push_back(e);
    if (auto* s = getSession(session)) s->state = state;
}

XrTime nowNs() {
    LARGE_INTEGER qpc{}, freq{};
    QueryPerformanceCounter(&qpc);
    QueryPerformanceFrequency(&freq);
    return static_cast<XrTime>((qpc.QuadPart / freq.QuadPart)*1000000000LL + (qpc.QuadPart % freq.QuadPart)*1000000000LL/freq.QuadPart);
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

uint64_t hostToNetwork64(uint64_t value) {
    const uint32_t high = htonl(static_cast<uint32_t>(value >> 32));
    const uint32_t low = htonl(static_cast<uint32_t>(value & 0xffffffffULL));
    return (static_cast<uint64_t>(low) << 32) | high;
}

bool sendAll(SOCKET s, const void* data, size_t bytes) {
    const char* p = static_cast<const char*>(data);
    while (bytes > 0) {
        int chunk = static_cast<int>(std::min<size_t>(bytes, 1u << 20));
        int sent = send(s, p, chunk, 0);
        if (sent <= 0) return false;
        p += sent;
        bytes -= static_cast<size_t>(sent);
    }
    return true;
}

bool recvAll(SOCKET s, void* data, size_t bytes) {
    char* p = static_cast<char*>(data);
    while (bytes > 0) {
        int chunk = static_cast<int>(std::min<size_t>(bytes, 1u << 20));
        int got = recv(s, p, chunk, 0);
        if (got <= 0) return false;
        p += got;
        bytes -= static_cast<size_t>(got);
    }
    return true;
}

bool encodeJpeg(const StreamFrame& frame, std::vector<uint8_t>& jpeg) {
    if (frame.bgra.empty() || frame.width == 0 || frame.height == 0) return false;

    HRESULT initHr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    const bool shouldUninit = SUCCEEDED(initHr);

    IWICImagingFactory* factory = nullptr;
    IStream* stream = nullptr;
    IWICBitmapEncoder* encoder = nullptr;
    IWICBitmapFrameEncode* frameEncode = nullptr;
    IWICBitmap* bitmap = nullptr;

    bool ok = false;

    do {
        HRESULT hr = CoCreateInstance(
            CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER,
            IID_PPV_ARGS(&factory));
        if (FAILED(hr) || !factory) break;

        hr = CreateStreamOnHGlobal(nullptr, TRUE, &stream);
        if (FAILED(hr) || !stream) break;

        hr = factory->CreateEncoder(GUID_ContainerFormatJpeg, nullptr, &encoder);
        if (FAILED(hr) || !encoder) break;

        hr = encoder->Initialize(stream, WICBitmapEncoderNoCache);
        if (FAILED(hr)) break;

        hr = encoder->CreateNewFrame(&frameEncode, nullptr);
        if (FAILED(hr) || !frameEncode) break;

        hr = frameEncode->Initialize(nullptr);
        if (FAILED(hr)) break;

        hr = frameEncode->SetSize(frame.width, frame.height);
        if (FAILED(hr)) break;

        WICPixelFormatGUID pixelFormat = GUID_WICPixelFormat24bppBGR;
        hr = frameEncode->SetPixelFormat(&pixelFormat);
        if (FAILED(hr)) break;

        hr = factory->CreateBitmapFromMemory(
            frame.width,
            frame.height,
            GUID_WICPixelFormat32bppBGRA,
            frame.width * 4,
            static_cast<UINT>(frame.bgra.size()),
            const_cast<BYTE*>(frame.bgra.data()),
            &bitmap);
        if (FAILED(hr) || !bitmap) break;

        hr = frameEncode->WriteSource(bitmap, nullptr);
        if (FAILED(hr)) break;

        hr = frameEncode->Commit();
        if (FAILED(hr)) break;

        hr = encoder->Commit();
        if (FAILED(hr)) break;

        STATSTG stat{};
        hr = stream->Stat(&stat, STATFLAG_NONAME);
        if (FAILED(hr) || stat.cbSize.QuadPart == 0 || stat.cbSize.QuadPart > 16 * 1024 * 1024) break;

        LARGE_INTEGER zero{};
        hr = stream->Seek(zero, STREAM_SEEK_SET, nullptr);
        if (FAILED(hr)) break;

        jpeg.resize(static_cast<size_t>(stat.cbSize.QuadPart));
        ULONG readBytes = 0;
        hr = stream->Read(jpeg.data(), static_cast<ULONG>(jpeg.size()), &readBytes);
        if (FAILED(hr) || readBytes != jpeg.size()) break;

        ok = true;
    } while (false);

    if (bitmap) bitmap->Release();
    if (frameEncode) frameEncode->Release();
    if (encoder) encoder->Release();
    if (stream) stream->Release();
    if (factory) factory->Release();
    if (shouldUninit) CoUninitialize();

    return ok;
}

bool copyProjectionEye(
    SessionState* session,
    const XrCompositionLayerProjectionView& view,
    uint32_t eyeIndex,
    std::vector<uint8_t>& target) {

    auto* swapchain = getSwapchain(view.subImage.swapchain);
    if (!session || !swapchain || swapchain->lastReleased < 0) return false;
    if (swapchain->lastReleased >= static_cast<int32_t>(swapchain->images.size())) return false;

    ID3D11Texture2D* source = swapchain->images[swapchain->lastReleased];
    if (!source) return false;

    D3D11_TEXTURE2D_DESC srcDesc{};
    source->GetDesc(&srcDesc);

    if (srcDesc.SampleDesc.Count != 1) return false;
    if (srcDesc.Format != DXGI_FORMAT_R8G8B8A8_UNORM &&
        srcDesc.Format != DXGI_FORMAT_R8G8B8A8_UNORM_SRGB &&
        srcDesc.Format != DXGI_FORMAT_B8G8R8A8_UNORM) {
        return false;
    }

    const int32_t offsetX = std::max<int32_t>(0, view.subImage.imageRect.offset.x);
    const int32_t offsetY = std::max<int32_t>(0, view.subImage.imageRect.offset.y);
    const uint32_t rectW = static_cast<uint32_t>(std::max<int32_t>(1, view.subImage.imageRect.extent.width));
    const uint32_t rectH = static_cast<uint32_t>(std::max<int32_t>(1, view.subImage.imageRect.extent.height));

    if (offsetX >= static_cast<int32_t>(srcDesc.Width) ||
        offsetY >= static_cast<int32_t>(srcDesc.Height)) return false;

    const uint32_t copyW = std::min<uint32_t>(rectW, srcDesc.Width - static_cast<uint32_t>(offsetX));
    const uint32_t copyH = std::min<uint32_t>(rectH, srcDesc.Height - static_cast<uint32_t>(offsetY));

    D3D11_TEXTURE2D_DESC stageDesc{};
    stageDesc.Width = copyW;
    stageDesc.Height = copyH;
    stageDesc.MipLevels = 1;
    stageDesc.ArraySize = 1;
    stageDesc.Format = srcDesc.Format;
    stageDesc.SampleDesc.Count = 1;
    stageDesc.Usage = D3D11_USAGE_STAGING;
    stageDesc.CPUAccessFlags = D3D11_CPU_ACCESS_READ;

    if(swapchain->staging&&(swapchain->stagingWidth!=copyW||swapchain->stagingHeight!=copyH)){
        swapchain->staging->Release();swapchain->staging=nullptr;
    }
    if(!swapchain->staging){
        if(FAILED(session->device->CreateTexture2D(&stageDesc,nullptr,&swapchain->staging)))return false;
        swapchain->stagingWidth=copyW;swapchain->stagingHeight=copyH;
    }
    ID3D11Texture2D* staging=swapchain->staging;

    D3D11_BOX box{};
    box.left = static_cast<UINT>(offsetX);
    box.top = static_cast<UINT>(offsetY);
    box.front = 0;
    box.right = box.left + copyW;
    box.bottom = box.top + copyH;
    box.back = 1;

    const uint32_t arrayIndex = std::min<uint32_t>(view.subImage.imageArrayIndex, srcDesc.ArraySize - 1);
    const UINT srcSubresource = D3D11CalcSubresource(0, arrayIndex, srcDesc.MipLevels);
    session->context->CopySubresourceRegion(staging, 0, 0, 0, 0, source, srcSubresource, &box);

    D3D11_MAPPED_SUBRESOURCE mapped{};
    HRESULT hr = session->context->Map(staging, 0, D3D11_MAP_READ, 0, &mapped);
    if (FAILED(hr)) {
        return false;
    }

    const size_t required = static_cast<size_t>(kEyeStreamWidth * 2) * kEyeStreamHeight * 4;
    if (target.size() != required) target.resize(required);

    const uint32_t destXBase = eyeIndex * kEyeStreamWidth;

    for (uint32_t y = 0; y < kEyeStreamHeight; ++y) {
        const uint32_t sy = std::min<uint32_t>(copyH - 1, static_cast<uint32_t>((static_cast<uint64_t>(y) * copyH) / kEyeStreamHeight));
        const uint8_t* srcRow = static_cast<const uint8_t*>(mapped.pData) + static_cast<size_t>(sy) * mapped.RowPitch;

        for (uint32_t x = 0; x < kEyeStreamWidth; ++x) {
            const uint32_t sx = std::min<uint32_t>(copyW - 1, static_cast<uint32_t>((static_cast<uint64_t>(x) * copyW) / kEyeStreamWidth));
            const uint8_t* px = srcRow + static_cast<size_t>(sx) * 4;

            const size_t di = (static_cast<size_t>(y) * (kEyeStreamWidth * 2) + destXBase + x) * 4;
            if (srcDesc.Format == DXGI_FORMAT_B8G8R8A8_UNORM) {
                target[di + 0] = px[0];
                target[di + 1] = px[1];
                target[di + 2] = px[2];
            } else {
                target[di + 0] = px[2];
                target[di + 1] = px[1];
                target[di + 2] = px[0];
            }
            target[di + 3] = 255;
        }
    }

    session->context->Unmap(staging, 0);
    return true;
}

void captureProjectionFrame(SessionState* session, const XrFrameEndInfo* frameEndInfo) {
    if (!session || !frameEndInfo) return;

    if (!g_clientConnected || trackingSnapshot().headFlags != ql::Tracked) return;

    const XrCompositionLayerProjection* projection = nullptr;
    for (uint32_t i = 0; i < frameEndInfo->layerCount; ++i) {
        const XrCompositionLayerBaseHeader* layer = frameEndInfo->layers[i];
        if (layer && layer->type == XR_TYPE_COMPOSITION_LAYER_PROJECTION) {
            projection = reinterpret_cast<const XrCompositionLayerProjection*>(layer);
            break;
        }
    }

    if (!projection || projection->viewCount == 0 || !projection->views) return;

    StreamFrame frame;
    frame.id = session->frameIndex;
    frame.width = kEyeStreamWidth * 2;
    frame.height = kEyeStreamHeight;
    frame.bgra.resize(static_cast<size_t>(frame.width) * frame.height * 4);

    bool left = copyProjectionEye(session, projection->views[0], 0, frame.bgra);
    bool right = false;

    if (projection->viewCount > 1) {
        right = copyProjectionEye(session, projection->views[1], 1, frame.bgra);
    } else if (left) {
        for (uint32_t y = 0; y < frame.height; ++y) {
            uint8_t* row = frame.bgra.data() + static_cast<size_t>(y) * frame.width * 4;
            memcpy(row + kEyeStreamWidth * 4, row, kEyeStreamWidth * 4);
        }
        right = true;
    }

    if (!left || !right || projection->viewCount!=2) return;
    XrPosef origin;
    if(!worldSpace(getSpace(projection->space),trackingSnapshot(),origin))return;
    for(int i=0;i<2;++i){frame.eyes[i].pose=ql::compose(origin,projection->views[i].pose);frame.eyes[i].fov=projection->views[i].fov;}

    {
        std::lock_guard<std::mutex> lock(g_streamMutex);
        g_latestFrame = std::move(frame);
        ++g_latestFrameVersion;
    }
    g_streamCv.notify_all();
}

void receiveHeadPoseLoop(SOCKET client, std::atomic<bool>& alive) {
    uint32_t previousFlags=~0u;
    while(g_bridgeRunning && alive) {
        uint8_t payload[ql::TrackingBytes];
        if(!recvAll(client,payload,sizeof(payload)))break;
        try {
            auto t=ql::decodeTracking(payload,sizeof(payload));
            uint32_t flags=t.focused|(t.hands[0].gripFlags<<4)|(t.hands[1].gripFlags<<8);
            if(flags!=previousFlags){logLine("Tracking: focused="+std::to_string(t.focused)+" left="+std::to_string(t.hands[0].gripFlags)+" right="+std::to_string(t.hands[1].gripFlags));previousFlags=flags;}
            std::lock_guard<std::mutex> lock(g_trackingMutex);
            g_tracking=t;g_trackingTime=std::chrono::steady_clock::now();
        } catch(const std::exception& e){logLine(e.what());break;}
    }
    alive=false;g_clientConnected=false;g_streamCv.notify_all();
}

void bridgeServerLoop() {
    WSADATA wsa{};
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        logLine("QuestLink bridge: WSAStartup failed");
        g_bridgeRunning = false;
        return;
    }

    SOCKET listenSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (listenSocket == INVALID_SOCKET) {
        logLine("QuestLink bridge: socket() failed");
        WSACleanup();
        g_bridgeRunning = false;
        return;
    }

    g_bridgeListen = listenSocket;

    BOOL reuse = TRUE;
    setsockopt(listenSocket, SOL_SOCKET, SO_REUSEADDR, reinterpret_cast<const char*>(&reuse), sizeof(reuse));

    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    addr.sin_port = htons(kStreamPort);

    if (bind(listenSocket, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) == SOCKET_ERROR ||
        listen(listenSocket, 1) == SOCKET_ERROR) {
        logLine("QuestLink bridge: failed to bind/listen on port 47991");
        closesocket(listenSocket);
        g_bridgeListen = INVALID_SOCKET;
        WSACleanup();
        g_bridgeRunning = false;
        return;
    }

    logLine("QuestLink bridge: listening on TCP 47991");
    bool allowHardware=true,allowH264=true;

    while (g_bridgeRunning) {
        SOCKET client = accept(listenSocket, nullptr, nullptr);
        if (client == INVALID_SOCKET) {
            if (!g_bridgeRunning) break;
            continue;
        }

        {std::lock_guard<std::mutex> lock(g_socketMutex);g_bridgeClient=client;}
        logLine("QuestLink bridge: Quest stream client connected");

        int noDelay = 1;
        setsockopt(client, IPPROTO_TCP, TCP_NODELAY, reinterpret_cast<const char*>(&noDelay), sizeof(noDelay));

        DWORD timeout=2000;
        setsockopt(client,SOL_SOCKET,SO_RCVTIMEO,reinterpret_cast<char*>(&timeout),sizeof(timeout));
        setsockopt(client,SOL_SOCKET,SO_SNDTIMEO,reinterpret_cast<char*>(&timeout),sizeof(timeout));
        uint32_t hello=0;
        if(!recvAll(client,&hello,4)||ntohl(hello)!=ql::HelloMagic){std::lock_guard<std::mutex> lock(g_socketMutex);closesocket(client);g_bridgeClient=INVALID_SOCKET;continue;}
        {std::lock_guard<std::mutex> lock(g_trackingMutex);g_tracking={};g_trackingTime={};}
        g_clientConnected=true;
        ql::H264Encoder encoder;
        bool h264=allowH264&&encoder.open(kEyeStreamWidth*2,kEyeStreamHeight,72,g_videoBitrate,allowHardware);
        logLine(h264?(encoder.hardware()?"Video encoder: hardware H264":"Video encoder: software H264"):"Video encoder unavailable: JPEG fallback");
        std::atomic<bool> alive{true};
        std::thread receiver(receiveHeadPoseLoop, client, std::ref(alive));
        uint64_t lastVersion;
        {std::lock_guard<std::mutex> lock(g_streamMutex);lastVersion=g_latestFrameVersion;}

        while (g_bridgeRunning && alive.load()) {
            StreamFrame frame;
            {
                std::unique_lock<std::mutex> lock(g_streamMutex);
                g_streamCv.wait_for(lock, std::chrono::milliseconds(250), [&] {
                    return !g_bridgeRunning || !alive.load() || g_latestFrameVersion > lastVersion;
                });

                if (!g_bridgeRunning || !alive.load()) break;
                if (g_latestFrameVersion <= lastVersion) continue;

                frame = g_latestFrame;
                lastVersion = g_latestFrameVersion;
            }

            std::vector<uint8_t> encoded;
            bool encodedOk=h264?encoder.encode(frame.bgra,encoded):encodeJpeg(frame,encoded);
            if(!encodedOk){
                logLine("Video encoding failed; reconnecting with fallback encoder: "+encoder.error());
                if(h264&&encoder.hardware())allowHardware=false;else if(h264)allowH264=false;
                alive=false;break;
            }
            ql::VideoHeader header;
            header.width=frame.width;header.height=frame.height;header.id=frame.id;
            header.codec=h264?2:1;header.bytes=static_cast<uint32_t>(encoded.size());header.eyes=frame.eyes;
            auto packet=ql::encode(header);
            if(!sendAll(client,packet.data(),packet.size())||!sendAll(client,encoded.data(),encoded.size())){alive=false;break;}
        }
        g_clientConnected=false;

        shutdown(client, SD_BOTH);
        {std::lock_guard<std::mutex> lock(g_socketMutex);closesocket(client);g_bridgeClient=INVALID_SOCKET;}

        if (receiver.joinable()) receiver.join();
        logLine("QuestLink bridge: Quest stream client disconnected");
    }

    SOCKET owned=g_bridgeListen.exchange(INVALID_SOCKET);
    if(owned!=INVALID_SOCKET)closesocket(owned);
    WSACleanup();
}

void startBridgeServer() {
    bool expected = false;
    if (!g_bridgeStarted.compare_exchange_strong(expected, true)) return;

    g_bridgeRunning = true;
    g_bridgeThread = std::thread(bridgeServerLoop);
}

void stopBridgeServer() {
    if (!g_bridgeStarted.load()) return;

    g_bridgeRunning = false;
    g_streamCv.notify_all();

    {std::lock_guard<std::mutex> lock(g_socketMutex);if(g_bridgeClient!=INVALID_SOCKET)shutdown(g_bridgeClient,SD_BOTH);}
    SOCKET owned=g_bridgeListen.exchange(INVALID_SOCKET);
    if(owned!=INVALID_SOCKET)closesocket(owned);

    if (g_bridgeThread.joinable()) g_bridgeThread.join();
    g_bridgeStarted = false;
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
    loadStreamQuality();
    logLine("Stream per eye: "+std::to_string(kEyeStreamWidth)+"x"+std::to_string(kEyeStreamHeight));
    logLine(std::string("xrCreateInstance: ") + createInfo->applicationInfo.applicationName);
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrDestroyInstance(XrInstance instance) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_sessions.empty()) return XR_ERROR_CALL_ORDER_INVALID;
    g_instance = XR_NULL_HANDLE;
    g_events.clear();
    stopBridgeServer();
    logLine("xrDestroyInstance");
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrGetInstanceProperties(
    XrInstance instance,
    XrInstanceProperties* properties) {
    if (!validInstance(instance)) return XR_ERROR_HANDLE_INVALID;
    if (!properties) return XR_ERROR_VALIDATION_FAILURE;
    properties->runtimeVersion = XR_MAKE_VERSION(0, 4, 0);
    copyText(properties->runtimeName, "QuestLink OpenXR Runtime v0.5-dev");
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
        views[i].recommendedImageRectWidth = kEyeStreamWidth;
        views[i].maxImageRectWidth = 4096;
        views[i].recommendedImageRectHeight = kEyeStreamHeight;
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

    startBridgeServer();
    logLine("xrCreateSession: D3D11 session created; stream bridge available on TCP 47991");
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
    if (g_events.empty()) {
        for(auto& [session,state]:g_sessions) {
            if(!state->profileChanged)continue;
            state->profileChanged=false;
            XrEventDataInteractionProfileChanged changed{XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED};
            changed.session=session;
            memset(eventData,0,sizeof(*eventData));memcpy(eventData,&changed,sizeof(changed));
            logLine("Controller interaction profile announced: Oculus Touch (both hands)");
            return XR_SUCCESS;
        }
        return XR_EVENT_UNAVAILABLE;
    }

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
    return XR_SPACE_BOUNDS_UNAVAILABLE;
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

    auto t=trackingSnapshot();XrPosef a,base;
    bool valid=worldSpace(s,t,a)&&worldSpace(b,t,base);
    location->locationFlags=valid?(XR_SPACE_LOCATION_ORIENTATION_VALID_BIT|XR_SPACE_LOCATION_POSITION_VALID_BIT|XR_SPACE_LOCATION_ORIENTATION_TRACKED_BIT|XR_SPACE_LOCATION_POSITION_TRACKED_BIT):0;
    location->pose=valid?ql::compose(ql::inverse(base),a):ql::Identity;
    auto* next=reinterpret_cast<XrBaseOutStructure*>(location->next);
    while(next){if(next->type==XR_TYPE_SPACE_VELOCITY)reinterpret_cast<XrSpaceVelocity*>(next)->velocityFlags=0;next=next->next;}
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
    auto t=trackingSnapshot();XrPosef base;
    auto* space=getSpace(locateInfo->space);if(!space)return XR_ERROR_HANDLE_INVALID;
    bool valid=worldSpace(space,t,base)&&t.headFlags==ql::Tracked;
    viewState->viewStateFlags=valid?(XR_VIEW_STATE_ORIENTATION_VALID_BIT|XR_VIEW_STATE_POSITION_VALID_BIT|XR_VIEW_STATE_ORIENTATION_TRACKED_BIT|XR_VIEW_STATE_POSITION_TRACKED_BIT):0;
    if(capacityInput==0)return XR_SUCCESS;
    if(!views||capacityInput<2)return XR_ERROR_SIZE_INSUFFICIENT;
    for(int i=0;i<2;++i){views[i].pose=valid?ql::compose(ql::inverse(base),t.eyes[i].pose):ql::Identity;views[i].fov=t.eyes[i].fov;}
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
        DXGI_FORMAT_B8G8R8A8_UNORM
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
    if(it->second->staging)it->second->staging->Release();
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
    s->lastReleased = s->acquired;
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

    constexpr XrDuration period = 1000000000LL/72;
    XrTime now=nowNs();
    if(s->nextFrame<now-period)s->nextFrame=now;
    if(s->nextFrame>now)std::this_thread::sleep_for(std::chrono::nanoseconds(s->nextFrame-now));
    s->nextFrame+=period;
    frameState->predictedDisplayPeriod=period;
    frameState->predictedDisplayTime=s->nextFrame;
    frameState->shouldRender=trackingSnapshot().headFlags==ql::Tracked?XR_TRUE:XR_FALSE;
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
    captureProjectionFrame(s, frameEndInfo);
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
    state->owner=actionSet;
    if(createInfo->countSubactionPaths)state->subactions.assign(createInfo->subactionPaths,createInfo->subactionPaths+createInfo->countSubactionPaths);
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

static XrResult XRAPI_CALL ql_xrSuggestInteractionProfileBindings(XrInstance instance,const XrInteractionProfileSuggestedBinding* info) {
    if(!validInstance(instance))return XR_ERROR_HANDLE_INVALID;
    if(!info)return XR_ERROR_VALIDATION_FAILURE;
    auto p=g_pathToString.find(info->interactionProfile);
    if(p==g_pathToString.end())return XR_ERROR_PATH_INVALID;
    // OpenComposite suggests every core profile before the attached device is known.
    // Accept core profile suggestions, but select Touch for this Quest-only runtime.
    if(p->second!="/interaction_profiles/oculus/touch_controller") {
        static const char* core[]={"/interaction_profiles/khr/simple_controller","/interaction_profiles/htc/vive_controller","/interaction_profiles/valve/index_controller","/interaction_profiles/microsoft/motion_controller"};
        for(auto* name:core)if(p->second==name)return XR_SUCCESS;
        return XR_ERROR_PATH_UNSUPPORTED;
    }
    for(uint32_t i=0;i<info->countSuggestedBindings;++i){auto b=info->suggestedBindings[i];if(!g_actions.count(b.action)||!g_pathToString.count(b.binding))return XR_ERROR_PATH_INVALID;}
    for(auto& [_,a]:g_actions)a->bindings.clear();
    for(uint32_t i=0;i<info->countSuggestedBindings;++i){auto b=info->suggestedBindings[i];g_actions[b.action]->bindings.push_back(g_pathToString[b.binding]);}
    return XR_SUCCESS;
}
static XrResult XRAPI_CALL ql_xrAttachSessionActionSets(XrSession session,const XrSessionActionSetsAttachInfo* info) {
    auto* s=getSession(session);if(!s)return XR_ERROR_HANDLE_INVALID;if(!info)return XR_ERROR_VALIDATION_FAILURE;
    if(!s->attached.empty())return XR_ERROR_ACTIONSETS_ALREADY_ATTACHED;
    for(uint32_t i=0;i<info->countActionSets;++i)if(!g_actionSets.count(info->actionSets[i]))return XR_ERROR_HANDLE_INVALID;
    s->attached.assign(info->actionSets,info->actionSets+info->countActionSets);
    s->profileChanged=true;return XR_SUCCESS;
}
static InputValue bindingValue(const std::string& binding,const ql::Hand& h,XrActionType type,bool focused) {
    InputValue v;if(!focused)return v;v.active=true;
    auto ends=[&](const char* s){size_t n=strlen(s);return binding.size()>=n&&binding.compare(binding.size()-n,n,s)==0;};
    if(ends("/grip/pose")){v.active=h.gripFlags==ql::Tracked;}
    else if(ends("/aim/pose")){v.active=h.aimFlags==ql::Tracked;}
    else if(ends("/trigger/value")){v.scalar=h.trigger;v.boolean=h.trigger>0.5f;}
    else if(ends("/squeeze/value")){v.scalar=h.squeeze;v.boolean=h.squeeze>0.5f;}
    else if(ends("/thumbstick")){v.vector=h.stick;}
    else if(ends("/thumbstick/x")){v.scalar=h.stick.x;}
    else if(ends("/thumbstick/y")){v.scalar=h.stick.y;}
    else {
        uint32_t mask=0;
        if(ends("/a/click")||ends("/x/click"))mask=ql::Primary;
        else if(ends("/b/click")||ends("/y/click"))mask=ql::Secondary;
        else if(ends("/thumbstick/click"))mask=ql::Stick;
        else if(ends("/menu/click"))mask=ql::Menu;
        else if(ends("/a/touch")||ends("/x/touch"))mask=ql::PrimaryTouch;
        else if(ends("/b/touch")||ends("/y/touch"))mask=ql::SecondaryTouch;
        else if(ends("/thumbstick/touch"))mask=ql::StickTouch;
        else if(ends("/trigger/touch"))mask=ql::TriggerTouch;
        else if(ends("/thumbrest/touch"))mask=ql::ThumbrestTouch;
        else v.active=false;
        v.boolean=(h.buttons&mask)!=0;v.scalar=v.boolean?1.f:0.f;
    }
    if(type==XR_ACTION_TYPE_FLOAT_INPUT)v.boolean=v.scalar>0.5f;
    return v;
}
static InputValue combine(InputValue a,InputValue b) {
    a.active=a.active||b.active;a.boolean=a.boolean||b.boolean;
    if(std::abs(b.scalar)>std::abs(a.scalar))a.scalar=b.scalar;
    if(b.vector.x*b.vector.x+b.vector.y*b.vector.y>a.vector.x*a.vector.x+a.vector.y*a.vector.y)a.vector=b.vector;
    return a;
}
static XrResult XRAPI_CALL ql_xrSyncActions(XrSession session,const XrActionsSyncInfo* info) {
    auto* s=getSession(session);if(!s)return XR_ERROR_HANDLE_INVALID;if(!info)return XR_ERROR_VALIDATION_FAILURE;
    for(uint32_t i=0;i<info->countActiveActionSets;++i){auto a=info->activeActionSets[i];if(std::find(s->attached.begin(),s->attached.end(),a.actionSet)==s->attached.end())return XR_ERROR_ACTIONSET_NOT_ATTACHED;if(a.subactionPath&&handForPath(a.subactionPath)<0)return XR_ERROR_PATH_UNSUPPORTED;}
    auto t=trackingSnapshot();
    for(auto& [_,a]:g_actions){
        InputValue next[3];
        for(int h=0;h<2;++h){
            bool enabled=false;
            for(uint32_t i=0;i<info->countActiveActionSets;++i){auto x=info->activeActionSets[i];if(x.actionSet==a->owner&&(!x.subactionPath||handForPath(x.subactionPath)==h))enabled=true;}
            if(!enabled)continue;
            for(auto& b:a->bindings){int bh=b.find("/user/hand/left/")==0?0:b.find("/user/hand/right/")==0?1:-1;if(bh==h)next[h]=combine(next[h],bindingValue(b,t.hands[h],a->type,t.focused!=0));}
        }
        next[2]=combine(next[0],next[1]);
        for(int h=0;h<3;++h){auto old=a->values[h];bool changed=false;
            if(a->type==XR_ACTION_TYPE_BOOLEAN_INPUT)changed=old.boolean!=next[h].boolean;
            if(a->type==XR_ACTION_TYPE_FLOAT_INPUT)changed=old.scalar!=next[h].scalar;
            if(a->type==XR_ACTION_TYPE_VECTOR2F_INPUT)changed=old.vector.x!=next[h].vector.x||old.vector.y!=next[h].vector.y;
            next[h].changed=next[h].active&&old.active&&changed;
            next[h].time=next[h].active?((changed||!old.active)?nowNs():old.time):0;
            a->values[h]=next[h];
        }
    }
    return t.focused?XR_SUCCESS:XR_SESSION_NOT_FOCUSED;
}
static XrResult actionValue(XrSession session,const XrActionStateGetInfo* info,XrActionType type,InputValue& v) {
    auto* s=getSession(session);if(!s)return XR_ERROR_HANDLE_INVALID;if(!info)return XR_ERROR_VALIDATION_FAILURE;
    auto it=g_actions.find(info->action);if(it==g_actions.end())return XR_ERROR_HANDLE_INVALID;
    auto* a=it->second;if(a->type!=type)return XR_ERROR_ACTION_TYPE_MISMATCH;
    if(std::find(s->attached.begin(),s->attached.end(),a->owner)==s->attached.end())return XR_ERROR_ACTIONSET_NOT_ATTACHED;
    int h=info->subactionPath?handForPath(info->subactionPath):2;
    if(h<0||(info->subactionPath&&std::find(a->subactions.begin(),a->subactions.end(),info->subactionPath)==a->subactions.end()))return XR_ERROR_PATH_UNSUPPORTED;
    v=a->values[h];return XR_SUCCESS;
}
static XrResult XRAPI_CALL ql_xrGetActionStateBoolean(XrSession s,const XrActionStateGetInfo* i,XrActionStateBoolean* o) {
    if(!o)return XR_ERROR_VALIDATION_FAILURE;InputValue v;auto r=actionValue(s,i,XR_ACTION_TYPE_BOOLEAN_INPUT,v);if(XR_FAILED(r))return r;
    o->isActive=v.active;o->currentState=v.boolean;o->changedSinceLastSync=v.changed;o->lastChangeTime=v.time;return XR_SUCCESS;
}
static XrResult XRAPI_CALL ql_xrGetActionStateFloat(XrSession s,const XrActionStateGetInfo* i,XrActionStateFloat* o) {
    if(!o)return XR_ERROR_VALIDATION_FAILURE;InputValue v;auto r=actionValue(s,i,XR_ACTION_TYPE_FLOAT_INPUT,v);if(XR_FAILED(r))return r;
    o->isActive=v.active;o->currentState=v.scalar;o->changedSinceLastSync=v.changed;o->lastChangeTime=v.time;return XR_SUCCESS;
}
static XrResult XRAPI_CALL ql_xrGetActionStateVector2f(XrSession s,const XrActionStateGetInfo* i,XrActionStateVector2f* o) {
    if(!o)return XR_ERROR_VALIDATION_FAILURE;InputValue v;auto r=actionValue(s,i,XR_ACTION_TYPE_VECTOR2F_INPUT,v);if(XR_FAILED(r))return r;
    o->isActive=v.active;o->currentState=v.vector;o->changedSinceLastSync=v.changed;o->lastChangeTime=v.time;return XR_SUCCESS;
}
static XrResult XRAPI_CALL ql_xrGetActionStatePose(XrSession s,const XrActionStateGetInfo* i,XrActionStatePose* o) {
    if(!o)return XR_ERROR_VALIDATION_FAILURE;InputValue v;auto r=actionValue(s,i,XR_ACTION_TYPE_POSE_INPUT,v);if(XR_FAILED(r))return r;o->isActive=v.active;return XR_SUCCESS;
}
static XrResult XRAPI_CALL ql_xrCreateActionSpace(XrSession s,const XrActionSpaceCreateInfo* i,XrSpace* out) {
    if(!getSession(s))return XR_ERROR_HANDLE_INVALID;if(!i||!out)return XR_ERROR_VALIDATION_FAILURE;
    auto a=g_actions.find(i->action);if(a==g_actions.end())return XR_ERROR_HANDLE_INVALID;if(a->second->type!=XR_ACTION_TYPE_POSE_INPUT)return XR_ERROR_ACTION_TYPE_MISMATCH;
    if(i->subactionPath&&std::find(a->second->subactions.begin(),a->second->subactions.end(),i->subactionPath)==a->second->subactions.end())return XR_ERROR_PATH_UNSUPPORTED;
    auto* state=new SpaceState();state->type=XR_REFERENCE_SPACE_TYPE_LOCAL;state->pose=i->poseInActionSpace;state->action=i->action;state->subaction=i->subactionPath;
    auto h=reinterpret_cast<XrSpace>(state);g_spaces[h]=state;*out=h;return XR_SUCCESS;
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
    XrPath p=XR_NULL_PATH;
    ql_xrStringToPath(g_instance,"/interaction_profiles/oculus/touch_controller",&p);
    interactionProfile->interactionProfile = p;
    return XR_SUCCESS;
}

static XrResult XRAPI_CALL ql_xrEnumerateBoundSourcesForAction(
    XrSession session,const XrBoundSourcesForActionEnumerateInfo* info,
    uint32_t capacity,uint32_t* count,XrPath* sources) {
    auto* s=getSession(session);if(!s)return XR_ERROR_HANDLE_INVALID;
    if(!info||!count)return XR_ERROR_VALIDATION_FAILURE;
    auto a=g_actions.find(info->action);if(a==g_actions.end())return XR_ERROR_HANDLE_INVALID;
    if(std::find(s->attached.begin(),s->attached.end(),a->second->owner)==s->attached.end())return XR_ERROR_ACTIONSET_NOT_ATTACHED;
    std::vector<XrPath> paths;
    for(auto& binding:a->second->bindings){XrPath p;ql_xrStringToPath(g_instance,binding.c_str(),&p);if(std::find(paths.begin(),paths.end(),p)==paths.end())paths.push_back(p);}
    *count=static_cast<uint32_t>(paths.size());if(!capacity)return XR_SUCCESS;
    if(capacity<*count)return XR_ERROR_SIZE_INSUFFICIENT;if(*count&&!sources)return XR_ERROR_VALIDATION_FAILURE;
    std::copy(paths.begin(),paths.end(),sources);return XR_SUCCESS;
}
static XrResult XRAPI_CALL ql_xrGetInputSourceLocalizedName(
    XrSession session,const XrInputSourceLocalizedNameGetInfo* info,
    uint32_t capacity,uint32_t* count,char* buffer) {
    if(!getSession(session))return XR_ERROR_HANDLE_INVALID;
    if(!info||!count||!info->whichComponents)return XR_ERROR_VALIDATION_FAILURE;
    auto source=g_pathToString.find(info->sourcePath);if(source==g_pathToString.end())return XR_ERROR_PATH_INVALID;
    std::string name;
    if(info->whichComponents&XR_INPUT_SOURCE_LOCALIZED_NAME_USER_PATH_BIT)name=source->second.find("/left/")!=std::string::npos?"Left hand":"Right hand";
    if(info->whichComponents&XR_INPUT_SOURCE_LOCALIZED_NAME_INTERACTION_PROFILE_BIT)name+=" Touch controller";
    if(info->whichComponents&XR_INPUT_SOURCE_LOCALIZED_NAME_COMPONENT_BIT){auto component=source->second.find("/input/");name+=" "+(component==std::string::npos?source->second:source->second.substr(component+7));}
    *count=static_cast<uint32_t>(name.size()+1);if(!capacity)return XR_SUCCESS;if(capacity<*count)return XR_ERROR_SIZE_INSUFFICIENT;if(!buffer)return XR_ERROR_VALIDATION_FAILURE;
    memcpy(buffer,name.c_str(),*count);return XR_SUCCESS;
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
        QL_PROC(xrEnumerateBoundSourcesForAction);
        QL_PROC(xrGetInputSourceLocalizedName);
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

    logLine("xrNegotiateLoaderRuntimeInterface: QuestLink v0.5-dev");
    return XR_SUCCESS;
}

} // extern "C"
