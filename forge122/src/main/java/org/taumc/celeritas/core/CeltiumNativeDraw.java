package org.taumc.celeritas.core;

/**
 * The single seam between Minecraft's own draw call sites and the native renderer.
 *
 * <p>Vanilla and Forge reach the GPU through a handful of entry points, and each of them used to be answered by the
 * GL-less stub, which meant a vanilla draw travelled {@code GlStateManager} -> {@code GL11} -> the LWJGL-call rewrite
 * -> the hint -> the stub before it became a native draw. That chain is what this class removes: a mixin on the
 * call site asks here first, and when the answer is "handled" the original body - and therefore every GL frame above it -
 * never runs.
 *
 * <p>Keeping the routing in one class is deliberate. The stub stays what it was meant to be, the answer for mods that
 * call {@code GL11} directly, while the paths Minecraft itself owns get a named entry each. Adding a vanilla entry
 * point is then one method here plus one mixin, and the call-stack census ({@code CELTIUM_BATCH_SITE}) shows whether the
 * GL frames have actually disappeared, rather than a comment claiming they have.
 *
 * <p>Every method returns {@code true} only when the draw was taken. A {@code false} return must leave the caller to its
 * original body, which is what keeps a hybrid run - where a real GL context exists - working.
 */
public final class CeltiumNativeDraw {

    private CeltiumNativeDraw() {
    }

    /** {@return whether the native renderer took a {@code glDrawArrays} issued by Minecraft itself}. */
    public static boolean drawArrays(int mode, int first, int count) {
        if (!org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
            return false;
        }
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$drawArrays(mode, first, count);
        return true;
    }
}
