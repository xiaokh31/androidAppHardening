package ah.tools.validation.fuzz;

import ah.host.axml.AxmlTransformException;
import ah.host.axml.BinaryManifestTransformer;
import ah.host.axml.ManifestTransformRequest;
import ah.host.inspector.ManifestSummary;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class BinaryAxmlFuzzTarget {
    private static final String PACKAGE_NAME = "ah.fixtures.m302";
    private static final ManifestTransformRequest REQUEST = new ManifestTransformRequest(
            new ManifestSummary(
                    PACKAGE_NAME,
                    FuzzSupport.sha256(PACKAGE_NAME.getBytes(StandardCharsets.UTF_8)),
                    29,
                    36,
                    null,
                    null,
                    null));

    private BinaryAxmlFuzzTarget() {}

    public static void fuzzerTestOneInput(byte[] input) throws Exception {
        if (input.length > FuzzSupport.MAX_INPUT_BYTES) return;
        byte[] immutable = input.clone();
        byte[] before = FuzzSupport.sha256(immutable);
        try {
            BinaryManifestTransformer.INSTANCE.transform(immutable, REQUEST);
        } catch (Exception failure) {
            if (!(failure instanceof AxmlTransformException expectedRejection)) throw failure;
            if (expectedRejection.getCode() == null) {
                throw new AssertionError("AXML rejection must have a stable code");
            }
        }
        if (!MessageDigest.isEqual(before, FuzzSupport.sha256(immutable))) {
            throw new AssertionError("BinaryManifestTransformer modified the untrusted input");
        }
    }
}
