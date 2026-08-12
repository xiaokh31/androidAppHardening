#include "risk_signals.hpp"

#include <cstdlib>
#include <iostream>
#include <string>

namespace {

void require(bool condition, const char* label) {
    if (!condition) {
        std::cerr << "M2-05 risk signal test failed: " << label << '\n';
        std::exit(1);
    }
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
    std::string maps_overlong(512U * 1024U + 1U, 'x');
    require(ah::risk::parseMappings(maps_overlong.data(), maps_overlong.size()).mappings ==
                    State::kUnavailable,
            "maps-overlong");
#if defined(__linux__)
    const ah::risk::Collected current = ah::risk::collectCurrentProcess();
    require(current.tracer != State::kUnavailable, "current-tracer-readable");
    require(current.mappings != State::kUnavailable, "current-maps-readable");
#endif
    std::cout << "M2-05 native risk signal matrix PASS cases=10\n";
    return 0;
}
