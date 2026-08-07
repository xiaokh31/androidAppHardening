#ifndef AH_RUNTIME_MAPPED_APK_HPP
#define AH_RUNTIME_MAPPED_APK_HPP

#include "container_format.hpp"

#include <cstdint>

namespace ah::apk {

enum class Status : std::uint8_t {
    kSuccess = 0,
    kInvalidArgument = 1,
    kOpenFailed = 2,
    kNotRegular = 3,
    kLength = 4,
    kMapFailed = 5,
    kCleanupFailed = 6,
};

class ReadOnlyMapping final {
public:
    ReadOnlyMapping() noexcept = default;
    ~ReadOnlyMapping() noexcept;
    ReadOnlyMapping(const ReadOnlyMapping&) = delete;
    ReadOnlyMapping& operator=(const ReadOnlyMapping&) = delete;

    Status openAbsolute(const char* absolute_path) noexcept;
    Status close() noexcept;
    container::ByteView bytes() const noexcept { return {data_, size_}; }

private:
    const std::uint8_t* data_{};
    std::size_t size_{};
    int descriptor_{-1};
};

}  // namespace ah::apk

#endif
