package org.soutalab.celtium.core.geometry;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * The 48-byte descriptor of one resident terrain section (design V040-FINAL-PLAN section 2.1): where
 * the section sits in the world, where its quads live in the terrain slab, and how many there are.
 * The main pass and the shadow pass read the same descriptors against the same resident slab - there
 * is no second, shadow-only copy of the geometry.
 * <p>
 * The slab offsets are byte offsets from the slab base. {@code mcEntityBase} is the offset of this
 * section's pack-ON auxiliary column (the {@code +2 B mc_Entity} per axis quad the pack form adds);
 * it is {@link #NO_AUXILIARY} while packs are off, which is also the state every descriptor is born
 * in - the auxiliary column is generated only when a pack's {@code TerrainVertexRequirements} ask
 * for it (design section 2.3, gap G-5).
 */
public record SectionDesc(
        int sectionX,
        int sectionY,
        int sectionZ,
        int flags,
        long axisSlabOffset,
        long generalSlabOffset,
        int axisQuadCount,
        int generalQuadCount,
        int lightDataBase,
        int mcEntityBase) {

    public static final long BYTE_SIZE = 48L;

    /** {@link #mcEntityBase()} value while no auxiliary column exists for the section. */
    public static final int NO_AUXILIARY = -1;

    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("sectionX"),
            ValueLayout.JAVA_INT.withName("sectionY"),
            ValueLayout.JAVA_INT.withName("sectionZ"),
            ValueLayout.JAVA_INT.withName("flags"),
            ValueLayout.JAVA_LONG.withName("axisSlabOffset"),
            ValueLayout.JAVA_LONG.withName("generalSlabOffset"),
            ValueLayout.JAVA_INT.withName("axisQuadCount"),
            ValueLayout.JAVA_INT.withName("generalQuadCount"),
            ValueLayout.JAVA_INT.withName("lightDataBase"),
            ValueLayout.JAVA_INT.withName("mcEntityBase"));

    static {
        if (LAYOUT.byteSize() != BYTE_SIZE) {
            throw new ExceptionInInitializerError("SectionDesc layout is " + LAYOUT.byteSize()
                    + " bytes, the contract says " + BYTE_SIZE);
        }
    }

    public SectionDesc {
        if (axisSlabOffset < 0L || generalSlabOffset < 0L) {
            throw new IllegalArgumentException("slab offsets must not be negative");
        }
        if (axisQuadCount < 0 || generalQuadCount < 0) {
            throw new IllegalArgumentException("quad counts must not be negative");
        }
    }

    public boolean hasAuxiliaryColumn() {
        return mcEntityBase != NO_AUXILIARY;
    }

    public int totalQuadCount() {
        return axisQuadCount + generalQuadCount;
    }

    public void writeTo(MemorySegment segment, long offset) {
        segment.set(ValueLayout.JAVA_INT, offset, sectionX);
        segment.set(ValueLayout.JAVA_INT, offset + 4L, sectionY);
        segment.set(ValueLayout.JAVA_INT, offset + 8L, sectionZ);
        segment.set(ValueLayout.JAVA_INT, offset + 12L, flags);
        segment.set(ValueLayout.JAVA_LONG, offset + 16L, axisSlabOffset);
        segment.set(ValueLayout.JAVA_LONG, offset + 24L, generalSlabOffset);
        segment.set(ValueLayout.JAVA_INT, offset + 32L, axisQuadCount);
        segment.set(ValueLayout.JAVA_INT, offset + 36L, generalQuadCount);
        segment.set(ValueLayout.JAVA_INT, offset + 40L, lightDataBase);
        segment.set(ValueLayout.JAVA_INT, offset + 44L, mcEntityBase);
    }

    public static SectionDesc readFrom(MemorySegment segment, long offset) {
        return new SectionDesc(
                segment.get(ValueLayout.JAVA_INT, offset),
                segment.get(ValueLayout.JAVA_INT, offset + 4L),
                segment.get(ValueLayout.JAVA_INT, offset + 8L),
                segment.get(ValueLayout.JAVA_INT, offset + 12L),
                segment.get(ValueLayout.JAVA_LONG, offset + 16L),
                segment.get(ValueLayout.JAVA_LONG, offset + 24L),
                segment.get(ValueLayout.JAVA_INT, offset + 32L),
                segment.get(ValueLayout.JAVA_INT, offset + 36L),
                segment.get(ValueLayout.JAVA_INT, offset + 40L),
                segment.get(ValueLayout.JAVA_INT, offset + 44L));
    }
}
