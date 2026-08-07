#ifndef AH_RUNTIME_CRYPTO_BACKEND_HPP
#define AH_RUNTIME_CRYPTO_BACKEND_HPP

#include <cstddef>
#include <cstdint>

namespace ah::crypto {

enum class Status : std::uint8_t {
    kSuccess = 0,
    kInvalidArgument = 1,
    kAuthenticationFailed = 2,
    kBackendFailure = 3,
};

Status aes256GcmDecrypt(
    const std::uint8_t* key,
    std::size_t key_size,
    const std::uint8_t* nonce,
    std::size_t nonce_size,
    const std::uint8_t* aad,
    std::size_t aad_size,
    const std::uint8_t* ciphertext,
    std::size_t ciphertext_size,
    const std::uint8_t* tag,
    std::size_t tag_size,
    std::uint8_t* plaintext,
    std::size_t plaintext_capacity,
    std::size_t* plaintext_size) noexcept;

Status hkdfSha256(
    const std::uint8_t* ikm,
    std::size_t ikm_size,
    const std::uint8_t* salt,
    std::size_t salt_size,
    const std::uint8_t* info,
    std::size_t info_size,
    std::uint8_t* output,
    std::size_t output_size) noexcept;

void secureZero(void* data, std::size_t size) noexcept;

}  // namespace ah::crypto

#endif
