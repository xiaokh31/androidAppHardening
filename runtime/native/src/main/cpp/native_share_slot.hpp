#ifndef AH_RUNTIME_NATIVE_SHARE_SLOT_HPP
#define AH_RUNTIME_NATIVE_SHARE_SLOT_HPP

#include "container_format.hpp"

#include <cstdint>

namespace ah::share {

container::ByteView currentSlot() noexcept;
std::uint16_t currentAbiId() noexcept;

}  // namespace ah::share

#endif
