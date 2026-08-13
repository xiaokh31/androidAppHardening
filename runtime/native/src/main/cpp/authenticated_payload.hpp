#ifndef AH_RUNTIME_AUTHENTICATED_PAYLOAD_HPP
#define AH_RUNTIME_AUTHENTICATED_PAYLOAD_HPP

#include "container_format.hpp"
#include "payload_memory.hpp"
#include "zip_assets.hpp"

#include <cstdint>
#include <array>
#include <cstddef>

namespace ah::payload {

enum class Status : std::uint8_t {
    kSuccess = 0,
    kInvalidArgument = 1,
    kZip = 2,
    kFormat = 3,
    kVersion = 4,
    kBinding = 5,
    kAuthentication = 6,
    kCrypto = 7,
    kOutOfMemory = 8,
    kZlibWrapper = 9,
    kZlibDictionary = 10,
    kZlibChecksum = 11,
    kLength = 12,
    kDigest = 13,
    kTrailingData = 14,
    kMemoryProtection = 15,
    kIo = 16,
    kCancelled = 17,
};

#if defined(AH_M2_02_HOST_TESTING)
enum class FailureStage : std::uint8_t {
    kBeforeAuthentication = 0,
    kBeforeInflate = 1,
    kAfterInflate = 2,
};

using FailureProbe = Status (*)(
    std::size_t global_chunk,
    FailureStage stage,
    void* context) noexcept;
#endif

struct OpenRequest {
    zip::FixedAssets assets;
    container::ByteView native_share_slot;
    std::uint16_t expected_abi_id;
    container::ByteView measured_signer_sha256;
    container::ByteView framework_package_utf8;
#if defined(AH_M2_02_HOST_TESTING)
    FailureProbe failure_probe{};
    void* failure_context{};
#endif
};

struct UntrustedBinding {
    std::array<std::uint8_t, container::kIdBytes> build_id{};
    std::array<std::uint8_t, container::kIdBytes> key_slot_id{};
    std::array<std::uint8_t, container::kDigestBytes> current_signer_sha256{};
};

struct AuthenticatedMetadata {
    std::uint16_t container_major{};
    std::uint16_t container_minor{};
    std::uint16_t signer_policy_version{};
    std::uint16_t risk_policy_version{};
    std::array<std::uint8_t, container::kIdBytes> build_id{};
    std::array<std::uint8_t, container::kIdBytes> key_slot_id{};
    std::array<std::uint8_t, container::kDigestBytes> package_name_sha256{};
    std::array<std::uint8_t, container::kDigestBytes> current_signer_sha256{};
    std::array<std::array<std::uint8_t, container::kDigestBytes>, container::kMaxLineage>
        signer_lineage_sha256{};
    std::array<char, 512> original_factory{};
    std::uint16_t original_factory_size{};
    std::uint16_t signer_lineage_count{};
};

Status inspectUntrustedBinding(
    const zip::FixedAssets& assets,
    UntrustedBinding* output) noexcept;

Status openAuthenticatedPayload(
    const OpenRequest& request,
    memory::PayloadHandle* output,
    AuthenticatedMetadata* metadata_output,
    bool* cleanup_failed) noexcept;

#if defined(AH_M2_02_HOST_TESTING)
void resetZlibCleanupEvidenceForTesting() noexcept;
std::size_t zlibLiveAllocationCountForTesting() noexcept;
std::size_t zlibTotalFreeCountForTesting() noexcept;
std::size_t zlibZeroizedFreeCountForTesting() noexcept;
void resetShareScrubEvidenceForTesting() noexcept;
std::size_t shareScrubRunCountForTesting() noexcept;
std::size_t shareScrubZeroizedRunCountForTesting() noexcept;
Status inflateCompressedForTesting(
    container::ByteView compressed, std::uint8_t* output, std::size_t output_size) noexcept;
#endif

}  // namespace ah::payload

#endif
