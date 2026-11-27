package org.soutalab.celtium.glcompat.hooks;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.shader.UniformType;

import java.util.List;

/**
 * Shared per-frame uniform block for SDL GPU. Produced by Iris once per shader pack load. Every shader declares the
 * whole block regardless of which members it reads.
 */
public record PerFrameUniformBlock(List<Member> members) {

    public record Member(String name, UniformType type) {}

    public boolean isEmpty() {
        return members.isEmpty();
    }
}
