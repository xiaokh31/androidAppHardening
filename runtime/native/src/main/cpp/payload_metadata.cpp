#include "payload_metadata.hpp"

#include <algorithm>
#include <cstring>

namespace ah::metadata {
namespace {

void putU16(std::uint8_t* output, std::size_t offset, std::uint16_t value) noexcept {
    output[offset] = static_cast<std::uint8_t>(value);
    output[offset + 1] = static_cast<std::uint8_t>(value >> 8U);
}

}  // namespace

Status encodeUntrustedBinding(
    const payload::UntrustedBinding& value,
    std::uint8_t* output,
    std::size_t capacity,
    std::size_t* written) noexcept {
    if (written != nullptr) {
        *written = 0;
    }
    if (output == nullptr || written == nullptr || capacity < kBindingBytes) {
        return Status::kInvalidArgument;
    }
    std::fill_n(output, kBindingBytes, 0);
    std::memcpy(output, "AHUB", 4);
    putU16(output, 4, 1);
    putU16(output, 6, static_cast<std::uint16_t>(kBindingBytes));
    std::copy(value.build_id.begin(), value.build_id.end(), output + 8);
    std::copy(value.key_slot_id.begin(), value.key_slot_id.end(), output + 24);
    std::copy(value.current_signer_sha256.begin(), value.current_signer_sha256.end(), output + 40);
    *written = kBindingBytes;
    return Status::kSuccess;
}

Status encodeAuthenticatedMetadata(
    const payload::AuthenticatedMetadata& value,
    std::uint8_t* output,
    std::size_t capacity,
    std::size_t* written) noexcept {
    if (written != nullptr) {
        *written = 0;
    }
    if (output == nullptr || written == nullptr || value.original_factory_size > 512 ||
        value.signer_lineage_count == 0 ||
        value.signer_lineage_count > container::kMaxLineage) {
        return Status::kInvalidArgument;
    }
    const std::size_t required = kMetadataFixedBytes + value.original_factory_size +
                                 value.signer_lineage_count * container::kDigestBytes;
    if (required > capacity || required > kMetadataMaxBytes || required > 0xffffU) {
        return Status::kEncoding;
    }
    std::fill_n(output, required, 0);
    std::memcpy(output, "AHMD", 4);
    putU16(output, 4, 1);
    putU16(output, 6, static_cast<std::uint16_t>(required));
    putU16(output, 8, value.container_major);
    putU16(output, 10, value.container_minor);
    putU16(output, 12, value.signer_policy_version);
    putU16(output, 14, value.risk_policy_version);
    putU16(output, 16, value.original_factory_size);
    putU16(output, 18, value.signer_lineage_count);
    std::copy(value.build_id.begin(), value.build_id.end(), output + 24);
    std::copy(value.key_slot_id.begin(), value.key_slot_id.end(), output + 40);
    std::copy(value.package_name_sha256.begin(), value.package_name_sha256.end(), output + 56);
    std::copy(value.current_signer_sha256.begin(), value.current_signer_sha256.end(), output + 88);
    std::copy_n(reinterpret_cast<const std::uint8_t*>(value.original_factory.data()),
                value.original_factory_size, output + kMetadataFixedBytes);
    std::size_t cursor = kMetadataFixedBytes + value.original_factory_size;
    for (std::size_t index = 0; index < value.signer_lineage_count; ++index) {
        std::copy(value.signer_lineage_sha256[index].begin(),
                  value.signer_lineage_sha256[index].end(), output + cursor);
        cursor += container::kDigestBytes;
    }
    *written = required;
    return Status::kSuccess;
}

}  // namespace ah::metadata
