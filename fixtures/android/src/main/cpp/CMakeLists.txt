cmake_minimum_required(VERSION 4.1.2)

project(m0_05_fixture_jni LANGUAGES CXX)

set(AH_RUNTIME_NATIVE_CPP "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../runtime/native/src/main/cpp")

add_library(
    fixture_jni
    SHARED
    fixture_jni.cpp
    "${AH_RUNTIME_NATIVE_CPP}/jni_exception.cpp"
)
target_include_directories(fixture_jni PRIVATE "${AH_RUNTIME_NATIVE_CPP}")
target_compile_features(fixture_jni PRIVATE cxx_std_17)
set_target_properties(
    fixture_jni
    PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN YES
)
