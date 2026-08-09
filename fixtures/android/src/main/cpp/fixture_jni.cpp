#include "jni_exception.hpp"

#include <jni.h>

#include <cstddef>
#include <sys/mman.h>

extern "C" JNIEXPORT jstring JNICALL
Java_ah_fixtures_android_payload_PayloadJni_nativeMarker(JNIEnv* env, jclass) {
    return env->NewStringUTF("M0-05-JNI-FIXED");
}

extern "C" JNIEXPORT void JNICALL
Java_ah_fixtures_android_m202_M202NativeTestHooks_nativeThrowWithCleanupForTesting(
    JNIEnv* environment, jclass) {
    ah::jni::throwCodeWithCleanup(
        environment, "AAH-RUNTIME-CONTAINER-INJECTED", true);
}

extern "C" JNIEXPORT void JNICALL
Java_ah_fixtures_android_m202_M202NativeTestHooks_nativeUnmapDirectBufferForTesting(
    JNIEnv* environment, jclass, jobject buffer) {
    void* address = buffer == nullptr ? nullptr : environment->GetDirectBufferAddress(buffer);
    const jlong size = buffer == nullptr ? -1 : environment->GetDirectBufferCapacity(buffer);
    if (address == nullptr || size <= 0 ||
        munmap(address, static_cast<std::size_t>(size)) != 0) {
        jclass failure = environment->FindClass("java/lang/IllegalStateException");
        if (failure != nullptr) {
            environment->ThrowNew(failure, "M2-02 direct-buffer unmap injection failed");
            environment->DeleteLocalRef(failure);
        }
    }
}
