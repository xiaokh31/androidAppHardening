package ah.runtime.bootstrap;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.Adler32;
import java.util.zip.CRC32;

final class StoredDexReader {
    static final String ENTRY_NAME = "assets/ah/poc/classes.dex";

    private static final int EOCD_SIGNATURE = 0x06054b50;
    private static final int CENTRAL_SIGNATURE = 0x02014b50;
    private static final int LOCAL_SIGNATURE = 0x04034b50;
    private static final int EOCD_MIN_SIZE = 22;
    private static final int MAX_COMMENT_SIZE = 65_535;
    private static final int CENTRAL_FIXED_SIZE = 46;
    private static final int LOCAL_FIXED_SIZE = 30;
    private static final int STORED_METHOD = 0;
    private static final int ENCRYPTED_FLAG = 1;
    private static final int DATA_DESCRIPTOR_FLAG = 1 << 3;
    private static final int MAX_DEX_SIZE = 16 * 1024 * 1024;
    private static final byte[] DEX_MAGIC = {'d', 'e', 'x', '\n'};
    private static final int DEX_HEADER_SIZE = 112;
    private static final int DEX_ENDIAN_CONSTANT = 0x12345678;

    private StoredDexReader() {}

    static ByteBuffer read(String sourceDir) {
        if (sourceDir == null || sourceDir.isEmpty()) {
            throw PocFailure.create("missing Framework sourceDir");
        }

        try (FileInputStream input = new FileInputStream(sourceDir);
                FileChannel channel = input.getChannel()) {
            long fileSize = channel.size();
            CentralDirectory central = findCentralDirectory(channel, fileSize);
            Entry entry = findEntry(channel, central, fileSize);
            long dataOffset = findDataOffset(channel, entry, fileSize);

            ByteBuffer payload = ByteBuffer.allocateDirect(entry.uncompressedSize);
            readFully(channel, payload, dataOffset);
            payload.flip();
            validateDex(payload, entry.crc32);
            return payload.asReadOnlyBuffer();
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw PocFailure.create("cannot read packaged payload", exception);
        }
    }

    private static CentralDirectory findCentralDirectory(FileChannel channel, long fileSize)
            throws IOException {
        if (fileSize < EOCD_MIN_SIZE) {
            throw PocFailure.create("APK is shorter than EOCD");
        }

        int tailSize = (int) Math.min(fileSize, EOCD_MIN_SIZE + MAX_COMMENT_SIZE);
        ByteBuffer tail = littleEndianBuffer(tailSize);
        readFully(channel, tail, fileSize - tailSize);
        tail.flip();

        for (int offset = tailSize - EOCD_MIN_SIZE; offset >= 0; offset--) {
            if (tail.getInt(offset) != EOCD_SIGNATURE) {
                continue;
            }

            int diskNumber = unsignedShort(tail, offset + 4);
            int centralDisk = unsignedShort(tail, offset + 6);
            int entriesOnDisk = unsignedShort(tail, offset + 8);
            int totalEntries = unsignedShort(tail, offset + 10);
            long centralSize = unsignedInt(tail, offset + 12);
            long centralOffset = unsignedInt(tail, offset + 16);
            int commentLength = unsignedShort(tail, offset + 20);

            if (offset + EOCD_MIN_SIZE + commentLength != tailSize
                    || diskNumber != 0
                    || centralDisk != 0
                    || entriesOnDisk != totalEntries
                    || totalEntries == 0xffff
                    || centralOffset + centralSize > fileSize) {
                throw PocFailure.create("unsupported or malformed APK directory");
            }
            return new CentralDirectory(centralOffset, centralSize, totalEntries);
        }
        throw PocFailure.create("APK EOCD is missing");
    }

    private static Entry findEntry(
            FileChannel channel, CentralDirectory central, long fileSize) throws IOException {
        long cursor = central.offset;
        long end = central.offset + central.size;
        byte[] expectedName = ENTRY_NAME.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        for (int index = 0; index < central.entryCount; index++) {
            if (cursor + CENTRAL_FIXED_SIZE > end) {
                throw PocFailure.create("truncated central directory");
            }

            ByteBuffer header = littleEndianBuffer(CENTRAL_FIXED_SIZE);
            readFully(channel, header, cursor);
            header.flip();
            if (header.getInt(0) != CENTRAL_SIGNATURE) {
                throw PocFailure.create("invalid central directory signature");
            }

            int flags = unsignedShort(header, 8);
            int method = unsignedShort(header, 10);
            long crc32 = unsignedInt(header, 16);
            long compressedSize = unsignedInt(header, 20);
            long uncompressedSize = unsignedInt(header, 24);
            int nameLength = unsignedShort(header, 28);
            int extraLength = unsignedShort(header, 30);
            int commentLength = unsignedShort(header, 32);
            long localOffset = unsignedInt(header, 42);
            long next = cursor + CENTRAL_FIXED_SIZE + nameLength + extraLength + commentLength;
            if (next > end || next > fileSize || nameLength == 0) {
                throw PocFailure.create("invalid central directory bounds");
            }

            ByteBuffer name = ByteBuffer.allocate(nameLength);
            readFully(channel, name, cursor + CENTRAL_FIXED_SIZE);
            if (Arrays.equals(name.array(), expectedName)) {
                if ((flags & (ENCRYPTED_FLAG | DATA_DESCRIPTOR_FLAG)) != 0
                        || method != STORED_METHOD
                        || compressedSize != uncompressedSize
                        || uncompressedSize <= 0
                        || uncompressedSize > MAX_DEX_SIZE) {
                    throw PocFailure.create("payload ZIP contract is invalid");
                }
                return new Entry(
                        flags,
                        method,
                        Math.toIntExact(uncompressedSize),
                        crc32,
                        localOffset,
                        expectedName);
            }
            cursor = next;
        }
        throw PocFailure.create("payload entry is missing");
    }

    private static long findDataOffset(FileChannel channel, Entry entry, long fileSize)
            throws IOException {
        if (entry.localOffset + LOCAL_FIXED_SIZE > fileSize) {
            throw PocFailure.create("payload local header is truncated");
        }

        ByteBuffer header = littleEndianBuffer(LOCAL_FIXED_SIZE);
        readFully(channel, header, entry.localOffset);
        header.flip();
        if (header.getInt(0) != LOCAL_SIGNATURE
                || unsignedShort(header, 6) != entry.flags
                || unsignedShort(header, 8) != entry.method
                || unsignedInt(header, 14) != entry.crc32
                || unsignedInt(header, 18) != entry.uncompressedSize
                || unsignedInt(header, 22) != entry.uncompressedSize) {
            throw PocFailure.create("payload local header does not match central directory");
        }

        int nameLength = unsignedShort(header, 26);
        int extraLength = unsignedShort(header, 28);
        if (nameLength != entry.name.length) {
            throw PocFailure.create("payload local name length is invalid");
        }

        ByteBuffer name = ByteBuffer.allocate(nameLength);
        readFully(channel, name, entry.localOffset + LOCAL_FIXED_SIZE);
        if (!Arrays.equals(name.array(), entry.name)) {
            throw PocFailure.create("payload local name is invalid");
        }

        long dataOffset = entry.localOffset + LOCAL_FIXED_SIZE + nameLength + extraLength;
        if (dataOffset < 0 || dataOffset + entry.uncompressedSize > fileSize) {
            throw PocFailure.create("payload data is outside APK bounds");
        }
        return dataOffset;
    }

    private static void validateDex(ByteBuffer payload, long expectedCrc32) {
        if (payload.remaining() < DEX_HEADER_SIZE) {
            throw PocFailure.create("payload DEX is empty or truncated");
        }
        for (int index = 0; index < DEX_MAGIC.length; index++) {
            if (payload.get(index) != DEX_MAGIC[index]) {
                throw PocFailure.create("payload DEX magic is invalid");
            }
        }
        if (payload.get(7) != 0) {
            throw PocFailure.create("payload DEX version terminator is invalid");
        }

        ByteBuffer header = payload.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        if (header.getInt(32) != payload.remaining()
                || header.getInt(36) != DEX_HEADER_SIZE
                || header.getInt(40) != DEX_ENDIAN_CONSTANT) {
            throw PocFailure.create("payload DEX header is invalid");
        }

        CRC32 crc32 = new CRC32();
        updateChecksum(crc32, payload.duplicate());
        if (crc32.getValue() != expectedCrc32) {
            throw PocFailure.create("payload ZIP CRC-32 mismatch");
        }

        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            ByteBuffer signedRegion = payload.duplicate();
            signedRegion.position(32);
            sha1.update(signedRegion);
            byte[] expectedSignature = new byte[20];
            ByteBuffer signature = payload.duplicate();
            signature.position(12);
            signature.get(expectedSignature);
            if (!MessageDigest.isEqual(expectedSignature, sha1.digest())) {
                throw PocFailure.create("payload DEX signature mismatch");
            }
        } catch (NoSuchAlgorithmException exception) {
            throw PocFailure.create("platform SHA-1 is unavailable", exception);
        }

        Adler32 adler32 = new Adler32();
        ByteBuffer checksumRegion = payload.duplicate();
        checksumRegion.position(12);
        updateChecksum(adler32, checksumRegion);
        if (Integer.toUnsignedLong(header.getInt(8)) != adler32.getValue()) {
            throw PocFailure.create("payload DEX checksum mismatch");
        }
    }

    private static void updateChecksum(java.util.zip.Checksum checksum, ByteBuffer bytes) {
        while (bytes.hasRemaining()) {
            checksum.update(Byte.toUnsignedInt(bytes.get()));
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer target, long offset)
            throws IOException {
        while (target.hasRemaining()) {
            int read = channel.read(target, offset + target.position());
            if (read < 0) {
                throw PocFailure.create("unexpected end of APK");
            }
            if (read == 0) {
                throw PocFailure.create("APK read made no progress");
            }
        }
    }

    private static ByteBuffer littleEndianBuffer(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static int unsignedShort(ByteBuffer buffer, int offset) {
        return Short.toUnsignedInt(buffer.getShort(offset));
    }

    private static long unsignedInt(ByteBuffer buffer, int offset) {
        return Integer.toUnsignedLong(buffer.getInt(offset));
    }

    private static final class CentralDirectory {
        final long offset;
        final long size;
        final int entryCount;

        CentralDirectory(long offset, long size, int entryCount) {
            this.offset = offset;
            this.size = size;
            this.entryCount = entryCount;
        }
    }

    private static final class Entry {
        final int flags;
        final int method;
        final int uncompressedSize;
        final long crc32;
        final long localOffset;
        final byte[] name;

        Entry(
                int flags,
                int method,
                int uncompressedSize,
                long crc32,
                long localOffset,
                byte[] name) {
            this.flags = flags;
            this.method = method;
            this.uncompressedSize = uncompressedSize;
            this.crc32 = crc32;
            this.localOffset = localOffset;
            this.name = name;
        }
    }
}
