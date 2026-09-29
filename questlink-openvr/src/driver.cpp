#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <d3d11.h>
#include <dxgi1_2.h>
#include <wincodec.h>
#include <objidl.h>

#include "openvr_driver.h"

#include <atomic>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace {

constexpr uint16_t kStreamPort = 47991;
constexpr uint32_t kStreamWidth = 1280;
constexpr uint32_t kStreamHeight = 672;

void Log(const char* message) {
    if (vr::VRDriverLog()) {
        vr::VRDriverLog()->Log(message);
    }
}

bool SendAll(SOCKET socket, const void* data, size_t size) {
    const char* ptr = static_cast<const char*>(data);
    while (size > 0) {
        int chunk = static_cast<int>(size > (1u << 20) ? (1u << 20) : size);
        int sent = send(socket, ptr, chunk, 0);
        if (sent <= 0) return false;
        ptr += sent;
        size -= static_cast<size_t>(sent);
    }
    return true;
}

bool RecvAll(SOCKET socket, void* data, size_t size) {
    char* ptr = static_cast<char*>(data);
    while (size > 0) {
        int chunk = static_cast<int>(size > (1u << 20) ? (1u << 20) : size);
        int got = recv(socket, ptr, chunk, 0);
        if (got <= 0) return false;
        ptr += got;
        size -= static_cast<size_t>(got);
    }
    return true;
}

uint64_t HostToNetwork64(uint64_t value) {
    uint32_t hi = htonl(static_cast<uint32_t>(value >> 32));
    uint32_t lo = htonl(static_cast<uint32_t>(value & 0xffffffffULL));
    return (static_cast<uint64_t>(lo) << 32) | hi;
}

float NetworkFloat(const uint8_t* p) {
    uint32_t bits = 0;
    std::memcpy(&bits, p, sizeof(bits));
    bits = ntohl(bits);
    float value = 0.0f;
    std::memcpy(&value, &bits, sizeof(value));
    return value;
}

struct HeadState {
    double qx = 0.0;
    double qy = 0.0;
    double qz = 0.0;
    double qw = 1.0;
    double px = 0.0;
    double py = 1.6;
    double pz = 0.0;
};

struct FramePacket {
    uint32_t width = 0;
    uint32_t height = 0;
    uint64_t frameId = 0;
    std::vector<uint8_t> jpeg;
};

class QuestBridge {
public:
    void Start() {
        bool expected = false;
        if (!running_.compare_exchange_strong(expected, true)) return;
        worker_ = std::thread(&QuestBridge::ServerLoop, this);
    }

    void Stop() {
        if (!running_.exchange(false)) return;
        cv_.notify_all();

        SOCKET client = client_.exchange(INVALID_SOCKET);
        if (client != INVALID_SOCKET) {
            shutdown(client, SD_BOTH);
            closesocket(client);
        }

        SOCKET listener = listener_.exchange(INVALID_SOCKET);
        if (listener != INVALID_SOCKET) {
            closesocket(listener);
        }

        if (worker_.joinable()) worker_.join();
    }

    ~QuestBridge() {
        Stop();
    }

    void Publish(uint32_t width, uint32_t height, uint64_t frameId, std::vector<uint8_t>&& jpeg) {
        if (jpeg.empty()) return;
        {
            std::lock_guard<std::mutex> lock(frameMutex_);
            latest_.width = width;
            latest_.height = height;
            latest_.frameId = frameId;
            latest_.jpeg = std::move(jpeg);
            ++frameVersion_;
        }
        cv_.notify_all();
    }

    HeadState GetHeadState() const {
        std::lock_guard<std::mutex> lock(headMutex_);
        return head_;
    }

private:
    void ReceivePoseLoop(SOCKET socket, std::atomic<bool>& alive) {
        while (running_.load() && alive.load()) {
            char magic[4]{};
            if (!RecvAll(socket, magic, sizeof(magic))) break;
            if (std::memcmp(magic, "QLH1", 4) != 0) break;

            uint8_t payload[28]{};
            if (!RecvAll(socket, payload, sizeof(payload))) break;

            HeadState state{};
            state.qx = NetworkFloat(payload + 0);
            state.qy = NetworkFloat(payload + 4);
            state.qz = NetworkFloat(payload + 8);
            state.qw = NetworkFloat(payload + 12);
            state.px = NetworkFloat(payload + 16);
            state.py = NetworkFloat(payload + 20);
            state.pz = NetworkFloat(payload + 24);

            double length = std::sqrt(
                state.qx * state.qx +
                state.qy * state.qy +
                state.qz * state.qz +
                state.qw * state.qw);

            if (length > 0.0001) {
                state.qx /= length;
                state.qy /= length;
                state.qz /= length;
                state.qw /= length;
            } else {
                state.qx = state.qy = state.qz = 0.0;
                state.qw = 1.0;
            }

            std::lock_guard<std::mutex> lock(headMutex_);
            head_ = state;
        }

        alive.store(false);
        cv_.notify_all();
    }

    void ServerLoop() {
        WSADATA wsa{};
        if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
            running_.store(false);
            return;
        }

        SOCKET listener = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        if (listener == INVALID_SOCKET) {
            WSACleanup();
            running_.store(false);
            return;
        }
        listener_.store(listener);

        BOOL reuse = TRUE;
        setsockopt(listener, SOL_SOCKET, SO_REUSEADDR, reinterpret_cast<const char*>(&reuse), sizeof(reuse));

        sockaddr_in address{};
        address.sin_family = AF_INET;
        address.sin_addr.s_addr = htonl(INADDR_ANY);
        address.sin_port = htons(kStreamPort);

        if (bind(listener, reinterpret_cast<sockaddr*>(&address), sizeof(address)) == SOCKET_ERROR ||
            listen(listener, 1) == SOCKET_ERROR) {
            closesocket(listener);
            listener_.store(INVALID_SOCKET);
            WSACleanup();
            running_.store(false);
            return;
        }

        Log("QuestLink OpenVR: stream bridge listening on TCP 47991");

        while (running_.load()) {
            SOCKET socketClient = accept(listener, nullptr, nullptr);
            if (socketClient == INVALID_SOCKET) {
                if (!running_.load()) break;
                continue;
            }

            client_.store(socketClient);
            int noDelay = 1;
            setsockopt(socketClient, IPPROTO_TCP, TCP_NODELAY, reinterpret_cast<const char*>(&noDelay), sizeof(noDelay));
            Log("QuestLink OpenVR: Quest client connected");

            std::atomic<bool> alive{true};
            std::thread receiver(&QuestBridge::ReceivePoseLoop, this, socketClient, std::ref(alive));
            uint64_t sentVersion = 0;

            while (running_.load() && alive.load()) {
                FramePacket frame;
                {
                    std::unique_lock<std::mutex> lock(frameMutex_);
                    cv_.wait_for(lock, std::chrono::milliseconds(250), [&] {
                        return !running_.load() || !alive.load() || frameVersion_ > sentVersion;
                    });

                    if (!running_.load() || !alive.load()) break;
                    if (frameVersion_ <= sentVersion) continue;

                    frame = latest_;
                    sentVersion = frameVersion_;
                }

                const char magic[4] = {'Q', 'L', 'F', '1'};
                uint32_t width = htonl(frame.width);
                uint32_t height = htonl(frame.height);
                uint64_t id = HostToNetwork64(frame.frameId);
                uint32_t size = htonl(static_cast<uint32_t>(frame.jpeg.size()));

                if (!SendAll(socketClient, magic, sizeof(magic)) ||
                    !SendAll(socketClient, &width, sizeof(width)) ||
                    !SendAll(socketClient, &height, sizeof(height)) ||
                    !SendAll(socketClient, &id, sizeof(id)) ||
                    !SendAll(socketClient, &size, sizeof(size)) ||
                    !SendAll(socketClient, frame.jpeg.data(), frame.jpeg.size())) {
                    alive.store(false);
                    break;
                }
            }

            shutdown(socketClient, SD_BOTH);
            closesocket(socketClient);
            client_.store(INVALID_SOCKET);

            if (receiver.joinable()) receiver.join();
            Log("QuestLink OpenVR: Quest client disconnected");
        }

        SOCKET remaining = listener_.exchange(INVALID_SOCKET);
        if (remaining != INVALID_SOCKET) closesocket(remaining);
        WSACleanup();
    }

    std::atomic<bool> running_{false};
    std::atomic<SOCKET> listener_{INVALID_SOCKET};
    std::atomic<SOCKET> client_{INVALID_SOCKET};
    std::thread worker_;

    mutable std::mutex headMutex_;
    HeadState head_{};

    std::mutex frameMutex_;
    std::condition_variable cv_;
    FramePacket latest_{};
    uint64_t frameVersion_ = 0;
};

QuestBridge g_bridge;

bool EncodeJpeg(
    const std::vector<uint8_t>& bgra,
    uint32_t width,
    uint32_t height,
    std::vector<uint8_t>& jpeg) {

    if (bgra.empty() || width == 0 || height == 0) return false;

    HRESULT init = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    bool uninit = SUCCEEDED(init);

    IWICImagingFactory* factory = nullptr;
    IStream* stream = nullptr;
    IWICBitmapEncoder* encoder = nullptr;
    IWICBitmapFrameEncode* frame = nullptr;
    IWICBitmap* bitmap = nullptr;
    bool ok = false;

    do {
        if (FAILED(CoCreateInstance(
                CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER,
                IID_PPV_ARGS(&factory)))) break;

        if (FAILED(CreateStreamOnHGlobal(nullptr, TRUE, &stream))) break;
        if (FAILED(factory->CreateEncoder(GUID_ContainerFormatJpeg, nullptr, &encoder))) break;
        if (FAILED(encoder->Initialize(stream, WICBitmapEncoderNoCache))) break;
        if (FAILED(encoder->CreateNewFrame(&frame, nullptr))) break;
        if (FAILED(frame->Initialize(nullptr))) break;
        if (FAILED(frame->SetSize(width, height))) break;

        WICPixelFormatGUID format = GUID_WICPixelFormat24bppBGR;
        if (FAILED(frame->SetPixelFormat(&format))) break;

        if (FAILED(factory->CreateBitmapFromMemory(
                width,
                height,
                GUID_WICPixelFormat32bppBGRA,
                width * 4,
                static_cast<UINT>(bgra.size()),
                const_cast<BYTE*>(bgra.data()),
                &bitmap))) break;

        if (FAILED(frame->WriteSource(bitmap, nullptr))) break;
        if (FAILED(frame->Commit())) break;
        if (FAILED(encoder->Commit())) break;

        STATSTG stat{};
        if (FAILED(stream->Stat(&stat, STATFLAG_NONAME))) break;
        if (stat.cbSize.QuadPart <= 0 || stat.cbSize.QuadPart > 16 * 1024 * 1024) break;

        LARGE_INTEGER zero{};
        if (FAILED(stream->Seek(zero, STREAM_SEEK_SET, nullptr))) break;

        jpeg.resize(static_cast<size_t>(stat.cbSize.QuadPart));
        ULONG read = 0;
        if (FAILED(stream->Read(jpeg.data(), static_cast<ULONG>(jpeg.size()), &read))) break;
        if (read != jpeg.size()) break;
        ok = true;
    } while (false);

    if (bitmap) bitmap->Release();
    if (frame) frame->Release();
    if (encoder) encoder->Release();
    if (stream) stream->Release();
    if (factory) factory->Release();
    if (uninit) CoUninitialize();

    return ok;
}

class QuestDisplayComponent final : public vr::IVRDisplayComponent {
public:
    bool IsDisplayOnDesktop() override { return false; }
    bool IsDisplayRealDisplay() override { return false; }

    void GetRecommendedRenderTargetSize(uint32_t* width, uint32_t* height) override {
        *width = 1832;
        *height = 1920;
    }

    void GetEyeOutputViewport(
        vr::EVREye eye,
        uint32_t* x,
        uint32_t* y,
        uint32_t* width,
        uint32_t* height) override {

        *y = 0;
        *width = 1832;
        *height = 1920;
        *x = eye == vr::Eye_Left ? 0 : 1832;
    }

    void GetProjectionRaw(
        vr::EVREye,
        float* left,
        float* right,
        float* top,
        float* bottom) override {

        *left = -1.0f;
        *right = 1.0f;
        *top = -1.0f;
        *bottom = 1.0f;
    }

    vr::DistortionCoordinates_t ComputeDistortion(vr::EVREye, float u, float v) override {
        vr::DistortionCoordinates_t out{};
        out.rfRed[0] = out.rfGreen[0] = out.rfBlue[0] = u;
        out.rfRed[1] = out.rfGreen[1] = out.rfBlue[1] = v;
        return out;
    }

    void GetWindowBounds(int32_t* x, int32_t* y, uint32_t* width, uint32_t* height) override {
        *x = 0;
        *y = 0;
        *width = 3664;
        *height = 1920;
    }

    bool ComputeInverseDistortion(
        vr::HmdVector2_t*,
        vr::EVREye,
        uint32_t,
        float,
        float) override {
        return false;
    }
};

class QuestHmd final : public vr::ITrackedDeviceServerDriver {
public:
    vr::EVRInitError Activate(uint32_t objectId) override {
        objectId_ = objectId;
        auto container = vr::VRProperties()->TrackedDeviceToPropertyContainer(objectId);

        vr::VRProperties()->SetStringProperty(container, vr::Prop_ModelNumber_String, "QuestLink Virtual HMD");
        vr::VRProperties()->SetStringProperty(container, vr::Prop_ManufacturerName_String, "QuestLink");
        vr::VRProperties()->SetFloatProperty(container, vr::Prop_DisplayFrequency_Float, 90.0f);
        vr::VRProperties()->SetFloatProperty(container, vr::Prop_UserIpdMeters_Float, 0.064f);
        vr::VRProperties()->SetFloatProperty(container, vr::Prop_SecondsFromVsyncToPhotons_Float, 0.011f);
        vr::VRProperties()->SetBoolProperty(container, vr::Prop_IsOnDesktop_Bool, false);
        vr::VRProperties()->SetBoolProperty(container, vr::Prop_DisplayDebugMode_Bool, true);
        return vr::VRInitError_None;
    }

    void Deactivate() override {
        objectId_ = vr::k_unTrackedDeviceIndexInvalid;
    }

    void EnterStandby() override {}

    void* GetComponent(const char* name) override {
        if (name && std::strcmp(name, vr::IVRDisplayComponent_Version) == 0) {
            return &display_;
        }
        return nullptr;
    }

    void DebugRequest(const char*, char* response, uint32_t responseSize) override {
        if (response && responseSize > 0) response[0] = '\0';
    }

    vr::DriverPose_t GetPose() override {
        HeadState head = g_bridge.GetHeadState();

        vr::DriverPose_t pose{};
        pose.qWorldFromDriverRotation.w = 1.0;
        pose.qDriverFromHeadRotation.w = 1.0;

        pose.qRotation.w = head.qw;
        pose.qRotation.x = head.qx;
        pose.qRotation.y = head.qy;
        pose.qRotation.z = head.qz;

        pose.vecPosition[0] = head.px;
        pose.vecPosition[1] = head.py;
        pose.vecPosition[2] = head.pz;

        pose.poseIsValid = true;
        pose.deviceIsConnected = true;
        pose.result = vr::TrackingResult_Running_OK;
        pose.shouldApplyHeadModel = true;
        return pose;
    }

    uint32_t ObjectId() const { return objectId_; }

private:
    QuestDisplayComponent display_;
    std::atomic<uint32_t> objectId_{vr::k_unTrackedDeviceIndexInvalid};
};

class QuestDisplayRedirect final :
    public vr::ITrackedDeviceServerDriver,
    public vr::IVRVirtualDisplay {

public:
    ~QuestDisplayRedirect() override {
        ReleaseD3D();
    }

    vr::EVRInitError Activate(uint32_t objectId) override {
        objectId_ = objectId;
        auto container = vr::VRProperties()->TrackedDeviceToPropertyContainer(objectId);
        vr::VRProperties()->SetStringProperty(container, vr::Prop_ModelNumber_String, "QuestLink Stream Redirect");
        vr::VRProperties()->SetStringProperty(container, vr::Prop_ManufacturerName_String, "QuestLink");
        vr::VRProperties()->SetFloatProperty(container, vr::Prop_SecondsFromVsyncToPhotons_Float, 0.011f);
        return vr::VRInitError_None;
    }

    void Deactivate() override {
        objectId_ = vr::k_unTrackedDeviceIndexInvalid;
    }

    void EnterStandby() override {}

    void* GetComponent(const char* name) override {
        if (name && std::strcmp(name, vr::IVRVirtualDisplay_Version) == 0) {
            return static_cast<vr::IVRVirtualDisplay*>(this);
        }
        return nullptr;
    }

    void DebugRequest(const char*, char* response, uint32_t responseSize) override {
        if (response && responseSize > 0) response[0] = '\0';
    }

    vr::DriverPose_t GetPose() override {
        vr::DriverPose_t pose{};
        pose.qWorldFromDriverRotation.w = 1.0;
        pose.qDriverFromHeadRotation.w = 1.0;
        pose.qRotation.w = 1.0;
        pose.poseIsValid = true;
        pose.deviceIsConnected = true;
        pose.result = vr::TrackingResult_Running_OK;
        return pose;
    }

    void Present(const vr::PresentInfo_t* info, uint32_t infoSize) override {
        if (!info || infoSize < sizeof(vr::PresentInfo_t)) return;
        ++presentCount_;

        // Proof-of-life build: capture about 15fps from a 90Hz compositor.
        if ((presentCount_ % 6) != 0) return;

        ID3D11Texture2D* texture = OpenTexture(info->backbufferTextureHandle);
        if (!texture || !context_) {
            if (texture) texture->Release();
            return;
        }

        D3D11_TEXTURE2D_DESC desc{};
        texture->GetDesc(&desc);

        if (desc.SampleDesc.Count != 1 ||
            (desc.Format != DXGI_FORMAT_R8G8B8A8_UNORM &&
             desc.Format != DXGI_FORMAT_R8G8B8A8_UNORM_SRGB &&
             desc.Format != DXGI_FORMAT_B8G8R8A8_UNORM &&
             desc.Format != DXGI_FORMAT_B8G8R8A8_UNORM_SRGB)) {
            texture->Release();
            return;
        }

        D3D11_TEXTURE2D_DESC stagingDesc{};
        stagingDesc.Width = desc.Width;
        stagingDesc.Height = desc.Height;
        stagingDesc.MipLevels = 1;
        stagingDesc.ArraySize = 1;
        stagingDesc.Format = desc.Format;
        stagingDesc.SampleDesc.Count = 1;
        stagingDesc.Usage = D3D11_USAGE_STAGING;
        stagingDesc.CPUAccessFlags = D3D11_CPU_ACCESS_READ;

        ID3D11Texture2D* staging = nullptr;
        if (FAILED(device_->CreateTexture2D(&stagingDesc, nullptr, &staging)) || !staging) {
            texture->Release();
            return;
        }

        context_->CopyResource(staging, texture);
        context_->Flush();

        D3D11_MAPPED_SUBRESOURCE mapped{};
        if (FAILED(context_->Map(staging, 0, D3D11_MAP_READ, 0, &mapped))) {
            staging->Release();
            texture->Release();
            return;
        }

        std::vector<uint8_t> bgra(static_cast<size_t>(kStreamWidth) * kStreamHeight * 4);

        for (uint32_t y = 0; y < kStreamHeight; ++y) {
            uint32_t sy = static_cast<uint32_t>((static_cast<uint64_t>(y) * desc.Height) / kStreamHeight);
            if (sy >= desc.Height) sy = desc.Height - 1;
            const uint8_t* row = static_cast<const uint8_t*>(mapped.pData) +
                                 static_cast<size_t>(sy) * mapped.RowPitch;

            for (uint32_t x = 0; x < kStreamWidth; ++x) {
                uint32_t sx = static_cast<uint32_t>((static_cast<uint64_t>(x) * desc.Width) / kStreamWidth);
                if (sx >= desc.Width) sx = desc.Width - 1;

                const uint8_t* src = row + static_cast<size_t>(sx) * 4;
                size_t dst = (static_cast<size_t>(y) * kStreamWidth + x) * 4;

                if (desc.Format == DXGI_FORMAT_B8G8R8A8_UNORM ||
                    desc.Format == DXGI_FORMAT_B8G8R8A8_UNORM_SRGB) {
                    bgra[dst + 0] = src[0];
                    bgra[dst + 1] = src[1];
                    bgra[dst + 2] = src[2];
                } else {
                    bgra[dst + 0] = src[2];
                    bgra[dst + 1] = src[1];
                    bgra[dst + 2] = src[0];
                }
                bgra[dst + 3] = 255;
            }
        }

        context_->Unmap(staging, 0);
        staging->Release();
        texture->Release();

        std::vector<uint8_t> jpeg;
        if (EncodeJpeg(bgra, kStreamWidth, kStreamHeight, jpeg)) {
            g_bridge.Publish(kStreamWidth, kStreamHeight, info->nFrameId, std::move(jpeg));
        }

        lastVsync_ = std::chrono::steady_clock::now();
        frameCounter_ = info->nFrameId;
    }

    void WaitForPresent() override {
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }

    bool GetTimeSinceLastVsync(float* seconds, uint64_t* frameCounter) override {
        if (seconds) {
            auto elapsed = std::chrono::duration<double>(
                std::chrono::steady_clock::now() - lastVsync_).count();
            *seconds = static_cast<float>(elapsed);
        }
        if (frameCounter) *frameCounter = frameCounter_;
        return true;
    }

private:
    void ReleaseD3D() {
        if (context_) {
            context_->Release();
            context_ = nullptr;
        }
        if (device_) {
            device_->Release();
            device_ = nullptr;
        }
    }

    ID3D11Texture2D* TryOpen(vr::SharedTextureHandle_t handle) {
        if (!device_) return nullptr;
        ID3D11Texture2D* texture = nullptr;
        HRESULT hr = device_->OpenSharedResource(
            reinterpret_cast<HANDLE>(static_cast<uintptr_t>(handle)),
            __uuidof(ID3D11Texture2D),
            reinterpret_cast<void**>(&texture));
        return SUCCEEDED(hr) ? texture : nullptr;
    }

    ID3D11Texture2D* OpenTexture(vr::SharedTextureHandle_t handle) {
        if (ID3D11Texture2D* existing = TryOpen(handle)) return existing;

        ReleaseD3D();

        IDXGIFactory1* factory = nullptr;
        if (FAILED(CreateDXGIFactory1(IID_PPV_ARGS(&factory))) || !factory) return nullptr;

        ID3D11Texture2D* found = nullptr;

        for (UINT i = 0; ; ++i) {
            IDXGIAdapter1* adapter = nullptr;
            if (factory->EnumAdapters1(i, &adapter) == DXGI_ERROR_NOT_FOUND) break;
            if (!adapter) continue;

            ID3D11Device* candidateDevice = nullptr;
            ID3D11DeviceContext* candidateContext = nullptr;
            D3D_FEATURE_LEVEL level{};

            HRESULT hr = D3D11CreateDevice(
                adapter,
                D3D_DRIVER_TYPE_UNKNOWN,
                nullptr,
                D3D11_CREATE_DEVICE_BGRA_SUPPORT,
                nullptr,
                0,
                D3D11_SDK_VERSION,
                &candidateDevice,
                &level,
                &candidateContext);

            adapter->Release();

            if (FAILED(hr) || !candidateDevice || !candidateContext) {
                if (candidateContext) candidateContext->Release();
                if (candidateDevice) candidateDevice->Release();
                continue;
            }

            ID3D11Texture2D* texture = nullptr;
            hr = candidateDevice->OpenSharedResource(
                reinterpret_cast<HANDLE>(static_cast<uintptr_t>(handle)),
                __uuidof(ID3D11Texture2D),
                reinterpret_cast<void**>(&texture));

            if (SUCCEEDED(hr) && texture) {
                device_ = candidateDevice;
                context_ = candidateContext;
                found = texture;
                break;
            }

            candidateContext->Release();
            candidateDevice->Release();
        }

        factory->Release();
        return found;
    }

    std::atomic<uint32_t> objectId_{vr::k_unTrackedDeviceIndexInvalid};
    ID3D11Device* device_ = nullptr;
    ID3D11DeviceContext* context_ = nullptr;
    uint64_t presentCount_ = 0;
    uint64_t frameCounter_ = 0;
    std::chrono::steady_clock::time_point lastVsync_ = std::chrono::steady_clock::now();
};

class QuestProvider final : public vr::IServerTrackedDeviceProvider {
public:
    vr::EVRInitError Init(vr::IVRDriverContext* context) override {
        VR_INIT_SERVER_DRIVER_CONTEXT(context);

        g_bridge.Start();

        hmd_ = std::make_unique<QuestHmd>();
        redirect_ = std::make_unique<QuestDisplayRedirect>();

        if (!vr::VRServerDriverHost()->TrackedDeviceAdded(
                "QL-HMD-001",
                vr::TrackedDeviceClass_HMD,
                hmd_.get())) {
            return vr::VRInitError_Driver_Unknown;
        }

        if (!vr::VRServerDriverHost()->TrackedDeviceAdded(
                "QL-REDIRECT-001",
                vr::TrackedDeviceClass_DisplayRedirect,
                redirect_.get())) {
            return vr::VRInitError_Driver_Unknown;
        }

        Log("QuestLink OpenVR: virtual HMD and display redirect registered");
        return vr::VRInitError_None;
    }

    void Cleanup() override {
        redirect_.reset();
        hmd_.reset();
        g_bridge.Stop();
        VR_CLEANUP_SERVER_DRIVER_CONTEXT();
    }

    const char* const* GetInterfaceVersions() override {
        return vr::k_InterfaceVersions;
    }

    void RunFrame() override {
        if (!hmd_) return;
        uint32_t id = hmd_->ObjectId();
        if (id != vr::k_unTrackedDeviceIndexInvalid) {
            vr::DriverPose_t pose = hmd_->GetPose();
            vr::VRServerDriverHost()->TrackedDevicePoseUpdated(id, pose, sizeof(pose));
        }
    }

    bool ShouldBlockStandbyMode() override { return false; }
    void EnterStandby() override {}
    void LeaveStandby() override {}

private:
    std::unique_ptr<QuestHmd> hmd_;
    std::unique_ptr<QuestDisplayRedirect> redirect_;
};

QuestProvider g_provider;

} // namespace

extern "C" __declspec(dllexport)
void* HmdDriverFactory(const char* interfaceName, int* returnCode) {
    if (interfaceName &&
        std::strcmp(interfaceName, vr::IServerTrackedDeviceProvider_Version) == 0) {
        return &g_provider;
    }

    if (returnCode) {
        *returnCode = vr::VRInitError_Init_InterfaceNotFound;
    }
    return nullptr;
}
