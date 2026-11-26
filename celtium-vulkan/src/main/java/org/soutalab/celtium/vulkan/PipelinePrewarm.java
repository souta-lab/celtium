package org.soutalab.celtium.vulkan;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Startup pipeline prewarm and asynchronous pipeline compilation (new-architecture design
 * sections 2.6 and 3: on-demand {@code vkCreateGraphicsPipelines} in the middle of a frame is a
 * 50-300 ms spike and is banned from the frame path). The standard pipeline set is registered up
 * front and warmed from the persistent pipeline cache during startup; a state combination that
 * still appears for the first time mid-frame is compiled on this class's single background
 * thread while the caller draws with the compatible fallback pipeline, and
 * {@link #needsFallback(long)} tells the caller which side of that line a key is on.
 *
 * <p>The class owns scheduling only. What "compile key K" means - building the actual Vulkan
 * pipeline and putting it where the draw path looks - is the {@link Compiler} the owner plugs
 * in, which keeps this policy unit-testable and free of pipeline-layout knowledge.
 *
 * <p>A failed compile is terminal for the key until {@link #retry(long)}: silently retrying a
 * deterministic failure every frame would recreate the spike this class exists to remove.
 */
public final class PipelinePrewarm {

    /** Builds the pipeline for one state key. Returns true when the draw path may use it. */
    public interface Compiler {
        boolean compile(long pipelineKey) throws Exception;
    }

    private enum State { QUEUED, COMPILING, READY, FAILED }

    private final Compiler compiler;
    private final Set<Long> startupKeys = new LinkedHashSet<Long>();
    private final ConcurrentHashMap<Long, State> states = new ConcurrentHashMap<Long, State>();
    private final LinkedBlockingQueue<Long> queue = new LinkedBlockingQueue<Long>();
    private final AtomicLong fallbackDraws = new AtomicLong();
    private volatile boolean running;
    private Thread worker;

    public PipelinePrewarm(Compiler compiler) {
        if (compiler == null) {
            throw new IllegalArgumentException("compiler");
        }
        this.compiler = compiler;
    }

    /** Adds a key to the startup warm set. Idempotent. */
    public synchronized void registerStartup(long pipelineKey) {
        this.startupKeys.add(pipelineKey);
    }

    /**
     * Compiles up to {@code maxCount} not-yet-ready startup keys on the calling thread (the
     * startup path, where blocking is the point).
     *
     * @return how many startup keys are still not ready afterwards
     */
    public int warmUp(int maxCount) {
        int compiled = 0;
        for (Long key : startupSnapshot()) {
            if (compiled >= maxCount) {
                break;
            }
            if (isReady(key)) {
                continue;
            }
            compileNow(key);
            compiled++;
        }
        int remaining = 0;
        for (Long key : startupSnapshot()) {
            if (!isReady(key)) {
                remaining++;
            }
        }
        return remaining;
    }

    /**
     * Asks for a key to be compiled in the background. Until {@link #isReady(long)} the caller
     * must use the fallback pipeline for this key.
     */
    public void request(long pipelineKey) {
        State previous = this.states.putIfAbsent(pipelineKey, State.QUEUED);
        if (previous != null) {
            return;
        }
        ensureWorker();
        this.queue.offer(pipelineKey);
    }

    /** Requeues a key whose compile failed. No-op for keys in any other state. */
    public void retry(long pipelineKey) {
        if (this.states.replace(pipelineKey, State.FAILED, State.QUEUED)) {
            ensureWorker();
            this.queue.offer(pipelineKey);
        }
    }

    public boolean isReady(long pipelineKey) {
        return this.states.get(pipelineKey) == State.READY;
    }

    public boolean isFailed(long pipelineKey) {
        return this.states.get(pipelineKey) == State.FAILED;
    }

    /** True while the key is not ready: the caller draws this state with the fallback pipeline. */
    public boolean needsFallback(long pipelineKey) {
        return !isReady(pipelineKey);
    }

    /**
     * Records one draw issued with the fallback pipeline because the exact key was not ready.
     * The issuing side calls this next to {@link #needsFallback(long)} (which stays a pure
     * query): a warm window with zero synchronous pipeline creations is only honest evidence
     * when the number of draws it quietly substituted is printed beside it, so this counter is
     * surfaced as {@code prewarmFallbackDraws} in the backend's diagnostic summaries.
     */
    public void noteFallbackDraw() {
        this.fallbackDraws.incrementAndGet();
    }

    /** {@return how many draws have been issued with a fallback pipeline so far} */
    public long fallbackDraws() {
        return this.fallbackDraws.get();
    }

    /** {@return the prewarm census, in the summary style of the other backend counters}. */
    public String summary() {
        return "prewarmFallbackDraws=" + this.fallbackDraws.get()
                + " prewarmReady=" + readyCount()
                + " prewarmFailed=" + failedCount();
    }

    public int readyCount() {
        return count(State.READY);
    }

    public int failedCount() {
        return count(State.FAILED);
    }

    /** Waits until the background queue is empty and no compile is in flight (tests, shutdown). */
    public boolean awaitIdle(long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (this.queue.isEmpty() && count(State.COMPILING) == 0 && count(State.QUEUED) == 0) {
                return true;
            }
            Thread.sleep(2);
        }
        return false;
    }

    /**
     * Waits until no compile is in flight. Queued keys may remain: after {@link #close()} the
     * worker is stopped, so a queued key will never run and cannot touch the device - only a
     * compile already executing can. This is therefore the wait teardown needs before the
     * device is destroyed, where {@link #awaitIdle} would additionally wait for a queue that
     * nothing will ever drain.
     */
    public boolean awaitCompilesIdle(long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (count(State.COMPILING) == 0) {
                return true;
            }
            Thread.sleep(2);
        }
        return false;
    }

    /** Stops the background thread. Ready keys stay ready; queued keys stay queued. */
    public synchronized void close() {
        this.running = false;
        if (this.worker != null) {
            this.worker.interrupt();
            this.worker = null;
        }
    }

    private synchronized Long[] startupSnapshot() {
        return this.startupKeys.toArray(new Long[0]);
    }

    private int count(State wanted) {
        int total = 0;
        for (State state : this.states.values()) {
            if (state == wanted) {
                total++;
            }
        }
        return total;
    }

    private void compileNow(long pipelineKey) {
        this.states.put(pipelineKey, State.COMPILING);
        boolean ok;
        try {
            ok = this.compiler.compile(pipelineKey);
        } catch (Exception failure) {
            ok = false;
        }
        this.states.put(pipelineKey, ok ? State.READY : State.FAILED);
    }

    private synchronized void ensureWorker() {
        if (this.worker != null) {
            return;
        }
        this.running = true;
        this.worker = new Thread(new Runnable() {
            @Override
            public void run() {
                drainLoop();
            }
        }, "celtium-pipeline-prewarm");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    private void drainLoop() {
        while (this.running) {
            Long key;
            try {
                key = this.queue.poll(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                if (!this.running) {
                    return;
                }
                continue;
            }
            if (key == null) {
                continue;
            }
            if (this.states.get(key) == State.QUEUED) {
                compileNow(key);
            }
        }
    }
}
