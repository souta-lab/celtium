package org.taumc.celeritas.mixin.core;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.tileentity.TileEntitySignRenderer;
import net.minecraft.util.text.ITextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.taumc.celeritas.impl.render.producer.SignTextCache;

import java.util.List;

/**
 * Answers the sign renderer's per-line split and formatting from {@link SignTextCache} instead of recomputing them
 * every frame. The drawing itself is untouched: the same strings reach the same {@code drawString} calls.
 */
@Mixin(TileEntitySignRenderer.class)
public abstract class TileEntitySignRendererMixin {
    @Redirect(method = "render(Lnet/minecraft/tileentity/TileEntitySign;DDDFIF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiUtilRenderComponents;func_178908_a("
                    + "Lnet/minecraft/util/text/ITextComponent;ILnet/minecraft/client/gui/FontRenderer;ZZ)Ljava/util/List;"),
            require = 0)
    private List<ITextComponent> celtium$cachedSplit(ITextComponent component, int width, FontRenderer font,
            boolean trimSpace, boolean forceTextColor) {
        return SignTextCache.split(component, width, font, trimSpace, forceTextColor);
    }

    @Redirect(method = "render(Lnet/minecraft/tileentity/TileEntitySign;DDDFIF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/text/ITextComponent;func_150254_d()Ljava/lang/String;"),
            require = 0)
    private String celtium$cachedFormatted(ITextComponent component) {
        return SignTextCache.formatted(component);
    }
}
