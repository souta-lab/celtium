package org.taumc.celeritas.impl;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ReportedException;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.crash.ICrashReportDetail;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs {@code TextureMap.generateMipmaps} - the per-sprite resource read, PNG decode and mip pyramid - on a
 * bounded worker pool instead of the render thread.
 * <p>
 * What a sprite's generateMipmaps touches is sprite-local: its own {@code framesTextureData}, its animation
 * metadata and its own pixels. What the stitch touches is not, so the caller-side contract stays sequential:
 * {@link #submit} returns a placeholder that lets {@code loadTexture} keep walking sprites and progress, and
 * {@link #awaitAndStitch} blocks at the {@code stitcher.addSprite} call - per sprite, in the original order -
 * until that sprite's task finishes, then adds only on a true result. Ordering, the stitch and every FML
 * tracking call all stay on the render thread.
 * <p>
 * The failure semantics of the original private method are reproduced one for one: a runtime exception while
 * loading the frames logs and skips the sprite, an IO exception logs and skips it, and a throwable inside
 * {@code generateMipmaps} still produces the same {@code ReportedException} - rethrown at the await point so
 * it lands on the same thread the original would have thrown it on.
 * <p>
 * Kill switch: {@code -Dceltium.parallelAtlas=false} routes every sprite through the original method again.
 */
public final class CeltiumAtlasLoader {
    private static final Logger LOGGER = LogManager.getLogger("CeltiumAtlasLoader");
    public static final boolean ENABLED = !"false".equals(System.getProperty("celtium.parallelAtlas", "true"));
    private static final int WORKERS = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
    private static final ExecutorService POOL = Executors.newFixedThreadPool(WORKERS, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "celtium-atlas-worker-" + this.counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    /**
     * One outstanding task per sprite, keyed by identity: a map entry exists only between the submit call in
     * {@code loadTexture} and the matching await at {@code stitcher.addSprite}, so the map never grows past the
     * window between those two calls on a single stitch.
     */
    private static final Map<TextureAtlasSprite, Future<Boolean>> PENDING = new ConcurrentHashMap<>();
    /**
     * Sprites whose load ran synchronously and failed, carried to the drain point so the stitch is refused.
     * Without this the synchronous branch's answer was dropped: {@code await} saw no future, answered "may be
     * stitched", and a sprite whose frame read had failed was stitched with no frames at all.
     */
    private static final Set<TextureAtlasSprite> REFUSED = ConcurrentHashMap.newKeySet();
    private static long submittedSprites;
    private static long awaitedSprites;
    private static long awaitedMillis;
    private static long parallelLoads;
    private static long lastReportedStitch;
    private static long firstSubmitNanos;
    private static long windowMillis;
    private static final AtomicLong workerBusyMillis = new AtomicLong();

    private CeltiumAtlasLoader() {
    }

    /**
     * Submits the sprite's resource read and mip generation to the pool and reports success so
     * {@code loadTexture} proceeds to the (gated) stitch call. The sprite itself is the future's identity.
     */
    public static boolean submit(final TextureMap map, final String basePath, final int mipmapLevels,
            final IResourceManager resourceManager, final TextureAtlasSprite sprite) {
        if (sprite.hasCustomLoader(resourceManager, resourceLocationOf(basePath, sprite))) {
            // A custom loader is the sprite's own code - thread assumptions belong to the mod that wrote it, so
            // the whole call stays synchronous through the replicated body. Its answer is kept for the drain
            // point: a refused sprite must not be stitched, exactly as the sequential path would not add it.
            boolean loaded = generateMipmapsSequential(basePath, mipmapLevels, resourceManager, sprite);
            if (!loaded) {
                REFUSED.add(sprite);
            }
            return loaded;
        }
        final ResourceLocation resourceLocation = resourceLocationOf(basePath, sprite);
        PENDING.put(sprite, POOL.submit(() -> {
            long started = System.nanoTime();
            try {
                return generateMipmapsWorker(resourceManager, resourceLocation, mipmapLevels, sprite);
            } finally {
                workerBusyMillis.addAndGet((System.nanoTime() - started) / 1000000L);
            }
        }));
        if (submittedSprites++ == 0 || firstSubmitNanos == 0L) {
            firstSubmitNanos = System.nanoTime();
        }
        parallelLoads++;
        return true;
    }

    /**
     * Awaits the sprite's pending task at the drain point - the head of {@code finishLoading} - and reports
     * whether the sprite may be added to the stitcher. A null future means the sprite never took the async
     * path (custom loader ran synchronously): the caller stitches it directly.
     */
    public static boolean await(TextureAtlasSprite sprite) throws Throwable {
        if (REFUSED.remove(sprite)) {
            return false;
        }
        // Never examine async frame data before the worker has finished populating it: a normal sprite
        // starts with zero frames, and an early check would falsely refuse it, leaving its Future in PENDING.
        Future<Boolean> future = PENDING.remove(sprite);
        if (future == null) {
            return stitchable(sprite);
        }
        long started = System.nanoTime();
        try {
            Boolean result = future.get();
            // Worker success does not guarantee that a modded sprite supplied frame 0.
            // Conversely, missing frames before future.get() says nothing about the eventual result.
            return Boolean.TRUE.equals(result) && stitchable(sprite);
        } finally {
            awaitedSprites++;
            awaitedMillis += (System.nanoTime() - started) / 1000000L;
            if (PENDING.isEmpty() && awaitedSprites > lastReportedStitch) {
                windowMillis = (System.nanoTime() - firstSubmitNanos) / 1000000L;
                // The drain emptied the pipeline for this atlas: print the parallel window's cost - the
                // time the render thread still had to block on futures - once, not once per sprite.
                lastReportedStitch = awaitedSprites;
                System.out.println("[Celtium] " + summary());
            }
        }
    }

    /** Only call after the sprite's worker finishes (or when it never had an async worker). */
    private static boolean stitchable(TextureAtlasSprite sprite) {
        if (sprite.getFrameCount() == 0) {
            // Stitcher subsequently requests frame 0, which would throw for an empty modded sprite.
            LOGGER.error("Refusing to stitch {} with no frames; it would crash the atlas", sprite.getIconName());
            return false;
        }
        return true;
    }

    /** The sequential equivalent for sprites whose own loader code may not be thread-safe. */
    private static boolean generateMipmapsSequential(String basePath, int mipmapLevels,
            IResourceManager resourceManager, TextureAtlasSprite sprite) {
        return generateMipmapsBody(resourceManager, resourceLocationOf(basePath, sprite), mipmapLevels, sprite);
    }

    /** Worker entry: the replicated body of {@code TextureMap.generateMipmaps}, minus its crash wrapping. */
    private static boolean generateMipmapsWorker(IResourceManager resourceManager,
            ResourceLocation resourceLocation, int mipmapLevels, TextureAtlasSprite sprite) {
        try {
            return generateMipmapsBody(resourceManager, resourceLocation, mipmapLevels, sprite);
        } catch (ReportedException crash) {
            // The original throws from generateMipmaps itself; the caller rethrows at the await point.
            throw crash;
        }
    }

    /**
     * The body of {@code TextureMap.generateMipmaps}, faithfully: custom-loader sprites skip the frame load
     * entirely, a runtime exception in the frame load logs and refuses the sprite, an IO exception refuses it,
     * and a throwable from the mip pass is wrapped in the same crash report the original builds.
     */
    private static boolean generateMipmapsBody(IResourceManager resourceManager,
            final ResourceLocation resourceLocation, int mipmapLevels, final TextureAtlasSprite sprite) {
        // Frames are read when the sprite does not already have them. Vanilla asks {@code hasCustomLoader}
        // here instead, but this loader can only ask that question against the location it reconstructed from
        // the sprite's icon name - not the one {@code TextureMap.loadTexture} was handed. When the two
        // disagree the frame read was skipped for a sprite that had none, and the sprite reached
        // {@code finishLoading} with an empty frame list: measured with Tinkers' Construct loaded, that
        // crashed the atlas at {@code tconstruct:items/arrow/head_obsidian} on
        // {@code getFrameTextureData(0)} (IndexOutOfBounds, "Stitching texture atlas"). A sprite whose own
        // loader already produced its frames still skips the read, so the fast path is unchanged.
        if (sprite.getFrameCount() == 0) {
            IResource resource = null;
            try {
                resource = resourceManager.getResource(resourceLocation);
                sprite.loadSpriteFrames(resource, mipmapLevels + 1);
            } catch (RuntimeException runtimeException) {
                LOGGER.error("Unable to parse metadata from {}", resourceLocation, runtimeException);
                return false;
            } catch (IOException ioException) {
                LOGGER.error("Using missing texture, unable to load {}", resourceLocation, ioException);
                return false;
            } finally {
                IOUtils.closeQuietly((Closeable) resource);
            }
        }
        try {
            sprite.generateMipmaps(mipmapLevels);
            return true;
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.makeCrashReport(throwable, "Applying mipmap");
            CrashReportCategory category = crashReport.makeCategory("Sprite being mipmapped");
            category.addDetail("Sprite name", new ICrashReportDetail<String>() {
                @Override
                public String call() {
                    return sprite.getIconName();
                }
            });
            category.addDetail("Sprite size", new ICrashReportDetail<String>() {
                @Override
                public String call() {
                    return sprite.getIconWidth() + " x " + sprite.getIconHeight();
                }
            });
            category.addDetail("Sprite frames", new ICrashReportDetail<String>() {
                @Override
                public String call() {
                    return sprite.getFrameCount() + " frames";
                }
            });
            category.addCrashSection("Mipmap levels", Integer.valueOf(mipmapLevels));
            throw new ReportedException(crashReport);
        }
    }

    private static ResourceLocation resourceLocationOf(String basePath, TextureAtlasSprite sprite) {
        ResourceLocation name = new ResourceLocation(sprite.getIconName());
        return new ResourceLocation(name.getNamespace(),
                String.format("%s/%s%s", basePath, name.getPath(), ".png"));
    }

    /** {@return the instrument line}: how many sprites went parallel and how long awaits actually blocked. */
    public static String summary() {
        return "atlasParallel=[submitted=" + submittedSprites + " awaited=" + awaitedSprites
                + " awaitMillis=" + awaitedMillis + " windowMillis=" + windowMillis
                + " workerBusyMillis=" + workerBusyMillis.get() + " workers=" + WORKERS
                + " enabled=" + ENABLED + "]";
    }

    /** Drops the instrument counters between legs. */
    public static void reset() {
        submittedSprites = 0;
        awaitedSprites = 0;
        awaitedMillis = 0;
        parallelLoads = 0;
        workerBusyMillis.set(0);
        windowMillis = 0;
        firstSubmitNanos = 0;
    }
}
