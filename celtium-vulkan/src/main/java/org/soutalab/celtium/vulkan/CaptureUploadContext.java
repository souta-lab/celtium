package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The production {@link TextureUploadContext} (S3-b E4-b2, door ruling family 3): the capture
 * store's producers file uploads against this context, whose records are the seat-B owners
 * themselves - the registry's capture store for extents and captures, {@link NativeTextureBindings}
 * for the bound-name resolution, {@link PixelUnpackState} for the pixel unpack binding and byte
 * read, and {@link BufferStorageRecord} for buffer logical sizes.
 *
 * <p>Until E4-b2 the {@code GlsmFrontend} monolith implemented the context out of its own fields.
 * The bodies below are those implementations with the monolith's fields replaced by their seat
 * owners; the monolith keeps its own implementation for its tests until E4-c, but production
 * filing - the capture entry's dispatch and the hint doors - goes through this one.
 */
public final class CaptureUploadContext implements TextureUploadContext {

    private static final CaptureUploadContext CURRENT = new CaptureUploadContext();

    public static CaptureUploadContext current() {
        return CURRENT;
    }

    private static final boolean TARGET_FORMAT_CENSUS = Boolean.getBoolean("celtium.vulkan.targetFormatCensus");

    /** Bounded: a run reports the first few large re-specifications and deletions. */
    private static final AtomicInteger RESPECIFIED_LINES = new AtomicInteger(8);
    private static final AtomicInteger DELETED_LINES = new AtomicInteger(8);

    /** Highest low-byte mip level written to the store, so the forget sweep avoids 256 probes. */
    private int maxRecordedTexLevel = -1;

    private int pboUploadLogs;
    private int pixelBufferRefusals;

    private CaptureUploadContext() {
    }

    private static TextureCaptureStore store() {
        return VulkanTextureRegistry.captureStore();
    }

    /** The shared record key: texture name in the high bits, level in the low byte. */
    public static long texLevelKey(int name, int level) {
        return ((long) name << 16) | (level & 0xFFL);
    }

    @Override
    public long putTexLevelKey(int name, int level) {
        int masked = level & 0xFF;
        if (masked > this.maxRecordedTexLevel) {
            this.maxRecordedTexLevel = masked;
        }
        return ((long) name << 16) | masked;
    }

    /**
     * The name an upload's records belong to: the texture bound to the target on the active unit, or {@code -target}
     * for a proxy target. Proxy uploads are how FML probes the maximum texture size - they bind to nothing and their
     * "pixels" are never a real image, so only an extent is recorded for them.
     */
    @Override
    public int uploadTargetName(int target) {
        // GL_PROXY_TEXTURE_1D/2D/3D/CUBE_MAP - uploads to these carry no pixels and bind to nothing.
        if (target == 0x8063 || target == 0x8064 || target == 0x8070 || target == 0x851B) {
            return -target;
        }
        int bound = NativeTextureBindings.current().boundTexture(target);
        if (bound < 0) {
            // The per-unit map is empty under GL-less, by design: GlStateManager.bindTexture skips the GL call when its
            // own cache already holds the binding, so no glBindTexture arrives for the unit a texture was first bound
            // on. The texture parameters and the texture copies already fall back to the last binding the state manager
            // named; an upload has the same need, and it is what gives the block atlas its pixels. Measured on the
            // 268-mod E2E pack: the 8192x8192 atlas is GL texture 353 and the registry logged
            // CELTIUM_CAPTURE_MISS glTexture=353 level=0 "the stub captured no upload for this texture level" while
            // nglTexSubImage2D was called 41199 times in a single cohort run - the uploads went nowhere because the
            // unit map had no entry to file them under.
            bound = NativeTextureBindings.current().lastBoundTexture();
        }
        return bound < 0 ? 0 : bound;
    }

    /**
     * Records an uploaded extent under {@link #uploadTargetName}, which is what {@code glGetTexLevelParameter} answers
     * from. For an upload whose pixels the store cannot use, this record is all there is.
     */
    @Override
    public void recordExtent(int target, int level, int internal, int width, int height) {
        int name = uploadTargetName(target);
        if (name == 0) {
            return;
        }
        recordExtentForName(name, level, internal, width, height);
    }

    /** The extent record for a texture already named, as the DSA storage forms carry it. */
    public void recordExtentForName(int name, int level, int internal, int width, int height) {
        int[] previous = store().texLevels().put(putTexLevelKey(name, level), new int[] { width, height, internal });
        if (TARGET_FORMAT_CENSUS && level == 0 && internal != 0 && !isDepthInternalFormat(internal)) {
            store().noteTargetFormatRequest(internal);
        }
        if (level == 0 && previous != null && (previous[0] != width || previous[1] != height)
                && previous[0] * previous[1] >= 1024 * 1024 && RESPECIFIED_LINES.getAndDecrement() > 0) {
            // A large level re-specified at another size: the texture name was handed to something else (a mod that
            // deleted and re-generated it, or one that uploaded while it believed its own texture was bound). The
            // mirror under that name is then replaced, so this is the line that names who took a block atlas's name.
            StringBuilder callers = new StringBuilder();
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (int i = 2; i < stack.length && i < 14; i++) {
                callers.append(i == 2 ? "" : " <- ").append(stack[i].getClassName().replaceAll(".*\\.", ""))
                        .append('.').append(stack[i].getMethodName()).append(':').append(stack[i].getLineNumber());
            }
            System.out.println("[Celtium] CELTIUM_TEXTURE_RESPECIFIED name=" + name + " " + previous[0] + "x"
                    + previous[1] + " -> " + width + "x" + height + " callers=[" + callers + "]");
        }
    }

    private static boolean isDepthInternalFormat(int internalFormat) {
        return switch (internalFormat) {
            case 0x1902, 0x81A5, 0x81A6, 0x81A7, 0x8CAC, 0x88F0, 0x8CAD, 0x84F9 -> true;
            default -> false;
        };
    }

    @Override
    public int pixelUnpackBuffer() {
        return PixelUnpackState.current().pixelUnpackBuffer();
    }

    @Override
    public void pixelUnpackBuffer(int buffer) {
        PixelUnpackState.current().pixelUnpackBuffer(buffer);
    }

    @Override
    public ByteBuffer pixelUnpackStorage() {
        return PixelUnpackState.current().storage(PixelUnpackState.current().pixelUnpackBuffer());
    }

    @Override
    public int unpackAlignment() {
        return PixelUnpackState.current().unpackAlignment();
    }

    @Override
    public int unpackRowLength() {
        return PixelUnpackState.current().unpackRowLength();
    }

    @Override
    public int unpackSkipPixels() {
        return PixelUnpackState.current().unpackSkipPixels();
    }

    @Override
    public int unpackSkipRows() {
        return PixelUnpackState.current().unpackSkipRows();
    }

    @Override
    public int logicalSizeOf(int bufferName, int capacity) {
        return BufferStorageRecord.current().logicalSizeOf(bufferName, capacity);
    }

    @Override
    public boolean countPboUploadLog() {
        if (this.pboUploadLogs < 8) {
            this.pboUploadLogs++;
            return true;
        }
        return false;
    }

    @Override
    public boolean countPixelBufferRefusal() {
        if (this.pixelBufferRefusals < 8) {
            this.pixelBufferRefusals++;
            return true;
        }
        return false;
    }

    @Override
    public void forgetTextureRecords(int name) {
        // The name's parameter record drops with it: a recycled name must not inherit its
        // predecessor's wrap/filter (the record's own javadoc gives the reasons, #389), nor its
        // extent records or captures.
        store().parameters().forget(name);
        int maxLevel = this.maxRecordedTexLevel;
        if (maxLevel < 0) {
            return;
        }
        if (!store().texLevels().isEmpty()) {
            for (int level = 0; level <= maxLevel; level++) {
                store().texLevels().remove(texLevelKey(name, level));
            }
        }
        store().forgetCaptures(name, maxLevel);
    }

    @Override
    public void deleteTextureRecords(int name) {
        NativeImageBindings.current().delete(name);
        if (name != 0) {
            Object backend = CeltiumBackends.current();
            if (backend instanceof VulkanTerrainGpuBackend vulkan) {
                for (Integer fbo : FramebufferAttachmentTable.current().framebuffersWithColorTexture(name)) {
                    vulkan.celtium$rawAttachmentTextureDeleted(fbo.intValue());
                }
                vulkan.celtium$rawTextureDeleted(name);
            }
        }
        int[] level0 = store().texLevels().get(texLevelKey(name, 0));
        if (level0 != null && level0[0] * level0[1] >= 1024 * 1024 && DELETED_LINES.getAndDecrement() > 0) {
            StringBuilder callers = new StringBuilder();
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (int i = 2; i < stack.length && i < 14; i++) {
                callers.append(i == 2 ? "" : " <- ").append(stack[i].getClassName().replaceAll(".*\\.", ""))
                        .append('.').append(stack[i].getMethodName()).append(':').append(stack[i].getLineNumber());
            }
            System.out.println("[Celtium] CELTIUM_TEXTURE_DELETED name=" + name + " " + level0[0] + "x" + level0[1]
                    + " callers=[" + callers + "]");
        }
        NativeTextureBindings.current().delete(name);
        // The "allocated without data" classification belongs to the deleted object (item 328, V-04): a
        // recycled name was read as render-target idiom it never declared when the raw route left it standing.
        GlsmNameLedger.current().removeNullAllocated(name);
        NativeTextureBindings.current().forgetBoundTexture(name);
        forgetTextureRecords(name);
    }
}
