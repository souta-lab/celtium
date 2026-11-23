package org.soutalab.celtium.vulkan;

/**
 * E10 (item 723): GPU-driven terrain visibility tests and indirect command generation.
 * <p>
 * This class is the flag and capability-gate surface of the design in
 * {@code e10-design.md}. Stage E10-a shipped two things only: the
 * {@code drawIndirectCount} device capability is queried and enabled in
 * {@link VulkanContext} (reported there as part of {@code CELTIUM_DEVICE_FEATURES}), and the
 * shadow-generation census below. Stage E10-b adds the drawing switch
 * ({@link VulkanGpuTerrainDraw}): with the main flag on, the SOLID pass' draws are
 * GPU-generated whenever the frame's population reproduces the collected one, and the
 * census keeps its independent shadow-generation role.
 * <p>
 * Flags follow the house rule for structural experiments (A1 / B3 shape): opt-in, default off.
 * With both flags off, nothing in this area is allocated, bound or recorded, and the terrain
 * record path is byte-identical in behaviour to the path without E10.
 */
public final class GpuTerrainCulling {

    /** Master switch for E10. From E10-b it arms the drawing executor as well as the gate report. */
    public static final boolean ENABLED = Boolean.getBoolean("celtium.vulkan.gpuTerrainCulling");

    /**
     * Shadow-generation census mode (design section 5.3 (c)): the compute pass runs and its
     * generated command list is compared against the CPU's canonical list for the same frame,
     * while drawing stays on the current path. Independent of {@link #ENABLED}: census alone
     * never changes what is drawn.
     */
    public static final boolean CENSUS = Boolean.getBoolean("celtium.vulkan.gpuTerrainCulling.census");

    /**
     * Shadow-stage census stream (E10 shadow preparation): a second, independent census
     * instance fed from the pack route's shadow stage. The shadow population is drawn under
     * the light's view-projection, which the camera stream's per-frame collection cannot
     * hold (one plane set per frame), so the streams never share an instance. Independent
     * of {@link #CENSUS}: either stream may be armed alone. Census only — never changes
     * what is drawn.
     */
    public static final boolean SHADOW_CENSUS =
            Boolean.getBoolean("celtium.vulkan.gpuTerrainCulling.shadowCensus");

    /**
     * Distance limit, in blocks, for the census visibility test. The CPU search distance is
     * decided inside the chunk render-list manager and is not plumbed to the Vulkan backend in
     * E10-a, so the census takes its limit from here; the default is far beyond any render
     * distance in use, which keeps the distance term from ever rejecting a command the CPU drew
     * (the safe direction) while the shader term itself is exercised and unit-tested. Plumbing
     * the real search distance is E10-b preparation work, recorded in the E10-a record.
     */
    public static final float MAX_DISTANCE_BLOCKS =
            Float.parseFloat(System.getProperty("celtium.vulkan.gpuTerrainCulling.maxDistanceBlocks", "4096"));

    private GpuTerrainCulling() {
    }

    /** Why a frame (or the whole configuration) cannot take the GPU generation path. */
    public enum GateReason {
        /** No gate applies: the frame is eligible for GPU generation (E10-b's question). */
        NONE,
        /** The device did not report / could not enable {@code drawIndirectCount}. */
        NO_DRAW_INDIRECT_COUNT,
        /** A frame budget cap ({@code terrainMaxVertices} / {@code terrainMaxDraws}) is active. */
        BUDGET_ACTIVE,
        /** A batch in the pass does not have the uniform shared index source generation requires. */
        INDEX_SOURCE_NOT_UNIFORM,
        /** The entry count exceeds the device's {@code maxDrawIndirectCount} limit. */
        OVER_MAX_DRAW_INDIRECT_COUNT,
        /** The occlusion mask's generation does not match the table's (design section 3.4). */
        MASK_GENERATION_MISMATCH
    }

    /**
     * Evaluates the standing capability gates of design section 5.2, in the order a frame would
     * meet them. Per-frame gates (index source, mask generation, entry count) are evaluated by
     * the census from its own counters; this method covers the configuration-level prefix and is
     * the single place the gate order is written down.
     *
     * @param drawIndirectCountEnabled whether the device was created with the feature enabled
     * @param budgetActive             whether a terrain frame budget cap is in force
     * @return the first gate that applies, or {@link GateReason#NONE}
     */
    public static GateReason capabilityGate(boolean drawIndirectCountEnabled, boolean budgetActive) {
        if (!drawIndirectCountEnabled) {
            return GateReason.NO_DRAW_INDIRECT_COUNT;
        }
        if (budgetActive) {
            return GateReason.BUDGET_ACTIVE;
        }
        return GateReason.NONE;
    }
}
