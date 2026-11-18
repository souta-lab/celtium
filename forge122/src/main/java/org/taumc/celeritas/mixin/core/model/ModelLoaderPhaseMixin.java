package org.taumc.celeritas.mixin.core.model;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.impl.model.ModelPhaseTrace;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Item 338a instrument: blocks / itemModels / spriteSet brackets and the bake accumulation inside
 * {@code ModelLoader}. The bake loop calls {@code IModel.bake} directly (Forge ModelLoader source,
 * setupModelRegistry), so the bake phase is accumulated at that invocation point.
 */
@Mixin(ModelLoader.class)
public class ModelLoaderPhaseMixin {
    @Shadow
    private Map<ModelResourceLocation, IModel> stateModels;

    @Shadow
    private Map<ResourceLocation, Exception> loadingExceptions;

    @Unique
    private int celtium$stateModelsAtBlocksStart;

    @Unique
    private int celtium$stateModelsAtItemsStart;

    @Unique
    private int celtium$exceptionsAtItemsStart;

    @Inject(method = "func_177570_a", at = @At("HEAD"), require = 0)
    private void celtium$setupBegin(CallbackInfoReturnable<?> ci) {
        ModelPhaseTrace.setupBegin();
    }

    @Inject(method = "func_177570_a", at = @At("RETURN"), require = 0)
    private void celtium$setupEnd(CallbackInfoReturnable<?> ci) {
        ModelPhaseTrace.setupEnd();
    }

    @Inject(method = "func_188640_b", at = @At("HEAD"), require = 0)
    private void celtium$blocksBegin(CallbackInfo ci) {
        if (!ModelPhaseTrace.ENABLED) {
            return;
        }
        this.celtium$stateModelsAtBlocksStart = this.stateModels.size();
        ModelPhaseTrace.blocksBegin();
    }

    @Inject(method = "func_188640_b", at = @At("RETURN"), require = 0)
    private void celtium$blocksEnd(CallbackInfo ci) {
        if (!ModelPhaseTrace.ENABLED) {
            return;
        }
        int blocks = 0;
        for (Block block : Block.REGISTRY) {
            if (block.getRegistryName() != null) {
                blocks++;
            }
        }
        ModelPhaseTrace.blocksEnd(blocks, this.stateModels.size() - this.celtium$stateModelsAtBlocksStart);
    }

    @Inject(method = "func_177590_d", at = @At("HEAD"), require = 0)
    private void celtium$itemModelsBegin(CallbackInfo ci) {
        if (!ModelPhaseTrace.ENABLED) {
            return;
        }
        this.celtium$stateModelsAtItemsStart = this.stateModels.size();
        this.celtium$exceptionsAtItemsStart = this.loadingExceptions.size();
        ModelPhaseTrace.itemModelsBegin();
    }

    @Inject(method = "func_177590_d", at = @At("RETURN"), require = 0)
    private void celtium$itemModelsEnd(CallbackInfo ci) {
        if (!ModelPhaseTrace.ENABLED) {
            return;
        }
        int items = 0;
        for (Item item : Item.REGISTRY) {
            if (item.getRegistryName() != null) {
                items++;
            }
        }
        ModelPhaseTrace.itemModelsEnd(items,
                this.stateModels.size() - this.celtium$stateModelsAtItemsStart,
                this.loadingExceptions.size() - this.celtium$exceptionsAtItemsStart);
    }

    @Inject(method = "func_177570_a", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureMap;func_174943_a(Lnet/minecraft/client/resources/IResourceManager;Lnet/minecraft/client/renderer/texture/ITextureMapPopulator;)V"),
            require = 0)
    private void celtium$spriteSetBegin(CallbackInfoReturnable<?> ci, @Local Set<ResourceLocation> textures) {
        if (!ModelPhaseTrace.ENABLED) {
            return;
        }
        ModelPhaseTrace.spriteSetBegin();
        ModelPhaseTrace.spriteSetNoteCount(textures == null ? -1 : textures.size());
    }

    @Inject(method = "func_177570_a", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureMap;func_174943_a(Lnet/minecraft/client/resources/IResourceManager;Lnet/minecraft/client/renderer/texture/ITextureMapPopulator;)V",
            shift = At.Shift.AFTER), require = 0)
    private void celtium$spriteSetEnd(CallbackInfoReturnable<?> ci) {
        ModelPhaseTrace.spriteSetEnd();
    }

    @Inject(method = "func_177570_a", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/client/model/IModel;bake(Lnet/minecraftforge/common/model/IModelState;Lnet/minecraft/client/renderer/vertex/VertexFormat;Ljava/util/function/Function;)Lnet/minecraft/client/renderer/block/model/IBakedModel;"),
            require = 0)
    private void celtium$bakeCallBegin(CallbackInfoReturnable<?> ci) {
        if (!ModelPhaseTrace.ENABLED) {
            return;
        }
        ModelPhaseTrace.noteDistinctModels(new HashSet<>(this.stateModels.values()).size());
        ModelPhaseTrace.bakeCallBegin();
    }

    @Inject(method = "func_177570_a", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/client/model/IModel;bake(Lnet/minecraftforge/common/model/IModelState;Lnet/minecraft/client/renderer/vertex/VertexFormat;Ljava/util/function/Function;)Lnet/minecraft/client/renderer/block/model/IBakedModel;",
            shift = At.Shift.AFTER), require = 0)
    private void celtium$bakeCallEnd(CallbackInfoReturnable<?> ci) {
        ModelPhaseTrace.bakeCallEnd();
    }
}
