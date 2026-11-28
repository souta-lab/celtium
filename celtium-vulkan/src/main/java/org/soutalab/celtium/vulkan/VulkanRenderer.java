package org.soutalab.celtium.vulkan;

/**
 * The root the renderer is reached through (refactoring plan R7).
 * <p>
 * Everything the game and the terrain factory need is declared here, so the factory can ask a
 * {@code VulkanRenderer} instead of testing for {@link VulkanBackendAdapter} and reaching into the
 * class. It extends {@link CeltiumBackend} because that is the contract the game already holds; what
 * this adds is the window-time surface - whether this instance is the one the window created, and the
 * adoption that hands a world to it - plus the lifecycle the factory calls. The lifetime split itself
 * (device and swapchain on one side, world resources on the other) shows up as method groups rather
 * than as comments.
 */
public interface VulkanRenderer extends CeltiumBackend {

    /**
     * {@return whether this instance is the one the window created}.
     * <p>
     * The window's instance owns the swapchain and the frame target; a world either adopts it or builds
     * its own device. Asking this instead of testing the concrete type is what makes the factory's
     * decision a contract rather than a class check.
     */
    boolean celtium$isWindowBackend();

    /**
     * Hands a loaded world to the window-time instance, so the first world frame finds the device, the
     * swapchain and the frame target warm instead of a second backend being built beside them.
     */
    void celtium$adoptWorld(DynamicLightService dynamicLightService);

    /**
     * The world-lifetime counterpart of the adoption: frees what a loaded world built - the Lane 1
     * terrain residency and the model meshes a captured group borrows - while leaving the device, the
     * swapchain and the frame target alone.
     */
    void celtium$releaseWorldResources();

    /** Releases the device and everything built on it. */
    void delete();
}
