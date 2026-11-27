package org.soutalab.celtium.core.shader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The shader-side contracts of interface 1 and 2 (design section 2.3): the closed pass vocabulary,
 * SPIR-V bundle validity, program/blend consistency, and plan immutability.
 */
class ShaderContractTest {

    private static int[] spirv(int... extra) {
        int[] words = new int[1 + extra.length];
        words[0] = SpirvModuleBundle.SPIRV_MAGIC;
        System.arraycopy(extra, 0, words, 1, extra.length);
        return words;
    }

    @Test
    void terrainPassKindIsTheClosedSixValueSet() {
        assertEquals(6, TerrainPassKind.values().length);
        assertEquals(List.of(
                        TerrainPassKind.MAIN_SOLID, TerrainPassKind.MAIN_CUTOUT, TerrainPassKind.MAIN_TRANSLUCENT,
                        TerrainPassKind.SHADOW_SOLID, TerrainPassKind.SHADOW_CUTOUT, TerrainPassKind.SHADOW_TRANSLUCENT),
                List.of(TerrainPassKind.values()));
        assertTrue(TerrainPassKind.MAIN_SOLID.isMain());
        assertTrue(TerrainPassKind.SHADOW_TRANSLUCENT.isShadow());
        assertFalse(TerrainPassKind.MAIN_TRANSLUCENT.isShadow());
    }

    @Test
    void spirvBundleValidatesMagicAndCopies() {
        int[] vertex = spirv(1, 2, 3);
        SpirvModuleBundle bundle = SpirvModuleBundle.of(vertex, spirv(4));
        vertex[1] = 99;
        assertEquals(1, bundle.vertexWords()[1]);
        assertFalse(bundle.hasGeometry());
        assertThrows(IllegalArgumentException.class,
                () -> SpirvModuleBundle.of(new int[]{0x1234}, spirv()));
        assertThrows(IllegalArgumentException.class,
                () -> SpirvModuleBundle.of(new int[0], spirv()));
        SpirvModuleBundle withGeometry = new SpirvModuleBundle(spirv(), spirv(), spirv(7));
        assertTrue(withGeometry.hasGeometry());
        assertEquals(7, withGeometry.geometry().orElseThrow()[1]);
    }

    @Test
    void terrainStageProgramRejectsBlendFormatMismatch() {
        SpirvModuleBundle bundle = SpirvModuleBundle.of(spirv(), spirv());
        AttachmentFormatKey oneColor = AttachmentFormatKey.of(new int[]{37}, 0, 0);
        TerrainStageProgram program = new TerrainStageProgram(
                TerrainPassKind.MAIN_SOLID, bundle, oneColor, BlendStateSpec.opaque(1),
                0.0f, true, true, DescriptorBindingPlan.empty());
        assertEquals(TerrainPassKind.MAIN_SOLID, program.passKind());
        assertThrows(IllegalArgumentException.class, () -> new TerrainStageProgram(
                TerrainPassKind.MAIN_SOLID, bundle, oneColor, BlendStateSpec.opaque(2),
                0.0f, true, true, DescriptorBindingPlan.empty()));
        assertThrows(NullPointerException.class, () -> new TerrainStageProgram(
                TerrainPassKind.MAIN_SOLID, null, oneColor, BlendStateSpec.opaque(1),
                0.0f, true, true, DescriptorBindingPlan.empty()));
    }

    @Test
    void descriptorBindingPlanFindsBindings() {
        DescriptorBindingPlan.Binding ubo = new DescriptorBindingPlan.Binding(
                0, DescriptorBindingPlan.Kind.UNIFORM_BUFFER,
                EnumSet.of(ShaderStage.VERTEX, ShaderStage.FRAGMENT), 1);
        DescriptorBindingPlan plan = new DescriptorBindingPlan(List.of(ubo));
        assertTrue(plan.find(0).isPresent());
        assertTrue(plan.find(1).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new DescriptorBindingPlan.Binding(
                0, DescriptorBindingPlan.Kind.SAMPLER, EnumSet.noneOf(ShaderStage.class), 1));
    }

    @Test
    void vertexRequirementsAuxiliaryBytesFollowMcEntity() {
        assertEquals(0, TerrainVertexRequirements.NONE.auxiliaryBytesPerQuad());
        assertFalse(TerrainVertexRequirements.NONE.needsAuxiliaryColumn());
        TerrainVertexRequirements withEntity = new TerrainVertexRequirements(true, true, false, false);
        assertEquals(2, withEntity.auxiliaryBytesPerQuad());
        assertTrue(withEntity.needsAuxiliaryColumn());
    }

    @Test
    void packExecutionPlanIsImmutableAndResolvesNames() {
        List<PackExecutionPlan.RenderTargetSpec> targets = new ArrayList<>();
        targets.add(new PackExecutionPlan.RenderTargetSpec(
                "colortex0", PackExecutionPlan.RenderTargetSpec.Kind.COLOR_TEXTURE,
                37, 1.0f, false, new float[]{0f, 0f, 0f, 1f}));
        List<PackExecutionPlan.OrderedPass> passes = new ArrayList<>();
        passes.add(new PackExecutionPlan.OrderedPass.FinalPass(
                "final", SpirvModuleBundle.of(spirv(), spirv()),
                List.of("colortex0"), PackExecutionPlan.BarrierSpec.NONE));
        PackExecutionPlan plan = new PackExecutionPlan(
                targets, new PackExecutionPlan.PackUniformLayout(List.of(), 0), passes);
        targets.clear();
        passes.clear();
        assertEquals(1, plan.renderTargets().size());
        assertEquals(1, plan.passes().size());
        assertTrue(plan.targetNamed("colortex0").isPresent());
        assertTrue(plan.targetNamed("depthtex0").isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> plan.passes().add(plan.passes().get(0)));
        PackExecutionPlan.OrderedPass finalPass = plan.passes().get(0);
        assertEquals(List.of("swapchain"), finalPass.outputTargets());
        assertTrue(finalPass.program().isPresent());
    }
}
