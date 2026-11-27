package org.soutalab.celtium.glcompat.context;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


/** Exposes the native handle of a transformed LWJGL context. */
public interface NativeContextHandle {
    /** Returns the native window or SDL context handle owned by this context. */
    long actinium$nativeHandle();
}
