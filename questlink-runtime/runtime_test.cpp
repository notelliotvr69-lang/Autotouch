#include "runtime.cpp"
#include <cassert>
#include <iostream>
int main(){
    XrInstanceCreateInfo info{XR_TYPE_INSTANCE_CREATE_INFO};info.applicationInfo.apiVersion=XR_MAKE_VERSION(1,0,0);XrInstance instance;assert(ql_xrCreateInstance(&info,&instance)==XR_SUCCESS);
    SessionState state;state.running=true;XrSession session=reinterpret_cast<XrSession>(&state);g_sessions[session]=&state;
    XrPath left,right,profile,binding;ql_xrStringToPath(instance,"/user/hand/left",&left);ql_xrStringToPath(instance,"/user/hand/right",&right);ql_xrStringToPath(instance,"/interaction_profiles/oculus/touch_controller",&profile);ql_xrStringToPath(instance,"/user/hand/left/input/trigger/value",&binding);
    XrActionSetCreateInfo setInfo{XR_TYPE_ACTION_SET_CREATE_INFO};XrActionSet set;assert(ql_xrCreateActionSet(instance,&setInfo,&set)==XR_SUCCESS);
    XrPath paths[]={left,right};XrActionCreateInfo actionInfo{XR_TYPE_ACTION_CREATE_INFO};actionInfo.actionType=XR_ACTION_TYPE_FLOAT_INPUT;actionInfo.countSubactionPaths=2;actionInfo.subactionPaths=paths;XrAction trigger;assert(ql_xrCreateAction(set,&actionInfo,&trigger)==XR_SUCCESS);
    XrAction leftPose,rightPose,button;
    XrActionCreateInfo poseInfo{XR_TYPE_ACTION_CREATE_INFO};poseInfo.actionType=XR_ACTION_TYPE_POSE_INPUT;
    assert(ql_xrCreateAction(set,&poseInfo,&leftPose)==XR_SUCCESS);assert(ql_xrCreateAction(set,&poseInfo,&rightPose)==XR_SUCCESS);
    poseInfo.actionType=XR_ACTION_TYPE_BOOLEAN_INPUT;assert(ql_xrCreateAction(set,&poseInfo,&button)==XR_SUCCESS);
    XrPath lp,rp,rb;ql_xrStringToPath(instance,"/user/hand/left/input/grip/pose",&lp);ql_xrStringToPath(instance,"/user/hand/right/input/grip/pose",&rp);ql_xrStringToPath(instance,"/user/hand/right/input/a/click",&rb);
    XrActionSuggestedBinding bindings[]={{trigger,binding},{leftPose,lp},{rightPose,rp},{button,rb}};
    XrActionSuggestedBinding bind{trigger,binding};XrInteractionProfileSuggestedBinding suggestion{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};suggestion.interactionProfile=profile;suggestion.countSuggestedBindings=4;suggestion.suggestedBindings=bindings;assert(ql_xrSuggestInteractionProfileBindings(instance,&suggestion)==XR_SUCCESS);
    XrSessionActionSetsAttachInfo attach{XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO};attach.countActionSets=1;attach.actionSets=&set;assert(ql_xrAttachSessionActionSets(session,&attach)==XR_SUCCESS);
    XrEventDataBuffer event{XR_TYPE_EVENT_DATA_BUFFER};
    assert(ql_xrPollEvent(instance,&event)==XR_SUCCESS);
    assert(event.type==XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED);
    assert(reinterpret_cast<XrEventDataInteractionProfileChanged*>(&event)->session==session);
    assert(ql_xrPollEvent(instance,&event)==XR_EVENT_UNAVAILABLE);
    XrInteractionProfileState selected{XR_TYPE_INTERACTION_PROFILE_STATE};
    assert(ql_xrGetCurrentInteractionProfile(session,left,&selected)==XR_SUCCESS&&selected.interactionProfile==profile);
    assert(ql_xrGetCurrentInteractionProfile(session,right,&selected)==XR_SUCCESS&&selected.interactionProfile==profile);
    XrActionSpaceCreateInfo poseSpace{XR_TYPE_ACTION_SPACE_CREATE_INFO};poseSpace.poseInActionSpace=ql::Identity;
    XrSpace leftSpace,rightSpace;poseSpace.action=leftPose;assert(ql_xrCreateActionSpace(session,&poseSpace,&leftSpace)==XR_SUCCESS);
    poseSpace.action=rightPose;assert(ql_xrCreateActionSpace(session,&poseSpace,&rightSpace)==XR_SUCCESS);
    g_tracking.hands[0].gripFlags=3;g_tracking.hands[1].gripFlags=3;
    g_tracking.hands[0].grip.position={-.3f,1.2f,-.4f};g_tracking.hands[1].grip.position={.3f,1.2f,-.4f};g_tracking.hands[1].buttons=ql::Primary;
    g_clientConnected=true;g_tracking.focused=1;g_tracking.headFlags=3;g_tracking.head=ql::Identity;g_tracking.head.position={1,2,3};g_tracking.hands[0].trigger=.8f;g_trackingTime=std::chrono::steady_clock::now();
    XrActiveActionSet active{set,XR_NULL_PATH};XrActionsSyncInfo sync{XR_TYPE_ACTIONS_SYNC_INFO};sync.countActiveActionSets=1;sync.activeActionSets=&active;assert(ql_xrSyncActions(session,&sync)==XR_SUCCESS);
    XrActionStateGetInfo get{XR_TYPE_ACTION_STATE_GET_INFO};get.action=trigger;get.subactionPath=left;XrActionStateFloat value{XR_TYPE_ACTION_STATE_FLOAT};assert(ql_xrGetActionStateFloat(session,&get,&value)==XR_SUCCESS);assert(value.isActive&&value.currentState==.8f&&!value.changedSinceLastSync);
    g_tracking.hands[0].trigger=.2f;ql_xrSyncActions(session,&sync);ql_xrGetActionStateFloat(session,&get,&value);assert(value.currentState==.2f&&value.changedSinceLastSync);ql_xrGetActionStateFloat(session,&get,&value);assert(value.changedSinceLastSync);ql_xrSyncActions(session,&sync);ql_xrGetActionStateFloat(session,&get,&value);assert(!value.changedSinceLastSync);
    get.subactionPath=right;ql_xrGetActionStateFloat(session,&get,&value);assert(!value.isActive&&value.currentState==0);
    XrReferenceSpaceCreateInfo space{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};space.referenceSpaceType=XR_REFERENCE_SPACE_TYPE_LOCAL;space.poseInReferenceSpace=ql::Identity;space.poseInReferenceSpace.position={1,0,0};XrSpace base,view;ql_xrCreateReferenceSpace(session,&space,&base);space.referenceSpaceType=XR_REFERENCE_SPACE_TYPE_VIEW;space.poseInReferenceSpace=ql::Identity;ql_xrCreateReferenceSpace(session,&space,&view);
    XrSpaceLocation location{XR_TYPE_SPACE_LOCATION};assert(ql_xrLocateSpace(view,base,1,&location)==XR_SUCCESS);assert(location.locationFlags&&location.pose.position.x==0&&location.pose.position.y==2);
    XrSpaceLocation handLocation{XR_TYPE_SPACE_LOCATION};
    assert(ql_xrLocateSpace(leftSpace,base,1,&handLocation)==XR_SUCCESS&&handLocation.locationFlags);
    assert(std::abs(handLocation.pose.position.x+1.3f)<1e-5f);
    assert(ql_xrLocateSpace(rightSpace,base,1,&handLocation)==XR_SUCCESS&&handLocation.locationFlags);
    assert(std::abs(handLocation.pose.position.x+.7f)<1e-5f);
    XrActionStateGetInfo buttonGet{XR_TYPE_ACTION_STATE_GET_INFO};buttonGet.action=button;
    XrActionStateBoolean buttonState{XR_TYPE_ACTION_STATE_BOOLEAN};assert(ql_xrGetActionStateBoolean(session,&buttonGet,&buttonState)==XR_SUCCESS&&buttonState.isActive&&buttonState.currentState);
    XrBoundSourcesForActionEnumerateInfo bound{XR_TYPE_BOUND_SOURCES_FOR_ACTION_ENUMERATE_INFO};bound.action=trigger;uint32_t count=0;assert(ql_xrEnumerateBoundSourcesForAction(session,&bound,0,&count,nullptr)==XR_SUCCESS&&count==1);XrPath source;assert(ql_xrEnumerateBoundSourcesForAction(session,&bound,1,&count,&source)==XR_SUCCESS&&source==binding);
    g_trackingTime-=std::chrono::seconds(1);assert(ql_xrSyncActions(session,&sync)==XR_SESSION_NOT_FOCUSED);get.subactionPath=left;ql_xrGetActionStateFloat(session,&get,&value);assert(!value.isActive&&value.currentState==0);ql_xrLocateSpace(view,base,1,&location);assert(location.locationFlags==0);
    ql_xrLocateSpace(leftSpace,base,1,&handLocation);assert(handLocation.locationFlags==0);
    ql_xrGetActionStateBoolean(session,&buttonGet,&buttonState);assert(!buttonState.isActive&&!buttonState.currentState);
    XrFrameState frame{XR_TYPE_FRAME_STATE};auto before=std::chrono::steady_clock::now();for(int i=0;i<4;++i)assert(ql_xrWaitFrame(session,nullptr,&frame)==XR_SUCCESS);assert(std::chrono::steady_clock::now()-before>=std::chrono::milliseconds(35));assert(!frame.shouldRender);
    bool videoPassed=false;
    for(bool hardware:{true,false}){
        ql::H264Encoder encoder;
        if(!encoder.open(3360,1760,72,36000000,hardware)){std::cout<<"Encoder initialization failed"<<std::endl;continue;}
        std::cout<<(encoder.hardware()?"Testing hardware H264":"Testing software H264")<<std::endl;
        std::vector<uint8_t> pixels(3360*1760*4,128),encoded;
        auto start=std::chrono::steady_clock::now();bool ok=true;
        for(int frame=0;frame<8;++frame){if(!encoder.encode(pixels,encoded)||encoded.size()<8||encoded[0]!=0||encoded[1]!=0||(encoded[2]!=1&&(encoded[2]!=0||encoded[3]!=1))){ok=false;break;}}
        if(!ok){std::cout<<"Encoder failed; trying software fallback: "<<encoder.error()<<std::endl;continue;}
        std::cout<<"8 frames passed in "<<std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now()-start).count()<<" ms"<<std::endl;videoPassed=true;break;
    }
    if(!videoPassed){std::cerr<<"H264 unavailable on this machine; runtime can only use JPEG fallback"<<std::endl;return 2;}
    ql_xrDestroySpace(leftSpace);ql_xrDestroySpace(rightSpace);ql_xrDestroyAction(leftPose);ql_xrDestroyAction(rightPose);ql_xrDestroyAction(button);
    ql_xrDestroySpace(view);ql_xrDestroySpace(base);ql_xrDestroyAction(trigger);ql_xrDestroyActionSet(set);g_sessions.clear();ql_xrDestroyInstance(instance);
    std::cout<<"Runtime input, tracking-loss, relative space, pacing and H264 smoke checks passed\n";
}
