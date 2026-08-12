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

using MonotonicClock = std::uint64_t (*)() noexcept;
using BoundedReader = bool (*)(const char*, char*, std::size_t, std::size_t*,
                               std::uint64_t, MonotonicClock) noexcept;

State parseTracerStatus(const char* bytes, std::size_t size) noexcept;
Collected parseMappings(const char* bytes, std::size_t size) noexcept;
Collected collectWithDependencies(BoundedReader reader, MonotonicClock clock,
                                  std::uint64_t budget_nanos) noexcept;
Collected collectCurrentProcess() noexcept;

}  // namespace ah::risk
