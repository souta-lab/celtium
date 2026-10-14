package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketJoinGame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports whether the client receives and finishes the join packet.
 * <p>
 * Round 842b left an asymmetry that cannot be explained by the renderer: the integrated server logs
 * {@code CeltiumValidation logged in with entity id 4} and {@code joined the game}, while the client sits with
 * {@code world=true}, {@code player=false}, no render view entity and a loading screen for 1200 client ticks with
 * {@code worldTicks=0}. Then round 103 checked the other half: {@code Minecraft.func_71353_a} is the real
 * {@code loadWorld} - javap shows it writing {@code field_71441_e} - and both overloads are hooked, yet no call with a
 * non-null world was ever recorded. If the client had handled the join, one would have been.
 * <p>
 * So the question is whether the packet arrives at all. Two bounded lines - one at the head, one at the return - say
 * it: nothing means the packet never reaches the handler, which puts the defect in the network path rather than in
 * rendering.
 */
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerJoinProbeMixin {

    @Inject(method = "func_147282_a", at = @At("HEAD"), require = 0)
    private void celtium$noteJoinReceived(SPacketJoinGame packet, CallbackInfo info) {
        Minecraft mc = Minecraft.getMinecraft();
        System.out.println("[Celtium] CELTIUM_JOIN received player=" + (mc != null && mc.player != null)
                + " world=" + (mc != null && mc.world != null)
                + " dimension=" + (packet == null ? "?" : String.valueOf(packet.getDimension())));
    }

    @Inject(method = "func_147282_a", at = @At("RETURN"), require = 0)
    private void celtium$noteJoinReturned(SPacketJoinGame packet, CallbackInfo info) {
        Minecraft mc = Minecraft.getMinecraft();
        System.out.println("[Celtium] CELTIUM_JOIN returned player=" + (mc != null && mc.player != null)
                + " world=" + (mc != null && mc.world != null)
                + " screen=" + (mc == null || mc.currentScreen == null ? "none"
                        : mc.currentScreen.getClass().getSimpleName()));
    }
}
