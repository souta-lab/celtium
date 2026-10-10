package org.taumc.celeritas.mixin.core;

import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Records who changes a player's dimension, with the caller chain.
 * <p>
 * The dimension fixture measured the player arriving in the Nether and then being moved back to the Overworld about
 * fifty ticks later, with full health, not in a portal and with the chunk loaded - so neither death nor the portal logic
 * explains it, and the causer has to be named rather than guessed. This logs the caller chain instead of the effect.
 * <p>
 * A diagnostic, not a behaviour: it prints and returns.
 */
@Mixin(EntityPlayerMP.class)
public class EntityPlayerMPMixin {
    @Inject(method = "changeDimension(ILnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/entity/Entity;",
            at = @At("HEAD"), require = 0)
    private void celtium$traceDimensionChange(int dimension, net.minecraftforge.common.util.ITeleporter teleporter,
            CallbackInfoReturnable<net.minecraft.entity.Entity> info) {
        try {
            StringBuilder chain = new StringBuilder();
            StackTraceElement[] stack = new Throwable().getStackTrace();
            for (int i = 1; i < Math.min(stack.length, 14); i++) {
                String name = stack[i].getClassName();
                int dot = name.lastIndexOf('.');
                chain.append('|').append(dot < 0 ? name : name.substring(dot + 1)).append('.').append(stack[i].getMethodName());
            }
            System.out.println("[Celtium] CELTIUM_CHANGE_DIM to=" + dimension + " teleporter="
                    + (teleporter == null ? "null" : teleporter.getClass().getSimpleName()) + " chain=" + chain);
        } catch (RuntimeException | LinkageError ignored) {
            // A diagnostic must never break a dimension change.
        }
    }
}
