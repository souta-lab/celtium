package org.soutalab.celtium.forge.terrain;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * The asynchronous build scheduler of the zero-based terrain lane (design section 2.4's thread
 * split): meshing runs on a platform-thread pool sized {@code cores - 2} (minimum 2) - the
 * mesher's SWAR passes and the snapshot fills are CPU work that must never park a virtual
 * thread's carrier - while cache and texture I/O runs on virtual threads, where blocking is
 * free.
 * <p>
 * Each platform worker owns one {@link SectionMesher} (thread-local scratch, no sharing), and
 * {@link MeshBuffers} circulate through a small pool: a build checks one out, the future's
 * consumer copies the geometry out and returns it through {@link #releaseBuffers}. Once the
 * pool is warm a build allocates nothing. The caller fills the snapshot (on whichever thread
 * owns the world read) and hands it over; the caller must not touch the snapshot until the
 * future completes.
 * <p>
 * Snapshots circulate through a pool of their own ({@link #acquireSnapshot} /
 * {@link #releaseSnapshot}): one snapshot is ~87.5 KB of arrays, and the driver used to
 * allocate a fresh one per build on the render thread. The caller returns a snapshot by
 * registering the release on the build future's completion, which fires on success, failure
 * and cancellation alike. The render-light rule also runs here, at the head of each build:
 * it is pure snapshot-array work, so it belongs on the worker, not in the render thread's
 * fill (S2-b-perf CPU-3, discharging D-V040-5).
 */
public final class TerrainBuildScheduler implements AutoCloseable {
    private final ExecutorService meshPool;
    private final ExecutorService ioPool;
    private final ThreadLocal<SectionMesher> meshers = ThreadLocal.withInitial(SectionMesher::new);
    private final java.util.concurrent.ConcurrentLinkedQueue<MeshBuffers> bufferPool =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.concurrent.ConcurrentLinkedQueue<WorldSectionSnapshot> snapshotPool =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final ThreadLocal<byte[][]> lightRuleScratch = ThreadLocal.withInitial(() ->
            new byte[][]{new byte[WorldSectionSnapshot.VOLUME], new byte[WorldSectionSnapshot.VOLUME]});
    private final int meshThreads;

    public TerrainBuildScheduler() {
        this(Math.max(2, Runtime.getRuntime().availableProcessors() - 2));
    }

    public TerrainBuildScheduler(int meshThreads) {
        if (meshThreads < 2) {
            throw new IllegalArgumentException("the mesher pool keeps at least 2 threads: " + meshThreads);
        }
        this.meshThreads = meshThreads;
        this.meshPool = Executors.newFixedThreadPool(meshThreads, platformFactory("celtium-terrain-mesh"));
        this.ioPool = Executors.newThreadPerTaskExecutor(virtualFactory("celtium-terrain-io"));
    }

    private static ThreadFactory platformFactory(String prefix) {
        AtomicInteger next = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + next.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static ThreadFactory virtualFactory(String prefix) {
        AtomicInteger next = new AtomicInteger();
        return runnable -> Thread.ofVirtual()
                .name(prefix + "-" + next.incrementAndGet())
                .unstarted(runnable);
    }

    public int meshThreads() {
        return meshThreads;
    }

    /**
     * Checks a snapshot out of the pool (allocating only while the pool is cold). The caller
     * fills it and hands it to {@link #build}; see the class doc for how it comes back.
     */
    public WorldSectionSnapshot acquireSnapshot() {
        WorldSectionSnapshot snapshot = snapshotPool.poll();
        return snapshot != null ? snapshot : new WorldSectionSnapshot();
    }

    /** Returns a snapshot to the pool. Its arrays are fully overwritten by the next fill. */
    public void releaseSnapshot(WorldSectionSnapshot snapshot) {
        if (snapshot != null) {
            snapshotPool.offer(snapshot);
        }
    }

    /**
     * Meshes one filled snapshot on the platform pool. The future's {@link MeshBuffers} are
     * checked out of the pool: the consumer copies the geometry out (the store does, on the
     * render thread) and returns them through {@link #releaseBuffers}. The snapshot's
     * render-light rule is applied first, on the worker, over thread-local scratch.
     */
    public CompletableFuture<MeshBuffers> build(WorldSectionSnapshot snapshot, BlockFaceData data,
                                                MesherOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            MeshBuffers out = bufferPool.poll();
            if (out == null) {
                out = new MeshBuffers();
            }
            byte[][] scratch = lightRuleScratch.get();
            snapshot.applyRenderLightRule(scratch[0], scratch[1]);
            meshers.get().mesh(snapshot, data, options, out);
            return out;
        }, meshPool);
    }

    /** Returns buffers to the pool after the consumer has copied the geometry out. */
    public void releaseBuffers(MeshBuffers buffers) {
        if (buffers != null) {
            bufferPool.offer(buffers);
        }
    }

    /** Runs cache/texture I/O on the virtual-thread executor (blocking is free there). */
    public <T> CompletableFuture<T> io(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, ioPool);
    }

    public ExecutorService ioExecutor() {
        return ioPool;
    }

    public ExecutorService meshExecutor() {
        return meshPool;
    }

    @Override
    public void close() {
        meshPool.shutdown();
        ioPool.shutdown();
    }
}
