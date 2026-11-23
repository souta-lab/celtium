package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.entity.RenderItemFrame;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The S2 counting point of the upstream arrival census (item 727): one note per
 * {@code RenderItemFrame.doRender} entry, split by whether the frame's displayed item is a filled map.
 * <p>
 * The names were verified against the decompiled sources before this was written
 * ({@code scripts/mc-sources.sh} material on the build PC: the MCP tree's {@code doRender(EntityItemFrame,
 * double, double, double, float, float)} is {@code func_76986_a} in the SRG tree, and
 * {@code EntityItemFrame.getDisplayedItem()} is {@code func_82335_i}); the descriptor is spelled in both
 * names because the class also carries the {@code Render<Entity>} bridge overload. Vanilla's own map test
 * inside the renderer is {@code instanceof ItemMap}; the census deliberately counts the narrower
 * {@code Items.FILLED_MAP} identity, which is the item the map-wall scene's frames display.
 * <p>
 * {@code require = 0} follows the house style for census hooks: a run where this mixin never applies is
 * not silent - the census's positive control (steady-phase {@code renders} equal to the gathered item
 * frames) fails on its face and the run is not used for judgement.
 */
@Mixin(RenderItemFrame.class)
public abstract class RenderItemFrameArrivalMixin {

    @Inject(method = {
            "doRender(Lnet/minecraft/entity/item/EntityItemFrame;DDDFF)V",
            "func_76986_a(Lnet/minecraft/entity/item/EntityItemFrame;DDDFF)V"},
            at = @At("HEAD"), require = 0)
    private void celtium$noteFrameRender(EntityItemFrame entity, double x, double y, double z,
            float entityYaw, float partialTicks, CallbackInfo info) {
        if (org.soutalab.celtium.vulkan.UpstreamArrivalCensus.ENABLED) {
            ItemStack stack = entity == null ? null : entity.getDisplayedItem();
            org.soutalab.celtium.vulkan.UpstreamArrivalCensus.noteRender(
                    stack != null && !stack.isEmpty() && stack.getItem() == Items.FILLED_MAP);
        }
    }
}
