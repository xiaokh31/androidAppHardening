cmake_minimum_required(VERSION 4.1.2)

project(ah_runtime_native_crypto LANGUAGES C CXX)

set(AH_MBEDTLS_ROOT "" CACHE PATH "Path to the verified Mbed TLS 4.1.1 source tree")
if(NOT IS_ABSOLUTE "${AH_MBEDTLS_ROOT}")
    message(FATAL_ERROR "AH_MBEDTLS_ROOT must be an absolute path to the verified source tree")
endif()
if(NOT EXISTS "${AH_MBEDTLS_ROOT}/tf-psa-crypto/CMakeLists.txt")
    message(FATAL_ERROR
        "M2-07 dependency missing. Prepare and verify .toolchains/native-crypto first; "
        "Gradle/CMake never downloads dependencies.")
endif()

file(SHA256 "${AH_MBEDTLS_ROOT}/LICENSE" AH_MBEDTLS_LICENSE_SHA256)
if(NOT AH_MBEDTLS_LICENSE_SHA256 STREQUAL
        "9b405ef4c89342f5eae1dd828882f931747f71001cfba7d114801039b52ad09b")
    message(FATAL_ERROR "Mbed TLS LICENSE hash does not match the M2-07 lock")
endif()
file(SHA256 "${AH_MBEDTLS_ROOT}/tf-psa-crypto/LICENSE" AH_TF_PSA_LICENSE_SHA256)
if(NOT AH_TF_PSA_LICENSE_SHA256 STREQUAL
        "da8c58f05f135a9d15e9ffad4ecf854cfcc1f014c8abfd75ba05f62630ccc118")
    message(FATAL_ERROR "TF-PSA-Crypto LICENSE hash does not match the M2-07 lock")
endif()
file(READ "${AH_MBEDTLS_ROOT}/tf-psa-crypto/CMakeLists.txt" AH_TF_PSA_CMAKE)
if(NOT AH_TF_PSA_CMAKE MATCHES "set\\(TF_PSA_CRYPTO_VERSION[ \t]+1\\.1\\.1\\)")
    message(FATAL_ERROR "Expected bundled TF-PSA-Crypto 1.1.1")
endif()

set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)
set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)
set(GEN_FILES OFF CACHE BOOL "" FORCE)
set(USE_STATIC_TF_PSA_CRYPTO_LIBRARY ON CACHE BOOL "" FORCE)
set(USE_SHARED_TF_PSA_CRYPTO_LIBRARY OFF CACHE BOOL "" FORCE)
set(DISABLE_PACKAGE_CONFIG_AND_INSTALL ON CACHE BOOL "" FORCE)
set(TF_PSA_CRYPTO_CONFIG_FILE
    "${CMAKE_CURRENT_SOURCE_DIR}/ah_crypto_config.h"
    CACHE FILEPATH "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_subdirectory(
    "${AH_MBEDTLS_ROOT}/tf-psa-crypto"
    "${CMAKE_CURRENT_BINARY_DIR}/tf-psa-crypto"
    EXCLUDE_FROM_ALL
)

add_library(ah_crypto_backend OBJECT crypto_backend.cpp)
target_compile_features(ah_crypto_backend PRIVATE cxx_std_17)
target_link_libraries(ah_crypto_backend PRIVATE TF-PSA-Crypto::tfpsacrypto)
set_target_properties(
    ah_crypto_backend
    PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN YES
)

if(ANDROID)
    add_library(ah_runtime SHARED $<TARGET_OBJECTS:ah_crypto_backend>)
    target_link_libraries(ah_runtime PRIVATE TF-PSA-Crypto::tfpsacrypto)
    target_compile_options(ah_runtime PRIVATE -fstack-protector-strong -ffunction-sections -fdata-sections)
    target_link_options(
        ah_runtime
        PRIVATE
            -Wl,--gc-sections
            -Wl,--exclude-libs,ALL
            -Wl,--undefined=ah_crypto_backend_anchor
            -Wl,-z,relro
            -Wl,-z,now
            -Wl,-z,noexecstack
    )
    set_target_properties(
        ah_runtime
        PROPERTIES
            CXX_VISIBILITY_PRESET hidden
            C_VISIBILITY_PRESET hidden
            VISIBILITY_INLINES_HIDDEN YES
    )
else()
    enable_testing()
    add_executable(ah_crypto_self_test crypto_backend_test.cpp $<TARGET_OBJECTS:ah_crypto_backend>)
    target_compile_features(ah_crypto_self_test PRIVATE cxx_std_17)
    target_link_libraries(ah_crypto_self_test PRIVATE TF-PSA-Crypto::tfpsacrypto)
    add_test(NAME m2_07_native_crypto_vectors COMMAND ah_crypto_self_test)
endif()
