-keep class ah.runtime.bootstrap.ShellAppComponentFactory { public *; }
# The Release/R8 target and separately installed instrumentation APK share these
# bounded PoC diagnostics by binary name. They are not product Runtime APIs.
-keep class ah.runtime.bootstrap.ClassLoaderProbe { public *; }
-keep class ah.runtime.bootstrap.ProbeEvent { public *; }
-keep class ah.runtime.bootstrap.EarlySignerProbe { public *; }
-keep class ah.runtime.bootstrap.EarlySignerResult { public *; }
-keep class ah.runtime.bootstrap.NativeLibrarySearchPath { public *; }
-keep class ah.runtime.bootstrap.NativeLibrarySearchPathResolver { public *; }
-keep class ah.fixtures.android.CompatibilityPocRunner { public *; }
-keep class ah.fixtures.android.ProbeSignal { public *; }
-keep class ah.runtime.loader.PayloadRuntime { *; }
-keep class ah.runtime.loader.PayloadRuntime$* { *; }
-keep class ah.runtime.loader.LoadedPayload { *; }
-keep class ah.runtime.loader.AuthenticatedPayloadMetadata { *; }
-keep class ah.runtime.loader.NativePayloadBridge { *; }
# The separately installed M2-02 instrumentation fixture asserts the exact
# public-API Native search path selected by this package-private helper.
-keep class ah.runtime.loader.PayloadClassLoaders { *; }
-keep class ah.fixtures.android.m202.M202ColdStartActivity { *; }
-keep class ah.fixtures.android.m202.M202NativeTestHooks { *; }
-keepattributes *Annotation*,InnerClasses,EnclosingMethod
