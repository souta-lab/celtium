package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostic for task-9: <b>is the particle draw being called, and does it have particles in its layers?</b>
 * <p>
 * The fixture spawns 24 still FLAME particles one tick before the capture and reports
 * {@code particlesSpawned=24}, and the immediate path reports {@code acceptedByFormat 5:0} - no format-5 batch was
 * offered or accepted in the GL-less leg. {@code ParticleManager.renderParticles} is called every frame whether or
 * not a layer is non-empty, so a call count alone cannot decide it; the layer sizes are printed beside the count for
 * that reason. {@code fxLayers} is private, so the accessor mixin {@link ParticleManagerAccessor} reads it.
 * See {@link CloudDrawProbeMixin} for the flag.
 */
@Mixin(ParticleManager.class)
public abstract class ParticleDrawProbeMixin {

    private static final java.util.concurrent.atomic.AtomicLong celtium$probeParticleCalls =
            new java.util.concurrent.atomic.AtomicLong();
    private static final boolean celtium$PROBE_DRAW_CALLS =
            Boolean.getBoolean("celtium.probe.draws");
    private static final int celtium$PROBE_EVERY =
            Integer.getInteger("celtium.probe.every", 50).intValue();

    @Inject(method = "func_78874_a", at = @At("HEAD"), require = 0)
    private void celtium$probeRenderParticles(Entity entityIn, float partialTicks, CallbackInfo info) {
        if (!celtium$PROBE_DRAW_CALLS) {
            return;
        }
        long calls = celtium$probeParticleCalls.incrementAndGet();
        if (calls > 3 && (celtium$PROBE_EVERY <= 0 || calls % celtium$PROBE_EVERY != 0)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int lit = 0;
        int normal = 0;
        try {
            java.util.ArrayDeque<?>[][] layers =
                    ((ParticleManagerAccessor) (Object) this).celtium$getFxLayers();
            for (int i = 0; i < layers.length; i++) {
                for (int j = 0; j < layers[i].length; j++) {
                    if (i == 0) {
                        normal += layers[i][j].size();
                    } else {
                        lit += layers[i][j].size();
                    }
                }
            }
        } catch (RuntimeException | LinkageError error) {
            lit = -1;
            normal = -1;
        }
        System.out.println("[Celtium] CELTIUM_PARTICLE_CALL calls=" + calls
                + " tick=" + (mc.world == null ? -1L : mc.world.getTotalWorldTime())
                + " layers0=" + normal
                + " layers1=" + lit
                + " particlesSetting=" + mc.gameSettings.particleSetting);
    }
}
