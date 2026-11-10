#pragma once
#include <cstdint>
#include <vector>
#include <mftransform.h>

// Hardware Media Foundation H.264 encoder. Input is tightly packed BGRA;
// output is an Annex-B H.264 access unit (SPS/PPS are emitted by the MFT).
class QuestLinkH264Encoder {
public:
    QuestLinkH264Encoder() = default;
    ~QuestLinkH264Encoder();
    bool initialize(uint32_t width, uint32_t height, uint32_t fps = 45, uint32_t bitrate = 8000000);
    bool encode(const uint8_t* bgra, uint32_t width, uint32_t height,
                int64_t timestamp100ns, std::vector<uint8_t>& accessUnit);
    void shutdown();
private:
    IMFTransform* transform_ = nullptr;
    uint32_t width_ = 0, height_ = 0;
    uint64_t frameIndex_ = 0;
    bool mfStarted_ = false;
    bool comInitialized_ = false;
};
