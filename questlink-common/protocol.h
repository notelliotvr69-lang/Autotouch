#pragma once
#include <openxr/openxr.h>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <stdexcept>
#include <vector>

// Protocol 2: explicit network byte order; never send native C++ structs.
namespace ql {
constexpr uint32_t TrackingMagic = 0x514c5432; // QLT2
constexpr uint32_t VideoMagic = 0x514c4632;    // QLF2
constexpr uint32_t HelloMagic = 0x514c4332;    // QLC2
constexpr size_t TrackingBytes = 300;
constexpr size_t VideoHeaderBytes = 116;
constexpr uint32_t MaxVideoBytes = 16 * 1024 * 1024;
constexpr XrPosef Identity{{0,0,0,1},{0,0,0}};
constexpr uint32_t Tracked = 3; // orientation + position valid/tracked
enum Button : uint32_t { Primary=1, Secondary=2, Stick=4, Menu=8,
    PrimaryTouch=16, SecondaryTouch=32, StickTouch=64, TriggerTouch=128, ThumbrestTouch=256 };
struct Hand {
    XrPosef grip=Identity, aim=Identity;
    uint32_t gripFlags=0, aimFlags=0, buttons=0;
    float trigger=0, squeeze=0;
    XrVector2f stick{};
};
struct Eye { XrPosef pose=Identity; XrFovf fov{-0.9f,0.9f,0.9f,-0.9f}; };
struct Tracking {
    uint32_t sequence=0, headFlags=0, focused=0;
    XrPosef head=Identity;
    std::array<Eye,2> eyes;
    std::array<Hand,2> hands;
};
struct VideoHeader {
    uint32_t width=1280, height=672, codec=1, bytes=0; // 1=JPEG, 2=Annex-B H264
    uint64_t id=0;
    std::array<Eye,2> eyes;
};
struct Writer {
    std::vector<uint8_t> data;
    void u32(uint32_t n) { for(int i=3;i>=0;--i) data.push_back(uint8_t(n>>(8*i))); }
    void u64(uint64_t n) { u32(uint32_t(n>>32)); u32(uint32_t(n)); }
    void f(float v) { uint32_t n; memcpy(&n,&v,4); u32(n); }
    void pose(const XrPosef& p) { f(p.orientation.x);f(p.orientation.y);f(p.orientation.z);f(p.orientation.w); f(p.position.x);f(p.position.y);f(p.position.z); }
    void eye(const Eye& e) {pose(e.pose); f(e.fov.angleLeft); f(e.fov.angleRight); f(e.fov.angleUp); f(e.fov.angleDown);}
};
struct Reader {
    const uint8_t* p; size_t n;
    uint32_t u32() { if(n<4) throw std::runtime_error("Truncated packet"); uint32_t v=0; for(int i=0;i<4;++i) v=(v<<8)|*p++; n-=4; return v; }
    uint64_t u64() {uint64_t a=u32(); return (a<<32)|u32();}
    float f() {uint32_t v=u32(); float x;memcpy(&x,&v,4);if(!std::isfinite(x))throw std::runtime_error("Nonfinite tracking");return x;}
    XrPosef pose() {
        XrPosef v; v.orientation={f(),f(),f(),f()}; v.position={f(),f(),f()};
        auto& q=v.orientation;float l=std::sqrt(q.x*q.x+q.y*q.y+q.z*q.z+q.w*q.w);
        if(l<0.5f||l>1.5f||std::abs(v.position.x)>1000||std::abs(v.position.y)>1000||std::abs(v.position.z)>1000)throw std::runtime_error("Invalid pose");
        q.x/=l;q.y/=l;q.z/=l;q.w/=l;return v;
    }
    Eye eye() {Eye e;e.pose=pose();e.fov={f(),f(),f(),f()}; if(e.fov.angleLeft>=e.fov.angleRight||e.fov.angleDown>=e.fov.angleUp||std::abs(e.fov.angleLeft)>1.57f||std::abs(e.fov.angleRight)>1.57f||std::abs(e.fov.angleUp)>1.57f||std::abs(e.fov.angleDown)>1.57f)throw std::runtime_error("Invalid FOV");return e;}
};
inline std::vector<uint8_t> encode(const Tracking& t) {
    Writer w;w.u32(TrackingMagic);w.u32(t.sequence);w.u32(t.headFlags);w.u32(t.focused);w.pose(t.head);
    for(auto& e:t.eyes)w.eye(e);
    for(auto& h:t.hands){w.pose(h.grip);w.pose(h.aim);w.u32(h.gripFlags);w.u32(h.aimFlags);w.u32(h.buttons);w.f(h.trigger);w.f(h.squeeze);w.f(h.stick.x);w.f(h.stick.y);}
    return w.data;
}
inline Tracking decodeTracking(const uint8_t* p,size_t n) {
    if(n!=TrackingBytes)throw std::runtime_error("Invalid tracking length");Reader r{p,n};
    if(r.u32()!=TrackingMagic)throw std::runtime_error("Tracking version mismatch");
    Tracking t;t.sequence=r.u32();t.headFlags=r.u32();t.focused=r.u32();t.head=r.pose();for(auto& e:t.eyes)e=r.eye();
    for(auto& h:t.hands){h.grip=r.pose();h.aim=r.pose();h.gripFlags=r.u32();h.aimFlags=r.u32();h.buttons=r.u32();h.trigger=r.f();h.squeeze=r.f();h.stick={r.f(),r.f()};if(h.trigger<0||h.trigger>1||h.squeeze<0||h.squeeze>1||std::abs(h.stick.x)>1||std::abs(h.stick.y)>1)throw std::runtime_error("Invalid input range");}
    return t;
}
inline std::vector<uint8_t> encode(const VideoHeader& v) {
    Writer w;w.u32(VideoMagic);w.u32(v.width);w.u32(v.height);w.u64(v.id);w.u32(v.codec);w.u32(v.bytes);for(auto& e:v.eyes)w.eye(e);return w.data;
}
inline VideoHeader decodeVideo(const uint8_t* p,size_t n) {
    if(n!=VideoHeaderBytes)throw std::runtime_error("Invalid video header");Reader r{p,n};if(r.u32()!=VideoMagic)throw std::runtime_error("Runtime upgrade required");
    VideoHeader v;v.width=r.u32();v.height=r.u32();v.id=r.u64();v.codec=r.u32();v.bytes=r.u32();for(auto& e:v.eyes)e=r.eye();
    if(v.width==0||v.width>8192||v.width%2||v.height==0||v.height>4096||v.bytes==0||v.bytes>MaxVideoBytes||(v.codec!=1&&v.codec!=2))throw std::runtime_error("Invalid video dimensions/codec/length");return v;
}
inline XrQuaternionf mul(XrQuaternionf a,XrQuaternionf b) {return {a.w*b.x+a.x*b.w+a.y*b.z-a.z*b.y,a.w*b.y-a.x*b.z+a.y*b.w+a.z*b.x,a.w*b.z+a.x*b.y-a.y*b.x+a.z*b.w,a.w*b.w-a.x*b.x-a.y*b.y-a.z*b.z};}
inline XrVector3f rotate(XrQuaternionf q,XrVector3f v) {auto p=mul(mul(q,{v.x,v.y,v.z,0}),{-q.x,-q.y,-q.z,q.w});return {p.x,p.y,p.z};}
inline XrPosef compose(XrPosef a,XrPosef b) {auto v=rotate(a.orientation,b.position);return {mul(a.orientation,b.orientation),{a.position.x+v.x,a.position.y+v.y,a.position.z+v.z}};}
inline XrPosef inverse(XrPosef p) {XrQuaternionf q{-p.orientation.x,-p.orientation.y,-p.orientation.z,p.orientation.w};return {q,rotate(q,{-p.position.x,-p.position.y,-p.position.z})};}
}
