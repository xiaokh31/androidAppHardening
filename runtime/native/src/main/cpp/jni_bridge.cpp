#include "authenticated_payload.hpp"
#include "crypto_backend.hpp"
#include "jni_exception.hpp"
#include "mapped_apk.hpp"
#include "native_share_slot.hpp"
#include "payload_handle_registry.hpp"
#include "payload_metadata.hpp"
#include "risk_signals.hpp"
#include "zip_assets.hpp"

#include <jni.h>

#include <array>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <limits>

namespace {

using ah::jni::kCleanupCode;
using ah::jni::throwCode;
using ah::jni::throwCodeWithCleanup;

void throwMemoryCode(JNIEnv* environment, const char* code) noexcept {
    if (environment == nullptr || code == nullptr || environment->ExceptionCheck()) return;
    jclass type = environment->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) {
        environment->ThrowNew(type, code);
        environment->DeleteLocalRef(type);
    }
}

const char* payloadCode(ah::payload::Status status) noexcept {
    switch (status) {
        case ah::payload::Status::kInvalidArgument:
            return "AAH-RUNTIME-CONTAINER-ARGUMENT";
        case ah::payload::Status::kZip:
            return "AAH-RUNTIME-CONTAINER-ZIP";
        case ah::payload::Status::kFormat:
            return "AAH-RUNTIME-CONTAINER-FORMAT";
        case ah::payload::Status::kVersion:
            return "AAH-RUNTIME-CONTAINER-VERSION";
        case ah::payload::Status::kBinding:
            return "AAH-RUNTIME-CONTAINER-BINDING";
        case ah::payload::Status::kAuthentication:
            return "AAH-RUNTIME-CONTAINER-AUTHENTICATION";
        case ah::payload::Status::kCrypto:
            return "AAH-RUNTIME-CONTAINER-CRYPTO";
        case ah::payload::Status::kOutOfMemory:
            return "AAH-RUNTIME-CONTAINER-OOM";
        case ah::payload::Status::kZlibWrapper:
            return "AAH-RUNTIME-CONTAINER-ZLIB-WRAPPER";
        case ah::payload::Status::kZlibDictionary:
            return "AAH-RUNTIME-CONTAINER-ZLIB-DICTIONARY";
        case ah::payload::Status::kZlibChecksum:
            return "AAH-RUNTIME-CONTAINER-ZLIB-CHECKSUM";
        case ah::payload::Status::kLength:
            return "AAH-RUNTIME-CONTAINER-LENGTH";
        case ah::payload::Status::kDigest:
            return "AAH-RUNTIME-CONTAINER-SHA256";
        case ah::payload::Status::kTrailingData:
            return "AAH-RUNTIME-CONTAINER-TRAILING-DATA";
        case ah::payload::Status::kMemoryProtection:
            return "AAH-RUNTIME-CONTAINER-MEMORY-PROTECTION";
        case ah::payload::Status::kIo:
            return "AAH-RUNTIME-CONTAINER-IO";
        case ah::payload::Status::kCancelled:
            return "AAH-RUNTIME-CONTAINER-CANCELLED";
        case ah::payload::Status::kSuccess:
            return "AAH-RUNTIME-CONTAINER-INTERNAL";
    }
    return "AAH-RUNTIME-CONTAINER-INTERNAL";
}

const char* mappedApkCode(ah::apk::Status status) noexcept {
    return status == ah::apk::Status::kLength
               ? "AAH-RUNTIME-CONTAINER-LENGTH"
               : "AAH-RUNTIME-CONTAINER-IO";
}

const char* zipCode(ah::zip::Status status) noexcept {
    switch (status) {
        case ah::zip::Status::kMissing:
            return "AAH-RUNTIME-CONTAINER-ZIP-MISSING";
        case ah::zip::Status::kDuplicate:
            return "AAH-RUNTIME-CONTAINER-ZIP-DUPLICATE";
        case ah::zip::Status::kCrcMismatch:
            return "AAH-RUNTIME-CONTAINER-ZIP-CRC";
        case ah::zip::Status::kInvalidArgument:
            return "AAH-RUNTIME-CONTAINER-ARGUMENT";
        case ah::zip::Status::kFormat:
        case ah::zip::Status::kUnsupported:
            return "AAH-RUNTIME-CONTAINER-ZIP";
        case ah::zip::Status::kSuccess:
            return "AAH-RUNTIME-CONTAINER-INTERNAL";
    }
    return "AAH-RUNTIME-CONTAINER-INTERNAL";
}

class UtfChars final {
public:
    UtfChars(JNIEnv* environment, jstring value) noexcept
        : environment_(environment), value_(value) {
        if (environment_ != nullptr && value_ != nullptr) {
            chars_ = environment_->GetStringUTFChars(value_, nullptr);
        }
    }

    ~UtfChars() noexcept {
        if (chars_ != nullptr) {
            environment_->ReleaseStringUTFChars(value_, chars_);
        }
    }

    UtfChars(const UtfChars&) = delete;
    UtfChars& operator=(const UtfChars&) = delete;

    const char* get() const noexcept { return chars_; }
    std::size_t size() const noexcept {
        return chars_ == nullptr ? 0 : std::strlen(chars_);
    }

private:
    JNIEnv* environment_{};
    jstring value_{};
    const char* chars_{};
};

jbyteArray makeByteArray(JNIEnv* environment, const std::uint8_t* bytes,
                         std::size_t size) noexcept {
    if (environment == nullptr || bytes == nullptr || size == 0 ||
        size > static_cast<std::size_t>(std::numeric_limits<jsize>::max())) {
        return nullptr;
    }
    jbyteArray result = environment->NewByteArray(static_cast<jsize>(size));
    if (result != nullptr) {
        environment->SetByteArrayRegion(
            result, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte*>(bytes));
    }
    return result;
}

bool validPath(const UtfChars& path) noexcept {
    return path.get() != nullptr && path.size() > 1 && path.size() < 4096 && path.get()[0] == '/';
}

struct MetadataContext {
    JNIEnv* environment;
    jbyteArray result;
};

bool encodeMetadata(const ah::handles::Snapshot& snapshot, void* opaque) noexcept {
    auto* context = static_cast<MetadataContext*>(opaque);
    if (context == nullptr || context->environment == nullptr || snapshot.metadata == nullptr) {
        return false;
    }
    std::array<std::uint8_t, ah::metadata::kMetadataMaxBytes> encoded{};
    std::size_t written = 0;
    if (ah::metadata::encodeAuthenticatedMetadata(
            *snapshot.metadata, encoded.data(), encoded.size(), &written) !=
        ah::metadata::Status::kSuccess) {
        return false;
    }
    context->result = makeByteArray(context->environment, encoded.data(), written);
    return context->result != nullptr || context->environment->ExceptionCheck();
}

struct BufferContext {
    JNIEnv* environment;
    jobjectArray result;
};

bool createBuffers(const ah::handles::Snapshot& snapshot, void* opaque) noexcept {
    auto* context = static_cast<BufferContext*>(opaque);
    if (context == nullptr || context->environment == nullptr || snapshot.payload == nullptr ||
        snapshot.payload->size() == 0 || snapshot.payload->size() > ah::container::kMaxDex) {
        return false;
    }
    JNIEnv* environment = context->environment;
    jclass byte_buffer = environment->FindClass("java/nio/ByteBuffer");
    if (byte_buffer == nullptr) {
        return true;
    }
    jobjectArray result = environment->NewObjectArray(
        static_cast<jsize>(snapshot.payload->size()), byte_buffer, nullptr);
    if (result == nullptr) {
        environment->DeleteLocalRef(byte_buffer);
        return true;
    }
    for (std::size_t index = 0; index < snapshot.payload->size(); ++index) {
        const ah::memory::Mapping& mapping = snapshot.payload->mapping(index);
        if (mapping.data == nullptr || mapping.size == 0 || !mapping.read_only ||
            mapping.size > static_cast<std::size_t>(std::numeric_limits<jlong>::max())) {
            environment->DeleteLocalRef(result);
            environment->DeleteLocalRef(byte_buffer);
            return false;
        }
        jobject buffer = environment->NewDirectByteBuffer(
            mapping.data, static_cast<jlong>(mapping.size));
        if (buffer == nullptr) {
            environment->DeleteLocalRef(result);
            environment->DeleteLocalRef(byte_buffer);
            return true;
        }
        environment->SetObjectArrayElement(result, static_cast<jsize>(index), buffer);
        environment->DeleteLocalRef(buffer);
        if (environment->ExceptionCheck()) {
            environment->DeleteLocalRef(result);
            environment->DeleteLocalRef(byte_buffer);
            return true;
        }
    }
    environment->DeleteLocalRef(byte_buffer);
    context->result = result;
    return true;
}

}  // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_ah_runtime_loader_NativePayloadBridge_nativeInspectBinding(
    JNIEnv* environment, jclass, jstring installed_apk_path) {
    UtfChars path(environment, installed_apk_path);
    if (!validPath(path)) {
        throwCode(environment, "AAH-RUNTIME-CONTAINER-ARGUMENT");
        return nullptr;
    }
    ah::apk::ReadOnlyMapping apk{};
    const ah::apk::Status mapped = apk.openAbsolute(path.get());
    if (mapped != ah::apk::Status::kSuccess) {
        throwCode(environment, mappedApkCode(mapped));
        return nullptr;
    }
    ah::zip::FixedAssets assets{};
    const ah::zip::Status located = ah::zip::locateFixedAssets(apk.bytes(), &assets);
    if (located != ah::zip::Status::kSuccess) {
        throwCode(environment, zipCode(located));
        return nullptr;
    }
    ah::payload::UntrustedBinding binding{};
    const ah::payload::Status inspected = ah::payload::inspectUntrustedBinding(assets, &binding);
    if (inspected != ah::payload::Status::kSuccess) {
        throwCode(environment, payloadCode(inspected));
        return nullptr;
    }
    std::array<std::uint8_t, ah::metadata::kBindingBytes> encoded{};
    std::size_t written = 0;
    if (ah::metadata::encodeUntrustedBinding(
            binding, encoded.data(), encoded.size(), &written) != ah::metadata::Status::kSuccess) {
        throwCode(environment, "AAH-RUNTIME-CONTAINER-METADATA");
        return nullptr;
    }
    return makeByteArray(environment, encoded.data(), written);
}

extern "C" JNIEXPORT jlong JNICALL
Java_ah_runtime_loader_NativePayloadBridge_nativeOpenVerifiedPayload(
    JNIEnv* environment, jclass, jstring installed_apk_path,
    jstring installed_package_name, jbyteArray installed_signer_sha256) {
    UtfChars path(environment, installed_apk_path);
    UtfChars package_name(environment, installed_package_name);
    if (!validPath(path) || package_name.get() == nullptr || package_name.size() == 0 ||
        package_name.size() > 255 || installed_signer_sha256 == nullptr ||
        environment->GetArrayLength(installed_signer_sha256) !=
            static_cast<jsize>(ah::container::kDigestBytes)) {
        throwCode(environment, "AAH-RUNTIME-CONTAINER-ARGUMENT");
        return 0;
    }
    std::array<std::uint8_t, ah::container::kDigestBytes> signer{};
    environment->GetByteArrayRegion(
        installed_signer_sha256, 0, static_cast<jsize>(signer.size()),
        reinterpret_cast<jbyte*>(signer.data()));
    if (environment->ExceptionCheck()) {
        return 0;
    }
    ah::apk::ReadOnlyMapping apk{};
    const ah::apk::Status mapped = apk.openAbsolute(path.get());
    if (mapped != ah::apk::Status::kSuccess) {
        ah::crypto::secureZero(signer.data(), signer.size());
        throwCode(environment, mappedApkCode(mapped));
        return 0;
    }
    ah::zip::FixedAssets assets{};
    const ah::zip::Status located = ah::zip::locateFixedAssets(apk.bytes(), &assets);
    if (located != ah::zip::Status::kSuccess) {
        ah::crypto::secureZero(signer.data(), signer.size());
        throwCode(environment, zipCode(located));
        return 0;
    }
    ah::memory::PayloadHandle payload_handle{};
    ah::payload::AuthenticatedMetadata metadata{};
    bool cleanup_failed = false;
    const ah::payload::OpenRequest request{
        assets,
        ah::share::currentSlot(),
        ah::share::currentAbiId(),
        {signer.data(), signer.size()},
        {reinterpret_cast<const std::uint8_t*>(package_name.get()), package_name.size()},
    };
    const ah::payload::Status opened = ah::payload::openAuthenticatedPayload(
        request, &payload_handle, &metadata, &cleanup_failed);
    ah::crypto::secureZero(signer.data(), signer.size());
    if (opened != ah::payload::Status::kSuccess) {
        throwCodeWithCleanup(environment, payloadCode(opened), cleanup_failed);
        return 0;
    }
    std::uint64_t typed_handle = 0;
    const ah::handles::Status installed =
        ah::handles::install(&payload_handle, metadata, &typed_handle);
    if (installed != ah::handles::Status::kSuccess || typed_handle == 0 ||
        typed_handle > static_cast<std::uint64_t>(std::numeric_limits<jlong>::max())) {
        const bool install_cleanup_failed =
            payload_handle.close() == ah::memory::Status::kCleanupFailed;
        throwCodeWithCleanup(
            environment, "AAH-RUNTIME-CONTAINER-HANDLE", install_cleanup_failed);
        return 0;
    }
    return static_cast<jlong>(typed_handle);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_ah_runtime_loader_NativePayloadBridge_nativeAuthenticatedMetadata(
    JNIEnv* environment, jclass, jlong handle) {
    if (handle <= 0) {
        throwCode(environment, "AAH-RUNTIME-CONTAINER-HANDLE");
        return nullptr;
    }
    MetadataContext context{environment, nullptr};
    const ah::handles::Status consumed = ah::handles::consume(
        static_cast<std::uint64_t>(handle), encodeMetadata, &context);
    if (consumed != ah::handles::Status::kSuccess && !environment->ExceptionCheck()) {
        throwCode(environment, "AAH-RUNTIME-CONTAINER-HANDLE");
    }
    return context.result;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_ah_runtime_loader_NativePayloadBridge_nativeDexBuffers(
    JNIEnv* environment, jclass, jlong handle) {
    if (handle <= 0) {
        throwCode(environment, "AAH-RUNTIME-CONTAINER-HANDLE");
        return nullptr;
    }
    BufferContext context{environment, nullptr};
    const ah::handles::Status consumed = ah::handles::consume(
        static_cast<std::uint64_t>(handle), createBuffers, &context);
    if (consumed != ah::handles::Status::kSuccess && !environment->ExceptionCheck()) {
        throwCode(environment, "AAH-RUNTIME-CONTAINER-HANDLE");
    }
    return context.result;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_ah_runtime_loader_NativePayloadBridge_nativeApplyMemoryProfile(
    JNIEnv* environment, jclass, jlong handle, jint profile) {
    if (environment == nullptr) return nullptr;
    if (handle <= 0 || profile < 0 || profile > 2) {
        throwMemoryCode(environment, "AAH-RUNTIME-MEMORY-ARGUMENT");
        return nullptr;
    }
    ah::memory::Capabilities capabilities{};
    const ah::handles::Status applied = ah::handles::applyProfile(
        static_cast<std::uint64_t>(handle),
        static_cast<ah::memory::Profile>(profile),
        &capabilities);
    if (applied != ah::handles::Status::kSuccess) {
        throwMemoryCode(environment, "AAH-RUNTIME-MEMORY-HANDLE");
        return nullptr;
    }
    const std::array<jlong, 4> values{
        capabilities.dont_dump ? 1 : 0,
        static_cast<jlong>(capabilities.locked_bytes),
        capabilities.process_dumpable ? 1 : 0,
        static_cast<jlong>(capabilities.jitter_milliseconds),
    };
    jlongArray result = environment->NewLongArray(static_cast<jsize>(values.size()));
    if (result == nullptr) return nullptr;
    environment->SetLongArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_ah_runtime_loader_NativePayloadBridge_nativeClosePayload(
    JNIEnv* environment, jclass, jlong handle) {
    if (handle <= 0) {
        throwCode(environment, "AAH-RUNTIME-CONTAINER-HANDLE");
        return;
    }
    const ah::handles::Status closed =
        ah::handles::close(static_cast<std::uint64_t>(handle));
    if (closed == ah::handles::Status::kCleanupFailed) {
        throwCode(environment, kCleanupCode);
    } else if (closed != ah::handles::Status::kSuccess) {
        throwCode(environment, "AAH-RUNTIME-CONTAINER-HANDLE");
    }
}

extern "C" JNIEXPORT jintArray JNICALL
Java_ah_runtime_risk_NativeRiskSignals_collect(JNIEnv* environment, jclass) {
    if (environment == nullptr) return nullptr;
    const ah::risk::Collected collected = ah::risk::collectCurrentProcess();
    const std::array<jint, 4> values{
        1,
        static_cast<jint>(collected.tracer),
        static_cast<jint>(collected.mappings),
        static_cast<jint>(collected.mapping_family_mask),
    };
    jintArray result = environment->NewIntArray(static_cast<jsize>(values.size()));
    if (result != nullptr) {
        environment->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
    }
    return result;
}
