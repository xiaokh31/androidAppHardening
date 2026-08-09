#ifndef AH_RUNTIME_PAYLOAD_MEMORY_HPP
#define AH_RUNTIME_PAYLOAD_MEMORY_HPP

#include "container_format.hpp"

#include <array>
#include <cstddef>
#include <cstdint>

namespace ah::memory {

enum class Status : std::uint8_t {
    kSuccess = 0,
    kInvalidArgument = 1,
    kOutOfMemory = 2,
    kCleanupFailed = 3,
    kProtectionFailed = 4,
};

struct Mapping {
    std::uint8_t* data{};
    std::size_t size{};
    bool read_only{};
};

class PayloadHandle final {
public:
    PayloadHandle() noexcept = default;
    ~PayloadHandle() noexcept;
    PayloadHandle(const PayloadHandle&) = delete;
    PayloadHandle& operator=(const PayloadHandle&) = delete;

    std::size_t size() const noexcept { return count_; }
    const Mapping& mapping(std::size_t index) const noexcept { return mappings_[index]; }
    Status close() noexcept;
    Status transferTo(PayloadHandle* output) noexcept;

private:
    friend class PayloadTransaction;
    std::array<Mapping, container::kMaxDex> mappings_{};
    std::size_t count_{};
};

class PayloadTransaction final {
public:
    PayloadTransaction() noexcept = default;
    ~PayloadTransaction() noexcept;
    PayloadTransaction(const PayloadTransaction&) = delete;
    PayloadTransaction& operator=(const PayloadTransaction&) = delete;

    Status allocate(std::size_t size, Mapping** output) noexcept;
    Status rollback() noexcept;
    Status commit(PayloadHandle* output) noexcept;
    std::size_t size() const noexcept { return count_; }

private:
    std::array<Mapping, container::kMaxDex> mappings_{};
    std::size_t count_{};
    bool committed_{};
};

#if defined(AH_M2_02_HOST_TESTING)
void resetFailureInjectionForTesting() noexcept;
void failAllocationAfterForTesting(std::int64_t successful_allocations) noexcept;
void failProtectionAfterForTesting(std::int64_t successful_protections) noexcept;
void failReleaseAfterForTesting(std::int64_t successful_releases) noexcept;
std::size_t liveMappingCountForTesting() noexcept;
std::size_t zeroizedReleaseCountForTesting() noexcept;
#endif

}  // namespace ah::memory

#endif
