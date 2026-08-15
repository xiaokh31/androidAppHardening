package ah.fixtures.android.m301;

import android.content.Context;
import android.os.Process;
import android.os.SystemClock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;

final class FixtureTimings {
    private static final String FILE_NAME = "startup-timings.txt";

    private FixtureTimings() {}

    static void begin(Context context) {
        long processStart = Process.getStartElapsedRealtime();
        long application = SystemClock.elapsedRealtime();
        if (processStart <= 0 || application < processStart) throw new IllegalStateException("invalid startup clock");
        write(context, processStart + "\n" + application + "\n0\n", false);
    }

    static void markInteractive(Context context) {
        long interactive = SystemClock.elapsedRealtime();
        long[] current = read(context);
        if (current[0] <= 0 || current[1] < current[0] || interactive < current[1]) {
            throw new IllegalStateException("invalid interactive clock");
        }
        write(context, current[0] + "\n" + current[1] + "\n" + interactive + "\n", false);
    }

    static long[] read(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.isFile()) return new long[] {0, 0, 0};
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.US_ASCII);
            if (lines.size() != 3) return new long[] {0, 0, 0};
            return new long[] {Long.parseLong(lines.get(0)), Long.parseLong(lines.get(1)), Long.parseLong(lines.get(2))};
        } catch (Exception failure) {
            throw new IllegalStateException("startup timing read failed", failure);
        }
    }

    private static void write(Context context, String value, boolean append) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        try {
            if (append) {
                Files.write(file.toPath(), value.getBytes(StandardCharsets.US_ASCII),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.write(file.toPath(), value.getBytes(StandardCharsets.US_ASCII),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (Exception failure) {
            throw new IllegalStateException("startup timing write failed", failure);
        }
    }
}
