package org.soutalab.celtium.vulkan.gl;

import org.soutalab.celtium.vulkan.NativeRasterState;

/** Raw mod GL setters write the Vulkan owner's state; no OpenGL dispatch or draw-time reconciliation is needed. */
public final class NativeRasterAdapter {
    private NativeRasterAdapter() {}

    public static void glBlendFunc(int source, int destination) {
        NativeRasterState.current().blendFunc(source, destination, source, destination);
    }
    public static void glBlendFuncSeparate(int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha) {
        NativeRasterState.current().blendFunc(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
    }
    public static void glBlendEquation(int equation) { NativeRasterState.current().blendEquation(equation, equation); }
    public static void glBlendEquationSeparate(int rgb, int alpha) { NativeRasterState.current().blendEquation(rgb, alpha); }
    public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        NativeRasterState.current().colorMask((red ? 1 : 0) | (green ? 2 : 0) | (blue ? 4 : 0) | (alpha ? 8 : 0));
    }
    public static void glCullFace(int face) { NativeRasterState.current().cullFace(face); }
    public static void glFrontFace(int face) { NativeRasterState.current().frontFace(face); }
    public static void glPolygonOffset(float factor, float units) { NativeRasterState.current().polygonOffset(factor, units); }
    public static void glScissor(int x, int y, int width, int height) { NativeRasterState.current().scissor(x, y, width, height); }
}
