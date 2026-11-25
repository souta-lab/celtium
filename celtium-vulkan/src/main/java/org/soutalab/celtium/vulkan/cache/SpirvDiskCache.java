package org.soutalab.celtium.vulkan.cache;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

/**
 * SHA-256-keyed persistent disk cache for GLSL-to-SPIR-V compilation outputs (spec §2.6, M15).
 *
 * <p>Eliminates cold-start and mid-frame shader translation stalls on modpacks and Demonica
 * shaderpacks by caching compiled SPIR-V binaries under a SHA-256 digest of
 * {@code (formatVersion, translatorVersion, stage, compilerFlags, glslSource)}.
 *
 * <p>Safety invariants:
 * <ul>
 *   <li>Writes go to a sibling temporary file and are atomically moved into place so interrupted
 *       runs never leave truncated binaries on disk.</li>
 *   <li>Every cached file carries a 16-byte header ({@code "CSPV"} magic, format version,
 *       payload length, CRC32) and must begin with the SPIR-V magic word ({@code 0x07230203});
 *       corrupt or mismatched entries degrade cleanly to a cache miss.</li>
 *   <li>Thread-safe for background shader compilation workers ({@code PipelinePrewarm}).</li>
 * </ul>
 */
public final class SpirvDiskCache {

    /** Container header magic ("CSPV" big-endian). */
    public static final int CONTAINER_MAGIC = 0x43535056;

    /** Current on-disk container format version. */
    public static final int FORMAT_VERSION = 1;

    /** Standard SPIR-V binary magic number (little-endian word 0). */
    public static final int SPIRV_MAGIC = 0x07230203;

    /** Fixed header length in bytes (magic + version + payloadLen + crc32). */
    public static final int HEADER_BYTES = 16;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final Path rootDir;
    private final String translatorVersion;
    private final ConcurrentHashMap<String, byte[]> memoryCache = new ConcurrentHashMap<>();
    private final AtomicLong memoryHits = new AtomicLong();
    private final AtomicLong diskHits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong corruptRejections = new AtomicLong();
    private final AtomicLong writes = new AtomicLong();

    public SpirvDiskCache(Path rootDir, String translatorVersion) {
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir");
        this.translatorVersion = Objects.requireNonNull(translatorVersion, "translatorVersion");
    }

    /**
     * Computes the deterministic 64-character lowercase hex SHA-256 key for a shader translation.
     */
    public String computeKey(String stage, String compilerFlags, String glslSource) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(compilerFlags, "compilerFlags");
        Objects.requireNonNull(glslSource, "glslSource");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            updateLenPrefixed(md, Integer.toString(FORMAT_VERSION));
            updateLenPrefixed(md, translatorVersion);
            updateLenPrefixed(md, stage);
            updateLenPrefixed(md, compilerFlags);
            updateLenPrefixed(md, glslSource);
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable in JVM", e);
        }
    }

    /**
     * Looks up a compiled SPIR-V binary by SHA-256 key. Returns a defensive copy or {@code null}
     * on miss / corruption.
     */
    public byte[] get(String sha256Key) {
        Objects.requireNonNull(sha256Key, "sha256Key");
        byte[] mem = memoryCache.get(sha256Key);
        if (mem != null) {
            memoryHits.incrementAndGet();
            return mem.clone();
        }

        Path path = pathForKey(sha256Key);
        if (!Files.isRegularFile(path)) {
            misses.incrementAndGet();
            return null;
        }

        try {
            byte[] raw = Files.readAllBytes(path);
            byte[] spirv = decodeAndValidate(raw);
            if (spirv == null) {
                corruptRejections.incrementAndGet();
                misses.incrementAndGet();
                return null;
            }
            memoryCache.put(sha256Key, spirv);
            diskHits.incrementAndGet();
            return spirv.clone();
        } catch (IOException e) {
            misses.incrementAndGet();
            return null;
        }
    }

    /**
     * Persists a validated SPIR-V binary under {@code sha256Key} using an atomic temp-file rename.
     *
     * @throws IllegalArgumentException if {@code spirvBytes} is empty, not 4-byte aligned, or lacks
     *                                  the SPIR-V magic word
     */
    public void put(String sha256Key, byte[] spirvBytes) throws IOException {
        Objects.requireNonNull(sha256Key, "sha256Key");
        Objects.requireNonNull(spirvBytes, "spirvBytes");
        if (!isValidSpirvPayload(spirvBytes)) {
            throw new IllegalArgumentException(
                    "Invalid SPIR-V payload (length=" + spirvBytes.length + ")");
        }

        byte[] copy = spirvBytes.clone();
        byte[] encoded = encodeContainer(copy);
        Path target = pathForKey(sha256Key);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tmp = Files.createTempFile(parent, sha256Key.substring(0, Math.min(8, sha256Key.length())), ".tmp");
        try {
            Files.write(tmp, encoded);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }

        memoryCache.put(sha256Key, copy);
        writes.incrementAndGet();
    }

    public Path pathForKey(String sha256Key) {
        if (sha256Key.length() < 4) {
            throw new IllegalArgumentException("sha256Key too short: " + sha256Key);
        }
        String shard = sha256Key.substring(0, 2);
        return rootDir.resolve(shard).resolve(sha256Key + ".spv");
    }

    public static boolean isValidSpirvPayload(byte[] spirvBytes) {
        if (spirvBytes == null || spirvBytes.length < 20 || (spirvBytes.length & 3) != 0) {
            return false;
        }
        int magic = (spirvBytes[0] & 0xFF)
                | ((spirvBytes[1] & 0xFF) << 8)
                | ((spirvBytes[2] & 0xFF) << 16)
                | ((spirvBytes[3] & 0xFF) << 24);
        return magic == SPIRV_MAGIC;
    }

    private static byte[] encodeContainer(byte[] spirv) {
        CRC32 crc = new CRC32();
        crc.update(spirv);
        ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES + spirv.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(CONTAINER_MAGIC);
        buf.putInt(FORMAT_VERSION);
        buf.putInt(spirv.length);
        buf.putInt((int) crc.getValue());
        buf.put(spirv);
        return buf.array();
    }

    private static byte[] decodeAndValidate(byte[] raw) {
        if (raw.length < HEADER_BYTES + 20) {
            return null;
        }
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int magic = buf.getInt();
        int version = buf.getInt();
        int payloadLen = buf.getInt();
        int expectedCrc = buf.getInt();
        if (magic != CONTAINER_MAGIC || version != FORMAT_VERSION) {
            return null;
        }
        if (payloadLen < 20 || (payloadLen & 3) != 0 || raw.length != HEADER_BYTES + payloadLen) {
            return null;
        }
        byte[] spirv = new byte[payloadLen];
        buf.get(spirv);
        if (!isValidSpirvPayload(spirv)) {
            return null;
        }
        CRC32 crc = new CRC32();
        crc.update(spirv);
        if ((int) crc.getValue() != expectedCrc) {
            return null;
        }
        return spirv;
    }

    private static void updateLenPrefixed(MessageDigest md, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int len = bytes.length;
        md.update((byte) (len >>> 24));
        md.update((byte) (len >>> 16));
        md.update((byte) (len >>> 8));
        md.update((byte) len);
        md.update(bytes);
    }

    private static String toHex(byte[] digest) {
        char[] out = new char[digest.length * 2];
        for (int i = 0; i < digest.length; i++) {
            int v = digest[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    public long memoryHits() {
        return memoryHits.get();
    }

    public long diskHits() {
        return diskHits.get();
    }

    public long misses() {
        return misses.get();
    }

    public long corruptRejections() {
        return corruptRejections.get();
    }

    public long writes() {
        return writes.get();
    }
}
