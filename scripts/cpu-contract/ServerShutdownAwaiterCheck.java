import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.taumc.celeritas.impl.util.ServerShutdownAwaiter;

public final class ServerShutdownAwaiterCheck {
    public static void main(String[] args) throws Exception {
        Thread stopped = new Thread(() -> { });
        stopped.start();
        stopped.join();
        FutureTask<String> neverRun = new FutureTask<>(() -> "unreachable");
        if (ServerShutdownAwaiter.await(neverRun, () -> !stopped.isAlive()) != null || !neverRun.isCancelled()) {
            throw new AssertionError("a terminated consumer cannot execute its pending future");
        }
        CountDownLatch exit = new CountDownLatch(1);
        Thread server = new Thread(() -> {
            try { exit.await(); } catch (InterruptedException error) { throw new AssertionError(error); }
        });
        server.start();
        FutureTask<String> raced = new FutureTask<String>(() -> "unreachable") {
            @Override public String get(long timeout, TimeUnit unit)
                    throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
                exit.countDown();
                return super.get(timeout, unit);
            }
        };
        if (ServerShutdownAwaiter.await(raced, () -> !server.isAlive()) != null || !raced.isCancelled()) {
            throw new AssertionError("termination during the wait must release the client");
        }
        server.join();
        FutureTask<String> completed = new FutureTask<>(() -> "done");
        completed.run();
        if (!"done".equals(ServerShutdownAwaiter.await(completed, () -> true))) {
            throw new AssertionError("completed results survive consumer termination");
        }
        FutureTask<String> interrupted = new FutureTask<>(() -> "unreachable");
        java.util.concurrent.atomic.AtomicInteger stoppedChecks = new java.util.concurrent.atomic.AtomicInteger();
        Thread.currentThread().interrupt();
        try {
            ServerShutdownAwaiter.await(interrupted, () -> stoppedChecks.incrementAndGet() > 1);
            if (!Thread.currentThread().isInterrupted() || !interrupted.isCancelled()) {
                throw new AssertionError("interruption must survive the wait and consumer completion");
            }
        } finally {
            Thread.interrupted();
        }
        RuntimeException cause = new RuntimeException("original failure");
        FutureTask<String> failed = new FutureTask<>(() -> { throw cause; });
        failed.run();
        try {
            ServerShutdownAwaiter.await(failed, () -> true);
            throw new AssertionError("task failure was swallowed");
        } catch (ExecutionException expected) {
            if (expected.getCause() != cause) throw new AssertionError("failure identity changed");
        }
        System.out.println("PASS: pending shutdown tasks release after consumer termination; results/failures preserved");
    }
}
