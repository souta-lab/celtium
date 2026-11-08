package org.taumc.celeritas.mixin.core;

import com.google.common.util.concurrent.Futures;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.taumc.celeritas.impl.util.ServerShutdownAwaiter;

/**
 * World exit must not await work on an integrated server thread which has already terminated.
 * <p>
 * {@code require = 0} for the same reason every other injection in this package carries it: this is a behaviour
 * improvement on top of vanilla's own await, not a precondition for the class to exist. Measured on Cleanroom
 * 0.7.14-alpha with the Supersymmetry pack: another mod redirects the same call, Mixin reports
 * {@code @Redirect conflict. Skipping ... IntegratedServerShutdownMixin}, and a required redirect then fails the
 * whole class - {@code NoClassDefFoundError: net/minecraft/server/integrated/IntegratedServer} during
 * {@code Minecraft.init}, which is a crash before any world or mod is constructed. Degrading to vanilla's await
 * costs the shutdown improvement; failing costs the game.
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerShutdownMixin {
    @Redirect(method = "initiateShutdown", at = @At(value = "INVOKE",
            target = "Lcom/google/common/util/concurrent/Futures;getUnchecked(Ljava/util/concurrent/Future;)Ljava/lang/Object;"),
            require = 0)
    private Object celtium$awaitShutdownTask(Future<?> task) {
        try {
            // Use the same vanilla completion flag Minecraft.loadWorld waits on. Cleanroom 0.7 has no getServerThread.
            return ServerShutdownAwaiter.await(task, ((MinecraftServer) (Object) this)::isServerStopped);
        } catch (ExecutionException failure) {
            // Preserve Guava's original failure/error wrapping for tasks which actually ran.
            return Futures.getUnchecked(task);
        }
    }
}
