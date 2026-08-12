#ifndef AH_RUNTIME_MEMORY_CONTROLS_HPP
#define AH_RUNTIME_MEMORY_CONTROLS_HPP

#include <cstddef>
#include <cstdint>

namespace ah::memory {

inline constexpr std::size_t kMaximumLockedBytes = 1024U * 1024U;
inline constexpr std::size_t kDexEdgeBytes = 64U * 1024U;

struct LockedRegion {
    std::uint8_t* data{};
    std::size_t size{};
};

bool lockRegionBestEffort(
    std::uint8_t* data, std::size_t size, LockedRegion* output) noexcept;
void unlockRegion(LockedRegion* region) noexcept;
bool adviseDontDump(std::uint8_t* data, std::size_t size) noexcept;
bool currentProcessDumpable(bool* output) noexcept;
bool disableCurrentProcessDumping() noexcept;
bool applyHighRiskJitter(std::uint32_t* applied_milliseconds) noexcept;
std::size_t lockedBytesForTesting() noexcept;
#if defined(AH_M2_02_HOST_TESTING)
void setMemoryControlUnavailableForTesting(bool unavailable) noexcept;
#endif

}  // namespace ah::memory

#endif
