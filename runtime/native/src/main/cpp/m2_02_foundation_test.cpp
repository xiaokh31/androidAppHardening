#include "container_format.hpp"
#include "crypto_backend.hpp"
#include "payload_metadata.hpp"
#include "payload_memory.hpp"
#include "zip_assets.hpp"

#include <algorithm>
#include <array>
#include <cstdint>
#include <cstring>
#include <utility>
#include <vector>

namespace {

void put16(std::vector<std::uint8_t>* bytes, std::size_t offset, std::uint16_t value) {
    (*bytes)[offset] = static_cast<std::uint8_t>(value);
    (*bytes)[offset + 1] = static_cast<std::uint8_t>(value >> 8U);
}

void put32(std::vector<std::uint8_t>* bytes, std::size_t offset, std::uint32_t value) {
    for (std::size_t index = 0; index < 4; ++index) {
        (*bytes)[offset + index] = static_cast<std::uint8_t>(value >> (index * 8U));
    }
}

std::uint16_t get16(const std::vector<std::uint8_t>& bytes, std::size_t offset) {
    return static_cast<std::uint16_t>(bytes[offset]) |
           static_cast<std::uint16_t>(bytes[offset + 1]) << 8U;
}

std::uint32_t get32(const std::vector<std::uint8_t>& bytes, std::size_t offset) {
    return static_cast<std::uint32_t>(bytes[offset]) |
           static_cast<std::uint32_t>(bytes[offset + 1]) << 8U |
           static_cast<std::uint32_t>(bytes[offset + 2]) << 16U |
           static_cast<std::uint32_t>(bytes[offset + 3]) << 24U;
}

std::uint32_t crc32(const std::vector<std::uint8_t>& bytes) {
    std::uint32_t crc = 0xffffffffU;
    for (std::uint8_t value : bytes) {
        crc ^= value;
        for (unsigned bit = 0; bit < 8; ++bit) {
            const std::uint32_t mask = 0U - (crc & 1U);
            crc = (crc >> 1U) ^ (0xedb88320U & mask);
        }
    }
    return ~crc;
}

struct ZipSpec {
    const char* name;
    std::vector<std::uint8_t> data;
};

std::vector<std::uint8_t> buildZip(const std::vector<ZipSpec>& specs) {
    struct Central {
        const ZipSpec* spec;
        std::uint32_t crc;
        std::uint32_t local;
    };
    std::vector<std::uint8_t> bytes;
    std::vector<Central> central;
    for (const ZipSpec& spec : specs) {
        const std::size_t name_size = std::strlen(spec.name);
        const std::size_t local = bytes.size();
        const std::size_t prefix = local + 30 + name_size;
        const std::size_t extra = (4096 - prefix % 4096) % 4096;
        bytes.resize(prefix + extra, 0);
        put32(&bytes, local, 0x04034b50U);
        put16(&bytes, local + 4, 20);
        put32(&bytes, local + 14, crc32(spec.data));
        put32(&bytes, local + 18, static_cast<std::uint32_t>(spec.data.size()));
        put32(&bytes, local + 22, static_cast<std::uint32_t>(spec.data.size()));
        put16(&bytes, local + 26, static_cast<std::uint16_t>(name_size));
        put16(&bytes, local + 28, static_cast<std::uint16_t>(extra));
        std::memcpy(bytes.data() + local + 30, spec.name, name_size);
        bytes.insert(bytes.end(), spec.data.begin(), spec.data.end());
        central.push_back({&spec, crc32(spec.data), static_cast<std::uint32_t>(local)});
    }
    const std::size_t central_offset = bytes.size();
    for (const Central& item : central) {
        const std::size_t name_size = std::strlen(item.spec->name);
        const std::size_t offset = bytes.size();
        bytes.resize(offset + 46 + name_size, 0);
        put32(&bytes, offset, 0x02014b50U);
        put16(&bytes, offset + 4, 20);
        put16(&bytes, offset + 6, 20);
        put32(&bytes, offset + 16, item.crc);
        put32(&bytes, offset + 20, static_cast<std::uint32_t>(item.spec->data.size()));
        put32(&bytes, offset + 24, static_cast<std::uint32_t>(item.spec->data.size()));
        put16(&bytes, offset + 28, static_cast<std::uint16_t>(name_size));
        put32(&bytes, offset + 42, item.local);
        std::memcpy(bytes.data() + offset + 46, item.spec->name, name_size);
    }
    const std::size_t eocd = bytes.size();
    bytes.resize(eocd + 22, 0);
    put32(&bytes, eocd, 0x06054b50U);
    put16(&bytes, eocd + 8, static_cast<std::uint16_t>(central.size()));
    put16(&bytes, eocd + 10, static_cast<std::uint16_t>(central.size()));
    put32(&bytes, eocd + 12, static_cast<std::uint32_t>(eocd - central_offset));
    put32(&bytes, eocd + 16, static_cast<std::uint32_t>(central_offset));
    return bytes;
}

int testCryptoExtensions() {
    constexpr std::array<std::uint8_t, 32> sha_expected{
        0xba, 0x78, 0x16, 0xbf, 0x8f, 0x01, 0xcf, 0xea, 0x41, 0x41, 0x40, 0xde,
        0x5d, 0xae, 0x22, 0x23, 0xb0, 0x03, 0x61, 0xa3, 0x96, 0x17, 0x7a, 0x9c,
        0xb4, 0x10, 0xff, 0x61, 0xf2, 0x00, 0x15, 0xad};
    const std::array<std::uint8_t, 3> abc{'a', 'b', 'c'};
    std::array<std::uint8_t, 32> output{};
    if (ah::crypto::sha256(abc.data(), abc.size(), output.data(), output.size()) !=
            ah::crypto::Status::kSuccess || output != sha_expected) {
        return 1;
    }
    constexpr std::array<std::uint8_t, 32> hmac_expected{
        0xb0, 0x34, 0x4c, 0x61, 0xd8, 0xdb, 0x38, 0x53, 0x5c, 0xa8, 0xaf, 0xce,
        0xaf, 0x0b, 0xf1, 0x2b, 0x88, 0x1d, 0xc2, 0x00, 0xc9, 0x83, 0x3d, 0xa7,
        0x26, 0xe9, 0x37, 0x6c, 0x2e, 0x32, 0xcf, 0xf7};
    std::array<std::uint8_t, 20> key{};
    key.fill(0x0b);
    constexpr std::array<std::uint8_t, 8> hi_there{'H', 'i', ' ', 'T', 'h', 'e', 'r', 'e'};
    const std::array<ah::crypto::BufferView, 2> segments{{
        {hi_there.data(), 3}, {hi_there.data() + 3, hi_there.size() - 3}}};
    if (ah::crypto::hmacSha256(key.data(), key.size(), segments.data(), segments.size(),
                               output.data(), output.size()) != ah::crypto::Status::kSuccess ||
        output != hmac_expected) {
        return 2;
    }
    return 0;
}

int testSlotAndZip() {
    std::array<std::uint8_t, ah::container::kNativeShareSlotBytes> slot{};
    std::memcpy(slot.data(), "AHS1", 4);
    slot[4] = 1;
    slot[6] = 4;
    slot[8] = 1;
    slot[24] = 2;
    slot[40] = 3;
    if (ah::crypto::sha256(slot.data(), 72, slot.data() + 72, 32) != ah::crypto::Status::kSuccess) {
        return 1;
    }
    ah::container::NativeShareSlotV1 parsed{};
    if (ah::container::parseNativeShareSlotV1({slot.data(), slot.size()}, 4, &parsed) !=
        ah::container::Status::kSuccess) {
        return 2;
    }

    const ZipSpec config{"assets/ah/runtime/config.bin",
                         std::vector<std::uint8_t>(ah::container::kConfigBytes, 0x41)};
    const ZipSpec payload{"assets/ah/runtime/payload.ahdc",
                          std::vector<std::uint8_t>(ah::container::kHeaderBytes, 0x42)};
    const auto valid_apk = buildZip({config, payload});
    auto apk = valid_apk;
    ah::zip::FixedAssets assets{};
    if (ah::zip::locateFixedAssets({apk.data(), apk.size()}, &assets) != ah::zip::Status::kSuccess ||
        assets.config.size != ah::container::kConfigBytes ||
        assets.payload.size != ah::container::kHeaderBytes) {
        return 3;
    }
    const std::size_t config_offset = static_cast<std::size_t>(assets.config.data - apk.data());
    auto duplicate = buildZip({config, payload, config});
    if (ah::zip::locateFixedAssets({duplicate.data(), duplicate.size()}, &assets) !=
        ah::zip::Status::kDuplicate) {
        return 4;
    }
    apk[config_offset] ^= 1;
    if (ah::zip::locateFixedAssets({apk.data(), apk.size()}, &assets) != ah::zip::Status::kCrcMismatch) {
        return 5;
    }

    const std::size_t central = get32(valid_apk, valid_apk.size() - 6);
    const std::uint32_t config_local = get32(valid_apk, central + 42);
    const auto expect = [&assets](std::vector<std::uint8_t> candidate, ah::zip::Status expected) {
        return ah::zip::locateFixedAssets({candidate.data(), candidate.size()}, &assets) == expected;
    };
    if (!expect(buildZip({config}), ah::zip::Status::kMissing)) return 6;

    auto encrypted = valid_apk;
    put16(&encrypted, central + 8, 1);
    put16(&encrypted, config_local + 6, 1);
    if (!expect(std::move(encrypted), ah::zip::Status::kUnsupported)) return 7;
    auto descriptor = valid_apk;
    put16(&descriptor, central + 8, 1U << 3U);
    put16(&descriptor, config_local + 6, 1U << 3U);
    if (!expect(std::move(descriptor), ah::zip::Status::kUnsupported)) return 8;
    auto deflated = valid_apk;
    put16(&deflated, central + 10, 8);
    put16(&deflated, config_local + 8, 8);
    if (!expect(std::move(deflated), ah::zip::Status::kUnsupported)) return 9;
    auto local_mismatch = valid_apk;
    put16(&local_mismatch, config_local + 6, 1);
    if (!expect(std::move(local_mismatch), ah::zip::Status::kFormat)) return 10;
    auto misaligned = valid_apk;
    const std::uint16_t extra = get16(misaligned, config_local + 28);
    put16(&misaligned, config_local + 28, static_cast<std::uint16_t>(extra - 1U));
    if (!expect(std::move(misaligned), ah::zip::Status::kFormat)) return 11;
    auto zip64 = valid_apk;
    put32(&zip64, central + 20, 0xffffffffU);
    if (!expect(std::move(zip64), ah::zip::Status::kUnsupported)) return 12;
    auto multi_disk = valid_apk;
    put16(&multi_disk, central + 34, 1);
    if (!expect(std::move(multi_disk), ah::zip::Status::kUnsupported)) return 13;
    auto bad_local_offset = valid_apk;
    put32(&bad_local_offset, central + 42, static_cast<std::uint32_t>(central));
    if (!expect(std::move(bad_local_offset), ah::zip::Status::kFormat)) return 14;

    auto local_header_overlap = valid_apk;
    const std::size_t payload_central =
        central + 46U + std::strlen(config.name);
    const std::uint32_t payload_local = get32(local_header_overlap, payload_central + 42);
    const std::size_t payload_name_size = get16(local_header_overlap, payload_local + 26);
    const std::size_t payload_extra_size = get16(local_header_overlap, payload_local + 28);
    const std::size_t payload_data =
        payload_local + 30U + payload_name_size + payload_extra_size;
    const std::size_t overlapping_local = config_offset;
    const std::size_t overlapping_prefix = overlapping_local + 30U + payload_name_size;
    if (payload_data <= overlapping_prefix || payload_data - overlapping_prefix > 0xffffU) {
        return 15;
    }
    std::copy_n(local_header_overlap.begin() + payload_local,
                30U + payload_name_size,
                local_header_overlap.begin() + overlapping_local);
    put16(&local_header_overlap, overlapping_local + 28,
          static_cast<std::uint16_t>(payload_data - overlapping_prefix));
    put32(&local_header_overlap, payload_central + 42,
          static_cast<std::uint32_t>(overlapping_local));
    const std::vector<std::uint8_t> modified_config(
        local_header_overlap.begin() + config_offset,
        local_header_overlap.begin() + config_offset + config.data.size());
    const std::uint32_t modified_config_crc = crc32(modified_config);
    put32(&local_header_overlap, config_local + 14, modified_config_crc);
    put32(&local_header_overlap, central + 16, modified_config_crc);
    if (!expect(std::move(local_header_overlap), ah::zip::Status::kFormat)) return 16;

    auto truncated = valid_apk;
    truncated.pop_back();
    if (!expect(std::move(truncated), ah::zip::Status::kFormat)) return 17;
    return 0;
}

int testMappingTransaction() {
    ah::memory::PayloadTransaction transaction{};
    ah::memory::Mapping* first = nullptr;
    ah::memory::Mapping* second = nullptr;
    if (transaction.allocate(4096, &first) != ah::memory::Status::kSuccess ||
        transaction.allocate(8192, &second) != ah::memory::Status::kSuccess ||
        first == nullptr || second == nullptr || first->data == second->data) {
        return 1;
    }
    std::fill_n(first->data, first->size, static_cast<std::uint8_t>(0xa5));
    std::fill_n(second->data, second->size, static_cast<std::uint8_t>(0x5a));
    ah::memory::PayloadHandle handle{};
    if (transaction.commit(&handle) != ah::memory::Status::kSuccess || handle.size() != 2 ||
        !handle.mapping(0).read_only || !handle.mapping(1).read_only ||
        handle.close() != ah::memory::Status::kSuccess || handle.size() != 0) {
        return 2;
    }
    ah::memory::PayloadTransaction rollback{};
    if (rollback.allocate(4096, &first) != ah::memory::Status::kSuccess ||
        rollback.rollback() != ah::memory::Status::kSuccess || rollback.size() != 0) {
        return 3;
    }
    return 0;
}

int testMetadataEncoding() {
    ah::payload::UntrustedBinding binding{};
    binding.build_id[0] = 1;
    binding.key_slot_id[15] = 2;
    binding.current_signer_sha256[31] = 3;
    std::array<std::uint8_t, ah::metadata::kBindingBytes> binding_bytes{};
    std::size_t written = 0;
    if (ah::metadata::encodeUntrustedBinding(
            binding, binding_bytes.data(), binding_bytes.size(), &written) !=
            ah::metadata::Status::kSuccess ||
        written != binding_bytes.size() ||
        std::memcmp(binding_bytes.data(), "AHUB", 4) != 0 || binding_bytes[8] != 1 ||
        binding_bytes[39] != 2 || binding_bytes[71] != 3) {
        return 1;
    }

    ah::payload::AuthenticatedMetadata metadata{};
    metadata.container_major = 2;
    metadata.container_minor = 0;
    metadata.signer_policy_version = 1;
    metadata.risk_policy_version = 1;
    metadata.build_id[0] = 4;
    metadata.key_slot_id[0] = 5;
    metadata.package_name_sha256[0] = 6;
    metadata.current_signer_sha256[0] = 7;
    metadata.original_factory_size = 20;
    std::memcpy(metadata.original_factory.data(), "ah.fixture.RealClass", 20);
    metadata.signer_lineage_count = 2;
    metadata.signer_lineage_sha256[0][0] = 8;
    metadata.signer_lineage_sha256[1][31] = 9;
    std::array<std::uint8_t, ah::metadata::kMetadataMaxBytes> encoded{};
    if (ah::metadata::encodeAuthenticatedMetadata(
            metadata, encoded.data(), encoded.size(), &written) !=
            ah::metadata::Status::kSuccess ||
        written != ah::metadata::kMetadataFixedBytes + 20 + 64 ||
        std::memcmp(encoded.data(), "AHMD", 4) != 0 || encoded[8] != 2 ||
        encoded[24] != 4 || encoded[40] != 5 || encoded[56] != 6 || encoded[88] != 7 ||
        encoded[ah::metadata::kMetadataFixedBytes + 20] != 8 ||
        encoded[written - 1] != 9) {
        return 2;
    }
    metadata.signer_lineage_count = 0;
    if (ah::metadata::encodeAuthenticatedMetadata(
            metadata, encoded.data(), encoded.size(), &written) !=
        ah::metadata::Status::kInvalidArgument) {
        return 3;
    }
    return 0;
}

int testDeterministicParserFuzz() {
    std::uint64_t state = 0x9e3779b97f4a7c15ULL;
    const auto next = [&state]() -> std::uint64_t {
        state ^= state << 13U;
        state ^= state >> 7U;
        state ^= state << 17U;
        return state;
    };
    std::array<std::uint8_t, 2048> bytes{};
    std::size_t accepted = 0;
    for (std::size_t iteration = 0; iteration < 20'000; ++iteration) {
        const std::size_t size = static_cast<std::size_t>(next() % (bytes.size() + 1U));
        for (std::size_t index = 0; index < size; ++index) {
            bytes[index] = static_cast<std::uint8_t>(next());
        }
        const ah::container::ByteView view{bytes.data(), size};
        ah::container::HeaderV2 header{};
        ah::container::SignerPolicyV1 signer{};
        ah::container::RecordV2 record{};
        ah::container::ChunkV2 chunk{};
        ah::container::ConfigV2 config{};
        ah::container::NativeShareSlotV1 slot{};
        ah::zip::FixedAssets assets{};
        accepted += ah::container::parseHeaderV2(view, &header) ==
                    ah::container::Status::kSuccess;
        accepted += ah::container::parseSignerPolicyV1(view, &signer) ==
                    ah::container::Status::kSuccess;
        accepted += ah::container::parseRecordV2(view, &record) ==
                    ah::container::Status::kSuccess;
        accepted += ah::container::parseChunkV2(view, &chunk) ==
                    ah::container::Status::kSuccess;
        accepted += ah::container::parseConfigV2(view, &config) ==
                    ah::container::Status::kSuccess;
        accepted += ah::container::parseNativeShareSlotV1(
                        view, static_cast<std::uint16_t>((next() % 4U) + 1U), &slot) ==
                    ah::container::Status::kSuccess;
        accepted += ah::zip::locateFixedAssets(view, &assets) == ah::zip::Status::kSuccess;
    }
    ah::container::HeaderV2 output{};
    if (ah::container::parseHeaderV2({nullptr, 1}, &output) !=
            ah::container::Status::kInvalidArgument ||
        ah::zip::locateFixedAssets({nullptr, 1}, nullptr) !=
            ah::zip::Status::kInvalidArgument) {
        return 1;
    }
    return accepted <= 20'000U * 7U ? 0 : 2;
}

}  // namespace

int runM202FoundationSelfTests() {
    const int crypto = testCryptoExtensions();
    const int format = testSlotAndZip();
    const int mapping = testMappingTransaction();
    const int metadata = testMetadataEncoding();
    const int fuzz = testDeterministicParserFuzz();
    return crypto == 0 && format == 0 && mapping == 0 && metadata == 0 && fuzz == 0
               ? 0
               : 10000 * crypto + 1000 * format + 100 * mapping + 10 * metadata + fuzz;
}
