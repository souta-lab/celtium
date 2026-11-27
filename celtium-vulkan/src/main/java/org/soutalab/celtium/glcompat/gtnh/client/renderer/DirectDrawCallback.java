package org.soutalab.celtium.glcompat.gtnh.client.renderer;

/*
 * Minimal GTNHLib subset, included file-by-file for celtium's glcompat layer (S4).
 * Source: GTNHLib (Actinium vendored copy @ ce77eed9), path client/renderer/DirectDrawCallback.java.
 * License: LGPL-3.0. See THIRD_PARTY_NOTICES.md. Only files with a proven consumer
 * in the converted GLSM are present; nothing else of GTNHLib is imported.
 */



// Not a stable API. May change in the future.
public interface DirectDrawCallback {

    boolean onDraw(CallbackTessellator tessellator);
}
