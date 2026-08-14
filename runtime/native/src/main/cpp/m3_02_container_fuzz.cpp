#include "container_format.hpp"

#include <cstddef>
#include <cstdint>
#include <vector>

namespace {

constexpr std::size_t kMaximumInput = 4U * 1024U * 1024U;

ah::container::ByteView view(const std::uint8_t* data, std::size_t size) noexcept {
    return {data, size};
}

void fuzzContainer(const std::uint8_t* data, std::size_t size) {
    using namespace ah::container;
    if (size < kHeaderBytes) return;
    HeaderV2 header{};
    if (parseHeaderV2(view(data, kHeaderBytes), &header) != Status::kSuccess) return;
    std::size_t cursor = kHeaderBytes;
    const std::size_t metadataBytes = static_cast<std::size_t>(header.signer_policy_size) +
                                      static_cast<std::size_t>(header.record_table_size) +
                                      static_cast<std::size_t>(header.chunk_table_size);
    if (metadataBytes > size - cursor) return;
    SignerPolicyV1 signer{};
    (void)parseSignerPolicyV1(view(data + cursor, header.signer_policy_size), &signer);
    cursor += header.signer_policy_size;
    std::vector<RecordV2> records(header.dex_count);
    for (std::size_t index = 0; index < records.size(); ++index) {
        if (parseRecordV2(view(data + cursor, kRecordBytes), &records[index]) != Status::kSuccess) {
            return;
        }
        cursor += kRecordBytes;
    }
    (void)validateTopology(
        header, records.data(), records.size(), view(data + cursor, header.chunk_table_size));
}

}  // namespace

extern "C" int LLVMFuzzerTestOneInput(const std::uint8_t* data, std::size_t size) {
    using namespace ah::container;
    if (data == nullptr || size < 2 || size > kMaximumInput) return 0;
    const std::uint8_t* input = data + 1;
    const std::size_t inputSize = size - 1;
    switch (data[0] % 6U) {
        case 0:
            fuzzContainer(input, inputSize);
            break;
        case 1: {
            ConfigV2 output{};
            (void)parseConfigV2(view(input, inputSize), &output);
            break;
        }
        case 2: {
            NativeShareSlotV1 output{};
            (void)parseNativeShareSlotV1(view(input, inputSize), 1, &output);
            break;
        }
        case 3: {
            RecordV2 output{};
            (void)parseRecordV2(view(input, inputSize), &output);
            break;
        }
        case 4: {
            ChunkV2 output{};
            (void)parseChunkV2(view(input, inputSize), &output);
            break;
        }
        default: {
            SignerPolicyV1 output{};
            (void)parseSignerPolicyV1(view(input, inputSize), &output);
            break;
        }
    }
    return 0;
}
