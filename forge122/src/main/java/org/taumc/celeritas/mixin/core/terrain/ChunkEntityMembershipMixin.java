package org.taumc.celeritas.mixin.core.terrain;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.render.entity.EntityMembershipEpoch;

/** These original Chunk methods own the entity-map mutation/load boundaries the gather cache observes. */
@Mixin(Chunk.class)
public abstract class ChunkEntityMembershipMixin {
    @Shadow @Final private World world;

    @Inject(method = {"func_76612_a", "func_76608_a", "func_76631_c", "func_76623_d"},
            at = @At("RETURN"))
    private void celtium$entityMembershipChanged(CallbackInfo info) {
        EntityMembershipEpoch.changed(this.world);
    }
}
