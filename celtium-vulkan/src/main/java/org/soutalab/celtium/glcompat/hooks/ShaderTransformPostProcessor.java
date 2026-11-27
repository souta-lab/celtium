package org.soutalab.celtium.glcompat.hooks;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.shader.ShaderType;
import org.taumc.glsl.grammar.GLSLParser;

@FunctionalInterface
public interface ShaderTransformPostProcessor {
    void onTransformed(String transformedSource, ShaderType shaderType);

    default void onTransformed(String transformedSource, GLSLParser.Translation_unitContext bodyTree, int headerLen, ShaderType shaderType) {
        onTransformed(transformedSource, shaderType);
    }
}
