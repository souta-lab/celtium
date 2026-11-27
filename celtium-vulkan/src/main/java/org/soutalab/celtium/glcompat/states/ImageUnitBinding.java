package org.soutalab.celtium.glcompat.states;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

public class ImageUnitBinding implements ISettableState<ImageUnitBinding> {
     protected int texture;
     protected int level;
     protected boolean layered;
     protected int layer;
     protected int access = GL15.GL_READ_ONLY;
     protected int format = GL30.GL_R8;

    public void setBinding(int texture, int level, boolean layered, int layer, int access, int format) {
        this.texture = texture;
        this.level = level;
        this.layered = layered;
        this.layer = layer;
        this.access = access;
        this.format = format;
    }

    /** Restores the binding a freshly allocated image unit reports. */
    public void reset() {
        setBinding(0, 0, false, 0, GL15.GL_READ_ONLY, GL30.GL_R8);
    }

    @Override
    public ImageUnitBinding set(ImageUnitBinding state) {
        this.texture = state.texture;
        this.level = state.level;
        this.layered = state.layered;
        this.layer = state.layer;
        this.access = state.access;
        this.format = state.format;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof ImageUnitBinding other)) return false;
        return texture == other.texture && level == other.level && layered == other.layered && layer == other.layer && access == other.access && format == other.format;
    }

    @Override
    public ImageUnitBinding copy() {
        return new ImageUnitBinding().set(this);
    }

    // -- explicit accessors (delomboked during the S4 native conversion) --
    public int getTexture() { return texture; }
    public int getLevel() { return level; }
    public boolean isLayered() { return layered; }
    public int getLayer() { return layer; }
    public int getAccess() { return access; }
    public int getFormat() { return format; }
}
