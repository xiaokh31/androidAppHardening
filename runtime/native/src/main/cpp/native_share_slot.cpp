#include "native_share_slot.hpp"

#include <array>
#include <cstddef>
#include <cstdint>

namespace {

#if defined(__arm__) && !defined(__aarch64__)
constexpr std::uint16_t kAbiId = 1;
#elif defined(__aarch64__)
constexpr std::uint16_t kAbiId = 2;
#elif defined(__i386__)
constexpr std::uint16_t kAbiId = 3;
#elif defined(__x86_64__)
constexpr std::uint16_t kAbiId = 4;
#else
#error "Unsupported Android runtime ABI"
#endif

constexpr std::array<std::uint8_t, ah::container::kNativeShareSlotBytes> makePlaceholder() noexcept {
    std::array<std::uint8_t, ah::container::kNativeShareSlotBytes> result{};
    result[0] = 'A';
    result[1] = 'H';
    result[2] = 'P';
    result[3] = '0';
    result[4] = 1;
    result[6] = static_cast<std::uint8_t>(kAbiId);
    result[7] = static_cast<std::uint8_t>(kAbiId >> 8U);
    return result;
}

extern "C" __attribute__((used, section(".ah_share_v1"), visibility("hidden")))
const std::array<std::uint8_t, ah::container::kNativeShareSlotBytes> ah_native_share_slot =
    makePlaceholder();

}  // namespace

namespace ah::share {

container::ByteView currentSlot() noexcept {
    return {ah_native_share_slot.data(), ah_native_share_slot.size()};
}

std::uint16_t currentAbiId() noexcept {
    return kAbiId;
}

}  // namespace ah::share
