#include "secure_buffer.hpp"

#include "crypto_backend.hpp"

#include <algorithm>
#include <atomic>
#include <limits>

#if defined(_WIN32)
#define NOMINMAX
#include <windows.h>
#else
#include <sys/mman.h>
#include <unistd.h>
#endif

namespace ah::memory {
namespace {

#if defined(AH_M2_02_HOST_TESTING)
std::atomic<std::size_t> g_zeroized_releases{0};
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

bool roundAllocation(std::size_t size, std::size_t* output) noexcept {
    if (size == 0 || output == nullptr) return false;
    const std::size_t page = pageSize();
    if (size > std::numeric_limits<std::size_t>::max() - (page - 1U)) return false;
    *output = ((size + page - 1U) / page) * page;
    return *output != 0;
}

}  // namespace

SecureBuffer::SecureBuffer(std::size_t size, bool lock_pages) noexcept {
    (void) allocate(size, lock_pages);
}

SecureBuffer::~SecureBuffer() noexcept {
    reset();
}

SecureBuffer::SecureBuffer(SecureBuffer&& other) noexcept
    : data_(other.data_),
      size_(other.size_),
      allocation_size_(other.allocation_size_),
      locked_(other.locked_) {
    other.data_ = nullptr;
    other.size_ = 0;
    other.allocation_size_ = 0;
    other.locked_ = LockedRegion{};
}

SecureBuffer& SecureBuffer::operator=(SecureBuffer&& other) noexcept {
    if (this != &other) {
        reset();
        data_ = other.data_;
        size_ = other.size_;
        allocation_size_ = other.allocation_size_;
        locked_ = other.locked_;
        other.data_ = nullptr;
        other.size_ = 0;
        other.allocation_size_ = 0;
        other.locked_ = LockedRegion{};
    }
    return *this;
}

bool SecureBuffer::allocate(std::size_t size, bool lock_pages) noexcept {
    if (data_ != nullptr || !roundAllocation(size, &allocation_size_)) return false;
#if defined(_WIN32)
    data_ = static_cast<std::uint8_t*>(
        VirtualAlloc(nullptr, allocation_size_, MEM_RESERVE | MEM_COMMIT, PAGE_READWRITE));
#else
    void* mapped = mmap(nullptr, allocation_size_, PROT_READ | PROT_WRITE,
                        MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    data_ = mapped == MAP_FAILED ? nullptr : static_cast<std::uint8_t*>(mapped);
#endif
    if (data_ == nullptr) {
        allocation_size_ = 0;
        return false;
    }
    size_ = size;
    std::fill_n(data_, allocation_size_, static_cast<std::uint8_t>(0));
    if (lock_pages) {
        (void) lockRegionBestEffort(data_, allocation_size_, &locked_);
    }
    return true;
}

void SecureBuffer::reset() noexcept {
    if (data_ == nullptr) return;
    crypto::secureZero(data_, allocation_size_);
#if defined(AH_M2_02_HOST_TESTING)
    bool all_zero = true;
    for (std::size_t index = 0; index < allocation_size_; ++index) {
        all_zero = all_zero && data_[index] == 0;
    }
    if (all_zero) g_zeroized_releases.fetch_add(1, std::memory_order_relaxed);
#endif
    unlockRegion(&locked_);
#if defined(_WIN32)
    (void) VirtualFree(data_, 0, MEM_RELEASE);
#else
    (void) munmap(data_, allocation_size_);
#endif
    data_ = nullptr;
    size_ = 0;
    allocation_size_ = 0;
}

#if defined(AH_M2_02_HOST_TESTING)
void resetSecureBufferEvidenceForTesting() noexcept {
    g_zeroized_releases.store(0, std::memory_order_relaxed);
}

std::size_t secureBufferZeroizedReleaseCountForTesting() noexcept {
    return g_zeroized_releases.load(std::memory_order_relaxed);
}
#endif

}  // namespace ah::memory
