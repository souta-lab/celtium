package org.soutalab.celtium.vulkan.gl;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
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
 * {@code glDrawElements} must resolve stub-owned VBOs from {@code bufferStore} and enforce shrunk
 * {@code bufferLogicalSize} and overflow-safe bounds on both the vertex buffer and the element buffer.
 */
class DrawElementsBufferBoundsTest {
    private static final int GL_TRIANGLES = 0x0004;
    private static final int GL_UNSIGNED_INT = 0x1405;
    private static final int GL_FLOAT = 0x1406;
    private static final int GL_VERTEX_ARRAY = 0x8074;
    private static final int GL_ARRAY_BUFFER = 0x8892;
    private static final int GL_ELEMENT_ARRAY_BUFFER = 0x8893;
    private static final int GL_STATIC_DRAW = 0x88E4;

    private static final class RecordingBackend
            implements org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend {
        record Submission(int stride, int formatId, int vertexCount, int mode, float[] xCoords) {}

        final List<Submission> submissions = new ArrayList<>();

        @Override
        public String getName() {
            return "test-recording";
        }

        @Override
        public void render(ChunkRenderMatrices matrices, ChunkRenderListIterable renderLists, TerrainRenderPass pass,
                           org.embeddedt.embeddium.impl.render.viewport.CameraTransform occlusionCamera,
                           org.embeddedt.embeddium.impl.render.viewport.CameraTransform camera, Object view, boolean profile) {
        }

        @Override
        public boolean submitImmediateBatch(Object vertexFormat, int vertexStride, int formatId, long vertexAddress,
                                            int vertexCount, int primitiveMode) {
            float[] xCoords = new float[vertexCount];
            for (int i = 0; i < vertexCount; i++) {
                xCoords[i] = MemoryUtil.memGetFloat(vertexAddress + (long) i * vertexStride);
            }
            this.submissions.add(new Submission(vertexStride, formatId, vertexCount, primitiveMode, xCoords));
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

    DrawElementsBufferBoundsTest() throws Exception {
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
        resetSharedGlState();
    }

    /**
     * The routes rewiring moved the capability record, the legacy vertex-pointer tuple and the
     * buffer bindings into singleton owners (GlsmEnableState / GlsmClientArrayState /
     * GlsmBufferState), where they outlive a stub instance. A fresh stub used to start from GL's
     * initial values; restore that here so another test's client state cannot describe this
     * test's draws.
     */
    private static void resetSharedGlState() {
        GlsmEnableState.current().set(GL_VERTEX_ARRAY, false);
        GlsmEnableState.current().set(0x8075, false); // GL_NORMAL_ARRAY
        GlsmEnableState.current().set(0x8076, false); // GL_COLOR_ARRAY
        GlsmEnableState.current().set(0x8078, false); // GL_TEXTURE_COORD_ARRAY
        GlsmEnableState.current().set(0x8622, false); // the attribute-0 enable mirror
        GlsmClientArrayState.Pointer vertexPointer =
                GlsmClientArrayState.current().clientPointer(GL_VERTEX_ARRAY, true);
        vertexPointer.size = 4;
        vertexPointer.type = GL_FLOAT;
        vertexPointer.stride = 0;
        vertexPointer.offset = 0L;
        vertexPointer.buffer = 0;
        vertexPointer.enabled = false;
        vertexPointer.normalized = false;
        vertexPointer.integer = false;
        GlsmBufferState.current().bind(GL_ARRAY_BUFFER, 0);
        GlsmBufferState.current().bind(GL_ELEMENT_ARRAY_BUFFER, 0);
        NativeGeometryBuffer.unbind();
    }

    private long call(String name, long... args) throws Exception {
        long[] slots = new long[16];
        System.arraycopy(args, 0, slots, 0, args.length);
        return (Long) this.dispatch.invoke(this.stub, this.ids.get(name), slots);
    }

    private int genBuffer() throws Exception {
        ByteBuffer nameBuf = MemoryUtil.memAlloc(4);
        try {
            call("glGenBuffers", 1, MemoryUtil.memAddress(nameBuf));
            return nameBuf.getInt(0);
        } finally {
            MemoryUtil.memFree(nameBuf);
        }
    }

    private void uploadBuffer(int target, int name, ByteBuffer data) throws Exception {
        call("glBindBuffer", target, name);
        call("glBufferData", target, data.capacity(), MemoryUtil.memAddress(data), GL_STATIC_DRAW);
    }

    @Test
    void drawsIndexedVerticesFromStubOwnedArrayAndElementBuffers() throws Exception {
        Object previousStub = this.installedField.get(null);
        var previousBackend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        RecordingBackend backend = new RecordingBackend();
        this.installedField.set(null, this.stub);
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(backend);

        ByteBuffer vboData = MemoryUtil.memAlloc(3 * 12);
        ByteBuffer eboData = MemoryUtil.memAlloc(3 * 4);
        try {
            for (int i = 0; i < 3; i++) {
                vboData.putFloat(i * 12, 10.0f + i);
            }
            eboData.putInt(0, 2).putInt(4, 0).putInt(8, 1);

            int vbo = genBuffer();
            int ebo = genBuffer();
            uploadBuffer(GL_ARRAY_BUFFER, vbo, vboData);
            uploadBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo, eboData);

            call("glEnableClientState", GL_VERTEX_ARRAY);
            call("glVertexPointer", 3, GL_FLOAT, 12, 0L);

            GlsmFrontend.celtium$drawElements(GL_TRIANGLES, 3, GL_UNSIGNED_INT, 0L);

            assertEquals(1, backend.submissions.size(),
                    "glDrawElements with a stub-owned VBO must submit the expanded batch instead of treating it as null client memory");
            RecordingBackend.Submission sub = backend.submissions.get(0);
            assertEquals(3, sub.vertexCount());
            assertEquals(12.0f, sub.xCoords()[0]);
            assertEquals(10.0f, sub.xCoords()[1]);
            assertEquals(11.0f, sub.xCoords()[2]);
        } finally {
            NativeGeometryBuffer.unbind();
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(previousBackend);
            this.installedField.set(null, previousStub);
            MemoryUtil.memFree(vboData);
            MemoryUtil.memFree(eboData);
        }
    }

    @Test
    void refusesIndexedDrawPastShrunkVboOrEboLogicalSizeAndOverflow() throws Exception {
        Object previousStub = this.installedField.get(null);
        var previousBackend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        RecordingBackend backend = new RecordingBackend();
        this.installedField.set(null, this.stub);
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(backend);

        ByteBuffer largeVbo = MemoryUtil.memAlloc(64);
        ByteBuffer largeEbo = MemoryUtil.memAlloc(64);
        ByteBuffer shrunkVbo = MemoryUtil.memAlloc(24); // 2 vertices
        ByteBuffer shrunkEbo = MemoryUtil.memAlloc(8);  // 2 ints: indices 0, 2 (index 2 is out of shrunk VBO)
        ByteBuffer emptyClientSentinel = MemoryUtil.memAlloc(36);
        try {
            largeEbo.putInt(0, 0).putInt(4, 1).putInt(8, 0);
            shrunkEbo.putInt(0, 0).putInt(4, 2);

            int vbo = genBuffer();
            int ebo = genBuffer();
            uploadBuffer(GL_ARRAY_BUFFER, vbo, largeVbo);
            uploadBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo, largeEbo);

            // Shrink both buffers; backing ByteBuffers retain 64-byte capacity.
            uploadBuffer(GL_ARRAY_BUFFER, vbo, shrunkVbo);
            uploadBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo, shrunkEbo);

            call("glEnableClientState", GL_VERTEX_ARRAY);
            call("glVertexPointer", 3, GL_FLOAT, 12, 0L);

            // 1. Reading 3 indices (12 bytes) from an EBO shrunk to 8 bytes must be refused.
            GlsmFrontend.celtium$drawElements(GL_TRIANGLES, 3, GL_UNSIGNED_INT, 0L);
            assertEquals(0, backend.submissions.size(),
                    "glDrawElements must reject element range past shrunk EBO logical size");

            // 2. Reading an index that points past the shrunk VBO (index 2 in 2-vertex logical VBO) must be refused.
            GlsmFrontend.celtium$drawElements(GL_TRIANGLES, 2, GL_UNSIGNED_INT, 0L);
            assertEquals(0, backend.submissions.size(),
                    "glDrawElements must reject vertex index past shrunk VBO logical size");

            // 3. Overflowing EBO offset + byteSize must be refused.
            GlsmFrontend.celtium$drawElements(GL_TRIANGLES, 2, GL_UNSIGNED_INT, Long.MAX_VALUE - 4L);
            assertEquals(0, backend.submissions.size(),
                    "glDrawElements must reject overflowing EBO offset");

            // 4. Bound non-zero VBO without storage must not fall back to reading vertexPointerOffset as client memory.
            int emptyVbo = genBuffer();
            call("glBindBuffer", GL_ARRAY_BUFFER, emptyVbo);
            call("glVertexPointer", 3, GL_FLOAT, 12, MemoryUtil.memAddress(emptyClientSentinel));
            shrunkEbo.putInt(0, 0).putInt(4, 1);
            uploadBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo, shrunkEbo);
            GlsmFrontend.celtium$drawElements(GL_TRIANGLES, 2, GL_UNSIGNED_INT, 0L);
            assertEquals(0, backend.submissions.size(),
                    "glDrawElements with a bound non-zero VBO lacking storage must not read vertexPointerOffset as client memory");
        } finally {
            NativeGeometryBuffer.unbind();
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(previousBackend);
            this.installedField.set(null, previousStub);
            MemoryUtil.memFree(largeVbo);
            MemoryUtil.memFree(largeEbo);
            MemoryUtil.memFree(shrunkVbo);
            MemoryUtil.memFree(shrunkEbo);
            MemoryUtil.memFree(emptyClientSentinel);
        }
    }

    @Test
    void clientVerticesWithShrunkEboRefusesStaleRangeAndOverflow() throws Exception {
        Object previousStub = this.installedField.get(null);
        var previousBackend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        RecordingBackend backend = new RecordingBackend();
        this.installedField.set(null, this.stub);
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(backend);

        ByteBuffer clientVertices = MemoryUtil.memAlloc(3 * 12);
        ByteBuffer largeEbo = MemoryUtil.memAlloc(64);
        ByteBuffer shrunkEbo = MemoryUtil.memAlloc(8);
        try {
            for (int i = 0; i < 3; i++) {
                clientVertices.putFloat(i * 12, 20.0f + i);
            }
            largeEbo.putInt(0, 0).putInt(4, 1).putInt(8, 2);
            shrunkEbo.putInt(0, 2).putInt(4, 0);

            int ebo = genBuffer();
            uploadBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo, largeEbo);
            uploadBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo, shrunkEbo);

            call("glBindBuffer", GL_ARRAY_BUFFER, 0);
            call("glEnableClientState", GL_VERTEX_ARRAY);
            call("glVertexPointer", 3, GL_FLOAT, 12, MemoryUtil.memAddress(clientVertices));

            // 1. Reading 3 indices (12 bytes) from an EBO shrunk to 8 bytes must be refused.
            GlsmFrontend.celtium$drawElements(GL_TRIANGLES, 3, GL_UNSIGNED_INT, 0L);
            assertEquals(0, backend.submissions.size(),
                    "glDrawElements with client vertices must still reject EBO ranges past shrunk logical size");

            // 2. Overflowing EBO offset + byteSize must be refused.
            GlsmFrontend.celtium$drawElements(GL_TRIANGLES, 2, GL_UNSIGNED_INT, Long.MAX_VALUE - 4L);
            assertEquals(0, backend.submissions.size(),
                    "glDrawElements with client vertices must reject overflowing EBO offset");

            // 3. Valid range within the shrunk 8-byte EBO still draws.
            GlsmFrontend.celtium$drawElements(GL_TRIANGLES, 2, GL_UNSIGNED_INT, 0L);
            assertEquals(1, backend.submissions.size());
            assertEquals(22.0f, backend.submissions.get(0).xCoords()[0]);
            assertEquals(20.0f, backend.submissions.get(0).xCoords()[1]);
        } finally {
            NativeGeometryBuffer.unbind();
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(previousBackend);
            this.installedField.set(null, previousStub);
            MemoryUtil.memFree(clientVertices);
            MemoryUtil.memFree(largeEbo);
            MemoryUtil.memFree(shrunkEbo);
        }
    }
}
