package org.soutalab.celtium.glcompat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.hooks.GLSMHooks;
import org.soutalab.celtium.glcompat.stacks.BooleanStateStack;
import org.lwjgl.opengl.GL11;

public final class ClipPlaneBooleanState extends BooleanStateStack {
    private final GLContextState owner;
    private final int index;

    ClipPlaneBooleanState(GLContextState owner, int index, int glCap) {
        super(glCap);
        this.owner = owner;
        this.index = index;
    }

    static boolean isClipPlane(int cap) {
        return cap >= GL11.GL_CLIP_PLANE0 && cap < GL11.GL_CLIP_PLANE0 + GLStateManager.MAX_CLIP_PLANES;
    }

    @Override
    protected boolean tracksStateWhenCacheBypassed() {
        // Both shader paths use the mask to select the plane equations to upload.
        return true;
    }

    @Override
    protected boolean forwardsToBackend() {
        return !RenderSystem.isClipDistanceEmulated();
    }

    @Override
    protected void onEnabledChanged() {
        final int before = owner.clipPlaneEnabledMask;
        if (isEnabled()) {
            owner.clipPlaneEnabledMask |= 1 << index;
        } else {
            owner.clipPlaneEnabledMask &= ~(1 << index);
        }
        if (before != owner.clipPlaneEnabledMask) {
            owner.clipPlaneGeneration++;
        }
    }
}
