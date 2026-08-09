#include "authenticated_payload.hpp"

#include "crypto_backend.hpp"

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <limits>

#if defined(AH_M2_02_HOST_TESTING)
#include <atomic>
#endif

#include <zlib.h>

namespace ah::payload {
namespace {

constexpr std::size_t kTagBytes = 16;
constexpr std::size_t kConfigAadBytes = 132;
constexpr char kKekDomain[] = "AHDC offline KEK v1";
constexpr char kManifestDomain[] = "AHDC manifest v2";
constexpr char kRecordDomain[] = "AHDC record v2";
constexpr char kChunkDomain[] = "AHDC-GCM-V2";
constexpr std::size_t kChunkAadBytes = sizeof(kChunkDomain) - 1 + 4 + 16 + 16 + 32 + 32 +
                                       container::kRecordBytes + container::kChunkBytes;

struct alignas(std::max_align_t) ZlibAllocationHeader {
    std::size_t payload_size;
};

#if defined(AH_M2_02_HOST_TESTING)
std::atomic<std::size_t> g_zlib_live_allocations{0};
std::atomic<std::size_t> g_zlib_total_frees{0};
std::atomic<std::size_t> g_zlib_zeroized_frees{0};
#endif

voidpf zlibAllocate(voidpf, uInt items, uInt size) noexcept {
    if (items == 0 || size == 0 ||
        static_cast<std::size_t>(items) >
            std::numeric_limits<std::size_t>::max() / static_cast<std::size_t>(size)) {
        return nullptr;
    }
    const std::size_t payload_size =
        static_cast<std::size_t>(items) * static_cast<std::size_t>(size);
    if (payload_size > std::numeric_limits<std::size_t>::max() -
                           sizeof(ZlibAllocationHeader)) {
        return nullptr;
    }
    auto* allocation = static_cast<ZlibAllocationHeader*>(
        std::calloc(1, sizeof(ZlibAllocationHeader) + payload_size));
    if (allocation == nullptr) {
        return nullptr;
    }
    allocation->payload_size = payload_size;
#if defined(AH_M2_02_HOST_TESTING)
    g_zlib_live_allocations.fetch_add(1, std::memory_order_relaxed);
#endif
    return allocation + 1;
}

void zlibRelease(voidpf, voidpf address) noexcept {
    if (address == nullptr) {
        return;
    }
    auto* allocation = static_cast<ZlibAllocationHeader*>(address) - 1;
    const std::size_t total = sizeof(ZlibAllocationHeader) + allocation->payload_size;
    crypto::secureZero(allocation, total);
#if defined(AH_M2_02_HOST_TESTING)
    bool all_zero = true;
    const auto* bytes = reinterpret_cast<const std::uint8_t*>(allocation);
    for (std::size_t index = 0; index < total; ++index) {
        all_zero = all_zero && bytes[index] == 0;
    }
    g_zlib_total_frees.fetch_add(1, std::memory_order_relaxed);
    if (all_zero) {
        g_zlib_zeroized_frees.fetch_add(1, std::memory_order_relaxed);
    }
    g_zlib_live_allocations.fetch_sub(1, std::memory_order_relaxed);
#endif
    std::free(allocation);
}

struct AuthenticatedContainer {
    container::HeaderV2 header{};
    container::SignerPolicyV1 signer{};
    container::ConfigV2 config{};
    std::array<container::RecordV2, container::kMaxDex> records{};
    container::ByteView encoded_header{};
    container::ByteView encoded_signer{};
    container::ByteView encoded_records{};
    container::ByteView encoded_chunks{};
    container::ByteView payload{};
    std::array<std::uint8_t, 32> cek{};
    std::array<std::uint8_t, 32> package_sha256{};

    ~AuthenticatedContainer() noexcept {
        crypto::secureZero(cek.data(), cek.size());
    }
};

bool valid(container::ByteView bytes) noexcept {
    return bytes.size == 0 || bytes.data != nullptr;
}

bool equal(const std::uint8_t* left, const std::uint8_t* right, std::size_t size) noexcept {
    return container::constantTimeEqual({left, size}, {right, size});
}

bool add(std::size_t left, std::size_t right, std::size_t* output) noexcept {
    if (output == nullptr || left > std::numeric_limits<std::size_t>::max() - right) {
        return false;
    }
    *output = left + right;
    return true;
}

Status cryptoStatus(crypto::Status status) noexcept {
    if (status == crypto::Status::kAuthenticationFailed) {
        return Status::kAuthentication;
    }
    return status == crypto::Status::kSuccess ? Status::kSuccess : Status::kCrypto;
}

Status parseStructure(const OpenRequest& request, AuthenticatedContainer* output) noexcept {
    if (output == nullptr || request.assets.config.size != container::kConfigBytes ||
        request.assets.payload.size < container::kHeaderBytes ||
        request.measured_signer_sha256.size != container::kDigestBytes ||
        request.framework_package_utf8.size == 0 || !valid(request.framework_package_utf8) ||
        !valid(request.measured_signer_sha256) || !valid(request.assets.config) ||
        !valid(request.assets.payload)) {
        return Status::kInvalidArgument;
    }
    container::Status parsed = container::parseConfigV2(request.assets.config, &output->config);
    if (parsed != container::Status::kSuccess) {
        return parsed == container::Status::kVersion ? Status::kVersion : Status::kFormat;
    }
    output->encoded_header = {request.assets.payload.data, container::kHeaderBytes};
    parsed = container::parseHeaderV2(output->encoded_header, &output->header);
    if (parsed != container::Status::kSuccess) {
        return parsed == container::Status::kVersion ? Status::kVersion : Status::kFormat;
    }

    std::size_t cursor = container::kHeaderBytes;
    std::size_t next = 0;
    if (!add(cursor, output->header.signer_policy_size, &next) || next > request.assets.payload.size) {
        return Status::kFormat;
    }
    output->encoded_signer = {request.assets.payload.data + cursor, output->header.signer_policy_size};
    parsed = container::parseSignerPolicyV1(output->encoded_signer, &output->signer);
    if (parsed != container::Status::kSuccess) {
        return parsed == container::Status::kVersion ? Status::kVersion : Status::kFormat;
    }
    cursor = next;
    if (!add(cursor, output->header.record_table_size, &next) || next > request.assets.payload.size) {
        return Status::kFormat;
    }
    output->encoded_records = {request.assets.payload.data + cursor, output->header.record_table_size};
    for (std::size_t index = 0; index < output->header.dex_count; ++index) {
        parsed = container::parseRecordV2(
            {output->encoded_records.data + index * container::kRecordBytes,
             container::kRecordBytes},
            &output->records[index]);
        if (parsed != container::Status::kSuccess || output->records[index].ordinal != index) {
            return Status::kFormat;
        }
    }
    cursor = next;
    if (!add(cursor, output->header.chunk_table_size, &next) || next > request.assets.payload.size) {
        return Status::kFormat;
    }
    output->encoded_chunks = {request.assets.payload.data + cursor, output->header.chunk_table_size};
    if (container::validateTopology(output->header, output->records.data(),
                                    output->header.dex_count, output->encoded_chunks) !=
        container::Status::kSuccess) {
        return Status::kFormat;
    }
    cursor = next;
    if (output->header.payload_size != request.assets.payload.size - cursor) {
        return Status::kTrailingData;
    }
    output->payload = {request.assets.payload.data + cursor,
                       static_cast<std::size_t>(output->header.payload_size)};
    return Status::kSuccess;
}

Status authenticate(const OpenRequest& request, AuthenticatedContainer* value) noexcept {
    container::NativeShareSlotV1 slot{};
    if (container::parseNativeShareSlotV1(request.native_share_slot, request.expected_abi_id, &slot) !=
        container::Status::kSuccess) {
        return Status::kBinding;
    }
    std::array<std::uint8_t, 32> digest{};
    crypto::Status crypto_status = crypto::sha256(
        request.native_share_slot.data, 72, digest.data(), digest.size());
    if (crypto_status != crypto::Status::kSuccess ||
        !equal(digest.data(), slot.slot_sha256.data(), digest.size())) {
        crypto::secureZero(digest.data(), digest.size());
        return crypto_status == crypto::Status::kSuccess ? Status::kBinding : Status::kCrypto;
    }
    crypto::secureZero(digest.data(), digest.size());

    if (!equal(slot.build_id.data(), value->config.build_id.data(), slot.build_id.size()) ||
        !equal(slot.key_slot_id.data(), value->config.key_slot_id.data(), slot.key_slot_id.size()) ||
        !equal(value->config.current_signer_sha256.data(), request.measured_signer_sha256.data,
               container::kDigestBytes)) {
        return Status::kBinding;
    }
    crypto_status = crypto::sha256(
        request.framework_package_utf8.data, request.framework_package_utf8.size,
        value->package_sha256.data(), value->package_sha256.size());
    if (crypto_status != crypto::Status::kSuccess) {
        return Status::kCrypto;
    }

    std::array<std::uint8_t, 32> root{};
    std::array<std::uint8_t, 32> kek{};
    std::array<std::uint8_t, sizeof(kKekDomain) - 1 + 64> kek_info{};
    for (std::size_t index = 0; index < root.size(); ++index) {
        root[index] = slot.r_native[index] ^ value->config.r_java[index];
    }
    std::memcpy(kek_info.data(), kKekDomain, sizeof(kKekDomain) - 1);
    std::memcpy(kek_info.data() + sizeof(kKekDomain) - 1,
                request.measured_signer_sha256.data, container::kDigestBytes);
    std::memcpy(kek_info.data() + sizeof(kKekDomain) - 1 + container::kDigestBytes,
                value->package_sha256.data(), value->package_sha256.size());
    crypto_status = crypto::hkdfSha256(
        root.data(), root.size(), value->config.build_id.data(), value->config.build_id.size(),
        kek_info.data(), kek_info.size(), kek.data(), kek.size());
    crypto::secureZero(root.data(), root.size());
    crypto::secureZero(kek_info.data(), kek_info.size());
    if (crypto_status != crypto::Status::kSuccess) {
        crypto::secureZero(kek.data(), kek.size());
        return Status::kCrypto;
    }
    std::size_t cek_size = 0;
    crypto_status = crypto::aes256GcmDecrypt(
        kek.data(), kek.size(), value->config.wrap_nonce.data(), value->config.wrap_nonce.size(),
        request.assets.config.data, kConfigAadBytes,
        value->config.wrapped_cek.data(), value->config.wrapped_cek.size(),
        value->config.wrapped_cek_tag.data(), value->config.wrapped_cek_tag.size(),
        value->cek.data(), value->cek.size(), &cek_size);
    crypto::secureZero(kek.data(), kek.size());
    if (crypto_status != crypto::Status::kSuccess || cek_size != value->cek.size()) {
        return cryptoStatus(crypto_status);
    }

    std::array<std::uint8_t, 32> manifest_key{};
    crypto_status = crypto::hkdfSha256(
        value->cek.data(), value->cek.size(), value->config.build_id.data(), value->config.build_id.size(),
        reinterpret_cast<const std::uint8_t*>(kManifestDomain), sizeof(kManifestDomain) - 1,
        manifest_key.data(), manifest_key.size());
    if (crypto_status != crypto::Status::kSuccess) {
        return Status::kCrypto;
    }
    std::array<std::uint8_t, container::kHeaderBytes> zero_mac_header{};
    std::memcpy(zero_mac_header.data(), value->encoded_header.data, zero_mac_header.size());
    std::fill(zero_mac_header.begin() + 104, zero_mac_header.begin() + 136, 0);
    const std::array<crypto::BufferView, 4> manifest_inputs{{
        {zero_mac_header.data(), zero_mac_header.size()},
        {value->encoded_signer.data, value->encoded_signer.size},
        {value->encoded_records.data, value->encoded_records.size},
        {value->encoded_chunks.data, value->encoded_chunks.size},
    }};
    std::array<std::uint8_t, 32> manifest_mac{};
    crypto_status = crypto::hmacSha256(
        manifest_key.data(), manifest_key.size(), manifest_inputs.data(), manifest_inputs.size(),
        manifest_mac.data(), manifest_mac.size());
    crypto::secureZero(manifest_key.data(), manifest_key.size());
    crypto::secureZero(zero_mac_header.data(), zero_mac_header.size());
    if (crypto_status != crypto::Status::kSuccess ||
        !equal(manifest_mac.data(), value->header.manifest_mac.data(), manifest_mac.size())) {
        crypto::secureZero(manifest_mac.data(), manifest_mac.size());
        return crypto_status == crypto::Status::kSuccess ? Status::kAuthentication : Status::kCrypto;
    }
    crypto::secureZero(manifest_mac.data(), manifest_mac.size());

    crypto_status = crypto::sha256(
        request.assets.config.data, request.assets.config.size, digest.data(), digest.size());
    if (crypto_status != crypto::Status::kSuccess ||
        !equal(digest.data(), value->header.config_sha256.data(), digest.size())) {
        crypto::secureZero(digest.data(), digest.size());
        return crypto_status == crypto::Status::kSuccess ? Status::kBinding : Status::kCrypto;
    }
    crypto::secureZero(digest.data(), digest.size());
    if (!equal(value->header.build_id.data(), value->config.build_id.data(), container::kIdBytes) ||
        !equal(value->header.key_slot_id.data(), value->config.key_slot_id.data(), container::kIdBytes) ||
        !equal(value->signer.current_signer_sha256.data(), request.measured_signer_sha256.data,
               container::kDigestBytes) || value->config.container_major != 2 ||
        value->config.signer_policy_version != 1) {
        return Status::kBinding;
    }
    return Status::kSuccess;
}

Status chunkStatus(int zlib_status) noexcept {
    if (zlib_status == Z_NEED_DICT) {
        return Status::kZlibDictionary;
    }
    if (zlib_status == Z_DATA_ERROR) {
        return Status::kZlibChecksum;
    }
    return Status::kZlibChecksum;
}

class RecordInflater final {
public:
    explicit RecordInflater(memory::Mapping* mapping) noexcept : mapping_(mapping) {}

    ~RecordInflater() noexcept {
        if (initialized_) {
            (void) inflateEnd(&stream_);
        }
        crypto::secureZero(&overflow_byte_, sizeof(overflow_byte_));
    }

    Status initialize() noexcept {
        if (mapping_ == nullptr || mapping_->data == nullptr || mapping_->size == 0 ||
            initialized_) {
            return Status::kInvalidArgument;
        }
        stream_.zalloc = zlibAllocate;
        stream_.zfree = zlibRelease;
        stream_.opaque = Z_NULL;
        if (inflateInit(&stream_) != Z_OK) {
            return Status::kOutOfMemory;
        }
        initialized_ = true;
        return Status::kSuccess;
    }

    Status consume(container::ByteView compressed, bool final_chunk) noexcept {
        if (!initialized_ || ended_ || compressed.data == nullptr || compressed.size == 0 ||
            compressed.size > std::numeric_limits<uInt>::max()) {
            return ended_ ? Status::kTrailingData : Status::kInvalidArgument;
        }
        if (first_input_) {
            if (compressed.size < 2 || (compressed.data[0] & 0x0fU) != Z_DEFLATED ||
                (compressed.data[0] >> 4U) > 7U ||
                ((static_cast<unsigned>(compressed.data[0]) << 8U) + compressed.data[1]) %
                        31U !=
                    0) {
                return Status::kZlibWrapper;
            }
            if ((compressed.data[1] & 0x20U) != 0) {
                return Status::kZlibDictionary;
            }
        }
        stream_.next_in = const_cast<Bytef*>(compressed.data);
        stream_.avail_in = static_cast<uInt>(compressed.size);
        while (stream_.avail_in != 0 && !ended_) {
            const bool at_limit = output_offset_ == mapping_->size;
            stream_.next_out = at_limit ? &overflow_byte_ : mapping_->data + output_offset_;
            stream_.avail_out = at_limit
                                    ? 1U
                                    : static_cast<uInt>(std::min<std::size_t>(
                                          mapping_->size - output_offset_,
                                          std::numeric_limits<uInt>::max()));
            const uInt before_out = stream_.avail_out;
            const uInt before_in = stream_.avail_in;
            const int zlib_status = inflate(&stream_, Z_NO_FLUSH);
            const uInt produced = before_out - stream_.avail_out;
            first_input_ = false;
            if (at_limit && produced != 0) {
                return Status::kLength;
            }
            output_offset_ += produced;
            if (zlib_status == Z_STREAM_END) {
                ended_ = true;
                return stream_.avail_in == 0 && final_chunk
                           ? Status::kSuccess
                           : Status::kTrailingData;
            }
            if (zlib_status != Z_OK) {
                return chunkStatus(zlib_status);
            }
            if (before_out == stream_.avail_out && before_in == stream_.avail_in &&
                stream_.avail_in != 0) {
                return Status::kZlibChecksum;
            }
        }
        return Status::kSuccess;
    }

    Status finish(Status primary) noexcept {
        int end_status = Z_OK;
        if (initialized_) {
            end_status = inflateEnd(&stream_);
            initialized_ = false;
        }
        crypto::secureZero(&overflow_byte_, sizeof(overflow_byte_));
        if (primary != Status::kSuccess) {
            return primary;
        }
        if (end_status != Z_OK) {
            return Status::kZlibChecksum;
        }
        return ended_ && output_offset_ == mapping_->size
                   ? Status::kSuccess
                   : Status::kLength;
    }

    bool ended() const noexcept { return ended_; }

private:
    memory::Mapping* mapping_{};
    z_stream stream_{};
    std::size_t output_offset_{};
    std::uint8_t overflow_byte_{};
    bool initialized_{};
    bool ended_{};
    bool first_input_{true};
};

Status inflateRecords(const OpenRequest& request, AuthenticatedContainer* value,
                      memory::PayloadTransaction* transaction) noexcept {
#if !defined(AH_M2_02_HOST_TESTING)
    (void) request;
#endif
    std::array<std::uint8_t, container::kChunkPlaintextMax> compressed{};
    std::array<std::uint8_t, kChunkAadBytes> aad{};
    std::array<std::uint8_t, 32> record_key{};
    std::array<std::uint8_t, sizeof(kRecordDomain) - 1 + 4> record_info{};
    Status result = Status::kSuccess;

    for (std::size_t record_index = 0; record_index < value->header.dex_count; ++record_index) {
        const container::RecordV2& record = value->records[record_index];
        memory::Mapping* mapping = nullptr;
        if (transaction->allocate(static_cast<std::size_t>(record.original_length), &mapping) !=
            memory::Status::kSuccess) {
            result = Status::kOutOfMemory;
            break;
        }
        std::memcpy(record_info.data(), kRecordDomain, sizeof(kRecordDomain) - 1);
        const std::uint32_t ordinal = record.ordinal;
        for (std::size_t byte = 0; byte < 4; ++byte) {
            record_info[sizeof(kRecordDomain) - 1 + byte] =
                static_cast<std::uint8_t>(ordinal >> (byte * 8U));
        }
        if (crypto::hkdfSha256(
                value->cek.data(), value->cek.size(), value->header.build_id.data(),
                value->header.build_id.size(), record_info.data(), record_info.size(),
                record_key.data(), record_key.size()) != crypto::Status::kSuccess) {
            result = Status::kCrypto;
            break;
        }

        RecordInflater inflater(mapping);
        result = inflater.initialize();
        if (result != Status::kSuccess) {
            break;
        }
        for (std::size_t local_chunk = 0; local_chunk < record.chunk_count; ++local_chunk) {
            const std::size_t global_chunk = record.first_chunk_index + local_chunk;
#if defined(AH_M2_02_HOST_TESTING)
            if (request.failure_probe != nullptr) {
                const Status injected = request.failure_probe(
                    global_chunk, FailureStage::kBeforeAuthentication,
                    request.failure_context);
                if (injected != Status::kSuccess) {
                    result = injected;
                    break;
                }
            }
#endif
            const container::ByteView encoded_chunk{
                value->encoded_chunks.data + global_chunk * container::kChunkBytes,
                container::kChunkBytes};
            container::ChunkV2 chunk{};
            if (container::parseChunkV2(encoded_chunk, &chunk) != container::Status::kSuccess ||
                chunk.payload_offset > value->payload.size ||
                chunk.plaintext_length + kTagBytes > value->payload.size - chunk.payload_offset) {
                result = Status::kFormat;
                break;
            }
            std::size_t aad_offset = 0;
            const auto append = [&aad, &aad_offset](const void* data, std::size_t size) {
                std::memcpy(aad.data() + aad_offset, data, size);
                aad_offset += size;
            };
            append(kChunkDomain, sizeof(kChunkDomain) - 1);
            append(value->encoded_header.data + 4, 4);
            append(value->header.build_id.data(), value->header.build_id.size());
            append(value->header.key_slot_id.data(), value->header.key_slot_id.size());
            append(value->signer.current_signer_sha256.data(), container::kDigestBytes);
            append(value->package_sha256.data(), value->package_sha256.size());
            append(value->encoded_records.data + record_index * container::kRecordBytes,
                   container::kRecordBytes);
            append(encoded_chunk.data, encoded_chunk.size);
            std::array<std::uint8_t, 12> nonce{};
            std::copy(record.nonce_prefix.begin(), record.nonce_prefix.end(), nonce.begin());
            for (std::size_t byte = 0; byte < 4; ++byte) {
                nonce[8 + byte] = static_cast<std::uint8_t>(chunk.chunk_ordinal >> (byte * 8U));
            }
            const std::uint8_t* ciphertext = value->payload.data + chunk.payload_offset;
            std::size_t plaintext_size = 0;
            const crypto::Status decrypted = crypto::aes256GcmDecrypt(
                record_key.data(), record_key.size(), nonce.data(), nonce.size(),
                aad.data(), aad_offset, ciphertext, chunk.plaintext_length,
                ciphertext + chunk.plaintext_length, kTagBytes,
                compressed.data(), compressed.size(), &plaintext_size);
            crypto::secureZero(aad.data(), aad.size());
            crypto::secureZero(nonce.data(), nonce.size());
            if (decrypted != crypto::Status::kSuccess || plaintext_size != chunk.plaintext_length) {
                result = decrypted == crypto::Status::kAuthenticationFailed
                             ? Status::kAuthentication
                             : Status::kCrypto;
                break;
            }
#if defined(AH_M2_02_HOST_TESTING)
            if (request.failure_probe != nullptr) {
                const Status injected = request.failure_probe(
                    global_chunk, FailureStage::kBeforeInflate,
                    request.failure_context);
                if (injected != Status::kSuccess) {
                    result = injected;
                }
            }
#endif

            if (result == Status::kSuccess) {
                result = inflater.consume(
                    {compressed.data(), plaintext_size},
                    local_chunk + 1 == record.chunk_count);
            }
            crypto::secureZero(compressed.data(), compressed.size());
#if defined(AH_M2_02_HOST_TESTING)
            if (result == Status::kSuccess && request.failure_probe != nullptr) {
                const Status injected = request.failure_probe(
                    global_chunk, FailureStage::kAfterInflate,
                    request.failure_context);
                if (injected != Status::kSuccess) {
                    result = injected;
                }
            }
#endif
            if (result != Status::kSuccess || inflater.ended()) {
                break;
            }
        }
        result = inflater.finish(result);
        crypto::secureZero(record_key.data(), record_key.size());
        crypto::secureZero(record_info.data(), record_info.size());
        if (result != Status::kSuccess) {
            break;
        }
        std::array<std::uint8_t, 32> digest{};
        if (crypto::sha256(mapping->data, mapping->size, digest.data(), digest.size()) !=
            crypto::Status::kSuccess) {
            result = Status::kCrypto;
        } else if (!equal(digest.data(), record.original_sha256.data(), digest.size())) {
            result = Status::kDigest;
        }
        crypto::secureZero(digest.data(), digest.size());
        if (result != Status::kSuccess) {
            break;
        }
    }
    crypto::secureZero(compressed.data(), compressed.size());
    crypto::secureZero(aad.data(), aad.size());
    crypto::secureZero(record_key.data(), record_key.size());
    crypto::secureZero(record_info.data(), record_info.size());
    return result;
}

}  // namespace

#if defined(AH_M2_02_HOST_TESTING)
void resetZlibCleanupEvidenceForTesting() noexcept {
    if (g_zlib_live_allocations.load(std::memory_order_relaxed) == 0) {
        g_zlib_total_frees.store(0, std::memory_order_relaxed);
        g_zlib_zeroized_frees.store(0, std::memory_order_relaxed);
    }
}

std::size_t zlibLiveAllocationCountForTesting() noexcept {
    return g_zlib_live_allocations.load(std::memory_order_relaxed);
}

std::size_t zlibTotalFreeCountForTesting() noexcept {
    return g_zlib_total_frees.load(std::memory_order_relaxed);
}

std::size_t zlibZeroizedFreeCountForTesting() noexcept {
    return g_zlib_zeroized_frees.load(std::memory_order_relaxed);
}

Status inflateCompressedForTesting(
    container::ByteView compressed, std::uint8_t* output, std::size_t output_size) noexcept {
    memory::Mapping mapping{output, output_size, false};
    RecordInflater inflater(&mapping);
    Status status = inflater.initialize();
    if (status == Status::kSuccess) {
        status = inflater.consume(compressed, true);
    }
    return inflater.finish(status);
}
#endif

Status openAuthenticatedPayload(
    const OpenRequest& request,
    memory::PayloadHandle* output,
    AuthenticatedMetadata* metadata_output,
    bool* cleanup_failed) noexcept {
    if (cleanup_failed != nullptr) {
        *cleanup_failed = false;
    }
    if (output == nullptr || metadata_output == nullptr || cleanup_failed == nullptr ||
        output->size() != 0) {
        return Status::kInvalidArgument;
    }
    *metadata_output = AuthenticatedMetadata{};
    AuthenticatedContainer value{};
    Status status = parseStructure(request, &value);
    if (status != Status::kSuccess) {
        return status;
    }
    status = authenticate(request, &value);
    if (status != Status::kSuccess) {
        return status;
    }
    memory::PayloadTransaction transaction{};
    status = inflateRecords(request, &value, &transaction);
    if (status != Status::kSuccess) {
        *cleanup_failed = transaction.rollback() == memory::Status::kCleanupFailed;
        return status;
    }
    const memory::Status committed = transaction.commit(output);
    if (committed != memory::Status::kSuccess) {
        *cleanup_failed = transaction.rollback() == memory::Status::kCleanupFailed;
        return committed == memory::Status::kProtectionFailed
                   ? Status::kMemoryProtection
                   : Status::kOutOfMemory;
    }
    metadata_output->container_major = value.config.container_major;
    metadata_output->container_minor = 0;
    metadata_output->signer_policy_version = value.config.signer_policy_version;
    metadata_output->risk_policy_version = value.config.risk_policy_version;
    metadata_output->build_id = value.config.build_id;
    metadata_output->key_slot_id = value.config.key_slot_id;
    metadata_output->package_name_sha256 = value.package_sha256;
    metadata_output->current_signer_sha256 = value.signer.current_signer_sha256;
    metadata_output->signer_lineage_count = value.signer.lineage_count;
    metadata_output->original_factory_size = value.config.original_factory_size;
    std::copy_n(value.config.original_factory.begin(), value.config.original_factory_size,
                metadata_output->original_factory.begin());
    std::copy_n(value.signer.lineage_sha256.begin(), value.signer.lineage_count,
                metadata_output->signer_lineage_sha256.begin());
    return Status::kSuccess;
}

Status inspectUntrustedBinding(
    const zip::FixedAssets& assets,
    UntrustedBinding* output) noexcept {
    if (output == nullptr) {
        return Status::kInvalidArgument;
    }
    *output = UntrustedBinding{};
    static constexpr std::array<std::uint8_t, container::kDigestBytes> kPlaceholderSigner{};
    static constexpr std::array<std::uint8_t, 1> kPlaceholderPackage{{'x'}};
    OpenRequest request{
        assets,
        {nullptr, 0},
        1,
        {kPlaceholderSigner.data(), kPlaceholderSigner.size()},
        {kPlaceholderPackage.data(), kPlaceholderPackage.size()},
    };
    AuthenticatedContainer value{};
    const Status status = parseStructure(request, &value);
    if (status != Status::kSuccess) {
        return status;
    }
    if (!equal(value.header.build_id.data(), value.config.build_id.data(), container::kIdBytes) ||
        !equal(value.header.key_slot_id.data(), value.config.key_slot_id.data(),
               container::kIdBytes) ||
        !equal(value.signer.current_signer_sha256.data(),
               value.config.current_signer_sha256.data(), container::kDigestBytes)) {
        return Status::kBinding;
    }
    output->build_id = value.config.build_id;
    output->key_slot_id = value.config.key_slot_id;
    output->current_signer_sha256 = value.config.current_signer_sha256;
    return Status::kSuccess;
}

}  // namespace ah::payload
