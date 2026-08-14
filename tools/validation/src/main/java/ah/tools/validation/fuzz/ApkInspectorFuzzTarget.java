package ah.tools.validation.fuzz;

import ah.host.inspector.ApkInspector;
import ah.host.inspector.InspectionException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;

public final class ApkInspectorFuzzTarget {
    private ApkInspectorFuzzTarget() {}

    public static void fuzzerTestOneInput(byte[] input) throws Exception {
        if (input.length > FuzzSupport.MAX_INPUT_BYTES) return;
        byte[] immutable = input.clone();
        Path candidate = FuzzSupport.workFile("apk-input-" + Thread.currentThread().getId() + ".apk");
        Files.write(
                candidate,
                immutable,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        byte[] before = FuzzSupport.sha256(immutable);
        try {
            new ApkInspector().inspect(candidate);
        } catch (Exception failure) {
            if (!(failure instanceof InspectionException expectedRejection)) throw failure;
            if (expectedRejection.getCode() == null) {
                throw new AssertionError("inspection rejection must have a stable code");
            }
        } finally {
            byte[] afterBytes = Files.readAllBytes(candidate);
            if (!MessageDigest.isEqual(before, FuzzSupport.sha256(afterBytes))) {
                throw new AssertionError("ApkInspector modified the untrusted input");
            }
            Files.deleteIfExists(candidate);
        }
    }
}
