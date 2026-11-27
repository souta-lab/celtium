package org.soutalab.celtium.glcompat.gtnh.client.renderer;
/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * Only the types the converted GLSM front references are carried; the library
 * as a whole is NOT imported. See THIRD_PARTY_NOTICES.md.
 */


import org.soutalab.celtium.glcompat.gtnh.client.renderer.vao.IVertexArrayObject;
import org.soutalab.celtium.glcompat.gtnh.client.renderer.vao.VertexBufferType;
import org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex.VertexFlags;
import org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex.VertexFormat;

/**
 * Capture-stack manager for the direct tessellators. The vanilla {@code BufferBuilder}
 * interception bridge from GTNHLib is not carried over: in celtium, vanilla tessellator
 * batches reach the renderer through Lane 2's native capture (ModelReplay), never
 * through this Lane 3 class.
 */
public final class TessellatorManager {
    public static final int DEFAULT_BUFFER_SIZE = 0x8000;
    private static final int DIRECT_TESSELLATOR_STACK_DEPTH = 16;

    // The capture stack is per-thread: a capture started on one thread (e.g. display
    // list recording on the client thread) must never intercept draws issued by
    // another thread (e.g. CustomLoadingScreen's splash renderer), otherwise the
    // foreign draw lands in the owner's tessellator and corrupts state.
    private static final ThreadLocal<DirectTessellator> mainInstance =
        ThreadLocal.withInitial(() -> new DirectTessellator(DEFAULT_BUFFER_SIZE, false));
    private static final ThreadLocal<CallbackTessellator> mainCallbackInstance =
        ThreadLocal.withInitial(() -> new CallbackTessellator(DEFAULT_BUFFER_SIZE, false));
    private static final ThreadLocal<DirectTessellator[]> directTessellators =
        ThreadLocal.withInitial(() -> new DirectTessellator[DIRECT_TESSELLATOR_STACK_DEPTH]);

    private static final ThreadLocal<Integer> directTessellatorIndex = ThreadLocal.withInitial(() -> -1);
    private static final ThreadLocal<Boolean> mainInstanceInStack = ThreadLocal.withInitial(() -> false);

    private TessellatorManager() {
    }

    private static DirectTessellator getDirectTessellator() {
        return directTessellators.get()[directTessellatorIndex.get()];
    }

    private static boolean hasDirectTessellator() {
        return directTessellatorIndex.get() != -1;
    }

    private static boolean isMainTessellator(DirectTessellator tessellator) {
        return tessellator == mainInstance.get() || tessellator == mainCallbackInstance.get();
    }

    private static void setDirectTessellator(DirectTessellator tessellator) {
        final int index = directTessellatorIndex.get() + 1;
        if (index >= DIRECT_TESSELLATOR_STACK_DEPTH) {
            throw new IllegalStateException("DirectTessellator stack overflow");
        }
        mainInstanceInStack.set(mainInstanceInStack.get() || isMainTessellator(tessellator));
        directTessellators.get()[index] = tessellator;
        directTessellatorIndex.set(index);
    }

    public static DirectTessellator startCapturingDirect() {
        if (!mainInstanceInStack.get()) {
            setDirectTessellator(mainInstance.get());
            return mainInstance.get();
        }
        final DirectTessellator tessellator = new DirectTessellator(DEFAULT_BUFFER_SIZE);
        setDirectTessellator(tessellator);
        return tessellator;
    }

    public static DirectTessellator startCapturingDirect(int capacity) {
        if (!mainInstanceInStack.get() && DEFAULT_BUFFER_SIZE >= capacity) {
            setDirectTessellator(mainInstance.get());
            return mainInstance.get();
        }
        final DirectTessellator tessellator = new DirectTessellator(capacity);
        setDirectTessellator(tessellator);
        return tessellator;
    }

    public static DirectTessellator startCapturingDirect(VertexFormat format) {
        final DirectTessellator tessellator = startCapturingDirect();
        tessellator.setVertexFormat(format);
        return tessellator;
    }

    public static CallbackTessellator startCapturingDirect(DirectDrawCallback callback) {
        if (!mainInstanceInStack.get()) {
            final CallbackTessellator tessellator = mainCallbackInstance.get();
            tessellator.setDrawCallback(callback);
            setDirectTessellator(tessellator);
            return tessellator;
        }
        final CallbackTessellator tessellator = new CallbackTessellator(DEFAULT_BUFFER_SIZE);
        tessellator.setDrawCallback(callback);
        setDirectTessellator(tessellator);
        return tessellator;
    }

    public static void startCapturingDirect(DirectTessellator tessellator) {
        setDirectTessellator(tessellator);
    }

    public static void stopCapturingDirect() {
        if (!hasDirectTessellator()) {
            throw new IllegalStateException("Tried to stop capturing when not capturing!");
        }
        final DirectTessellator tessellator = getDirectTessellator();
        directTessellators.get()[directTessellatorIndex.get()] = null;
        directTessellatorIndex.set(directTessellatorIndex.get() - 1);
        if (isMainTessellator(tessellator)) {
            mainInstanceInStack.set(false);
        }
        tessellator.onRemovedFromStack();
    }

    public static IVertexArrayObject stopCapturingDirectToVBO(VertexBufferType bufferType) {
        if (!hasDirectTessellator()) {
            throw new IllegalStateException("Tried to stop capturing when not capturing!");
        }
        final DirectTessellator tessellator = getDirectTessellator();
        final IVertexArrayObject vbo = tessellator.uploadToVBO(bufferType);
        stopCapturingDirect();
        return vbo;
    }

    public static void cleanup() {
        while (hasDirectTessellator()) {
            stopCapturingDirect();
        }
    }
}
