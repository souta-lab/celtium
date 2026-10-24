package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.tileentity.TileEntityEndGatewayRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityEndPortalRenderer;
import net.minecraft.tileentity.TileEntityEndPortal;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.render.producer.EndPortalProducer;

/**
 * Hands a portal block to {@link EndPortalProducer} instead of drawing its fifteen layers on the spot. The gateway's
 * renderer reaches this through {@code super.render}, after its own beam, so {@code this} answers its own layer count
 * and top offset; it is told apart by class because vanilla draws its body with fog disabled.
 */
@Mixin(TileEntityEndPortalRenderer.class)
public abstract class TileEntityEndPortalRendererMixin {
    @Shadow
    protected abstract int getPasses(double distanceSquared);

    @Shadow
    protected abstract float getOffset();

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntityEndPortal;DDDFIF)V", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void celtium$collect(TileEntityEndPortal te, double x, double y, double z, float partialTicks,
            int destroyStage, float alpha, CallbackInfo info) {
        if (!EndPortalProducer.collecting()) {
            return;
        }
        int faces = 0;
        if (te.shouldRenderFace(EnumFacing.SOUTH)) faces |= EndPortalProducer.SOUTH;
        if (te.shouldRenderFace(EnumFacing.NORTH)) faces |= EndPortalProducer.NORTH;
        if (te.shouldRenderFace(EnumFacing.EAST)) faces |= EndPortalProducer.EAST;
        if (te.shouldRenderFace(EnumFacing.WEST)) faces |= EndPortalProducer.WEST;
        if (te.shouldRenderFace(EnumFacing.DOWN)) faces |= EndPortalProducer.DOWN;
        if (te.shouldRenderFace(EnumFacing.UP)) faces |= EndPortalProducer.UP;
        EndPortalProducer.collect((Object) this instanceof TileEntityEndGatewayRenderer, x, y, z, faces,
                getPasses(x * x + y * y + z * z), getOffset());
        info.cancel();
    }
}
