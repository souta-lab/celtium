/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.vulkan.pack;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.frame.FrameContext;
import org.soutalab.celtium.core.shader.PackTerrainDriver;
import org.soutalab.celtium.shader.ShaderLogger;
import org.soutalab.celtium.shader.compiler.CeltiumPackCompiler;
import org.soutalab.celtium.shader.compiler.CompiledShaderPack;
import org.soutalab.celtium.shader.features.PackCompilerCapabilities;
import org.soutalab.celtium.shader.shaderpack.ShaderPack;
import org.soutalab.celtium.shader.shaderpack.discovery.ShaderpackDirectoryManager;
import org.soutalab.celtium.shader.shaderpack.option.menu.OptionMenuContainer;
import org.soutalab.celtium.vulkan.VulkanContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.lwjgl.vulkan.VK13.*;

/**
 * The pack runtime: discovery, selection, compilation and the frame entry of the active
 * pack. One instance lives for the process; the frame's owner calls {@link #onFrame} once per
 * frame and the runtime no-ops when no pack is active. A pack that fails to compile or throws
 * during a frame is deactivated with a logged reason - a broken pack must never take the
 * frame loop down with it.
 *
 * <p>Option wiring (the S5-a handover): the runtime owns the pending-values map the option
 * menu edits; applying it recompiles the pack with those values as option overrides.
 */
public final class ShaderPackRuntime {

    /** Frames in flight the executor rings its per-frame state over. */
    public static final int FRAMES_IN_FLIGHT = 2;

    private final Path shaderpacksRoot;
    private final CeltiumPackCompiler compiler = new CeltiumPackCompiler();
    private final Map<String, String> pendingOptionValues = new LinkedHashMap<>();

    private String activePackName;
    private Path activePackShadersRoot;
    private CompiledShaderPack compiledPack;
    private PackFrameExecutor executor;
    private int frameFailures;

    public ShaderPackRuntime(Path shaderpacksRoot) {
        this.shaderpacksRoot = shaderpacksRoot;
    }

    public Path shaderpacksRoot() {
        return shaderpacksRoot;
    }

    /** The pack names the directory manager finds under the root (folders and zips). */
    public List<String> availablePacks() {
        if (shaderpacksRoot == null || !Files.isDirectory(shaderpacksRoot)) {
            return List.of();
        }
        ShaderpackDirectoryManager manager = new ShaderpackDirectoryManager(shaderpacksRoot);
        try {
            List<String> names = new ArrayList<>(manager.enumerate());
            names.sort(String::compareToIgnoreCase);
            return names;
        } catch (IOException e) {
            ShaderLogger.LOGGER.warn("Failed to enumerate shader packs: {}", e.toString());
            return List.of();
        }
    }

    public boolean isPackValid(String packName) {
        Path candidate = shaderpacksRoot.resolve(packName);
        return new ShaderpackDirectoryManager(shaderpacksRoot).isValidShaderpack(candidate);
    }

    public String activePackName() {
        return activePackName;
    }

    public boolean packActive() {
        return compiledPack != null;
    }

    public CompiledShaderPack compiledPack() {
        return compiledPack;
    }

    /** The pending option overrides the menu edits; applied on {@link #applyPendingOptions()}. */
    public Map<String, String> pendingOptionValues() {
        return pendingOptionValues;
    }

    /** The active pack's option menu tree, or null when no pack is active. */
    public OptionMenuContainer optionMenu() {
        return compiledPack == null ? null : compiledPack.sourcePack().getMenuContainer();
    }

    /**
     * Compiles and activates a pack by directory-manager name. Zip packs are not compiled by
     * this seat (the compiler consumes a {@code shaders/} directory); a folder pack is.
     *
     * @return true when the pack compiled and is now active
     */
    public boolean activate(String packName) {
        deactivate();
        Path packDir = shaderpacksRoot.resolve(packName);
        Path shadersRoot = packDir.resolve("shaders");
        if (!Files.isDirectory(shadersRoot)) {
            ShaderLogger.LOGGER.warn("Shader pack '{}' has no shaders/ directory at {}", packName, shadersRoot);
            return false;
        }
        try {
            CompiledShaderPack compiled = compiler.compile(shadersRoot,
                    Map.copyOf(pendingOptionValues), PackCompilerCapabilities.DEFAULT_VULKAN_13);
            this.compiledPack = compiled;
            builtInPackActive = true;
            this.activePackName = packName;
            this.activePackShadersRoot = shadersRoot;
            this.frameFailures = 0;
            ShaderLogger.LOGGER.info("Activated shader pack '{}': {} ordered passes, {} render targets",
                    packName, compiled.executionPlan().passes().size(),
                    compiled.executionPlan().renderTargets().size());
            return true;
        } catch (IOException | RuntimeException e) {
            ShaderLogger.LOGGER.warn("Failed to compile shader pack '{}': {}", packName, e.toString());
            return false;
        }
    }

    /** Recompiles the active pack with the pending option values as overrides. */
    public boolean applyPendingOptions() {
        if (activePackName == null) {
            return false;
        }
        String name = activePackName;
        return activate(name);
    }

    public void deactivate() {
        builtInPackActive = false;
        if (executor != null) {
            executor.close();
            executor = null;
        }
        if (compiledPack != null) {
            compiledPack.close();
            compiledPack = null;
        }
        activePackName = null;
        activePackShadersRoot = null;
    }

    /**
     * The frame entry. Builds the executor on first use (it needs the live context and the
     * terrain driver the frame owner supplies), then records the pack frame.
     *
     * @return true when a pack frame was recorded
     */
    public boolean onFrame(VulkanContext context, VkCommandBuffer cmd, FrameContext ctx,
                           PackFrameEnvironment env,
                           Function<VulkanContext, PackTerrainDriver> terrainDriverFactory,
                           long seatFrameId, int seatFrameSlot) {
        if (compiledPack == null) {
            return false;
        }
        try {
            if (executor == null) {
                PackTerrainDriver driver = terrainDriverFactory.apply(context);
                int shadowExtent = shadowExtentOf(compiledPack);
                executor = new PackFrameExecutor(context, compiledPack, driver,
                        oneShotSubmitter(context), FRAMES_IN_FLIGHT, shadowExtent);
            }
            executor.executeFrame(cmd, ctx, env, seatFrameId, seatFrameSlot);
            executor.reportTimings(ctx.frameId());
            return true;
        } catch (RuntimeException e) {
            frameFailures++;
            ShaderLogger.LOGGER.warn("Shader pack frame failed ({}): {}; deactivating pack",
                    frameFailures, e.toString());
            deactivate();
            return false;
        }
    }

    /** The executor of the active pack, once built (integration + diagnostics seat). */
    public PackFrameExecutor executor() {
        return executor;
    }

    // ---- The native frame seat ------------------------------------------------------
    // Opened by the frame owner (the S3-b FrameCoordinator) for the duration of one frame.
    // The seat lives here, not in the forge module: forge122 sees this module's classes but
    // not its LWJGL dependency, so no forge-side signature may name VkCommandBuffer.

    private static VulkanContext seatContext;
    private static VkCommandBuffer seatCommandBuffer;
    private static ActiveRenderTargets seatSwapchain;
    private static Function<VulkanContext, PackTerrainDriver> seatTerrainDriverFactory;
    private static long seatFrameId;
    /** The seat's frame slot; -1 is the sentinel for "no seat open" (unlike the frame id,
     *  the slot has no sentinel convention of its own, so this class defines one). */
    private static int seatFrameSlot = -1;

    /**
     * Whether the built-in pack runtime currently has a pack active, visible to the
     * frame machinery without an instance handle: the present-image borrow gate reads
     * it to keep the direct-present borrow for built-in pack frames (the executor's
     * intermediates live on its own slab and its final pass writes only the swapchain
     * image), where the external-pack probe's bypass was written for a path whose
     * passes sample the frame colour (D7 seat ruling, NEON 2026-11-28).
     */
    private static volatile boolean builtInPackActive;

    /** Whether a pack is active in the built-in runtime (any instance). */
    public static boolean builtInPackActive() {
        return builtInPackActive;
    }

    /** Opens the native frame seat for one frame (frame-owner integration point). */
    public static synchronized void beginNativeFrame(VulkanContext context, VkCommandBuffer cmd,
                                                     ActiveRenderTargets swapchain,
                                                     Function<VulkanContext, PackTerrainDriver> terrainDriverFactory,
                                                     long frameId, int frameSlot) {
        seatContext = context;
        seatCommandBuffer = cmd;
        seatSwapchain = swapchain;
        seatTerrainDriverFactory = terrainDriverFactory;
        seatFrameId = frameId;
        seatFrameSlot = frameSlot;
    }

    /** Closes the native frame seat opened by {@link #beginNativeFrame}. */
    public static synchronized void endNativeFrame() {
        seatContext = null;
        seatCommandBuffer = null;
        seatSwapchain = null;
        seatTerrainDriverFactory = null;
        seatFrameSlot = -1;
    }

    public static synchronized boolean nativeFrameOpen() {
        return seatContext != null && seatCommandBuffer != null;
    }

    /** The swapchain targets of the open seat (null when no seat is open). */
    public static synchronized ActiveRenderTargets seatSwapchainTargets() {
        return seatSwapchain;
    }

    /** The frame id of the open seat. */
    public static synchronized long seatFrameId() {
        return seatFrameId;
    }

    /** The frame slot of the open seat (-1 when no seat is open). */
    public static synchronized int seatFrameSlot() {
        return seatFrameSlot;
    }

    /**
     * Records the pack frame from the open native seat.
     *
     * @return true when a pack frame was recorded; false when no seat is open
     */
    public boolean recordSeatedFrame(FrameContext ctx, PackFrameEnvironment env) {
        VulkanContext context;
        VkCommandBuffer cmd;
        Function<VulkanContext, PackTerrainDriver> factory;
        long frameId;
        int frameSlot;
        synchronized (ShaderPackRuntime.class) {
            context = seatContext;
            cmd = seatCommandBuffer;
            factory = seatTerrainDriverFactory;
            frameId = seatFrameId;
            frameSlot = seatFrameSlot;
        }
        if (context == null || cmd == null || factory == null) {
            return false;
        }
        return onFrame(context, cmd, ctx, env, factory, frameId, frameSlot);
    }

    private static int shadowExtentOf(CompiledShaderPack compiled) {
        try {
            ShaderPack source = compiled.sourcePack();
            var programSet = source.getProgramSet("overworld");
            return programSet.getPackDirectives().getShadowDirectives().getResolution();
        } catch (RuntimeException e) {
            return 2048;
        }
    }

    /** A one-shot submitter over the context's queue, for activation-time uploads. */
    public static PackSubmitter oneShotSubmitter(VulkanContext context) {
        return (label, recorder) -> {
            org.lwjgl.vulkan.VkDevice device = context.device();
            long pool = 0L;
            long fence = 0L;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                        .queueFamilyIndex(context.queueFamilyIndex())
                        .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
                java.nio.LongBuffer handle = stack.mallocLong(1);
                if (vkCreateCommandPool(device, poolInfo, null, handle) != VK_SUCCESS) {
                    throw new IllegalStateException("vkCreateCommandPool failed for " + label);
                }
                pool = handle.get(0);

                VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                        .commandPool(pool)
                        .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                        .commandBufferCount(1);
                org.lwjgl.PointerBuffer cmdPointer = stack.mallocPointer(1);
                if (vkAllocateCommandBuffers(device, allocInfo, cmdPointer) != VK_SUCCESS) {
                    throw new IllegalStateException("vkAllocateCommandBuffers failed for " + label);
                }
                VkCommandBuffer cmd = new VkCommandBuffer(cmdPointer.get(0), device);

                VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                        .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
                vkBeginCommandBuffer(cmd, beginInfo);
                recorder.accept(cmd);
                vkEndCommandBuffer(cmd);

                VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
                if (vkCreateFence(device, fenceInfo, null, handle) != VK_SUCCESS) {
                    throw new IllegalStateException("vkCreateFence failed for " + label);
                }
                fence = handle.get(0);

                VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                        .pCommandBuffers(cmdPointer);
                if (vkQueueSubmit(context.queue(), submitInfo, fence) != VK_SUCCESS) {
                    throw new IllegalStateException("vkQueueSubmit failed for " + label);
                }
                vkWaitForFences(device, handle, true, 30_000_000_000L);
            } finally {
                if (fence != 0L) {
                    vkDestroyFence(device, fence, null);
                }
                if (pool != 0L) {
                    vkDestroyCommandPool(device, pool, null);
                }
            }
        };
    }
}
