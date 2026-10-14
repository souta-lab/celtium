package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.taumc.celeritas.impl.CeltiumExplicitGc;

/**
 * Replaces the game's explicit {@code System.gc()} calls on the world-join path with a counter.
 * <p>
 * Three call sites produce a full stop-the-world collection inside one join: {@code launchIntegratedServer}'s
 * pre-load gc, {@code freeMemory}'s pair and {@code loadWorld}'s closing gc. Each measured pause was 88-103 ms
 * on the fixture, three times inside two seconds - the largest freezes in the leg. Redirecting them keeps every
 * other semantic of those methods (the reserve allocation, the display-list clear, the world load itself) and
 * drops only the full-collection request; the heap work it asked for happens at the next evacuation anyway.
 * <p>
 * The OOM-recovery call inside {@code freeMemory} goes through the same redirect: what actually frees the
 * reserve there is the {@code memoryReserve = new byte[0]} line, which still runs.
 * <p>
 * Each redirect is {@code require = 0}: what it removes is a measured pause, not a correctness property, and a
 * pause is not worth a startup failure. Measured with loliasm 5.33 present, applying this mixin to
 * {@code Minecraft} fails outright - {@code @At("INVOKE")} on {@code celeritas$skipFreeMemoryGc} cannot
 * resolve and the cause is {@code ClassNotFoundException: net.minecraft.client.Minecraft} - and the run exits
 * 1 before a frame is drawn. loliasm also mixes into {@code Minecraft} and ships a class cache over
 * {@code ClassLoader}, so two mixins are contesting one target. With {@code require = 0} an unresolvable
 * redirect is skipped and logged, the game keeps the vanilla {@code System.gc()} call at that site, and every
 * other semantic of the method is untouched.
 */
@Mixin(Minecraft.class)
public class ExplicitGcMixin {
    @Redirect(method = "func_71371_a", at = @At(value = "INVOKE", target = "Ljava/lang/System;gc()V"), require = 0)
    private void celeritas$skipJoinGc() {
        if (!CeltiumExplicitGc.ENABLED) {
            System.gc();
        } else {
            CeltiumExplicitGc.insteadOfSystemGc("launchIntegratedServer");
        }
    }

    @Redirect(method = "func_71398_f", at = @At(value = "INVOKE", target = "Ljava/lang/System;gc()V"), require = 0)
    private void celeritas$skipFreeMemoryGc() {
        if (!CeltiumExplicitGc.ENABLED) {
            System.gc();
        } else {
            CeltiumExplicitGc.insteadOfSystemGc("freeMemory");
        }
    }

    @Redirect(method = "func_71353_a", at = @At(value = "INVOKE", target = "Ljava/lang/System;gc()V"), require = 0)
    private void celeritas$skipLoadWorldGc() {
        if (!CeltiumExplicitGc.ENABLED) {
            System.gc();
        } else {
            CeltiumExplicitGc.insteadOfSystemGc("loadWorld");
        }
    }
}
