package ah.tools.validation.fuzz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class FuzzSupport {
    static final int MAX_INPUT_BYTES = 4 * 1024 * 1024;

    private FuzzSupport() {}

    static Path workFile(String name) throws IOException {
        String configured = System.getProperty("ah.m302.workDir", "build/fuzz-work/jvm");
        Path root = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root.resolve(name).normalize();
    }

    static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static String hex(byte[] value) {
        StringBuilder output = new StringBuilder(value.length * 2);
        for (byte current : value) {
            output.append(Character.forDigit((current >>> 4) & 0xf, 16));
            output.append(Character.forDigit(current & 0xf, 16));
        }
        return output.toString();
    }
}
