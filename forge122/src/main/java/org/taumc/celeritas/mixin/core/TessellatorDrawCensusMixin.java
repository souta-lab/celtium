package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.core.CeltiumTessellatorCensus;

/**
 * Counts the Tessellator draws that still reach the GL path, which is the traffic the CPU-frontend conversion has to
 * take over. {@code require = 0}: a build whose {@code draw()} is renamed simply reports nothing rather than failing.
 */
@Mixin(Tessellator.class)
public class TessellatorDrawCensusMixin {

    // Both names: the source name for a development mapping, the SRG name the reobfuscated jar actually carries
    // (func_78381_a is Tessellator.draw in 1.12.2). The first run of this census produced no line at all because
    // only the source name was listed and the production jar has no such method.
    @Inject(method = {"draw", "func_78381_a"}, at = @At("HEAD"), require = 0)
    private void celtium$countDraw(CallbackInfo callback) {
        CeltiumTessellatorCensus.noteDraw();
    }
}
