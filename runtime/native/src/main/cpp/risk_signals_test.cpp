#include "risk_signals.hpp"

#include <cstdlib>
#include <iostream>
#include <string>
#include <cstring>

namespace {

void require(bool condition, const char* label) {
    if (!condition) {
        std::cerr << "M2-05 risk signal test failed: " << label << '\n';
        std::exit(1);
    }
}

std::uint64_t fake_now{};

std::uint64_t fakeClock() noexcept {
    return fake_now;
}

bool unreadable(const char*, char*, std::size_t, std::size_t*, std::uint64_t,
                ah::risk::MonotonicClock) noexcept {
    return false;
}

bool fixedReader(const char* path, char* output, std::size_t capacity, std::size_t* written,
                 std::uint64_t, ah::risk::MonotonicClock) noexcept {
    const char* value = std::strstr(path, "status") == nullptr
            ? "1000-2000 r--p 0000 00:00 0 /data/frida-agent.so\n"
            : "Name:\ta\nTracerPid:\t0\n";
    const std::size_t size = std::strlen(value);
    if (size > capacity) return false;
    std::memcpy(output, value, size);
    *written = size;
    return true;
}

bool partialReadFailure(const char*, char* output, std::size_t capacity, std::size_t* written,
                        std::uint64_t, ah::risk::MonotonicClock) noexcept {
    if (capacity == 0) return false;
    output[0] = 'x';
    *written = 1;
    return false;
}

bool forcedTimeout(const char*, char*, std::size_t, std::size_t*, std::uint64_t deadline,
                   ah::risk::MonotonicClock) noexcept {
    fake_now = deadline;
    return false;
}

}  // namespace

int main() {
    using ah::risk::State;
    const std::string untraced = "Name:\ta\nTracerPid:\t0\n";
    require(ah::risk::parseTracerStatus(untraced.data(), untraced.size()) == State::kClear,
            "tracer-clear");
    const std::string traced = "Name:\ta\nTracerPid:\t123\n";
    require(ah::risk::parseTracerStatus(traced.data(), traced.size()) == State::kDetected,
            "tracer-hit");
    const std::string malformed = "TracerPid: nope\n";
    require(ah::risk::parseTracerStatus(malformed.data(), malformed.size()) == State::kUnavailable,
            "tracer-malformed");
    std::string overlong(64U * 1024U + 1U, 'x');
    require(ah::risk::parseTracerStatus(overlong.data(), overlong.size()) == State::kUnavailable,
            "tracer-overlong");

    const std::string clear = "1000-2000 r--p 0000 00:00 0 /system/lib/libc.so\n";
    require(ah::risk::parseMappings(clear.data(), clear.size()).mappings == State::kClear,
            "maps-clear");
    const std::string duplicate = "/data/frida-agent.so\n/data/FRIDA-gadget.so\n";
    require(ah::risk::parseMappings(duplicate.data(), duplicate.size()).mapping_family_mask == 1U,
            "family-dedup");
    const std::string two = "/data/frida-agent.so\n/data/lsposed-module.so\n";
    require(ah::risk::parseMappings(two.data(), two.size()).mapping_family_mask == 3U,
            "family-two-cap");
    std::string maps_larger_than_legacy_limit(600U * 1024U, 'x');
    maps_larger_than_legacy_limit += "\n7f00-7f10 r--p 0 00:00 0 libfrida-agent.so\n";
    maps_larger_than_legacy_limit += "7f10-7f20 r--p 0 00:00 0 libxposed.so\n";
    require(ah::risk::parseMappings(
                    maps_larger_than_legacy_limit.data(),
                    maps_larger_than_legacy_limit.size()).mapping_family_mask == 3U,
            "late mapping aliases beyond legacy limit");

    std::string maps_overlong(2U * 1024U * 1024U + 1U, 'x');
    require(ah::risk::parseMappings(maps_overlong.data(), maps_overlong.size()).mappings ==
                    State::kUnavailable,
            "maps-overlong");
    fake_now = 1;
    const ah::risk::Collected unreadable_result =
            ah::risk::collectWithDependencies(unreadable, fakeClock, 50'000'000U);
    require(unreadable_result.tracer == State::kUnavailable &&
                    unreadable_result.mappings == State::kUnavailable &&
                    unreadable_result.mapping_family_mask == 0,
            "unreadable-unavailable");
    fake_now = 1;
    const ah::risk::Collected read_failure =
            ah::risk::collectWithDependencies(partialReadFailure, fakeClock, 50'000'000U);
    require(read_failure.tracer == State::kUnavailable &&
                    read_failure.mappings == State::kUnavailable &&
                    read_failure.mapping_family_mask == 0,
            "read-failure-unavailable");
    fake_now = 1;
    const ah::risk::Collected injected =
            ah::risk::collectWithDependencies(fixedReader, fakeClock, 50'000'000U);
    require(injected.tracer == State::kClear && injected.mappings == State::kDetected &&
                    injected.mapping_family_mask == 1U,
            "injected-reader");
    fake_now = 1;
    const ah::risk::Collected timed_out =
            ah::risk::collectWithDependencies(forcedTimeout, fakeClock, 50'000'000U);
    require(timed_out.tracer == State::kUnavailable &&
                    timed_out.mappings == State::kUnavailable &&
                    timed_out.mapping_family_mask == 0,
            "forced-timeout-unavailable");
#if defined(__linux__)
    const ah::risk::Collected current = ah::risk::collectCurrentProcess();
    require(current.tracer != State::kUnavailable, "current-tracer-readable");
    require(current.mappings != State::kUnavailable, "current-maps-readable");
#endif
    std::cout << "M2-05 native risk signal matrix PASS cases=14\n";
    return 0;
}
