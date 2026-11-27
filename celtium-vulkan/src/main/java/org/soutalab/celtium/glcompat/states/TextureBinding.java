package org.soutalab.celtium.glcompat.states;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.texture.TextureInfo;
import org.soutalab.celtium.glcompat.texture.TextureInfoCache;

public class TextureBinding implements ISettableState<TextureBinding> {
     protected int binding;
     protected int target;
    protected TextureInfo info;

    public void setBinding(int binding) {
        setBinding(binding, 0);
    }

    public void setBinding(int binding, int target) {
        this.binding = binding;
        this.target = target;
        this.info = null;
    }

    public TextureInfo getOrResolveInfo() {
        TextureInfo resolved = info;
        if (resolved == null) {
            resolved = TextureInfoCache.INSTANCE.getInfo(binding);
            info = resolved;
        }
        return resolved;
    }

    @Override
    public TextureBinding set(TextureBinding state) {
        this.binding = state.binding;
        this.target = state.target;
        this.info = null;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof TextureBinding textureBinding)) return false;
        return binding == textureBinding.binding;
    }
    @Override
    public TextureBinding copy() {
        return new TextureBinding().set(this);
    }

    // -- explicit accessors (delomboked during the S4 native conversion) --
    public int getBinding() { return binding; }
    public int getTarget() { return target; }
    public TextureInfo getInfo() { return info; }
}
