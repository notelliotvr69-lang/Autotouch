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
#include <string>
#include <deque>

namespace ql {
// Output timestamps identify queued input frames; the caller bounds the pending queue.
// CPU NV12 upload is intentional for this first integration; zero-copy is future work.
class H264Encoder {
    template<class T> using Ptr=Microsoft::WRL::ComPtr<T>;
    Ptr<IMFTransform> transform;
    Ptr<IMFMediaEventGenerator> events;
    uint32_t width=0,height=0,fps=72,bitrate=36000000;
    LONGLONG index=0;
    uint64_t outputFrame=0;
    bool com=false,mf=false,async=false;
    bool hardwareSelected=false;
    std::string failure;
    bool fail(const char* stage,HRESULT hr){failure=std::string(stage)+" HRESULT="+std::to_string(static_cast<unsigned long>(hr));return false;}
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
        output->SetUINT32(MF_MT_AVG_BITRATE,bitrate);output->SetUINT32(MF_MT_INTERLACE_MODE,MFVideoInterlace_Progressive);
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
            HRESULT status;event->GetStatus(&status);if(FAILED(status))return fail("encoder event",status);
            MediaEventType type;event->GetType(&type);if(type==METransformNeedInput)++needInput;if(type==METransformHaveOutput)++haveOutput;
        }
    }
    struct Encoded {uint64_t frame;std::vector<uint8_t> bytes;};
    std::deque<Encoded> ready;
    bool drainOutputs(){
        for(int i=0;i<16;++i){
            if(!pump())return false;
            if(async&&haveOutput==0)return true;
            MFT_OUTPUT_STREAM_INFO info{};if(FAILED(transform->GetOutputStreamInfo(0,&info)))return false;
            Ptr<IMFSample> outSample;Ptr<IMFMediaBuffer> outBuffer;
            if(!(info.dwFlags&MFT_OUTPUT_STREAM_PROVIDES_SAMPLES)){
                if(FAILED(MFCreateSample(&outSample))||FAILED(MFCreateMemoryBuffer(std::max<DWORD>(info.cbSize,width*height*2),&outBuffer)))return false;
                outSample->AddBuffer(outBuffer.Get());
            }
            MFT_OUTPUT_DATA_BUFFER data{};data.pSample=outSample.Get();DWORD status=0;
            HRESULT hr=transform->ProcessOutput(0,1,&data,&status);if(async)--haveOutput;
            if(data.pEvents)data.pEvents->Release();
            if(hr==MF_E_TRANSFORM_STREAM_CHANGE){
                bool accepted=false;
                for(DWORD typeIndex=0;typeIndex<32;++typeIndex){
                    Ptr<IMFMediaType> type;
                    if(FAILED(transform->GetOutputAvailableType(0,typeIndex,&type)))break;
                    GUID subtype{};if(FAILED(type->GetGUID(MF_MT_SUBTYPE,&subtype))||subtype!=MFVideoFormat_H264)continue;
                    UINT32 w=0,h=0;if(SUCCEEDED(MFGetAttributeSize(type.Get(),MF_MT_FRAME_SIZE,&w,&h))&&(w!=width||h!=height))continue;
                    MFSetAttributeSize(type.Get(),MF_MT_FRAME_SIZE,width,height);
                    MFSetAttributeRatio(type.Get(),MF_MT_FRAME_RATE,fps,1);
                    type->SetUINT32(MF_MT_AVG_BITRATE,bitrate);
                    if(SUCCEEDED(transform->SetOutputType(0,type.Get(),0))){accepted=true;break;}
                }
                if(!accepted)return fail("output format negotiation",hr);
                // Async transforms signal a fresh HaveOutput event after format negotiation.

                continue;
            }
            if(hr==MF_E_TRANSFORM_NEED_MORE_INPUT)return true;
            if(FAILED(hr))return fail("ProcessOutput",hr);
            if(!outSample)outSample.Attach(data.pSample);
            if(!outSample||FAILED(outSample->ConvertToContiguousBuffer(&outBuffer)))return false;
            LONGLONG timestamp=0;if(FAILED(outSample->GetSampleTime(&timestamp))||timestamp<0)return fail("output timestamp",E_FAIL);
            uint64_t completedFrame=static_cast<uint64_t>((timestamp*fps+5000000)/10000000);
            BYTE* bytes=nullptr;DWORD size=0;if(FAILED(outBuffer->Lock(&bytes,nullptr,&size)))return false;
            ready.push_back({completedFrame,std::vector<uint8_t>(bytes,bytes+size)});outBuffer->Unlock();
            if(ready.size()>8)return fail("output queue exceeded",E_FAIL);
        }
        return true;
    }
    static uint8_t clamp(int v){return uint8_t(v<0?0:v>255?255:v);}
public:
    ~H264Encoder(){events.Reset();transform.Reset();if(mf)MFShutdown();if(com)CoUninitialize();}
    const std::string& error() const {return failure;}
    uint64_t frameId() const {return outputFrame;}
    bool hardware() const { return hardwareSelected; }
    bool open(uint32_t w,uint32_t h,uint32_t rate,uint32_t bitsPerSecond,bool allowHardware=true) {
        width=w;height=h;fps=rate;bitrate=bitsPerSecond;
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
    bool encode(const std::vector<uint8_t>& bgra,std::vector<uint8_t>& output,uint64_t frame=0) {
        output.clear();
        if(!transform||bgra.size()!=size_t(width)*height*4)return false;
        // BT.601 limited-range NV12, averaging chroma across each 2x2 block.
        for(uint32_t y=0;y<height;++y)for(uint32_t x=0;x<width;++x){auto p=&bgra[(size_t(y)*width+x)*4];nv12[size_t(y)*width+x]=clamp(((66*p[2]+129*p[1]+25*p[0]+128)>>8)+16);}
        for(uint32_t y=0;y<height;y+=2)for(uint32_t x=0;x<width;x+=2){int r=0,g=0,b=0;for(int dy=0;dy<2;++dy)for(int dx=0;dx<2;++dx){auto p=&bgra[(size_t(y+dy)*width+x+dx)*4];r+=p[2];g+=p[1];b+=p[0];}r/=4;g/=4;b/=4;size_t o=size_t(width)*height+(y/2)*width+x;nv12[o]=clamp(((-38*r-74*g+112*b+128)>>8)+128);nv12[o+1]=clamp(((112*r-94*g-18*b+128)>>8)+128);}
        Ptr<IMFSample> sample;Ptr<IMFMediaBuffer> buffer;
        if(FAILED(MFCreateSample(&sample))||FAILED(MFCreateMemoryBuffer(static_cast<DWORD>(nv12.size()),&buffer)))return false;
        BYTE* bytes=nullptr;if(FAILED(buffer->Lock(&bytes,nullptr,nullptr)))return false;
        memcpy(bytes,nv12.data(),nv12.size());buffer->Unlock();buffer->SetCurrentLength(static_cast<DWORD>(nv12.size()));sample->AddBuffer(buffer.Get());
        sample->SetSampleTime(static_cast<LONGLONG>(frame)*10000000/fps);sample->SetSampleDuration(10000000/fps);++index;
        auto deadline=std::chrono::steady_clock::now()+std::chrono::milliseconds(250);
        // Async MFTs may withhold NeedInput until their pending output is consumed.
        // Always drain first, including when waiting for the next input slot.
        if(!drainOutputs())return false;
        while(async&&needInput==0){
            if(!drainOutputs())return false;
            if(needInput)break;
            if(std::chrono::steady_clock::now()>deadline)return fail("waiting for input",E_PENDING);
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
        HRESULT inputResult=transform->ProcessInput(0,sample.Get(),0);
        if(inputResult==MF_E_NOTACCEPTING){
            if(!drainOutputs())return false;
            inputResult=transform->ProcessInput(0,sample.Get(),0);
        }
        if(FAILED(inputResult))return fail("ProcessInput",inputResult);
        if(async)--needInput;
        if(!drainOutputs())return false;
        if(!ready.empty()){
            outputFrame=ready.front().frame;output=std::move(ready.front().bytes);ready.pop_front();
        }
        return true;
    }
};
}

