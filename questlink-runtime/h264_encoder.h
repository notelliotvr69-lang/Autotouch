#pragma once
#include <mfapi.h>
#include <mfidl.h>
#include <mftransform.h>
#include <mferror.h>
#include <codecapi.h>
#include <strmif.h>
#include <wrl/client.h>
#include <chrono>
#include <thread>
#include <vector>

namespace ql {
// One submitted frame -> one access unit; no B frames or accumulated video queue.
// CPU NV12 upload is intentional for this first integration; zero-copy is future work.
class H264Encoder {
    template<class T> using Ptr=Microsoft::WRL::ComPtr<T>;
    Ptr<IMFTransform> transform;
    Ptr<IMFMediaEventGenerator> events;
    uint32_t width=0,height=0,fps=72;
    LONGLONG index=0;
    bool com=false,mf=false,async=false;
    bool hardwareSelected=false;
    int needInput=0,haveOutput=0;
    std::vector<uint8_t> nv12;
    void setting(const GUID& key,ULONG value) {
        Ptr<ICodecAPI> api;if(FAILED(transform.As(&api)))return;
        VARIANT v;VariantInit(&v);v.vt=VT_UI4;v.ulVal=value;api->SetValue(&key,&v);
    }
    void booleanSetting(const GUID& key,bool value) {
        Ptr<ICodecAPI> api;if(FAILED(transform.As(&api)))return;
        VARIANT v;VariantInit(&v);v.vt=VT_BOOL;v.boolVal=value?VARIANT_TRUE:VARIANT_FALSE;api->SetValue(&key,&v);
    }
    bool configure() {
        Ptr<IMFAttributes> attributes;
        if(SUCCEEDED(transform->GetAttributes(&attributes))){UINT32 a=0;attributes->GetUINT32(MF_TRANSFORM_ASYNC,&a);async=a!=0;if(async)attributes->SetUINT32(MF_TRANSFORM_ASYNC_UNLOCK,TRUE);attributes->SetUINT32(MF_LOW_LATENCY,TRUE);}
        if(async&&FAILED(transform.As(&events)))return false;
        Ptr<IMFMediaType> output,input;
        if(FAILED(MFCreateMediaType(&output))||FAILED(MFCreateMediaType(&input)))return false;
        output->SetGUID(MF_MT_MAJOR_TYPE,MFMediaType_Video);output->SetGUID(MF_MT_SUBTYPE,MFVideoFormat_H264);
        output->SetUINT32(MF_MT_AVG_BITRATE,12000000);output->SetUINT32(MF_MT_INTERLACE_MODE,MFVideoInterlace_Progressive);
        output->SetUINT32(MF_MT_MPEG2_PROFILE,66);
        MFSetAttributeSize(output.Get(),MF_MT_FRAME_SIZE,width,height);MFSetAttributeRatio(output.Get(),MF_MT_FRAME_RATE,fps,1);MFSetAttributeRatio(output.Get(),MF_MT_PIXEL_ASPECT_RATIO,1,1);
        booleanSetting(CODECAPI_AVLowLatencyMode,true);setting(CODECAPI_AVEncMPVDefaultBPictureCount,0);setting(CODECAPI_AVEncMPVGOPSize,fps);
        if(FAILED(transform->SetOutputType(0,output.Get(),0)))return false;
        input->SetGUID(MF_MT_MAJOR_TYPE,MFMediaType_Video);input->SetGUID(MF_MT_SUBTYPE,MFVideoFormat_NV12);
        input->SetUINT32(MF_MT_INTERLACE_MODE,MFVideoInterlace_Progressive);
        MFSetAttributeSize(input.Get(),MF_MT_FRAME_SIZE,width,height);MFSetAttributeRatio(input.Get(),MF_MT_FRAME_RATE,fps,1);MFSetAttributeRatio(input.Get(),MF_MT_PIXEL_ASPECT_RATIO,1,1);
        if(FAILED(transform->SetInputType(0,input.Get(),0)))return false;
        if(FAILED(transform->ProcessMessage(MFT_MESSAGE_NOTIFY_BEGIN_STREAMING,0)))return false;
        return SUCCEEDED(transform->ProcessMessage(MFT_MESSAGE_NOTIFY_START_OF_STREAM,0));
    }
    bool pump() {
        if(!async)return true;
        for(;;){Ptr<IMFMediaEvent> event;HRESULT hr=events->GetEvent(MF_EVENT_FLAG_NO_WAIT,&event);
            if(hr==MF_E_NO_EVENTS_AVAILABLE)return true;if(FAILED(hr))return false;
            HRESULT status;event->GetStatus(&status);if(FAILED(status))return false;
            MediaEventType type;event->GetType(&type);if(type==METransformNeedInput)++needInput;if(type==METransformHaveOutput)++haveOutput;
        }
    }
    static uint8_t clamp(int v){return uint8_t(v<0?0:v>255?255:v);}
public:
    ~H264Encoder(){events.Reset();transform.Reset();if(mf)MFShutdown();if(com)CoUninitialize();}
    bool hardware() const { return hardwareSelected; }
    bool open(uint32_t w,uint32_t h,uint32_t rate,uint32_t,bool allowHardware=true) {
        width=w;height=h;fps=rate;
        com=SUCCEEDED(CoInitializeEx(nullptr,COINIT_MULTITHREADED));
        mf=SUCCEEDED(MFStartup(MF_VERSION));if(!mf)return false;
        MFT_REGISTER_TYPE_INFO in{MFMediaType_Video,MFVideoFormat_NV12},out{MFMediaType_Video,MFVideoFormat_H264};
        for(UINT32 flags:{UINT32(MFT_ENUM_FLAG_HARDWARE|MFT_ENUM_FLAG_SORTANDFILTER),UINT32(MFT_ENUM_FLAG_SYNCMFT|MFT_ENUM_FLAG_SORTANDFILTER)}) {
            if(!allowHardware&&(flags&MFT_ENUM_FLAG_HARDWARE))continue;
            IMFActivate** list=nullptr;UINT32 count=0;
            if(FAILED(MFTEnumEx(MFT_CATEGORY_VIDEO_ENCODER,flags,&in,&out,&list,&count)))continue;
            bool ok=false;
            for(UINT32 i=0;i<count&&!ok;++i){events.Reset();transform.Reset();async=false;needInput=haveOutput=0;
                if(SUCCEEDED(list[i]->ActivateObject(IID_PPV_ARGS(&transform))))ok=configure();
            }
            for(UINT32 i=0;i<count;++i)list[i]->Release();CoTaskMemFree(list);
            if(ok){hardwareSelected=(flags&MFT_ENUM_FLAG_HARDWARE)!=0;nv12.resize(size_t(w)*h*3/2);return true;}
        }
        transform.Reset();return false;
    }
    bool encode(const std::vector<uint8_t>& bgra,std::vector<uint8_t>& output) {
        if(!transform||bgra.size()!=size_t(width)*height*4)return false;
        // BT.601 limited-range NV12, averaging chroma across each 2x2 block.
        for(uint32_t y=0;y<height;++y)for(uint32_t x=0;x<width;++x){auto p=&bgra[(size_t(y)*width+x)*4];nv12[size_t(y)*width+x]=clamp(((66*p[2]+129*p[1]+25*p[0]+128)>>8)+16);}
        for(uint32_t y=0;y<height;y+=2)for(uint32_t x=0;x<width;x+=2){int r=0,g=0,b=0;for(int dy=0;dy<2;++dy)for(int dx=0;dx<2;++dx){auto p=&bgra[(size_t(y+dy)*width+x+dx)*4];r+=p[2];g+=p[1];b+=p[0];}r/=4;g/=4;b/=4;size_t o=size_t(width)*height+(y/2)*width+x;nv12[o]=clamp(((-38*r-74*g+112*b+128)>>8)+128);nv12[o+1]=clamp(((112*r-94*g-18*b+128)>>8)+128);}
        Ptr<IMFSample> sample;Ptr<IMFMediaBuffer> buffer;
        if(FAILED(MFCreateSample(&sample))||FAILED(MFCreateMemoryBuffer(static_cast<DWORD>(nv12.size()),&buffer)))return false;
        BYTE* bytes=nullptr;if(FAILED(buffer->Lock(&bytes,nullptr,nullptr)))return false;
        memcpy(bytes,nv12.data(),nv12.size());buffer->Unlock();buffer->SetCurrentLength(static_cast<DWORD>(nv12.size()));sample->AddBuffer(buffer.Get());
        sample->SetSampleTime(index*10000000/fps);sample->SetSampleDuration(10000000/fps);++index;
        auto deadline=std::chrono::steady_clock::now()+std::chrono::milliseconds(250);
        while(async&&needInput==0){if(!pump()||std::chrono::steady_clock::now()>deadline)return false;std::this_thread::sleep_for(std::chrono::milliseconds(1));}
        if(FAILED(transform->ProcessInput(0,sample.Get(),0)))return false;if(async)--needInput;
        for(;;){
            if(std::chrono::steady_clock::now()>deadline||!pump())return false;
            if(async&&haveOutput==0){std::this_thread::sleep_for(std::chrono::milliseconds(1));continue;}
            MFT_OUTPUT_STREAM_INFO info{};if(FAILED(transform->GetOutputStreamInfo(0,&info)))return false;
            Ptr<IMFSample> outSample;Ptr<IMFMediaBuffer> outBuffer;
            if(!(info.dwFlags&MFT_OUTPUT_STREAM_PROVIDES_SAMPLES)){
                if(FAILED(MFCreateSample(&outSample))||FAILED(MFCreateMemoryBuffer(std::max<DWORD>(info.cbSize,width*height*2),&outBuffer)))return false;
                outSample->AddBuffer(outBuffer.Get());
            }
            MFT_OUTPUT_DATA_BUFFER data{};data.pSample=outSample.Get();DWORD status=0;
            HRESULT hr=transform->ProcessOutput(0,1,&data,&status);if(async)--haveOutput;
            if(data.pEvents)data.pEvents->Release();
            if(hr==MF_E_TRANSFORM_NEED_MORE_INPUT)return false; // Encoder buffering violates this transport's pose association.
            if(FAILED(hr))return false;
            if(!outSample)outSample.Attach(data.pSample);
            if(!outSample||FAILED(outSample->ConvertToContiguousBuffer(&outBuffer)))return false;
            DWORD size=0;if(FAILED(outBuffer->Lock(&bytes,nullptr,&size)))return false;
            output.assign(bytes,bytes+size);outBuffer->Unlock();return !output.empty();
        }
    }
};
}
