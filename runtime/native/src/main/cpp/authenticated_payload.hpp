#ifndef AH_RUNTIME_AUTHENTICATED_PAYLOAD_HPP
#define AH_RUNTIME_AUTHENTICATED_PAYLOAD_HPP

#include "container_format.hpp"
#include "payload_memory.hpp"
#include "zip_assets.hpp"

#include <cstdint>

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
};

struct OpenRequest {
    zip::FixedAssets assets;
    container::ByteView native_share_slot;
    std::uint16_t expected_abi_id;
    container::ByteView measured_signer_sha256;
    container::ByteView framework_package_utf8;
};

Status openAuthenticatedPayload(
    const OpenRequest& request,
    memory::PayloadHandle* output,
    bool* cleanup_failed) noexcept;

}  // namespace ah::payload

#endif
