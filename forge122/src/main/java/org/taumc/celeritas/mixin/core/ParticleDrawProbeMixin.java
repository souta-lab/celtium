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
        // fxLayers is [layer][depth-mask]: layers 0-2 are the three texture sheets renderParticles draws,
        // layer 3 alone is the lit layer renderLitParticles draws. Each layer is reported on its own
        // (layers0..layers3); normal is their 0-2 sum and lit is layer 3 alone. An earlier revision of this
        // probe filed layers 1-3 together as "lit", which made layers1 a mixture of two normal sheets and
        // the lit layer - neither number meant what its name said.
        int[] layerSizes = new int[4];
        int normal = 0;
        int lit = 0;
        try {
            java.util.ArrayDeque<?>[][] layers =
                    ((ParticleManagerAccessor) (Object) this).celtium$getFxLayers();
            for (int i = 0; i < layers.length && i < layerSizes.length; i++) {
                for (int j = 0; j < layers[i].length; j++) {
                    layerSizes[i] += layers[i][j].size();
                }
            }
            normal = layerSizes[0] + layerSizes[1] + layerSizes[2];
            lit = layerSizes[3];
        } catch (RuntimeException | LinkageError error) {
            // -1 is a failed read, not an empty layer set: a reader must not file it as zero.
            java.util.Arrays.fill(layerSizes, -1);
            lit = -1;
            normal = -1;
        }
        System.out.println("[Celtium] CELTIUM_PARTICLE_CALL calls=" + calls
                + " tick=" + (mc.world == null ? -1L : mc.world.getTotalWorldTime())
                + " layers0=" + layerSizes[0]
                + " layers1=" + layerSizes[1]
                + " layers2=" + layerSizes[2]
                + " layers3=" + layerSizes[3]
                + " normal=" + normal
                + " lit=" + lit
                + " particlesSetting=" + mc.gameSettings.particleSetting);
    }
}
