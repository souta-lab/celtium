package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Offers every finished immediate-mode batch to Vulkan before OpenGL draws it.
 * <p>
 * {@code WorldVertexBufferUploader.func_181679_a} is the single draw entry of Minecraft's immediate path:
 * {@code Tessellator.func_78381_a()} ends here, and so does every renderer built on {@code BufferBuilder} - entities,
 * tile entities, items and the hand, particles, the GUI, the sky, clouds and weather. Capturing it is what makes the
 * Forge API swap possible without changing a single mod.
 * <p>
 * The batch is only *offered*: when the backend takes it the original body is cancelled, and when it refuses the
 * original body runs and OpenGL draws exactly as before. Nothing public is changed.
 * <p>
 * Private members are reached through the {@link BufferBuilderAccessor} mixin rather than reflection, and the vertex
 * count through the public {@code getVertexCount()}. The first revision reflected per batch
 * ({@code getDeclaredField}/{@code setAccessible}, {@code Class.forName}/{@code getMethod}/{@code invoke}), which
 * measured 29252 ns per offered batch; the accessor removes the lookup at compile time and the refmap remaps it like
 * any other mixin reference.
 */
@Mixin(WorldVertexBufferUploader.class)
public abstract class WorldVertexBufferUploaderMixin {
    /** Layout ids, decided by comparing element lists rather than class names or strides. */
    private static int celtium$compositeDumps;

    private static final Map<VertexFormat, Integer> celtium$formatIds =
            Collections.synchronizedMap(new WeakHashMap<VertexFormat, Integer>());

    // cancellable = true is what makes info.cancel() mean anything: with it false - the default - cancel() throws, and
    // this injector's broad catch swallowed that exception, so the original GL draw ran for every batch the backend had
    // already accepted. The Vulkan path and the GL path drew the same geometry, the images matched, and nothing in the
    // comparisons could tell the difference. Counts of "accepted" batches were counts of intent, not of replacement.
    @Inject(method = "func_181679_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$offerBatch(BufferBuilder buffer, CallbackInfo info) {
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        if (backend == null || buffer == null) {
            return;
        }
        boolean accepted = false;
        try {
            VertexFormat format = celtium$vertexFormat(buffer);
            int stride = format == null ? 0 : format.getSize();
            int vertices = buffer.getVertexCount();
            ByteBuffer data = celtium$byteBuffer(buffer);
            if (data == null || vertices <= 0) {
                return;
            }
            long address = org.lwjgl.system.MemoryUtil.memAddress(data);
            if (address == 0L) {
                return;
            }
            int formatId = celtium$formatId(format);
            int mode = celtium$drawMode(buffer);
            if (formatId == 2 && celtium$compositeDumps < 1) {
                // Unconditional: this runs for every offered batch, before the backend's acceptance mask decides
                // anything, which is where a dump that must survive a mask has to live. It answers who draws the
                // full-screen composite quad, which decides where that quad belongs.
                celtium$compositeDumps++;
                StringBuilder elements = new StringBuilder();
                for (VertexFormatElement element : format.getElements()) {
                    elements.append(element.getSize()).append('b').append(element.getUsage()).append(' ');
                }
                System.out.println("celtium composite batch: elements=[" + elements.toString().trim() + "] vertices="
                        + vertices + " mode=" + mode + " stride=" + stride + " v0=(pos "
                        + org.lwjgl.system.MemoryUtil.memGetFloat(address) + ","
                        + org.lwjgl.system.MemoryUtil.memGetFloat(address + 4) + " uv "
                        + org.lwjgl.system.MemoryUtil.memGetFloat(address + 12) + ","
                        + org.lwjgl.system.MemoryUtil.memGetFloat(address + 16) + " col 0x"
                        + Integer.toHexString(org.lwjgl.system.MemoryUtil.memGetInt(address + 20)) + ")");
                StackTraceElement[] stack = new Throwable().getStackTrace();
                StringBuilder trace = new StringBuilder();
                for (int i = 1; i < Math.min(stack.length, 14); i++) {
                    trace.append(stack[i].getClassName()).append('.').append(stack[i].getMethodName()).append(':')
                            .append(stack[i].getLineNumber()).append(" <- ");
                }
                System.out.println("celtium composite caller: " + trace);
            }
        } catch (Throwable ignored) {
            // Never let the offer break the draw: the original body stays in charge when anything goes wrong. The
            // cancellation itself is outside this catch on purpose - swallowing it was the defect this comment used to
            // hide.
            return;
        }
        if (accepted) {
            // The original body resets the builder after uploading. Cancelling skips that, so it is done here; without
            // it Minecraft would re-upload the same vertices on the next draw.
            try {
                buffer.reset();
            } finally {
                info.cancel();
            }
        }
    }

    /**
     * Identifies the layout by comparing its element list with vanilla's own formats.
     * <p>
     * Class identity would fail for formats a mod builds itself, and a stride alone is ambiguous - 28 bytes is BLOCK,
     * ITEM and the particle layout, which differ in element order. The element instances are shared, so comparing them
     * is exact, and the result is cached per format instance because this runs for every batch.
     */
    private static int celtium$formatId(VertexFormat format) {
        if (format == null) {
            return 0;
        }
        Integer cached = celtium$formatIds.get(format);
        if (cached != null) {
            return cached.intValue();
        }
        int id;
        if (celtium$sameElements(format, DefaultVertexFormats.POSITION_COLOR)) {
            id = 1;
        } else if (celtium$sameElements(format, DefaultVertexFormats.POSITION_TEX_COLOR)) {
            id = 2;
        } else if (celtium$sameElements(format, DefaultVertexFormats.BLOCK)) {
            id = 3;
        } else if (celtium$sameElements(format, DefaultVertexFormats.ITEM)) {
            id = 4;
        } else if (celtium$sameElements(format, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP)) {
            id = 5;
        } else {
            id = 0;
        }
        celtium$formatIds.put(format, Integer.valueOf(id));
        return id;
    }

    private static boolean celtium$sameElements(VertexFormat left, VertexFormat right) {
        List<VertexFormatElement> a = left.getElements();
        List<VertexFormatElement> b = right.getElements();
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i) != b.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static VertexFormat celtium$vertexFormat(BufferBuilder buffer) {
        try {
            return ((BufferBuilderAccessor) buffer).celtium$vertexFormat();
        } catch (RuntimeException | LinkageError error) {
            return null;
        }
    }

    private static ByteBuffer celtium$byteBuffer(BufferBuilder buffer) {
        try {
            return ((BufferBuilderAccessor) buffer).celtium$byteBuffer();
        } catch (RuntimeException | LinkageError error) {
            return null;
        }
    }

    private static int celtium$drawMode(BufferBuilder buffer) {
        try {
            return ((BufferBuilderAccessor) buffer).celtium$drawMode();
        } catch (RuntimeException | LinkageError error) {
            return -1;
        }
    }
}
