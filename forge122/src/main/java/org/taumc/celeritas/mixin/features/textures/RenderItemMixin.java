package org.taumc.celeritas.mixin.features.textures;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.taumc.celeritas.impl.render.terrain.sprite.SpriteUtil;

@Mixin(RenderItem.class)
public class RenderItemMixin {
    // renderQuads is func_191970_a in the 1.12.2 SRG set (mcp_stable/39 srg-mcp.srg). The name this
    // list carried until 2026-11-22, func_175032_a, exists in no 1.12.2 mapping file, and the refmap
    // is empty, so in the SRG runtime neither listed name could match and this handler was dead in
    // production while alive in dev.
    @ModifyExpressionValue(method = { "func_191970_a", "renderQuads" }, at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
    private Object markSpriteActive(Object o) {
        if (o instanceof BakedQuad quad) {
            var sprite = quad.getSprite();
            if (sprite != null) {
                SpriteUtil.markSpriteActive(sprite);
            }
        }
        return o;
    }
}
