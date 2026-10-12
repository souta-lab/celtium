package org.taumc.celeritas.mixin.core;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dropped-item render distance, after Angelica PR #653 (`MixinRenderGlobal_ItemRenderDist` /
 * `MixinEntityItem_RenderDist`): vanilla gives a dropped item the generic entity range of
 * {@code box.averageEdgeLength * 64} - about 16 blocks for the 0.25m item box. The Angelica change
 * bases the item range on a real block count instead (32), and shrinks it dynamically when very many
 * item entities are already in view, which is the degenerate scene (a mob farm's item overflow, a
 * cactus farm unlucky tick) that turns the per-item render path into the frame's cost.
 *
 * <p>Implemented on {@link Entity#isInRangeToRenderDist} with an instanceof guard because
 * {@link EntityItem} does not declare the method in 1.12.2 - a mixin into the subclass has nothing to
 * attach to. Non-item entities keep the original expression untouched.</p>
 *
 * <p>The "already drawn" census resets on the world-tick boundary ({@code getTotalWorldTime}) - the
 * only cheap clock an entity carries. One tick covers a handful of frames, so the soft cap counts
 * items drawn over a tick rather than per frame; the shrink still engages only in scenes already
 * pathological. Behaviour deltas versus vanilla: the item range widens 16 -&gt; 32 blocks (Angelica's
 * setting) and contracts past the cap. Kill switch {@code -Dceltium.droppedItemRange=false}.</p>
 */
@Mixin(Entity.class)
public abstract class EntityItemRangeMixin {

    /** Kill flag: {@code -Dceltium.droppedItemRange=false} restores the vanilla 16-ish blocks. */
    private static final boolean CELTIUM_RANGE = !"false".equals(System.getProperty("celtium.droppedItemRange", "true"));
    /** Angelica's base range for a dropped item, in blocks. */
    private static final double CELTIUM_BASE_BLOCKS =
            Double.parseDouble(System.getProperty("celtium.droppedItemRangeBlocks", "32"));
    /** Items drawn per tick before the range starts shrinking; Angelica's hard ceiling is 2048 items. */
    private static final int CELTIUM_SOFT_CAP =
            Integer.parseInt(System.getProperty("celtium.droppedItemRangeSoftCap", "256"));
    /** Floor the shrink never goes below - nearer items keep drawing even in a flood. */
    private static final double CELTIUM_MIN_BLOCKS = 4.0;

    /** The tick the running count belongs to; a stale tick resets it. */
    private static long celtium$rangeTick = Long.MIN_VALUE;
    /** Items this tick that were already allowed to draw. */
    private static int celtium$visibleItems;

    @Inject(method = "func_70112_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$itemRenderRange(double distance, CallbackInfoReturnable<Boolean> info) {
        Entity self = (Entity) (Object) this;
        if (!CELTIUM_RANGE || !(self instanceof EntityItem)) {
            return;
        }
        World world = self.world;
        if (world != null && world.getTotalWorldTime() != celtium$rangeTick) {
            celtium$rangeTick = world.getTotalWorldTime();
            celtium$visibleItems = 0;
        }
        double cap = CELTIUM_BASE_BLOCKS;
        if (celtium$visibleItems >= CELTIUM_SOFT_CAP) {
            // Over the cap: shrink so the whole set stays near-bounded - sqrt keeps the first few
            // hundred extra items still reachable instead of hard-cutting at the cap.
            cap = CELTIUM_BASE_BLOCKS * Math.sqrt((double) CELTIUM_SOFT_CAP / celtium$visibleItems);
            if (cap < CELTIUM_MIN_BLOCKS) cap = CELTIUM_MIN_BLOCKS;
        }
        boolean visible = distance < cap * cap;
        if (visible) celtium$visibleItems++;
        info.setReturnValue(visible);
    }
}
