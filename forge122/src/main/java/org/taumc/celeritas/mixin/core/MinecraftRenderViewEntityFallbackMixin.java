package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Answers {@code getRenderViewEntity()} with the player while the render view entity has not been assigned yet.
 * <p>
 * This is the choke point for the world-entry window this project has now paid for four times. The client reaches a
 * frame with {@code mc.world} set but no render view entity, and vanilla dereferences it unguarded:
 * <ul>
 * <li>{@code EntityRenderer.updateFogColor} - the long-standing crash {@code EntityRendererFogGuardMixin} guards
 * (legs before 2026-10-15, reproduced on the GL comparison leg at world load);</li>
 * <li>{@code ActiveRenderInfo.getBlockStateAtEntityViewpoint} and {@code projectViewFromEntity} - measured on leg
 * {@code gv-cohort-fix131}, guarded in {@code ActiveRenderInfoViewPointGuardMixin};</li>
 * <li>{@code ActiveRenderInfo.updateRenderInfo} - measured on leg {@code gv-cohort-fix132}.</li>
 * </ul>
 * Each of those is a consumer answering a null it should not have been handed. The producer is this method, and
 * vanilla itself assigns {@code renderViewEntity = player} a few statements later on both world-load paths
 * (Minecraft.java:2662 in the integrated-server branch and 2694 after the spawn packet) - so returning the player
 * here is what the client is about to do anyway, and it is what makes those frames renderable rather than merely
 * survivable.
 * <p>
 * The guards above are kept: they also cover a world with no player at all, where this fallback has nothing to
 * return, and a null render view entity can still be set deliberately.
 * <p>
 * The target name is the SRG form of a vanilla method, which this runtime carries and which needs no mapping entry;
 * the shadowed fields keep their source names, the way the rest of this project's mixins reach vanilla fields.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftRenderViewEntityFallbackMixin {

    @Shadow
    private Entity renderViewEntity;

    @Shadow
    private EntityPlayerSP player;

    @Inject(method = "func_175606_aa", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$fallBackToPlayer(CallbackInfoReturnable<Entity> info) {
        if (this.renderViewEntity == null && this.player != null) {
            info.setReturnValue(this.player);
        }
    }
}
