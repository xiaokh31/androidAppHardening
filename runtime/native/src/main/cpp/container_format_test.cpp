#include "container_format.hpp"

#include <array>
#include <cctype>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iterator>
#include <string>
#include <vector>

namespace {

using ah::container::Status;

template <std::size_t N>
void put16(std::array<std::uint8_t, N>* bytes, std::size_t offset, std::uint16_t value) {
    (*bytes)[offset] = static_cast<std::uint8_t>(value);
    (*bytes)[offset + 1] = static_cast<std::uint8_t>(value >> 8U);
}

template <std::size_t N>
void put32(std::array<std::uint8_t, N>* bytes, std::size_t offset, std::uint32_t value) {
    for (std::size_t index = 0; index < 4; ++index) {
        (*bytes)[offset + index] = static_cast<std::uint8_t>(value >> (index * 8U));
    }
}

template <std::size_t N>
void put64(std::array<std::uint8_t, N>* bytes, std::size_t offset, std::uint64_t value) {
    for (std::size_t index = 0; index < 8; ++index) {
        (*bytes)[offset + index] = static_cast<std::uint8_t>(value >> (index * 8U));
    }
}

template <std::size_t N>
ah::container::ByteView view(const std::array<std::uint8_t, N>& bytes) {
    return {bytes.data(), bytes.size()};
}

ah::container::ByteView view(const std::vector<std::uint8_t>& bytes) {
    return {bytes.data(), bytes.size()};
}

std::vector<std::uint8_t> readHexFixture(const char* path) {
    std::ifstream input(path);
    std::string encoded((std::istreambuf_iterator<char>(input)), std::istreambuf_iterator<char>());
    std::vector<std::uint8_t> decoded;
    int high = -1;
    for (const unsigned char character : encoded) {
        if (std::isspace(character)) {
            continue;
        }
        const int value = character >= '0' && character <= '9'
                              ? character - '0'
                              : character >= 'a' && character <= 'f'
                                    ? character - 'a' + 10
                                    : character >= 'A' && character <= 'F' ? character - 'A' + 10 : -1;
        if (value < 0) {
            return {};
        }
        if (high < 0) {
            high = value;
        } else {
            decoded.push_back(static_cast<std::uint8_t>((high << 4) | value));
            high = -1;
        }
    }
    return high < 0 ? decoded : std::vector<std::uint8_t>{};
}

std::array<std::uint8_t, ah::container::kHeaderBytes> validHeader() {
    std::array<std::uint8_t, ah::container::kHeaderBytes> bytes{};
    std::memcpy(bytes.data(), "AHDC", 4);
    put16(&bytes, 4, 2);
    put16(&bytes, 6, 0);
    put16(&bytes, 8, ah::container::kHeaderBytes);
    put32(&bytes, 12, 1);
    put32(&bytes, 16, 76);
    put32(&bytes, 20, ah::container::kRecordBytes);
    put32(&bytes, 24, 1);
    put32(&bytes, 28, ah::container::kChunkBytes);
    put64(&bytes, 32, 20);
    put32(&bytes, 136, ah::container::kChunkPlaintextMax);
    return bytes;
}

std::array<std::uint8_t, 76> validSigner() {
    std::array<std::uint8_t, 76> bytes{};
    std::memcpy(bytes.data(), "SPV1", 4);
    put16(&bytes, 4, 1);
    put16(&bytes, 8, 1);
    for (std::size_t index = 0; index < ah::container::kDigestBytes; ++index) {
        bytes[12 + index] = static_cast<std::uint8_t>(index + 1);
        bytes[44 + index] = static_cast<std::uint8_t>(index + 1);
    }
    return bytes;
}

std::array<std::uint8_t, ah::container::kRecordBytes> validRecord() {
    std::array<std::uint8_t, ah::container::kRecordBytes> bytes{};
    constexpr char kName[] = "classes.dex";
    put16(&bytes, 4, sizeof(kName) - 1);
    put64(&bytes, 8, 8);
    put64(&bytes, 16, 4);
    put32(&bytes, 24, 1);
    bytes[40] = 1;
    std::memcpy(bytes.data() + 48, kName, sizeof(kName) - 1);
    bytes[72] = 1;
    return bytes;
}

std::array<std::uint8_t, ah::container::kChunkBytes> validChunk() {
    std::array<std::uint8_t, ah::container::kChunkBytes> bytes{};
    put32(&bytes, 24, 4);
    return bytes;
}

std::array<std::uint8_t, ah::container::kConfigBytes> validConfig() {
    std::array<std::uint8_t, ah::container::kConfigBytes> bytes{};
    constexpr char kFactory[] = "ah.fixture.RealFactory";
    std::memcpy(bytes.data(), "AHKC", 4);
    put16(&bytes, 4, 2);
    put16(&bytes, 8, 1);
    put32(&bytes, 12, ah::container::kConfigBytes);
    put16(&bytes, 16, 2);
    put16(&bytes, 18, 1);
    put16(&bytes, 20, 1);
    put16(&bytes, 22, sizeof(kFactory) - 1);
    std::memcpy(bytes.data() + 180, kFactory, sizeof(kFactory) - 1);
    bytes[24] = 1;
    bytes[40] = 2;
    bytes[56] = 3;
    bytes[88] = 4;
    bytes[120] = 5;
    bytes[132] = 6;
    bytes[164] = 7;
    return bytes;
}

bool rejectsHeaderMutation(std::size_t offset, std::uint8_t value, Status expected) {
    auto bytes = validHeader();
    bytes[offset] = value;
    ah::container::HeaderV2 output{};
    return ah::container::parseHeaderV2(view(bytes), &output) == expected;
}

int testHeaderAndSigner() {
    ah::container::HeaderV2 header{};
    const auto encodedHeader = validHeader();
    if (ah::container::parseHeaderV2(view(encodedHeader), &header) != Status::kSuccess ||
        header.dex_count != 1 || header.payload_size != 20) {
        return 1;
    }
    if (!rejectsHeaderMutation(0, 'X', Status::kFormat) ||
        !rejectsHeaderMutation(4, 1, Status::kVersion) ||
        !rejectsHeaderMutation(10, 1, Status::kVersion) ||
        !rejectsHeaderMutation(12, 0, Status::kLimitExceeded) ||
        !rejectsHeaderMutation(20, 0, Status::kFormat) ||
        !rejectsHeaderMutation(140, 1, Status::kFormat)) {
        return 2;
    }
    ah::container::SignerPolicyV1 signer{};
    auto encodedSigner = validSigner();
    if (ah::container::parseSignerPolicyV1(view(encodedSigner), &signer) != Status::kSuccess ||
        signer.lineage_count != 1) {
        return 3;
    }
    encodedSigner[44] ^= 1;
    if (ah::container::parseSignerPolicyV1(view(encodedSigner), &signer) != Status::kFormat) {
        return 4;
    }
    return 0;
}

int testRecordChunkAndTopology() {
    auto encodedRecord = validRecord();
    auto encodedChunk = validChunk();
    ah::container::RecordV2 record{};
    ah::container::ChunkV2 chunk{};
    if (ah::container::parseRecordV2(view(encodedRecord), &record) != Status::kSuccess ||
        ah::container::parseChunkV2(view(encodedChunk), &chunk) != Status::kSuccess) {
        return 1;
    }
    auto headerBytes = validHeader();
    ah::container::HeaderV2 header{};
    if (ah::container::parseHeaderV2(view(headerBytes), &header) != Status::kSuccess ||
        ah::container::validateTopology(header, &record, 1, view(encodedChunk)) != Status::kSuccess) {
        return 2;
    }
    auto badName = encodedRecord;
    badName[48] = 'x';
    if (ah::container::parseRecordV2(view(badName), &record) != Status::kFormat) {
        return 3;
    }
    auto badNonce = encodedRecord;
    badNonce[40] = 0;
    if (ah::container::parseRecordV2(view(badNonce), &record) != Status::kFormat) {
        return 4;
    }
    if (ah::container::parseRecordV2(view(encodedRecord), &record) != Status::kSuccess) {
        return 5;
    }
    auto badChunk = encodedChunk;
    put32(&badChunk, 4, 1);
    if (ah::container::validateTopology(header, &record, 1, view(badChunk)) != Status::kFormat) {
        return 6;
    }
    badChunk = encodedChunk;
    put32(&badChunk, 28, 1);
    if (ah::container::parseChunkV2(view(badChunk), &chunk) != Status::kFormat) {
        return 7;
    }
    return 0;
}

int testTopologyOutOfBoundsRegression() {
    using namespace ah::container;
    const auto fuzzInput = readHexFixture("m2_08_topology_oob.regression.hex");
    if (fuzzInput.size() != 399 || fuzzInput[0] % 6U != 0) {
        return 1;
    }
    const std::uint8_t* input = fuzzInput.data() + 1;
    const std::size_t inputSize = fuzzInput.size() - 1;
    HeaderV2 header{};
    if (parseHeaderV2({input, kHeaderBytes}, &header) != Status::kSuccess) {
        return 2;
    }
    std::size_t cursor = kHeaderBytes + header.signer_policy_size;
    if (cursor + header.record_table_size + header.chunk_table_size > inputSize) {
        return 3;
    }
    std::vector<RecordV2> records(header.dex_count);
    for (auto& record : records) {
        if (parseRecordV2({input + cursor, kRecordBytes}, &record) != Status::kSuccess) {
            return 4;
        }
        cursor += kRecordBytes;
    }
    if (validateTopology(
            header, records.data(), records.size(), {input + cursor, header.chunk_table_size}) !=
        Status::kFormat) {
        return 5;
    }

    auto encodedRecord = validRecord();
    RecordV2 record{};
    if (parseRecordV2(view(encodedRecord), &record) != Status::kSuccess) {
        return 6;
    }
    auto headerBytes = validHeader();
    HeaderV2 valid{};
    if (parseHeaderV2(view(headerBytes), &valid) != Status::kSuccess) {
        return 7;
    }
    const auto encodedChunk = validChunk();
    record.compressed_length = static_cast<std::uint64_t>(kChunkPlaintextMax) + 1;
    record.chunk_count = 2;
    if (validateTopology(valid, &record, 1, view(encodedChunk)) != Status::kFormat) {
        return 8;
    }
    valid.chunk_count = 2;
    if (validateTopology(valid, &record, 1, view(encodedChunk)) != Status::kFormat) {
        return 9;
    }
    return 0;
}

int testConfig() {
    ah::container::ConfigV2 config{};
    auto bytes = validConfig();
    if (ah::container::parseConfigV2(view(bytes), &config) != Status::kSuccess ||
        config.original_factory_size != std::strlen("ah.fixture.RealFactory") ||
        std::strcmp(config.original_factory.data(), "ah.fixture.RealFactory") != 0) {
        return 1;
    }
    auto bad = bytes;
    bad[180] = '1';
    if (ah::container::parseConfigV2(view(bad), &config) != Status::kFormat) {
        return 2;
    }
    bad = bytes;
    bad[180 + std::strlen("ah.fixture.RealFactory")] = 1;
    if (ah::container::parseConfigV2(view(bad), &config) != Status::kFormat) {
        return 3;
    }
    bad = bytes;
    bad[692] = 1;
    if (ah::container::parseConfigV2(view(bad), &config) != Status::kFormat) {
        return 4;
    }
    bad = bytes;
    constexpr char kShell[] = "ah.runtime.bootstrap.ShellAppComponentFactory";
    put16(&bad, 22, sizeof(kShell) - 1);
    std::fill(bad.begin() + 180, bad.begin() + 692, static_cast<std::uint8_t>(0));
    std::memcpy(bad.data() + 180, kShell, sizeof(kShell) - 1);
    if (ah::container::parseConfigV2(view(bad), &config) != Status::kFormat) {
        return 5;
    }
    return 0;
}

}  // namespace

int runContainerFormatSelfTests() {
    const int header = testHeaderAndSigner();
    const int topology = testRecordChunkAndTopology();
    const int topologyBounds = testTopologyOutOfBoundsRegression();
    const int config = testConfig();
    return header == 0 && topology == 0 && topologyBounds == 0 && config == 0
               ? 0
               : 1000 * header + 100 * topology + 10 * topologyBounds + config;
}

#if defined(AH_CONTAINER_FORMAT_STANDALONE_TEST)
int main() {
    return runContainerFormatSelfTests();
}
#endif
