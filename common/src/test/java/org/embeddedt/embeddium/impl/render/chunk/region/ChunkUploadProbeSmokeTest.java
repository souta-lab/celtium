package org.embeddedt.embeddium.impl.render.chunk.region;

import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.MeshBufferSegment;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.taumc.celeritas.lwjgl.FakeGlLWJGLService;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #422 C4 probe smoke: the storage hooks execute against a real {@link SectionRenderDataStorage} on
 * the fake LWJGL heap, and the row read-back check fires when the segment it is handed disagrees with the
 * segment the row was written from. The probe's ENABLED flag is read from the system property at class
 * load, so the anomaly assertion calls verifyRow directly - it runs in both flag states; a flag-on run
 * additionally exercises the guarded call sites in the storage.
 */
class ChunkUploadProbeSmokeTest {
    private static final class StubSegment implements MeshBufferSegment {
        private final int offset;
        private final int length;

        StubSegment(int offset, int length) {
            this.offset = offset;
            this.length = length;
        }

        @Override
        public int getOffset() {
            return this.offset;
        }

        @Override
        public int getLength() {
            return this.length;
        }

        @Override
        public void delete() {
        }
    }

    private SectionRenderDataStorage compact;
    private SectionRenderDataStorage full;

    @BeforeEach
    void setUp() {
        FakeGlLWJGLService.reset();
        this.compact = new SectionRenderDataStorage(QuadPrimitiveType.TRIANGULATED, false, 1);
        this.full = new SectionRenderDataStorage(QuadPrimitiveType.TRIANGULATED, true, 1);
    }

    @AfterEach
    void tearDown() {
        this.compact.delete();
        this.full.delete();
    }

    private static Map<ModelQuadFacing, VertexRange> ranges() {
        Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        ranges.put(ModelQuadFacing.NEG_Y, new VertexRange(0, 4));
        ranges.put(ModelQuadFacing.POS_Y, new VertexRange(4, 8));
        return ranges;
    }

    @Test
    void enabledFlagMirrorsTheSystemProperty() {
        assertEquals(Boolean.getBoolean("celtium.issue422.c4probe"), ChunkUploadProbe.ENABLED);
        ChunkUploadProbe.beginBurst();
        ChunkUploadProbe.forgetAll();
    }

    @Test
    void storageHooksRunOnSetMeshesAndReplaceIndexBuffer() {
        this.compact.setMeshes(0, new StubSegment(10, 100), null, ranges());
        this.compact.removeMeshes(0);

        this.full.setMeshes(0, new StubSegment(10, 100), new StubSegment(20, 50), ranges());
        this.full.replaceIndexBuffer(0, new StubSegment(30, 50));
        this.full.removeMeshes(0);
    }

    @Test
    void rowReadbackFlagsASegmentThatDisagreesWithTheWrittenRow() {
        Map<ModelQuadFacing, VertexRange> ranges = ranges();
        this.compact.setMeshes(0, new StubSegment(10, 100), null, ranges());

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            ChunkUploadProbe.verifyRow(this.compact, 0, new StubSegment(11, 100), null, ranges, 1,
                    SectionRenderDataUnsafe.Strategy.COMPACT, QuadPrimitiveType.TRIANGULATED);
        } finally {
            System.setOut(original);
        }
        String out = captured.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("CELTIUM_C4PROBE ANOMALY type=row-vertex-offset"), out);
    }
}
