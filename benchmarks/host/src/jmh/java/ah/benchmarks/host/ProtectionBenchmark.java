package ah.benchmarks.host;

import ah.integration.fixtures.FixtureCatalog;
import ah.integration.fixtures.FixtureDescriptor;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.runner.IterationType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@State(Scope.Benchmark)
public class ProtectionBenchmark {
    private static final long MIB = 1024L * 1024L;
    private static final String STRESS = "synthetic-100mib";

    @Param({"java-single-dex", "kotlin-multidex", "jni-four-abi", STRESS})
    public String fixtureId;

    private Path root;
    private Path trial;
    private Path signedInput;
    private Path keystore;
    private String password;
    private boolean measurement;
    private final AtomicLong sequence = new AtomicLong();

    @Setup(Level.Trial)
    public void setupTrial() throws Exception {
        root = Path.of(required("m305.root")).toAbsolutePath().normalize();
        Path owned = Path.of(required("m305.work")).toAbsolutePath().normalize();
        if (!owned.startsWith(root.resolve("benchmarks/host/build").toAbsolutePath().normalize())) {
            throw new IllegalStateException("benchmark work must remain under benchmarks/host/build");
        }
        trial = owned.resolve("trials").resolve(fixtureId);
        deleteTree(trial);
        Files.createDirectories(trial);
        FixtureDescriptor fixture = FixtureCatalog.INSTANCE.load(root).stream()
            .filter(candidate -> candidate.getId().equals(fixtureId.equals(STRESS) ? "java-single-dex" : fixtureId))
            .findFirst().orElseThrow();
        Path unsigned = fixture.getUnsignedFixtureApk();
        if (fixtureId.equals(STRESS)) {
            unsigned = trial.resolve("synthetic-100mib-unsigned.apk");
            createStressApk(fixture.getUnsignedFixtureApk(), unsigned);
            if (Files.size(unsigned) < 100L * MIB) throw new IllegalStateException("100 MiB stress input was not created");
        }
        keystore = trial.resolve("ephemeral.jks");
        password = randomPassword();
        run(List.of(keytool(), "-genkeypair", "-noprompt", "-keystore", keystore.toString(),
            "-storepass", password, "-keypass", password, "-alias", "m305", "-keyalg", "RSA",
            "-keysize", "3072", "-validity", "2", "-dname", "CN=M3-05 Synthetic Benchmark,O=androidAppHardening,C=XX"),
            Duration.ofMinutes(1));
        signedInput = trial.resolve("input-signed.apk");
        sign(unsigned, signedInput);
        Files.writeString(trial.resolve("input.sha256"), sha256(signedInput) + "\n", StandardCharsets.UTF_8);
    }

    @Setup(Level.Iteration)
    public void setupIteration(IterationParams params) {
        measurement = params.getType() == IterationType.MEASUREMENT;
    }

    @Benchmark
    public long protect() throws Exception {
        long id = sequence.incrementAndGet();
        Path output = trial.resolve("output-" + id + ".apk");
        Path report = trial.resolve("report-" + id + ".json");
        String inputBefore = sha256(signedInput);
        ProcessResult result = runMeasured(List.of(java(), "-cp", System.getProperty("java.class.path"),
            "ah.host.cli.CliMain", "protect", "--input", signedInput.toString(), "--output", output.toString(),
            "--report", report.toString()), Duration.ofMinutes(2));
        if (result.exitCode != 0 || !Files.isRegularFile(output) || !inputBefore.equals(sha256(signedInput))) {
            throw new IllegalStateException("protect failed or changed input: " + result.output.substring(0, Math.min(500, result.output.length())));
        }
        if (measurement) {
            Files.writeString(trial.resolve("samples.csv"), result.processingMillis + "," + result.peakRssBytes + "," +
                output.getFileName() + "," + result.wallMillis + "\n", StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        }
        return Files.size(output);
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() throws Exception {
        try {
            List<String> lines = Files.readAllLines(trial.resolve("samples.csv"), StandardCharsets.UTF_8);
            if (lines.isEmpty()) throw new IllegalStateException("no measured samples");
            String[] last = lines.get(lines.size() - 1).split(",");
            Path output = trial.resolve(last[2]);
            Path signedOutput = trial.resolve("output-externally-signed.apk");
            sign(output, signedOutput);
            Map<String, Object> sizes = HostBenchmarkMain.artifactSizes(root, fixtureId, signedInput, output, signedOutput);
            Files.writeString(trial.resolve("artifact-sizes.json"), HostBenchmarkMain.json(sizes) + "\n", StandardCharsets.UTF_8);
        } finally {
            Files.deleteIfExists(keystore);
            password = null;
            if (Files.exists(keystore)) throw new IllegalStateException("ephemeral keystore cleanup failed");
        }
    }

    private void sign(Path input, Path output) throws Exception {
        run(List.of(apksigner(), "sign", "--ks", keystore.toString(), "--ks-key-alias", "m305",
            "--ks-pass", "pass:" + password, "--key-pass", "pass:" + password,
            "--out", output.toString(), input.toString()), Duration.ofMinutes(1));
    }

    private ProcessResult runMeasured(List<String> command, Duration timeout) throws Exception {
        List<String> actual = wrapBatch(command);
        Process process = new ProcessBuilder(actual).redirectErrorStream(true).start();
        AtomicReference<Throwable> readFailure = new AtomicReference<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream input = process.getInputStream()) { input.transferTo(output); }
            catch (Throwable failure) { readFailure.set(failure); }
        }, "m305-output-reader");
        reader.start();
        long start = System.nanoTime();
        long peak = 0;
        long cpuNanos = 0;
        boolean cpuObserved = false;
        while (process.isAlive()) {
            peak = Math.max(peak, residentBytes(process.pid()));
            var cpu = process.toHandle().info().totalCpuDuration();
            if (cpu.isPresent()) {
                cpuNanos = Math.max(cpuNanos, cpu.get().toNanos());
                cpuObserved = true;
            }
            Thread.sleep(2);
            if (System.nanoTime() - start > timeout.toNanos()) {
                process.destroyForcibly();
                throw new IllegalStateException("benchmark child timeout");
            }
        }
        peak = Math.max(peak, residentBytes(process.pid()));
        var finalCpu = process.toHandle().info().totalCpuDuration();
        if (finalCpu.isPresent()) {
            cpuNanos = Math.max(cpuNanos, finalCpu.get().toNanos());
            cpuObserved = true;
        }
        reader.join(10_000);
        if (readFailure.get() != null) throw new IOException("child output read failed", readFailure.get());
        if (!cpuObserved || cpuNanos <= 0) throw new IllegalStateException("child CPU duration unavailable");
        long processing = Math.max(1, TimeUnit.NANOSECONDS.toMillis(cpuNanos));
        long wall = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        return new ProcessResult(process.exitValue(), processing, wall, peak, output.toString(StandardCharsets.UTF_8));
    }

    private static long residentBytes(long pid) {
        if (isWindows()) {
            WinNT.HANDLE handle = Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_QUERY_INFORMATION | WinNT.PROCESS_VM_READ, false, (int) pid);
            if (handle == null) return 0;
            try {
                ProcessMemoryCounters counters = new ProcessMemoryCounters();
                counters.cb = new WinDef.DWORD(counters.size());
                return ProcessMemory.INSTANCE.GetProcessMemoryInfo(handle, counters, counters.size())
                    ? counters.PeakWorkingSetSize.longValue() : 0;
            } finally {
                Kernel32.INSTANCE.CloseHandle(handle);
            }
        }
        Path status = Path.of("/proc", Long.toString(pid), "status");
        if (!Files.isRegularFile(status)) return 0;
        try {
            for (String line : Files.readAllLines(status, StandardCharsets.US_ASCII)) {
                if (line.startsWith("VmHWM:") || line.startsWith("VmRSS:")) {
                    String digits = line.replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) return Long.parseLong(digits) * 1024L;
                }
            }
        } catch (IOException | NumberFormatException ignored) {
            return 0;
        }
        return 0;
    }

    private static void createStressApk(Path source, Path target) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        CRC32 crc = new CRC32();
        long padding = 100L * MIB;
        for (long remaining = padding; remaining > 0; remaining -= Math.min(remaining, buffer.length)) {
            crc.update(buffer, 0, (int) Math.min(remaining, buffer.length));
        }
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(source));
             ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(target))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                ZipEntry copy = new ZipEntry(entry.getName());
                output.putNextEntry(copy);
                input.transferTo(output);
                output.closeEntry();
            }
            ZipEntry paddingEntry = new ZipEntry("assets/m305-padding.bin");
            paddingEntry.setMethod(ZipEntry.STORED);
            paddingEntry.setSize(padding);
            paddingEntry.setCompressedSize(padding);
            paddingEntry.setCrc(crc.getValue());
            output.putNextEntry(paddingEntry);
            for (long remaining = padding; remaining > 0; remaining -= Math.min(remaining, buffer.length)) {
                output.write(buffer, 0, (int) Math.min(remaining, buffer.length));
            }
            output.closeEntry();
        }
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        String value = HexFormat.of().formatHex(bytes);
        java.util.Arrays.fill(bytes, (byte) 0);
        return value;
    }

    private static void run(List<String> command, Duration timeout) throws Exception {
        Process process = new ProcessBuilder(wrapBatch(command)).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream input = process.getInputStream()) { input.transferTo(output); }
            catch (IOException ignored) { }
        });
        reader.start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("command timeout");
        }
        reader.join(10_000);
        if (process.exitValue() != 0) throw new IllegalStateException("command failed: " + output.toString(StandardCharsets.UTF_8));
    }

    private static List<String> wrapBatch(List<String> command) {
        if (isWindows() && (command.get(0).endsWith(".bat") || command.get(0).endsWith(".cmd"))) {
            ArrayList<String> wrapped = new ArrayList<>(List.of("cmd.exe", "/d", "/c"));
            wrapped.addAll(command);
            return wrapped;
        }
        return command;
    }

    private static String sdkTool(String name) {
        String sdk = System.getenv("ANDROID_HOME");
        if (sdk == null || sdk.isBlank()) sdk = System.getenv("ANDROID_SDK_ROOT");
        if (sdk == null || sdk.isBlank()) throw new IllegalStateException("pinned Android SDK unavailable");
        return Path.of(sdk, "build-tools", "36.1.0", name + (isWindows() ? ".bat" : "")).toString();
    }

    private static String apksigner() { return sdkTool("apksigner"); }
    private static String keytool() { return Path.of(System.getProperty("java.home"), "bin", "keytool" + (isWindows() ? ".exe" : "")).toString(); }
    private static String java() { return Path.of(System.getProperty("java.home"), "bin", "java" + (isWindows() ? ".exe" : "")).toString(); }
    private static boolean isWindows() { return System.getProperty("os.name").toLowerCase().contains("windows"); }
    private static String required(String name) { return java.util.Objects.requireNonNull(System.getProperty(name), name); }
    private static String sha256(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) { stream.sorted(java.util.Comparator.reverseOrder()).forEach(item -> { try { Files.delete(item); } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); } }); }
    }

    private record ProcessResult(int exitCode, long processingMillis, long wallMillis,
                                 long peakRssBytes, String output) {}

    private interface ProcessMemory extends StdCallLibrary {
        ProcessMemory INSTANCE = Native.load("psapi", ProcessMemory.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean GetProcessMemoryInfo(WinNT.HANDLE process, ProcessMemoryCounters counters, int size);
    }

    @Structure.FieldOrder({"cb", "PageFaultCount", "PeakWorkingSetSize", "WorkingSetSize",
        "QuotaPeakPagedPoolUsage", "QuotaPagedPoolUsage", "QuotaPeakNonPagedPoolUsage",
        "QuotaNonPagedPoolUsage", "PagefileUsage", "PeakPagefileUsage"})
    public static final class ProcessMemoryCounters extends Structure {
        public WinDef.DWORD cb;
        public WinDef.DWORD PageFaultCount;
        public BaseTSD.SIZE_T PeakWorkingSetSize;
        public BaseTSD.SIZE_T WorkingSetSize;
        public BaseTSD.SIZE_T QuotaPeakPagedPoolUsage;
        public BaseTSD.SIZE_T QuotaPagedPoolUsage;
        public BaseTSD.SIZE_T QuotaPeakNonPagedPoolUsage;
        public BaseTSD.SIZE_T QuotaNonPagedPoolUsage;
        public BaseTSD.SIZE_T PagefileUsage;
        public BaseTSD.SIZE_T PeakPagefileUsage;
    }
}
