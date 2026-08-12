#pragma once

#include <cstddef>
#include <cstdint>

namespace ah::risk {

enum class State : std::int32_t {
    kUnavailable = 0,
    kClear = 1,
    kDetected = 2,
};

struct Collected final {
    State tracer{State::kUnavailable};
    State mappings{State::kUnavailable};
    std::uint32_t mapping_family_mask{};
};

State parseTracerStatus(const char* bytes, std::size_t size) noexcept;
Collected parseMappings(const char* bytes, std::size_t size) noexcept;
Collected collectCurrentProcess() noexcept;

}  // namespace ah::risk
