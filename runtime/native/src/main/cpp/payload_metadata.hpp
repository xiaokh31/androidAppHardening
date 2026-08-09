#ifndef AH_RUNTIME_PAYLOAD_METADATA_HPP
#define AH_RUNTIME_PAYLOAD_METADATA_HPP

#include "authenticated_payload.hpp"

#include <cstddef>
#include <cstdint>

namespace ah::metadata {

constexpr std::size_t kBindingBytes = 72;
constexpr std::size_t kMetadataFixedBytes = 120;
constexpr std::size_t kMetadataMaxBytes =
    kMetadataFixedBytes + 512 + container::kMaxLineage * container::kDigestBytes;

enum class Status : std::uint8_t {
    kSuccess = 0,
    kInvalidArgument = 1,
    kEncoding = 2,
};

Status encodeUntrustedBinding(
    const payload::UntrustedBinding& value,
    std::uint8_t* output,
    std::size_t capacity,
    std::size_t* written) noexcept;

Status encodeAuthenticatedMetadata(
    const payload::AuthenticatedMetadata& value,
    std::uint8_t* output,
    std::size_t capacity,
    std::size_t* written) noexcept;

}  // namespace ah::metadata

#endif
