cmake_minimum_required(VERSION 4.1.2)

project(ah_runtime_native_crypto LANGUAGES C CXX)

option(AH_M2_02_SANITIZERS "Enable Host ASan and UBSan for M2-02" OFF)
option(AH_M3_02_LIBFUZZER "Build the bounded M3-02 Native libFuzzer target" OFF)

set(AH_MBEDTLS_ROOT "" CACHE PATH "Path to the verified Mbed TLS 4.1.1 source tree")
if(NOT IS_ABSOLUTE "${AH_MBEDTLS_ROOT}")
    message(FATAL_ERROR "AH_MBEDTLS_ROOT must be an absolute path to the verified source tree")
endif()
if(NOT EXISTS "${AH_MBEDTLS_ROOT}/tf-psa-crypto/CMakeLists.txt")
    message(FATAL_ERROR
        "M2-07 dependency missing. Prepare and verify .toolchains/native-crypto first; "
        "Gradle/CMake never downloads dependencies.")
endif()

set(AH_M2_07_VERIFIED_STAMP "${AH_MBEDTLS_ROOT}/.aah-m2-07-verified")
if(NOT EXISTS "${AH_M2_07_VERIFIED_STAMP}")
    message(FATAL_ERROR "M2-07 verified source stamp is missing")
endif()
file(READ "${AH_M2_07_VERIFIED_STAMP}" AH_M2_07_VERIFIED_IDENTITY)
string(STRIP "${AH_M2_07_VERIFIED_IDENTITY}" AH_M2_07_VERIFIED_IDENTITY)
set(AH_M2_07_EXPECTED_IDENTITY
    "archive_sha256=3359a349e23db3d5536fcee032ae7b2ecbfc08972fab643089b5cbf2a375c98c\nsource_tree_sha256=7c4ba6554fed6eb67c201054bc75b124fcdc0649e2f56cd762746e01a25d2140")
if(NOT AH_M2_07_VERIFIED_IDENTITY STREQUAL AH_M2_07_EXPECTED_IDENTITY)
    message(FATAL_ERROR "M2-07 verified source stamp does not match the locked archive/tree identity")
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

set(
    AH_RUNTIME_SOURCES
    crypto_backend.cpp
    container_format.cpp
    zip_assets.cpp
    memory_controls.cpp
    secure_buffer.cpp
    payload_memory.cpp
    payload_metadata.cpp
    risk_signals.cpp
)
if(ANDROID OR UNIX)
    list(APPEND AH_RUNTIME_SOURCES authenticated_payload.cpp mapped_apk.cpp)
endif()
if(ANDROID)
    list(APPEND AH_RUNTIME_SOURCES
        jni_exception.cpp
        jni_bridge.cpp
        native_share_slot.cpp
        payload_handle_registry.cpp
    )
endif()
add_library(ah_crypto_backend OBJECT ${AH_RUNTIME_SOURCES})

if(NOT ANDROID)
    target_compile_definitions(ah_crypto_backend PRIVATE AH_M2_02_HOST_TESTING=1)
endif()

if(UNIX AND NOT ANDROID)
    find_package(ZLIB REQUIRED)
endif()
target_compile_features(ah_crypto_backend PRIVATE cxx_std_17)
target_link_libraries(ah_crypto_backend PRIVATE TF-PSA-Crypto::tfpsacrypto)
if(MSVC)
    target_compile_options(ah_crypto_backend PRIVATE /W4 /WX)
else()
    target_compile_options(ah_crypto_backend PRIVATE -Wall -Wextra -Wpedantic -Werror)
endif()
if(ANDROID)
    target_compile_definitions(ah_crypto_backend PRIVATE _FORTIFY_SOURCE=2)
    target_compile_options(
        ah_crypto_backend
        PRIVATE
            -fstack-protector-strong
            -ffunction-sections
            -fdata-sections
    )
endif()
if(UNIX AND NOT ANDROID)
    target_link_libraries(ah_crypto_backend PRIVATE ZLIB::ZLIB)
endif()
set_target_properties(
    ah_crypto_backend
    PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN YES
)

if(ANDROID)
    add_library(ah_runtime SHARED $<TARGET_OBJECTS:ah_crypto_backend>)
    target_link_libraries(ah_runtime PRIVATE TF-PSA-Crypto::tfpsacrypto z)
    target_link_options(
        ah_runtime
        PRIVATE
            -Wl,--gc-sections
            -Wl,--exclude-libs,ALL
            -Wl,--undefined=ah_crypto_backend_anchor
            -Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/exports.map
            -Wl,-z,relro
            -Wl,-z,now
            -Wl,-z,noexecstack
    )
    set_property(
        TARGET ah_runtime
        APPEND
        PROPERTY LINK_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/exports.map"
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
    find_package(Threads REQUIRED)
    add_executable(
        ah_crypto_self_test
        crypto_backend_test.cpp
        container_format_test.cpp
        m2_02_foundation_test.cpp
        $<TARGET_OBJECTS:ah_crypto_backend>
    )
    target_compile_definitions(ah_crypto_self_test PRIVATE AH_M2_02_HOST_TESTING=1)
    if(UNIX)
        target_sources(ah_crypto_self_test PRIVATE m2_02_payload_vector_test.cpp)
        target_compile_definitions(ah_crypto_self_test PRIVATE AH_M2_02_PAYLOAD_VECTOR_TEST=1)
    endif()
    target_compile_features(ah_crypto_self_test PRIVATE cxx_std_17)
    configure_file(
        testdata/m2_08_topology_oob.regression.hex
        m2_08_topology_oob.regression.hex
        COPYONLY
    )
    target_link_libraries(ah_crypto_self_test PRIVATE TF-PSA-Crypto::tfpsacrypto Threads::Threads)
    if(UNIX)
        target_link_libraries(ah_crypto_self_test PRIVATE ZLIB::ZLIB)
    endif()
    if(AH_M2_02_SANITIZERS)
        if(NOT CMAKE_CXX_COMPILER_ID MATCHES "Clang|GNU")
            message(FATAL_ERROR "M2-02 sanitizers require Clang or GCC")
        endif()
        target_compile_options(ah_crypto_backend PRIVATE
            -fsanitize=address,undefined -fno-omit-frame-pointer)
        target_compile_options(ah_crypto_self_test PRIVATE
            -fsanitize=address,undefined -fno-omit-frame-pointer)
        target_link_options(ah_crypto_self_test PRIVATE -fsanitize=address,undefined)
    endif()
    if(AH_M3_02_LIBFUZZER)
        if(NOT CMAKE_CXX_COMPILER_ID MATCHES "Clang")
            message(FATAL_ERROR "M3-02 libFuzzer requires Clang")
        endif()
        target_compile_options(ah_crypto_backend PRIVATE
            -fsanitize=fuzzer-no-link,address,undefined -fno-omit-frame-pointer)
        target_link_options(ah_crypto_self_test PRIVATE -fsanitize=address,undefined)
        add_executable(
            m3_02_container_fuzz
            m3_02_container_fuzz.cpp
            $<TARGET_OBJECTS:ah_crypto_backend>
        )
        target_compile_definitions(m3_02_container_fuzz PRIVATE AH_M2_02_HOST_TESTING=1)
        target_compile_features(m3_02_container_fuzz PRIVATE cxx_std_17)
        target_compile_options(m3_02_container_fuzz PRIVATE
            -Wall -Wextra -Wpedantic -Werror
            -fsanitize=fuzzer,address,undefined -fno-omit-frame-pointer)
        target_link_options(m3_02_container_fuzz PRIVATE -fsanitize=fuzzer,address,undefined)
        target_link_libraries(
            m3_02_container_fuzz
            PRIVATE TF-PSA-Crypto::tfpsacrypto Threads::Threads ZLIB::ZLIB
        )
    endif()
    add_test(NAME m2_07_native_crypto_vectors COMMAND ah_crypto_self_test)
    if(UNIX AND DEFINED AH_M2_02_VECTOR_ROOT AND NOT AH_M2_02_VECTOR_ROOT STREQUAL "")
        if(NOT IS_ABSOLUTE "${AH_M2_02_VECTOR_ROOT}" OR
           NOT EXISTS "${AH_M2_02_VECTOR_ROOT}/config.bin" OR
           NOT EXISTS "${AH_M2_02_VECTOR_ROOT}/payload.ahdc" OR
           NOT EXISTS "${AH_M2_02_VECTOR_ROOT}/slot-x86_64.bin")
            message(FATAL_ERROR "AH_M2_02_VECTOR_ROOT must contain the prepared immutable vector")
        endif()
        add_test(
            NAME m2_02_authenticated_payload_vector
            COMMAND ah_crypto_self_test
                "${AH_M2_02_VECTOR_ROOT}/config.bin"
                "${AH_M2_02_VECTOR_ROOT}/payload.ahdc"
                "${AH_M2_02_VECTOR_ROOT}/slot-x86_64.bin"
        )
    endif()
endif()

if(NOT ANDROID)
    add_executable(m2_05_risk_signals_test risk_signals_test.cpp risk_signals.cpp)
    target_compile_features(m2_05_risk_signals_test PRIVATE cxx_std_17)
    if(MSVC)
        target_compile_options(m2_05_risk_signals_test PRIVATE /W4 /WX)
    else()
        target_compile_options(m2_05_risk_signals_test PRIVATE -Wall -Wextra -Wpedantic -Werror)
    endif()
    add_test(NAME m2_05_risk_signals_test COMMAND m2_05_risk_signals_test)
endif()
