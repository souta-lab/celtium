package org.soutalab.celtium.vulkan;

/**
 * The TickSplit2 bracket indices, resolved once at class load.
 * <p>
 * The tick hooks run inside {@code Minecraft.runTick} on the client thread, and the frame split's own history
 * records what per-call name resolution costs there (a hash lookup per bracket was 1.4% of the client thread in
 * a steady-state JFR). The hooks therefore hold the index as a constant and call
 * {@link TickTimings#begin(int)} / {@link TickTimings#end(int)} directly, the same index-based API the frame
 * brackets forward to. This class is separate from {@link TickTimings} on purpose: {@code TickTimings}' own
 * storage is allocated lazily to avoid a circular initialisation with {@link FramePassTimings}, and a constants
 * holder that only reads the finished name table keeps that cycle out.
 * <p>
 * The names themselves live in {@link FramePassTimings}' table (one name, one index); this class only pins the
 * ones the item-407 hooks use.
 */
public final class TickSplit2Index {

    public static final int TICK_SCHED = FramePassTimings.indexOfName("tickSched");
    public static final int TICK_NET = FramePassTimings.indexOfName("tickNet");
    public static final int TICK_INPUT = FramePassTimings.indexOfName("tickInput");
    public static final int TICK_GAME_MODE = FramePassTimings.indexOfName("tickGameMode");
    public static final int TICK_SOUND = FramePassTimings.indexOfName("tickSound");
    public static final int TICK_GUI_TICK = FramePassTimings.indexOfName("tickGuiTick");
    public static final int TICK_FML = FramePassTimings.indexOfName("tickFml");
    public static final int TICK_WORLD = FramePassTimings.indexOfName("tickWorld");
    public static final int TICK_LVL_RENDERER = FramePassTimings.indexOfName("tickLvlRenderer");
    public static final int TICK_FIXTURE = FramePassTimings.indexOfName("tickFixture");
    public static final int TICK_ENT = FramePassTimings.indexOfName("tickEnt");
    public static final int TICK_TILE_ENT = FramePassTimings.indexOfName("tickTileEnt");
    public static final int TICK_PARTICLES = FramePassTimings.indexOfName("tickParticles");
    public static final int TICK_CHUNKS = FramePassTimings.indexOfName("tickChunks");
    public static final int TICK_DRAIN = FramePassTimings.indexOfName("tickDrain");

    private TickSplit2Index() {
    }
}
