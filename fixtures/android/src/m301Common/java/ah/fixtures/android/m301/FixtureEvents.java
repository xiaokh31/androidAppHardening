package ah.fixtures.android.m301;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class FixtureEvents {
    private static final String FILE_NAME = "m301-events.txt";

    private FixtureEvents() {}

    static void append(Context context, String event) {
        appendFile(new File(context.getFilesDir(), FILE_NAME), event);
    }

    static void appendDataDir(String dataDir, String event) {
        appendFile(new File(new File(dataDir, "files"), FILE_NAME), event);
    }

    static List<String> read(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.isFile()) return Collections.emptyList();
        try {
            List<String> result = new ArrayList<>();
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (line.matches("[a-z0-9_.-]{1,64}")) result.add(line);
            }
            return Collections.unmodifiableList(result);
        } catch (Exception failure) {
            throw new IllegalStateException("M3-01 event read failed", failure);
        }
    }

    private static void appendFile(File file, String event) {
        if (!event.matches("[a-z0-9_.-]{1,64}")) throw new IllegalArgumentException("invalid event");
        File parent = file.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IllegalStateException("M3-01 event directory unavailable");
        }
        byte[] bytes = (event + "\n").getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream stream = new FileOutputStream(file, true);
             FileChannel channel = stream.getChannel();
             FileLock ignored = channel.lock()) {
            stream.write(bytes);
            stream.flush();
            channel.force(true);
        } catch (Exception failure) {
            throw new IllegalStateException("M3-01 event write failed", failure);
        }
    }
}
