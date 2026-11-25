package org.soutalab.celtium.vulkan.gl;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memGetLong;
import static org.lwjgl.system.MemoryUtil.memPutInt;

/**
 * The stub's texture-pixel captures, extracted from {@code GlsmFrontend} (giant-split seam 7, QUEUE 688).
 * Behaviour is unchanged: this class owns the filed capture records ({@link TexCapture}), the patch
 * backlog's bookkeeping, the RGBA8 conversion every upload passes through, the capture census and the
 * read-side answers the Vulkan mirrors are built from, while {@code GlsmFrontend} keeps the public doors
 * (the {@code textureUpload*} statics and kin) and the dispatch that calls in.
 * <p>
 * The three stores are not owned here: they are the stub's fields, handed in at construction, because
 * {@code forgetTexture} and the copy-image path read them beside this class's own state, and the cleanup
 * test reads {@code texCaptures} by name (the shape split-2 set for {@link BufferStorageRecord}). What
 * this class owns is everything filed into them: the write serial, the census counters, the patch-byte
 * total and the per-key patch census.
 * <p>
 * The producers and answers read the stub's live extent and binding records, so the ones that need them
 * take the stub itself rather than a copy of its values; the fields and helper methods they read are
 * package-private on the stub for this class (the same widening the state-query extraction applied).
 */
final class GlTextureCaptures {

    /** The stub's captured uploads, keyed like its extent record. Owned by the stub; operated on here. */
    private final it.unimi.dsi.fastutil.longs.Long2ObjectMap<TexCapture> texCaptures;
    /** The stub's per-level patch backlog. Owned by the stub; operated on here. */
    private final it.unimi.dsi.fastutil.longs.Long2ObjectMap<java.util.List<TexCapture>> texPatchBacklog;
    /** The stub's supplied-serial record. Owned by the stub; operated on here. */
    private final it.unimi.dsi.fastutil.longs.Long2LongMap suppliedSerials;

    GlTextureCaptures(it.unimi.dsi.fastutil.longs.Long2ObjectMap<TexCapture> texCaptures,
            it.unimi.dsi.fastutil.longs.Long2ObjectMap<java.util.List<TexCapture>> texPatchBacklog,
            it.unimi.dsi.fastutil.longs.Long2LongMap suppliedSerials) {
        this.texCaptures = texCaptures;
        this.texPatchBacklog = texPatchBacklog;
        this.suppliedSerials = suppliedSerials;
    }

    /** The serial every filed level write takes its number from; see {@link TexCapture#serial}. */
    private final java.util.concurrent.atomic.AtomicLong texUploadSerial =
            new java.util.concurrent.atomic.AtomicLong();

    /** The capture census {@link #textureCaptureSummary()} reports: full images, patches and refused uploads. */
    private final java.util.concurrent.atomic.AtomicInteger fullCaptures =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger subCaptures =
            new java.util.concurrent.atomic.AtomicInteger();
    /** Levels that were allocated with no data - a zero-filled surface waiting for the patches that fill it. */
    private final java.util.concurrent.atomic.AtomicInteger undefinedExtents =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger unconvertedUploads =
            new java.util.concurrent.atomic.AtomicInteger();
    /**
     * Patches whose converted bytes were row-for-row identical to the captured surface. Each is an upload the
     * caller asked for that changed nothing - the lightmap's per-frame DynamicTexture update is the regular one -
     * so skipping the serial bump keeps the descriptor the GPU side already holds instead of forcing a re-supply
     * of identical pixels.
     */
    private final java.util.concurrent.atomic.AtomicInteger identicalPatches =
            new java.util.concurrent.atomic.AtomicInteger();
    /**
     * Whole images ({@code glTexImage2D} with data) whose converted bytes were identical to the record the level
     * already held. The whole-image half of the rule {@link #identicalPatches} applies to patches: GL's content did
     * not change, so the level's serial must not either, and a mirror that already holds that serial skips a
     * re-supply of bytes it has.
     * <p>
     * The case this exists for is a mod that re-uploads a texture it did not change - a render target re-declared
     * every frame, a GUI sheet re-sent on a resize that did not resize. Before this, every such call published a new
     * record and took a new serial, so the mirror re-read and re-copied the whole image for nothing. Measured on leg
     * {@code r33b-empty} (`celtium-world-accept`, 188 mods): {@code rawWriteRefreshes=52853} over 3,486 world frames,
     * 15.2 uploads a frame at ~9 us each, and the supply part is 60% of the accept path.
     */
    private final java.util.concurrent.atomic.AtomicInteger identicalFullImages =
            new java.util.concurrent.atomic.AtomicInteger();

    private static final int GL_UNSIGNED_INT_8_8_8_8_REV = 0x8367;
    private static final int GL_UNSIGNED_INT_8_8_8_8 = 0x8035;

    /**
     * The largest level worth mirroring: 4096x4096 RGBA8, the biggest atlas the game builds. A larger upload is
     * counted as unconverted rather than allocated - one level of a speculative 16384x16384 probe would be a gigabyte
     * of direct memory for pixels the game itself never had to find room for.
     */
    private static final long MAX_CAPTURE_BYTES = 64L * 1024L * 1024L;
    /** Per-key patch-write census: which (name,level) the captured sub-image writes actually land on. */
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap patchKeys =
            new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    /** Which texSubImage branch each patch took, for the capture-layout question. */
    private int patchBranchBacklog;
    private int patchBranchSurface;
    private int patchBranchLone;
    private int patchBranchPaste;

    // Package-private: the stub's forgetTexture settles the total when it drops a backlog.
    long texPatchBytes;
    private static final long MAX_PATCH_BACKLOG_BYTES = 192L * 1024L * 1024L;

    /** One captured level: RGBA8, {@code width * height} pixels, top row first, in stub-owned direct storage. */
    static final class TexCapture {
        final int width;
        final int height;
        final ByteBuffer data;
        /**
         * Whether the game itself wrote a pixel into this level - true for a {@code glTexImage2D} that carried data and
         * for a {@code glTexSubImage2D} patch, false for the zero-filled surface {@link #undefinedLevel} lays down for
         * an allocation with a NULL data pointer.
         * <p>
         * The surface alone cannot answer this: an undefined level holds zeros whether the game filled it or not, and a
         * mirror built from those zeros claims whole content that no upload ever put there. Consumers ask through
         * {@link GlsmFrontend#textureLevelHasGamePixels}. Mutable because a patch is copied into an already filed surface, and
         * volatile because the game thread writes it while a reader may be polling the record.
         */
        volatile boolean gamePixels;
        /**
         * The write number the level reached when this record's bytes became true.
         * <p>
         * Every write the stub files - a full image, an undefined level, a patch - takes the next number from
         * {@link #texUploadSerial}; a record is assigned its number at construction and an in-place patch
         * bumps the number on the record it rewrote. A consumer that holds the number it last mirrored can answer "did
         * the game touch this level since" in O(1) - the alternative the mirrors used before was an FNV pass over
         * every byte of the level on every ask, which is what the per-batch light-map upload measured against.
         * Volatile for the same reason {@link #gamePixels} is.
         */
        volatile long serial;
        /**
         * Whether a {@code glTexSubImage2D} patch has ever been written into this record (or the record was itself
         * filed by one). The settled-capture release ({@link GlsmFrontend#releaseSettledTextureCaptures}) refuses
         * a patched record outright: a patch that arrives after a release would be pasted into a freshly laid
         * undefined surface and silently zero the rest of the level, so "was ever patched" is the stub's proof
         * that a level is not a write-once image. The lightmap and the block atlas - the two levels patched every
         * tick - are excluded by this flag from their first patch on. Set on the game thread, read by the release
         * on the same thread; volatile because a reader may poll the record from elsewhere.
         */
        volatile boolean patched;
        /**
         * The texel offset this record covers inside its level, for a capture that is a {@code glTexSubImage2D}
         * patch rather than the whole level. Zero on every record that fills its level from the origin; the patch
         * case exists because a level too large for the capture budget keeps its patch at patch size instead of an
         * extent-sized surface - the mirror path writes it at this position, not at 0,0.
         */
        final int x;
        final int y;

        TexCapture(int width, int height, ByteBuffer data, boolean gamePixels, long serial) {
            this(width, height, data, gamePixels, serial, 0, 0);
        }

        TexCapture(int width, int height, ByteBuffer data, boolean gamePixels, long serial, int x, int y) {
            this.width = width;
            this.height = height;
            this.data = data;
            this.gamePixels = gamePixels;
            this.serial = serial;
            this.x = x;
            this.y = y;
        }
    }

    /**
     * {@code glTexImage1D/2D/3D}: the extent record plus the uploaded pixels, converted to RGBA8 and filed under the
     * bound texture's level. A NULL data pointer is a legal GL call that allocates the level with undefined content -
     * see {@link #undefinedLevel}, because the game fills levels exactly that way.
     */
    void texImagePixels(GlsmFrontend stub, GlsmFrontend.Fn fn, long[] c) {
        this.texImagePixels(stub, fn.name, c);
    }

    void texImagePixels(GlsmFrontend stub, String name, long[] c) {
        this.texImagePixels(stub, name, (int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4], (int) c[5], (int) c[6],
                (int) c[7], c[8]);
    }

    /**
     * The one tex-image producer: the dispatch case and the transformer's route both land here.
     *
     * <p>Routing {@code glTexImage2D}/{@code glTexSubImage2D} matters because the stub's capture is what gives a
     * texture its pixels under GL-less. Measured on the 268-mod E2E pack: the block atlas (GL name 353) never reached
     * the mirror and the registry logged {@code CELTIUM_CAPTURE_MISS glTexture=353 level=0 ... the stub captured no
     * upload for this texture level}, so the atlas stayed incomplete, the terrain pass refused its batches, and the
     * lower half of the frame was drawn by nobody. The census counted the calls that were dropped all along:
     * {@code nglTexSubImage2D=41199} in one cohort run.
     */
    void texImagePixels(GlsmFrontend stub, String name, int target, int level, int internal, int width, int height, int border,
            int format, int type, long pixels) {
        boolean oneD = "glTexImage1D".equals(name);
        boolean threeD = "glTexImage3D".equals(name);
        int extentHeight = oneD ? 1 : height;
        int depth = threeD ? height : 1;
        // The format slot follows the extent, which differs per form: 1D has no height, 3D carries depth. The
        // transformer's route only carries the 2D form, whose slots are the ones this method's parameters name.
        stub.recordExtent(target, level, internal, width, extentHeight);
        int targetName = stub.uploadTargetName(target);
        if (GlsmFrontend.isProxyTarget(target) || targetName == 0) {
            return;
        }
        if (!carriesPixels(pixels, stub.pixelUnpackBuffer)) {
            if (level == 0) {
                GlsmNameLedger.current().addNullAllocated(targetName);
            }
            this.undefinedLevel(stub, targetName, level, width, extentHeight, depth);
            return;
        }
        GlsmNameLedger.current().removeNullAllocated(targetName);
        if (depth > 1) {
            // A 2D mirror cannot hold the remaining slices: the extent is recorded and the upload counted rather than
            // storing the first slice as if it were the whole image.
            this.unconvertedUploads.incrementAndGet();
            return;
        }
        ByteBuffer rgba = this.uploadToRgba8(stub, width, height, format, type, pixels);
        if (rgba == null) {
            this.unconvertedUploads.incrementAndGet();
            return;
        }
        long key = GlsmFrontend.texLevelKey(targetName, level);
        TexCapture previous = this.texCaptures.get(key);
        if (previous != null && previous.gamePixels && previous.x == 0 && previous.y == 0
                && previous.width == width && previous.height == extentHeight
                && previous.data.capacity() == rgba.capacity()
                && sameBytes(previous.data, rgba, rgba.capacity())) {
            // The same whole image, uploaded again. GL's content did not change, so the level's serial must not
            // either: the serial is what makes a mirror re-supply, and a re-supply of identical bytes is pure cost.
            // This is the whole-image half of the rule the patch path already applies (OPT-21); the record is left
            // as it is, so a reader polling the level still sees one whole image.
            this.identicalFullImages.incrementAndGet();
            return;
        }
        // Publishing a new record instead of rewriting the old one, so a reader polling the level sees one whole image.
        this.texCaptures.put(stub.putTexLevelKey(targetName, level),
                new TexCapture(width, extentHeight, rgba, true, this.texUploadSerial.incrementAndGet()));
        this.fullCaptures.incrementAndGet();
    }

    /**
     * {@return whether two native buffers hold the same bytes}, compared a word at a time.
     * <p>
     * LWJGL's {@code MemoryUtil} has no {@code memcmp} in the version this builds against, and the alternatives
     * allocate: {@code Arrays.equals} needs two byte arrays, which for a 512x512 image is a megabyte of garbage per
     * upload. The word loop reads the same memory the conversion just wrote, so it is one extra pass over the image -
     * the same order as the conversion pass the caller already paid - and it is what lets an unchanged whole image
     * keep its serial instead of forcing a mirror to re-copy bytes it already holds.
     */
    private static boolean sameBytes(ByteBuffer a, ByteBuffer b, int length) {
        long left = memAddress(a);
        long right = memAddress(b);
        int i = 0;
        for (; i + 8 <= length; i += 8) {
            if (MemoryUtil.memGetLong(left + i) != MemoryUtil.memGetLong(right + i)) {
                return false;
            }
        }
        for (; i < length; i++) {
            if (MemoryUtil.memGetByte(left + i) != MemoryUtil.memGetByte(right + i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@return whether a texture upload's {@code pixels} argument names data}. With a pixel unpack buffer bound it is an
     * offset into that buffer, and offset 0 is its first byte - not "no data". Xaero's Minimap uploads every map tile
     * that way ({@code glTexSubImage2D(..., 0L)} from a PBO), and reading 0 as NULL dropped all of them: the minimap
     * showed the world through itself, every tile batch refused for want of a complete image.
     */
    static boolean carriesPixels(long pixels, int unpackBuffer) {
        return pixels != 0L || unpackBuffer > 0;
    }

    /**
     * A level allocated with a NULL data pointer: GL reserves the storage and leaves its content undefined.
     * <p>
     * The level still needs a surface here, because the game fills levels in patches: {@code TextureUtil} allocates
     * every mip level with no data and then writes the image sprite by sprite through {@code glTexSubImage2D}. Without
     * one, those sprites were dropped one by one - the first became a capture the size of a single sprite and every
     * later one fell outside it, which left the block atlas mirrored as one 16x16 corner. Zeros are the honest reading
     * of an undefined level, and they are exactly what the extent record already claims, so the two records agree. It
     * carries no game bytes, so it is counted apart from the full images.
     */
    void undefinedLevel(GlsmFrontend stub, int name, int level, int width, int height, int depth) {
        long pixels = (long) width * (long) height;
        if (depth > 1 || width <= 0 || height <= 0 || pixels > MAX_CAPTURE_BYTES / 4L) {
            // No shape to lay a surface out from: a 2D record cannot describe depth, and this level is past the size
            // the stub will mirror at all. The extent stands, the pixels do not.
            this.unconvertedUploads.incrementAndGet();
            return;
        }
        // E8 (item 690, wpBeginTargets): if this level already holds a zeroed null-allocated capture of the
        // exact same dimensions, short-circuit so repeated prepareRenderTargets() declarations do not re-allocate
        // multi-megabyte direct buffers or bump the mirror upload serial on every frame.
        long existingKey = GlsmFrontend.texLevelKey(name, level);
        TexCapture previous = this.texCaptures.get(existingKey);
        if (previous != null && !previous.gamePixels && previous.width == width && previous.height == height) {
            return;
        }
        long bytes = pixels * 4L;
        ByteBuffer zeros = ByteBuffer.allocateDirect((int) bytes);
        // allocateDirect promises storage, not zeroed storage: HotSpot can hand back recycled memory for a small
        // buffer, and a garbage surface is exactly the silently wrong atlas this path exists to prevent.
        MemoryUtil.memSet(memAddress(zeros), 0, bytes);
        this.texCaptures.put(stub.putTexLevelKey(name, level),
                new TexCapture(width, height, zeros, false, this.texUploadSerial.incrementAndGet()));
        this.undefinedExtents.incrementAndGet();
    }

    /**
     * {@code glTexSubImage2D}: the patch is converted on its own and written into the level's captured rows.
     * <p>
     * A level with no capture yet takes the patch as its storage, placed at the origin - the summary counts that as a
     * patch, not as a full image. A patch that does not fit inside the captured image is counted and dropped, the way
     * GL raises GL_INVALID_VALUE and leaves the level alone; a partly written image would be worse than none.
     */
    void texSubImage(GlsmFrontend stub, long[] c) {
        this.texSubImage(stub, (int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4], (int) c[5], (int) c[6], (int) c[7],
                c[8]);
    }

    /** The mean byte value of the bytes an upload is about to read from a pixel unpack buffer, for the PBO log line. */
    private static String celtium$pboMean(ByteBuffer storage, long offset, long bytes) {
        if (storage == null || GlsmFrontend.mapRangeOffset(offset, bytes, storage.capacity()) < 0L) {
            return "n/a";
        }
        long sum = 0L;
        long nonZero = 0L;
        for (long i = 0; i < bytes; i++) {
            int value = storage.get((int) (offset + i)) & 0xFF;
            sum += value;
            if (value != 0) nonZero++;
        }
        return String.format(java.util.Locale.ROOT, "%.1f nonZero=%d/%d", (double) sum / bytes, nonZero, bytes);
    }

    /**
     * The one patch the animation announcement has already converted and applied to the mirror.
     * <p>
     * {@code TextureUtil.func_147955_a} announces a sprite's frame at HEAD and then runs the original body, which
     * issues the {@code glTexSubImage2D} for the same region. The announcement converts those pixels and writes them
     * into the mirror; the stub's capture then converts the identical pixels a second time and pastes them into the
     * captured level, which is the {@code animStub} bracket beside {@code animMirror}. The capture exists so that
     * {@code fillFromCapture} can establish a mirror that does not exist yet, and a mirror the announcement just
     * updated does exist - so for exactly that one call the second conversion buys nothing.
     * <p>
     * One shot, matched on the region and cleared by the first {@code texSubImage} whatever it is: the announcement
     * and the upload it announces are adjacent statements in the same method, so no other upload can intervene, and a
     * token that does not match is dropped rather than left for a later call. A refused announcement never sets one.
     */
    private static int celtium$announcedLevel;
    private static int celtium$announcedX;
    private static int celtium$announcedY;
    private static int celtium$announcedWidth;
    private static int celtium$announcedHeight;
    private static boolean celtium$announcedPending;
    /** Patches whose capture the announcement made unnecessary; the count is the saving, not a loss. */
    private static long celtium$announcedCaptureSkips;

    /** Arms the one-shot skip for the patch an announcement just applied. See the fields above. */
    static void celtium$noteAnnouncedPatch(int level, int x, int y, int width, int height) {
        celtium$announcedLevel = level;
        celtium$announcedX = x;
        celtium$announcedY = y;
        celtium$announcedWidth = width;
        celtium$announcedHeight = height;
        celtium$announcedPending = true;
    }

    /** {@return how many captures the announcement made unnecessary} */
    static long celtium$announcedCaptureSkips() {
        return celtium$announcedCaptureSkips;
    }

    /** The one tex-sub-image producer: the dispatch case and the transformer's route both land here. */
    void texSubImage(GlsmFrontend stub, int target, int level, int xOffset, int yOffset, int width, int height, int format,
            int type, long pixels) {
        if (GlsmFrontend.isProxyTarget(target)) {
            return;
        }
        if (celtium$announcedPending) {
            boolean announced = celtium$announcedLevel == level && celtium$announcedX == xOffset
                    && celtium$announcedY == yOffset && celtium$announcedWidth == width
                    && celtium$announcedHeight == height;
            celtium$announcedPending = false;
            if (announced) {
                celtium$announcedCaptureSkips++;
                return;
            }
        }
        // The stub's half of an upload: the format conversion and the write into the captured level. Inside the tick
        // this is the animated sprite's patch, and it is the second conversion of the same pixels the announcement
        // already converted - `animStub` beside `animMirror` says which of the two owns the cost.
        org.soutalab.celtium.vulkan.FramePassTimings.begin("animStub");
        try {
            this.texSubImageForName(stub, stub.uploadTargetName(target), level, xOffset, yOffset, width, height, format, type, pixels);
        } finally {
            org.soutalab.celtium.vulkan.FramePassTimings.end("animStub");
        }
    }

    /** The DSA spelling's producer: the same patch rules, with the texture named by the caller. */
    void texSubImageForName(GlsmFrontend stub, int name, int level, int xOffset, int yOffset, int width, int height, int format,
            int type, long pixels) {
        int x = xOffset;
        int y = yOffset;
        if (stub.pixelUnpackBuffer > 0 && stub.pboUploadLogs < 8) {
            stub.pboUploadLogs++;
            ByteBuffer storage = GlsmBufferState.current().storage(stub.pixelUnpackBuffer);
            System.out.println("[Celtium] CELTIUM_PBO_UPLOAD texture=" + name + " level=" + level + " at=" + x + ","
                    + y + " size=" + width + "x" + height + " format=0x" + Integer.toHexString(format) + " type=0x"
                    + Integer.toHexString(type) + " buffer=" + stub.pixelUnpackBuffer + " offset=" + pixels
                    + " storage=" + (storage == null ? "none" : storage.capacity() + "B")
                    + " sourceMean=" + celtium$pboMean(storage, pixels, (long) width * height * 4L));
        }
        if (name == 0 || !carriesPixels(pixels, stub.pixelUnpackBuffer) || width <= 0 || height <= 0) {
            return;
        }
        ByteBuffer patch = this.uploadToRgba8(stub, width, height, format, type, pixels);
        if (patch == null) {
            this.unconvertedUploads.incrementAndGet();
            return;
        }
        this.fileTexturePatch(stub, name, level, x, y, width, height, patch);
    }

    /**
     * Files one converted RGBA8 patch into a level's captured rows.
     * <p>
     * The shared half of {@link #texSubImageForName} and {@link GlsmFrontend#copyImageSubData}: a patch that arrives as
     * client memory and one that arrives as a copy from another captured level land in the same place, so the
     * backlog, surface and settled-release rules stay the level's own rather than diverging per spelling.
     * {@code x}/{@code y} are the patch's position in the level, {@code patch} its RGBA8 rows, top row first.
     */
    void fileTexturePatch(GlsmFrontend stub, int name, int level, int x, int y, int width, int height, ByteBuffer patch) {
        long key = GlsmFrontend.texLevelKey(name, level);
        this.patchKeys.addTo(key, 1);
        TexCapture existing = this.texCaptures.get(key);
        if (existing == null) {
            // A patch into a level whose size is already recorded: filed at patch size it would pose as the whole
            // level - measured on the E2E pack: the 8192x8192 atlas (GL 353) got a 1125x1125 capture from its first
            // animated sprite, the mirror was created at that size, and every later sprite was refused "outside the
            // mirror". Lay an extent-sized undefined surface (same rule as undefinedLevel) and let the normal patch
            // path write inside it; over the capture budget the patch is dropped and the mirror comes from the
            // announcement path, exactly as before the route existed.
            int[] extent = stub.texLevels.get(key);
            if (extent != null && extent[0] > 0 && extent[1] > 0
                    && (x != 0 || y != 0 || width != extent[0] || height != extent[1])) {
                long pixels2 = (long) extent[0] * extent[1];
                if (pixels2 > MAX_CAPTURE_BYTES / 4L) {
                    this.patchBranchBacklog++;
                    // Over the capture budget the patch goes to the per-level backlog instead of posing as the
                    // whole level - the case that built the E2E atlas's mirror at the first sprite's 1125x1125 and
                    // refused every later patch "outside the mirror". The mirror path replays the backlog as
                    // region writes; the byte bound keeps a never-mirrored texture from growing without limit.
                    if (this.texPatchBytes + patch.remaining() > MAX_PATCH_BACKLOG_BYTES) {
                        this.unconvertedUploads.incrementAndGet();
                        return;
                    }
                    java.util.List<TexCapture> pending = this.texPatchBacklog.get(key);
                    if (pending == null) {
                        pending = new java.util.ArrayList<TexCapture>();
                        this.texPatchBacklog.put(stub.putTexLevelKey(name, level), pending);
                    }
                    pending.add(new TexCapture(width, height, patch, true,
                            this.texUploadSerial.incrementAndGet(), x, y));
                    this.texPatchBytes += patch.remaining();
                    this.subCaptures.incrementAndGet();
                    return;
                }
                ByteBuffer surface = ByteBuffer.allocateDirect((int) (pixels2 * 4L));
                MemoryUtil.memSet(memAddress(surface), 0, pixels2 * 4L);
                existing = new TexCapture(extent[0], extent[1], surface, false,
                        this.texUploadSerial.incrementAndGet());
                this.texCaptures.put(stub.putTexLevelKey(name, level), existing);
                this.undefinedExtents.incrementAndGet();
                this.patchBranchSurface++;
            }
        }
        if (existing == null) {
            // Over the capture budget, or no extent recorded at all: keep the patch as a patch-sized capture with
            // its offset, so the mirror path can write it into the extent-sized image as a region instead of
            // treating the patch as the whole level. The record was born from a patch, so it is marked patched:
            // it is a fragment of its level, never a settled full image.
            TexCapture lone = new TexCapture(width, height, patch, true,
                    this.texUploadSerial.incrementAndGet(), x, y);
            lone.patched = true;
            this.texCaptures.put(stub.putTexLevelKey(name, level), lone);
            this.subCaptures.incrementAndGet();
            this.patchBranchLone++;
            return;
        }
        // A capture may itself be a patch at an offset (the over-budget path above files one at its own x,y), so
        // the position this patch writes inside the record is the level position minus the record's origin.
        int dstX = x - existing.x;
        int dstY = y - existing.y;
        this.patchBranchPaste++;
        // The level takes patches, so its record is never a settled-release candidate - even when this patch
        // turns out identical and writes nothing. Marked before the refusal below for the same reason: the
        // game's intent to patch is the fact the release rule reads, not the write's success.
        existing.patched = true;
        if (dstX < 0 || dstY < 0 || dstX + width > existing.width || dstY + height > existing.height) {
            this.unconvertedUploads.incrementAndGet();
            return;
        }
        int destinationStride = existing.width * 4;
        int sourceStride = width * 4;
        // OPT-21: an upload that does not change the pixels must not change the serial either - the serial is
        // what makes the GPU side re-supply, and a re-supply of identical bytes is pure cost. The row compare
        // runs on the same absolute positions the copy below uses, so "identical" is exactly what GL itself
        // would conclude from the pair (existing surface, this patch). The gamePixels guard keeps an
        // all-zero first patch on an undefined surface from being "identical" - GL did write pixels there,
        // and the flag/serial are what downstream readers use to tell the surface is defined.
        boolean identical = existing.gamePixels;
        if (identical) {
            // Eight bytes a call, on the same absolute positions the copy below uses. The per-byte loop this replaces
            // was 1,024 checked ByteBuffer.get pairs for a 16x16 sprite, and the compare is most of what `animStub`
            // measured once the conversion was skipped: a row is a whole number of texels, so the long chunks never
            // straddle a row and the tail is at most three bytes.
            long existingAddress = memAddress(existing.data);
            long patchAddress = memAddress(patch);
            for (int row = 0; row < height; row++) {
                long dst = existingAddress + (long) (dstY + row) * destinationStride + (long) dstX * 4L;
                long src = patchAddress + (long) row * sourceStride;
                int i = 0;
                for (; i + 8 <= sourceStride; i += 8) {
                    if (memGetLong(dst + i) != memGetLong(src + i)) {
                        identical = false;
                        break;
                    }
                }
                if (!identical) {
                    break;
                }
                for (; i < sourceStride; i++) {
                    if (MemoryUtil.memGetByte(dst + i) != MemoryUtil.memGetByte(src + i)) {
                        identical = false;
                        break;
                    }
                }
                if (!identical) {
                    break;
                }
            }
        }
        if (identical) {
            this.identicalPatches.incrementAndGet();
            return;
        }
        for (int row = 0; row < height; row++) {
            // Absolute and whole-row, so the record's own position never moves under a concurrent reader.
            existing.data.put((dstY + row) * destinationStride + dstX * 4, patch, row * sourceStride,
                    sourceStride);
        }
        // The surface now holds game bytes even when it started as an undefined allocation: the level is what GL has
        // (undefined elsewhere, this patch here), so a mirror may be built from it. Set after the copy, so a reader
        // that sees the flag also sees the rows. The serial bumps at the same point for the same reason: a consumer
        // that sees the new number is guaranteed the rows it belongs to are visible.
        existing.serial = this.texUploadSerial.incrementAndGet();
        existing.gamePixels = true;
        this.subCaptures.incrementAndGet();
    }

    /**
     * {@code glCompressedTexImage2D}: the extent is recorded like any other upload, but the payload is DXT/S3TC or
     * similar and decoding it is not this stub's job - the upload is counted as unconverted, which leaves whatever the
     * level held before it.
     */
    void compressedTexImage(GlsmFrontend stub, long[] c) {
        int target = (int) c[0];
        stub.recordExtent(target, (int) c[1], (int) c[2], (int) c[3], (int) c[4]);
        if (!GlsmFrontend.isProxyTarget(target) && c[7] != 0L) {
            this.unconvertedUploads.incrementAndGet();
        }
    }

    /**
     * Converts one upload's pixels to RGBA8, top row first, or {@code null} when the format/type pair or the recorded
     * unpack state is not one this understands.
     * <p>
     * A {@code null} is never turned into a guess: the caller counts it and keeps the level's previous content, because
     * an upload read with the wrong layout would be a silently wrong atlas rather than a missing one. Understood are
     * {@code GL_RGBA} and {@code GL_BGRA} with {@code GL_UNSIGNED_BYTE}, either of those with
     * {@code GL_UNSIGNED_INT_8_8_8_8_REV} - the packed {@code 0xAARRGGBB} form the atlas uploads use - or with
     * {@code GL_UNSIGNED_INT_8_8_8_8}, and
     * {@code GL_RGB} with {@code GL_UNSIGNED_BYTE}. Rows are read where GL would read them, padded to the recorded
     * unpack alignment; a row length or a pixel/row skip that is not the default is refused rather than misread.
     */
    ByteBuffer uploadToRgba8(GlsmFrontend stub, int width, int height, int format, int type, long pixels) {
        if (stub.pixelUnpackBuffer > 0) {
            // With an unpack buffer bound, pixels is an offset into it. The buffer's storage is this stub's own block,
            // so the offset is resolved to an address inside it - bounds-checked against the buffer's logical size.
            ByteBuffer storage = GlsmBufferState.current().storage(stub.pixelUnpackBuffer);
            int logicalSize = storage == null ? 0
                    : stub.logicalSizeOf(stub.pixelUnpackBuffer, storage.capacity());
            if (storage != null && width > 0 && height > 0
                    && (long) width * (long) height <= logicalSize / 4L
                    && GlsmFrontend.mapRangeOffset(pixels, (long) width * (long) height * 4L, logicalSize) >= 0L) {
                pixels = memAddress(storage) + pixels;
                int bound = stub.pixelUnpackBuffer;
                stub.pixelUnpackBuffer = 0;
                try {
                    return this.uploadToRgba8(stub, width, height, format, type, pixels);
                } finally {
                    stub.pixelUnpackBuffer = bound;
                }
            }
        }
        String pboRefusal = GlsmFrontend.pixelBufferRefusal(stub.pixelUnpackBuffer, pixels, "texture upload");
        if (pboRefusal != null) {
            if (stub.pixelBufferRefusals < 8) {
                stub.pixelBufferRefusals++;
                System.out.println("[Celtium] CELTIUM_PBO_REFUSED " + pboRefusal);
            }
            return null;
        }
        if (pixels == 0L || width <= 0 || height <= 0) {
            return null;
        }
        if ((GlsmBufferState.current().unpackRowLength() != 0 && GlsmBufferState.current().unpackRowLength() != width)
                || GlsmBufferState.current().unpackSkipPixels() != 0 || GlsmBufferState.current().unpackSkipRows() != 0) {
            return null;
        }
        long bytes = (long) width * (long) height * 4L;
        if (bytes > MAX_CAPTURE_BYTES) {
            return null;
        }
        boolean bgra = format == GlsmFrontend.GL_BGRA;
        if (type == GlsmFrontend.GL_UNSIGNED_BYTE && (format == GlsmFrontend.GL_RGBA || bgra)) {
            int rowBytes = width * 4;
            int stride = aligned(rowBytes, GlsmBufferState.current().unpackAlignment());
            ByteBuffer source = MemoryUtil.memByteBuffer(pixels, (int) ((long) (height - 1) * stride + rowBytes));
            ByteBuffer out = ByteBuffer.allocateDirect((int) bytes);
            long outAddress = memAddress(out);
            for (int row = 0; row < height; row++) {
                int base = row * stride;
                if (!bgra) {
                    // Absolute on both sides: the row's source offset and the record's offset move together, and no
                    // buffer position has to be legal at any intermediate point.
                    out.put(row * rowBytes, source, base, rowBytes);
                } else {
                    // One word per texel: four checked get/put pairs per pixel were the top of the client thread while
                    // a world was entered (JFR leg entry-jfr2: Buffer.nextPutIndex under uploadToRgba8).
                    long from = pixels + base;
                    long to = outAddress + (long) row * rowBytes;
                    for (int i = 0; i < rowBytes; i += 4) {
                        int bgraWord = MemoryUtil.memGetInt(from + i);
                        MemoryUtil.memPutInt(to + i, swapRedBlue(bgraWord));
                    }
                }
            }
            out.clear();
            return out;
        }
        if (type == GlsmFrontend.GL_UNSIGNED_BYTE && format == GlsmFrontend.GL_RGB) {
            // Three bytes per pixel, expanded with an opaque alpha; the source rows carry GL's alignment padding.
            int rowBytes = width * 3;
            int stride = aligned(rowBytes, GlsmBufferState.current().unpackAlignment());
            ByteBuffer source = MemoryUtil.memByteBuffer(pixels, (int) ((long) (height - 1) * stride + rowBytes));
            ByteBuffer out = ByteBuffer.allocateDirect((int) bytes);
            for (int row = 0; row < height; row++) {
                int base = row * stride;
                for (int x = 0; x < width; x++) {
                    int pixel = base + x * 3;
                    out.put(source.get(pixel)).put(source.get(pixel + 1))
                            .put(source.get(pixel + 2)).put((byte) 0xFF);
                }
            }
            out.clear();
            return out;
        }
        boolean reversed = type == GL_UNSIGNED_INT_8_8_8_8_REV;
        if ((reversed || type == GL_UNSIGNED_INT_8_8_8_8) && (format == GlsmFrontend.GL_RGBA || bgra)) {
            // The packed forms read the 32-bit word in host order. _REV puts the first component in the least
            // significant bits - for GL_BGRA that is the game's own 0xAARRGGBB int - and the plain form puts it in the
            // most significant bits (Xaero's Minimap uploads its tiles as GL_BGRA/GL_UNSIGNED_INT_8_8_8_8).
            int stride = aligned(width * 4, GlsmBufferState.current().unpackAlignment());
            ByteBuffer source = MemoryUtil.memByteBuffer(pixels, (height - 1) * stride + width * 4)
                    .order(java.nio.ByteOrder.nativeOrder());
            ByteBuffer out = ByteBuffer.allocateDirect((int) bytes);
            long outAddress = memAddress(out);
            boolean nativeLittle = java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN;
            for (int row = 0; row < height; row++) {
                long from = pixels + (long) row * stride;
                long to = outAddress + (long) row * width * 4L;
                for (int x = 0; x < width; x++) {
                    int packed = MemoryUtil.memGetInt(from + x * 4L);
                    if (!reversed) {
                        packed = Integer.reverseBytes(packed);
                    }
                    // packed holds the first component in its low byte; the record is R,G,B,A in memory order, which
                    // on a little-endian host is the word with R in the low byte.
                    int rgba = bgra ? swapRedBlue(packed) : packed;
                    MemoryUtil.memPutInt(to + x * 4L, nativeLittle ? rgba : Integer.reverseBytes(rgba));
                }
            }
            out.clear();
            return out;
        }
        return null;
    }

    /** Swaps the low and third bytes of a texel word: B,G,R,A to R,G,B,A in memory order on a little-endian host. */
    private static int swapRedBlue(int word) {
        return ((word >>> 16) & 0xFF) | (word & 0xFF00FF00) | ((word & 0xFF) << 16);
    }

    /** {@code bytes} rounded up to a GL unpack alignment, which is always a power of two: 1, 2, 4 or 8. */
    static int aligned(int bytes, int alignment) {
        return (bytes + alignment - 1) & ~(alignment - 1);
    }

    /**
     * The RGBA8 pixels the game uploaded for a texture level, top row first, or {@code null} when nothing was captured
     * for it.
     * <p>
     * The upload is the source of truth for the Vulkan mirrors: GL-less mode has no driver to read back from, so the
     * bytes are the ones {@code glTexImage2D} and friends handed the stub. {@code outWidthHeight} receives the
     * captured extent; the returned buffer is a read-only duplicate positioned at 0, so a caller can neither move nor
     * modify the record. Weakly consistent and safe from any thread - a concurrent upload may land mid-call, but an
     * unknown texture name is just a {@code null} answer, and a compressed or unconverted upload is a {@code null}
     * answer too (the census says which).
     */
    java.nio.ByteBuffer textureUploadRgba8(int textureName, int level, int[] outWidthHeight) {
        TexCapture capture = this.texCaptures.get(GlsmFrontend.texLevelKey(textureName, level));
        if (capture == null) {
            if (outWidthHeight != null && outWidthHeight.length >= 2) {
                // The extent goes with the pixels: a caller that reuses one array across levels must not read the
                // previous texture's size as this level's.
                outWidthHeight[0] = 0;
                outWidthHeight[1] = 0;
            }
            return null;
        }
        if (outWidthHeight != null && outWidthHeight.length >= 2) {
            outWidthHeight[0] = capture.width;
            outWidthHeight[1] = capture.height;
        }
        // The hand-out is the mirror taking this level's bytes: remember which serial it took, so the settled
        // release can tell "already mirrored in full" from "never read". A later write bumps the capture's
        // serial and the remembered one no longer matches, which is the whole test. Locked on the map: this
        // query is documented safe from any thread, and the release mutates the same map on the render thread.
        synchronized (this.suppliedSerials) {
            this.suppliedSerials.put(GlsmFrontend.texLevelKey(textureName, level), capture.serial);
        }
        ByteBuffer view = capture.data.duplicate();
        view.clear();
        return view.asReadOnlyBuffer();
    }

    /**
     * {@return the texel offset the level's captured record covers inside its level}, (0,0) for a record that fills
     * the level from the origin. A capture that is a {@code glTexSubImage2D} patch into a level too big for the
     * capture budget is kept at patch size and carries the position the patch belongs at, so the mirror path writes
     * it into the extent-sized image as a region rather than posing the patch as the whole level - the case that
     * built the E2E atlas's mirror at 1125x1125. Returns false when no record exists.
     */
    boolean textureUploadOffset(int textureName, int level, int[] outXY) {
        TexCapture capture = this.texCaptures.get(GlsmFrontend.texLevelKey(textureName, level));
        if (capture == null) {
            return false;
        }
        if (outXY != null && outXY.length >= 2) {
            outXY[0] = capture.x;
            outXY[1] = capture.y;
        }
        return true;
    }

    /**
     * {@return whether the level has backlog patches waiting}, without building the snapshot - the query the
     * supply paths ask before they may skip a full re-read.
     */
    boolean textureHasPendingPatches(int textureName, int level) {
        java.util.List<TexCapture> pending = this.texPatchBacklog.get(
                GlsmFrontend.texLevelKey(textureName, level));
        return pending != null && !pending.isEmpty();
    }

    /**
     * {@return a snapshot of the level's backlog patches}, ordered by write serial. Empty when none are pending.
     * A snapshot keeps a concurrent {@code glTexSubImage2D} append from changing the list under the consumer;
     * a patch appended mid-iteration lands in the next call, which its newer serial makes the consumer take.
     */
    java.util.List<GlsmFrontend.PendingPatch> texturePendingPatches(int textureName, int level) {
        java.util.List<TexCapture> pending = this.texPatchBacklog.get(
                GlsmFrontend.texLevelKey(textureName, level));
        java.util.List<GlsmFrontend.PendingPatch> out = new java.util.ArrayList<GlsmFrontend.PendingPatch>(
                pending == null ? 0 : pending.size());
        if (pending != null) {
            for (TexCapture capture : pending) {
                out.add(new GlsmFrontend.PendingPatch(capture));
            }
        }
        return out;
    }

    /**
     * Drops every backlog patch at or below {@code uptoSerial} for the level. A consumer calls this after writing
     * the patches into the mirror, which keeps the backlog at in-flight size instead of a second copy of the
     * texture. Patches appended after the call are untouched, so a write the consumer never saw is never pruned.
     */
    void texturePatchesConsumed(int textureName, int level, long uptoSerial) {
        long key = GlsmFrontend.texLevelKey(textureName, level);
        java.util.List<TexCapture> pending = this.texPatchBacklog.get(key);
        if (pending == null) {
            return;
        }
        for (java.util.Iterator<TexCapture> it = pending.iterator(); it.hasNext();) {
            TexCapture capture = it.next();
            if (capture.serial <= uptoSerial) {
                this.texPatchBytes -= capture.data.capacity();
                it.remove();
            }
        }
        if (pending.isEmpty()) {
            this.texPatchBacklog.remove(key);
        }
    }

    /** {@return the serial the most recent level write took}, any texture: unchanged means no texture was written. */
    long textureWriteEpoch() {
        return this.texUploadSerial.get();
    }

    /**
     * {@return the write serial the level's captured record carries, or {@link Long#MIN_VALUE} when no record
     * exists at all}
     * <p>
     * {@link TexCapture#serial} documents the numbering. Two calls that answer the same number mean the level's
     * pixels are exactly what they were at the first call, which is all a mirror needs to decide an upload would
     * be a no-op. {@link Long#MIN_VALUE} never collides with a real serial - real numbers start at 1 - so a level
     * with no record reads as "unknown" rather than "unchanged".
     *
     * @param textureName the GL texture name
     * @param level       the mip level
     */
    long textureUploadSerial(int textureName, int level) {
        long key = GlsmFrontend.texLevelKey(textureName, level);
        long serial = Long.MIN_VALUE;
        TexCapture capture = this.texCaptures.get(key);
        if (capture != null) {
            serial = capture.serial;
        }
        java.util.List<TexCapture> pending = this.texPatchBacklog.get(key);
        if (pending != null && !pending.isEmpty()) {
            serial = Math.max(serial, pending.get(pending.size() - 1).serial);
        }
        return serial;
    }

    /**
     * {@return whether the level exists but was allocated with no data}, the render-target idiom.
     * <p>
     * GL reserves the storage and leaves the content undefined, and a framebuffer then renders into it. For such a
     * level the stub holds an extent and a zero surface by design: it is neither a missed upload nor a refused fill, so
     * the mirror path reports it as deferred rather than as a rejection.
     */
    /** {@return whether the texture was allocated with a NULL data pointer and no data has arrived since}. */
    boolean textureAllocatedWithoutData(GlsmFrontend stub, int textureName) {
        return GlsmNameLedger.current().isNullAllocated(textureName);
    }

    boolean textureLevelAllocatedWithoutPixels(int textureName, int level) {
        TexCapture capture = this.texCaptures.get(GlsmFrontend.texLevelKey(textureName, level));
        return capture != null && !capture.gamePixels;
    }

    /**
     * {@return whether the level holds pixels the game itself wrote}, false for the zero-filled surface a
     * {@code glTexImage2D} with a NULL data pointer left behind.
     * <p>
     * {@link #textureUploadRgba8} answers "is there a surface", and an allocation with no data gives one: the game
     * allocates every level of a texture that way and then fills it in patches, so the surface has to exist. What the
     * surface cannot say is whether any of it is the game's, and a mirror built from allocation zeros claims whole
     * content no upload ever put there - measured as two 1280x720 mirrors, and the light map's first read, reporting
     * {@code opaque=0} over {@code full=true}. A level with any real upload - a full image or a single patch - answers
     * true, because that surface is what GL holds: the patches where they were written, undefined (read as zeros)
     * elsewhere.
     *
     * @param textureName the GL texture name
     * @param level       the mip level
     */
    boolean textureLevelHasGamePixels(int textureName, int level) {
        TexCapture capture = this.texCaptures.get(GlsmFrontend.texLevelKey(textureName, level));
        return capture != null && capture.gamePixels;
    }

    /**
     * {@return whether a mirror upload of this level has provably nothing to read}, so the upload would throw the
     * capture-miss instead of filling the image.
     * <p>
     * The two sources {@code uploadFromGlTexture} can read are the level's own captured record and its patch backlog.
     * When neither exists the read is refused - "the stub captured no upload for this texture level" - and the caller
     * has no content to take. That is a fact about the level, not a failure of the upload: a name the game allocated
     * empty and never wrote (a render target whose framebuffer the stub does not know, a texture a mod reserves and
     * fills through a path that carries no pixels) answers this way on every bind, and asking anyway costs an
     * exception per bind. Measured on leg {@code r33a-accept} (`celtium-world-accept`, 188 mods): 28,272 failed
     * uploads in 1,800 frames - 15.7 a frame - with every batch still accepted, so the failures were pure cost and
     * the picture was drawn from whatever the mirror already held.
     * <p>
     * A level that was allocated with no data but <em>is</em> recorded (the zero-filled render-target idiom) is not
     * "nothing to read": the record exists and the upload takes its zeros, which is the state the mirror is meant to
     * hold until the owner publishes. Only the absent record and the empty backlog answer true here.
     */
    boolean celtium$levelHasNothingToRead(int textureName, int level) {
        long key = GlsmFrontend.texLevelKey(textureName, level);
        if (this.texCaptures.containsKey(key)) {
            return false;
        }
        java.util.List<TexCapture> pending = this.texPatchBacklog.get(key);
        return pending == null || pending.isEmpty();
    }

    /**
     * The rule itself, with the three facts it reads passed in, so the pending-patch exception can be pinned by a CPU
     * test: a geometry backlog is content {@code uploadFromGlTexture} replays, and treating it as "nothing to supply"
     * would leave the level empty until its framebuffer happened to publish.
     */
    static boolean celtium$mirrorWaitsForFramebuffer(boolean colourAttachment, boolean pendingPatches,
            boolean gamePixels) {
        return colourAttachment && !pendingPatches && !gamePixels;
    }

    /** {@link #celtium$supplyDecision}: the level holds content a full upload can read. */
    public static final int SUPPLY_NOW = 0;
    /** {@link #celtium$supplyDecision}: a framebuffer's colour attachment with nothing captured; its owner publishes. */
    public static final int SUPPLY_WAIT_FRAMEBUFFER = 1;
    /** {@link #celtium$supplyDecision}: no captured record and no patch backlog, so the upload would throw. */
    public static final int SUPPLY_NOTHING_TO_READ = 2;

    /**
     * {@return which of the three supply outcomes this level has}, in one pass over the two capture maps.
     * <p>
     * The lazy supply asked the same two maps through three separate predicates - {@link #textureUploadSerial} (two
     * gets), {@link #celtium$mirrorWaitsForFramebuffer} (the framebuffer record plus two more gets on the same key) and
     * {@link #celtium$levelHasNothingToRead} (a {@code containsKey} and a second backlog get) - so one decision cost
     * nine map lookups on a path the accept census puts at 1.8 us per slow-path offer. The three facts the two rules
     * read are the same two records, so they are read once here and the rules are applied in the order the caller
     * applied them: the framebuffer deferral first, then "nothing to read".
     * <p>
     * The two older predicates stay: the registry's own lookup branches and the raw-write paths ask them one at a
     * time, and this method is the caller that asked all of them.
     *
     * @param textureName the GL texture name the bound batch samples
     * @param level       the mip level
     */
    int celtium$supplyDecision(int textureName, int level) {
        if (textureName <= 0) {
            return SUPPLY_NOTHING_TO_READ;
        }
        long key = GlsmFrontend.texLevelKey(textureName, level);
        TexCapture capture = this.texCaptures.get(key);
        java.util.List<TexCapture> pending = this.texPatchBacklog.get(key);
        boolean pendingPatches = pending != null && !pending.isEmpty();
        boolean gamePixels = capture != null && capture.gamePixels;
        if (celtium$mirrorWaitsForFramebuffer(GlsmFrontend.celtium$framebufferForColorTexture(textureName) != 0,
                pendingPatches, gamePixels)) {
            return SUPPLY_WAIT_FRAMEBUFFER;
        }
        if (capture == null && !pendingPatches) {
            return SUPPLY_NOTHING_TO_READ;
        }
        return SUPPLY_NOW;
    }

    /**
     * One line of capture diagnostics: the levels currently holding pixels, how many came from a full image, a
     * sub-image patch or an undefined level allocated with no data and zero-filled (the shape the game gives its
     * atlases before writing them sprite by sprite), the uploads that could not be converted (unsupported format/type
     * pair, a pixel-store state the conversion does not model, a compressed payload, a depth-carrying or oversized
     * level), and the RGBA8 bytes those levels hold.
     */
    String textureCaptureSummary() {
        long bytes = 0L;
        for (TexCapture capture : this.texCaptures.values()) {
            bytes += capture.data.capacity();
        }
        int backlogPatches = 0;
        long topName = -1L, topCount = -1L;
        for (java.util.Map.Entry<Long, java.util.List<TexCapture>> e : this.texPatchBacklog.entrySet()) {
            backlogPatches += e.getValue().size();
            if (e.getValue().size() > topCount) {
                topCount = e.getValue().size();
                topName = e.getKey().longValue();
            }
        }
        StringBuilder keys = new StringBuilder();
        java.util.List<long[]> sorted = new java.util.ArrayList<long[]>(this.patchKeys.size());
        it.unimi.dsi.fastutil.longs.LongIterator patchKeyIter = this.patchKeys.keySet().iterator();
        while (patchKeyIter.hasNext()) {
            long key = patchKeyIter.nextLong();
            sorted.add(new long[] { key, this.patchKeys.get(key) });
        }
        sorted.sort((a, b) -> Long.compare(b[1], a[1]));
        for (int i = 0; i < Math.min(4, sorted.size()); i++) {
            long[] entry = sorted.get(i);
            long key = entry[0];
            keys.append(" ").append(key >>> 16).append('.').append(key & 0xFFL).append('=').append(entry[1]);
            TexCapture stored = this.texCaptures.get(key);
            keys.append(stored == null
                    ? "[none]"
                    : "[" + stored.width + "x" + stored.height + "@" + stored.x + "," + stored.y
                            + (stored.gamePixels ? "" : ",undef") + "]");
        }
        StringBuilder branches = new StringBuilder("{");
        appendPatchBranch(branches, "backlog", this.patchBranchBacklog);
        appendPatchBranch(branches, "surface", this.patchBranchSurface);
        appendPatchBranch(branches, "lone", this.patchBranchLone);
        appendPatchBranch(branches, "paste", this.patchBranchPaste);
        branches.append('}');
        return "texture capture: levels=" + this.texCaptures.size()
                + " full=" + this.fullCaptures.get()
                + " patches=" + this.subCaptures.get()
                + " identicalPatches=" + this.identicalPatches.get()
                + " identicalFull=" + this.identicalFullImages.get()
                + " undefined=" + this.undefinedExtents.get()
                + " unconverted=" + this.unconvertedUploads.get()
                + " backlog=" + backlogPatches + "/" + this.texPatchBytes + "B@" + (topName >>> 16)
                + "." + (topName & 0xFFL)
                + " patchKeys=" + keys
                + " branches=" + branches
                + " bytes=" + bytes;
    }

    private static void appendPatchBranch(StringBuilder out, String name, int count) {
        if (count <= 0) {
            return;
        }
        if (out.length() > 1) {
            out.append(", ");
        }
        out.append(name).append('=').append(count);
    }

    /** {@return the RGBA8 bytes the filed captures currently hold} - the total {@link #textureCaptureSummary}
     * prints as {@code bytes=}, without building the line. */
    long textureCaptureBytes() {
        long bytes = 0L;
        for (TexCapture capture : this.texCaptures.values()) {
            bytes += capture.data.capacity();
        }
        return bytes;
    }

    /**
     * Drops the stub's CPU copy of every captured level that has settled - the celtium-owned half of the TexFix
     * rule (item 387 / 361-B). A level qualifies only when every one of these holds:
     * <ul>
     *   <li>the record holds game pixels from a full upload (not an undefined surface),</li>
     *   <li>no {@code glTexSubImage2D} patch has ever touched the record ({@link TexCapture#patched}) - a patch
     *   that arrives after a release would be pasted into a fresh undefined surface and zero the rest of the
     *   level, so a level that is patched at all is treated as patched forever,</li>
     *   <li>the record covers the whole recorded extent from the origin,</li>
     *   <li>no backlog patches are waiting for the level,</li>
     *   <li>a reader has already taken exactly this serial through {@link #textureUploadRgba8} - the mirror
     *   holds the full content, so the stub's copy is a second copy,</li>
     *   <li>the texture is not a framebuffer colour attachment (its capture is the readback source for a surface
     *   the renderer itself rewrites).</li>
     * </ul>
     * A level that fails any test keeps its copy. A later full upload simply files a fresh record, so a texture
     * the game re-uploads after a release is captured again as before. The caller decides when uploads have
     * settled; this method only applies the rule.
     *
     * @return {@code {levels released, bytes released}}
     */
    long[] releaseSettledTextureCaptures(GlsmFrontend stub) {
        long levels = 0L;
        long bytes = 0L;
        long[] keys = this.texCaptures.keySet().toLongArray();
        // The supplied-serial test and the removals hold the map's monitor throughout: a concurrent
        // textureUploadRgba8 put (it is documented safe from any thread) must not interleave with the
        // check-then-remove, or it could re-record a serial for a capture that is being dropped.
        synchronized (this.suppliedSerials) {
            for (long key : keys) {
                TexCapture capture = this.texCaptures.get(key);
                if (capture == null || !capture.gamePixels || capture.patched) {
                    continue;
                }
                if (capture.x != 0 || capture.y != 0) {
                    continue;
                }
                int[] extent = stub.texLevels.get(key);
                if (extent == null || extent[0] != capture.width || extent[1] != capture.height) {
                    continue;
                }
                java.util.List<TexCapture> pending = this.texPatchBacklog.get(key);
                if (pending != null && !pending.isEmpty()) {
                    continue;
                }
                if (!this.suppliedSerials.containsKey(key) || this.suppliedSerials.get(key) != capture.serial) {
                    continue;
                }
                int textureName = (int) (key >> 16);
                if (GlsmFrontend.celtium$framebufferForColorTexture(textureName) != 0) {
                    continue;
                }
                this.texCaptures.remove(key);
                this.suppliedSerials.remove(key);
                levels++;
                bytes += capture.data.capacity();
            }
        }
        return new long[] { levels, bytes };
    }
}
