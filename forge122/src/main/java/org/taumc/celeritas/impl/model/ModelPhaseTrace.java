package org.taumc.celeritas.impl.model;

import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.Set;

/**
 * Phase timing instrument for the model loading path (absorption item 338a, 2026-11-18).
 *
 * <p>Switch: {@code -Dceltium.modelPhaseReport=true} (default false). When off, every entry point
 * below costs one boolean check and allocates nothing. When on, one resource reload produces exactly
 * one {@code CELTIUM_MODEL_PHASE} line per phase on {@code System.out}:
 * modelTotal, blocks, itemVariants, itemModels (nested inside itemVariants), spriteSet, stitch
 * (nested inside spriteSet: vanilla {@code loadSprites} ends in {@code loadTextureAtlas}),
 * bake, stateStore, registry and unattributed. {@code registry} is the measured complement of
 * the named phases inside the setup bracket plus the ModelManager-side remainder (construction,
 * missing-model lookup, ModelBakeEvent) - the segment the first calibration leg proved exists
 * (~31% of modelTotal on the terrain fixture). {@code unattributed} is a difference, not a
 * measurement; a negative value means the instrument double-counted a nested phase and the
 * calibration leg must fail.
 *
 * <p>Measurement record: {@code ~/workspace/celtium-plan/ITEM338A-BASELINE-2026-11-18.md} and
 * {@code ITEM338A-RUN-2026-11-18.md} (calibration C1-C7 against the existing
 * {@code celtium.validation.resourceReloadAtTick} bracket). This class is an instrument only;
 * it changes no behaviour and must never be quoted as a speedup.
 *
 * <p>Deviation from the 338a plan, recorded in the RUN document: the Forge bake loop in
 * {@code ModelLoader.setupModelRegistry} calls {@code IModel.bake} directly, not
 * {@code ModelBakery.bakeModel}, so the bake phase accumulates around the {@code IModel.bake}
 * invocation points (plus any {@code ModelBakery.bakeModel} calls, which serve the vanilla
 * {@code ModelBlock} path).
 */
public final class ModelPhaseTrace {
    public static final boolean ENABLED = Boolean.getBoolean("celtium.modelPhaseReport");

    private static int reloadCounter;
    private static boolean modelOpen;
    private static long modelStartNanos;
    private static long heapBefore;
    private static final Set<Long> THREADS = new HashSet<>();

    private static long blocksStart;
    private static long blocksMs;
    private static int blocksCount;
    private static int blocksLocations;

    private static long itemVariantsStart;
    private static long itemVariantsMs;

    private static long itemModelsStart;
    private static long itemModelsMs;
    private static int itemModelsItems;
    private static int itemModelsTried;
    private static int itemModelsFallbacks;

    private static long spriteSetMs;
    private static int spriteSetCount;

    private static long stitchMs;
    private static int stitchSprites;
    private static int stitchCalls;
    private static boolean stitchInside;
    private static boolean stitchSeen;
    // A stitch that finished before the model bracket opened (the texture listener may run
    // first) is parked here and adopted by the next model bracket, marked insideModelTotal=false.
    private static long pendingStitchMs = -1L;
    private static int pendingStitchSprites;
    private static int pendingStitchCalls;

    private static long bakeCallStart;
    private static long bakeNanos;
    private static int bakeCalls;
    private static long setupMs;
    private static boolean spriteSetOpen;
    private static boolean stitchInsideSpriteSet;
    private static int distinctModels = -1;

    private static long stateStoreMs;
    private static int stateStoreStates;

    private ModelPhaseTrace() {
    }

    public static void reset() {
        reloadCounter = 0;
        modelOpen = false;
        pendingStitchMs = -1L;
        pendingStitchCalls = 0;
    }

    public static boolean isModelOpen() {
        return modelOpen;
    }

    private static void noteThread() {
        THREADS.add(Thread.currentThread().getId());
    }

    private static long toMs(long nanos) {
        return nanos / 1000000L;
    }

    public static void modelBegin() {
        if (!ENABLED) {
            return;
        }
        reloadCounter++;
        modelOpen = true;
        noteThread();
        modelStartNanos = System.nanoTime();
        heapBefore = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        THREADS.clear();
        noteThread();
        blocksMs = 0L;
        blocksCount = 0;
        blocksLocations = 0;
        itemVariantsMs = 0L;
        itemModelsMs = 0L;
        itemModelsItems = 0;
        itemModelsTried = 0;
        itemModelsFallbacks = 0;
        spriteSetMs = 0L;
        spriteSetCount = 0;
        stitchMs = 0L;
        stitchSprites = 0;
        stitchCalls = 0;
        stitchInside = false;
        stitchSeen = false;
        if (pendingStitchMs >= 0L) {
            stitchMs = pendingStitchMs;
            stitchSprites = pendingStitchSprites;
            stitchCalls = pendingStitchCalls;
            stitchInside = false;
            stitchSeen = true;
            pendingStitchMs = -1L;
            pendingStitchCalls = 0;
        }
        bakeNanos = 0L;
        bakeCalls = 0;
        setupMs = 0L;
        spriteSetOpen = false;
        stitchInsideSpriteSet = false;
        distinctModels = -1;
        stateStoreMs = 0L;
        stateStoreStates = 0;
    }

    public static void modelEnd() {
        if (!ENABLED || !modelOpen) {
            return;
        }
        long totalMs = toMs(System.nanoTime() - modelStartNanos);
        long heapAfter = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        modelOpen = false;
        long bakeMs = toMs(bakeNanos);
        // stitch is nested inside spriteSet when loadSprites ends in loadTextureAtlas (the
        // vanilla 1.12.2 shape); only subtract it separately when it ran outside spriteSet.
        long attributed = blocksMs + itemVariantsMs + spriteSetMs + bakeMs + stateStoreMs
                + ((stitchInside && !stitchInsideSpriteSet) ? stitchMs : 0L);
        // registry: everything inside modelTotal that is not a named phase - the ModelManager
        // side (ModelLoader construction, missing-model lookup, ModelBakeEvent) plus the
        // setupModelRegistry glue (texture-set collection, invertFrom, publish loop).
        long setupInternal = setupMs - (blocksMs + itemVariantsMs + spriteSetMs + bakeMs);
        long managerOutside = totalMs - setupMs - stateStoreMs;
        long registryMs = Math.max(0L, setupInternal) + Math.max(0L, managerOutside);
        long unattributed = totalMs - attributed - registryMs;
        int n = reloadCounter;
        System.out.println("[Celtium] CELTIUM_MODEL_PHASE reload=" + n + " phase=modelTotal ms=" + totalMs
                + " heapBefore=" + heapBefore + " heapAfter=" + heapAfter + " threads=" + THREADS.size());
        System.out.println("[Celtium] CELTIUM_MODEL_PHASE reload=" + n + " phase=blocks ms=" + blocksMs
                + " blocks=" + blocksCount + " locations=" + blocksLocations);
        System.out.println("[Celtium] CELTIUM_MODEL_PHASE reload=" + n + " phase=itemVariants ms=" + itemVariantsMs
                + " items=" + itemModelsItems + " nested=itemModels");
        System.out.println("[Celtium] CELTIUM_MODEL_PHASE reload=" + n + " phase=itemModels ms=" + itemModelsMs
                + " itemsTried=" + itemModelsTried + " fallbacks=" + itemModelsFallbacks + " nested=true");
        System.out.println("[Celtium] CELTIUM_MODEL_PHASE reload=" + n + " phase=spriteSet ms=" + spriteSetMs
                + " sprites=" + spriteSetCount);
        System.out.println("[Celtium] CELTIUM_MODEL_PHASE reload=" + n + " phase=stitch ms=" + stitchMs
                + " sprites=" + stitchSprites + " insideModelTotal=" + stitchInside);
        System.out.println("[Celtium] CELTIUM_MODEL_PHASE reload=" + n + " phase=bake ms=" + bakeMs
                + " bakeModelCalls=" + bakeCalls + " distinctModels=" + distinctModels);
        System.out.println("[Celtium] CELTIUM_MODEL_PHASE reload=" + n + " phase=stateStore ms=" + stateStoreMs
                + " states=" + stateStoreStates + " distinctModels=" + distinctModels);
        System.out.println("[Celtium] CELTIUM_MODEL_PHASE reload=" + n + " phase=registry ms=" + registryMs
                + " of=modelTotal");
        System.out.println("[Celtium] CELTIUM_MODEL_PHASE reload=" + n + " phase=unattributed ms=" + unattributed
                + " of=modelTotal");
    }

    public static void blocksBegin() {
        if (!ENABLED) {
            return;
        }
        noteThread();
        blocksStart = System.nanoTime();
    }

    public static void blocksEnd(int blocks, int locations) {
        if (!ENABLED) {
            return;
        }
        blocksMs = toMs(System.nanoTime() - blocksStart);
        blocksCount = blocks;
        blocksLocations = locations;
    }

    public static void itemVariantsBegin() {
        if (!ENABLED) {
            return;
        }
        noteThread();
        itemVariantsStart = System.nanoTime();
    }

    public static void itemVariantsEnd() {
        if (!ENABLED) {
            return;
        }
        itemVariantsMs = toMs(System.nanoTime() - itemVariantsStart);
    }

    public static void itemModelsBegin() {
        if (!ENABLED) {
            return;
        }
        noteThread();
        itemModelsStart = System.nanoTime();
    }

    public static void itemModelsEnd(int items, int itemsTried, int fallbacks) {
        if (!ENABLED) {
            return;
        }
        itemModelsMs = toMs(System.nanoTime() - itemModelsStart);
        itemModelsItems = items;
        itemModelsTried = itemsTried;
        itemModelsFallbacks = fallbacks;
    }

    private static long spriteSetStart;

    public static void spriteSetBegin() {
        if (!ENABLED) {
            return;
        }
        noteThread();
        spriteSetOpen = true;
        spriteSetStart = System.nanoTime();
    }

    public static void setupBegin() {
        if (!ENABLED) {
            return;
        }
        noteThread();
        setupStart = System.nanoTime();
    }

    private static long setupStart;

    public static void setupEnd() {
        if (!ENABLED) {
            return;
        }
        setupMs = toMs(System.nanoTime() - setupStart);
    }

    public static void spriteSetNoteCount(int sprites) {
        if (!ENABLED) {
            return;
        }
        spriteSetCount = sprites;
    }

    public static void spriteSetEnd() {
        if (!ENABLED) {
            return;
        }
        spriteSetMs = toMs(System.nanoTime() - spriteSetStart);
        spriteSetOpen = false;
    }

    public static void stitchBegin() {
        if (!ENABLED) {
            return;
        }
        noteThread();
        stitchStart = System.nanoTime();
        stitchStartInside = modelOpen;
        if (spriteSetOpen) {
            stitchInsideSpriteSet = true;
        }
    }

    private static long stitchStart;
    private static boolean stitchStartInside;

    public static void stitchEnd(int sprites) {
        if (!ENABLED) {
            return;
        }
        long ms = toMs(System.nanoTime() - stitchStart);
        if (stitchStartInside && modelOpen) {
            stitchMs += ms;
            stitchSprites += sprites;
            stitchCalls++;
            stitchInside = true;
            stitchSeen = true;
        } else if (modelOpen) {
            // Opened inside but the bracket closed first: count it as inside this reload.
            stitchMs += ms;
            stitchSprites += sprites;
            stitchCalls++;
            stitchInside = true;
            stitchSeen = true;
        } else {
            pendingStitchMs = (pendingStitchMs < 0L ? 0L : pendingStitchMs) + ms;
            pendingStitchSprites += sprites;
            pendingStitchCalls++;
        }
    }

    public static void bakeCallBegin() {
        if (!ENABLED) {
            return;
        }
        noteThread();
        bakeCallStart = System.nanoTime();
    }

    public static void bakeCallEnd() {
        if (!ENABLED) {
            return;
        }
        bakeNanos += System.nanoTime() - bakeCallStart;
        bakeCalls++;
    }

    public static void noteDistinctModels(int distinct) {
        if (!ENABLED) {
            return;
        }
        if (distinctModels < 0) {
            distinctModels = distinct;
        }
    }

    public static void stateStoreBegin() {
        if (!ENABLED) {
            return;
        }
        noteThread();
        stateStoreStart = System.nanoTime();
    }

    private static long stateStoreStart;

    public static void stateStoreEnd(int states) {
        if (!ENABLED) {
            return;
        }
        stateStoreMs = toMs(System.nanoTime() - stateStoreStart);
        stateStoreStates = states;
    }
}
