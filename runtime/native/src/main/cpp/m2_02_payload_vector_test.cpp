#include "authenticated_payload.hpp"

#include <array>
#include <cstdint>
#include <fstream>
#include <iterator>
#include <vector>

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

    ah::memory::PayloadHandle handle{};
    ah::payload::AuthenticatedMetadata metadata{};
    bool cleanup_failed = true;
    if (ah::payload::openAuthenticatedPayload(requestFor(config, container, slot),
                                               &handle, &metadata, &cleanup_failed) !=
            ah::payload::Status::kSuccess || cleanup_failed || handle.size() != 2 ||
        handle.mapping(0).size != 1024 || handle.mapping(1).size != 190000 ||
        handle.close() != ah::memory::Status::kSuccess) {
        return 2;
    }

    auto tampered_config = config;
    tampered_config[164] ^= 1;
    if (openExpect(tampered_config, container, slot, ah::payload::Status::kAuthentication) != 0) {
        return 3;
    }
    auto tampered_slot = slot;
    tampered_slot[40] ^= 1;
    if (openExpect(config, container, tampered_slot, ah::payload::Status::kBinding) != 0) {
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
    return 0;
}
