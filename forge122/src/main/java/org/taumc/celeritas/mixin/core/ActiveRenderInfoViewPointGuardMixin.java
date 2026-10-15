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
     * The descriptor is spelled out because this class has two {@code updateRenderInfo} overloads: naming the method
     * alone let Mixin pick the wrong one and fail with {@code InvalidInjectionException: Invalid descriptor} on legs
     * {@code gv-cohort-fix133} and {@code gv-cohort-fix135} - and that failure aborts the class definition, so the run
     * died with {@code NoClassDefFoundError} rather than a warning, with {@code require = 0} in place either way.
     * {@code remap = false} is right because the runtime jar carries the source names for this class.
     */
    /**
     * Fills the buffers the terrain renderer reads when there is no GL context to fill them.
     * <p>
     * {@code updateRenderInfo} fills MODELVIEW and PROJECTION by asking GL, and the terrain's
     * {@code createChunkRenderMatrices} hands those two buffers straight to the chunk renderer. With no GL context
     * they stay at zero, and a zero matrix sends every vertex to (0,0,0,0): measured 2026-10-15 (leg
     * gv-cohort-solid18) as CELTIUM_GROUP_DEPTH row1=0.0,0.0,0.0,0.0 mvT=0.0,0.0,0.0, with 179369 draw calls
     * recorded and 92% of the presented image black - and an A/B that made the fragment shader return solid
     * magenta changed nothing, which is what says the geometry never rasterises.
     * <p>
     * These are the OpenGL-convention matrices: the backend passes the projection through vulkanProjection(),
     * which rewrites the z column for Vulkan's [0,1] range, and the viewport's positive height is deliberate
     * because that helper applies the Vulkan convention. Applying either here as well would convert twice, which
     * is what an earlier attempt at this did.
     * <p>
     * The descriptor is spelled out because this name has two overloads; naming only the method aborts the
     * definition of the target class (see AGENTS.md).
     */
    @Inject(method = "updateRenderInfo(Lnet/minecraft/entity/Entity;Z)V", at = @At("TAIL"), require = 0)
    private static void celtium$fillGllessMatrices(Entity entityIn, boolean thirdPerson, CallbackInfo info) {
        if (!org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
            return;
        }

        org.joml.Matrix4f projection = org.taumc.celeritas.core.GllessViewProjection.projection();
        org.joml.Matrix4f modelView = org.taumc.celeritas.core.GllessViewProjection.modelView();
        if (projection == null || modelView == null) {
            return;
        }

        celtium$store(org.taumc.celeritas.mixin.core.terrain.ActiveRenderInfoAccessor.getProjectionMatrix(), projection);
        celtium$store(org.taumc.celeritas.mixin.core.terrain.ActiveRenderInfoAccessor.getModelViewMatrix(), modelView);
    }

    /** Overwrites a 16-float buffer from a matrix, in the column-major order GL and the shaders expect. */
    private static void celtium$store(java.nio.FloatBuffer buffer, org.joml.Matrix4f matrix) {
        if (buffer == null) {
            return;
        }
        int position = buffer.position();
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                buffer.put(position + (column * 4) + row, matrix.get(row, column));
            }
        }
    }
    @Inject(method = "updateRenderInfo(Lnet/minecraft/entity/Entity;Z)V", at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private static void celtium$keepCameraStateWithoutEntity(Entity entityIn, boolean thirdPerson, CallbackInfo info) {
        if (entityIn == null) {
            info.cancel();
        }
    }
}
