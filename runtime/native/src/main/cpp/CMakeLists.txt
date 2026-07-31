cmake_minimum_required(VERSION 4.1.2)

project(ah_runtime_skeleton LANGUAGES CXX)

add_library(ah_runtime SHARED empty.cpp)
target_compile_features(ah_runtime PRIVATE cxx_std_17)
set_target_properties(
    ah_runtime
    PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN YES
)
