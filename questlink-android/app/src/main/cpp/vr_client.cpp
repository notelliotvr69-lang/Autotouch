#include <jni.h>
#include <android_native_app_glue.h>
#include <android/log.h>
#include <android/imagedecoder.h>
#include <android/bitmap.h>
#include <android/native_window_jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <netinet/tcp.h>
#include <poll.h>
#include <fcntl.h>
#include <unistd.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <map>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>
#include "protocol.h"

namespace {
using Clock=std::chrono::steady_clock;
void check(XrResult r,const char* operation){if(XR_FAILED(r))throw std::runtime_error(std::string(operation)+" ("+std::to_string(r)+")");}
#define XR(call) check((call),#call)
struct Stream {
    std::atomic<bool> stop{false};
    std::mutex socketMutex,mutex;
    std::condition_variable cv;
    int socket=-1;
    std::thread thread;
    ql::Tracking tracking;
    uint64_t trackingVersion=0;
    ql::VideoHeader jpegHeader;
    std::vector<uint8_t> rgba;
    std::map<uint64_t,std::pair<ql::VideoHeader,Clock::time_point>> videoMetadata;
    Clock::time_point jpegTime{};
    std::string status="Connecting to PC...";
    ANativeWindow* videoWindow=nullptr;
    bool io(int fd,void* data,size_t size,bool sendData){auto* p=static_cast<uint8_t*>(data);while(size&&!stop){ssize_t n=sendData?send(fd,p,size,MSG_NOSIGNAL):recv(fd,p,size,0);if(n<=0)return false;p+=n;size-=size_t(n);}return size==0;}
    void setStatus(const std::string& s){std::lock_guard<std::mutex> lock(mutex);status=s;__android_log_print(ANDROID_LOG_INFO,"QuestLink","%s",s.c_str());}
    void publish(const ql::Tracking& t){std::lock_guard<std::mutex> lock(mutex);tracking=t;++trackingVersion;cv.notify_all();}
    void sendTracking(int fd,std::atomic<bool>& alive){uint64_t version=0;while(alive&&!stop){ql::Tracking t;{std::unique_lock<std::mutex> lock(mutex);cv.wait_for(lock,std::chrono::milliseconds(50),[&]{return stop||!alive||version!=trackingVersion;});if(version==trackingVersion)continue;t=tracking;version=trackingVersion;}auto bytes=ql::encode(t);if(!io(fd,bytes.data(),bytes.size(),true)){alive=false;shutdown(fd,SHUT_RDWR);break;}}}
    int connectHost(const std::string& host){
        sockaddr_in addr{};addr.sin_family=AF_INET;addr.sin_port=htons(47991);
        if(inet_pton(AF_INET,host.c_str(),&addr.sin_addr)!=1)throw std::runtime_error("Enter the PC's IPv4 address in the launcher");
        int fd=::socket(AF_INET,SOCK_STREAM,0);if(fd<0)return -1;
        fcntl(fd,F_SETFL,O_NONBLOCK);int result=connect(fd,reinterpret_cast<sockaddr*>(&addr),sizeof(addr));
        if(result!=0){pollfd p{fd,POLLOUT,0};int error=0;socklen_t n=sizeof(error);if(poll(&p,1,1000)<=0||getsockopt(fd,SOL_SOCKET,SO_ERROR,&error,&n)<0||error){close(fd);return -1;}}
        fcntl(fd,F_SETFL,0);timeval timeout{2,0};setsockopt(fd,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout));setsockopt(fd,SOL_SOCKET,SO_SNDTIMEO,&timeout,sizeof(timeout));int noDelay=1;setsockopt(fd,IPPROTO_TCP,TCP_NODELAY,&noDelay,sizeof(noDelay));return fd;
    }
    void run(const std::string& host){
        while(!stop){int fd=-1;try{fd=connectHost(host);}catch(const std::exception& e){setStatus(e.what());return;}
            if(fd<0){for(int i=0;i<10&&!stop;++i)std::this_thread::sleep_for(std::chrono::milliseconds(100));continue;}
            {std::lock_guard<std::mutex> lock(socketMutex);socket=fd;}
            {std::lock_guard<std::mutex> lock(mutex);videoMetadata.clear();rgba.clear();jpegTime={};}
            uint32_t hello=htonl(ql::HelloMagic);io(fd,&hello,sizeof(hello),true);
            std::atomic<bool> alive{true};std::thread sender(&Stream::sendTracking,this,fd,std::ref(alive));
            AMediaCodec* decoder=nullptr;
            try{
                uint32_t codec=0,width=0,height=0;setStatus("Connected; waiting for game frames");
                while(!stop&&alive){
                    std::array<uint8_t,ql::VideoHeaderBytes> raw{};if(!io(fd,raw.data(),raw.size(),false))break;
                    auto header=ql::decodeVideo(raw.data(),raw.size());std::vector<uint8_t> payload(header.bytes);if(!io(fd,payload.data(),payload.size(),false))break;
                    if(codec&&(codec!=header.codec||width!=header.width||height!=header.height))throw std::runtime_error("Video format changed; reconnecting");
                    codec=header.codec;width=header.width;height=header.height;
                    if(codec==1){
                        AImageDecoder* image=nullptr;
                        if(AImageDecoder_createFromBuffer(payload.data(),payload.size(),&image)!=ANDROID_IMAGE_DECODER_SUCCESS)throw std::runtime_error("JPEG decode failed");
                        const auto* info=AImageDecoder_getHeaderInfo(image);
                        if(AImageDecoderHeaderInfo_getWidth(info)!=int(width)||AImageDecoderHeaderInfo_getHeight(info)!=int(height)){AImageDecoder_delete(image);throw std::runtime_error("JPEG dimensions mismatch");}
                        AImageDecoder_setAndroidBitmapFormat(image,ANDROID_BITMAP_FORMAT_RGBA_8888);
                        std::vector<uint8_t> pixels(size_t(width)*height*4);
                        int result=AImageDecoder_decodeImage(image,pixels.data(),width*4,pixels.size());AImageDecoder_delete(image);
                        if(result!=ANDROID_IMAGE_DECODER_SUCCESS)throw std::runtime_error("JPEG pixels invalid");
                        std::lock_guard<std::mutex> lock(mutex);rgba=std::move(pixels);jpegHeader=header;jpegTime=Clock::now();
                    }else{
                        if(!decoder){
                            decoder=AMediaCodec_createDecoderByType("video/avc");if(!decoder)throw std::runtime_error("H264 decoder unavailable");
                            AMediaFormat* format=AMediaFormat_new();AMediaFormat_setString(format,AMEDIAFORMAT_KEY_MIME,"video/avc");AMediaFormat_setInt32(format,AMEDIAFORMAT_KEY_WIDTH,width);AMediaFormat_setInt32(format,AMEDIAFORMAT_KEY_HEIGHT,height);AMediaFormat_setInt32(format,"low-latency",1);
                            auto result=AMediaCodec_configure(decoder,format,videoWindow,nullptr,0);AMediaFormat_delete(format);
                            if(result!=AMEDIA_OK||AMediaCodec_start(decoder)!=AMEDIA_OK)throw std::runtime_error("H264 decoder setup failed");
                        }
                        // Timestamp identifies the exact projection poses used to render this image.
                        {std::lock_guard<std::mutex> lock(mutex);videoMetadata[header.id]={header,Clock::now()};while(videoMetadata.size()>32)videoMetadata.erase(videoMetadata.begin());}
                        ssize_t index=AMediaCodec_dequeueInputBuffer(decoder,100000);if(index<0)throw std::runtime_error("Decoder input stalled");
                        size_t capacity=0;auto* buffer=AMediaCodec_getInputBuffer(decoder,index,&capacity);if(!buffer||capacity<payload.size())throw std::runtime_error("Decoder input too large");
                        memcpy(buffer,payload.data(),payload.size());
                        if(AMediaCodec_queueInputBuffer(decoder,index,0,payload.size(),header.id,0)!=AMEDIA_OK)throw std::runtime_error("Decoder input rejected");
                        auto deadline=Clock::now()+std::chrono::milliseconds(150);bool rendered=false;
                        while(!stop&&Clock::now()<deadline){AMediaCodecBufferInfo info{};ssize_t output=AMediaCodec_dequeueOutputBuffer(decoder,&info,10000);
                            if(output>=0){AMediaCodec_releaseOutputBuffer(decoder,output,true);rendered=true;break;}
                            if(output==AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED){auto* f=AMediaCodec_getOutputFormat(decoder);AMediaFormat_delete(f);}
                        }
                        if(!rendered)throw std::runtime_error("Decoder output stalled");
                    }
                }
            }catch(const std::exception& e){setStatus(e.what());}
            alive=false;shutdown(fd,SHUT_RDWR);cv.notify_all();sender.join();
            if(decoder){AMediaCodec_stop(decoder);AMediaCodec_delete(decoder);}
            {std::lock_guard<std::mutex> lock(socketMutex);socket=-1;close(fd);}
            setStatus("Disconnected; retrying PC connection");
        }
    }
    void start(const std::string& host){thread=std::thread(&Stream::run,this,host);}
    void shutdownStream(){stop=true;cv.notify_all();{std::lock_guard<std::mutex> lock(socketMutex);if(socket>=0)shutdown(socket,SHUT_RDWR);}if(thread.joinable())thread.join();}
    ~Stream(){shutdownStream();}
};

struct Client {
    android_app* app;JNIEnv* env=nullptr;jclass activityClass=nullptr;jfloatArray textureMatrix=nullptr;
    jmethodID latchMethod=nullptr;
    XrInstance instance=XR_NULL_HANDLE;XrSession session=XR_NULL_HANDLE;XrSystemId system=0;
    XrSpace stage=XR_NULL_HANDLE,head=XR_NULL_HANDLE,gripSpace[2]{},aimSpace[2]{};
    XrActionSet actionSet=XR_NULL_HANDLE;XrAction grip{},aim{},trigger{},squeeze{},stick{},buttons[9]{};XrPath hands[2]{};
    uint32_t eyeWidth=0,eyeHeight=0;
    XrSwapchain chains[2]{};std::vector<XrSwapchainImageOpenGLESKHR> images[2];
    EGLDisplay display=EGL_NO_DISPLAY;EGLContext context=EGL_NO_CONTEXT;EGLSurface surface=EGL_NO_SURFACE;
    GLuint programs[2]{},texture=0,externalTexture=0,fbo=0,vao=0;
    bool running=false,focused=false;uint32_t sequence=0;
    Stream stream;
    ql::VideoHeader shown;Clock::time_point shownTime{};bool hasImage=false,external=false;
    float matrix[16]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
    explicit Client(android_app* a):app(a){}
    XrPath path(const std::string& s){XrPath p;XR(xrStringToPath(instance,s.c_str(),&p));return p;}
    void init(){
        app->activity->vm->AttachCurrentThread(&env,nullptr);
        activityClass=env->GetObjectClass(app->activity->clazz);
        auto getIntent=env->GetMethodID(activityClass,"getIntent","()Landroid/content/Intent;");jobject intent=env->CallObjectMethod(app->activity->clazz,getIntent);
        jclass intentClass=env->GetObjectClass(intent);jmethodID extra=env->GetMethodID(intentClass,"getStringExtra","(Ljava/lang/String;)Ljava/lang/String;");jstring key=env->NewStringUTF("host");jstring value=(jstring)env->CallObjectMethod(intent,extra,key);
        if(!value)throw std::runtime_error("Launch VR from QuestLink's PC connection screen");
        const char* chars=env->GetStringUTFChars(value,nullptr);std::string host=chars;env->ReleaseStringUTFChars(value,chars);env->DeleteLocalRef(value);env->DeleteLocalRef(key);env->DeleteLocalRef(intentClass);env->DeleteLocalRef(intent);
        PFN_xrInitializeLoaderKHR initialize=nullptr;
        XR(xrGetInstanceProcAddr(XR_NULL_HANDLE,"xrInitializeLoaderKHR",reinterpret_cast<PFN_xrVoidFunction*>(&initialize)));
        XrLoaderInitInfoAndroidKHR loader{XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR};loader.applicationVM=app->activity->vm;loader.applicationContext=app->activity->clazz;
        XR(initialize(reinterpret_cast<XrLoaderInitInfoBaseHeaderKHR*>(&loader)));
        const char* extensions[]={XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME,XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME};
        XrInstanceCreateInfoAndroidKHR android{XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR};android.applicationVM=app->activity->vm;android.applicationActivity=app->activity->clazz;
        XrInstanceCreateInfo create{XR_TYPE_INSTANCE_CREATE_INFO};create.next=&android;strcpy(create.applicationInfo.applicationName,"QuestLink Wireless");create.applicationInfo.apiVersion=XR_MAKE_VERSION(1,0,0);create.enabledExtensionCount=2;create.enabledExtensionNames=extensions;
        XR(xrCreateInstance(&create,&instance));XrSystemGetInfo get{XR_TYPE_SYSTEM_GET_INFO};get.formFactor=XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;XR(xrGetSystem(instance,&get,&system));
        PFN_xrGetOpenGLESGraphicsRequirementsKHR requirements=nullptr;XR(xrGetInstanceProcAddr(instance,"xrGetOpenGLESGraphicsRequirementsKHR",reinterpret_cast<PFN_xrVoidFunction*>(&requirements)));XrGraphicsRequirementsOpenGLESKHR req{XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR};XR(requirements(instance,system,&req));
        display=eglGetDisplay(EGL_DEFAULT_DISPLAY);if(!eglInitialize(display,nullptr,nullptr))throw std::runtime_error("EGL initialization failed");
        EGLint attrs[]={EGL_RENDERABLE_TYPE,EGL_OPENGL_ES3_BIT,EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_NONE};EGLConfig config;EGLint count=0;
        if(!eglChooseConfig(display,attrs,&config,1,&count)||count!=1)throw std::runtime_error("No GLES3 configuration");
        EGLint contextAttrs[]={EGL_CONTEXT_CLIENT_VERSION,3,EGL_NONE};context=eglCreateContext(display,config,EGL_NO_CONTEXT,contextAttrs);EGLint surfaceAttrs[]={EGL_WIDTH,16,EGL_HEIGHT,16,EGL_NONE};surface=eglCreatePbufferSurface(display,config,surfaceAttrs);
        if(context==EGL_NO_CONTEXT||surface==EGL_NO_SURFACE||!eglMakeCurrent(display,surface,surface,context))throw std::runtime_error("EGL context failed");
        XrGraphicsBindingOpenGLESAndroidKHR binding{XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR};binding.display=display;binding.config=config;binding.context=context;
        XrSessionCreateInfo sc{XR_TYPE_SESSION_CREATE_INFO};sc.next=&binding;sc.systemId=system;XR(xrCreateSession(instance,&sc,&session));
        XrReferenceSpaceCreateInfo space{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};space.poseInReferenceSpace=ql::Identity;space.referenceSpaceType=XR_REFERENCE_SPACE_TYPE_STAGE;XR(xrCreateReferenceSpace(session,&space,&stage));space.referenceSpaceType=XR_REFERENCE_SPACE_TYPE_VIEW;XR(xrCreateReferenceSpace(session,&space,&head));
        initActions();initGraphics();
        glGenTextures(1,&externalTexture);glBindTexture(GL_TEXTURE_EXTERNAL_OES,externalTexture);glTexParameteri(GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        auto createSurface=env->GetMethodID(activityClass,"createVideoSurface","(I)Landroid/view/Surface;");jobject videoSurface=env->CallObjectMethod(app->activity->clazz,createSurface,externalTexture);
        if(env->ExceptionCheck()||!videoSurface){env->ExceptionClear();throw std::runtime_error("Video surface failed");}
        stream.videoWindow=ANativeWindow_fromSurface(env,videoSurface);env->DeleteLocalRef(videoSurface);
        latchMethod=env->GetMethodID(activityClass,"latchVideo","([F)J");textureMatrix=env->NewFloatArray(16);
        stream.start(host);
    }
    XrAction action(const char* name,XrActionType type){XrActionCreateInfo c{XR_TYPE_ACTION_CREATE_INFO};strcpy(c.actionName,name);strcpy(c.localizedActionName,name);c.actionType=type;c.countSubactionPaths=2;c.subactionPaths=hands;XrAction a;XR(xrCreateAction(actionSet,&c,&a));return a;}
    void initActions(){
        hands[0]=path("/user/hand/left");hands[1]=path("/user/hand/right");XrActionSetCreateInfo set{XR_TYPE_ACTION_SET_CREATE_INFO};strcpy(set.actionSetName,"questlink");strcpy(set.localizedActionSetName,"QuestLink");XR(xrCreateActionSet(instance,&set,&actionSet));
        grip=action("grip",XR_ACTION_TYPE_POSE_INPUT);aim=action("aim",XR_ACTION_TYPE_POSE_INPUT);trigger=action("trigger",XR_ACTION_TYPE_FLOAT_INPUT);squeeze=action("squeeze",XR_ACTION_TYPE_FLOAT_INPUT);stick=action("stick",XR_ACTION_TYPE_VECTOR2F_INPUT);
        const char* names[]={"primary","secondary","stick_click","menu","primary_touch","secondary_touch","stick_touch","trigger_touch","thumbrest_touch"};for(int i=0;i<9;++i)buttons[i]=action(names[i],XR_ACTION_TYPE_BOOLEAN_INPUT);
        std::vector<XrActionSuggestedBinding> bindings;
        for(int h=0;h<2;++h){std::string base=h==0?"/user/hand/left/input/":"/user/hand/right/input/";
            auto bind=[&](XrAction a,const char* component){bindings.push_back({a,path(base+component)});};
            bind(grip,"grip/pose");bind(aim,"aim/pose");bind(trigger,"trigger/value");bind(squeeze,"squeeze/value");bind(stick,"thumbstick");
            bind(buttons[0],h==0?"x/click":"a/click");bind(buttons[1],h==0?"y/click":"b/click");bind(buttons[2],"thumbstick/click");if(h==0)bind(buttons[3],"menu/click");
            bind(buttons[4],h==0?"x/touch":"a/touch");bind(buttons[5],h==0?"y/touch":"b/touch");bind(buttons[6],"thumbstick/touch");bind(buttons[7],"trigger/touch");bind(buttons[8],"thumbrest/touch");
            XrActionSpaceCreateInfo c{XR_TYPE_ACTION_SPACE_CREATE_INFO};c.poseInActionSpace=ql::Identity;c.subactionPath=hands[h];c.action=grip;XR(xrCreateActionSpace(session,&c,&gripSpace[h]));c.action=aim;XR(xrCreateActionSpace(session,&c,&aimSpace[h]));
        }
        XrInteractionProfileSuggestedBinding suggestion{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};suggestion.interactionProfile=path("/interaction_profiles/oculus/touch_controller");suggestion.countSuggestedBindings=uint32_t(bindings.size());suggestion.suggestedBindings=bindings.data();XR(xrSuggestInteractionProfileBindings(instance,&suggestion));
        XrSessionActionSetsAttachInfo attach{XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO};attach.countActionSets=1;attach.actionSets=&actionSet;XR(xrAttachSessionActionSets(session,&attach));
    }
    GLuint shader(GLenum kind,const std::string& text){GLuint s=glCreateShader(kind);const char* p=text.c_str();glShaderSource(s,1,&p,nullptr);glCompileShader(s);GLint ok;glGetShaderiv(s,GL_COMPILE_STATUS,&ok);if(!ok){char log[1024];glGetShaderInfoLog(s,sizeof(log),nullptr,log);throw std::runtime_error(log);}return s;}
    void resizeSwapchains(uint32_t width,uint32_t height){
        if(width==eyeWidth&&height==eyeHeight)return;
        uint32_t count=0;
        for(int i=0;i<2;++i){if(chains[i]){XR(xrDestroySwapchain(chains[i]));chains[i]=XR_NULL_HANDLE;} XrSwapchainCreateInfo c{XR_TYPE_SWAPCHAIN_CREATE_INFO};c.usageFlags=XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT;c.format=GL_RGBA8;c.sampleCount=1;c.width=width;c.height=height;c.faceCount=1;c.arraySize=1;c.mipCount=1;XR(xrCreateSwapchain(session,&c,&chains[i]));XR(xrEnumerateSwapchainImages(chains[i],0,&count,nullptr));images[i].resize(count,{XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR});XR(xrEnumerateSwapchainImages(chains[i],count,&count,reinterpret_cast<XrSwapchainImageBaseHeader*>(images[i].data())));}
        eyeWidth=width;eyeHeight=height;
    }
    void initGraphics(){
        uint32_t count=0;XR(xrEnumerateSwapchainFormats(session,0,&count,nullptr));std::vector<int64_t> formats(count);XR(xrEnumerateSwapchainFormats(session,count,&count,formats.data()));
        if(std::find(formats.begin(),formats.end(),GL_RGBA8)==formats.end())throw std::runtime_error("RGBA8 swapchain unavailable");

        std::string vertex="#version 300 es\nprecision highp float;out vec2 uv;void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));uv=p;gl_Position=vec4(p*2.0-1.0,0,1);}";
        for(int i=0;i<2;++i){std::string fragment="#version 300 es\n";if(i)fragment+="#extension GL_OES_EGL_image_external_essl3 : require\n";
            fragment+="precision highp float;in vec2 uv;out vec4 color;uniform float eye;uniform mat4 texMatrix;uniform ";fragment+=i?"samplerExternalOES":"sampler2D";fragment+=" video;void main(){vec2 p=vec2((uv.x+eye)*0.5,";fragment+=i?"uv.y":"1.0-uv.y";fragment+=");color=texture(video,(texMatrix*vec4(p,0,1)).xy);}";
            auto v=shader(GL_VERTEX_SHADER,vertex),f=shader(GL_FRAGMENT_SHADER,fragment);programs[i]=glCreateProgram();glAttachShader(programs[i],v);glAttachShader(programs[i],f);glLinkProgram(programs[i]);glDeleteShader(v);glDeleteShader(f);GLint ok;glGetProgramiv(programs[i],GL_LINK_STATUS,&ok);if(!ok)throw std::runtime_error("Video shader link failed");
        }
        glGenFramebuffers(1,&fbo);glGenVertexArrays(1,&vao);glGenTextures(1,&texture);glBindTexture(GL_TEXTURE_2D,texture);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
    }
    uint32_t locate(XrSpace space,XrTime time,XrPosef& pose){XrSpaceLocation l{XR_TYPE_SPACE_LOCATION};XR(xrLocateSpace(space,stage,time,&l));auto flags=XR_SPACE_LOCATION_ORIENTATION_VALID_BIT|XR_SPACE_LOCATION_POSITION_VALID_BIT|XR_SPACE_LOCATION_ORIENTATION_TRACKED_BIT|XR_SPACE_LOCATION_POSITION_TRACKED_BIT;if((l.locationFlags&flags)!=flags)return 0;pose=l.pose;return ql::Tracked;}
    float scalar(XrAction a,int h){XrActionStateGetInfo i{XR_TYPE_ACTION_STATE_GET_INFO};i.action=a;i.subactionPath=hands[h];XrActionStateFloat s{XR_TYPE_ACTION_STATE_FLOAT};XR(xrGetActionStateFloat(session,&i,&s));return s.isActive?s.currentState:0;}
    ql::Tracking tracking(XrTime time){
        ql::Tracking t;t.sequence=++sequence;t.focused=focused;t.headFlags=locate(head,time,t.head);
        XrViewLocateInfo info{XR_TYPE_VIEW_LOCATE_INFO};info.viewConfigurationType=XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;info.displayTime=time;info.space=stage;XrViewState state{XR_TYPE_VIEW_STATE};XrView views[2]{{XR_TYPE_VIEW},{XR_TYPE_VIEW}};uint32_t count=0;XR(xrLocateViews(session,&info,&state,2,&count,views));
        if(count!=2||(state.viewStateFlags&(XR_VIEW_STATE_ORIENTATION_VALID_BIT|XR_VIEW_STATE_POSITION_VALID_BIT))!=(XR_VIEW_STATE_ORIENTATION_VALID_BIT|XR_VIEW_STATE_POSITION_VALID_BIT))t.headFlags=0;
        if(t.headFlags)for(int i=0;i<2;++i){t.eyes[i].pose=views[i].pose;t.eyes[i].fov=views[i].fov;}
        XrActiveActionSet active{actionSet,XR_NULL_PATH};XrActionsSyncInfo sync{XR_TYPE_ACTIONS_SYNC_INFO};sync.countActiveActionSets=1;sync.activeActionSets=&active;XrResult result=xrSyncActions(session,&sync);if(result==XR_SESSION_NOT_FOCUSED){t.focused=0;return t;}XR(result);
        for(int h=0;h<2;++h){auto& hand=t.hands[h];hand.gripFlags=locate(gripSpace[h],time,hand.grip);hand.aimFlags=locate(aimSpace[h],time,hand.aim);hand.trigger=scalar(trigger,h);hand.squeeze=scalar(squeeze,h);
            XrActionStateGetInfo i{XR_TYPE_ACTION_STATE_GET_INFO};i.action=stick;i.subactionPath=hands[h];XrActionStateVector2f v{XR_TYPE_ACTION_STATE_VECTOR2F};XR(xrGetActionStateVector2f(session,&i,&v));if(v.isActive)hand.stick=v.currentState;
            for(int b=0;b<9;++b){i.action=buttons[b];XrActionStateBoolean s{XR_TYPE_ACTION_STATE_BOOLEAN};XR(xrGetActionStateBoolean(session,&i,&s));if(s.isActive&&s.currentState)hand.buttons|=1u<<b;}
        }
        return t;
    }
    void latch(){
        jlong timestamp=env->CallLongMethod(app->activity->clazz,latchMethod,textureMatrix);
        if(env->ExceptionCheck()){env->ExceptionClear();throw std::runtime_error("Video texture update failed");}
        if(timestamp>0){std::lock_guard<std::mutex> lock(stream.mutex);auto it=stream.videoMetadata.find(uint64_t(timestamp)/1000);if(it!=stream.videoMetadata.end()){shown=it->second.first;shownTime=it->second.second;hasImage=true;external=true;env->GetFloatArrayRegion(textureMatrix,0,16,matrix);}}
        std::vector<uint8_t> pixels;{std::lock_guard<std::mutex> lock(stream.mutex);if(!stream.rgba.empty()){pixels=std::move(stream.rgba);shown=stream.jpegHeader;shownTime=stream.jpegTime;hasImage=true;external=false;}}
        if(!pixels.empty()){glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,shown.width,shown.height,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels.data());}
    }
    void frame(){
        XrFrameWaitInfo wait{XR_TYPE_FRAME_WAIT_INFO};XrFrameState state{XR_TYPE_FRAME_STATE};XR(xrWaitFrame(session,&wait,&state));XrFrameBeginInfo begin{XR_TYPE_FRAME_BEGIN_INFO};XR(xrBeginFrame(session,&begin));
        auto t=tracking(state.predictedDisplayTime);stream.publish(t);latch();
        bool draw=state.shouldRender&&hasImage&&Clock::now()-shownTime<std::chrono::milliseconds(250)&&t.headFlags==ql::Tracked;
        XrCompositionLayerProjectionView views[2]{{XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW},{XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW}};
        if(draw)resizeSwapchains(shown.width/2,shown.height);
        if(draw)for(int eye=0;eye<2;++eye){
            uint32_t index;XrSwapchainImageAcquireInfo acquire{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};XR(xrAcquireSwapchainImage(chains[eye],&acquire,&index));XrSwapchainImageWaitInfo w{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};w.timeout=XR_INFINITE_DURATION;XR(xrWaitSwapchainImage(chains[eye],&w));
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,images[eye][index].image,0);if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw std::runtime_error("Invalid VR framebuffer");
            glViewport(0,0,eyeWidth,eyeHeight);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glBindVertexArray(vao);GLuint p=programs[external?1:0];glUseProgram(p);glActiveTexture(GL_TEXTURE0);glBindTexture(external?GL_TEXTURE_EXTERNAL_OES:GL_TEXTURE_2D,external?externalTexture:texture);glUniform1i(glGetUniformLocation(p,"video"),0);glUniform1f(glGetUniformLocation(p,"eye"),float(eye));float identity[16]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};glUniformMatrix4fv(glGetUniformLocation(p,"texMatrix"),1,GL_FALSE,external?matrix:identity);glDrawArrays(GL_TRIANGLES,0,3);glFlush();
            XrSwapchainImageReleaseInfo release{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};XR(xrReleaseSwapchainImage(chains[eye],&release));
            // Preserve the PC render pose. Meta's compositor reprojects to current head pose.
            views[eye].pose=shown.eyes[eye].pose;views[eye].fov=shown.eyes[eye].fov;views[eye].subImage.swapchain=chains[eye];views[eye].subImage.imageRect.extent={int32_t(eyeWidth),int32_t(eyeHeight)};
        }
        XrCompositionLayerProjection layer{XR_TYPE_COMPOSITION_LAYER_PROJECTION};layer.space=stage;layer.viewCount=2;layer.views=views;const XrCompositionLayerBaseHeader* layers[]={reinterpret_cast<XrCompositionLayerBaseHeader*>(&layer)};
        XrFrameEndInfo end{XR_TYPE_FRAME_END_INFO};end.displayTime=state.predictedDisplayTime;end.environmentBlendMode=XR_ENVIRONMENT_BLEND_MODE_OPAQUE;end.layerCount=draw?1:0;end.layers=draw?layers:nullptr;XR(xrEndFrame(session,&end));
    }
    bool events(){XrEventDataBuffer event{XR_TYPE_EVENT_DATA_BUFFER};while(xrPollEvent(instance,&event)==XR_SUCCESS){
        if(event.type==XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING)return false;
        if(event.type==XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED){auto s=reinterpret_cast<XrEventDataSessionStateChanged*>(&event)->state;focused=s==XR_SESSION_STATE_FOCUSED;
            if(s==XR_SESSION_STATE_READY){XrSessionBeginInfo b{XR_TYPE_SESSION_BEGIN_INFO};b.primaryViewConfigurationType=XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;XR(xrBeginSession(session,&b));running=true;}
            if(s==XR_SESSION_STATE_STOPPING){XR(xrEndSession(session));running=false;}
            if(s==XR_SESSION_STATE_EXITING||s==XR_SESSION_STATE_LOSS_PENDING)return false;
        }event={XR_TYPE_EVENT_DATA_BUFFER};}return true;}
    ~Client(){
        stream.shutdownStream();if(stream.videoWindow)ANativeWindow_release(stream.videoWindow);
        if(env&&activityClass){auto method=env->GetMethodID(activityClass,"releaseVideoSurface","()V");env->CallVoidMethod(app->activity->clazz,method);if(env->ExceptionCheck())env->ExceptionClear();}
        for(auto s:gripSpace)if(s)xrDestroySpace(s);for(auto s:aimSpace)if(s)xrDestroySpace(s);if(head)xrDestroySpace(head);if(stage)xrDestroySpace(stage);for(auto s:chains)if(s)xrDestroySwapchain(s);if(session)xrDestroySession(session);if(actionSet)xrDestroyActionSet(actionSet);if(instance)xrDestroyInstance(instance);
        if(context!=EGL_NO_CONTEXT){glDeleteTextures(1,&texture);glDeleteTextures(1,&externalTexture);glDeleteFramebuffers(1,&fbo);glDeleteVertexArrays(1,&vao);for(auto p:programs)if(p)glDeleteProgram(p);eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);eglDestroyContext(display,context);}if(surface!=EGL_NO_SURFACE)eglDestroySurface(display,surface);if(display!=EGL_NO_DISPLAY)eglTerminate(display);
        if(env){if(textureMatrix)env->DeleteLocalRef(textureMatrix);if(activityClass)env->DeleteLocalRef(activityClass);app->activity->vm->DetachCurrentThread();}
    }
};
}
void android_main(android_app* app){
    app_dummy();
    try{
        Client client(app);client.init();
        while(!app->destroyRequested){int events;android_poll_source* source;while(ALooper_pollOnce(client.running?0:50,nullptr,&events,reinterpret_cast<void**>(&source))>=0){if(source)source->process(app,source);if(app->destroyRequested)break;}
            if(app->destroyRequested||!client.events())break;if(client.running)client.frame();
        }
    }catch(const std::exception& e){
        __android_log_print(ANDROID_LOG_ERROR,"QuestLink","%s",e.what());JNIEnv* env=nullptr;app->activity->vm->AttachCurrentThread(&env,nullptr);jclass cls=env->GetObjectClass(app->activity->clazz);auto method=env->GetMethodID(cls,"reportError","(Ljava/lang/String;)V");jstring message=env->NewStringUTF(e.what());env->CallVoidMethod(app->activity->clazz,method,message);env->DeleteLocalRef(message);env->DeleteLocalRef(cls);app->activity->vm->DetachCurrentThread();
    }
    ANativeActivity_finish(app->activity);
}
