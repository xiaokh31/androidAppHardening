package ah.runtime.guard;

import android.content.pm.ApplicationInfo;
import ah.runtime.loader.AuthenticatedPayloadMetadata;
import ah.runtime.loader.LoadedPayload;
import ah.runtime.loader.PayloadRuntime;
import ah.runtime.loader.UntrustedPayloadBinding;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** The unique production entry point for signer, metadata and payload startup verification. */
public final class RuntimeStartupGuard {
    enum GuardStage {
        LOADED_PAYLOAD,
        METADATA,
        IDENTITY,
        CONFIGURATION,
        SESSION,
        BEFORE_RETURN
    }

    interface GuardFailureProbe {
        void hit(GuardStage stage);

        default void close(LoadedPayload payload) {
            payload.close();
        }

        default UntrustedPayloadBinding binding(UntrustedPayloadBinding binding) {
            return binding;
        }

        default AuthenticatedPayloadMetadata metadata(LoadedPayload payload) {
            return payload.authenticatedMetadata();
        }

        default void verifyMetadata(
                AuthenticatedPayloadMetadata metadata,
                UntrustedPayloadBinding binding,
                byte[] packageNameSha256,
                RuntimeSignerVerifier.Measurement measurement) {
            IntegrityChecks.verifyAuthenticatedMetadata(
                    metadata, binding, packageNameSha256, measurement);
        }

        default void closed() {}
    }

    private RuntimeStartupGuard() {}

    public static VerifiedPayloadSession openVerifiedPayload(
            ApplicationInfo applicationInfo, ClassLoader shellLoader) {
        return openVerifiedPayloadInternal(applicationInfo, shellLoader, null);
    }

    static VerifiedPayloadSession openVerifiedPayloadForTesting(
            ApplicationInfo applicationInfo,
            ClassLoader shellLoader,
            GuardFailureProbe failureProbe) {
        if (failureProbe == null) {
            throw RuntimeIntegrityFailure.create("ARGUMENT");
        }
        return openVerifiedPayloadInternal(applicationInfo, shellLoader, failureProbe);
    }

    private static VerifiedPayloadSession openVerifiedPayloadInternal(
            ApplicationInfo applicationInfo,
            ClassLoader shellLoader,
            GuardFailureProbe failureProbe) {
        if (shellLoader == null) {
            throw RuntimeIntegrityFailure.create("ARGUMENT");
        }
        RuntimeSignerVerifier.Measurement measurement = RuntimeSignerVerifier.verify(applicationInfo);
        byte[] packageNameSha256 = sha256(applicationInfo.packageName);
        UntrustedPayloadBinding binding;
        try {
            binding = PayloadRuntime.inspectBinding(applicationInfo);
        } catch (RuntimeException failure) {
            throw RuntimeIntegrityFailure.create("CONTAINER", failure);
        }
        if (failureProbe != null) {
            binding = failureProbe.binding(binding);
        }
        IntegrityChecks.verifyPreReadSigner(binding, measurement.currentSignerSha256());

        LoadedPayload loadedPayload = null;
        VerifiedPayloadSession session = null;
        boolean committed = false;
        Throwable primary = null;
        try {
            loadedPayload =
                    PayloadRuntime.openVerified(
                            shellLoader, applicationInfo, measurement.currentSignerSha256());
            hit(failureProbe, GuardStage.LOADED_PAYLOAD);
            AuthenticatedPayloadMetadata ownedMetadata = loadedPayload.authenticatedMetadata();
            AuthenticatedPayloadMetadata metadata = failureProbe == null
                    ? ownedMetadata
                    : failureProbe.metadata(loadedPayload);
            if (metadata != ownedMetadata) {
                throw RuntimeIntegrityFailure.create("METADATA_HANDLE");
            }
            if (failureProbe == null) {
                IntegrityChecks.verifyAuthenticatedMetadata(
                        metadata, binding, packageNameSha256, measurement);
            } else {
                failureProbe.verifyMetadata(metadata, binding, packageNameSha256, measurement);
            }
            hit(failureProbe, GuardStage.METADATA);
            VerifiedSignerIdentity identity =
                    new VerifiedSignerIdentity(
                            measurement.currentSignerSha256(),
                            measurement.signerLineageSha256());
            hit(failureProbe, GuardStage.IDENTITY);
            VerifiedStartupConfiguration configuration =
                    new VerifiedStartupConfiguration(metadata);
            hit(failureProbe, GuardStage.CONFIGURATION);
            session = new VerifiedPayloadSession(loadedPayload, identity, configuration);
            hit(failureProbe, GuardStage.SESSION);
            hit(failureProbe, GuardStage.BEFORE_RETURN);
            committed = true;
            return session;
        } catch (RuntimeIntegrityFailure failure) {
            primary = failure;
            throw failure;
        } catch (RuntimeException failure) {
            RuntimeIntegrityFailure mapped = RuntimeIntegrityFailure.create("CONTAINER", failure);
            primary = mapped;
            throw mapped;
        } catch (Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (!committed && loadedPayload != null) {
                try {
                    if (failureProbe == null) {
                        loadedPayload.close();
                    } else {
                        failureProbe.close(loadedPayload);
                    }
                } catch (RuntimeException | Error cleanupFailure) {
                    if (primary == null) {
                        throw cleanupFailure;
                    }
                    try {
                        primary.addSuppressed(cleanupFailure);
                    } catch (RuntimeException | Error ignored) {
                        // Preserve the primary failure when suppression itself cannot allocate.
                    }
                } finally {
                    if (failureProbe != null) {
                        try {
                            failureProbe.closed();
                        } catch (RuntimeException | Error ignored) {
                            // Test observation must never replace startup or cleanup semantics.
                        }
                    }
                }
            }
            packageNameSha256 = null;
            binding = null;
            loadedPayload = null;
            if (!committed) {
                session = null;
            }
        }
    }

    private static byte[] sha256(String value) {
        if (value == null || value.isEmpty()) {
            throw RuntimeIntegrityFailure.create("ARGUMENT");
        }
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException failure) {
            throw RuntimeIntegrityFailure.create("INTERNAL", failure);
        }
    }

    private static void hit(GuardFailureProbe failureProbe, GuardStage stage) {
        if (failureProbe != null) {
            failureProbe.hit(stage);
        }
    }
}
