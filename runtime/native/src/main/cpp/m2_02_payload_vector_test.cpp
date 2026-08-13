#include "authenticated_payload.hpp"
#include "mapped_apk.hpp"

#include <array>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iterator>
#include <vector>

#include <zlib.h>

namespace {

std::vector<std::uint8_t> readFile(const char* path) {
    std::ifstream input(path, std::ios::binary);
    return input.good()
               ? std::vector<std::uint8_t>(std::istreambuf_iterator<char>(input), {})
               : std::vector<std::uint8_t>{};
}

ah::payload::OpenRequest requestFor(
    const std::vector<std::uint8_t>& config,
    const std::vector<std::uint8_t>& container,
    const std::vector<std::uint8_t>& slot) {
    static constexpr std::array<std::uint8_t, 32> kSigner{
        0x0b, 0x12, 0x19, 0x20, 0x27, 0x2e, 0x35, 0x3c,
        0x43, 0x4a, 0x51, 0x58, 0x5f, 0x66, 0x6d, 0x74,
        0x7b, 0x82, 0x89, 0x90, 0x97, 0x9e, 0xa5, 0xac,
        0xb3, 0xba, 0xc1, 0xc8, 0xcf, 0xd6, 0xdd, 0xe4};
    static constexpr std::array<std::uint8_t, 21> kPackage{
        'a', 'h', '.', 'f', 'i', 'x', 't', 'u', 'r', 'e', 's', '.',
        'c', 'o', 'n', 't', 'a', 'i', 'n', 'e', 'r'};
    return {
        {{config.data(), config.size()}, {container.data(), container.size()}},
        {slot.data(), slot.size()},
        4,
        {kSigner.data(), kSigner.size()},
        {kPackage.data(), kPackage.size()},
    };
}

int openExpect(
    const std::vector<std::uint8_t>& config,
    const std::vector<std::uint8_t>& container,
    const std::vector<std::uint8_t>& slot,
    ah::payload::Status expected) {
    ah::memory::PayloadHandle handle{};
    ah::payload::AuthenticatedMetadata metadata{};
    bool cleanup_failed = true;
    const ah::payload::Status status =
        ah::payload::openAuthenticatedPayload(requestFor(config, container, slot),
                                              &handle, &metadata, &cleanup_failed);
    if (status != expected || cleanup_failed || handle.size() != 0) {
        return 1;
    }
    return 0;
}

int openRequestExpect(ah::payload::OpenRequest request, ah::payload::Status expected) {
    ah::memory::PayloadHandle handle{};
    ah::payload::AuthenticatedMetadata metadata{};
    bool cleanup_failed = true;
    const ah::payload::Status status =
        ah::payload::openAuthenticatedPayload(request, &handle, &metadata, &cleanup_failed);
    if (status != expected || cleanup_failed || handle.size() != 0 ||
        ah::payload::zlibLiveAllocationCountForTesting() != 0) {
        return 1;
    }
    return 0;
}

struct InjectedFailure {
    std::size_t chunk;
    ah::payload::FailureStage stage;
    ah::payload::Status status;
};

ah::payload::Status injectAt(
    std::size_t chunk,
    ah::payload::FailureStage stage,
    void* context) noexcept {
    const auto* injected = static_cast<const InjectedFailure*>(context);
    return injected != nullptr && injected->chunk == chunk && injected->stage == stage
               ? injected->status
               : ah::payload::Status::kSuccess;
}

int openInjected(
    const std::vector<std::uint8_t>& config,
    const std::vector<std::uint8_t>& container,
    const std::vector<std::uint8_t>& slot,
    const InjectedFailure& injected,
    bool inject_cleanup_failure = false) {
    ah::memory::resetFailureInjectionForTesting();
    if (inject_cleanup_failure) {
        ah::memory::failReleaseAfterForTesting(0);
    }
    ah::payload::OpenRequest request = requestFor(config, container, slot);
    request.failure_probe = injectAt;
    request.failure_context = const_cast<InjectedFailure*>(&injected);
    ah::memory::PayloadHandle handle{};
    ah::payload::AuthenticatedMetadata metadata{};
    bool cleanup_failed = false;
    const ah::payload::Status status = ah::payload::openAuthenticatedPayload(
        request, &handle, &metadata, &cleanup_failed);
    ah::memory::resetFailureInjectionForTesting();
    if (status != injected.status || cleanup_failed != inject_cleanup_failure ||
        handle.size() != 0 || ah::memory::liveMappingCountForTesting() != 0) {
        return 1;
    }
    return 0;
}

bool mutateChunkTag(std::vector<std::uint8_t>* bytes, std::size_t chunk_index) {
    ah::container::HeaderV2 header{};
    if (bytes == nullptr || bytes->size() < ah::container::kHeaderBytes ||
        ah::container::parseHeaderV2({bytes->data(), ah::container::kHeaderBytes}, &header) !=
            ah::container::Status::kSuccess || chunk_index >= header.chunk_count) {
        return false;
    }
    const std::size_t chunk_table = ah::container::kHeaderBytes + header.signer_policy_size +
                                    header.record_table_size;
    const std::size_t payload = chunk_table + header.chunk_table_size;
    ah::container::ChunkV2 chunk{};
    if (payload > bytes->size() ||
        ah::container::parseChunkV2(
            {bytes->data() + chunk_table + chunk_index * ah::container::kChunkBytes,
             ah::container::kChunkBytes},
            &chunk) != ah::container::Status::kSuccess ||
        chunk.payload_offset > bytes->size() - payload ||
        chunk.plaintext_length + 16U > bytes->size() - payload - chunk.payload_offset) {
        return false;
    }
    (*bytes)[payload + static_cast<std::size_t>(chunk.payload_offset) + chunk.plaintext_length] ^= 1;
    return true;
}

bool mutateChunkCiphertext(std::vector<std::uint8_t>* bytes, std::size_t chunk_index) {
    ah::container::HeaderV2 header{};
    if (bytes == nullptr || bytes->size() < ah::container::kHeaderBytes ||
        ah::container::parseHeaderV2({bytes->data(), ah::container::kHeaderBytes}, &header) !=
            ah::container::Status::kSuccess || chunk_index >= header.chunk_count) {
        return false;
    }
    const std::size_t chunk_table = ah::container::kHeaderBytes + header.signer_policy_size +
                                    header.record_table_size;
    const std::size_t payload = chunk_table + header.chunk_table_size;
    ah::container::ChunkV2 chunk{};
    if (payload > bytes->size() ||
        ah::container::parseChunkV2(
            {bytes->data() + chunk_table + chunk_index * ah::container::kChunkBytes,
             ah::container::kChunkBytes},
            &chunk) != ah::container::Status::kSuccess ||
        chunk.plaintext_length == 0 || chunk.payload_offset > bytes->size() - payload ||
        chunk.plaintext_length + 16U > bytes->size() - payload - chunk.payload_offset) {
        return false;
    }
    (*bytes)[payload + static_cast<std::size_t>(chunk.payload_offset) +
             chunk.plaintext_length / 2U] ^= 1;
    return true;
}

std::vector<std::uint8_t> deflateForTesting(
    const std::vector<std::uint8_t>& plaintext,
    int window_bits,
    const std::vector<std::uint8_t>* dictionary = nullptr) {
    z_stream stream{};
    if (deflateInit2(
            &stream, Z_BEST_COMPRESSION, Z_DEFLATED, window_bits, 8,
            Z_DEFAULT_STRATEGY) != Z_OK) {
        return {};
    }
    if (dictionary != nullptr &&
        deflateSetDictionary(
            &stream, dictionary->data(), static_cast<uInt>(dictionary->size())) != Z_OK) {
        (void) deflateEnd(&stream);
        return {};
    }
    std::vector<std::uint8_t> encoded(deflateBound(&stream, plaintext.size()));
    stream.next_in = const_cast<Bytef*>(plaintext.data());
    stream.avail_in = static_cast<uInt>(plaintext.size());
    stream.next_out = encoded.data();
    stream.avail_out = static_cast<uInt>(encoded.size());
    const int status = deflate(&stream, Z_FINISH);
    const std::size_t written = stream.total_out;
    const int ended = deflateEnd(&stream);
    if (status != Z_STREAM_END || ended != Z_OK) {
        return {};
    }
    encoded.resize(written);
    return encoded;
}

bool zlibCleanupWasComplete() {
    const std::size_t total = ah::payload::zlibTotalFreeCountForTesting();
    return ah::payload::zlibLiveAllocationCountForTesting() == 0 && total != 0 &&
           ah::payload::zlibZeroizedFreeCountForTesting() == total;
}

int testZlibFailureMatrix() {
    const std::vector<std::uint8_t> plaintext(8192, 0x44);
    const std::vector<std::uint8_t> zlib = deflateForTesting(plaintext, MAX_WBITS);
    const std::vector<std::uint8_t> raw = deflateForTesting(plaintext, -MAX_WBITS);
    const std::vector<std::uint8_t> gzip = deflateForTesting(plaintext, MAX_WBITS + 16);
    const std::vector<std::uint8_t> dictionary(64, 0x31);
    const std::vector<std::uint8_t> preset =
        deflateForTesting(plaintext, MAX_WBITS, &dictionary);
    if (zlib.empty() || raw.empty() || gzip.empty() || preset.empty()) {
        return 1;
    }
    const auto expect = [&](const std::vector<std::uint8_t>& encoded,
                            std::size_t output_size,
                            ah::payload::Status expected) {
        std::vector<std::uint8_t> output(output_size, 0);
        ah::payload::resetZlibCleanupEvidenceForTesting();
        const ah::payload::Status actual = ah::payload::inflateCompressedForTesting(
            {encoded.data(), encoded.size()}, output.data(), output.size());
        const bool cleanup = zlibCleanupWasComplete();
        std::fill(output.begin(), output.end(), 0);
        return actual == expected && cleanup;
    };
    if (!expect(zlib, plaintext.size(), ah::payload::Status::kSuccess)) return 2;
    if (!expect(raw, plaintext.size(), ah::payload::Status::kZlibWrapper)) return 3;
    if (!expect(gzip, plaintext.size(), ah::payload::Status::kZlibWrapper)) return 4;
    if (!expect(preset, plaintext.size(), ah::payload::Status::kZlibDictionary)) return 5;

    auto checksum = zlib;
    checksum.back() ^= 1;
    if (!expect(checksum, plaintext.size(), ah::payload::Status::kZlibChecksum)) return 6;
    auto truncated = zlib;
    truncated.pop_back();
    if (!expect(truncated, plaintext.size(), ah::payload::Status::kLength)) return 7;
    auto trailing = zlib;
    trailing.push_back(0);
    if (!expect(trailing, plaintext.size(), ah::payload::Status::kTrailingData)) return 8;
    auto concatenated = zlib;
    concatenated.insert(concatenated.end(), zlib.begin(), zlib.end());
    if (!expect(concatenated, plaintext.size(), ah::payload::Status::kTrailingData)) return 9;
    if (!expect(zlib, plaintext.size() - 1, ah::payload::Status::kLength)) return 10;
    if (!expect(zlib, plaintext.size() + 1, ah::payload::Status::kLength)) return 11;
    return ah::apk::kMaxSourceApkBytes == 2'147'483'647ULL ? 0 : 12;
}

}  // namespace

int runM202PayloadVector(const char* config_path, const char* container_path,
                         const char* slot_path) {
    const std::vector<std::uint8_t> config = readFile(config_path);
    const std::vector<std::uint8_t> container = readFile(container_path);
    const std::vector<std::uint8_t> slot = readFile(slot_path);
    if (config.size() != ah::container::kConfigBytes ||
        slot.size() != ah::container::kNativeShareSlotBytes || container.empty()) {
        return 1;
    }

    if (testZlibFailureMatrix() != 0) {
        return 1;
    }

    ah::payload::resetZlibCleanupEvidenceForTesting();
    ah::payload::resetShareScrubEvidenceForTesting();
    ah::memory::PayloadHandle handle{};
    ah::payload::AuthenticatedMetadata metadata{};
    bool cleanup_failed = true;
    if (ah::payload::openAuthenticatedPayload(requestFor(config, container, slot),
                                               &handle, &metadata, &cleanup_failed) !=
            ah::payload::Status::kSuccess || cleanup_failed || handle.size() != 2 ||
        handle.mapping(0).size != 1024 || handle.mapping(1).size != 190000 ||
        handle.close() != ah::memory::Status::kSuccess || !zlibCleanupWasComplete()) {
        return 2;
    }
    if (ah::payload::shareScrubRunCountForTesting() != 2 ||
        ah::payload::shareScrubZeroizedRunCountForTesting() != 2) {
        return 2;
    }

    auto tampered_config = config;
    tampered_config[164] ^= 1;
    if (openExpect(tampered_config, container, slot, ah::payload::Status::kAuthentication) != 0) {
        return 3;
    }
    auto tampered_slot = slot;
    tampered_slot[40] ^= 1;
    ah::payload::resetShareScrubEvidenceForTesting();
    if (openExpect(config, container, tampered_slot, ah::payload::Status::kBinding) != 0) {
        return 4;
    }
    if (ah::payload::shareScrubRunCountForTesting() != 2 ||
        ah::payload::shareScrubZeroizedRunCountForTesting() != 2) {
        return 4;
    }
    auto tampered_manifest = container;
    tampered_manifest[104] ^= 1;
    if (openExpect(config, tampered_manifest, slot, ah::payload::Status::kAuthentication) != 0) {
        return 5;
    }
    constexpr std::array<std::size_t, 3> kFirstMiddleLast{0, 2, 3};
    for (std::size_t index = 0; index < kFirstMiddleLast.size(); ++index) {
        auto tampered_tag = container;
        if (!mutateChunkTag(&tampered_tag, kFirstMiddleLast[index]) ||
            openExpect(config, tampered_tag, slot, ah::payload::Status::kAuthentication) != 0) {
            return 6 + static_cast<int>(index);
        }
    }
    for (std::size_t index = 0; index < kFirstMiddleLast.size(); ++index) {
        auto tampered_ciphertext = container;
        if (!mutateChunkCiphertext(&tampered_ciphertext, kFirstMiddleLast[index]) ||
            openExpect(config, tampered_ciphertext, slot,
                       ah::payload::Status::kAuthentication) != 0) {
            return 9 + static_cast<int>(index);
        }
    }
    auto unknown_version = container;
    unknown_version[4] = 3;
    if (openExpect(config, unknown_version, slot, ah::payload::Status::kVersion) != 0) {
        return 12;
    }
    auto invalid_table = container;
    invalid_table[20] ^= 1;
    if (openExpect(config, invalid_table, slot, ah::payload::Status::kFormat) != 0) {
        return 13;
    }
    auto trailing_container = container;
    trailing_container.push_back(0);
    if (openExpect(config, trailing_container, slot,
                   ah::payload::Status::kTrailingData) != 0) {
        return 14;
    }
    std::array<std::uint8_t, 32> wrong_signer{};
    auto wrong_signer_request = requestFor(config, container, slot);
    wrong_signer_request.measured_signer_sha256 =
        {wrong_signer.data(), wrong_signer.size()};
    if (openRequestExpect(wrong_signer_request, ah::payload::Status::kBinding) != 0) {
        return 15;
    }
    constexpr std::array<std::uint8_t, 5> kWrongPackage{'w', 'r', 'o', 'n', 'g'};
    auto wrong_package_request = requestFor(config, container, slot);
    wrong_package_request.framework_package_utf8 =
        {kWrongPackage.data(), kWrongPackage.size()};
    if (openRequestExpect(wrong_package_request,
                         ah::payload::Status::kAuthentication) != 0) {
        return 16;
    }

    struct FailureKind {
        ah::payload::FailureStage stage;
        ah::payload::Status status;
    };
    constexpr std::array<FailureKind, 6> kFailureKinds{{
        {ah::payload::FailureStage::kBeforeAuthentication,
         ah::payload::Status::kAuthentication},
        {ah::payload::FailureStage::kBeforeAuthentication, ah::payload::Status::kIo},
        {ah::payload::FailureStage::kBeforeAuthentication,
         ah::payload::Status::kCancelled},
        {ah::payload::FailureStage::kBeforeAuthentication,
         ah::payload::Status::kOutOfMemory},
        {ah::payload::FailureStage::kBeforeInflate,
         ah::payload::Status::kZlibChecksum},
        {ah::payload::FailureStage::kAfterInflate, ah::payload::Status::kDigest},
    }};
    for (std::size_t chunk : kFirstMiddleLast) {
        for (const FailureKind& kind : kFailureKinds) {
            const InjectedFailure injected{chunk, kind.stage, kind.status};
            if (openInjected(config, container, slot, injected) != 0) {
                return 20 + static_cast<int>(chunk);
            }
        }
    }

    const InjectedFailure cleanup_injected{
        2, ah::payload::FailureStage::kBeforeAuthentication,
        ah::payload::Status::kAuthentication};
    if (openInjected(config, container, slot, cleanup_injected, true) != 0) {
        return 30;
    }

    for (std::int64_t successful_allocations : {std::int64_t{0}, std::int64_t{1}}) {
        ah::memory::resetFailureInjectionForTesting();
        ah::memory::failAllocationAfterForTesting(successful_allocations);
        ah::memory::PayloadHandle oom_handle{};
        ah::payload::AuthenticatedMetadata oom_metadata{};
        bool oom_cleanup_failed = false;
        const ah::payload::Status oom = ah::payload::openAuthenticatedPayload(
            requestFor(config, container, slot), &oom_handle, &oom_metadata,
            &oom_cleanup_failed);
        ah::memory::resetFailureInjectionForTesting();
        if (oom != ah::payload::Status::kOutOfMemory || oom_cleanup_failed ||
            oom_handle.size() != 0 || ah::memory::liveMappingCountForTesting() != 0) {
            return 31 + static_cast<int>(successful_allocations);
        }
    }

    ah::memory::resetFailureInjectionForTesting();
    ah::memory::failProtectionAfterForTesting(0);
    ah::memory::PayloadHandle protection_handle{};
    ah::payload::AuthenticatedMetadata protection_metadata{};
    bool protection_cleanup_failed = false;
    const ah::payload::Status protection = ah::payload::openAuthenticatedPayload(
        requestFor(config, container, slot), &protection_handle, &protection_metadata,
        &protection_cleanup_failed);
    ah::memory::resetFailureInjectionForTesting();
    if (protection != ah::payload::Status::kMemoryProtection ||
        protection_cleanup_failed || protection_handle.size() != 0 ||
        ah::memory::liveMappingCountForTesting() != 0 ||
        ah::memory::zeroizedReleaseCountForTesting() == 0) {
        return 33;
    }

    ah::memory::PayloadHandle cleanup_handle{};
    ah::payload::AuthenticatedMetadata cleanup_metadata{};
    bool open_cleanup_failed = false;
    if (ah::payload::openAuthenticatedPayload(
            requestFor(config, container, slot), &cleanup_handle, &cleanup_metadata,
            &open_cleanup_failed) != ah::payload::Status::kSuccess || open_cleanup_failed) {
        return 34;
    }
    ah::memory::resetFailureInjectionForTesting();
    ah::memory::failReleaseAfterForTesting(0);
    const ah::memory::Status close_status = cleanup_handle.close();
    ah::memory::resetFailureInjectionForTesting();
    if (close_status != ah::memory::Status::kCleanupFailed || cleanup_handle.size() != 0 ||
        ah::memory::liveMappingCountForTesting() != 0) {
        return 35;
    }
    return 0;
}
