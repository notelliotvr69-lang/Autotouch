#include "protocol.h"
#include <cassert>
#include <iostream>
#include <limits>
int main(){
    ql::Tracking t;t.sequence=0x01020304;t.focused=1;t.headFlags=3;t.head.position={1,2,3};t.hands[0].trigger=.75f;t.hands[1].buttons=ql::Primary;
    auto p=ql::encode(t);assert(p.size()==ql::TrackingBytes);assert(p[0]=='Q'&&p[7]==4);
    auto v=ql::decodeTracking(p.data(),p.size());assert(v.sequence==t.sequence&&v.head.position.y==2&&v.hands[0].trigger==.75f&&v.hands[1].buttons==ql::Primary);
    for(size_t i=0;i<p.size();++i){bool rejected=false;try{ql::decodeTracking(p.data(),i);}catch(...){rejected=true;}assert(rejected);}
    t.head.position.x=std::numeric_limits<float>::quiet_NaN();p=ql::encode(t);bool rejected=false;try{ql::decodeTracking(p.data(),p.size());}catch(...){rejected=true;}assert(rejected);
    ql::VideoHeader h;h.bytes=123;h.id=0x0123456789abcdef;auto b=ql::encode(h);assert(b.size()==ql::VideoHeaderBytes);auto decoded=ql::decodeVideo(b.data(),b.size());assert(decoded.id==h.id&&decoded.bytes==123);
    h.bytes=ql::MaxVideoBytes+1;b=ql::encode(h);rejected=false;try{ql::decodeVideo(b.data(),b.size());}catch(...){rejected=true;}assert(rejected);
    auto a=ql::Identity;a.orientation={0,0,.70710678f,.70710678f};a.position={1,2,3};auto point=ql::Identity;point.position={1,0,0};auto world=ql::compose(a,point);assert(std::abs(world.position.x-1)<1e-5&&std::abs(world.position.y-3)<1e-5);
    auto relative=ql::compose(ql::inverse(a),world);assert(std::abs(relative.position.x-1)<1e-5&&std::abs(relative.position.y)<1e-5);
    std::cout<<"Protocol bounds, endian, tracking, frame metadata and pose transforms passed\n";
}
