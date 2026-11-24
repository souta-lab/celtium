package org.soutalab.celtium.vulkan.gl;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.NativeGeometryBuffer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code celtium$splitArraysValid} must account for {@code first} and guard against {@code long} offset overflow
 * when validating split {@code texCoord}/{@code color} arrays in a bound VBO.
 */
class SplitArraysBoundsTest {
    private static final int GL_QUADS = 0x0007;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_FLOAT = 0x1406;
    private static final int GL_VERTEX_ARRAY = 0x8074;
    private static final int GL_NORMAL_ARRAY = 0x8075;
    private static final int GL_COLOR_ARRAY = 0x8076;
    private static final int GL_TEXTURE_COORD_ARRAY = 0x8078;
    private static final int GL_ARRAY_BUFFER = 0x8892;
    private static final int GL_STATIC_DRAW = 0x88E4;

    private static final class RecordingBackend
            implements org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend {
        record Submission(int stride, int formatId, int vertexCount, float firstX, float firstU, int firstColor) {}

        final List<Submission> submissions = new ArrayList<>();

        @Override
        public String getName() {
            return "test-recording";
        }

        @Override
        public void render(ChunkRenderMatrices matrices, ChunkRenderListIterable renderLists, TerrainRenderPass pass,
                           CameraTransform occlusionCamera, CameraTransform camera, Object view, boolean profile) {
        }

        @Override
        public boolean submitImmediateBatch(Object vertexFormat, int vertexStride, int formatId, long vertexAddress,
                                            int vertexCount, int primitiveMode) {
            float firstX = MemoryUtil.memGetFloat(vertexAddress);
            float firstU = MemoryUtil.memGetFloat(vertexAddress + 12L);
            int firstColor = MemoryUtil.memGetInt(vertexAddress + 20L);
            this.submissions.add(new Submission(vertexStride, formatId, vertexCount, firstX, firstU, firstColor));
            return true;
        }

        @Override
        public void uploadMeshes(Collection<ChunkJobResult.Success<? extends ChunkTaskOutput>> results,
                                 Runnable graphUpdateTrigger) {
        }

        @Override
        public void updateRegions() {
        }

        @Override
        public void updateTimings() {
        }

        @Override
        public Object2LongMap<TerrainRenderPass> snapshotTimings() {
            return Object2LongMaps.emptyMap();
        }

        @Override
        public void collectMemoryStats(RenderSectionManager.DeviceMemoryStats destination) {
        }

        @Override
        public String getTransferQueueDebugString() {
            return "";
        }

        @Override
        public void delete() {
        }
    }

    private final GlsmFrontend stub;
    private final Method dispatch;
    private final Map<?, ?> ids;
    private final Field installedField;

    SplitArraysBoundsTest() throws Exception {
        var constructor = GlsmFrontend.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        this.stub = constructor.newInstance();
        this.dispatch = GlsmFrontend.class.getDeclaredMethod("dispatchArgs", int.class, long[].class);
        this.dispatch.setAccessible(true);
        Field idsField = GlsmFrontend.class.getDeclaredField("IDS");
        idsField.setAccessible(true);
        this.ids = (Map<?, ?>) idsField.get(null);
        this.installedField = GlsmFrontend.class.getDeclaredField("installed");
        this.installedField.setAccessible(true);
    }

    private long call(String name, long... args) throws Exception {
        long[] slots = new long[16];
        System.arraycopy(args, 0, slots, 0, args.length);
        return (Long) this.dispatch.invoke(this.stub, this.ids.get(name), slots);
    }

    private int genAndUpload(ByteBuffer data) throws Exception {
        ByteBuffer nameBuf = MemoryUtil.memAlloc(4);
        try {
            call("glGenBuffers", 1, MemoryUtil.memAddress(nameBuf));
            int name = nameBuf.getInt(0);
            call("glBindBuffer", GL_ARRAY_BUFFER, name);
            call("glBufferData", GL_ARRAY_BUFFER, data.capacity(), MemoryUtil.memAddress(data), GL_STATIC_DRAW);
            return name;
        } finally {
            MemoryUtil.memFree(nameBuf);
        }
    }

    @Test
    void refusesSplitArrayDrawWhenFirstAdvancesTexCoordPastVboBounds() throws Exception {
        Object previousStub = this.installedField.get(null);
        var previousBackend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        RecordingBackend backend = new RecordingBackend();
        this.installedField.set(null, this.stub);
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(backend);

        // Allocate 1024B first so the backing ByteBuffer stays safely readable, then shrink logical size to 512B.
        ByteBuffer large = MemoryUtil.memAlloc(1024);
        ByteBuffer logical = MemoryUtil.memAlloc(512);
        try {
            int vbo = genAndUpload(large);
            call("glBindBuffer", GL_ARRAY_BUFFER, vbo);
            call("glBufferData", GL_ARRAY_BUFFER, logical.capacity(), MemoryUtil.memAddress(logical), GL_STATIC_DRAW);

            call("glEnableClientState", GL_VERTEX_ARRAY);
            call("glEnableClientState", GL_NORMAL_ARRAY);
            call("glEnableClientState", GL_COLOR_ARRAY);
            call("glEnableClientState", GL_TEXTURE_COORD_ARRAY);

            // Positions fit: first=4, count=4, stride=48 -> end = 8 * 48 = 384 <= 512.
            call("glVertexPointer", 3, GL_FLOAT, 48, 0L);
            // Colors fit: offset=16, first=4, count=4, stride=16 -> end = 16 + 7 * 16 + 4 = 132 <= 512.
            GlsmFrontend.celtium$colorPointer(4, GL_UNSIGNED_BYTE, 16, 16L);
            // TexCoords alone appear to fit (300 + 3*32 + 8 = 404 <= 512), but with first=4 it reads to byte 532 > 512.
            GlsmFrontend.celtium$texCoordPointer(2, GL_FLOAT, 32, 300L);

            GlsmFrontend.celtium$drawArrays(GL_QUADS, 4, 4);

            assertEquals(0, backend.submissions.size(),
                    "split-array draw must reject texCoord range that crosses VBO end once first*texStride is included");

            // Overflowing offset must also be rejected cleanly without wrapping negative.
            GlsmFrontend.celtium$texCoordPointer(2, GL_FLOAT, 32, Long.MAX_VALUE - 16L);
            GlsmFrontend.celtium$drawArrays(GL_QUADS, 0, 4);
            assertEquals(0, backend.submissions.size(),
                    "split-array draw must reject overflowing texCoord offset");
        } finally {
            NativeGeometryBuffer.unbind();
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(previousBackend);
            this.installedField.set(null, previousStub);
            MemoryUtil.memFree(large);
            MemoryUtil.memFree(logical);
        }
    }

    @Test
    void drawsValidSplitArrayWithNonZeroFirstWithinBounds() throws Exception {
        Object previousStub = this.installedField.get(null);
        var previousBackend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        RecordingBackend backend = new RecordingBackend();
        this.installedField.set(null, this.stub);
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(backend);

        ByteBuffer buf = MemoryUtil.memAlloc(512);
        try {
            // Vertex 2 (at first=2): position x at 2*48=96, color at 16+2*16=48, texCoord u at 200+2*32=264.
            long addr = MemoryUtil.memAddress(buf);
            MemoryUtil.memPutFloat(addr + 96L, 7.5f);
            MemoryUtil.memPutInt(addr + 48L, 0x11223344);
            MemoryUtil.memPutFloat(addr + 264L, 0.75f);

            genAndUpload(buf);
            call("glEnableClientState", GL_VERTEX_ARRAY);
            call("glEnableClientState", GL_NORMAL_ARRAY);
            call("glEnableClientState", GL_COLOR_ARRAY);
            call("glEnableClientState", GL_TEXTURE_COORD_ARRAY);

            call("glVertexPointer", 3, GL_FLOAT, 48, 0L);
            GlsmFrontend.celtium$colorPointer(4, GL_UNSIGNED_BYTE, 16, 16L);
            GlsmFrontend.celtium$texCoordPointer(2, GL_FLOAT, 32, 200L);

            GlsmFrontend.celtium$drawArrays(GL_QUADS, 2, 2);

            assertEquals(1, backend.submissions.size());
            RecordingBackend.Submission sub = backend.submissions.get(0);
            assertEquals(24, sub.stride());
            assertEquals(2, sub.vertexCount());
            assertEquals(7.5f, sub.firstX());
            assertEquals(0.75f, sub.firstU());
            assertEquals(0x11223344, sub.firstColor());
        } finally {
            NativeGeometryBuffer.unbind();
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(previousBackend);
            this.installedField.set(null, previousStub);
            MemoryUtil.memFree(buf);
        }
    }
}
