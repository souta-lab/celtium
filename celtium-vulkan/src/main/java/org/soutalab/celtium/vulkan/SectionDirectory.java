package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;

/**
 * The section directory of mesh stage 2 (item 677, design section 3.2): an index over a
 * section-granular {@link GpuTerrainDrawTable} that lets a task shader fire per section —
 * one record per section, naming the section's entry range in the table. Like
 * {@link GpuTerrainDrawTable} this class is pure Java with no Vulkan calls, so the shape
 * and the arithmetic can be unit-tested without a device.
 * <p>
 * One record is 32 bytes (std430, eight words), in this order: section coordinates
 * (x, y, z), the section's dictionary index, the first entry of the section's run in the
 * table, the run's entry count, the run's total index count (the split arithmetic's input:
 * quads = total / 6), and a flags word. The flags word's low half is the part of the
 * entries' table flags the whole section has in common (the bitwise AND over the run —
 * bit 0 index source, bit 1 section box, bits 8..15 pass ordinal); the high half carries
 * directory-local summary bits, below.
 * <p>
 * The build leans on the table's section-major sort (design section 8.3, rule 2): a
 * section's entries are one consecutive run. A table that is not section-granular — any
 * entry without the section-box bit, whether mixed in or missing outright — produces no
 * directory at all ({@code null}), and the caller counts that and stays on the current
 * path, in the E10 house form of never falling back silently.
 */
public final class SectionDirectory {

    /** Bytes in one directory record (std430, eight int32 words). */
    public static final int RECORD_BYTES = 32;

    /**
     * The table-entry flag bit marking a section box (16^3) instead of a region box:
     * bit 1, the value E10-b preparation assigned to {@code FLAG_SECTION_BOX} in
     * {@link GpuTerrainDrawTable}. The bit value is the contract; it is restated here
     * because the named constant lands with that preparation.
     */
    public static final int ENTRY_FLAG_SECTION_BOX = 1 << 1;

    /** Directory flags bit: the section's entries did not all carry identical table flags. */
    public static final int FLAG_MIXED_ENTRY_FLAGS = 1 << 16;
    /**
     * Directory flags bit: some entry's index count is not a multiple of 6, so the section
     * is not made of quads and is not stage-2 eligible (stage 1's rejection, same form).
     */
    public static final int FLAG_NON_QUAD_ENTRY = 1 << 17;
    /**
     * Directory flags bit: the section's entries do not share one dictionary index, so the
     * single remap/mask answer the task shader looks up would not speak for the section;
     * not stage-2 eligible.
     */
    public static final int FLAG_MIXED_DICT_INDEX = 1 << 18;

    private SectionDirectory() {
    }

    /** One section's directory record: its coordinates and its run in the table. */
    public static final class Record {
        public int sectionX;
        public int sectionY;
        public int sectionZ;
        /** The dictionary index every entry of the section resolves through. */
        public int dictIndex;
        /** Index of the section's first entry in the table. */
        public int firstEntry;
        /** Number of table entries in the section's run. */
        public int entryCount;
        /** Sum of the run's index counts; the section's quad count is this / 6. */
        public int totalIndexCount;
        /** Common table flags in the low half, directory summary bits in the high half. */
        public int flags;

        public Record(int sectionX, int sectionY, int sectionZ, int dictIndex,
                      int firstEntry, int entryCount, int totalIndexCount, int flags) {
            this.sectionX = sectionX;
            this.sectionY = sectionY;
            this.sectionZ = sectionZ;
            this.dictIndex = dictIndex;
            this.firstEntry = firstEntry;
            this.entryCount = entryCount;
            this.totalIndexCount = totalIndexCount;
            this.flags = flags;
        }

        /** Whether the task shader may emit mesh work for this section. */
        public boolean stage2Eligible() {
            return (this.flags & (FLAG_NON_QUAD_ENTRY | FLAG_MIXED_DICT_INDEX)) == 0;
        }

        /** The section's quad count (exact for eligible sections: every entry is quads). */
        public int quadCount() {
            return this.totalIndexCount / 6;
        }

        /**
         * The number of mesh workgroups the task shader emits for this section at the
         * given per-workgroup quad budget: ceil(quads / budget), 0 for an empty section.
         * The task shader performs the same arithmetic on the same inputs.
         */
        public int splitCount(int quadsPerMeshWorkgroup) {
            int budget = Math.max(1, quadsPerMeshWorkgroup);
            int quads = quadCount();
            return (quads + budget - 1) / budget;
        }

        public void pack(ByteBuffer out, int byteOffset) {
            out.putInt(byteOffset, this.sectionX);
            out.putInt(byteOffset + 4, this.sectionY);
            out.putInt(byteOffset + 8, this.sectionZ);
            out.putInt(byteOffset + 12, this.dictIndex);
            out.putInt(byteOffset + 16, this.firstEntry);
            out.putInt(byteOffset + 20, this.entryCount);
            out.putInt(byteOffset + 24, this.totalIndexCount);
            out.putInt(byteOffset + 28, this.flags);
        }

        public static Record unpack(ByteBuffer in, int byteOffset) {
            return new Record(in.getInt(byteOffset), in.getInt(byteOffset + 4),
                    in.getInt(byteOffset + 8), in.getInt(byteOffset + 12),
                    in.getInt(byteOffset + 16), in.getInt(byteOffset + 20),
                    in.getInt(byteOffset + 24), in.getInt(byteOffset + 28));
        }
    }

    /** A built directory: the records plus the eligibility accounting the gate reports. */
    public static final class Directory {
        public final Record[] records;
        /** Sections the task shader must not emit for (non-quad entries, mixed dict). */
        public final int ineligibleSections;

        private Directory(Record[] records, int ineligibleSections) {
            this.records = records;
            this.ineligibleSections = ineligibleSections;
        }

        public int sectionCount() {
            return this.records.length;
        }

        /** Packs the records into {@code out} at {@code byteOffset}, std430 order. */
        public void packRecords(ByteBuffer out, int byteOffset) {
            for (int i = 0; i < this.records.length; i++) {
                this.records[i].pack(out, byteOffset + i * RECORD_BYTES);
            }
        }

        public int recordsBytes() {
            return this.records.length * RECORD_BYTES;
        }
    }

    /**
     * Builds the directory over a built table, or returns {@code null} when the table is
     * not section-granular (any entry missing the section-box bit). The table's
     * section-major sort is a precondition — it is the {@link GpuTerrainDrawTable.Builder}
     * build contract — and each maximal run of equal section coordinates becomes one
     * record, in table order.
     */
    public static Directory build(GpuTerrainDrawTable.Table table) {
        GpuTerrainDrawTable.Entry[] entries = table.entries;
        for (int i = 0; i < entries.length; i++) {
            if ((entries[i].flags & ENTRY_FLAG_SECTION_BOX) == 0) {
                return null;
            }
        }
        int runs = 0;
        for (int i = 0; i < entries.length; i++) {
            if (i == 0 || entries[i].sectionX != entries[i - 1].sectionX
                    || entries[i].sectionY != entries[i - 1].sectionY
                    || entries[i].sectionZ != entries[i - 1].sectionZ) {
                runs++;
            }
        }
        Record[] records = new Record[runs];
        int ineligible = 0;
        int r = 0;
        int i = 0;
        while (i < entries.length) {
            int start = i;
            int x = entries[i].sectionX;
            int y = entries[i].sectionY;
            int z = entries[i].sectionZ;
            int dictIndex = entries[i].dictIndex;
            int commonFlags = entries[i].flags;
            boolean mixedFlags = false;
            boolean mixedDict = false;
            boolean nonQuad = false;
            long totalIndexCount = 0L;
            while (i < entries.length && entries[i].sectionX == x
                    && entries[i].sectionY == y && entries[i].sectionZ == z) {
                GpuTerrainDrawTable.Entry e = entries[i];
                if (e.flags != entries[start].flags) {
                    mixedFlags = true;
                }
                commonFlags &= e.flags;
                if (e.dictIndex != dictIndex) {
                    mixedDict = true;
                }
                if (e.indexCount % 6 != 0) {
                    nonQuad = true;
                }
                totalIndexCount += e.indexCount;
                i++;
            }
            // The low half is the table-flag copy; mask it so no table bit can ever
            // alias a directory-local summary bit in the high half.
            int flags = commonFlags & 0xFFFF;
            if (mixedFlags) {
                flags |= FLAG_MIXED_ENTRY_FLAGS;
            }
            if (nonQuad) {
                flags |= FLAG_NON_QUAD_ENTRY;
            }
            if (mixedDict) {
                flags |= FLAG_MIXED_DICT_INDEX;
            }
            Record record = new Record(x, y, z, dictIndex, start, i - start,
                    (int) totalIndexCount, flags);
            if (!record.stage2Eligible()) {
                ineligible++;
            }
            records[r++] = record;
        }
        return new Directory(records, ineligible);
    }
}
