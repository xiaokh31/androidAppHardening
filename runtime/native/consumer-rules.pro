# JNI uses the frozen Java_ah_runtime_loader_NativePayloadBridge_* symbol names.
# Keep only this package-private bridge surface; callers and public facade code remain shrinkable.
-keep class ah.runtime.loader.NativePayloadBridge { *; }
