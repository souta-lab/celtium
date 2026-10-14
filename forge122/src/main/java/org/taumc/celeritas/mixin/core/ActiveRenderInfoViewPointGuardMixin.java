package org.taumc.celeritas.mixin.core;

import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Answers the viewpoint block lookup when there is no camera entity, instead of dereferencing null.
 * <p>
 * Measured 2026-10-15 (leg {@code gv-cohort-fix130}, the first GL-less run past the splash, the atlas and the
 * render-ahead fence): the client reached world entry and died with
 * {@code NullPointerException: Cannot read field "field_70169_q" because "entityIn" is null} at
 * {@code ActiveRenderInfo.projectViewFromEntity(ActiveRenderInfo.java:57)} - {@code entityIn.prevPosX} - reached
 * through {@code getBlockStateAtEntityViewpoint} from
 * {@code EntityRenderer.getFOVModifier(EntityRenderer.java:535)} and
 * {@code setupCameraTransform}, inside {@code renderWorld}.
 * <p>
 * The entity is {@code mc.getRenderViewEntity()}, which is null during world entry while {@code mc.world} already
 * exists. That window is the one {@link EntityRendererFogGuardMixin} documents for fog, and it is a property of the
 * vanilla client rather than of this renderer: the guard's comment records it being reproduced twice on the GL
 * (non-GL-less) comparison leg at world load. The GL-less leg happened not to render a frame in it before; now that
 * it does, the same window is reached through a second method.
 * <p>
 * Every caller in the game passes {@code mc.getRenderViewEntity()} for this argument - EntityRenderer's FOV path
 * (line 540), its fog path (693), and the two block-at-viewpoint lookups (1843, 1958) - so guarding the lookup
 * itself covers all four rather than one. Air is the honest answer: with no camera entity there is no viewpoint
 * block to project, and air is not water, so the FOV and fog paths behave as "not in water" instead of throwing.
 * <p>
 * The projection underneath it has two more callers that do not go through the lookup - EntityRenderer's own fog
 * colour path (1855) and {@code BlockLiquid} (547) - so it is guarded too, with the constant origin, which is a
 * usable point where a null dereference is not. Two small guards at the two entry points of one pair, rather than an
 * attempt to catch every caller of either individually.
 */
@Mixin(ActiveRenderInfo.class)
public abstract class ActiveRenderInfoViewPointGuardMixin {

    @Inject(method = "func_186703_a", at = @At("HEAD"), cancellable = true, require = 0)
    private static void celtium$answerWithoutCameraEntity(World worldIn, Entity entityIn, float partialTicks,
            CallbackInfoReturnable<IBlockState> info) {
        if (entityIn == null) {
            info.setReturnValue(Blocks.AIR.getDefaultState());
        }
    }

    @Inject(method = "func_178806_a", at = @At("HEAD"), cancellable = true, require = 0)
    private static void celtium$projectWithoutCameraEntity(Entity entityIn, double partialTicks,
            CallbackInfoReturnable<Vec3d> info) {
        if (entityIn == null) {
            info.setReturnValue(Vec3d.ZERO);
        }
    }


    /**
     * Keeps the previous camera state while there is no camera entity.
     * <p>
     * Measured on legs {@code gv-cohort-fix132} and {@code gv-cohort-fix134}: {@code NullPointerException: Cannot read
     * field "field_70125_A" because "entityplayerIn" is null} inside {@code updateRenderInfo}, reached from
     * {@code EntityRenderer.updateCameraAndRender:1330}, which passes the render view entity without checking it. A
     * fallback answering {@code Minecraft.getRenderViewEntity} with the player did not prevent it, because that call
     * site reads the field rather than going through the getter.
     * <p>
     * With no camera entity the static camera fields keep the previous frame's values instead of being computed from
     * null, which is what the fog guard's comment describes vanilla doing with the fog colour.
     * <p>
     * The guarded method is the Forge-added {@code (Entity, boolean)} overload, which has no SRG mapping and keeps
     * its source name at runtime - hence {@code remap = false}. Naming it in a vanilla class without that flag
     * aborted the class definition and killed the run with {@code NoClassDefFoundError} on leg
     * {@code gv-cohort-fix133}; the flag is what makes an unmapped member addressable. The {@code (EntityPlayer,
     * boolean)} overload is not targeted: it only delegates, and a null argument arrives at the method below.
     */
    @Inject(method = "updateRenderInfo", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void celtium$keepCameraStateWithoutEntity(Entity entityIn, boolean thirdPerson, CallbackInfo info) {
        if (entityIn == null) {
            info.cancel();
        }
    }
}
