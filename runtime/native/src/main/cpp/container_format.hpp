#ifndef AH_RUNTIME_CONTAINER_FORMAT_HPP
#define AH_RUNTIME_CONTAINER_FORMAT_HPP

#include <array>
#include <cstddef>
#include <cstdint>

namespace ah::container {

constexpr std::size_t kHeaderBytes = 160;
constexpr std::size_t kRecordBytes = 128;
constexpr std::size_t kChunkBytes = 32;
constexpr std::size_t kConfigBytes = 768;
constexpr std::size_t kNativeShareSlotBytes = 104;
constexpr std::size_t kDigestBytes = 32;
constexpr std::size_t kIdBytes = 16;
constexpr std::size_t kMaxDex = 64;
constexpr std::size_t kMaxLineage = 16;
constexpr std::size_t kMaxChunks = 65'536;
constexpr std::uint32_t kChunkPlaintextMax = 65'536;
constexpr std::uint64_t kMaxDexBytes = 536'870'912ULL;
constexpr std::uint64_t kMaxCompressedDexBytes = 537'034'781ULL;
constexpr std::uint64_t kMaxTotalDexBytes = 4'294'967'296ULL;

enum class Status : std::uint8_t {
    kSuccess = 0,
    kInvalidArgument = 1,
    kFormat = 2,
    kVersion = 3,
    kLimitExceeded = 4,
    kAuthenticationFailed = 5,
};

struct ByteView {
    const std::uint8_t* data;
    std::size_t size;
};

struct HeaderV2 {
    std::uint32_t dex_count{};
    std::uint32_t signer_policy_size{};
    std::uint32_t record_table_size{};
    std::uint32_t chunk_count{};
    std::uint32_t chunk_table_size{};
    std::uint64_t payload_size{};
    std::array<std::uint8_t, kIdBytes> build_id{};
    std::array<std::uint8_t, kIdBytes> key_slot_id{};
    std::array<std::uint8_t, kDigestBytes> config_sha256{};
    std::array<std::uint8_t, kDigestBytes> manifest_mac{};
};

struct SignerPolicyV1 {
    std::array<std::uint8_t, kDigestBytes> current_signer_sha256{};
    std::array<std::array<std::uint8_t, kDigestBytes>, kMaxLineage> lineage_sha256{};
    std::uint16_t lineage_count{};
};

struct RecordV2 {
    std::uint32_t ordinal{};
    std::array<char, 25> name{};
    std::uint64_t original_length{};
    std::uint64_t compressed_length{};
    std::uint32_t chunk_count{};
    std::uint32_t first_chunk_index{};
    std::uint64_t payload_offset{};
    std::array<std::uint8_t, 8> nonce_prefix{};
    std::array<std::uint8_t, kDigestBytes> original_sha256{};
};

struct ChunkV2 {
    std::uint32_t record_ordinal{};
    std::uint32_t chunk_ordinal{};
    std::uint64_t compressed_offset{};
    std::uint64_t payload_offset{};
    std::uint32_t plaintext_length{};
};

struct ConfigV2 {
    std::uint16_t flags{};
    std::uint16_t container_major{};
    std::uint16_t signer_policy_version{};
    std::uint16_t risk_policy_version{};
    std::array<std::uint8_t, kIdBytes> build_id{};
    std::array<std::uint8_t, kIdBytes> key_slot_id{};
    std::array<std::uint8_t, kDigestBytes> current_signer_sha256{};
    std::array<std::uint8_t, kDigestBytes> r_java{};
    std::array<std::uint8_t, 12> wrap_nonce{};
    std::array<std::uint8_t, kDigestBytes> wrapped_cek{};
    std::array<std::uint8_t, 16> wrapped_cek_tag{};
    std::array<char, 513> original_factory{};
    std::uint16_t original_factory_size{};
};

struct NativeShareSlotV1 {
    std::uint16_t abi_id{};
    std::array<std::uint8_t, kIdBytes> key_slot_id{};
    std::array<std::uint8_t, kIdBytes> build_id{};
    std::array<std::uint8_t, kDigestBytes> r_native{};
    std::array<std::uint8_t, kDigestBytes> slot_sha256{};
};

Status parseHeaderV2(ByteView bytes, HeaderV2* output) noexcept;
Status parseSignerPolicyV1(ByteView bytes, SignerPolicyV1* output) noexcept;
Status parseRecordV2(ByteView bytes, RecordV2* output) noexcept;
Status parseChunkV2(ByteView bytes, ChunkV2* output) noexcept;
Status parseConfigV2(ByteView bytes, ConfigV2* output) noexcept;
Status parseNativeShareSlotV1(ByteView bytes, std::uint16_t expected_abi_id,
                              NativeShareSlotV1* output) noexcept;

Status validateTopology(
    const HeaderV2& header,
    const RecordV2* records,
    std::size_t record_count,
    ByteView encoded_chunks) noexcept;

bool constantTimeEqual(ByteView left, ByteView right) noexcept;

}  // namespace ah::container

#endif
