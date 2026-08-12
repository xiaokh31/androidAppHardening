#include "risk_signals.hpp"

#include <array>
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <memory>
#include <new>
#include <limits>

#if defined(__linux__)
#include <fcntl.h>
#include <unistd.h>
#endif

namespace ah::risk {
namespace {

constexpr std::size_t kStatusLimit = 64U * 1024U;
// ART may exceed 512 KiB of mappings in Release/direct-loading processes on
// newer Android versions. Keep the read bounded while leaving enough room for
// late code-cache mappings that must still participate in risk classification.
constexpr std::size_t kMapsLimit = 2U * 1024U * 1024U;
constexpr std::uint32_t kDynamicInstrumentation = 1U;
constexpr std::uint32_t kRuntimeHook = 2U;
#if defined(__linux__)
constexpr std::uint64_t kCollectionBudgetNanos = 50U * 1000U * 1000U;

std::uint64_t monotonicNanos() noexcept {
    using Clock = std::chrono::steady_clock;
    const auto now = Clock::now().time_since_epoch();
    return static_cast<std::uint64_t>(
            std::chrono::duration_cast<std::chrono::nanoseconds>(now).count());
}
#endif

bool deadlineReached(MonotonicClock clock, std::uint64_t deadline) noexcept {
    return clock == nullptr || clock() >= deadline;
}

char asciiLower(char value) noexcept {
    return value >= 'A' && value <= 'Z' ? static_cast<char>(value + ('a' - 'A')) : value;
}

bool containsAscii(const char* bytes, std::size_t size, const char* needle) noexcept {
    const std::size_t needle_size = std::strlen(needle);
    if (bytes == nullptr || needle_size == 0 || needle_size > size) return false;
    for (std::size_t offset = 0; offset + needle_size <= size; ++offset) {
        bool match = true;
        for (std::size_t index = 0; index < needle_size; ++index) {
            if (asciiLower(bytes[offset + index]) != needle[index]) {
                match = false;
                break;
            }
        }
        if (match) return true;
    }
    return false;
}

void clearBytes(char* bytes, std::size_t size) noexcept {
    volatile char* cursor = bytes;
    while (cursor != nullptr && size-- > 0) *cursor++ = 0;
}

#if defined(__linux__)
bool readBounded(const char* path, char* output, std::size_t capacity,
                 std::size_t* written, std::uint64_t deadline,
                 MonotonicClock clock) noexcept {
    if (path == nullptr || output == nullptr || capacity == 0 || written == nullptr) return false;
    *written = 0;
    if (deadlineReached(clock, deadline)) return false;
    const int descriptor = open(path, O_RDONLY | O_CLOEXEC | O_NONBLOCK);
    if (descriptor < 0) return false;
    bool valid = true;
    while (*written < capacity) {
        if (deadlineReached(clock, deadline)) {
            valid = false;
            break;
        }
        const ssize_t count = read(descriptor, output + *written, capacity - *written);
        if (count == 0) break;
        if (count < 0) {
            valid = false;
            break;
        }
        *written += static_cast<std::size_t>(count);
    }
    if (valid && *written == capacity) {
        char extra{};
        valid = !deadlineReached(clock, deadline) && read(descriptor, &extra, 1) == 0;
    }
    if (close(descriptor) != 0) valid = false;
    return valid && !deadlineReached(clock, deadline);
}
#endif

}  // namespace

State parseTracerStatus(const char* bytes, std::size_t size) noexcept {
    if (bytes == nullptr || size == 0 || size > kStatusLimit) return State::kUnavailable;
    constexpr char kPrefix[] = "TracerPid:";
    for (std::size_t line = 0; line < size;) {
        std::size_t end = line;
        while (end < size && bytes[end] != '\n') ++end;
        const std::size_t length = end - line;
        if (length >= sizeof(kPrefix) - 1 &&
            std::memcmp(bytes + line, kPrefix, sizeof(kPrefix) - 1) == 0) {
            std::size_t cursor = line + sizeof(kPrefix) - 1;
            while (cursor < end && (bytes[cursor] == ' ' || bytes[cursor] == '\t')) ++cursor;
            if (cursor == end || bytes[cursor] < '0' || bytes[cursor] > '9') {
                return State::kUnavailable;
            }
            bool detected = false;
            while (cursor < end && bytes[cursor] >= '0' && bytes[cursor] <= '9') {
                detected = detected || bytes[cursor] != '0';
                ++cursor;
            }
            while (cursor < end && (bytes[cursor] == ' ' || bytes[cursor] == '\t' ||
                                     bytes[cursor] == '\r')) ++cursor;
            return cursor == end ? (detected ? State::kDetected : State::kClear)
                                 : State::kUnavailable;
        }
        line = end < size ? end + 1 : size;
    }
    return State::kUnavailable;
}

Collected parseMappings(const char* bytes, std::size_t size) noexcept {
    Collected result{};
    if (bytes == nullptr || size == 0 || size > kMapsLimit) return result;
    if (containsAscii(bytes, size, "frida") || containsAscii(bytes, size, "gadget")) {
        result.mapping_family_mask |= kDynamicInstrumentation;
    }
    if (containsAscii(bytes, size, "xposed") || containsAscii(bytes, size, "lsposed") ||
        containsAscii(bytes, size, "substrate") || containsAscii(bytes, size, "zygisk") ||
        containsAscii(bytes, size, "riru")) {
        result.mapping_family_mask |= kRuntimeHook;
    }
    result.mappings = result.mapping_family_mask == 0 ? State::kClear : State::kDetected;
    return result;
}

Collected collectWithDependencies(BoundedReader reader, MonotonicClock clock,
                                  std::uint64_t budget_nanos) noexcept {
    Collected result{};
    if (reader == nullptr || clock == nullptr || budget_nanos == 0) return result;
    const std::uint64_t started = clock();
    const std::uint64_t deadline = budget_nanos > std::numeric_limits<std::uint64_t>::max() - started
            ? std::numeric_limits<std::uint64_t>::max()
            : started + budget_nanos;
    std::array<char, kStatusLimit> status{};
    std::size_t status_size = 0;
    if (reader("/proc/self/status", status.data(), status.size(), &status_size, deadline, clock)) {
        result.tracer = parseTracerStatus(status.data(), status_size);
    }
    clearBytes(status.data(), status.size());

    if (deadlineReached(clock, deadline)) return Collected{};

    std::unique_ptr<char[]> maps(new (std::nothrow) char[kMapsLimit]);
    if (maps != nullptr) {
        std::size_t maps_size = 0;
        if (reader("/proc/self/maps", maps.get(), kMapsLimit, &maps_size, deadline, clock)) {
            Collected parsed = parseMappings(maps.get(), maps_size);
            result.mappings = parsed.mappings;
            result.mapping_family_mask = parsed.mapping_family_mask;
        }
        clearBytes(maps.get(), kMapsLimit);
    }
    return deadlineReached(clock, deadline) ? Collected{} : result;
}

Collected collectCurrentProcess() noexcept {
#if defined(__linux__)
    return collectWithDependencies(readBounded, monotonicNanos, kCollectionBudgetNanos);
#else
    return Collected{};
#endif
}

}  // namespace ah::risk
