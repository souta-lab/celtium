package org.soutalab.celtium.vulkan;

/**
 * The {@code -Dceltium.vulkan.upstreamArrivalCensus=true} per-frame series for QUEUE 727: one
 * {@code CELTIUM_UPSTREAM} line per world frame, printed from the same frame-end point as
 * {@link GroupDecayCensus} so the two series align frame for frame.
 * <p>
 * The question this separates: on the f5-fixed map-wall scene the GROUP_DECAY series showed the offer rate
 * collapsing in a single frame (frame 50 of legB, item 657), and its offers counter sits at the backend's
 * offer entry - downstream of everything that could have stopped the batches. "The game stopped drawing the
 * item frames" and "the capture hook stopped forwarding them" are the same silence at that point. This
 * series counts every stage upstream of it, per frame: the item frames gathered for the pass, the three
 * ways the entity loop can drop one (the vanilla {@code shouldRender} cull, celtium's visibility cull, and
 * the dispatch gate), the dispatches, the {@code RenderItemFrame.doRender} entries (split by whether the
 * displayed item is a filled map), and inside the capture hook the entries, the five early exits, the
 * format-identified arrivals, the backend calls and the acceptances. The first stage whose count falls at
 * the collapse frame names the owner; the pre-registered reading rules live in item 727's design record.
 * <p>
 * Bounded by {@code -Dceltium.vulkan.upstreamArrivalCensusFrames=<n>} (default 900 frames), like the
 * GROUP_DECAY series it aligns with. The positive control is fixed in advance: in the steady phase the
 * gathered count must equal the scene's item-frame total (168 on f5-fixed) and dispatched must equal
 * renders, or the instrument is not seeing the path it claims to.
 * <p>
 * Default off, and every note is a single static-boolean read at the call site, so an ordinary run pays
 * nothing and behaves exactly as before.
 */
public final class UpstreamArrivalCensus {
    public static final boolean ENABLED = Boolean.getBoolean("celtium.vulkan.upstreamArrivalCensus");
    private static final int MAX_FRAMES =
            Integer.getInteger("celtium.vulkan.upstreamArrivalCensusFrames", 900).intValue();

    /** The capture hook returned before its entry counter: no backend, or no buffer. */
    public static final int EXIT_NULL = 0;
    /** The capture hook returned because a display list is being compiled. */
    public static final int EXIT_COMPILING = 1;
    /** The capture hook returned because the finished batch carried no vertices. */
    public static final int EXIT_EMPTY = 2;
    /** The capture hook returned because the batch carried no readable data (null buffer or address 0). */
    public static final int EXIT_NO_DATA = 3;
    /** The capture hook's broad catch swallowed a failure and returned. */
    public static final int EXIT_SWALLOWED = 4;

    /** The format id of {@code POSITION_TEX}, the layout a map face quad arrives in. */
    private static final int FORMAT_POSITION_TEX = 6;

    private static long tick;
    private static int gatheredFrames;
    private static long cullShould;
    private static long cullVisible;
    private static long cullGate;
    private static long dispatched;
    private static long renders;
    private static long mapRenders;
    private static long hookEntries;
    private static final long[] hookExits = new long[5];
    private static long arrivals6;
    private static long arrivalsAll;
    private static long submitCalls;
    private static long accepted;
    private static boolean started;

    private UpstreamArrivalCensus() {
    }

    /**
     * Records the pass-0 membership result for this frame: how many item frames the gathered list carries,
     * and the world tick the gather belongs to. Called once per entity pass from the render loop, before
     * any culling, so the count is the stage every later stage is compared against.
     */
    public static void noteGathered(int frames, long worldTick) {
        gatheredFrames = frames;
        tick = worldTick;
    }

    /** One item frame dropped by the vanilla {@code shouldRender} cull. */
    public static void noteCullShould() {
        cullShould++;
    }

    /** One item frame dropped by celtium's visible-subchunk cull. */
    public static void noteCullVisible() {
        cullVisible++;
    }

    /** One item frame that passed both culls but failed the dispatch gate. */
    public static void noteCullGate() {
        cullGate++;
    }

    /** One item frame handed to {@code renderEntityStatic}. */
    public static void noteDispatch() {
        dispatched++;
    }

    /** One {@code RenderItemFrame.doRender} entry; {@code map} when the displayed item is a filled map. */
    public static void noteRender(boolean map) {
        renders++;
        if (map) {
            mapRenders++;
        }
    }

    /** One batch past the capture hook's entry counter. */
    public static void noteHookEntry() {
        hookEntries++;
    }

    /** One capture-hook early exit, by kind ({@code EXIT_*}). */
    public static void noteHookExit(int kind) {
        if (kind >= 0 && kind < hookExits.length) {
            hookExits[kind]++;
        }
    }

    /** One batch that passed format identification, counted at the arrivals counter's own point. */
    public static void noteArrival(int formatId) {
        arrivalsAll++;
        if (formatId == FORMAT_POSITION_TEX) {
            arrivals6++;
        }
    }

    /** One call into the backend's {@code submitImmediateBatch}. */
    public static void noteSubmit() {
        submitCalls++;
    }

    /** One batch the backend accepted. */
    public static void noteAccepted() {
        accepted++;
    }

    /**
     * Prints the ended frame's line and resets the per-frame counters. Called from the backend's frame end,
     * directly beside {@link GroupDecayCensus#frameEnd}, with the same frame number, so a reader can put the
     * two lines side by side without any re-alignment.
     */
    public static void frameEnd(long frame) {
        if (frame > MAX_FRAMES) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_UPSTREAM frame=" + frame
                + (started ? "" : " start=1")
                + " tick=" + tick
                + " gatheredFrames=" + gatheredFrames
                + " cullShould=" + cullShould
                + " cullVisible=" + cullVisible
                + " cullGate=" + cullGate
                + " dispatched=" + dispatched
                + " renders=" + renders
                + " mapRenders=" + mapRenders
                + " hookEntries=" + hookEntries
                + " exitNull=" + hookExits[EXIT_NULL]
                + " exitCompiling=" + hookExits[EXIT_COMPILING]
                + " exitEmpty=" + hookExits[EXIT_EMPTY]
                + " exitNoData=" + hookExits[EXIT_NO_DATA]
                + " exitSwallowed=" + hookExits[EXIT_SWALLOWED]
                + " arrivals6=" + arrivals6
                + " arrivalsAll=" + arrivalsAll
                + " submitCalls=" + submitCalls
                + " accepted=" + accepted);
        started = true;
        gatheredFrames = 0;
        cullShould = 0;
        cullVisible = 0;
        cullGate = 0;
        dispatched = 0;
        renders = 0;
        mapRenders = 0;
        hookEntries = 0;
        for (int i = 0; i < hookExits.length; i++) {
            hookExits[i] = 0;
        }
        arrivals6 = 0;
        arrivalsAll = 0;
        submitCalls = 0;
        accepted = 0;
    }
}
