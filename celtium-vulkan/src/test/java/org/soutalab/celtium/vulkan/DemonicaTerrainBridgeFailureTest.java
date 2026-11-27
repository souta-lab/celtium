package org.soutalab.celtium.vulkan;

import net.coderbot.iris.shadows.ShadowRenderingState;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DP-06: the Demonica/Iris reflection bridge must not collapse a failure into the
 * same silent {@code false} / {@code null} as a genuine negative. The guest
 * classes are test stubs (see {@link ShadowRenderingState} and
 * {@code com.demonica.celeritas.terrain.ShaderTerrain} in the test sources), so
 * each outcome - absent guest, answering guest, throwing guest, methodless
 * guest - is driven behaviourally rather than read off the source.
 */
class DemonicaTerrainBridgeFailureTest {

    private static final String SHADOW_CLASS = "net.coderbot.iris.shadows.ShadowRenderingState";
    private static final String TERRAIN_CLASS = "com.demonica.celeritas.terrain.ShaderTerrain";
    private static final String ABSENT_CLASS = "org.soutalab.celtium.vulkan.NoSuchGuestClass";
    private static final String METHODLESS_CLASS =
            "org.soutalab.celtium.vulkan.DemonicaTerrainBridgeFailureTest$MethodlessGuest";

    /** A guest-shaped class with neither bridge method: binding it must fail visibly. */
    static final class MethodlessGuest {
        private MethodlessGuest() {
        }
    }

    @BeforeEach
    void resetBridge() {
        ShadowRenderingState.resetStub();
        com.demonica.celeritas.terrain.ShaderTerrain.resetStub();
        DemonicaTerrainBridge.celtium$resetForTests(SHADOW_CLASS, TERRAIN_CLASS);
    }

    @AfterEach
    void restoreBridge() {
        resetBridge();
    }

    private static TerrainRenderPass testPass() {
        return TerrainRenderPass.builder()
                .name("bridge-test")
                .pipelineState(new TerrainRenderPass.PipelineState() {
                    @Override public void setup() { }
                    @Override public void clear() { }
                })
                .vertexType(ChunkMeshFormats.COMPACT)
                .primitiveType(org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType.TRIANGULATED)
                .build();
    }

    private static RenderPassConfiguration<?> testConfiguration(TerrainRenderPass pass) {
        return new RenderPassConfiguration<String>(
                Map.of(), Map.of("stage", List.of(pass)), null, null, null);
    }

    @Test
    void anAbsentGuestIsNotABridgeFailure() {
        DemonicaTerrainBridge.celtium$resetForTests(ABSENT_CLASS, ABSENT_CLASS);

        assertFalse(DemonicaTerrainBridge.shadowsBeingRendered(),
                "no Iris installed: the answer is false");
        TerrainRenderPass pass = testPass();
        assertNull(DemonicaTerrainBridge.packProgram(pass, testConfiguration(pass)),
                "no Demonica installed: the answer is null");
        assertEquals(0L, DemonicaTerrainBridge.resolveFailures(),
                "an absent guest is a vanilla run, not a bridge failure");
        assertEquals(0L, DemonicaTerrainBridge.invokeFailures());
        assertTrue(DemonicaTerrainBridge.failureSummary().contains("lastFailure=none"),
                DemonicaTerrainBridge.failureSummary());
    }

    @Test
    void anAnsweringGuestIsUsedAndNotCounted() {
        ShadowRenderingState.answer = true;

        assertTrue(DemonicaTerrainBridge.shadowsBeingRendered(),
                "the guest's own answer is returned");
        TerrainRenderPass pass = testPass();
        assertNull(DemonicaTerrainBridge.packProgram(pass, testConfiguration(pass)),
                "the stub guest answers null (no pack program) without failing");
        assertEquals(0L, DemonicaTerrainBridge.resolveFailures());
        assertEquals(0L, DemonicaTerrainBridge.invokeFailures());
    }

    @Test
    void aThrowingShadowGuestIsCountedNotCollapsed() {
        ShadowRenderingState.throwOnCall = true;

        assertFalse(DemonicaTerrainBridge.shadowsBeingRendered(),
                "the caller still gets its boolean answer");
        assertEquals(1L, DemonicaTerrainBridge.invokeFailures(),
                "but the failure is counted, not collapsed into the false");
        assertTrue(DemonicaTerrainBridge.failureSummary().contains("shadow-state"),
                DemonicaTerrainBridge.failureSummary());
    }

    @Test
    void aThrowingPackGuestIsCountedNotCollapsed() {
        com.demonica.celeritas.terrain.ShaderTerrain.throwOnCall = true;

        TerrainRenderPass pass = testPass();
        assertNull(DemonicaTerrainBridge.packProgram(pass, testConfiguration(pass)),
                "the caller still gets its null answer");
        assertEquals(1L, DemonicaTerrainBridge.invokeFailures(),
                "but the failure is counted, not collapsed into the null");
        assertTrue(DemonicaTerrainBridge.failureSummary().contains("pack-program"),
                DemonicaTerrainBridge.failureSummary());
    }

    @Test
    void aMethodlessGuestIsAResolutionFailure() {
        DemonicaTerrainBridge.celtium$resetForTests(METHODLESS_CLASS, METHODLESS_CLASS);

        assertFalse(DemonicaTerrainBridge.shadowsBeingRendered());
        assertEquals(1L, DemonicaTerrainBridge.resolveFailures(),
                "the class exists but its method is gone: a bridge failure, counted");
        TerrainRenderPass pass = testPass();
        assertNull(DemonicaTerrainBridge.packProgram(pass, testConfiguration(pass)));
        assertEquals(2L, DemonicaTerrainBridge.resolveFailures(),
                "each bridge half counts its own resolution failure");
        assertEquals(0L, DemonicaTerrainBridge.invokeFailures(),
                "nothing was ever invoked");
    }
}
