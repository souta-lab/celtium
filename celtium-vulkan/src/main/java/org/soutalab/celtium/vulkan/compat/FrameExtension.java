package org.soutalab.celtium.vulkan.compat;

/**
 * A frame-start extension: a named contributor the {@link RenderExtensionRegistry} calls once per
 * frame, before the frame graph is compiled and before any pass records. The contribution is data
 * only, through the {@link FrameExtensionContext} sink - an extension never sees a command buffer,
 * never binds anything, and never runs inside a pass.
 * <p>
 * That narrowness is the contract that keeps this surface compatible with ledger II (item 378): the
 * vanilla instruction points addons target - {@code World.getCombinedLight} and its neighbours - stay
 * exactly where vanilla put them, served by celtium's funnel, and nothing registered here can
 * intercept, wrap or replace them. An addon such as Celeritas Dynamic Lights keeps working through
 * those points; what lives here is celtium's own frame data (its native dynamic lights today), which
 * never needed to touch a vanilla query to begin with.
 */
public interface FrameExtension {

    /** {@return this extension's stable id}; the registry keys extensions by it. */
    String id();

    /** Contributes this frame's data through {@code context}. Called on the render thread. */
    void gather(FrameExtensionContext context);
}
