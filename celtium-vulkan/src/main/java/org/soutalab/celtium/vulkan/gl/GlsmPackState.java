package org.soutalab.celtium.vulkan.gl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * GLSM stage 1 (glsm-stage1-design.md): the pack route's program-state machine.
 * <p>
 * The registry's single scalar uniform version made every write - including the chunk interface's
 * per-region offset write - invalidate a program's whole uniform block, so the terrain pack loop
 * re-packed, re-copied and re-claimed the block for every region. This class is the classification
 * and shared-state half of the fix, behind {@code -Dceltium.vulkan.glsmPackState} (default on since
 * the stage-1 on-default landing; {@code -Dceltium.vulkan.glsmPackState=false} opts out to the
 * original code path byte for byte):
 * <ul>
 * <li>Every lowered block member is classified at link into a frequency bucket
 * ({@link Bucket}). The classification is static and name-isolated here, so the census the design
 * calls for (which real pack name the region offset travels under, which standard members a pack
 * actually uses) has exactly one place to correct.</li>
 * <li>{@code REGION} members leave the program block entirely: the translator emits the built-in
 * route's indexed SSBO table ({@code CeltiumRegionOffsets}) for them and the backend delivers the
 * offset through the draw's {@code firstInstance} instead of a uniform write.</li>
 * <li>{@code PER_FRAME} members - only the interface/Iris standard names whose cross-program
 * equality is contractual - leave the program block for one shared block
 * ({@link #PER_FRAME_MEMBERS}, a fixed std140 layout every program declares identically, the
 * Angelica PerFrameUniformBlock contract). The harvester below packs that block once per epoch
 * from the first program that claims it; a later program in the same epoch whose value for a
 * harvested member differs is counted as a divergence, never silently mixed - the merge-phase
 * census reads {@link #divergences()} to decide whether the first-writer rule is ever exercised
 * by a real pack.</li>
 * <li>{@code PER_PASS} / {@code PER_TICK} members stay in the program block in this stage, but the
 * registry tracks a version per bucket, so their writes no longer invalidate the {@code DYNAMIC}
 * members' pack (and vice versa). {@code ONCE} is emergent rather than classified: a member no
 * write ever reaches is never dirtied, so the incremental packer never rewrites it after the
 * first full pack.</li>
 * <li>Everything else is {@code DYNAMIC}: program-private, packed by the member-dirty incremental
 * packer alpha-pack-uniform built (its {@code -Dceltium.vulkan.packUniformIncremental} flag was
 * absorbed here and removed at the on-default landing - the incremental packer is the DYNAMIC
 * packer, selected by this flag alone).</li>
 * </ul>
 * Angelica (LGPL-3.0) is the reference for the per-frame shared block's shape - one block, packed
 * once per frame, declared whole by every program - reimplemented here against celtium's own
 * lowering; no Angelica code is copied in this stage.
 */
public final class GlsmPackState {

    /**
     * Whether the GLSM pack-state route is on: {@code -Dceltium.vulkan.glsmPackState}, default on
     * (the tree's default-on form; only an explicit {@code false} opts out).
     */
    public static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.glsmPackState", "true"));

    /** The frequency bucket a lowered block member belongs to; decided at link, never per frame. */
    public enum Bucket {
        REGION, PER_FRAME, PER_PASS, PER_TICK, DYNAMIC
    }

    /**
     * The names the chunk interface's region offset may travel under - the one isolated place the
     * design (§7) asks for. The interface contract name is {@code u_RegionOffset} (the built-in
     * block's field of the same role); {@code chunkOffset} is the Iris-standard name gbuffers
     * programs declare. The merge-phase census confirms which name a real pack's link produces;
     * until then both are recognised, and a name that matches neither stays DYNAMIC (safe side).
     */
    private static final Set<String> REGION_NAMES = Set.of("u_RegionOffset", "chunkOffset", "regionOffset");

    /**
     * The PER_FRAME standard members and their contractual types, in shared-block order. Only a
     * member whose lowered type matches the type here is harvested; a same-named member of another
     * type (a pack-defined custom uniform shadowing a standard name) stays DYNAMIC, as does any
     * member not listed. The list is the Iris/interface standard set the guest supplies identically
     * to every program each frame: the view/projection matrices, the camera position and the
     * frame-time family.
     */
    public static final Map<String, String> PER_FRAME_MEMBERS = new LinkedHashMap<>();
    /** The PER_PASS standard members: the shadow pass' own matrices, re-supplied per pass. */
    private static final Map<String, String> PER_PASS_MEMBERS = new LinkedHashMap<>();
    /** The PER_TICK standard members: values that move on the tick boundary, not the frame. */
    private static final Set<String> PER_TICK_NAMES = Set.of("worldTime", "tickTime", "worldDay");

    static {
        PER_FRAME_MEMBERS.put("modelViewMatrix", "mat4");
        PER_FRAME_MEMBERS.put("modelViewMatrixInverse", "mat4");
        PER_FRAME_MEMBERS.put("projectionMatrix", "mat4");
        PER_FRAME_MEMBERS.put("projectionMatrixInverse", "mat4");
        PER_FRAME_MEMBERS.put("textureMatrix", "mat4");
        PER_FRAME_MEMBERS.put("normalMatrix", "mat3");
        PER_FRAME_MEMBERS.put("cameraPosition", "vec3");
        PER_FRAME_MEMBERS.put("cameraUp", "vec3");
        PER_FRAME_MEMBERS.put("frameTime", "float");
        PER_FRAME_MEMBERS.put("frameTimeCounter", "float");
        PER_FRAME_MEMBERS.put("frameCounter", "int");
        PER_FRAME_MEMBERS.put("sunAngle", "float");
        PER_FRAME_MEMBERS.put("shadowAngle", "float");

        PER_PASS_MEMBERS.put("shadowModelView", "mat4");
        PER_PASS_MEMBERS.put("shadowModelViewInverse", "mat4");
        PER_PASS_MEMBERS.put("shadowProjection", "mat4");
        PER_PASS_MEMBERS.put("shadowProjectionInverse", "mat4");
    }

    /** The shared per-frame block's std140 layout: member name -> {offset, size}, in declaration order. */
    private static final Map<String, int[]> PER_FRAME_LAYOUT = new LinkedHashMap<>();
    /** The shared per-frame block's total std140 size in bytes. */
    public static final int PER_FRAME_BLOCK_BYTES;

    static {
        int offset = 0;
        for (Map.Entry<String, String> member : PER_FRAME_MEMBERS.entrySet()) {
            int[] layout = ModGlslTranslator.std140(member.getValue(), 0);
            offset = (offset + layout[0] - 1) / layout[0] * layout[0];
            PER_FRAME_LAYOUT.put(member.getKey(), new int[] {offset, layout[1]});
            offset += layout[1];
        }
        PER_FRAME_BLOCK_BYTES = (offset + 15) / 16 * 16;
    }

    private GlsmPackState() {
    }

    /** {@return whether {@code name} is a region-offset candidate name} (see {@link #REGION_NAMES}). */
    public static boolean isRegionName(String name) {
        return REGION_NAMES.contains(name);
    }

    /**
     * {@return the bucket for one lowered member}. REGION requires the scalar/vector shape the
     * indexed table can carry (a plain vec3/vec4, no array, no matrix): an offset that arrives as
     * part of an array or struct is not extractable in this form, is counted by the translator as
     * unextractable, and stays DYNAMIC. PER_FRAME requires the contractual type. Everything
     * unrecognised is DYNAMIC - the safe side, whose only cost is the pack the member already had.
     */
    public static Bucket classify(String name, String type, int arrayLength) {
        if (REGION_NAMES.contains(name)) {
            return ("vec3".equals(type) || "vec4".equals(type)) && arrayLength == 0
                    ? Bucket.REGION : Bucket.DYNAMIC;
        }
        String perFrameType = PER_FRAME_MEMBERS.get(name);
        if (perFrameType != null) {
            return perFrameType.equals(type) && arrayLength == 0 ? Bucket.PER_FRAME : Bucket.DYNAMIC;
        }
        String perPassType = PER_PASS_MEMBERS.get(name);
        if (perPassType != null) {
            return perPassType.equals(type) && arrayLength == 0 ? Bucket.PER_PASS : Bucket.DYNAMIC;
        }
        if (PER_TICK_NAMES.contains(name)) {
            return Bucket.PER_TICK;
        }
        return Bucket.DYNAMIC;
    }

    /** {@return the shared block's {offset, size} for a PER_FRAME member}, or null when not shared. */
    public static int[] perFrameLayout(String name) {
        return PER_FRAME_LAYOUT.get(name);
    }

    // ---- Harvest state: the shared per-frame block's values for one epoch ----------------------

    private static long harvestEpoch = Long.MIN_VALUE;
    /** The harvested value bits per PER_FRAME member for {@link #harvestEpoch}; null until claimed. */
    private static Map<String, float[]> harvestedValues;
    /** How many times the shared block was harvested (once per epoch, by construction). */
    private static long harvests;
    /**
     * Writes a program offered for a member the epoch already harvested with different bits: the
     * cross-program equality contract was broken by the guest, the first writer's value stands for
     * the epoch, and the break is counted here instead of silently mixing two programs' frames.
     */
    private static long divergences;
    /** Programs whose lowering extracted a REGION member into the SSBO table. */
    private static long regionExtracted;
    /** REGION-named members whose shape refused extraction (array / matrix / struct part). */
    private static long regionUnextractable;

    /**
     * Offers {@code program}'s current values to the shared block for {@code epoch} and returns the
     * harvested values to pack from: on the epoch's first offer, the program's own values become
     * the harvest; on later offers, a member whose bits differ from the harvest counts a divergence
     * and the harvest stands. A member the offering program never wrote contributes nothing (the
     * shared block keeps the harvest's value, or zero when nobody wrote it - as in GL).
     */
    public static synchronized Map<String, float[]> harvest(ModProgramRegistry.Program program, long epoch) {
        if (epoch != harvestEpoch) {
            harvestEpoch = epoch;
            harvestedValues = null;
        }
        if (harvestedValues == null) {
            harvestedValues = new LinkedHashMap<>();
            for (String name : PER_FRAME_MEMBERS.keySet()) {
                float[] value = program.floats(name);
                if (value == null) {
                    int[] ints = program.ints(name);
                    if (ints != null) {
                        value = new float[ints.length];
                        for (int i = 0; i < ints.length; i++) {
                            value[i] = ints[i];
                        }
                    }
                }
                if (value != null) {
                    harvestedValues.put(name, value.clone());
                }
            }
            harvests++;
            return harvestedValues;
        }
        for (String name : PER_FRAME_MEMBERS.keySet()) {
            float[] offered = program.floats(name);
            if (offered == null) {
                continue;
            }
            float[] held = harvestedValues.get(name);
            if (held == null) {
                harvestedValues.put(name, offered.clone());
            } else if (!sameBits(held, offered)) {
                divergences++;
            }
        }
        return harvestedValues;
    }

    private static boolean sameBits(float[] a, float[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (Float.floatToRawIntBits(a[i]) != Float.floatToRawIntBits(b[i])) {
                return false;
            }
        }
        return true;
    }

    public static synchronized void noteRegionExtracted() {
        regionExtracted++;
    }

    public static synchronized void noteRegionUnextractable() {
        regionUnextractable++;
    }

    public static long harvests() {
        return harvests;
    }

    public static long divergences() {
        return divergences;
    }

    public static long regionExtracted() {
        return regionExtracted;
    }

    public static long regionUnextractable() {
        return regionUnextractable;
    }

    /** Test support: forget the harvest and the counters. */
    static synchronized void clear() {
        harvestEpoch = Long.MIN_VALUE;
        harvestedValues = null;
        harvests = 0;
        divergences = 0;
        regionExtracted = 0;
        regionUnextractable = 0;
    }
}
