package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Skips the world render itself on frames that have no camera at all.
 * <p>
 * Two legs and one probe led here. Leg {@code gv-cohort-fix137} wrote {@code mc.skipRenderWorld} from the head of
 * {@code updateCameraAndRender} and changed nothing; the frame-state probe on leg {@code gv-cohort-fix138} showed the
 * flag set to true at the heads of both {@code updateCameraAndRender} and {@code renderWorld}, with {@code renderWorld}
 * running anyway. {@code javap} on the class the game actually loads
 * ({@code forge122/build/rfg/srg_merged_minecraft.jar}, which is SRG-named throughout) gives the reason: inside
 * {@code func_78471_a} there are three calls to {@code func_175068_a} at bytecode offsets 90, 108 and 125, and
 * <b>no read of {@code skipRenderWorld} at all</b> - that gate lives only in {@code Minecraft.func_71411_J}, one level
 * up.
 * <p>
 * And that gate cannot cover this frame either: the probe reports {@code frame=1} for both sites, and the world test
 * is what makes the difference, so the world becomes non-null part-way through the frame in which the crash happens -
 * the head of {@code runGameLoop} still sees {@code world == null} and cannot set the flag in time. Hence the call
 * itself is intercepted.
 * <p>
 * {@code func_175068_a} cannot be cancelled directly: this project's HEAD/RETURN pair on it calls the backend's
 * {@code beginWorldPass} and {@code endWorldPass}, so a cancellation would leave the pass open. Redirecting the calls
 * in {@code func_78471_a} leaves that pair intact - it simply never starts for a frame with no camera - and the GUI
 * afterwards still draws.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererSkipWorldWithoutCameraMixin {

    /** The world render, as the loaded class spells it. */
    @Shadow
    protected abstract void func_175068_a(int pass, float partialTicks, long finishTimeNano);

    @Redirect(method = "func_78471_a",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;func_175068_a(IFJ)V"),
            require = 0)
    private void celtium$skipWorldWithoutCamera(EntityRenderer self, int pass, float partialTicks,
            long finishTimeNano) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.world != null && mc.player == null && mc.getRenderViewEntity() == null) {
            return;
        }
        this.func_175068_a(pass, partialTicks, finishTimeNano);
    }
}
