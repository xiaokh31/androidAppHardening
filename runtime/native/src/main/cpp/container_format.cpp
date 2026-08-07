#include "container_format.hpp"

#include <algorithm>
#include <cstring>
#include <limits>

namespace ah::container {
namespace {

constexpr std::uint16_t kMajor = 2;
constexpr std::uint16_t kMinor = 0;
constexpr std::size_t kSpv1FixedBytes = 44;
constexpr std::size_t kFactoryOffset = 180;
constexpr std::size_t kFactorySlotBytes = 512;
constexpr std::size_t kConfigReservedOffset = 692;
constexpr std::size_t kGcmTagBytes = 16;

bool validView(ByteView view) noexcept {
    return view.size == 0 || view.data != nullptr;
}

bool exact(ByteView view, std::size_t expected) noexcept {
    return validView(view) && view.size == expected;
}

std::uint16_t u16(ByteView view, std::size_t offset) noexcept {
    return static_cast<std::uint16_t>(view.data[offset]) |
           static_cast<std::uint16_t>(view.data[offset + 1]) << 8U;
}

std::uint32_t u32(ByteView view, std::size_t offset) noexcept {
    return static_cast<std::uint32_t>(view.data[offset]) |
           static_cast<std::uint32_t>(view.data[offset + 1]) << 8U |
           static_cast<std::uint32_t>(view.data[offset + 2]) << 16U |
           static_cast<std::uint32_t>(view.data[offset + 3]) << 24U;
}

std::uint64_t u64(ByteView view, std::size_t offset) noexcept {
    std::uint64_t value = 0;
    for (std::size_t index = 0; index < 8; ++index) {
        value |= static_cast<std::uint64_t>(view.data[offset + index]) << (index * 8U);
    }
    return value;
}

bool bytesEqual(ByteView view, std::size_t offset, const char* expected, std::size_t size) noexcept {
    return std::memcmp(view.data + offset, expected, size) == 0;
}

bool allZero(ByteView view, std::size_t offset, std::size_t size) noexcept {
    std::uint8_t result = 0;
    for (std::size_t index = 0; index < size; ++index) {
        result |= view.data[offset + index];
    }
    return result == 0;
}

template <std::size_t N>
void copyAt(ByteView source, std::size_t offset, std::array<std::uint8_t, N>* output) noexcept {
    std::copy_n(source.data + offset, N, output->begin());
}

bool checkedAdd(std::uint64_t left, std::uint64_t right, std::uint64_t* output) noexcept {
    if (output == nullptr || left > std::numeric_limits<std::uint64_t>::max() - right) {
        return false;
    }
    *output = left + right;
    return true;
}

bool checkedMultiply(std::uint64_t left, std::uint64_t right, std::uint64_t* output) noexcept {
    if (output == nullptr || (left != 0 && right > std::numeric_limits<std::uint64_t>::max() / left)) {
        return false;
    }
    *output = left * right;
    return true;
}

bool canonicalDexName(std::uint32_t ordinal, const char* name, std::size_t size) noexcept {
    char expected[25]{};
    if (ordinal == 0) {
        constexpr char kPrimary[] = "classes.dex";
        std::copy_n(kPrimary, sizeof(kPrimary), expected);
    } else {
        constexpr char kPrefix[] = "classes";
        constexpr char kSuffix[] = ".dex";
        std::size_t offset = sizeof(kPrefix) - 1;
        std::copy_n(kPrefix, offset, expected);
        std::uint32_t value = ordinal + 1;
        char reversed[10]{};
        std::size_t digits = 0;
        while (value != 0 && digits < sizeof(reversed)) {
            reversed[digits++] = static_cast<char>('0' + value % 10);
            value /= 10;
        }
        if (value != 0 || offset + digits + sizeof(kSuffix) > sizeof(expected)) {
            return false;
        }
        for (std::size_t index = 0; index < digits; ++index) {
            expected[offset + index] = reversed[digits - index - 1];
        }
        offset += digits;
        std::copy_n(kSuffix, sizeof(kSuffix), expected + offset);
    }
    return std::strlen(expected) == size && std::memcmp(expected, name, size) == 0;
}

bool validFactory(const std::uint8_t* bytes, std::size_t size) noexcept {
    if (size == 0 || size > kFactorySlotBytes || bytes == nullptr) {
        return false;
    }
    bool segmentStart = true;
    for (std::size_t index = 0; index < size; ++index) {
        const unsigned char value = bytes[index];
        if (value == '.') {
            if (segmentStart || index + 1 == size) {
                return false;
            }
            segmentStart = true;
            continue;
        }
        const bool first = (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z') ||
                           value == '_' || value == '$';
        const bool later = first || (value >= '0' && value <= '9');
        if ((segmentStart && !first) || (!segmentStart && !later)) {
            return false;
        }
        segmentStart = false;
    }
    constexpr char kShellFactory[] = "ah.runtime.bootstrap.ShellAppComponentFactory";
    return size != sizeof(kShellFactory) - 1 ||
           std::memcmp(bytes, kShellFactory, sizeof(kShellFactory) - 1) != 0;
}

}  // namespace

bool constantTimeEqual(ByteView left, ByteView right) noexcept {
    if (!validView(left) || !validView(right) || left.size != right.size) {
        return false;
    }
    std::uint8_t difference = 0;
    for (std::size_t index = 0; index < left.size; ++index) {
        difference |= left.data[index] ^ right.data[index];
    }
    return difference == 0;
}

Status parseHeaderV2(ByteView bytes, HeaderV2* output) noexcept {
    if (output == nullptr || !exact(bytes, kHeaderBytes)) {
        return Status::kInvalidArgument;
    }
    *output = HeaderV2{};
    if (!bytesEqual(bytes, 0, "AHDC", 4)) {
        return Status::kFormat;
    }
    if (u16(bytes, 4) != kMajor || u16(bytes, 6) != kMinor || u16(bytes, 10) != 0 ||
        u32(bytes, 136) != kChunkPlaintextMax) {
        return Status::kVersion;
    }
    if (u16(bytes, 8) != kHeaderBytes || !allZero(bytes, 140, 20)) {
        return Status::kFormat;
    }
    const std::uint32_t dexCount = u32(bytes, 12);
    const std::uint32_t signerSize = u32(bytes, 16);
    const std::uint32_t recordTableSize = u32(bytes, 20);
    const std::uint32_t chunkCount = u32(bytes, 24);
    const std::uint32_t chunkTableSize = u32(bytes, 28);
    if (dexCount == 0 || dexCount > kMaxDex || chunkCount == 0 || chunkCount > kMaxChunks) {
        return Status::kLimitExceeded;
    }
    if (signerSize < kSpv1FixedBytes + kDigestBytes ||
        signerSize > kSpv1FixedBytes + kMaxLineage * kDigestBytes ||
        recordTableSize != dexCount * kRecordBytes || chunkTableSize != chunkCount * kChunkBytes) {
        return Status::kFormat;
    }
    output->dex_count = dexCount;
    output->signer_policy_size = signerSize;
    output->record_table_size = recordTableSize;
    output->chunk_count = chunkCount;
    output->chunk_table_size = chunkTableSize;
    output->payload_size = u64(bytes, 32);
    copyAt(bytes, 40, &output->build_id);
    copyAt(bytes, 56, &output->key_slot_id);
    copyAt(bytes, 72, &output->config_sha256);
    copyAt(bytes, 104, &output->manifest_mac);
    return Status::kSuccess;
}

Status parseSignerPolicyV1(ByteView bytes, SignerPolicyV1* output) noexcept {
    if (output == nullptr || !validView(bytes)) {
        return Status::kInvalidArgument;
    }
    *output = SignerPolicyV1{};
    if (bytes.size < kSpv1FixedBytes + kDigestBytes || !bytesEqual(bytes, 0, "SPV1", 4)) {
        return Status::kFormat;
    }
    if (u16(bytes, 4) != 1 || u16(bytes, 6) != 0 || u16(bytes, 10) != 0) {
        return Status::kVersion;
    }
    const std::uint16_t count = u16(bytes, 8);
    if (count == 0 || count > kMaxLineage) {
        return Status::kLimitExceeded;
    }
    if (bytes.size != kSpv1FixedBytes + static_cast<std::size_t>(count) * kDigestBytes) {
        return Status::kFormat;
    }
    copyAt(bytes, 12, &output->current_signer_sha256);
    output->lineage_count = count;
    for (std::size_t index = 0; index < count; ++index) {
        copyAt(bytes, kSpv1FixedBytes + index * kDigestBytes, &output->lineage_sha256[index]);
        for (std::size_t prior = 0; prior < index; ++prior) {
            if (constantTimeEqual(
                    {output->lineage_sha256[prior].data(), kDigestBytes},
                    {output->lineage_sha256[index].data(), kDigestBytes})) {
                *output = SignerPolicyV1{};
                return Status::kFormat;
            }
        }
    }
    if (!constantTimeEqual(
            {output->current_signer_sha256.data(), kDigestBytes},
            {output->lineage_sha256[count - 1].data(), kDigestBytes})) {
        *output = SignerPolicyV1{};
        return Status::kFormat;
    }
    return Status::kSuccess;
}

Status parseRecordV2(ByteView bytes, RecordV2* output) noexcept {
    if (output == nullptr || !exact(bytes, kRecordBytes)) {
        return Status::kInvalidArgument;
    }
    *output = RecordV2{};
    const std::uint32_t ordinal = u32(bytes, 0);
    const std::uint16_t nameSize = u16(bytes, 4);
    if (u16(bytes, 6) != 0 || nameSize == 0 || nameSize > 24 ||
        !allZero(bytes, 48 + nameSize, 24 - nameSize) || !allZero(bytes, 104, 24)) {
        return Status::kFormat;
    }
    for (std::size_t index = 0; index < nameSize; ++index) {
        if (bytes.data[48 + index] < 0x21 || bytes.data[48 + index] > 0x7e) {
            return Status::kFormat;
        }
    }
    if (!canonicalDexName(ordinal, reinterpret_cast<const char*>(bytes.data + 48), nameSize)) {
        return Status::kFormat;
    }
    const std::uint64_t originalLength = u64(bytes, 8);
    const std::uint64_t compressedLength = u64(bytes, 16);
    const std::uint32_t chunkCount = u32(bytes, 24);
    const std::uint32_t firstChunkIndex = u32(bytes, 28);
    if (ordinal >= kMaxDex || originalLength == 0 || originalLength > kMaxDexBytes ||
        compressedLength == 0 || compressedLength > kMaxCompressedDexBytes ||
        chunkCount == 0 || chunkCount > kMaxChunks || firstChunkIndex >= kMaxChunks) {
        return Status::kLimitExceeded;
    }
    if (allZero(bytes, 40, 8)) {
        return Status::kFormat;
    }
    output->ordinal = ordinal;
    std::copy_n(reinterpret_cast<const char*>(bytes.data + 48), nameSize, output->name.begin());
    output->original_length = originalLength;
    output->compressed_length = compressedLength;
    output->chunk_count = chunkCount;
    output->first_chunk_index = firstChunkIndex;
    output->payload_offset = u64(bytes, 32);
    copyAt(bytes, 40, &output->nonce_prefix);
    copyAt(bytes, 72, &output->original_sha256);
    return Status::kSuccess;
}

Status parseChunkV2(ByteView bytes, ChunkV2* output) noexcept {
    if (output == nullptr || !exact(bytes, kChunkBytes)) {
        return Status::kInvalidArgument;
    }
    *output = ChunkV2{};
    const std::uint32_t recordOrdinal = u32(bytes, 0);
    const std::uint32_t chunkOrdinal = u32(bytes, 4);
    const std::uint32_t length = u32(bytes, 24);
    if (u32(bytes, 28) != 0 || recordOrdinal >= kMaxDex || chunkOrdinal >= kMaxChunks ||
        length == 0 || length > kChunkPlaintextMax) {
        return Status::kFormat;
    }
    output->record_ordinal = recordOrdinal;
    output->chunk_ordinal = chunkOrdinal;
    output->compressed_offset = u64(bytes, 8);
    output->payload_offset = u64(bytes, 16);
    output->plaintext_length = length;
    return Status::kSuccess;
}

Status parseConfigV2(ByteView bytes, ConfigV2* output) noexcept {
    if (output == nullptr || !exact(bytes, kConfigBytes)) {
        return Status::kInvalidArgument;
    }
    *output = ConfigV2{};
    if (!bytesEqual(bytes, 0, "AHKC", 4)) {
        return Status::kFormat;
    }
    const std::uint16_t flags = u16(bytes, 8);
    if (u16(bytes, 4) != kMajor || u16(bytes, 6) != kMinor || u16(bytes, 10) != 0 ||
        u32(bytes, 12) != kConfigBytes || u16(bytes, 16) != kMajor ||
        u16(bytes, 18) != 1 || u16(bytes, 20) != 1 || (flags & ~1U) != 0) {
        return Status::kVersion;
    }
    const std::uint16_t factorySize = u16(bytes, 22);
    if ((flags == 0 && factorySize != 0) || (flags == 1 &&
        (factorySize == 0 || factorySize > kFactorySlotBytes))) {
        return Status::kFormat;
    }
    if ((factorySize != 0 && !validFactory(bytes.data + kFactoryOffset, factorySize)) ||
        !allZero(bytes, kFactoryOffset + factorySize, kFactorySlotBytes - factorySize) ||
        !allZero(bytes, kConfigReservedOffset, kConfigBytes - kConfigReservedOffset)) {
        return Status::kFormat;
    }
    output->flags = flags;
    output->container_major = u16(bytes, 16);
    output->signer_policy_version = u16(bytes, 18);
    output->risk_policy_version = u16(bytes, 20);
    output->original_factory_size = factorySize;
    copyAt(bytes, 24, &output->build_id);
    copyAt(bytes, 40, &output->key_slot_id);
    copyAt(bytes, 56, &output->current_signer_sha256);
    copyAt(bytes, 88, &output->r_java);
    copyAt(bytes, 120, &output->wrap_nonce);
    copyAt(bytes, 132, &output->wrapped_cek);
    copyAt(bytes, 164, &output->wrapped_cek_tag);
    if (factorySize != 0) {
        std::copy_n(reinterpret_cast<const char*>(bytes.data + kFactoryOffset),
                    factorySize, output->original_factory.begin());
    }
    return Status::kSuccess;
}

Status validateTopology(
    const HeaderV2& header,
    const RecordV2* records,
    std::size_t record_count,
    ByteView encoded_chunks) noexcept {
    if (records == nullptr || record_count != header.dex_count || !validView(encoded_chunks) ||
        encoded_chunks.size != header.chunk_table_size) {
        return Status::kInvalidArgument;
    }
    std::uint64_t totalOriginal = 0;
    std::uint64_t payloadOffset = 0;
    std::uint32_t firstChunk = 0;
    std::size_t globalChunk = 0;
    for (std::size_t index = 0; index < record_count; ++index) {
        const RecordV2& record = records[index];
        std::uint64_t roundedCompressed = 0;
        if (!checkedAdd(record.compressed_length, kChunkPlaintextMax - 1, &roundedCompressed)) {
            return Status::kLimitExceeded;
        }
        const std::uint64_t expectedChunks = roundedCompressed / kChunkPlaintextMax;
        if (record.ordinal != index || record.first_chunk_index != firstChunk ||
            record.payload_offset != payloadOffset || record.chunk_count != expectedChunks) {
            return Status::kFormat;
        }
        if (!checkedAdd(totalOriginal, record.original_length, &totalOriginal) ||
            totalOriginal > kMaxTotalDexBytes) {
            return Status::kLimitExceeded;
        }
        for (std::uint32_t ordinal = 0; ordinal < record.chunk_count; ++ordinal) {
            ChunkV2 chunk{};
            const Status parsed = parseChunkV2(
                {encoded_chunks.data + globalChunk * kChunkBytes, kChunkBytes}, &chunk);
            const std::uint64_t compressedOffset =
                static_cast<std::uint64_t>(ordinal) * kChunkPlaintextMax;
            const std::uint32_t expectedLength = static_cast<std::uint32_t>(std::min<std::uint64_t>(
                record.compressed_length - compressedOffset, kChunkPlaintextMax));
            std::uint64_t tagOffset = 0;
            std::uint64_t expectedPayload = 0;
            if (parsed != Status::kSuccess ||
                !checkedMultiply(ordinal, kGcmTagBytes, &tagOffset) ||
                !checkedAdd(record.payload_offset, compressedOffset, &expectedPayload) ||
                !checkedAdd(expectedPayload, tagOffset, &expectedPayload) ||
                chunk.record_ordinal != record.ordinal || chunk.chunk_ordinal != ordinal ||
                chunk.compressed_offset != compressedOffset || chunk.payload_offset != expectedPayload ||
                chunk.plaintext_length != expectedLength) {
                return Status::kFormat;
            }
            ++globalChunk;
        }
        if (record.chunk_count > std::numeric_limits<std::uint32_t>::max() - firstChunk) {
            return Status::kLimitExceeded;
        }
        firstChunk += record.chunk_count;
        std::uint64_t tags = 0;
        if (!checkedMultiply(record.chunk_count, kGcmTagBytes, &tags) ||
            !checkedAdd(payloadOffset, record.compressed_length, &payloadOffset) ||
            !checkedAdd(payloadOffset, tags, &payloadOffset)) {
            return Status::kLimitExceeded;
        }
    }
    return firstChunk == header.chunk_count && globalChunk == header.chunk_count &&
                   payloadOffset == header.payload_size
               ? Status::kSuccess
               : Status::kFormat;
}

}  // namespace ah::container
