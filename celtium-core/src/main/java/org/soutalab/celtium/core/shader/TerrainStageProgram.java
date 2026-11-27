package org.soutalab.celtium.core.shader;

import java.util.Objects;

/**
 * One terrain pass's fully-resolved program (design V040-FINAL-PLAN section 2.3, interface 1): the
 * native replacement for the old supplier-shaped {@code PassInfo} + {@code GlProgram} pair. By the
 * time a program exists in this form, every decision the pack left open has been taken - the SPIR-V
 * is already translated to celtium's 16 B / 64 B (+ mc_Entity) vertex inputs, the blend state already
 * reflects the pack's stained-glass depth rule, the alpha threshold already carries the pass
 * default (solid 0.0, cutout 0.1, translucent 0.0001) or the pack's override. The HAL only builds
 * pipelines from it; it decides nothing.
 */
public record TerrainStageProgram(
        TerrainPassKind passKind,
        SpirvModuleBundle spirv,
        AttachmentFormatKey targetFormats,
        BlendStateSpec blendSpec,
        float alphaDiscardThreshold,
        boolean depthWrite,
        boolean faceCullEnabled,
        DescriptorBindingPlan bindings) {

    public TerrainStageProgram {
        Objects.requireNonNull(passKind, "passKind");
        Objects.requireNonNull(spirv, "spirv");
        Objects.requireNonNull(targetFormats, "targetFormats");
        Objects.requireNonNull(blendSpec, "blendSpec");
        Objects.requireNonNull(bindings, "bindings");
        if (blendSpec.attachments().size() != targetFormats.colorAttachmentCount()) {
            throw new IllegalArgumentException("blend spec covers " + blendSpec.attachments().size()
                    + " attachments but the target formats declare "
                    + targetFormats.colorAttachmentCount());
        }
    }
}
