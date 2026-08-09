#ifndef AH_RUNTIME_JNI_EXCEPTION_HPP
#define AH_RUNTIME_JNI_EXCEPTION_HPP

#include <jni.h>

namespace ah::jni {

inline constexpr char kCleanupCode[] = "AAH-RUNTIME-CONTAINER-CLEANUP";

void throwCode(JNIEnv* environment, const char* code) noexcept;
void throwCodeWithCleanup(
    JNIEnv* environment, const char* primary_code, bool cleanup_failed) noexcept;

}  // namespace ah::jni

#endif
