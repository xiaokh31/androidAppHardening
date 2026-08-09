#ifndef AH_RUNTIME_ZIP_ASSETS_HPP
#define AH_RUNTIME_ZIP_ASSETS_HPP

#include "container_format.hpp"

#include <cstdint>

namespace ah::zip {

enum class Status : std::uint8_t {
    kSuccess = 0,
    kInvalidArgument = 1,
    kFormat = 2,
    kUnsupported = 3,
    kMissing = 4,
    kDuplicate = 5,
    kCrcMismatch = 6,
};

struct FixedAssets {
    container::ByteView config;
    container::ByteView payload;
};

Status locateFixedAssets(container::ByteView apk, FixedAssets* output) noexcept;

}  // namespace ah::zip

#endif
