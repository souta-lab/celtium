package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The CPU-side model of E10's GPU draw-source data (design section 3), kept free of Vulkan
 * calls so the shapes and the arithmetic can be unit-tested without a device.
 * <p>
 * Three structures, matching the design:
 * <ul>
 *   <li><b>DrawSourceTable</b> — one 32-byte entry per draw command source, sorted
 *   section-major (the sort is a design constraint: the mesh stage-2 reload reads the table
 *   section by section, section 8.3 of the design). In E10-a an entry was region-granular:
 *   a {@code VulkanMultiDrawBatch} command is a merged run over a region's sections and
 *   carried no per-section identity, so the entry's box was the whole region. The E10-b
 *   preparation retains the assembler's section tags in the batch, so an entry now names
 *   its own section and is culled by that section's box ({@link #FLAG_SECTION_BOX}); an
 *   entry without a tag keeps the region-granular form as a fallback.</li>
 *   <li><b>RegionRemap</b> — dictionary index → the frame's binding-5 region index
 *   ({@code firstInstance}), or -1 when the region is not drawn this frame. The table never
 *   bakes a frame index in (design section 8.3, rule 3).</li>
 *   <li><b>OcclusionMask</b> — the design's form (section 3.4) is a bitset over section
 *   lattice indices, transcribed from the occlusion search's visible set and stamped with
 *   the lattice window it belongs to; a {@link Builder} given such a mask through
 *   {@link Builder#latticeMask} builds a table whose mask resolves each entry's bit from
 *   its section coordinates. A builder given no lattice mask keeps the E10-a form: one bit
 *   per dictionary index, every collected region's bit set (a visited region's batch is by
 *   construction in the CPU's visible set). The word layout and the shader's bit test are
 *   the same in both forms.</li>
 * </ul>
 * The visibility test here is the Java reference implementation of the common GLSL fragment
 * ({@code terrain_cull.glsl}); design section 5.3 (b) asks for the two to be held to the
 * same formulas, and the tests check the reference against brute-force sampling. The test is
 * deliberately conservative in the same places the CPU is: a box is rejected only when its
 * positive vertex is outside a frustum plane, and the distance term measures to the box's
 * closest point, so a box the CPU drew is not rejected by rounding at the boundary.
 */
public final class GpuTerrainDrawTable {

    /** Bytes in one DrawSourceTable entry (std430, eight uint32 words). */
    public static final int ENTRY_BYTES = 32;
    /** Bytes in one generated command: {@code VkDrawIndexedIndirectCommand}. */
    public static final int COMMAND_BYTES = 20;
    /** Ints in one generated command. */
    public static final int COMMAND_INTS = 5;

    /** Region box the entry's visibility test uses, in blocks (RenderRegion is 8x4x8 sections). */
    public static final int REGION_SIZE_X_BLOCKS = 8 * 16;
    public static final int REGION_SIZE_Y_BLOCKS = 4 * 16;
    public static final int REGION_SIZE_Z_BLOCKS = 8 * 16;

    /** Entry flag: the command's indices come from the region's own index arena. */
    public static final int FLAG_INDEX_SOURCE_REGION_ARENA = 1;
    // Flag value 0 for the index source means the shared quad index buffer (firstIndex 0),
    // which is the unsorted passes' uniform source on the current main.

    /**
     * Entry flag: the entry's section coordinates name the command's own section and the
     * visibility test culls by that section's 16-block box, not by the whole region's.
     * Set when the batch retained the assembler's section tag for the command; an entry
     * without it is region-granular (its section coordinates are the region's origin).
     * The compute shader reads the same bit of the same word to pick its box size, so the
     * two implementations stay in step (design section 5.3 (b)).
     */
    public static final int FLAG_SECTION_BOX = 2;

    /** Edge of one section's box, in blocks, for {@link #FLAG_SECTION_BOX} entries. */
    public static final int SECTION_SIZE_BLOCKS = 16;

    /**
     * Words of header at the head of a lattice-mask segment, in the mask buffer and in
     * {@link Table#maskWords} alike: the lattice window's base section coordinates
     * (x, y, z) followed by its dimensions (x, y, z), as int bit patterns. A segment whose
     * dimension words are all zero is a dictionary mask (the E10-a form) instead.
     */
    public static final int MASK_HEADER_WORDS = 6;

    private GpuTerrainDrawTable() {
    }

    /** One DrawSourceTable entry: a single draw command source and the box it is culled by. */
    public static final class Entry {
        public int indexCount;
        public int firstIndex;
        public int vertexOffset;
        /** Index into this frame's remap (the collection dictionary). */
        public int dictIndex;
        /**
         * World section coordinates of the box this entry is culled by: the command's own
         * section when {@link #FLAG_SECTION_BOX} is set in {@link #flags}, otherwise the
         * origin section of the command's region.
         */
        public int sectionX;
        public int sectionY;
        public int sectionZ;
        public int flags;

        public Entry(int indexCount, int firstIndex, int vertexOffset, int dictIndex,
                     int sectionX, int sectionY, int sectionZ, int flags) {
            this.indexCount = indexCount;
            this.firstIndex = firstIndex;
            this.vertexOffset = vertexOffset;
            this.dictIndex = dictIndex;
            this.sectionX = sectionX;
            this.sectionY = sectionY;
            this.sectionZ = sectionZ;
            this.flags = flags;
        }

        /** The pass ordinal this entry belongs to (flags bits 8..15). */
        public int passOrdinal() {
            return (this.flags >>> 8) & 0xFF;
        }

        public void pack(ByteBuffer out, int byteOffset) {
            out.putInt(byteOffset, this.indexCount);
            out.putInt(byteOffset + 4, this.firstIndex);
            out.putInt(byteOffset + 8, this.vertexOffset);
            out.putInt(byteOffset + 12, this.dictIndex);
            out.putInt(byteOffset + 16, this.sectionX);
            out.putInt(byteOffset + 20, this.sectionY);
            out.putInt(byteOffset + 24, this.sectionZ);
            out.putInt(byteOffset + 28, this.flags);
        }

        public static Entry unpack(ByteBuffer in, int byteOffset) {
            return new Entry(in.getInt(byteOffset), in.getInt(byteOffset + 4),
                    in.getInt(byteOffset + 8), in.getInt(byteOffset + 12),
                    in.getInt(byteOffset + 16), in.getInt(byteOffset + 20),
                    in.getInt(byteOffset + 24), in.getInt(byteOffset + 28));
        }
    }

    /** A built table: section-major entries plus the remap and mask that belong with them. */
    public static final class Table {
        public final Entry[] entries;
        /** dictIndex → frame region index (firstInstance), -1 = not drawn. */
        public final int[] remap;
        /**
         * Occlusion mask as uint32 words: {@link #MASK_HEADER_WORDS} header words (the lattice
         * window, all zero in the dictionary form) followed by the bitset itself — one bit per
         * lattice cell in the lattice form (see {@link #maskDimX}), one bit per dictIndex in
         * the dictionary form.
         */
        public final int[] maskWords;
        /** Generation stamps from the builder (design section 3.4). */
        public final long maskGeneration;
        public final long tableGeneration;
        /**
         * The lattice window the mask belongs to: base section coordinates and dimensions,
         * in the indexing of {@code SectionLattice} (a section's lattice index is
         * {@code ((x - baseX) * dimZ + (z - baseZ)) * dimY + (y - baseY)}, interior cells
         * only). All zero in the dictionary form, which is how the two forms are told apart
         * here and in the shader alike.
         */
        public final int maskBaseX;
        public final int maskBaseY;
        public final int maskBaseZ;
        public final int maskDimX;
        public final int maskDimY;
        public final int maskDimZ;

        private Table(Entry[] entries, int[] remap, int[] maskWords,
                      long maskGeneration, long tableGeneration,
                      int maskBaseX, int maskBaseY, int maskBaseZ,
                      int maskDimX, int maskDimY, int maskDimZ) {
            this.entries = entries;
            this.remap = remap;
            this.maskWords = maskWords;
            this.maskGeneration = maskGeneration;
            this.tableGeneration = tableGeneration;
            this.maskBaseX = maskBaseX;
            this.maskBaseY = maskBaseY;
            this.maskBaseZ = maskBaseZ;
            this.maskDimX = maskDimX;
            this.maskDimY = maskDimY;
            this.maskDimZ = maskDimZ;
        }

        /** Whether this table's mask is the lattice form (design section 3.4). */
        public boolean hasLatticeMask() {
            return this.maskDimX > 0;
        }

        /** Whether a mask offered under {@code generation} belongs to another lattice build. */
        public boolean maskGenerationMismatch(long generation) {
            return this.maskGeneration != generation;
        }

        /** Packs the entries into {@code out} at {@code byteOffset}, std430 order. */
        public void packEntries(ByteBuffer out, int byteOffset) {
            for (int i = 0; i < this.entries.length; i++) {
                this.entries[i].pack(out, byteOffset + i * ENTRY_BYTES);
            }
        }

        public int entriesBytes() {
            return this.entries.length * ENTRY_BYTES;
        }
    }

    /** Collects entries for one frame (or one pass of one frame) and builds the table. */
    public static final class Builder {
        private final List<Entry> entries = new ArrayList<Entry>();
        private final Map<Integer, Integer> dictByRegionId = new HashMap<Integer, Integer>();
        private final List<Integer> remap = new ArrayList<Integer>();
        private long maskGeneration;
        private long tableGeneration;
        private boolean allSectionGranular = true;
        private int[] latticeMaskWords;
        private int maskBaseX;
        private int maskBaseY;
        private int maskBaseZ;
        private int maskDimX;
        private int maskDimY;
        private int maskDimZ;

        /**
         * Registers a region in the dictionary, or returns its existing index.
         *
         * @param frameIndex the binding-5 index the region was assigned for this frame
         */
        public int dictIndexFor(int regionId, int frameIndex) {
            Integer existing = this.dictByRegionId.get(regionId);
            if (existing != null) {
                return existing.intValue();
            }
            int index = this.remap.size();
            this.dictByRegionId.put(regionId, index);
            this.remap.add(frameIndex);
            return index;
        }

        public void add(Entry entry) {
            this.entries.add(entry);
            if ((entry.flags & FLAG_SECTION_BOX) == 0) {
                this.allSectionGranular = false;
            }
        }

        /**
         * Whether every entry added so far names its own section. A lattice mask can only
         * judge a collection uniformly in this state: a region-granular entry's bit would be
         * read at its origin section alone, which is not the judgement the mask makes, so the
         * supplier withholds the lattice form from a mixed collection and it builds with the
         * dictionary mask instead.
         */
        public boolean allSectionGranular() {
            return this.allSectionGranular;
        }

        /**
         * Supplies the occlusion mask in its design form (section 3.4): a bitset over section
         * lattice indices, transcribed from the occlusion search's visible set, together with
         * the lattice window it belongs to and that window's generation stamp. The words are
         * {@link #MASK_HEADER_WORDS} header words (base xyz, dims xyz, matching the window
         * arguments) followed by one bit per lattice cell, in the indexing {@link Table}
         * documents. When supplied, {@link #build()} uses these words verbatim instead of
         * fabricating the dictionary mask.
         *
         * @param generation the mask's generation stamp, from {@link #latticeGeneration}
         */
        public Builder latticeMask(int[] words, int baseX, int baseY, int baseZ,
                                   int dimX, int dimY, int dimZ, long generation) {
            this.latticeMaskWords = words;
            this.maskBaseX = baseX;
            this.maskBaseY = baseY;
            this.maskBaseZ = baseZ;
            this.maskDimX = dimX;
            this.maskDimY = dimY;
            this.maskDimZ = dimZ;
            this.maskGeneration = generation;
            return this;
        }

        public int entryCount() {
            return this.entries.size();
        }

        /** Stamps the generations the mask and table were built from (design section 3.4). */
        public Builder generations(long maskGeneration, long tableGeneration) {
            this.maskGeneration = maskGeneration;
            this.tableGeneration = tableGeneration;
            return this;
        }

        public long maskGeneration() {
            return this.maskGeneration;
        }

        public long tableGeneration() {
            return this.tableGeneration;
        }

        /**
         * Builds the table. Entries are sorted section-major — by section coordinates, then
         * dictionary index, then firstIndex — so the order is total and stable. The mask is
         * the supplied lattice mask when one was given (see {@link #latticeMask}); otherwise
         * every dictionary entry's mask bit is set (a collected region is in the CPU's
         * visible set by construction).
         */
        public Table build() {
            Entry[] sorted = this.entries.toArray(new Entry[0]);
            Arrays.sort(sorted, (a, b) -> {
                if (a.sectionX != b.sectionX) return Integer.compare(a.sectionX, b.sectionX);
                if (a.sectionY != b.sectionY) return Integer.compare(a.sectionY, b.sectionY);
                if (a.sectionZ != b.sectionZ) return Integer.compare(a.sectionZ, b.sectionZ);
                if (a.dictIndex != b.dictIndex) return Integer.compare(a.dictIndex, b.dictIndex);
                return Integer.compare(a.firstIndex, b.firstIndex);
            });
            int[] remap = new int[this.remap.size()];
            for (int i = 0; i < remap.length; i++) {
                remap[i] = this.remap.get(i).intValue();
            }
            if (this.latticeMaskWords != null) {
                return new Table(sorted, remap, this.latticeMaskWords, this.maskGeneration,
                        this.tableGeneration, this.maskBaseX, this.maskBaseY, this.maskBaseZ,
                        this.maskDimX, this.maskDimY, this.maskDimZ);
            }
            int[] mask = new int[MASK_HEADER_WORDS + (remap.length + 31) / 32];
            for (int i = 0; i < remap.length; i++) {
                mask[MASK_HEADER_WORDS + (i >>> 5)] |= 1 << (i & 31);
            }
            return new Table(sorted, remap, mask, this.maskGeneration, this.tableGeneration,
                    0, 0, 0, 0, 0, 0);
        }
    }

    /** The gate the census applies to a built table before generation (design section 5.2). */
    public static GpuTerrainCulling.GateReason frameGate(Table table, int maxDrawIndirectCount,
                                                         long maskGeneration) {
        if (table.maskGenerationMismatch(maskGeneration)) {
            return GpuTerrainCulling.GateReason.MASK_GENERATION_MISMATCH;
        }
        if (maxDrawIndirectCount > 0 && table.entries.length > maxDrawIndirectCount) {
            return GpuTerrainCulling.GateReason.OVER_MAX_DRAW_INDIRECT_COUNT;
        }
        return GpuTerrainCulling.GateReason.NONE;
    }

    /**
     * Extracts the six frustum planes from a column-major 4x4 view-projection matrix
     * (JOML {@code Matrix4fc.get(float[])} layout), Gribb–Hartmann form, each plane
     * normalised by its xyz length. Returns 24 floats: plane i at {@code [4i .. 4i+3]} as
     * (a, b, c, d) with the inside half-space {@code a*x + b*y + c*z + d >= 0}.
     * The near plane uses the OpenGL-style row combination; the terrain projection's
     * Vulkan depth remap is affine in (z, w), so the extracted plane set is the same
     * geometry either way.
     */
    public static float[] extractPlanes(float[] m) {
        float[] rows = new float[16];
        // rows[r] = (m[r], m[4+r], m[8+r], m[12+r]) in column-major storage.
        for (int r = 0; r < 4; r++) {
            rows[r * 4] = m[r];
            rows[r * 4 + 1] = m[4 + r];
            rows[r * 4 + 2] = m[8 + r];
            rows[r * 4 + 3] = m[12 + r];
        }
        float[] planes = new float[24];
        // left = r3 + r0, right = r3 - r0, bottom = r3 + r1, top = r3 - r1,
        // near = r3 + r2, far = r3 - r2.
        int[][] combos = {{0, 1}, {0, -1}, {1, 1}, {1, -1}, {2, 1}, {2, -1}};
        for (int p = 0; p < 6; p++) {
            int row = combos[p][0];
            int sign = combos[p][1];
            float a = rows[12] + sign * rows[row * 4];
            float b = rows[13] + sign * rows[row * 4 + 1];
            float c = rows[14] + sign * rows[row * 4 + 2];
            float d = rows[15] + sign * rows[row * 4 + 3];
            float inv = 1.0f / (float) Math.sqrt(a * a + b * b + c * c);
            planes[p * 4] = a * inv;
            planes[p * 4 + 1] = b * inv;
            planes[p * 4 + 2] = c * inv;
            planes[p * 4 + 3] = d * inv;
        }
        return planes;
    }

    /**
     * The reference visibility test: the same formulas as {@code terrain_cull.glsl}'s
     * {@code celtiumBoxVisible}. The box sits at the entry's section coordinates; its size
     * is the section's own when {@link #FLAG_SECTION_BOX} is set, the whole region's
     * otherwise — the shader picks its box size from the same flag bit.
     *
     * @param planes 24 floats from {@link #extractPlanes}
     */
    public static boolean isVisible(float[] planes, float cameraX, float cameraY, float cameraZ,
                                    float maxDistance, Entry entry) {
        boolean sectionBox = (entry.flags & FLAG_SECTION_BOX) != 0;
        float minX = entry.sectionX * 16.0f;
        float minY = entry.sectionY * 16.0f;
        float minZ = entry.sectionZ * 16.0f;
        float maxX = minX + (sectionBox ? SECTION_SIZE_BLOCKS : REGION_SIZE_X_BLOCKS);
        float maxY = minY + (sectionBox ? SECTION_SIZE_BLOCKS : REGION_SIZE_Y_BLOCKS);
        float maxZ = minZ + (sectionBox ? SECTION_SIZE_BLOCKS : REGION_SIZE_Z_BLOCKS);
        for (int p = 0; p < 6; p++) {
            float a = planes[p * 4];
            float b = planes[p * 4 + 1];
            float c = planes[p * 4 + 2];
            float d = planes[p * 4 + 3];
            float px = a >= 0 ? maxX : minX;
            float py = b >= 0 ? maxY : minY;
            float pz = c >= 0 ? maxZ : minZ;
            if (a * px + b * py + c * pz + d < 0) {
                return false;
            }
        }
        float closestX = Math.max(minX, Math.min(cameraX, maxX));
        float closestY = Math.max(minY, Math.min(cameraY, maxY));
        float closestZ = Math.max(minZ, Math.min(cameraZ, maxZ));
        float dx = cameraX - closestX;
        float dy = cameraY - closestY;
        float dz = cameraZ - closestZ;
        return dx * dx + dy * dy + dz * dz <= maxDistance * maxDistance;
    }

    /**
     * The CPU mirror of the compute generation (design section 3.5): for the entries in
     * {@code [entryStart, entryStart + entryCount)}, applies mask ∧ remap ∧ frustum ∧ distance
     * and appends the surviving commands, in table order. The GPU appends in atomic order
     * instead, so callers compare the two as multisets (see {@link #compareCommands}).
     *
     * @return the generated commands, five ints each:
     *         indexCount, instanceCount (always 1), firstIndex, vertexOffset, firstInstance
     */
    public static int[] generate(Table table, int entryStart, int entryCount,
                                 float[] planes, float cameraX, float cameraY, float cameraZ,
                                 float maxDistance) {
        int[] out = new int[entryCount * COMMAND_INTS];
        int written = 0;
        for (int i = entryStart; i < entryStart + entryCount; i++) {
            Entry e = table.entries[i];
            if (e.dictIndex < 0 || e.dictIndex >= table.remap.length) {
                continue;
            }
            int frameIndex = table.remap[e.dictIndex];
            if (frameIndex < 0) {
                continue;
            }
            if (!maskAllows(table, e)) {
                continue;
            }
            if (!isVisible(planes, cameraX, cameraY, cameraZ, maxDistance, e)) {
                continue;
            }
            out[written++] = e.indexCount;
            out[written++] = 1;
            out[written++] = e.firstIndex;
            out[written++] = e.vertexOffset;
            out[written++] = frameIndex;
        }
        return Arrays.copyOf(out, written);
    }

    /**
     * The mask test of {@link #generate}, mirrored by {@code terrain_cull.comp}: in the
     * lattice form the entry's bit is resolved from its section coordinates against the
     * table's window (interior cells only, in {@code SectionLattice}'s indexing — a section
     * outside the window has no bit, and the culler's word on it is "not visible"); in the
     * dictionary form the bit is the entry's dictionary index.
     */
    private static boolean maskAllows(Table table, Entry e) {
        if (table.hasLatticeMask()) {
            int lx = e.sectionX - table.maskBaseX;
            int ly = e.sectionY - table.maskBaseY;
            int lz = e.sectionZ - table.maskBaseZ;
            if (lx < 1 || lx > table.maskDimX - 2
                    || ly < 1 || ly > table.maskDimY - 2
                    || lz < 1 || lz > table.maskDimZ - 2) {
                return false;
            }
            int idx = (lx * table.maskDimZ + lz) * table.maskDimY + ly;
            int word = table.maskWords[MASK_HEADER_WORDS + (idx >>> 5)];
            return (word & (1 << (idx & 31))) != 0;
        }
        int word = table.maskWords[MASK_HEADER_WORDS + (e.dictIndex >>> 5)];
        return (word & (1 << (e.dictIndex & 31))) != 0;
    }

    /**
     * The generation stamp of a lattice mask: a mix of the window it belongs to (base and
     * dimensions) and the frame its visible set was searched for. Two masks with the same
     * stamp describe the same window and search; any other pairing is the mismatch
     * {@link #frameGate} rejects (design section 3.4). The transcription's producer and
     * the gate's consumer both take the stamp from here so the definition lives once.
     */
    public static long latticeGeneration(int baseX, int baseY, int baseZ,
                                         int dimX, int dimY, int dimZ, int frame) {
        long g = 0x9E3779B97F4A7C15L;
        g = (g ^ baseX) * 0x100000001B3L;
        g = (g ^ baseY) * 0x100000001B3L;
        g = (g ^ baseZ) * 0x100000001B3L;
        g = (g ^ dimX) * 0x100000001B3L;
        g = (g ^ dimY) * 0x100000001B3L;
        g = (g ^ dimZ) * 0x100000001B3L;
        g = (g ^ frame) * 0x100000001B3L;
        return g ^ (g >>> 29);
    }

    /**
     * The CPU mirror of deterministic-slot generation (the shader's issuance mode 1,
     * Angelica T1 form): like {@link #generate}, except the output is exactly
     * {@code entryCount} commands long — entry {@code i}'s command sits at slot
     * {@code i - entryStart}, and a culled entry's slot is a zero command
     * ({@code indexCount = 0, instanceCount = 0}, all other fields 0). The list length
     * is therefore known without any count buffer, which is the property a drawing
     * consumer needs to issue it with a plain indirect draw. Use
     * {@link #stripZeroCommands} to recover the surviving list for comparison against
     * a canonical list.
     */
    public static int[] generateDeterministicSlot(Table table, int entryStart, int entryCount,
                                                  float[] planes, float cameraX, float cameraY,
                                                  float cameraZ, float maxDistance) {
        int[] out = new int[entryCount * COMMAND_INTS];
        for (int i = entryStart; i < entryStart + entryCount; i++) {
            Entry e = table.entries[i];
            if (e.dictIndex < 0 || e.dictIndex >= table.remap.length) {
                continue;
            }
            int frameIndex = table.remap[e.dictIndex];
            if (frameIndex < 0) {
                continue;
            }
            if (!maskAllows(table, e)) {
                continue;
            }
            if (!isVisible(planes, cameraX, cameraY, cameraZ, maxDistance, e)) {
                continue;
            }
            int slot = (i - entryStart) * COMMAND_INTS;
            out[slot] = e.indexCount;
            out[slot + 1] = 1;
            out[slot + 2] = e.firstIndex;
            out[slot + 3] = e.vertexOffset;
            out[slot + 4] = frameIndex;
        }
        return out;
    }

    /**
     * Drops the zero commands of a deterministic-slot list ({@code indexCount = 0} and
     * {@code instanceCount = 0}), preserving slot order. A genuine command can never be
     * a zero command: generation writes {@code instanceCount = 1} for every survivor.
     */
    public static int[] stripZeroCommands(int[] commands) {
        int survivors = 0;
        for (int i = 0; i + COMMAND_INTS <= commands.length; i += COMMAND_INTS) {
            if (commands[i] != 0 || commands[i + 1] != 0) {
                survivors++;
            }
        }
        int[] out = new int[survivors * COMMAND_INTS];
        int written = 0;
        for (int i = 0; i + COMMAND_INTS <= commands.length; i += COMMAND_INTS) {
            if (commands[i] != 0 || commands[i + 1] != 0) {
                System.arraycopy(commands, i, out, written, COMMAND_INTS);
                written += COMMAND_INTS;
            }
        }
        return out;
    }

    /** The outcome of comparing a CPU canonical command list with a generated one. */
    public static final class Diff {
        public final int cpuCount;
        public final int gpuCount;
        /** Commands present in both, counting multiplicity. */
        public final int matched;
        /** Commands the CPU drew that generation did not produce (the F-type direction). */
        public final int cpuOnly;
        /** Commands generation produced that the CPU did not draw (the O-type direction). */
        public final int gpuOnly;

        private Diff(int cpuCount, int gpuCount, int matched) {
            this.cpuCount = cpuCount;
            this.gpuCount = gpuCount;
            this.matched = matched;
            this.cpuOnly = cpuCount - matched;
            this.gpuOnly = gpuCount - matched;
        }
    }

    /** Multiset comparison of two command lists (five ints per command, order ignored). */
    public static Diff compareCommands(int[] cpuCommands, int[] gpuCommands) {
        // Exact string keys: the census runs once per frame-slot cycle over a few thousand
        // commands, so exactness is worth more here than a folding hash's speed — a folded
        // collision would silently merge two distinct commands in the oracle's count.
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (int i = 0; i + COMMAND_INTS <= cpuCommands.length; i += COMMAND_INTS) {
            counts.merge(exactKey(cpuCommands, i), 1, Integer::sum);
        }
        int matched = 0;
        for (int i = 0; i + COMMAND_INTS <= gpuCommands.length; i += COMMAND_INTS) {
            String key = exactKey(gpuCommands, i);
            Integer available = counts.get(key);
            if (available != null && available.intValue() > 0) {
                matched++;
                if (available.intValue() == 1) {
                    counts.remove(key);
                } else {
                    counts.put(key, available.intValue() - 1);
                }
            }
        }
        return new Diff(cpuCommands.length / COMMAND_INTS, gpuCommands.length / COMMAND_INTS, matched);
    }

    /** The exact comparison key of one command: all five fields, in order. */
    static String exactKey(int[] commands, int offset) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COMMAND_INTS; i++) {
            sb.append(commands[offset + i]).append(',');
        }
        return sb.toString();
    }

    /** Native-order scratch buffer helper for packing a table's three arrays contiguously. */
    public static ByteBuffer nativeBuffer(int bytes) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes);
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }
}
