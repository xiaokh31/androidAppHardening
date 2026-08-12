#include "memory_controls.hpp"

#include <atomic>
#include <cerrno>
#include <cstdint>
#include <limits>

#if defined(_WIN32)
#define NOMINMAX
#include <windows.h>
#else
#include <sys/mman.h>
#include <sys/prctl.h>
#include <sys/random.h>
#include <time.h>
#include <unistd.h>
#endif

namespace ah::memory {
namespace {

std::atomic<std::size_t> g_locked_bytes{0};
#if defined(AH_M2_02_HOST_TESTING)
std::atomic<bool> g_force_unavailable{false};
#endif

std::size_t pageSize() noexcept {
#if defined(_WIN32)
    SYSTEM_INFO information{};
    GetSystemInfo(&information);
    return information.dwPageSize == 0 ? 4096U : information.dwPageSize;
#else
    const long result = sysconf(_SC_PAGESIZE);
    return result <= 0 ? 4096U : static_cast<std::size_t>(result);
#endif
}

bool roundedRegion(
    std::uint8_t* data,
    std::size_t size,
    std::uint8_t** rounded_data,
    std::size_t* rounded_size) noexcept {
    if (data == nullptr || size == 0 || rounded_data == nullptr || rounded_size == nullptr) {
        return false;
    }
    const std::size_t page = pageSize();
    const std::uintptr_t address = reinterpret_cast<std::uintptr_t>(data);
    const std::uintptr_t start = address - (address % page);
    if (address > std::numeric_limits<std::uintptr_t>::max() - size) {
        return false;
    }
    const std::uintptr_t logical_end = address + size;
    if (logical_end > std::numeric_limits<std::uintptr_t>::max() - (page - 1U)) {
        return false;
    }
    const std::uintptr_t end = ((logical_end + page - 1U) / page) * page;
    if (end <= start || end - start > kMaximumLockedBytes) {
        return false;
    }
    *rounded_data = reinterpret_cast<std::uint8_t*>(start);
    *rounded_size = static_cast<std::size_t>(end - start);
    return true;
}

bool reserve(std::size_t size) noexcept {
    std::size_t current = g_locked_bytes.load(std::memory_order_relaxed);
    while (size <= kMaximumLockedBytes && current <= kMaximumLockedBytes - size) {
        if (g_locked_bytes.compare_exchange_weak(
                current, current + size, std::memory_order_acq_rel)) {
            return true;
        }
    }
    return false;
}

void release(std::size_t size) noexcept {
    if (size != 0) {
        g_locked_bytes.fetch_sub(size, std::memory_order_acq_rel);
    }
}

}  // namespace

bool lockRegionBestEffort(
    std::uint8_t* data, std::size_t size, LockedRegion* output) noexcept {
    if (output == nullptr || output->data != nullptr || output->size != 0) {
        return false;
    }
#if defined(AH_M2_02_HOST_TESTING)
    if (g_force_unavailable.load(std::memory_order_relaxed)) return false;
#endif
    std::uint8_t* rounded_data = nullptr;
    std::size_t rounded_size = 0;
    if (!roundedRegion(data, size, &rounded_data, &rounded_size) || !reserve(rounded_size)) {
        return false;
    }
#if defined(_WIN32)
    const bool locked = VirtualLock(rounded_data, rounded_size) != 0;
#else
    const bool locked = mlock(rounded_data, rounded_size) == 0;
#endif
    if (!locked) {
        release(rounded_size);
        return false;
    }
    output->data = rounded_data;
    output->size = rounded_size;
    return true;
}

void unlockRegion(LockedRegion* region) noexcept {
    if (region == nullptr || region->data == nullptr || region->size == 0) {
        return;
    }
#if defined(_WIN32)
    (void) VirtualUnlock(region->data, region->size);
#else
    (void) munlock(region->data, region->size);
#endif
    release(region->size);
    *region = LockedRegion{};
}

bool adviseDontDump(std::uint8_t* data, std::size_t size) noexcept {
    if (data == nullptr || size == 0) {
        return false;
    }
#if defined(AH_M2_02_HOST_TESTING)
    if (g_force_unavailable.load(std::memory_order_relaxed)) return false;
#endif
#if defined(MADV_DONTDUMP)
    return madvise(data, size, MADV_DONTDUMP) == 0;
#else
    (void) data;
    (void) size;
    return false;
#endif
}

bool currentProcessDumpable(bool* output) noexcept {
    if (output == nullptr) {
        return false;
    }
#if defined(_WIN32)
    *output = true;
    return false;
#else
    const int result = prctl(PR_GET_DUMPABLE, 0, 0, 0, 0);
    if (result < 0) {
        return false;
    }
    *output = result != 0;
    return true;
#endif
}

bool disableCurrentProcessDumping() noexcept {
#if defined(_WIN32)
    return false;
#else
    return prctl(PR_SET_DUMPABLE, 0, 0, 0, 0) == 0;
#endif
}

bool applyHighRiskJitter(std::uint32_t* applied_milliseconds) noexcept {
    if (applied_milliseconds != nullptr) *applied_milliseconds = 0;
#if defined(_WIN32)
    return false;
#else
    std::uint32_t random_value = 0;
    const ssize_t received = getrandom(&random_value, sizeof(random_value), 0);
    if (received != static_cast<ssize_t>(sizeof(random_value))) {
        return false;
    }
    const std::uint32_t milliseconds = 20U + random_value % 31U;
    timespec requested{
        static_cast<time_t>(milliseconds / 1000U),
        static_cast<long>((milliseconds % 1000U) * 1'000'000U),
    };
    while (nanosleep(&requested, &requested) != 0) {
        if (errno != EINTR) {
            return false;
        }
    }
    if (applied_milliseconds != nullptr) *applied_milliseconds = milliseconds;
    return true;
#endif
}

std::size_t lockedBytesForTesting() noexcept {
    return g_locked_bytes.load(std::memory_order_acquire);
}

#if defined(AH_M2_02_HOST_TESTING)
void setMemoryControlUnavailableForTesting(bool unavailable) noexcept {
    g_force_unavailable.store(unavailable, std::memory_order_relaxed);
}
#endif

}  // namespace ah::memory
