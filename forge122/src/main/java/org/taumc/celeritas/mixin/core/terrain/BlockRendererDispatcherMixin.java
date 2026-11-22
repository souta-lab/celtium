package org.taumc.celeritas.mixin.core.terrain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.taumc.celeritas.impl.render.terrain.compile.ChunkBufferBuilder;
import org.taumc.celeritas.impl.render.terrain.compile.task.ChunkBuilderMeshingTask;
import org.taumc.celeritas.impl.world.cloned.CeleritasBlockAccess;

/**
 * Hooks only the model call inside renderBlock, so mods that override or inject into renderBlock itself still
 * get to run when the fast block renderer is enabled.
 *
 * <p>Port of upstream Celeritas 26ab21758 ("Route the fast block renderer through BlockRendererDispatcher",
 * QUEUE item 490). Meshing used to bypass the dispatcher entirely for MODEL blocks when
 * {@link ChunkBuilderMeshingTask#USE_NEW_BLOCK_RENDERER} was on, which broke mods that replace the dispatcher
 * (ArchitectureCraft) or inject into renderBlock (Snow! Real Magic). The meshing task now always calls the
 * dispatcher, and this wrap redirects just the inner model render to the fast renderer when the call is on
 * behalf of a chunk build (the buffer is a {@link ChunkBufferBuilder}) and the fast renderer is enabled.
 * Every other call - the flag off, a foreign buffer, a non-cloned world - runs the vanilla model renderer
 * unchanged.
 */
@Mixin(BlockRendererDispatcher.class)
public class BlockRendererDispatcherMixin {
    @WrapOperation(method = "renderBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BlockModelRenderer;renderModel(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/renderer/BufferBuilder;Z)Z"))
    private boolean celeritas$useFastBlockRenderer(BlockModelRenderer renderer, IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos, BufferBuilder buffer, boolean checkSides, Operation<Boolean> original) {
        if (ChunkBuilderMeshingTask.USE_NEW_BLOCK_RENDERER && buffer instanceof ChunkBufferBuilder chunkBuffer && world instanceof CeleritasBlockAccess blockAccess) {
            return chunkBuffer.getContext().getBlockRenderer().renderModel(model, state, pos, blockAccess, chunkBuffer.getLayer());
        }
        return original.call(renderer, world, model, state, pos, buffer, checkSides);
    }
}
