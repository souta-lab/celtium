package org.embeddedt.embeddium.impl.render.chunk;

import org.embeddedt.embeddium.impl.render.ShaderModBridge;
import org.embeddedt.embeddium.impl.render.chunk.shader.TerrainPipelineConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the shadow-pass construction decision (item 419, the fix for the gate item 409 found): a section manager is
 * built with a shadow pass when the core config explicitly requests one, or when a shader pack is active through the
 * shader bridge - and not otherwise, so a pack-less build constructs exactly as it did before the gate was wired.
 * <p>
 * The test JVM carries no shader mod, so the bridge half is deterministically false here; the pack-active half is
 * covered by the fixture leg reading the construction-time {@code CELTIUM_SECTION_MANAGER} line, not by this test.
 */
class TerrainPipelineConfigShadowPassTest {

    @AfterEach
    void resetRequest() {
        TerrainPipelineConfig.setShadowPassRequested(false);
    }

    @Test
    void noRequestAndNoShaderModMeansNoShadowPass() {
        assertFalse(ShaderModBridge.areShadersEnabled(), "the test classpath must not carry a shader mod");
        assertFalse(TerrainPipelineConfig.isShadowPassRequested());
        assertFalse(TerrainPipelineConfig.isShadowPassEnabled());
    }

    @Test
    void explicitRequestEnablesShadowPass() {
        TerrainPipelineConfig.setShadowPassRequested(true);
        assertTrue(TerrainPipelineConfig.isShadowPassEnabled());
    }

    @Test
    void clearingTheRequestDisablesItAgain() {
        TerrainPipelineConfig.setShadowPassRequested(true);
        assertTrue(TerrainPipelineConfig.isShadowPassEnabled());
        TerrainPipelineConfig.setShadowPassRequested(false);
        assertFalse(TerrainPipelineConfig.isShadowPassEnabled());
    }
}
