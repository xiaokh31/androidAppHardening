#include "crypto_backend.hpp"

#include <mutex>

#include <psa/crypto.h>

namespace ah::crypto {
namespace {

constexpr std::size_t kAes256KeyBytes = 32;
constexpr std::size_t kGcmNonceBytes = 12;
constexpr std::size_t kGcmTagBytes = 16;
constexpr std::size_t kSha256Bytes = 32;
constexpr std::size_t kHkdfMaxBytes = 255 * kSha256Bytes;
std::mutex backendMutex;

psa_status_t initializeBackend() noexcept {
    static std::once_flag once;
    static psa_status_t result = PSA_ERROR_BAD_STATE;
    std::call_once(once, [] { result = psa_crypto_init(); });
    return result;
}

bool validBuffer(const void* data, std::size_t size) noexcept {
    return size == 0 || data != nullptr;
}

}  // namespace

void secureZero(void* data, std::size_t size) noexcept {
    if (data == nullptr) {
        return;
    }
    auto* bytes = static_cast<volatile std::uint8_t*>(data);
    while (size-- != 0) {
        *bytes++ = 0;
    }
}

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
    std::size_t* plaintext_size) noexcept {
    if (plaintext_size != nullptr) {
        *plaintext_size = 0;
    }
    if (key_size != kAes256KeyBytes || nonce_size != kGcmNonceBytes || tag_size != kGcmTagBytes ||
        plaintext_size == nullptr || plaintext_capacity < ciphertext_size ||
        !validBuffer(key, key_size) || !validBuffer(nonce, nonce_size) ||
        !validBuffer(aad, aad_size) || !validBuffer(ciphertext, ciphertext_size) ||
        !validBuffer(tag, tag_size) || !validBuffer(plaintext, ciphertext_size)) {
        if (plaintext != nullptr) {
            secureZero(plaintext, plaintext_capacity);
        }
        return Status::kInvalidArgument;
    }
    // TF-PSA-Crypto 1.1.1 does not provide a general thread-safety guarantee.
    // Serialize the complete backend transaction, not only initialization or
    // key-store mutation, so Runtime callers may safely use this facade from
    // different Java/JNI threads.
    const std::lock_guard<std::mutex> backendLock(backendMutex);
    if (initializeBackend() != PSA_SUCCESS) {
        secureZero(plaintext, plaintext_capacity);
        return Status::kBackendFailure;
    }

    psa_key_attributes_t attributes = PSA_KEY_ATTRIBUTES_INIT;
    psa_set_key_type(&attributes, PSA_KEY_TYPE_AES);
    psa_set_key_bits(&attributes, 256);
    psa_set_key_usage_flags(&attributes, PSA_KEY_USAGE_DECRYPT);
    psa_set_key_algorithm(&attributes, PSA_ALG_GCM);

    psa_key_id_t key_id = 0;
    psa_status_t status = psa_import_key(&attributes, key, key_size, &key_id);
    psa_reset_key_attributes(&attributes);
    if (status != PSA_SUCCESS) {
        secureZero(plaintext, plaintext_capacity);
        return Status::kBackendFailure;
    }

    psa_aead_operation_t operation = PSA_AEAD_OPERATION_INIT;
    std::uint8_t empty_output = 0;
    std::uint8_t* output = ciphertext_size == 0 ? &empty_output : plaintext;
    std::size_t update_size = 0;
    std::size_t finish_size = 0;

    status = psa_aead_decrypt_setup(&operation, key_id, PSA_ALG_GCM);
    if (status == PSA_SUCCESS) {
        status = psa_aead_set_nonce(&operation, nonce, nonce_size);
    }
    if (status == PSA_SUCCESS) {
        status = psa_aead_set_lengths(&operation, aad_size, ciphertext_size);
    }
    if (status == PSA_SUCCESS && aad_size != 0) {
        status = psa_aead_update_ad(&operation, aad, aad_size);
    }
    if (status == PSA_SUCCESS && ciphertext_size != 0) {
        status = psa_aead_update(
            &operation,
            ciphertext,
            ciphertext_size,
            output,
            plaintext_capacity,
            &update_size);
    }
    if (status == PSA_SUCCESS) {
        status = psa_aead_verify(
            &operation,
            output + update_size,
            plaintext_capacity - update_size,
            &finish_size,
            tag,
            tag_size);
    }

    const psa_status_t abort_status = psa_aead_abort(&operation);
    const psa_status_t destroy_status = psa_destroy_key(key_id);
    if (status == PSA_SUCCESS && abort_status == PSA_SUCCESS && destroy_status == PSA_SUCCESS &&
        update_size + finish_size == ciphertext_size) {
        *plaintext_size = ciphertext_size;
        return Status::kSuccess;
    }

    secureZero(plaintext, plaintext_capacity);
    if (abort_status != PSA_SUCCESS || destroy_status != PSA_SUCCESS) {
        return Status::kBackendFailure;
    }
    if (status == PSA_ERROR_INVALID_SIGNATURE) {
        return Status::kAuthenticationFailed;
    }
    return Status::kBackendFailure;
}

Status hkdfSha256(
    const std::uint8_t* ikm,
    std::size_t ikm_size,
    const std::uint8_t* salt,
    std::size_t salt_size,
    const std::uint8_t* info,
    std::size_t info_size,
    std::uint8_t* output,
    std::size_t output_size) noexcept {
    if (output_size == 0 || output_size > kHkdfMaxBytes ||
        !validBuffer(ikm, ikm_size) || !validBuffer(salt, salt_size) ||
        !validBuffer(info, info_size) || output == nullptr) {
        if (output != nullptr) {
            secureZero(output, output_size);
        }
        return Status::kInvalidArgument;
    }
    const std::lock_guard<std::mutex> backendLock(backendMutex);
    if (initializeBackend() != PSA_SUCCESS) {
        secureZero(output, output_size);
        return Status::kBackendFailure;
    }

    psa_key_derivation_operation_t operation = PSA_KEY_DERIVATION_OPERATION_INIT;
    psa_status_t status = psa_key_derivation_setup(&operation, PSA_ALG_HKDF(PSA_ALG_SHA_256));
    if (status == PSA_SUCCESS) {
        status = psa_key_derivation_input_bytes(
            &operation, PSA_KEY_DERIVATION_INPUT_SALT, salt, salt_size);
    }
    if (status == PSA_SUCCESS) {
        status = psa_key_derivation_input_bytes(
            &operation, PSA_KEY_DERIVATION_INPUT_SECRET, ikm, ikm_size);
    }
    if (status == PSA_SUCCESS) {
        status = psa_key_derivation_input_bytes(
            &operation, PSA_KEY_DERIVATION_INPUT_INFO, info, info_size);
    }
    if (status == PSA_SUCCESS) {
        status = psa_key_derivation_output_bytes(&operation, output, output_size);
    }

    const psa_status_t abort_status = psa_key_derivation_abort(&operation);
    if (status == PSA_SUCCESS && abort_status == PSA_SUCCESS) {
        return Status::kSuccess;
    }
    secureZero(output, output_size);
    return Status::kBackendFailure;
}

}  // namespace ah::crypto

#if defined(__ANDROID__)
// M2-07 has no JNI consumer yet. Retain the hidden facade in each Android
// template so four-ABI validation covers the code M2-02 will call, without
// exposing upstream or project crypto symbols in the dynamic symbol table.
extern "C" __attribute__((visibility("hidden"))) void ah_crypto_backend_anchor() noexcept {
    volatile auto decrypt = &ah::crypto::aes256GcmDecrypt;
    volatile auto derive = &ah::crypto::hkdfSha256;
    volatile auto zero = &ah::crypto::secureZero;
    (void) decrypt;
    (void) derive;
    (void) zero;
}
#endif
