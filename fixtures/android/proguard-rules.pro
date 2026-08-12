-keep class ah.runtime.bootstrap.ShellAppComponentFactory { public *; }
-keep class ah.runtime.bootstrap.LegacyShellAppComponentFactory { public *; }
-keep class ah.runtime.bootstrap.EarlyConfigProbe { public *; }
-keep class ah.runtime.bootstrap.EarlyConfigResult { public *; }
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
-keep class ah.runtime.loader.PayloadMemoryHandle { *; }
-keep class ah.runtime.loader.MemoryProfile { *; }
-keep class ah.runtime.loader.MemoryProtectionCapabilities { *; }
-keep class ah.runtime.loader.AuthenticatedPayloadMetadata { *; }
-keep class ah.runtime.loader.UntrustedPayloadBinding { *; }
-keep class ah.runtime.loader.NativePayloadBridge { *; }
# The separately installed M2-02 instrumentation fixture calls code() on the
# frozen exception binary name. Keep this only in the synthetic Release target;
# the production AAR consumer rule remains limited to the JNI constructor.
-keep class ah.runtime.loader.PayloadLoadException { *; }
# The separately installed M2-02 instrumentation fixture asserts the exact
# public-API Native search path selected by this package-private helper.
-keep class ah.runtime.loader.PayloadClassLoaders { *; }
-keep class ah.fixtures.android.m202.M202ColdStartActivity { *; }
-keep class ah.runtime.guard.RuntimeStartupGuard { *; }
-keep class ah.runtime.guard.RuntimeStartupGuard$* { *; }
-keep class ah.runtime.guard.RuntimeIntegrityFailure { *; }
-keep class ah.runtime.guard.IntegrityChecks { *; }
-keep class ah.runtime.guard.RuntimeSignerVerifier { *; }
-keep class ah.runtime.guard.RuntimeSignerVerifier$* { *; }
-keep class ah.runtime.guard.VerifiedPayloadSession { *; }
-keep class ah.runtime.guard.VerifiedSignerIdentity { *; }
-keep class ah.runtime.guard.VerifiedStartupConfiguration { *; }
-keep class ah.runtime.MemoryControls { *; }
-keep class ah.runtime.MemoryProtectionReport { *; }
-keep class ah.fixtures.android.m203.M203ColdStartActivity { *; }
-keep class ah.fixtures.android.m201.M201DeviceRunner { *; }
# The separately installed M2-03 instrumentation fixture exercises the M2-05
# public facade against this synthetic Release/R8 target.
-keep class ah.runtime.risk.** { *; }
-keepattributes *Annotation*,InnerClasses,EnclosingMethod
