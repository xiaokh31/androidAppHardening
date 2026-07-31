#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL
Java_ah_fixtures_android_payload_PayloadJni_nativeMarker(JNIEnv* env, jclass) {
    return env->NewStringUTF("M0-05-JNI-FIXED");
}
