#include "h264_encoder.h"
#include <mfapi.h>
#include <mferror.h>
#include <mfidl.h>
#include <mfobjects.h>
#include <mftransform.h>
#include <algorithm>
#include <cstring>
#include <new>

namespace {
void releaseCom(IUnknown*& p) { if (p) { p->Release(); p = nullptr; } }

bool bgraToNv12(const uint8_t* src, uint32_t w, uint32_t h, uint8_t* dst) {
    if (!src || !dst || (w & 1) || (h & 1)) return false;
    const size_t ySize = static_cast<size_t>(w) * h;
    uint8_t* yPlane = dst;
    uint8_t* uvPlane = dst + ySize;
    for (uint32_t y = 0; y < h; ++y) {
        for (uint32_t x = 0; x < w; ++x) {
            const uint8_t* p = src + (static_cast<size_t>(y) * w + x) * 4;
            const int b = p[0], g = p[1], r = p[2];
            yPlane[static_cast<size_t>(y) * w + x] =
                static_cast<uint8_t>(std::clamp(((66*r + 129*g + 25*b + 128) >> 8) + 16, 0, 255));
        }
    }
    for (uint32_t y = 0; y < h; y += 2) {
        for (uint32_t x = 0; x < w; x += 2) {
            int r=0,g=0,b=0;
            for (uint32_t dy=0; dy<2; ++dy) for (uint32_t dx=0; dx<2; ++dx) {
                const uint8_t* p=src+(static_cast<size_t>(y+dy)*w+x+dx)*4;
                b+=p[0]; g+=p[1]; r+=p[2];
            }
            r/=4; g/=4; b/=4;
            const size_t uv=static_cast<size_t>(y/2)*w+x;
            uvPlane[uv]=static_cast<uint8_t>(std::clamp(((-38*r-74*g+112*b+128)>>8)+128,0,255));
            uvPlane[uv+1]=static_cast<uint8_t>(std::clamp(((112*r-94*g-18*b+128)>>8)+128,0,255));
        }
    }
    return true;
}
}

QuestLinkH264Encoder::~QuestLinkH264Encoder() { shutdown(); }

void QuestLinkH264Encoder::shutdown() {
    if (transform_) { transform_->ProcessMessage(MFT_MESSAGE_NOTIFY_END_STREAMING, 0); transform_->Release(); transform_=nullptr; }
    width_=height_=0; frameIndex_=0;
    MFShutdown();
}

bool QuestLinkH264Encoder::initialize(uint32_t width, uint32_t height, uint32_t fps, uint32_t bitrate) {
    shutdown();
    if (!width || !height || (width&1) || (height&1)) return false;
    if (FAILED(MFStartup(MF_VERSION, MFSTARTUP_FULL))) return false;

    MFT_REGISTER_TYPE_INFO inputInfo{MFMediaType_Video, MFVideoFormat_NV12};
    MFT_REGISTER_TYPE_INFO outputInfo{MFMediaType_Video, MFVideoFormat_H264};
    IMFActivate** activates=nullptr;
    UINT32 count=0;
    HRESULT hr=MFTEnumEx(MFT_CATEGORY_VIDEO_ENCODER,
        MFT_ENUM_FLAG_HARDWARE | MFT_ENUM_FLAG_SORTANDFILTER,
        &inputInfo, &outputInfo, &activates, &count);
    if (FAILED(hr) || !count || !activates) { MFShutdown(); return false; }

    for (UINT32 i=0; i<count && !transform_; ++i) {
        IMFTransform* candidate=nullptr;
        if (SUCCEEDED(activates[i]->ActivateObject(IID_PPV_ARGS(&candidate)))) {
            IMFMediaType* inType=nullptr; IMFMediaType* outType=nullptr;
            HRESULT a=MFCreateMediaType(&inType);
            if (SUCCEEDED(a)) a=inType->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
            if (SUCCEEDED(a)) a=inType->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_NV12);
            if (SUCCEEDED(a)) a=inType->SetUINT64(MF_MT_FRAME_SIZE, (static_cast<UINT64>(width)<<32)|height);
            if (SUCCEEDED(a)) a=inType->SetUINT64(MF_MT_FRAME_RATE, (static_cast<UINT64>(fps)<<32)|1);
            if (SUCCEEDED(a)) a=inType->SetUINT64(MF_MT_PIXEL_ASPECT_RATIO, (static_cast<UINT64>(1)<<32)|1);
            if (SUCCEEDED(a)) a=inType->SetUINT32(MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive);
            if (SUCCEEDED(a)) a=candidate->SetInputType(0,inType,0);

            if (SUCCEEDED(a)) a=MFCreateMediaType(&outType);
            if (SUCCEEDED(a)) a=outType->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
            if (SUCCEEDED(a)) a=outType->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_H264);
            if (SUCCEEDED(a)) a=outType->SetUINT64(MF_MT_FRAME_SIZE, (static_cast<UINT64>(width)<<32)|height);
            if (SUCCEEDED(a)) a=outType->SetUINT64(MF_MT_FRAME_RATE, (static_cast<UINT64>(fps)<<32)|1);
            if (SUCCEEDED(a)) a=outType->SetUINT64(MF_MT_PIXEL_ASPECT_RATIO, (static_cast<UINT64>(1)<<32)|1);
            if (SUCCEEDED(a)) a=outType->SetUINT32(MF_MT_AVG_BITRATE, bitrate);
            if (SUCCEEDED(a)) a=outType->SetUINT32(MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive);
            if (SUCCEEDED(a)) a=candidate->SetOutputType(0,outType,0);
            if (SUCCEEDED(a)) transform_=candidate; else candidate->Release();
            if (inType) inType->Release(); if (outType) outType->Release();
        }
    }
    for (UINT32 i=0;i<count;++i) activates[i]->Release();
    CoTaskMemFree(activates);
    if (!transform_) { MFShutdown(); return false; }
    transform_->ProcessMessage(MFT_MESSAGE_NOTIFY_BEGIN_STREAMING,0);
    transform_->ProcessMessage(MFT_MESSAGE_NOTIFY_START_OF_STREAM,0);
    width_=width; height_=height; frameIndex_=0;
    return true;
}

bool QuestLinkH264Encoder::encode(const uint8_t* bgra, uint32_t width, uint32_t height,
        int64_t timestamp100ns, std::vector<uint8_t>& accessUnit) {
    accessUnit.clear();
    if (!transform_ || !bgra || width!=width_ || height!=height_) return false;
    const DWORD yuvBytes=static_cast<DWORD>(static_cast<size_t>(width_)*height_*3/2);
    IMFMediaBuffer* inputBuffer=nullptr; IMFSample* inputSample=nullptr;
    HRESULT hr=MFCreateMemoryBuffer(yuvBytes,&inputBuffer);
    if (FAILED(hr)) return false;
    BYTE* data=nullptr; DWORD maxLen=0, currentLen=0;
    hr=inputBuffer->Lock(&data,&maxLen,&currentLen);
    if (SUCCEEDED(hr)) {
        bgraToNv12(bgra,width_,height_,data);
        inputBuffer->Unlock(); inputBuffer->SetCurrentLength(yuvBytes);
    }
    if (SUCCEEDED(hr)) hr=MFCreateSample(&inputSample);
    if (SUCCEEDED(hr)) hr=inputSample->AddBuffer(inputBuffer);
    if (SUCCEEDED(hr)) hr=inputSample->SetSampleTime(timestamp100ns);
    if (SUCCEEDED(hr)) hr=inputSample->SetSampleDuration(10000000LL/45);
    if (SUCCEEDED(hr)) hr=transform_->ProcessInput(0,inputSample,0);
    inputBuffer->Release(); if (inputSample) inputSample->Release();
    if (FAILED(hr)) return false;

    MFT_OUTPUT_STREAM_INFO info{};
    hr=transform_->GetOutputStreamInfo(0,&info);
    if (FAILED(hr)) return false;
    IMFSample* outputSample=nullptr; IMFMediaBuffer* outputBuffer=nullptr;
    if (!(info.dwFlags & MFT_OUTPUT_STREAM_PROVIDES_SAMPLES)) {
        DWORD size=std::max<DWORD>(info.cbSize, 1024*1024);
        if (FAILED(MFCreateSample(&outputSample)) || FAILED(MFCreateMemoryBuffer(size,&outputBuffer)) ||
            FAILED(outputSample->AddBuffer(outputBuffer))) {
            if(outputBuffer) outputBuffer->Release(); if(outputSample) outputSample->Release(); return false;
        }
    }
    MFT_OUTPUT_DATA_BUFFER out{}; out.dwStreamID=0; out.pSample=outputSample;
    DWORD status=0;
    hr=transform_->ProcessOutput(0,1,&out,&status);
    if (hr==MF_E_TRANSFORM_NEED_MORE_INPUT) hr=S_FALSE;
    if (SUCCEEDED(hr) && hr!=S_FALSE && out.pSample) {
        IMFMediaBuffer* resultBuffer=nullptr;
        if (SUCCEEDED(out.pSample->ConvertToContiguousBuffer(&resultBuffer))) {
            BYTE* bytes=nullptr; DWORD max=0, len=0;
            if (SUCCEEDED(resultBuffer->Lock(&bytes,&max,&len))) {
                accessUnit.assign(bytes,bytes+len);
                resultBuffer->Unlock();
            }
            resultBuffer->Release();
        }
    }
    if (out.pEvents) out.pEvents->Release();
    if (outputBuffer) outputBuffer->Release();
    if (outputSample) outputSample->Release();
    if (out.pSample && out.pSample!=outputSample) out.pSample->Release();
    return !accessUnit.empty();
}
