#include "crypto_backend.hpp"

#include <algorithm>
#include <array>
#include <cstdint>
#include <iostream>

namespace {

using ah::crypto::Status;

template <std::size_t N>
bool equals(const std::array<std::uint8_t, N>& left, const std::array<std::uint8_t, N>& right) {
    return std::equal(left.begin(), left.end(), right.begin(), right.end());
}

int testNistAes256Gcm() {
    const std::array<std::uint8_t, 32> key{};
    const std::array<std::uint8_t, 12> nonce{};
    const std::array<std::uint8_t, 16> ciphertext{
        0xce, 0xa7, 0x40, 0x3d, 0x4d, 0x60, 0x6b, 0x6e,
        0x07, 0x4e, 0xc5, 0xd3, 0xba, 0xf3, 0x9d, 0x18,
    };
    const std::array<std::uint8_t, 16> tag{
        0xd0, 0xd1, 0xc8, 0xa7, 0x99, 0x99, 0x6b, 0xf0,
        0x26, 0x5b, 0x98, 0xb5, 0xd4, 0x8a, 0xb9, 0x19,
    };
    const std::array<std::uint8_t, 16> expected{};
    std::array<std::uint8_t, 16> plaintext{};
    std::size_t plaintext_size = 0;
    const Status result = ah::crypto::aes256GcmDecrypt(
        key.data(), key.size(), nonce.data(), nonce.size(), nullptr, 0,
        ciphertext.data(), ciphertext.size(), tag.data(), tag.size(),
        plaintext.data(), plaintext.size(), &plaintext_size);
    if (result != Status::kSuccess || plaintext_size != expected.size() || !equals(plaintext, expected)) {
        return 1;
    }

    auto bad_tag = tag;
    bad_tag.back() ^= 1;
    plaintext.fill(0xa5);
    plaintext_size = 99;
    const Status tamper = ah::crypto::aes256GcmDecrypt(
        key.data(), key.size(), nonce.data(), nonce.size(), nullptr, 0,
        ciphertext.data(), ciphertext.size(), bad_tag.data(), bad_tag.size(),
        plaintext.data(), plaintext.size(), &plaintext_size);
    const std::array<std::uint8_t, 16> zero{};
    if (tamper != Status::kAuthenticationFailed || plaintext_size != 0 || !equals(plaintext, zero)) {
        return 2;
    }

    plaintext.fill(0xa5);
    if (ah::crypto::aes256GcmDecrypt(
            key.data(), key.size() - 1, nonce.data(), nonce.size(), nullptr, 0,
            ciphertext.data(), ciphertext.size(), tag.data(), tag.size(),
            plaintext.data(), plaintext.size(), &plaintext_size) != Status::kInvalidArgument ||
        !equals(plaintext, zero)) {
        return 3;
    }
    return 0;
}

int testRfc5869Case1() {
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
    const Status result = ah::crypto::hkdfSha256(
        ikm.data(), ikm.size(), salt.data(), salt.size(), info.data(), info.size(),
        output.data(), output.size());
    if (result != Status::kSuccess || !equals(output, expected)) {
        return 1;
    }

    std::array<std::uint8_t, 1> invalid{0xa5};
    if (ah::crypto::hkdfSha256(
            ikm.data(), ikm.size(), salt.data(), salt.size(), info.data(), info.size(),
            invalid.data(), 0) != Status::kInvalidArgument || invalid[0] != 0xa5) {
        return 2;
    }
    return 0;
}

}  // namespace

int main() {
    const int gcm = testNistAes256Gcm();
    const int hkdf = testRfc5869Case1();
    if (gcm != 0 || hkdf != 0) {
        std::cerr << "M2-07 native crypto self-test failed: gcm=" << gcm << " hkdf=" << hkdf << '\n';
        return 1;
    }
    std::cout << "M2-07 NIST AES-256-GCM and RFC 5869 vectors: PASS\n";
    return 0;
}
