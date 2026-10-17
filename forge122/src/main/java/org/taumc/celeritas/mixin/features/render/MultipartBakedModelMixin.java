package org.taumc.celeritas.mixin.features.render;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.MultipartBakedModel;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Memoizes the multipart selector match for {@link MultipartBakedModel#getQuads}.
 *
 * <p>The vanilla body walks {@code selectors} and calls {@code predicate.apply(state)} for every
 * entry on every quads request - chunk meshing does that once per block face per section, so a
 * multipart block (fences, walls, glass panes, redstone) re-evaluates all of its {@link
 * net.minecraft.client.renderer.block.model.multipart.ConditionPropertyValue} predicates - each one
 * a {@code BlockStateContainer} property lookup plus an {@code ImmutableMap} value fetch - on every
 * single {@code getQuads}. The JFR attribution (round 201, {@code jfratt3.jfr}) puts that predicate
 * cluster ({@code ConditionPropertyValue.apply} + {@code RegularImmutableMap.get} + the property
 * reads) near the top of the meshing threads' hot frames.
 *
 * <p>Which predicates match depends only on the {@link IBlockState} - never on {@code side} or
 * {@code rand} - so the matched model list is computed once per state and reused. The state itself
 * is the key; {@code StateContainer} interning means identical states share an instance, and the
 * {@code equals}/{@code hashCode} path covers any that do not. {@link #getQuads} is called from the
 * {@code Chunk Render Task Executor} worker threads, so the cache is a {@link ConcurrentHashMap}
 * with the compute done outside the map's bin lock (a {@code get} then {@code putIfAbsent}); two
 * workers may compute the same state once, which is benign because the result is identical.
 *
 * <p>Behaviour is unchanged: the returned list is still a fresh {@code ArrayList} of the matched
 * sub-models' own {@code getQuads(state, side, rand++)} - only the redundant selector walk is
 * skipped.
 */
@Mixin(MultipartBakedModel.class)
public abstract class MultipartBakedModelMixin
{
    @Shadow
    @Final
    private Map<Predicate<IBlockState>, IBakedModel> selectors;

    @Unique
    private final Map<IBlockState, List<IBakedModel>> celeritas$matchedSelectors = new ConcurrentHashMap<>();

    @Inject(method = "getQuads", at = @At("HEAD"), cancellable = true)
    private void celeritas$getQuadsCached(@Nullable IBlockState state, @Nullable EnumFacing side, long rand,
            CallbackInfoReturnable<List<BakedQuad>> cir)
    {
        List<BakedQuad> list = Lists.<BakedQuad>newArrayList();
        if (state != null)
        {
            List<IBakedModel> models = this.celeritas$matchedSelectors.get(state);
            if (models == null)
            {
                models = Lists.<IBakedModel>newArrayList();
                for (Entry<Predicate<IBlockState>, IBakedModel> entry : this.selectors.entrySet())
                {
                    if (((Predicate)entry.getKey()).apply(state))
                    {
                        models.add(entry.getValue());
                    }
                }
                this.celeritas$matchedSelectors.putIfAbsent(state, models);
            }
            for (IBakedModel model : models)
            {
                list.addAll(model.getQuads(state, side, rand++));
            }
        }
        cir.setReturnValue(list);
    }
}
