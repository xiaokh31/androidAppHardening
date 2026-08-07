/*
 * M2-07 local TF-PSA-Crypto feature profile.
 * This project file is Apache-2.0 and does not modify the ignored upstream tree.
 */
#ifndef AH_TF_PSA_CRYPTO_CONFIG_H
#define AH_TF_PSA_CRYPTO_CONFIG_H

#define PSA_WANT_ALG_GCM 1
#define PSA_WANT_ALG_HKDF 1
#define PSA_WANT_ALG_HMAC 1
#define PSA_WANT_ALG_SHA_256 1
#define PSA_WANT_KEY_TYPE_AES 1
#define PSA_WANT_KEY_TYPE_HMAC 1

#define MBEDTLS_CTR_DRBG_C
#define MBEDTLS_PSA_BUILTIN_GET_ENTROPY
#define MBEDTLS_PSA_CRYPTO_C

#endif
