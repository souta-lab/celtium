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
    // GLSM M-4 (INT-2 remainder): the stencil family, polygon mode, sample coverage and the constant
    // blend colour join the same owner. Record-only for now: this renderer has no stencil attachment,
    // so the values land in NativeRasterState and nothing applies them to a pipeline yet.
    public static void glStencilFunc(int func, int ref, int mask) { NativeRasterState.current().stencilFunc(func, ref, mask); }
    public static void glStencilFuncSeparate(int face, int func, int ref, int mask) {
        NativeRasterState.current().stencilFuncSeparate(face, func, ref, mask);
    }
    public static void glStencilOp(int fail, int depthFail, int depthPass) {
        NativeRasterState.current().stencilOp(fail, depthFail, depthPass);
    }
    public static void glStencilOpSeparate(int face, int fail, int depthFail, int depthPass) {
        NativeRasterState.current().stencilOpSeparate(face, fail, depthFail, depthPass);
    }
    public static void glStencilMask(int mask) { NativeRasterState.current().stencilMask(mask); }
    public static void glStencilMaskSeparate(int face, int mask) { NativeRasterState.current().stencilMaskSeparate(face, mask); }
    public static void glPolygonMode(int face, int mode) { NativeRasterState.current().polygonMode(face, mode); }
    public static void glSampleCoverage(float value, boolean invert) { NativeRasterState.current().sampleCoverage(value, invert); }
    public static void glBlendColor(float red, float green, float blue, float alpha) {
        NativeRasterState.current().blendColor(red, green, blue, alpha);
    }
}
