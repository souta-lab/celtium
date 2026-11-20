package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MusicTicker;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.soutalab.celtium.vulkan.TickSplit2Index;
import org.soutalab.celtium.vulkan.TickTimings;

/**
 * TickSplit2's call-site brackets inside {@code Minecraft.runTick} (item 407).
 * <p>
 * Each bracket wraps one disjoint call site, so the union {@link TickTimings} subtracts per tick is a covered
 * span rather than a sum of overlapping spans. Call sites that run several times a tick, or several call sites
 * that are one candidate (the two world calls, the two sound updates, the three GUI updates, Forge's pre/post
 * pair), accumulate into the one bracket the design names. The indices are constants from
 * {@link TickSplit2Index}: no name is resolved on the tick path.
 * <p>
 * Two of the design's names are not hooked here, on the record in {@link TickTimings}' javadoc:
 * {@code tickSched} (the scheduled-executables drain runs in {@code runGameLoop}, outside the tick) and
 * {@code tickFixture} (the validation client's tick hook can only reach the timing classes reflectively).
 * <p>
 * {@code require = 0} throughout, the {@link MinecraftTickTimingMixin} rule: a configuration whose runTick has
 * a different shape must lose the split, not the game - and the report's calls-per-tick column is the detector,
 * because a bracket whose hook missed prints zero calls rather than a plausible-looking zero cost.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftTickSplitMixin {

    @Redirect(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;updateTick()V"), require = 0)
    private void celtium$tickGuiIngame(GuiIngame gui) {
        TickTimings.begin(TickSplit2Index.TICK_GUI_TICK);
        try {
            gui.updateTick();
        } finally {
            TickTimings.end(TickSplit2Index.TICK_GUI_TICK);
        }
    }

    @Redirect(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;handleInput()V"), require = 0)
    private void celtium$tickGuiScreenInput(GuiScreen screen) throws java.io.IOException {
        TickTimings.begin(TickSplit2Index.TICK_GUI_TICK);
        try {
            screen.handleInput();
        } finally {
            TickTimings.end(TickSplit2Index.TICK_GUI_TICK);
        }
    }

    @Redirect(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;updateScreen()V"), require = 0)
    private void celtium$tickGuiScreenUpdate(GuiScreen screen) {
        TickTimings.begin(TickSplit2Index.TICK_GUI_TICK);
        try {
            screen.updateScreen();
        } finally {
            TickTimings.end(TickSplit2Index.TICK_GUI_TICK);
        }
    }

    @Redirect(method = "runTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;updateController()V"),
            require = 0)
    private void celtium$tickGameMode(PlayerControllerMP controller) {
        TickTimings.begin(TickSplit2Index.TICK_GAME_MODE);
        try {
            controller.updateController();
        } finally {
            TickTimings.end(TickSplit2Index.TICK_GAME_MODE);
        }
    }

    // runTickMouse / runTickKeyboard are private methods of the mixin's own target class, so there is no
    // callable original for a redirect to wrap; the bracket is an inject pair around the call instead. A throw
    // out of either call leaves the bracket open, and TickTimings resets its depths at the next tick's begin.
    @Inject(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTickMouse()V",
                    shift = At.Shift.BEFORE), require = 0)
    private void celtium$tickMouseBegin(CallbackInfo ci) {
        TickTimings.begin(TickSplit2Index.TICK_INPUT);
    }

    @Inject(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTickMouse()V",
                    shift = At.Shift.AFTER), require = 0)
    private void celtium$tickMouseEnd(CallbackInfo ci) {
        TickTimings.end(TickSplit2Index.TICK_INPUT);
    }

    @Inject(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTickKeyboard()V",
                    shift = At.Shift.BEFORE), require = 0)
    private void celtium$tickKeyboardBegin(CallbackInfo ci) {
        TickTimings.begin(TickSplit2Index.TICK_INPUT);
    }

    @Inject(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTickKeyboard()V",
                    shift = At.Shift.AFTER), require = 0)
    private void celtium$tickKeyboardEnd(CallbackInfo ci) {
        TickTimings.end(TickSplit2Index.TICK_INPUT);
    }

    @Redirect(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;updateClouds()V"),
            require = 0)
    private void celtium$tickLvlRenderer(RenderGlobal renderGlobal) {
        TickTimings.begin(TickSplit2Index.TICK_LVL_RENDERER);
        try {
            renderGlobal.updateClouds();
        } finally {
            TickTimings.end(TickSplit2Index.TICK_LVL_RENDERER);
        }
    }

    @Redirect(method = "runTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;updateEntities()V"), require = 0)
    private void celtium$tickWorldEntities(WorldClient world) {
        TickTimings.begin(TickSplit2Index.TICK_WORLD);
        try {
            world.updateEntities();
        } finally {
            TickTimings.end(TickSplit2Index.TICK_WORLD);
        }
    }

    @Redirect(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;tick()V"),
            require = 0)
    private void celtium$tickWorldTick(WorldClient world) {
        TickTimings.begin(TickSplit2Index.TICK_WORLD);
        try {
            world.tick();
        } finally {
            TickTimings.end(TickSplit2Index.TICK_WORLD);
        }
    }

    @Redirect(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/audio/MusicTicker;update()V"), require = 0)
    private void celtium$tickMusic(MusicTicker ticker) {
        TickTimings.begin(TickSplit2Index.TICK_SOUND);
        try {
            ticker.update();
        } finally {
            TickTimings.end(TickSplit2Index.TICK_SOUND);
        }
    }

    @Redirect(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/audio/SoundHandler;update()V"), require = 0)
    private void celtium$tickSound(SoundHandler handler) {
        TickTimings.begin(TickSplit2Index.TICK_SOUND);
        try {
            handler.update();
        } finally {
            TickTimings.end(TickSplit2Index.TICK_SOUND);
        }
    }

    @Redirect(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkManager;processReceivedPackets()V"),
            require = 0)
    private void celtium$tickNet(NetworkManager manager) {
        TickTimings.begin(TickSplit2Index.TICK_NET);
        try {
            manager.processReceivedPackets();
        } finally {
            TickTimings.end(TickSplit2Index.TICK_NET);
        }
    }

    // Forge's patch calls both of these inside runTick (pre at the head, post at the tail); the validation
    // client's own tick work rides inside this dispatch, which is why tickFml is also where most of the
    // fixture's in-tick tax is visible.
    @Redirect(method = "runTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/FMLCommonHandler;onPreClientTick()V"), require = 0)
    private static void celtium$tickFmlPre() {
        TickTimings.begin(TickSplit2Index.TICK_FML);
        try {
            net.minecraftforge.fml.common.FMLCommonHandler.instance().onPreClientTick();
        } finally {
            TickTimings.end(TickSplit2Index.TICK_FML);
        }
    }

    @Redirect(method = "runTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/FMLCommonHandler;onPostClientTick()V"), require = 0)
    private static void celtium$tickFmlPost() {
        TickTimings.begin(TickSplit2Index.TICK_FML);
        try {
            net.minecraftforge.fml.common.FMLCommonHandler.instance().onPostClientTick();
        } finally {
            TickTimings.end(TickSplit2Index.TICK_FML);
        }
    }
}
