#include "payload_memory.hpp"

#include "crypto_backend.hpp"

#if defined(_WIN32)
#include <windows.h>
#else
#include <sys/mman.h>
#endif

namespace ah::memory {
namespace {

std::uint8_t* allocateAnonymous(std::size_t size) noexcept {
#if defined(_WIN32)
    return static_cast<std::uint8_t*>(
        VirtualAlloc(nullptr, size, MEM_RESERVE | MEM_COMMIT, PAGE_READWRITE));
#else
    void* result = mmap(nullptr, size, PROT_READ | PROT_WRITE,
                        MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    return result == MAP_FAILED ? nullptr : static_cast<std::uint8_t*>(result);
#endif
}

bool releaseAnonymous(Mapping* mapping) noexcept {
    if (mapping == nullptr || mapping->data == nullptr || mapping->size == 0) {
        return true;
    }
    ah::crypto::secureZero(mapping->data, mapping->size);
#if defined(_WIN32)
    const bool released = VirtualFree(mapping->data, 0, MEM_RELEASE) != 0;
#else
    const bool released = munmap(mapping->data, mapping->size) == 0;
#endif
    mapping->data = nullptr;
    mapping->size = 0;
    return released;
}

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

PayloadHandle::~PayloadHandle() noexcept {
    (void) close();
}

Status PayloadHandle::close() noexcept {
    return clearAll(&mappings_, &count_);
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
    mappings_[count_] = {data, size};
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
        output->mappings_[index] = mappings_[index];
        mappings_[index] = Mapping{};
    }
    output->count_ = count_;
    count_ = 0;
    committed_ = true;
    return Status::kSuccess;
}

}  // namespace ah::memory
