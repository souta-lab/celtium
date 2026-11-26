package org.soutalab.celtium.vulkan.gl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Disk cache for compiled SPIR-V, keyed by the SHA-256 of the lowered GLSL source (A-to-B plan,
 * Stage 5 item 4: the {@link ModGlslTranslator} output is cached on disk under a SHA-256 key).
 * {@link ModShaderCompiler#compile} routes through {@link #loadOrCompile}: a hit returns the
 * stored words without invoking shaderc at all, a miss compiles and then stores, and a corrupt
 * entry is evicted and treated as a miss, so the worst case of any cache failure is the pre-cache
 * behaviour - one compile.
 *
 * <p>Entry format (file {@code <sha256-hex>.spvbin} in the cache directory):
 * magic {@code CSPV} (4 bytes), format version (4 bytes, big-endian), payload length (4 bytes),
 * the SPIR-V bytes, and the SHA-256 of the payload (32 bytes). The trailing checksum is what
 * makes corruption detectable: a truncated or torn write never verifies, so it can never hand
 * the driver a half-written shader module. Writes go to a sibling temporary file and are moved
 * into place atomically, the same shape as the persistent {@code VkPipelineCache} beside it.
 *
 * <p>The key material covers the cache format version, the compiler contract this class is
 * written against (shaderc, Vulkan 1.2 target environment, performance optimisation - the
 * settings {@code ModShaderCompiler} compiles with) and the stage kind, so a vertex and a
 * fragment stage of the same source never collide. Changing any of those settings must bump
 * {@link #KEY_VERSION}; that constant is the invalidation lever.
 *
 * <p>The directory follows the pipeline cache's convention: {@code celtium/spirv-cache} under
 * the working directory, overridable with {@code -Dceltium.spirvCache.dir=<path>}, and the whole
 * cache can be switched off with {@code -Dceltium.spirvCache.enabled=false}. Every cache
 * operation is failure-swallowing and counted: a cache that can break compilation would be worse
 * than no cache.
 */
public final class SpirvDiskCache {

    /** Bumped whenever the key material or the entry format changes; old entries become orphans. */
    private static final String KEY_VERSION = "celtium-spirv-cache-v1";
    /** The compiler contract baked into the key: {@code ModShaderCompiler}'s shaderc settings. */
    private static final String COMPILER_CONTRACT = "shaderc|vulkan-1.2|performance";
    private static final int ENTRY_FORMAT_VERSION = 1;
    private static final byte[] MAGIC = {'C', 'S', 'P', 'V'};
    private static final int HEADER_BYTES = 4 + 4 + 4;
    private static final int CHECKSUM_BYTES = 32;

    private static final AtomicLong HITS = new AtomicLong();
    private static final AtomicLong MISSES = new AtomicLong();
    private static final AtomicLong STORES = new AtomicLong();
    private static final AtomicLong CORRUPT_EVICTIONS = new AtomicLong();
    private static final AtomicLong IO_FAILURES = new AtomicLong();

    private SpirvDiskCache() {
    }

    static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty("celtium.spirvCache.enabled", "true"));
    }

    /** The cache directory, resolved per call so the property can redirect it (tests do). */
    static Path directory() {
        String configured = System.getProperty("celtium.spirvCache.dir");
        return configured != null ? Paths.get(configured)
                : Paths.get(System.getProperty("user.dir"), "celtium", "spirv-cache");
    }

    /** {@return the hex SHA-256 entry key for one stage of one source}. */
    static String keyFor(String source, boolean vertex) {
        MessageDigest digest = sha256();
        digest.update(KEY_VERSION.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(COMPILER_CONTRACT.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(vertex ? (byte) 'V' : (byte) 'F');
        digest.update(source.getBytes(StandardCharsets.UTF_8));
        return toHex(digest.digest());
    }

    /**
     * {@return the cached SPIR-V for one stage, or null on a miss}. A present-but-invalid entry
     * (bad magic, wrong version, length mismatch, checksum mismatch, unreadable) is evicted and
     * reported as a miss, never propagated.
     */
    public static byte[] load(String source, boolean vertex) {
        if (!enabled()) {
            return null;
        }
        Path file = directory().resolve(keyFor(source, vertex) + ".spvbin");
        byte[] raw;
        try {
            if (!Files.isRegularFile(file)) {
                MISSES.incrementAndGet();
                return null;
            }
            raw = Files.readAllBytes(file);
        } catch (IOException | RuntimeException failure) {
            IO_FAILURES.incrementAndGet();
            MISSES.incrementAndGet();
            return null;
        }
        byte[] payload = decode(raw);
        if (payload == null) {
            CORRUPT_EVICTIONS.incrementAndGet();
            MISSES.incrementAndGet();
            try {
                Files.deleteIfExists(file);
            } catch (IOException | RuntimeException ignored) {
                IO_FAILURES.incrementAndGet();
            }
            return null;
        }
        HITS.incrementAndGet();
        return payload;
    }

    /** Stores one stage's SPIR-V atomically. Failures are counted and swallowed. */
    public static void store(String source, boolean vertex, byte[] spirv) {
        if (!enabled() || spirv == null || spirv.length == 0) {
            return;
        }
        Path dir = directory();
        Path file = dir.resolve(keyFor(source, vertex) + ".spvbin");
        try {
            Files.createDirectories(dir);
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temporary, encode(spirv));
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            STORES.incrementAndGet();
        } catch (IOException | RuntimeException failure) {
            IO_FAILURES.incrementAndGet();
        }
    }

    /**
     * The compile path's single entry: returns the cached words when present, otherwise runs
     * {@code compiler} (the real shaderc compile) and stores its result before returning it.
     * A corrupt entry falls back to compiling and is overwritten by the fresh store.
     */
    public static byte[] loadOrCompile(String source, boolean vertex, Supplier<byte[]> compiler) {
        byte[] cached = load(source, vertex);
        if (cached != null) {
            return cached;
        }
        byte[] compiled = compiler.get();
        if (compiled != null) {
            store(source, vertex, compiled);
        }
        return compiled;
    }

    /** {@return the cache census so far}, in the census style of the stub's summaries. */
    public static String summary() {
        return "spirvCacheHits=" + HITS.get()
                + " spirvCacheMisses=" + MISSES.get()
                + " spirvCacheStores=" + STORES.get()
                + " spirvCacheCorruptEvictions=" + CORRUPT_EVICTIONS.get()
                + " spirvCacheIoFailures=" + IO_FAILURES.get();
    }

    private static byte[] encode(byte[] spirv) {
        byte[] out = new byte[HEADER_BYTES + spirv.length + CHECKSUM_BYTES];
        System.arraycopy(MAGIC, 0, out, 0, 4);
        putInt(out, 4, ENTRY_FORMAT_VERSION);
        putInt(out, 8, spirv.length);
        System.arraycopy(spirv, 0, out, HEADER_BYTES, spirv.length);
        byte[] checksum = sha256().digest(spirv);
        System.arraycopy(checksum, 0, out, HEADER_BYTES + spirv.length, CHECKSUM_BYTES);
        return out;
    }

    /** {@return the payload of a well-formed entry, or null when anything about it is off}. */
    private static byte[] decode(byte[] raw) {
        if (raw == null || raw.length < HEADER_BYTES + CHECKSUM_BYTES) {
            return null;
        }
        for (int i = 0; i < 4; i++) {
            if (raw[i] != MAGIC[i]) {
                return null;
            }
        }
        if (getInt(raw, 4) != ENTRY_FORMAT_VERSION) {
            return null;
        }
        int length = getInt(raw, 8);
        if (length <= 0 || raw.length != HEADER_BYTES + length + CHECKSUM_BYTES) {
            return null;
        }
        byte[] payload = new byte[length];
        System.arraycopy(raw, HEADER_BYTES, payload, 0, length);
        byte[] expected = sha256().digest(payload);
        int difference = 0;
        for (int i = 0; i < CHECKSUM_BYTES; i++) {
            difference |= expected[i] ^ raw[HEADER_BYTES + length + i];
        }
        return difference == 0 ? payload : null;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 is a required algorithm of every Java platform.
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16));
            out.append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }

    private static void putInt(byte[] out, int offset, int value) {
        out[offset] = (byte) (value >>> 24);
        out[offset + 1] = (byte) (value >>> 16);
        out[offset + 2] = (byte) (value >>> 8);
        out[offset + 3] = (byte) value;
    }

    private static int getInt(byte[] raw, int offset) {
        return ((raw[offset] & 0xFF) << 24) | ((raw[offset + 1] & 0xFF) << 16)
                | ((raw[offset + 2] & 0xFF) << 8) | (raw[offset + 3] & 0xFF);
    }
}
