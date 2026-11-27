package org.soutalab.celtium.forge.ui;

import net.minecraft.client.Minecraft;
import org.soutalab.celtium.core.frame.FrameContext;
import org.soutalab.celtium.vulkan.pack.PackFrameEnvironment;
import org.soutalab.celtium.vulkan.pack.ShaderPackRuntime;

import java.nio.file.Path;

/**
 * The forge side's single seat for the shader pack runtime: owns the runtime instance (rooted
 * at {@code <gameDir>/shaderpacks}), the {@link CeltiumConfig} the selection persists in, and
 * the native frame seat the frame owner opens while its frame command buffer is recording.
 *
 * <p>Frame flow: the frame-drive mixin calls {@link #onWorldPassBegin(float)} at the head of
 * every world pass - it syncs the selection from the config, feeds the pack's frame state and,
 * when the native frame seat is open (the frame coordinator's integration point), records the
 * pack frame through the runtime. With no seat open the hook is state feeding only, so it is
 * safe in every configuration.
 */
public final class ShaderPackAccess {

    private static ShaderPackRuntime runtime;
    private static CeltiumConfig config;
    private static String syncedSelection;

    private ShaderPackAccess() {
    }

    public static synchronized CeltiumConfig config() {
        if (config == null) {
            try {
                config = org.taumc.celeritas.CeleritasVintage.options();
            } catch (IllegalStateException notYetAvailable) {
                config = CeltiumConfig.load();
            }
        }
        return config;
    }

    public static synchronized ShaderPackRuntime runtime() {
        if (runtime == null) {
            Path gameDir = Minecraft.getMinecraft().gameDir.toPath();
            runtime = new ShaderPackRuntime(gameDir.resolve("shaderpacks"));
        }
        return runtime;
    }

    /**
     * Selects a pack (or disables packs for an empty name): persists the choice in the config
     * and activates it in the runtime. Called by the shader pack screen.
     *
     * @return true when the resulting state matches the request
     */
    public static synchronized boolean selectPack(String packName) {
        CeltiumConfig cfg = config();
        cfg.shaders.shaderPack = packName == null ? "" : packName;
        cfg.save();
        return syncSelection();
    }

    /** Applies the runtime's pending option values: recompiles the pack and persists them. */
    public static synchronized boolean applyPendingOptions() {
        ShaderPackRuntime rt = runtime();
        boolean applied = rt.applyPendingOptions();
        if (applied) {
            CeltiumConfig cfg = config();
            cfg.shaders.shaderPackOptionOverrides.clear();
            cfg.shaders.shaderPackOptionOverrides.putAll(rt.pendingOptionValues());
            cfg.save();
        }
        return applied;
    }

    /** Brings the runtime's activation in line with the persisted selection. */
    public static synchronized boolean syncSelection() {
        CeltiumConfig cfg = config();
        String wanted = cfg.shaders.shaderPack == null ? "" : cfg.shaders.shaderPack;
        if (wanted.equals(syncedSelection)) {
            return runtime().packActive() == !wanted.isEmpty();
        }
        syncedSelection = wanted;
        ShaderPackRuntime rt = runtime();
        if (wanted.isEmpty()) {
            rt.deactivate();
            return true;
        }
        rt.pendingOptionValues().clear();
        rt.pendingOptionValues().putAll(cfg.shaders.shaderPackOptionOverrides);
        return rt.activate(wanted);
    }

    /** The frame-drive hook's entry: state feeding, selection sync, and the pack frame. */
    public static void onWorldPassBegin(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.getRenderViewEntity() == null) {
            return;
        }
        ShaderPackRuntime rt;
        synchronized (ShaderPackAccess.class) {
            syncSelection();
            rt = runtime();
            if (!rt.packActive()) {
                return;
            }
        }
        // Feed the pack's frame state from the live world, seat or no seat.
        ShaderFrameStateFeeder.fill(mc, rt.compiledPack().frameState(), partialTicks);
        // The native frame seat lives on ShaderPackRuntime (the forge module cannot name
        // LWJGL types); the frame owner opens it there for the duration of one frame.
        if (!ShaderPackRuntime.nativeFrameOpen()) {
            return;
        }
        PackFrameEnvironment env = ShaderFrameStateFeeder.buildEnvironment(
                mc, rt.compiledPack().frameState(), partialTicks,
                ShaderPackRuntime.seatSwapchainTargets());
        FrameContext ctx = new FrameContext(ShaderPackRuntime.seatFrameId(),
                mc.world.getWorldTime() > Integer.MAX_VALUE ? Integer.MAX_VALUE
                        : (int) mc.world.getWorldTime(),
                partialTicks);
        rt.recordSeatedFrame(ctx, env);
    }
}
