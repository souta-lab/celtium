package org.soutalab.celtium.glcompat.gtnh.client.renderer;

/*
 * Minimal GTNHLib subset, included file-by-file for celtium's glcompat layer (S4).
 * Source: GTNHLib (Actinium vendored copy @ ce77eed9), path client/renderer/CallbackTessellator.java.
 * License: LGPL-3.0. See THIRD_PARTY_NOTICES.md. Only files with a proven consumer
 * in the converted GLSM are present; nothing else of GTNHLib is imported.
 */


import java.nio.ByteBuffer;

public final class CallbackTessellator extends DirectTessellator {

    private DirectDrawCallback drawCallback;

    public CallbackTessellator(DirectDrawCallback callback) {
        super(TessellatorManager.DEFAULT_BUFFER_SIZE);
        this.drawCallback = callback;
    }

    public CallbackTessellator(ByteBuffer initial, DirectDrawCallback callback) {
        super(initial);
        this.drawCallback = callback;
    }

    public CallbackTessellator(ByteBuffer initial) {
        super(initial);
    }

    public CallbackTessellator(int capacity) {
        super(capacity);
    }

    public CallbackTessellator(int capacity, boolean deleteAfter) {
        super(capacity, deleteAfter);
    }

    public CallbackTessellator(ByteBuffer initial, boolean deleteAfter) {
        super(initial, deleteAfter);
    }

    @Override
    public int draw() {
        final int result = super.draw();
        if (drawCallback != null && drawCallback.onDraw(this)) {
            this.reset();
        }
        return result;
    }

    public void setDrawCallback(DirectDrawCallback drawCallback) {
        this.drawCallback = drawCallback;
    }
}
