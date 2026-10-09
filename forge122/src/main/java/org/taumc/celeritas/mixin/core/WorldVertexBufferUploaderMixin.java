package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import java.nio.ByteBuffer;

import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Offers every finished immediate-mode batch to Vulkan before OpenGL draws it.
 * <p>
 * {@code WorldVertexBufferUploader.func_181679_a} is the single draw entry of Minecraft's immediate path:
 * {@code Tessellator.func_78381_a()} ends here, and so does every renderer built on {@code BufferBuilder} - entities,
 * tile entities, items and the hand, particles, the GUI, the sky, clouds and weather. Capturing it is what makes the
 * Forge API swap possible without changing a single mod.
 * <p>
 * The batch is only *offered*: when the backend takes it the original body is cancelled, and when it refuses (which
 * is every case today, because the per-vertex-format pipelines are still missing) the original body runs and OpenGL
 * draws exactly as before. The fields are read through shadows rather than accessors so the buffer object itself is
 * untouched, and no Minecraft signature is changed.
 */
@Mixin(WorldVertexBufferUploader.class)
public abstract class WorldVertexBufferUploaderMixin {
    @Inject(method = "func_181679_a", at = @At("HEAD"), require = 0)
    private void celtium$offerBatch(BufferBuilder buffer, CallbackInfo info) {
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        if (backend == null || buffer == null) {
            return;
        }
        try {
            // Values are read from the buffer through its own public API where one exists; the vertex data address and
            // the vertex count come from the shadowed fields because Minecraft exposes no accessor for them.
            Object format = celtium$vertexFormat(buffer);
            int stride = celtium$vertexStride(format);
            int formatId = celtium$formatId(format);
            long address = celtium$vertexAddress(buffer);
            int vertices = celtium$vertexCount(buffer);
            int mode = celtium$drawMode(buffer);
            if (address == 0L || vertices <= 0) {
                return;
            }
            if (backend.submitImmediateBatch(format, stride, formatId, address, vertices, mode)) {
                info.cancel();
            }
        } catch (Throwable ignored) {
            // Never let the offer break the draw: the original body stays in charge when anything goes wrong.
        }
    }

    /** The stride comes from the format itself, so the backend can decide structurally rather than by class. */
    private static final java.util.Map<Object, Integer> celtium$formatIds =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<Object, Integer>());

    private static int celtium$vertexStride(Object format) {
        if (format instanceof net.minecraft.client.renderer.vertex.VertexFormat) {
            return ((net.minecraft.client.renderer.vertex.VertexFormat) format).getSize();
        }
        return 0;
    }

    /**
     * Identifies the layout by comparing its element list with vanilla's own formats.
     * <p>
     * Class identity would fail for the formats mods build themselves and a stride alone is ambiguous - 24 bytes is
     * both {@code POSITION_TEX_COLOR} and {@code POSITION_TEX_LMAP_COLOR}. The element instances are shared, so
     * comparing them is exact, and the result is cached per format instance because this runs for every batch.
     */
    private static int celtium$formatId(Object format) {
        if (!(format instanceof net.minecraft.client.renderer.vertex.VertexFormat)) {
            return 0;
        }
        net.minecraft.client.renderer.vertex.VertexFormat vertexFormat =
                (net.minecraft.client.renderer.vertex.VertexFormat) format;
        Integer cached = celtium$formatIds.get(vertexFormat);
        if (cached != null) {
            return cached;
        }
        int id = 0;
        if (celtium$sameElements(vertexFormat, DefaultVertexFormats.POSITION_COLOR)) {
            id = 1;
        } else if (celtium$sameElements(vertexFormat, DefaultVertexFormats.POSITION_TEX_COLOR)) {
            id = 2;
        }
        celtium$formatIds.put(vertexFormat, id);
        return id;
    }

    private static boolean celtium$sameElements(net.minecraft.client.renderer.vertex.VertexFormat left,
                                                net.minecraft.client.renderer.vertex.VertexFormat right) {
        java.util.List<net.minecraft.client.renderer.vertex.VertexFormatElement> a = left.getElements();
        java.util.List<net.minecraft.client.renderer.vertex.VertexFormatElement> b = right.getElements();
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

    private static Object celtium$vertexFormat(BufferBuilder buffer) {
        try {
            java.lang.reflect.Field field = BufferBuilder.class.getDeclaredField("field_179011_q");
            field.setAccessible(true);
            return field.get(buffer);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }

    private static long celtium$vertexAddress(BufferBuilder buffer) {
        try {
            java.lang.reflect.Field field = BufferBuilder.class.getDeclaredField("field_179001_a");
            field.setAccessible(true);
            Object value = field.get(buffer);
            if (!(value instanceof ByteBuffer)) {
                return 0L;
            }
            // The address comes from LWJGL's MemoryUtil through reflection: this module does not compile against the
            // LWJGL bindings, and the mixin must not add that dependency just to read a pointer.
            Class<?> memoryUtil = Class.forName("org.lwjgl.system.MemoryUtil");
            return ((Number) memoryUtil.getMethod("memAddress", ByteBuffer.class).invoke(null, value)).longValue();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return 0L;
        }
    }

    private static int celtium$vertexCount(BufferBuilder buffer) {
        try {
            java.lang.reflect.Field field = BufferBuilder.class.getDeclaredField("field_178997_d");
            field.setAccessible(true);
            return field.getInt(buffer);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return 0;
        }
    }

    private static int celtium$drawMode(BufferBuilder buffer) {
        try {
            java.lang.reflect.Field field = BufferBuilder.class.getDeclaredField("field_179006_k");
            field.setAccessible(true);
            return field.getInt(buffer);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return -1;
        }
    }
}
