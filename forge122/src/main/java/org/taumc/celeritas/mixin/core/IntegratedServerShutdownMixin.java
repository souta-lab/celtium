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

/** World exit must not await work on an integrated server thread which has already terminated. */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerShutdownMixin {
    @Redirect(method = "initiateShutdown", at = @At(value = "INVOKE",
            target = "Lcom/google/common/util/concurrent/Futures;getUnchecked(Ljava/util/concurrent/Future;)Ljava/lang/Object;"))
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
