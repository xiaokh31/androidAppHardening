#include "crypto_backend.hpp"

#include <algorithm>
#include <array>
#include <atomic>
#include <cstdint>
#include <iostream>
#include <thread>
#include <utility>
#include <vector>

int runContainerFormatSelfTests();
int runM202FoundationSelfTests();
#if defined(AH_M2_02_PAYLOAD_VECTOR_TEST)
int runM202PayloadVector(const char* config_path, const char* container_path,
                         const char* slot_path);
#endif

namespace {

using ah::crypto::Status;

constexpr std::array<std::uint8_t, 32> kKey{};
constexpr std::array<std::uint8_t, 12> kNonce{};
constexpr std::array<std::uint8_t, 16> kCiphertext{
    0xce, 0xa7, 0x40, 0x3d, 0x4d, 0x60, 0x6b, 0x6e,
    0x07, 0x4e, 0xc5, 0xd3, 0xba, 0xf3, 0x9d, 0x18,
};
constexpr std::array<std::uint8_t, 16> kTag{
    0xd0, 0xd1, 0xc8, 0xa7, 0x99, 0x99, 0x6b, 0xf0,
    0x26, 0x5b, 0x98, 0xb5, 0xd4, 0x8a, 0xb9, 0x19,
};
constexpr std::array<std::uint8_t, 16> kEmptyCiphertextTag{
    0x53, 0x0f, 0x8a, 0xfb, 0xc7, 0x45, 0x36, 0xb9,
    0xa9, 0x63, 0xb4, 0xf1, 0xc4, 0xcb, 0x73, 0x8b,
};

template <std::size_t N>
bool equals(const std::array<std::uint8_t, N>& left, const std::array<std::uint8_t, N>& right) {
    return std::equal(left.begin(), left.end(), right.begin(), right.end());
}

template <std::size_t N>
bool allZero(const std::array<std::uint8_t, N>& bytes) {
    return std::all_of(bytes.begin(), bytes.end(), [](std::uint8_t value) { return value == 0; });
}

int testNistAes256Gcm() {
    const std::array<std::uint8_t, 16> expected{};
    std::array<std::uint8_t, 16> plaintext{};
    std::size_t plaintextSize = 0;
    const Status result = ah::crypto::aes256GcmDecrypt(
        kKey.data(), kKey.size(), kNonce.data(), kNonce.size(), nullptr, 0,
        kCiphertext.data(), kCiphertext.size(), kTag.data(), kTag.size(),
        plaintext.data(), plaintext.size(), &plaintextSize);
    if (result != Status::kSuccess || plaintextSize != expected.size() || !equals(plaintext, expected)) {
        return 1;
    }

    auto badTag = kTag;
    badTag.back() ^= 1;
    plaintext.fill(0xa5);
    plaintextSize = 99;
    const Status tamper = ah::crypto::aes256GcmDecrypt(
        kKey.data(), kKey.size(), kNonce.data(), kNonce.size(), nullptr, 0,
        kCiphertext.data(), kCiphertext.size(), badTag.data(), badTag.size(),
        plaintext.data(), plaintext.size(), &plaintextSize);
    if (tamper != Status::kAuthenticationFailed || plaintextSize != 0 || !allZero(plaintext)) {
        return 2;
    }

    plaintext.fill(0xa5);
    plaintextSize = 99;
    if (ah::crypto::aes256GcmDecrypt(
            kKey.data(), kKey.size() - 1, kNonce.data(), kNonce.size(), nullptr, 0,
            kCiphertext.data(), kCiphertext.size(), kTag.data(), kTag.size(),
            plaintext.data(), plaintext.size(), &plaintextSize) != Status::kInvalidArgument ||
        plaintextSize != 0 || !allZero(plaintext)) {
        return 3;
    }

    plaintext.fill(0xa5);
    plaintextSize = 99;
    if (ah::crypto::aes256GcmDecrypt(
            kKey.data(), kKey.size(), kNonce.data(), kNonce.size() - 1, nullptr, 0,
            kCiphertext.data(), kCiphertext.size(), kTag.data(), kTag.size(),
            plaintext.data(), plaintext.size(), &plaintextSize) != Status::kInvalidArgument ||
        plaintextSize != 0 || !allZero(plaintext)) {
        return 4;
    }

    plaintext.fill(0xa5);
    plaintextSize = 99;
    if (ah::crypto::aes256GcmDecrypt(
            kKey.data(), kKey.size(), kNonce.data(), kNonce.size(), nullptr, 0,
            kCiphertext.data(), kCiphertext.size(), kTag.data(), kTag.size() - 1,
            plaintext.data(), plaintext.size(), &plaintextSize) != Status::kInvalidArgument ||
        plaintextSize != 0 || !allZero(plaintext)) {
        return 5;
    }

    std::array<std::uint8_t, 15> shortPlaintext{};
    shortPlaintext.fill(0xa5);
    plaintextSize = 99;
    if (ah::crypto::aes256GcmDecrypt(
            kKey.data(), kKey.size(), kNonce.data(), kNonce.size(), nullptr, 0,
            kCiphertext.data(), kCiphertext.size(), kTag.data(), kTag.size(),
            shortPlaintext.data(), shortPlaintext.size(), &plaintextSize) != Status::kInvalidArgument ||
        plaintextSize != 0 || !allZero(shortPlaintext)) {
        return 6;
    }

    plaintextSize = 99;
    if (ah::crypto::aes256GcmDecrypt(
            kKey.data(), kKey.size(), kNonce.data(), kNonce.size(), nullptr, 0,
            nullptr, 0, kEmptyCiphertextTag.data(), kEmptyCiphertextTag.size(),
            nullptr, 0, &plaintextSize) != Status::kSuccess || plaintextSize != 0) {
        return 7;
    }

    struct NullCase {
        const std::uint8_t* key;
        std::size_t keySize;
        const std::uint8_t* nonce;
        std::size_t nonceSize;
        const std::uint8_t* aad;
        std::size_t aadSize;
        const std::uint8_t* ciphertext;
        std::size_t ciphertextSize;
        const std::uint8_t* tag;
        std::size_t tagSize;
    };
    const std::array<NullCase, 5> nullCases{{
        {nullptr, kKey.size(), kNonce.data(), kNonce.size(), nullptr, 0,
         kCiphertext.data(), kCiphertext.size(), kTag.data(), kTag.size()},
        {kKey.data(), kKey.size(), nullptr, kNonce.size(), nullptr, 0,
         kCiphertext.data(), kCiphertext.size(), kTag.data(), kTag.size()},
        {kKey.data(), kKey.size(), kNonce.data(), kNonce.size(), nullptr, 1,
         kCiphertext.data(), kCiphertext.size(), kTag.data(), kTag.size()},
        {kKey.data(), kKey.size(), kNonce.data(), kNonce.size(), nullptr, 0,
         nullptr, kCiphertext.size(), kTag.data(), kTag.size()},
        {kKey.data(), kKey.size(), kNonce.data(), kNonce.size(), nullptr, 0,
         kCiphertext.data(), kCiphertext.size(), nullptr, kTag.size()},
    }};
    for (std::size_t index = 0; index < nullCases.size(); ++index) {
        const auto& invalid = nullCases[index];
        plaintext.fill(0xa5);
        plaintextSize = 99;
        if (ah::crypto::aes256GcmDecrypt(
                invalid.key, invalid.keySize, invalid.nonce, invalid.nonceSize,
                invalid.aad, invalid.aadSize, invalid.ciphertext, invalid.ciphertextSize,
                invalid.tag, invalid.tagSize, plaintext.data(), plaintext.size(),
                &plaintextSize) != Status::kInvalidArgument ||
            plaintextSize != 0 || !allZero(plaintext)) {
            return 10 + static_cast<int>(index);
        }
    }

    plaintextSize = 99;
    if (ah::crypto::aes256GcmDecrypt(
            kKey.data(), kKey.size(), kNonce.data(), kNonce.size(), nullptr, 0,
            kCiphertext.data(), kCiphertext.size(), kTag.data(), kTag.size(),
            nullptr, kCiphertext.size(), &plaintextSize) != Status::kInvalidArgument || plaintextSize != 0) {
        return 15;
    }
    plaintext.fill(0xa5);
    if (ah::crypto::aes256GcmDecrypt(
            kKey.data(), kKey.size(), kNonce.data(), kNonce.size(), nullptr, 0,
            kCiphertext.data(), kCiphertext.size(), kTag.data(), kTag.size(),
            plaintext.data(), plaintext.size(), nullptr) != Status::kInvalidArgument || !allZero(plaintext)) {
        return 16;
    }
    return 0;
}

int testRfc5869AndBoundaries() {
    std::array<std::uint8_t, 22> ikm{};
    ikm.fill(0x0b);
    const std::array<std::uint8_t, 13> salt{
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
        0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c,
    };
    const std::array<std::uint8_t, 10> info{
        0xf0, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8, 0xf9,
    };
    const std::array<std::uint8_t, 42> expected{
        0x3c, 0xb2, 0x5f, 0x25, 0xfa, 0xac, 0xd5, 0x7a, 0x90, 0x43, 0x4f,
        0x64, 0xd0, 0x36, 0x2f, 0x2a, 0x2d, 0x2d, 0x0a, 0x90, 0xcf, 0x1a,
        0x5a, 0x4c, 0x5d, 0xb0, 0x2d, 0x56, 0xec, 0xc4, 0xc5, 0xbf, 0x34,
        0x00, 0x72, 0x08, 0xd5, 0xb8, 0x87, 0x18, 0x58, 0x65,
    };
    std::array<std::uint8_t, 42> output{};
    if (ah::crypto::hkdfSha256(
            ikm.data(), ikm.size(), salt.data(), salt.size(), info.data(), info.size(),
            output.data(), output.size()) != Status::kSuccess || !equals(output, expected)) {
        return 1;
    }

    std::array<std::uint8_t, 1> zeroLength{0xa5};
    if (ah::crypto::hkdfSha256(
            ikm.data(), ikm.size(), salt.data(), salt.size(), info.data(), info.size(),
            zeroLength.data(), 0) != Status::kInvalidArgument || zeroLength[0] != 0xa5) {
        return 2;
    }

    std::array<std::uint8_t, 8160> maximum{};
    if (ah::crypto::hkdfSha256(
            ikm.data(), ikm.size(), salt.data(), salt.size(), info.data(), info.size(),
            maximum.data(), maximum.size()) != Status::kSuccess || allZero(maximum)) {
        return 3;
    }

    std::array<std::uint8_t, 8161> oversized{};
    oversized.fill(0xa5);
    if (ah::crypto::hkdfSha256(
            ikm.data(), ikm.size(), salt.data(), salt.size(), info.data(), info.size(),
            oversized.data(), oversized.size()) != Status::kInvalidArgument || !allZero(oversized)) {
        return 4;
    }

    const std::array<std::pair<const std::uint8_t*, std::size_t>, 3> invalidInputs{{
        {nullptr, 1}, {nullptr, 1}, {nullptr, 1},
    }};
    for (std::size_t index = 0; index < invalidInputs.size(); ++index) {
        std::array<std::uint8_t, 32> invalidOutput{};
        invalidOutput.fill(0xa5);
        const std::uint8_t* invalidIkm = index == 0 ? invalidInputs[index].first : ikm.data();
        const std::size_t invalidIkmSize = index == 0 ? invalidInputs[index].second : ikm.size();
        const std::uint8_t* invalidSalt = index == 1 ? invalidInputs[index].first : salt.data();
        const std::size_t invalidSaltSize = index == 1 ? invalidInputs[index].second : salt.size();
        const std::uint8_t* invalidInfo = index == 2 ? invalidInputs[index].first : info.data();
        const std::size_t invalidInfoSize = index == 2 ? invalidInputs[index].second : info.size();
        if (ah::crypto::hkdfSha256(
                invalidIkm, invalidIkmSize, invalidSalt, invalidSaltSize,
                invalidInfo, invalidInfoSize, invalidOutput.data(), invalidOutput.size()) !=
                Status::kInvalidArgument ||
            !allZero(invalidOutput)) {
            return 5 + static_cast<int>(index);
        }
    }

    if (ah::crypto::hkdfSha256(
            ikm.data(), ikm.size(), salt.data(), salt.size(), info.data(), info.size(),
            nullptr, 32) != Status::kInvalidArgument) {
        return 8;
    }

    std::array<std::uint8_t, 32> emptyOptional{};
    if (ah::crypto::hkdfSha256(
            ikm.data(), ikm.size(), nullptr, 0, nullptr, 0,
            emptyOptional.data(), emptyOptional.size()) != Status::kSuccess) {
        return 9;
    }
    std::array<std::uint8_t, 32> emptyAll{};
    if (ah::crypto::hkdfSha256(
            nullptr, 0, nullptr, 0, nullptr, 0,
            emptyAll.data(), emptyAll.size()) != Status::kSuccess || allZero(emptyAll)) {
        return 10;
    }
    return 0;
}

int testConcurrentFacade() {
    std::atomic<int> failures{0};
    std::vector<std::thread> threads;
    for (int threadIndex = 0; threadIndex < 8; ++threadIndex) {
        threads.emplace_back([&failures] {
            for (int iteration = 0; iteration < 100; ++iteration) {
                std::array<std::uint8_t, 16> plaintext{};
                std::size_t plaintextSize = 0;
                if (ah::crypto::aes256GcmDecrypt(
                        kKey.data(), kKey.size(), kNonce.data(), kNonce.size(), nullptr, 0,
                        kCiphertext.data(), kCiphertext.size(), kTag.data(), kTag.size(),
                        plaintext.data(), plaintext.size(), &plaintextSize) != Status::kSuccess ||
                    plaintextSize != plaintext.size() || !allZero(plaintext)) {
                    ++failures;
                    return;
                }
                std::array<std::uint8_t, 32> derived{};
                if (ah::crypto::hkdfSha256(
                        kKey.data(), kKey.size(), nullptr, 0, nullptr, 0,
                        derived.data(), derived.size()) != Status::kSuccess || allZero(derived)) {
                    ++failures;
                    return;
                }
            }
        });
    }
    for (auto& thread : threads) {
        thread.join();
    }
    return failures.load() == 0 ? 0 : 1;
}

}  // namespace

int main(int argc, char** argv) {
    const int gcm = testNistAes256Gcm();
    const int hkdf = testRfc5869AndBoundaries();
    const int concurrent = testConcurrentFacade();
    const int containerFormat = runContainerFormatSelfTests();
    const int m202Foundation = runM202FoundationSelfTests();
    if (gcm != 0 || hkdf != 0 || concurrent != 0 || containerFormat != 0 ||
        m202Foundation != 0) {
        std::cerr << "M2-07 native crypto self-test failed: gcm=" << gcm
                  << " hkdf=" << hkdf << " concurrent=" << concurrent
                  << " container_format=" << containerFormat
                  << " m2_02_foundation=" << m202Foundation << '\n';
        return 1;
    }
#if defined(AH_M2_02_PAYLOAD_VECTOR_TEST)
    if (argc == 4) {
        const int vector = runM202PayloadVector(argv[1], argv[2], argv[3]);
        if (vector != 0) {
            std::cerr << "M2-02 authenticated payload vector failed: " << vector << '\n';
            return 1;
        }
        std::cout << "M2-02 authenticated payload vector and tamper rollback matrix: PASS\n";
        return 0;
    }
#else
    (void) argc;
    (void) argv;
#endif
    std::cout << "M2-07 crypto and M2-02 ZIP/auth/mapping foundation matrix: PASS\n";
    return 0;
}
