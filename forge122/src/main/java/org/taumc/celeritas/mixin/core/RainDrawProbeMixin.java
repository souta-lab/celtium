package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostic for task-9: <b>is the game calling the rain/snow draw at all?</b>
 * <p>
 * Rain is format 5 in the immediate path ({@code EntityRenderer.renderRainSnow} begins a
 * {@code PARTICLE_POSITION_TEX_COLOR_LMAP} batch), and the measured fact this exists for is that no format-5 batch
 * reaches the immediate path under GL-less at all - not accepted, not offered, absent from every histogram line -
 * while the fixture's own report says {@code raining=true rainStrength=0.49} at the capture. The draw's first gate
 * is {@code world.getRainStrength(partialTicks) > 0}, so the count and that value together separate "the draw was
 * never called" from "the draw ran and its batch was lost". See {@link CloudDrawProbeMixin} for the flag.
 */
@Mixin(EntityRenderer.class)
public abstract class RainDrawProbeMixin {

    private static final java.util.concurrent.atomic.AtomicLong celtium$probeRainCalls =
            new java.util.concurrent.atomic.AtomicLong();
    private static final boolean celtium$PROBE_DRAW_CALLS =
            Boolean.getBoolean("celtium.probe.draws");
    private static final int celtium$PROBE_EVERY =
            Integer.getInteger("celtium.probe.every", 50).intValue();

    @Inject(method = "func_78474_d", at = @At("HEAD"), require = 0)
    private void celtium$probeRenderRainSnow(float partialTicks, CallbackInfo info) {
        if (!celtium$PROBE_DRAW_CALLS) {
            return;
        }
        long calls = celtium$probeRainCalls.incrementAndGet();
        if (calls > 3 && (celtium$PROBE_EVERY <= 0 || calls % celtium$PROBE_EVERY != 0)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        System.out.println("[Celtium] CELTIUM_RAIN_CALL calls=" + calls
                + " tick=" + (mc.world == null ? -1L : mc.world.getTotalWorldTime())
                + " rainStrength=" + (mc.world == null ? -1.0F : mc.world.getRainStrength(partialTicks))
                + " raining=" + (mc.world != null && mc.world.getWorldInfo().isRaining())
                + " rainTime=" + (mc.world == null ? -1 : mc.world.getWorldInfo().getRainTime())
                + " weatherRenderer=" + (mc.world != null && mc.world.provider.getWeatherRenderer() != null));
    }
}
