package org.soutalab.celtium.glcompat.stacks;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.GLStateManager;
import org.soutalab.celtium.glcompat.backend.DirectGlExecutor;
import org.lwjgl.opengl.GL13;

public final class TextureUnitBooleanStateStack extends BooleanStateStack {
    private final int unitIndex;

    public TextureUnitBooleanStateStack(int glCap, int unitIndex) {
        super(glCap, false, true);
        this.unitIndex = unitIndex;
    }

    public TextureUnitBooleanStateStack(int glCap, int unitIndex, boolean initialState) {
        super(glCap, initialState, true);
        this.unitIndex = unitIndex;
    }

    TextureUnitBooleanStateStack(int glCap, int unitIndex, boolean initialState, boolean ffpStateOnly) {
        super(glCap, initialState, ffpStateOnly);
        this.unitIndex = unitIndex;
    }

    @Override
    public void setEnabled(boolean enabled) {
        GLStateManager.recordExitCapability(cowDepths().owner, glCap, unitIndex);
        if (!GLStateManager.isCachingEnabled() || enabled != this.enabled) {
            GLStateManager.beforeUncapturedTextureUnitChange(glCap, unitIndex);
            beforeModify();
        }
        setEnabledWithUnitSwitch(enabled);
    }

    @Override
    public void restoreSlot(int slot) {
        setEnabledWithUnitSwitch(stack[slot]);
    }

    private void setEnabledWithUnitSwitch(boolean enabled) {
        final boolean bypass = GLStateManager.shouldBypassCache();
        if (bypass || enabled != this.enabled) {
            if (!bypass) this.enabled = enabled;

            if (!ffpStateOnly) {
                final int currentUnit = GLStateManager.getActiveTextureUnit();
                final boolean needsSwitch = currentUnit != unitIndex;

                if (needsSwitch) DirectGlExecutor.EXECUTOR.activeTexture(GL13.GL_TEXTURE0 + unitIndex);
                if (enabled) DirectGlExecutor.EXECUTOR.enable(glCap); else DirectGlExecutor.EXECUTOR.disable(glCap);
                if (needsSwitch) DirectGlExecutor.EXECUTOR.activeTexture(GL13.GL_TEXTURE0 + currentUnit);
            }
        }
    }

    public int getUnitIndex() {
        return unitIndex;
    }
}
