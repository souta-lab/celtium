package org.soutalab.celtium.glcompat.states;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


public interface ISettableState<T> extends Cloneable {
    T set(T state);
    @SuppressWarnings("unchecked")
    default void set(ISettableState<?> value) {
        set((T) value);
    }

    boolean sameAs(Object state);

    T copy();

}

