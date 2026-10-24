package org.taumc.celeritas.impl.util;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/** A stopped integrated server cannot execute a shutdown task queued during its last tick. */
public final class ServerShutdownAwaiter {
    private ServerShutdownAwaiter() { }

    public static <T> T await(Future<T> task, BooleanSupplier serverStopped) throws ExecutionException {
        boolean interrupted = false;
        try {
            for (;;) {
                // Minecraft sets this after stopServer/handleServerStopped, when no further tick can run the task.
                if (!task.isDone() && serverStopped.getAsBoolean()) {
                    task.cancel(false);
                    return null;
                }
                try {
                    return task.get(50, TimeUnit.MILLISECONDS);
                } catch (TimeoutException pending) {
                    // Recheck the task's consumer, which can exit between the original isServerRunning and enqueue.
                } catch (InterruptedException interruptedWait) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
