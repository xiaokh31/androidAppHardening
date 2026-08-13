#include "payload_memory.hpp"

#include "crypto_backend.hpp"
#include "memory_controls.hpp"

#if defined(AH_M2_02_HOST_TESTING)
#include <atomic>
#endif

#if defined(_WIN32)
#include <windows.h>
#else
#include <sys/mman.h>
#endif

namespace ah::memory {
namespace {

#if defined(AH_M2_02_HOST_TESTING)
std::atomic<std::int64_t> g_allocation_countdown{-1};
std::atomic<std::int64_t> g_protection_countdown{-1};
std::atomic<std::int64_t> g_release_countdown{-1};
std::atomic<std::size_t> g_live_mappings{0};
std::atomic<std::size_t> g_zeroized_releases{0};

bool failNow(std::atomic<std::int64_t>* countdown) noexcept {
    std::int64_t current = countdown->load(std::memory_order_relaxed);
    while (current >= 0) {
        if (current == 0) {
            return true;
        }
        if (countdown->compare_exchange_weak(
                current, current - 1, std::memory_order_relaxed)) {
            return false;
        }
    }
    return false;
}
#endif

std::uint8_t* allocateAnonymous(std::size_t size) noexcept {
#if defined(AH_M2_02_HOST_TESTING)
    if (failNow(&g_allocation_countdown)) {
        return nullptr;
    }
#endif
#if defined(_WIN32)
    std::uint8_t* result = static_cast<std::uint8_t*>(
        VirtualAlloc(nullptr, size, MEM_RESERVE | MEM_COMMIT, PAGE_READWRITE));
#else
    void* mapped = mmap(nullptr, size, PROT_READ | PROT_WRITE,
                        MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    std::uint8_t* result = mapped == MAP_FAILED ? nullptr : static_cast<std::uint8_t*>(mapped);
#endif
#if defined(AH_M2_02_HOST_TESTING)
    if (result != nullptr) {
        g_live_mappings.fetch_add(1, std::memory_order_relaxed);
    }
#endif
    return result;
}

bool releaseAnonymous(Mapping* mapping) noexcept {
    if (mapping == nullptr || mapping->data == nullptr || mapping->size == 0) {
        return true;
    }
#if defined(_WIN32)
    bool writable = true;
    if (mapping->read_only) {
        DWORD prior = 0;
        if (VirtualProtect(mapping->data, mapping->size, PAGE_READWRITE, &prior) == 0) {
            writable = false;
        }
    }
#else
    bool writable = true;
    if (mapping->read_only && mprotect(mapping->data, mapping->size, PROT_READ | PROT_WRITE) != 0) {
        writable = false;
    }
#endif
    if (writable) {
        ah::crypto::secureZero(mapping->data, mapping->size);
#if defined(AH_M2_02_HOST_TESTING)
        bool all_zero = true;
        for (std::size_t index = 0; index < mapping->size; ++index) {
            all_zero = all_zero && mapping->data[index] == 0;
        }
        if (all_zero) {
            g_zeroized_releases.fetch_add(1, std::memory_order_relaxed);
        }
#endif
    }
    for (std::size_t index = 0; index < mapping->locked_region_count; ++index) {
        unlockRegion(&mapping->locked_regions[index]);
    }
#if defined(_WIN32)
    const bool released = VirtualFree(mapping->data, 0, MEM_RELEASE) != 0;
#else
    const bool released = munmap(mapping->data, mapping->size) == 0;
#endif
#if defined(AH_M2_02_HOST_TESTING)
    if (released) {
        g_live_mappings.fetch_sub(1, std::memory_order_relaxed);
    }
    const bool injected_failure = failNow(&g_release_countdown);
#else
    const bool injected_failure = false;
#endif
    mapping->data = nullptr;
    mapping->size = 0;
    mapping->read_only = false;
    mapping->dont_dump = false;
    mapping->lock_attempted = false;
    mapping->locked_region_count = 0;
    return released && writable && !injected_failure;
}

}  // namespace

bool Mapping::seal() noexcept {
    if (data == nullptr || size == 0 || read_only) return false;
#if defined(AH_M2_02_HOST_TESTING)
    if (failNow(&g_protection_countdown)) {
        return false;
    }
#endif
#if defined(_WIN32)
    DWORD prior = 0;
    if (VirtualProtect(data, size, PAGE_READONLY, &prior) == 0) {
        return false;
    }
#else
    if (mprotect(data, size, PROT_READ) != 0) {
        return false;
    }
#endif
    read_only = true;
    dont_dump = adviseDontDump(data, size);
    return true;
}

std::size_t Mapping::lockEdgesBestEffort() noexcept {
    if (data == nullptr || size == 0 || lock_attempted) return lockedBytes();
    lock_attempted = true;
    if (size <= 2U * kDexEdgeBytes) {
        if (lockRegionBestEffort(data, size, &locked_regions[0])) locked_region_count = 1;
        return lockedBytes();
    }
    if (lockRegionBestEffort(data, kDexEdgeBytes, &locked_regions[0])) {
        locked_region_count = 1;
    }
    std::uint8_t* suffix_data = data + size - kDexEdgeBytes;
    std::uint8_t* mapping_end = data + size;
    if (locked_region_count == 1) {
        std::uint8_t* prefix_end = locked_regions[0].data + locked_regions[0].size;
        if (suffix_data < prefix_end) suffix_data = prefix_end;
    }
    LockedRegion suffix{};
    if (suffix_data < mapping_end &&
        lockRegionBestEffort(
            suffix_data, static_cast<std::size_t>(mapping_end - suffix_data), &suffix)) {
        locked_regions[locked_region_count++] = suffix;
    }
    return lockedBytes();
}

std::size_t Mapping::lockedBytes() const noexcept {
    std::size_t total = 0;
    for (std::size_t index = 0; index < locked_region_count; ++index) {
        total += locked_regions[index].size;
    }
    return total;
}

namespace {

Status clearAll(std::array<Mapping, container::kMaxDex>* mappings,
                std::size_t* count) noexcept {
    if (mappings == nullptr || count == nullptr) {
        return Status::kInvalidArgument;
    }
    bool cleanup_failed = false;
    while (*count != 0) {
        --*count;
        if (!releaseAnonymous(&(*mappings)[*count])) {
            cleanup_failed = true;
        }
    }
    return cleanup_failed ? Status::kCleanupFailed : Status::kSuccess;
}

}  // namespace

#if defined(AH_M2_02_HOST_TESTING)
void resetFailureInjectionForTesting() noexcept {
    g_allocation_countdown.store(-1, std::memory_order_relaxed);
    g_protection_countdown.store(-1, std::memory_order_relaxed);
    g_release_countdown.store(-1, std::memory_order_relaxed);
}

void failAllocationAfterForTesting(std::int64_t successful_allocations) noexcept {
    g_allocation_countdown.store(successful_allocations, std::memory_order_relaxed);
}

void failProtectionAfterForTesting(std::int64_t successful_protections) noexcept {
    g_protection_countdown.store(successful_protections, std::memory_order_relaxed);
}

void failReleaseAfterForTesting(std::int64_t successful_releases) noexcept {
    g_release_countdown.store(successful_releases, std::memory_order_relaxed);
}

std::size_t liveMappingCountForTesting() noexcept {
    return g_live_mappings.load(std::memory_order_relaxed);
}

std::size_t zeroizedReleaseCountForTesting() noexcept {
    return g_zeroized_releases.load(std::memory_order_relaxed);
}
#endif

PayloadHandle::~PayloadHandle() noexcept {
    (void) close();
}

Status PayloadHandle::close() noexcept {
    const Status result = clearAll(&mappings_, &count_);
    profile_ = Profile::kBaseline;
    high_applied_ = false;
    jitter_milliseconds_ = 0;
    return result;
}

Capabilities PayloadHandle::capabilities() const noexcept {
    Capabilities result{};
    result.dont_dump = count_ != 0;
    for (std::size_t index = 0; index < count_; ++index) {
        result.dont_dump = result.dont_dump && mappings_[index].dont_dump;
        result.locked_bytes += mappings_[index].lockedBytes();
    }
    bool dumpable = true;
    if (currentProcessDumpable(&dumpable)) result.process_dumpable = dumpable;
    result.jitter_milliseconds = jitter_milliseconds_;
    return result;
}

Status PayloadHandle::applyProfile(Profile profile, Capabilities* output) noexcept {
    if (output == nullptr || count_ == 0 || static_cast<unsigned>(profile) > 2U) {
        return Status::kInvalidArgument;
    }
    if (static_cast<unsigned>(profile) > static_cast<unsigned>(profile_)) {
        profile_ = profile;
    }
    if (profile_ == Profile::kElevated || profile_ == Profile::kHigh) {
        for (std::size_t index = 0; index < count_; ++index) {
            (void) mappings_[index].lockEdgesBestEffort();
        }
    }
    if (profile_ == Profile::kHigh && !high_applied_) {
        (void) disableCurrentProcessDumping();
        (void) applyHighRiskJitter(&jitter_milliseconds_);
        high_applied_ = true;
    }
    *output = capabilities();
    return Status::kSuccess;
}

Status PayloadHandle::transferTo(PayloadHandle* output) noexcept {
    if (output == nullptr || output == this || output->count_ != 0 || count_ == 0) {
        return Status::kInvalidArgument;
    }
    for (std::size_t index = 0; index < count_; ++index) {
        output->mappings_[index] = mappings_[index];
        mappings_[index] = Mapping{};
    }
    output->count_ = count_;
    output->profile_ = profile_;
    output->high_applied_ = high_applied_;
    output->jitter_milliseconds_ = jitter_milliseconds_;
    count_ = 0;
    profile_ = Profile::kBaseline;
    high_applied_ = false;
    jitter_milliseconds_ = 0;
    return Status::kSuccess;
}

PayloadTransaction::~PayloadTransaction() noexcept {
    if (!committed_) {
        (void) rollback();
    }
}

Status PayloadTransaction::allocate(std::size_t size, Mapping** output) noexcept {
    if (output != nullptr) {
        *output = nullptr;
    }
    if (output == nullptr || size == 0 || size > container::kMaxDexBytes ||
        committed_ || count_ >= mappings_.size()) {
        return Status::kInvalidArgument;
    }
    std::uint8_t* data = allocateAnonymous(size);
    if (data == nullptr) {
        return Status::kOutOfMemory;
    }
    mappings_[count_] = Mapping{};
    mappings_[count_].data = data;
    mappings_[count_].size = size;
    *output = &mappings_[count_++];
    return Status::kSuccess;
}

Status PayloadTransaction::rollback() noexcept {
    if (committed_) {
        return Status::kInvalidArgument;
    }
    return clearAll(&mappings_, &count_);
}

Status PayloadTransaction::commit(PayloadHandle* output) noexcept {
    if (output == nullptr || committed_ || output->count_ != 0 || count_ == 0) {
        return Status::kInvalidArgument;
    }
    for (std::size_t index = 0; index < count_; ++index) {
        if (!mappings_[index].seal()) {
            return Status::kProtectionFailed;
        }
    }
    for (std::size_t index = 0; index < count_; ++index) {
        output->mappings_[index] = mappings_[index];
        mappings_[index] = Mapping{};
    }
    output->count_ = count_;
    count_ = 0;
    committed_ = true;
    return Status::kSuccess;
}

}  // namespace ah::memory
