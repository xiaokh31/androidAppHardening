#include "jni_exception.hpp"

namespace ah::jni {
namespace {

constexpr char kExceptionClass[] = "ah/runtime/loader/PayloadLoadException";
constexpr char kExceptionConstructor[] = "(Ljava/lang/String;)V";

jthrowable newCodeException(JNIEnv* environment, const char* code) noexcept {
    if (environment == nullptr || code == nullptr || environment->ExceptionCheck()) {
        return nullptr;
    }
    jclass type = environment->FindClass(kExceptionClass);
    if (type == nullptr) {
        return nullptr;
    }
    jmethodID constructor = environment->GetMethodID(type, "<init>", kExceptionConstructor);
    jstring message = environment->NewStringUTF(code);
    jthrowable exception = nullptr;
    if (constructor != nullptr && message != nullptr) {
        exception = static_cast<jthrowable>(environment->NewObject(type, constructor, message));
    }
    if (message != nullptr) {
        environment->DeleteLocalRef(message);
    }
    environment->DeleteLocalRef(type);
    return exception;
}

}  // namespace

void throwCode(JNIEnv* environment, const char* code) noexcept {
    jthrowable exception = newCodeException(environment, code);
    if (exception != nullptr) {
        environment->Throw(exception);
        environment->DeleteLocalRef(exception);
    }
}

void throwCodeWithCleanup(
    JNIEnv* environment, const char* primary_code, bool cleanup_failed) noexcept {
    if (!cleanup_failed) {
        throwCode(environment, primary_code);
        return;
    }
    jthrowable primary = newCodeException(environment, primary_code);
    if (primary == nullptr) {
        return;
    }
    jthrowable cleanup = newCodeException(environment, kCleanupCode);
    if (cleanup == nullptr) {
        if (environment->ExceptionCheck()) {
            environment->ExceptionClear();
        }
    } else {
        jclass throwable = environment->FindClass("java/lang/Throwable");
        jmethodID add_suppressed = throwable == nullptr
                                       ? nullptr
                                       : environment->GetMethodID(
                                             throwable, "addSuppressed",
                                             "(Ljava/lang/Throwable;)V");
        if (add_suppressed != nullptr) {
            environment->CallVoidMethod(primary, add_suppressed, cleanup);
        }
        if (environment->ExceptionCheck()) {
            environment->ExceptionClear();
        }
        if (throwable != nullptr) {
            environment->DeleteLocalRef(throwable);
        }
        environment->DeleteLocalRef(cleanup);
    }
    environment->Throw(primary);
    environment->DeleteLocalRef(primary);
}

}  // namespace ah::jni
