package org.taumc.celeritas.impl.render.terrain.compile.pipeline;

import org.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wiring contracts of the native leaf culling (III-1), read from the compiled classes -
 * the same bytes Mixin and the renderer run, in the shape {@code DualFormHookNamesTest}
 * established: the mixin names its target in both the MCP and the SRG form (a single-form
 * hook is silently unbound in the other environment), the decision and census calls are
 * present at their seats, and the option's default is SOLID.
 */
class LeafCullingContractTest {
    private static String classText(String resource) throws IOException {
        try (InputStream in = LeafCullingContractTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            // ISO-8859-1 keeps bytes and chars 1:1, so a substring search over the decoded
            // text is a byte search over the class file's constant pool.
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    @Test
    void mixinNamesBothFormsOfTheTargetMethod() throws IOException {
        String mixin = classText("/org/taumc/celeritas/mixin/core/terrain/BlockLeavesMixin.class");
        assertTrue(mixin.contains("shouldSideBeRendered"), "MCP form of the target method");
        assertTrue(mixin.contains("func_176225_a"), "SRG form of the target method");
        assertTrue(mixin.contains("net/minecraft/block/BlockLeaves"), "mixin target class");
    }

    @Test
    void mixinDelegatesToTheCoreAndCountsSuppressions() throws IOException {
        String mixin = classText("/org/taumc/celeritas/mixin/core/terrain/BlockLeavesMixin.class");
        assertTrue(mixin.contains("shouldSuppressFace"), "the suppression decision is the core's");
        assertTrue(mixin.contains("recordSuppressedFace"), "suppressions reach the census");
        assertTrue(mixin.contains("leafCullingMode"), "the mode comes from the option");
    }

    @Test
    void rendererTakesTheSwapDecisionAtItsSeat() throws IOException {
        String renderer = classText("/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.class");
        assertTrue(renderer.contains("shouldSolidify"), "renderModel consults the swap decision");
        assertTrue(renderer.contains("shaderPackInUse"), "the swap consults the pack signal");
        assertTrue(renderer.contains("recordSolidifiedBlock"), "solidifications reach the census");
    }

    @Test
    void censusCarriesItsTagAndProperty() throws IOException {
        String census = classText("/org/taumc/celeritas/impl/render/terrain/compile/pipeline/LeafCullingCensus.class");
        assertTrue(census.contains("CELTIUM_LEAF_CULLING"));
        assertTrue(census.contains("celtium.debug.leafCullingCensus"));
        assertTrue(census.contains("suppressedFaces="));
        assertTrue(census.contains("solidifiedBlocks="));
    }

    @Test
    void defaultModeIsSolidAndNoneExists() {
        SodiumGameOptions.QualitySettings quality = new SodiumGameOptions.QualitySettings();
        assertEquals(SodiumGameOptions.LeafCullingMode.SOLID, quality.leafCullingMode);
        assertEquals(4, SodiumGameOptions.LeafCullingMode.values().length);
    }
}
