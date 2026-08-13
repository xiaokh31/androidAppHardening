#ifndef AH_RUNTIME_SECURE_BUFFER_HPP
#define AH_RUNTIME_SECURE_BUFFER_HPP

#include "memory_controls.hpp"

#include <cstddef>
#include <cstdint>

namespace ah::memory {

class SecureBuffer final {
public:
    SecureBuffer() noexcept = default;
    explicit SecureBuffer(std::size_t size, bool lock_pages = false) noexcept;
    ~SecureBuffer() noexcept;
    SecureBuffer(const SecureBuffer&) = delete;
    SecureBuffer& operator=(const SecureBuffer&) = delete;
    SecureBuffer(SecureBuffer&& other) noexcept;
    SecureBuffer& operator=(SecureBuffer&& other) noexcept;

    bool allocate(std::size_t size, bool lock_pages = false) noexcept;
    void reset() noexcept;
    std::uint8_t* data() noexcept { return data_; }
    const std::uint8_t* data() const noexcept { return data_; }
    std::size_t size() const noexcept { return size_; }
    bool empty() const noexcept { return size_ == 0; }
    bool locked() const noexcept { return locked_.size != 0; }
    std::uint8_t& operator[](std::size_t index) noexcept { return data_[index]; }
    const std::uint8_t& operator[](std::size_t index) const noexcept { return data_[index]; }
    std::uint8_t* begin() noexcept { return data_; }
    std::uint8_t* end() noexcept { return data_ + size_; }
    const std::uint8_t* begin() const noexcept { return data_; }
    const std::uint8_t* end() const noexcept { return data_ + size_; }

private:
    std::uint8_t* data_{};
    std::size_t size_{};
    std::size_t allocation_size_{};
    LockedRegion locked_{};
};

#if defined(AH_M2_02_HOST_TESTING)
void resetSecureBufferEvidenceForTesting() noexcept;
std::size_t secureBufferZeroizedReleaseCountForTesting() noexcept;
#endif
}  // namespace ah::memory

#endif
