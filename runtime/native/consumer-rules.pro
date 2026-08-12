# JNI uses the frozen Java_ah_runtime_loader_NativePayloadBridge_* symbol names.
# Keep only this package-private bridge surface; callers and public facade code remain shrinkable.
-keep class ah.runtime.loader.NativePayloadBridge { *; }
# Native code resolves this fail-closed exception by binary name and invokes its
# single-String constructor. Preserve that JNI contract in every R8 consumer.
-keep class ah.runtime.loader.PayloadLoadException {
    <init>(java.lang.String);
}
# M2-05 uses this package-private fixed JNI method and returns only normalized states.
-keep class ah.runtime.risk.NativeRiskSignals { *; }
