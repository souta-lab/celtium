package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import org.embeddedt.embeddium.impl.render.frame.RenderAheadManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.CeleritasVintage;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Unique
    private final RenderAheadManager celeritas$renderAheadManager = new RenderAheadManager();

    @Inject(method = "runTick", at = @At("HEAD"))
    private void preRender(CallbackInfo ci) {
        celeritas$renderAheadManager.startFrame(CeleritasVintage.options().advanced.cpuRenderAheadLimit);
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void postRender(CallbackInfo ci) {
        celeritas$renderAheadManager.endFrame();
    }

    /**
     * Creates the renderer at window creation under the GL-less window, so the menu and the loading screens have
     * someone to draw and present them.
     * <p>
     * Measured on the user's own launch: the menu draws are client-memory {@code glDrawArrays} calls, the stub decodes
     * them (524,288 in 90 s, layouts 24/16/20 bytes), and every one was refused with "no terrain backend is installed"
     * because {@code TerrainGpuBackends} stays empty until a world renderer exists - so the window had nothing in it,
     * and the present at the swap point (which requires a backend) never ran either. Two anchors, earliest first: the
     * tail of {@code createDisplay} (the window is up, the game loop has not started, and the loading screen is about
     * to be drawn) and the head of {@code runTick} (the retry point, and the only one a configuration where the display
     * is created before the stub could use). Nothing menu-specific is needed at either: the backend's own
     * no-terrain-pass UI render pass is what these frames draw with.
     * <p>
     * Only under the stub: a bridge frame draws its menu with OpenGL, so the world-time creation stays the only one
     * there and this hook does nothing. The world renderer adopts this instance rather than building a second one, so
     * the swapchain and the frame target keep one owner.
     */
    /** Latched after the saved VSync option has been handed to the present policy once (task-56). */
    @Unique
    private static boolean celtium$vsyncPushed;
    @Unique
    private static boolean celtium$windowRendererCreated;
    @Unique
    private static int celtium$windowRendererAttempts;
    /** Attempts are spaced this far apart, so a transient failure does not cost the session. */
    @Unique
    private static int celtium$windowRendererNextTick;
    @Unique
    private static final int celtium$WINDOW_RENDERER_ATTEMPTS = 8;
    @Unique
    private static final int celtium$WINDOW_RENDERER_RETRY_TICKS = 40;
    /** Which stage asked: the display creation, or a later tick. Printed so the two are not confused in a log. */
    @Unique
    private static String celtium$windowRendererStage = "none";

    /**
     * The earliest install point: the tail of {@code Minecraft.createDisplay()}, before {@code startGame} reaches the
     * loading screen.
     * <p>
     * Measured from the sources: {@code startGame} calls {@code createDisplay} (Minecraft.java:505), then
     * {@code FMLClientHandler.beginMinecraftLoading} (:514) and {@code SplashProgress.drawVanillaScreen} (:517) - and
     * Forge's splash is disabled under GL-less, so that call runs {@code Minecraft.drawSplashScreen} (:959-1005), which
     * draws the Mojang logo through the immediate path and ends in {@code updateDisplay()}. With the renderer installed
     * only at {@code runTick} HEAD, every one of those draws arrived with {@code TerrainGpuBackends.current() == null}
     * and was refused as "no terrain backend is installed", so the loading screen could not exist. The window itself is
     * already up here ({@code Display.create} was patched to a GLFW_NO_API window by LwjglxDisplayMixin), which is what
     * the surface needs; the device and swapchain are created before the framebuffer and the resource load, and the
     * world legs see the same single renderer as before because this is the same latch the tick path uses.
     */
    /**
     * Requests the presented dump for the loading phase, from the splash's own <em>head</em>.
     * <p>
     * No existing switch can land in this window: the tick-keyed dumps need a world or the main menu, and the loading
     * phase runs inside {@code Minecraft.startGame} before the first client tick
     * ({@code Minecraft.java:517} -> {@code SplashProgress.drawVanillaScreen} -> {@code drawSplashScreen}). The fixture
     * is a separate mod jar, so the call is reflective and silent when it is absent; the stage name is {@code loading}
     * and the request is latched once inside the fixture.
     * <p>
     * <b>HEAD, not RETURN, and that is the whole measurement.</b> The swapchain is created lazily on the present path,
     * so a request made at the splash's return - after its last {@code updateDisplay()} has already presented - has no
     * swapchain to write through and was refused. Requested at the head instead, the request is pending when that first
     * present runs, and the frame it writes is the splash's own. Measured before this change: the request line existed
     * with no PNG beside it ({@code pictureRequested_loading … after 0 presented frames}); the backend now holds such a
     * request instead of refusing it.
     */
    /**
     * The control anchor: {@code startGame} runs on every launch, so a leg that shows no splash line at all can be
     * read as "the mixin did not apply" rather than "the splash method was not reached" - the two have different
     * fixes and the same silence otherwise.
     */
    /**
     * Gives the Client thread its own LWJGL3 GLCapabilities before mod pre-initialisation runs on it.
     * <p>
     * LWJGL3 stores them per thread, so the install the mixin plugin does on {@code main} does not reach this
     * thread - and a mod that calls LWJGL3 GL entry points here aborts the JVM
     * ({@code No context is current or a function that is not available in the current context was called}).
     * Measured 2026-10-15: Thaumcraft's {@code ClientProxy.preInit} does exactly that from this thread.
     * {@code startGame} is the earliest Client-thread point before {@code Loader.preinitializeMods}, and the call is
     * idempotent.
     */

    /**
     * The same install on the thread that actually calls GL.
     * <p>
     * Measured 2026-10-15 (leg {@code gv-e2e-268-fix128g}): {@code startGame} runs on {@code main}, while the game's
     * GL calls come from a separate thread named {@code Client thread} (thread #3 in the abort message, distinct from
     * the launcher's #1) - so the {@code startGame} hook installed capabilities for the wrong thread and Thaumcraft's
     * {@code ClientProxy.preInit} aborted anyway. This injection and the one below run on that thread:
     * {@code createDisplay} is the first Client-thread point that touches the window (the leg logs its window lines at
     * 02:04:46 while the abort comes at 02:05:1x), and {@code runTick} covers anything that starts calling GL after
     * the window exists. Both are idempotent.
     */



    @Inject(method = "startGame", at = @At("HEAD"), require = 0)
    private void celtium$splashHookControl(CallbackInfo ci) {
        System.out.println("[Celtium] CELTIUM_SPLASH_HOOK startGame=true (mixin applied; the splash hook is live)");
    }

    @Inject(method = "drawSplashScreen", at = @At("HEAD"), require = 0)
    private void celtium$dumpLoadingFrame(CallbackInfo ci) {
        System.out.println("[Celtium] CELTIUM_SPLASH_HOOK drawSplashScreen=true");
        try {
            Class<?> fixture = Class.forName("org.soutalab.celtium.validation.ValidationClient");
            fixture.getMethod("requestLoadingDump").invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException absent) {
            // No fixture, or one that cannot answer: a measurement lost, never a frame lost - but the *reason* is
            // printed, bounded. "The fixture is absent" and "the fixture is present and the call failed" are different
            // findings with different fixes, and swallowing the exception made them look the same: a round of legs ran
            // with the splash line present and the fixture's own line missing, and nothing in the log said why.
            if (celtium$splashHookFailures < 3) {
                celtium$splashHookFailures++;
                System.out.println("[Celtium] CELTIUM_SPLASH_HOOK fixture call failed: "
                        + absent.getClass().getName() + ": " + absent.getMessage());
                Throwable cause = absent.getCause();
                if (cause != null) {
                    System.out.println("[Celtium] CELTIUM_SPLASH_HOOK fixture call cause: "
                            + cause.getClass().getName() + ": " + cause.getMessage());
                }
            }
        }
    }

    /** Bounded lines for {@link #celtium$dumpLoadingFrame}'s failure path. */
    @Unique
    private static int celtium$splashHookFailures;

    /**
     * Reports the LWJGL3 GL dispatch state once the display exists, so a leg can say why a mod's GL call aborts.
     * <p>
     * The aborts this project has chased (legs {@code gv-e2e-268-fix128g..k}) happen in mod code calling LWJGL3's
     * generated GL classes, and the two candidate explanations - our function provider not being the one LWJGL3
     * uses, and a capabilities object that is not ours - look identical from the outside. This line separates them:
     * the provider's class name says whether {@code GL.create(provider)} took effect, and the capabilities identity
     * plus its address buffer say what the native dispatch would use. Reflection keeps this module free of an LWJGL3
     * compile-time dependency, and the report is one bounded line at the point where the window exists and mod
     * pre-init has not run yet.
     */
    @Inject(method = "createDisplay", at = @At("RETURN"), require = 0)
    private void celtium$reportLwjgl3DispatchState(CallbackInfo ci) {
        try {
            Class<?> gl = Class.forName("org.lwjgl.opengl.GL");
            Object provider = gl.getMethod("getFunctionProvider").invoke(null);
            String capabilitiesState;
            try {
                Object capabilities = gl.getMethod("getCapabilities").invoke(null);
                if (capabilities == null) {
                    capabilitiesState = "null";
                } else {
                    Object addresses = capabilities.getClass().getMethod("getAddressBuffer").invoke(capabilities);
                    // How many of the first entries are non-zero is what separates "a capabilities table built from
                    // our provider" from "a table that exists but is empty", and the write-once ICD means the first
                    // one created wins for the whole process - so the count matters more than the buffer's metadata.
                    long nonZero = -1;
                    long sampled = 0;
                    try {
                        // PointerBuffer has get(int) and capacity(); the first version of this called getBuffer(),
                        // which does not exist, and every leg reported nonZeroFirst0=unknown because of it.
                        java.lang.reflect.Method get = addresses.getClass().getMethod("get", int.class);
                        java.lang.reflect.Method capacityMethod = addresses.getClass().getMethod("capacity");
                        int capacity = ((Number) capacityMethod.invoke(addresses)).intValue();
                        sampled = Math.min(capacity, 256);
                        nonZero = 0;
                        for (int i = 0; i < sampled; i++) {
                            if (((Number) get.invoke(addresses, Integer.valueOf(i))).longValue() != 0L) {
                                nonZero++;
                            }
                        }
                    } catch (Throwable ignored) {
                        // leave the count as unknown
                    }
                    // The two values that decide the fix, and they are different fixes: whether the capabilities
                    // object was built with the ARB shader-object extension in its set at all (our stub answers
                    // glGetString(GL_EXTENSIONS) with an empty string on purpose, so a set derived from it contains
                    // nothing and LWJGL leaves every extension function at zero), and what address that function
                    // actually has. false means "install a capabilities built from our own 427-extension set first";
                    // true-with-zero means the write-once ICD is holding an older object.
                    String arbState = "unknown";
                    try {
                        Object flag = capabilities.getClass().getField("GL_ARB_shader_objects").get(capabilities);
                        Object address = capabilities.getClass().getField("glCreateShaderObjectARB").get(capabilities);
                        arbState = "GL_ARB_shader_objects=" + flag + " glCreateShaderObjectARB=0x"
                                + Long.toHexString(((Number) address).longValue());
                    } catch (Throwable ignored) {
                        // leave it as unknown
                    }
                    capabilitiesState = capabilities.getClass().getName() + "@"
                            + Integer.toHexString(System.identityHashCode(capabilities))
                            + " addresses=" + addresses
                            + " nonZeroFirst" + sampled + "=" + (nonZero < 0 ? "unknown" : Long.toString(nonZero))
                            + " " + arbState;
                }
            } catch (Throwable error) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                capabilitiesState = "throws(" + cause.getClass().getSimpleName() + ")";
            }
            org.taumc.celeritas.mixin.CeleritasVintageMixinPlugin.LOGGER.info(
                    "CELTIUM_GLLESS LWJGL3 dispatch on thread={} provider={} capabilities={}",
                    Thread.currentThread().getName(),
                    provider == null ? "null" : provider.getClass().getName(),
                    capabilitiesState);
        } catch (Throwable error) {
            org.taumc.celeritas.mixin.CeleritasVintageMixinPlugin.LOGGER.warn(
                    "CELTIUM_GLLESS LWJGL3 dispatch report failed: {}", error.toString());
        }
    }

    @Inject(method = "createDisplay", at = @At("RETURN"), require = 0)
    private void celtium$ensureWindowRendererAtDisplay(CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            org.soutalab.celtium.vulkan.gl.CeltiumGlStub.initializeWindowRectangles(
                    mc.displayWidth, mc.displayHeight);
        }
        celtium$windowRendererStage = "createDisplay";
        celtium$ensureWindowRenderer();
    }

    @Inject(method = "runTick", at = @At("HEAD"), require = 0)
    private void celtium$ensureWindowRendererAtTick(CallbackInfo ci) {
        if (!"createDisplay".equals(celtium$windowRendererStage)) {
            celtium$windowRendererStage = "runTick";
        }
        celtium$ensureWindowRenderer();
    }

    @Unique
    private static void celtium$ensureWindowRenderer() {
        // Latched on success, not on the attempt: latching the attempt would leave the menu blank for the whole session
        // after one transient device-creation failure. The retries are bounded and spaced so a configuration that
        // cannot have a window renderer prints its reason a handful of times instead of every tick.
        if (celtium$windowRendererCreated || celtium$windowRendererAttempts >= celtium$WINDOW_RENDERER_ATTEMPTS) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        // The saved VSync option is pushed once, here, before anything creates a swapchain: the option page's setter
        // only runs when the player opens the video-settings screen, so without this a saved `enableVsync=false`
        // began as FIFO (the policy's default) and only a press of the row could change the mode. Pushed at the same
        // early point the window renderer is created, which is before the first present - so the first swapchain is
        // built with the player's own value. Measured need: task-56's `vsync-toggle2` leg, where the row's press was
        // the only thing that reached the policy.
        if (!celtium$vsyncPushed) {
            celtium$vsyncPushed = true;
            if (mc != null) {
                org.soutalab.celtium.vulkan.VulkanSwapchain.setVSyncRequested(mc.gameSettings.enableVsync);
            }
        }
        if (mc != null && mc.world != null) {
            // Past the menu already; the world-time creation is the right path there.
            return;
        }
        long ticks = mc == null || mc.world == null ? 0L : mc.world.getTotalWorldTime();
        int now = (int) (System.nanoTime() / 1000000L);
        if (celtium$windowRendererAttempts > 0 && now < celtium$windowRendererNextTick) {
            return;
        }
        java.util.List<String> reasons = new java.util.ArrayList<String>();
        try {
            if (!org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
                // A bridge frame draws its menu with OpenGL, so the world-time creation stays the only one.
                celtium$windowRendererAttempts = celtium$WINDOW_RENDERER_ATTEMPTS;
                return;
            }
            if (org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current() != null) {
                celtium$windowRendererCreated = true;
                return;
            }
            celtium$windowRendererAttempts++;
            celtium$windowRendererNextTick = now + celtium$WINDOW_RENDERER_RETRY_TICKS * 50;
            Class<?> factory = Class.forName("org.soutalab.celtium.vulkan.VulkanTerrainFactory");
            Object backend = factory.getMethod("createForWindow", java.util.List.class).invoke(null, reasons);
            celtium$windowRendererCreated = backend != null;
            System.out.println("[Celtium] CELTIUM_WINDOW_RENDERER created=" + (backend != null)
                    + " attempt=" + celtium$windowRendererAttempts + " ticks=" + ticks
                    + " stage=" + celtium$windowRendererStage + " reasons=" + reasons);
        } catch (ClassNotFoundException | NoSuchMethodException absent) {
            celtium$windowRendererAttempts = celtium$WINDOW_RENDERER_ATTEMPTS;
            System.out.println("[Celtium] CELTIUM_WINDOW_RENDERER unavailable: " + absent);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            // Fail-closed: without a renderer the menu is blank, which is the state this replaces, and the reason is on
            // the line rather than in a stack trace nobody reads. The next attempt is a few dozen ticks away.
            // The cause chain is printed too: the call is reflective, so what arrives here is an
            // InvocationTargetException whose own message names nothing - measured on the user's own launch, where six
            // attempts all reported exactly that and the reason the renderer could not be created was invisible.
            Throwable cause = error;
            StringBuilder chain = new StringBuilder();
            int depth = 0;
            while (cause != null && depth < 6) {
                chain.append(depth == 0 ? "" : " <- ").append(cause.getClass().getName());
                String message = cause.getMessage();
                if (message != null) {
                    chain.append(": ").append(message);
                }
                cause = cause.getCause();
                depth++;
            }
            System.out.println("[Celtium] CELTIUM_WINDOW_RENDERER failed: attempt=" + celtium$windowRendererAttempts
                    + " error=" + chain + " reasons=" + reasons);
            error.printStackTrace(System.out);
        }
    }




    /**
     * Presents the frame from Vulkan at the game's own swap point.
     * <p>
     * The world render is the wrong place for the GL-less window: it runs only when a world is loaded, so the menu, the
     * loading screens and every GUI-only frame reached the window with nothing to show. {@code updateDisplay} is the
     * one point every frame passes through on its way to the screen, and it is also where the GL swap below sits, so
     * the present and the swap decision happen in the same place and in the right order: this presents first, and the
     * redirect on {@code Display.update()} then consumes the fact and skips the swap it replaced.
     */
    @Unique
    private static int celtium$swapPointCalls;

    @Inject(method = "func_175601_h", at = @At("HEAD"), require = 0)
    private void celtium$presentAtSwapPoint(CallbackInfo ci) {
        celtium$swapPointCalls++;
        if (celtium$swapPointCalls <= 3 || celtium$swapPointCalls % 300 == 0) {
            System.out.println("[Celtium] CELTIUM_SWAPPOINT calls=" + celtium$swapPointCalls);
        }
        try {
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan
                    && org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
                vulkan.celtium$presentFrame();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A failed present must not stop the frame from being shown, and the swap below still runs.
        }
    }

    /**
     * Replaces the GL swap with nothing for a frame Vulkan already presented.
     * <p>
     * A redirect is used rather than cancelling the method at this call, so only the swap itself is replaced and the
     * profiler sections and window-resize check the same method performs still run. {@code Display.update()} is static, so
     * the handler takes no arguments; a frame that was not presented calls it through exactly as before.
     */
    @org.spongepowered.asm.mixin.injection.Redirect(method = "func_175601_h",
            at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/Display;update()V"), require = 0)
    private void celtium$maybeSkipGlSwap() {
        try {
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan
                    && vulkan.celtium$consumeSwapPendingFromVulkan()) {
                // The frame is already on the swapchain, so only the swap is skipped: processMessages() is
                // everything Display.update() does besides it - GLFW's events, the mouse position and the resize
                // bookkeeping Minecraft.checkWindowResize reads - and skipping all of that froze input and resizes.
                org.lwjgl.opengl.Display.processMessages();
                return;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A frame must still be shown if this fails, so the swap below happens.
        }
        org.lwjgl.opengl.Display.update();
    }
}
