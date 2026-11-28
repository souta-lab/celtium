package org.soutalab.celtium.vulkan.terrain.lane1;

import org.soutalab.celtium.core.geometry.GeneralQuad;
import org.soutalab.celtium.core.geometry.PackedAxisQuad;
import org.soutalab.celtium.core.geometry.SectionDesc;
import org.soutalab.celtium.core.shader.TerrainVertexRequirements;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Lane 1 section store (design V040-FINAL-PLAN section 2.1): every resident section's axis
 * quads (16 B), general quads (80 B) and - only while a pack asks for it - {@code mc_Entity}
 * auxiliary column (2 B per axis quad) live in ONE slab shared by the main pass and the shadow
 * pass; there is no shadow-only geometry copy. Each section is described by the core
 * {@link SectionDesc} (48 B), which the cull stage reads from the descriptor region.
 * <p>
 * Per-layer ranges are not in the descriptor (it carries one range per slab by contract), so the
 * store keeps them in its own side table - {@link LayerRange} per section slot - which the cull
 * stage uploads next to the descriptors when it builds the indirect command stream.
 * <p>
 * Requirements changes (gap G-5) regenerate ONLY the auxiliary column: {@link
 * #setRequirements(TerrainVertexRequirements, long)} walks the resident sections, allocates or
 * releases each column and rewrites the descriptor's {@code mcEntityBase}; the axis and general
 * ranges never move, so a pack switch never re-uploads geometry. A section uploaded while its
 * column is missing is armed per the store's rule: its descriptor reports {@link
 * SectionDesc#NO_AUXILIARY} and the terrain shaders read the fallback entity id 0 - never garbage.
 * <p>
 * The per-corner smooth-light column (R-107, 8 B per quad over both quad kinds) also lives in
 * the auxiliary space, but it is NOT requirements-driven: every upload writes it and the
 * descriptor's {@code lightDataBase} (its offset in 4-byte words) points at it for the whole
 * residency; a requirements change preserves the base (see {@link #withAux}).
 * <p>
 * All frees are timeline-deferred through the regions; the caller drives {@link
 * #collectCompleted(long)} with the scheduler's completed-submission watermark. That
 * watermark is the frees' own currency: the timeline values stamped here are submission
 * ordinals (the {@code submitSequence} the backend reads at upload/remove time), so only a
 * watermark in the same scale can ever collect them. A count of completion events (such as
 * the context's completion epoch) advances on a different scale and must never drive this
 * collection - under a rebuild burst it lags the stamps indefinitely and the slab stays
 * full while the submission side looks healthy. Methods are synchronized: mesher threads
 * hand geometry in, the render thread reads descriptors out.
 */
public final class TerrainSectionStore {

    public static final int LAYER_COUNT = 3;
    public static final int LAYER_SOLID = 0;
    public static final int LAYER_CUTOUT = 1;
    public static final int LAYER_TRANSLUCENT = 2;

    private static final long AXIS_ALIGNMENT = PackedAxisQuad.BYTE_SIZE;
    private static final long GENERAL_ALIGNMENT = GeneralQuad.BYTE_SIZE;
    private static final long AUX_ALIGNMENT = 2L;
    private static final long LIGHT_ALIGNMENT = 4L;
    /** Bytes of corner-light column per quad (two packed ints, axis and general alike). */
    private static final long LIGHT_BYTES_PER_QUAD = 8L;

    /** One layer's slice of a section's slab ranges: quad offset (in quads) and count, per slab kind. */
    public record LayerRange(int axisQuadOffset, int axisQuadCount,
                             int generalQuadOffset, int generalQuadCount) {
        public static final LayerRange EMPTY = new LayerRange(0, 0, 0, 0);
    }

    /** Bumped on every residency change; the frontend gates its descriptor rewrite on it. */
    private long generation;
    /** Cached key-ordered descriptor snapshot, rebuilt lazily after a residency change. */
    private List<SectionDesc> cachedDescriptors;

    private static final class Resident {
        final long key;
        SectionDesc desc;
        final LayerRange[] layers = new LayerRange[LAYER_COUNT];
        short[] mcEntityIds; // retained so a later requirements change can materialize the column (G-5)
        long axisBytes;
        long generalBytes;
        long auxBytes;
        long lightBytes;

        Resident(long key) {
            this.key = key;
        }
    }

    private final SlabSpace axisSpace;
    private final SlabSpace generalSpace;
    private final SlabSpace auxSpace;
    private final SlabRegion axisRegion;
    private final SlabRegion generalRegion;
    private final SlabRegion auxRegion;
    private final Map<Long, Resident> sections = new HashMap<>();
    private TerrainVertexRequirements requirements = TerrainVertexRequirements.NONE;

    /**
     * @param axisSpace    slab space for the 16 B axis quads
     * @param generalSpace slab space for the 80 B general quads
     * @param auxSpace     slab space for the 2 B-per-quad auxiliary columns
     */
    public TerrainSectionStore(SlabSpace axisSpace, SlabSpace generalSpace, SlabSpace auxSpace) {
        this.axisSpace = axisSpace;
        this.generalSpace = generalSpace;
        this.auxSpace = auxSpace;
        this.axisRegion = new SlabRegion(axisSpace.capacity());
        this.generalRegion = new SlabRegion(generalSpace.capacity());
        this.auxRegion = new SlabRegion(auxSpace.capacity());
    }

    public static long sectionKey(int sectionX, int sectionY, int sectionZ) {
        return ((long) (sectionX & 0x3FFFFF) << 42) | ((long) (sectionY & 0xFFFFF) << 22)
                | (long) (sectionZ & 0x3FFFFF);
    }

    public synchronized TerrainVertexRequirements requirements() {
        return requirements;
    }

    public synchronized int sectionCount() {
        return sections.size();
    }

    public synchronized boolean contains(int sectionX, int sectionY, int sectionZ) {
        return sections.containsKey(sectionKey(sectionX, sectionY, sectionZ));
    }

    /** The descriptor of a resident section, or null when the section is not resident. */
    public synchronized SectionDesc descriptor(int sectionX, int sectionY, int sectionZ) {
        Resident resident = sections.get(sectionKey(sectionX, sectionY, sectionZ));
        return resident == null ? null : resident.desc;
    }

    /** The per-layer ranges of a resident section (the store side table the cull stage uploads). */
    public synchronized LayerRange[] layerRanges(int sectionX, int sectionY, int sectionZ) {
        Resident resident = sections.get(sectionKey(sectionX, sectionY, sectionZ));
        if (resident == null) {
            return null;
        }
        return resident.layers.clone();
    }

    /**
     * Uploads one section's geometry, replacing any previous residency of the same section (the old
     * ranges go back timeline-deferred under {@code timelineValue}). Returns the new descriptor.
     *
     * @throws IllegalStateException when a region cannot fit the geometry - the previous residency
     *                               is left untouched in that case
     */
    public synchronized SectionDesc upload(int sectionX, int sectionY, int sectionZ,
                                           SectionGeometry geometry, long timelineValue) {
        long key = sectionKey(sectionX, sectionY, sectionZ);
        int axisCount = geometry.axisQuadCount();
        int generalCount = geometry.generalQuadCount();
        long axisBytes = axisCount * PackedAxisQuad.BYTE_SIZE;
        long generalBytes = generalCount * GeneralQuad.BYTE_SIZE;
        boolean wantsAux = requirements.needsAuxiliaryColumn() && geometry.mcEntityIds().length == axisCount
                && axisCount > 0;
        long auxBytes = wantsAux ? axisCount * 2L : 0L;
        long lightBytes = (long) (axisCount + generalCount) * LIGHT_BYTES_PER_QUAD;

        long axisOffset = axisBytes == 0L ? 0L : axisRegion.allocate(axisBytes, AXIS_ALIGNMENT);
        long generalOffset = generalBytes == 0L ? 0L : generalRegion.allocate(generalBytes, GENERAL_ALIGNMENT);
        long auxOffset = auxBytes == 0L ? -1L : auxRegion.allocate(auxBytes, AUX_ALIGNMENT);
        long lightOffset = lightBytes == 0L ? 0L : auxRegion.allocate(lightBytes, LIGHT_ALIGNMENT);
        if ((axisBytes > 0L && axisOffset < 0L) || (generalBytes > 0L && generalOffset < 0L)
                || (auxBytes > 0L && auxOffset < 0L) || (lightBytes > 0L && lightOffset < 0L)) {
            if (axisBytes > 0L && axisOffset >= 0L) {
                axisRegion.releaseDeferred(axisOffset, axisBytes, timelineValue);
            }
            if (generalBytes > 0L && generalOffset >= 0L) {
                generalRegion.releaseDeferred(generalOffset, generalBytes, timelineValue);
            }
            if (auxBytes > 0L && auxOffset >= 0L) {
                auxRegion.releaseDeferred(auxOffset, auxBytes, timelineValue);
            }
            if (lightBytes > 0L && lightOffset >= 0L) {
                auxRegion.releaseDeferred(lightOffset, lightBytes, timelineValue);
            }
            throw new IllegalStateException("terrain slab cannot fit section (" + sectionX + ", "
                    + sectionY + ", " + sectionZ + "): axis " + axisBytes + " B, general "
                    + generalBytes + " B, aux " + auxBytes + " B, light " + lightBytes + " B");
        }

        Resident resident = new Resident(key);
        resident.axisBytes = axisBytes;
        resident.generalBytes = generalBytes;
        resident.auxBytes = auxBytes;
        resident.lightBytes = lightBytes;
        resident.mcEntityIds = geometry.mcEntityIds();

        MemorySegment axisSegment = axisBytes == 0L ? null : axisSpace.segment(axisOffset, axisBytes);
        MemorySegment generalSegment = generalBytes == 0L ? null
                : generalSpace.segment(generalOffset, generalBytes);
        int axisCursor = 0;
        int generalCursor = 0;
        for (int layer = 0; layer < LAYER_COUNT; layer++) {
            List<PackedAxisQuad> axis = geometry.axisLayer(layer);
            List<GeneralQuad> general = geometry.generalLayer(layer);
            resident.layers[layer] = new LayerRange(axisCursor, axis.size(), generalCursor, general.size());
            for (PackedAxisQuad quad : axis) {
                quad.writeTo(axisSegment, (long) axisCursor * PackedAxisQuad.BYTE_SIZE);
                axisCursor++;
            }
            for (GeneralQuad quad : general) {
                quad.writeTo(generalSegment, (long) generalCursor * GeneralQuad.BYTE_SIZE);
                generalCursor++;
            }
        }
        int mcEntityBase = SectionDesc.NO_AUXILIARY;
        if (auxOffset >= 0L) {
            MemorySegment auxSegment = auxSpace.segment(auxOffset, auxBytes);
            short[] ids = geometry.mcEntityIds();
            for (int i = 0; i < ids.length; i++) {
                auxSegment.set(ValueLayout.JAVA_SHORT, (long) i * 2L, ids[i]);
            }
            mcEntityBase = (int) (auxOffset / 2L);
        }
        int lightDataBase = 0;
        if (lightBytes > 0L) {
            MemorySegment lightSegment = auxSpace.segment(lightOffset, lightBytes);
            int[] column = geometry.cornerLight();
            for (int i = 0; i < column.length; i++) {
                lightSegment.set(ValueLayout.JAVA_INT, (long) i * 4L, column[i]);
            }
            lightDataBase = (int) (lightOffset / 4L);
        }
        resident.desc = new SectionDesc(sectionX, sectionY, sectionZ, 0,
                axisOffset, generalOffset, axisCount, generalCount, lightDataBase, mcEntityBase);

        generation++;
        cachedDescriptors = null;
        Resident previous = sections.put(key, resident);
        if (previous != null) {
            releaseRanges(previous, timelineValue);
        }
        return resident.desc;
    }

    /** Removes a section; its ranges return timeline-deferred. Returns false when it was not resident. */
    public synchronized boolean remove(int sectionX, int sectionY, int sectionZ, long timelineValue) {
        generation++;
        cachedDescriptors = null;
        Resident previous = sections.remove(sectionKey(sectionX, sectionY, sectionZ));
        if (previous == null) {
            return false;
        }
        releaseRanges(previous, timelineValue);
        return true;
    }

    /**
     * Switches the active vertex requirements (gap G-5): only auxiliary columns change. Sections
     * that gain a requirement get a column materialized from their retained ids; sections that lose
     * it release theirs timeline-deferred. Geometry ranges never move.
     */
    public synchronized void setRequirements(TerrainVertexRequirements newRequirements, long timelineValue) {
        if (newRequirements.equals(requirements)) {
            return;
        }
        boolean wasAux = requirements.needsAuxiliaryColumn();
        boolean wantsAux = newRequirements.needsAuxiliaryColumn();
        requirements = newRequirements;
        if (wasAux == wantsAux) {
            return;
        }
        for (Resident resident : sections.values()) {
            if (wantsAux) {
                short[] ids = resident.mcEntityIds;
                if (ids == null || ids.length == 0 || ids.length != resident.desc.axisQuadCount()) {
                    continue; // no source data: the section stays unarmed (fallback id 0), never garbage
                }
                long auxBytes = ids.length * 2L;
                long auxOffset = auxRegion.allocate(auxBytes, AUX_ALIGNMENT);
                if (auxOffset < 0L) {
                    continue; // slab pressure: stays unarmed rather than failing the pack switch
                }
                MemorySegment auxSegment = auxSpace.segment(auxOffset, auxBytes);
                for (int i = 0; i < ids.length; i++) {
                    auxSegment.set(ValueLayout.JAVA_SHORT, (long) i * 2L, ids[i]);
                }
                resident.auxBytes = auxBytes;
                resident.desc = withAux(resident.desc, (int) (auxOffset / 2L));
            } else if (resident.desc.hasAuxiliaryColumn()) {
                auxRegion.releaseDeferred((long) resident.desc.mcEntityBase() * 2L, resident.auxBytes,
                        timelineValue);
                resident.auxBytes = 0L;
                resident.desc = withAux(resident.desc, SectionDesc.NO_AUXILIARY);
            }
        }
    }

    /** Drives the regions' deferred-free collection with the slab completion currency. */
    public synchronized void collectCompleted(long completedTimelineValue) {
        axisRegion.collectCompleted(completedTimelineValue);
        generalRegion.collectCompleted(completedTimelineValue);
        auxRegion.collectCompleted(completedTimelineValue);
    }

    /** Writes every resident descriptor, in key order, into {@code out} at {@code offset}; returns the count. */
    public synchronized int writeDescriptors(MemorySegment out, long offset) {
        List<Long> keys = sections.keySet().stream().sorted().toList();
        long cursor = offset;
        for (Long key : keys) {
            sections.get(key).desc.writeTo(out, cursor);
            cursor += SectionDesc.BYTE_SIZE;
        }
        return keys.size();
    }

    /** Writes three layer ranges per section in descriptor slot order (48 bytes per slot). */
    public synchronized void writeLayerRanges(MemorySegment segment) {
        List<Long> keys = sections.keySet().stream().sorted().toList();
        long offset = 0L;
        for (Long key : keys) {
            LayerRange[] ranges = sections.get(key).layers;
            for (int layer = 0; layer < LAYER_COUNT; layer++) {
                LayerRange range = ranges[layer];
                if (range == null) {
                    range = LayerRange.EMPTY;
                }
                segment.set(ValueLayout.JAVA_INT, offset, range.axisQuadOffset());
                segment.set(ValueLayout.JAVA_INT, offset + 4, range.axisQuadCount());
                segment.set(ValueLayout.JAVA_INT, offset + 8, range.generalQuadOffset());
                segment.set(ValueLayout.JAVA_INT, offset + 12, range.generalQuadCount());
                offset += 16;
            }
        }
    }

    /** Snapshot of the resident descriptors, in key order - the cull stage's CPU-side input. */
    public synchronized List<SectionDesc> descriptors() {
        if (cachedDescriptors == null) {
            cachedDescriptors = sections.keySet().stream().sorted()
                    .map(key -> sections.get(key).desc).toList();
        }
        return cachedDescriptors;
    }


    private static int countOverlaps(java.util.List<long[]> ranges) {
        ranges.sort(java.util.Comparator.comparingLong(r -> r[0]));
        int overlaps = 0;
        long maxEnd = -1L;
        for (long[] range : ranges) {
            if (range[0] < maxEnd) {
                overlaps++;
            }
            maxEnd = Math.max(maxEnd, range[1]);
        }
        return overlaps;
    }


    /** The residency generation: unchanged while no section was uploaded or removed. */
    public synchronized long generation() {
        return generation;
    }

    /**
     * The per-layer ranges of a resident section, WITHOUT the defensive copy
     * {@link #layerRanges} makes: the draw stage reads them on the render thread inside the
     * store's own call frame and never retains or mutates the array.
     */
    public synchronized LayerRange[] layerRangesRef(int sectionX, int sectionY, int sectionZ) {
        Resident resident = sections.get(sectionKey(sectionX, sectionY, sectionZ));
        return resident == null ? null : resident.layers;
    }

    public long axisUsedBytes() {
        return axisRegion.usedBytes();
    }

    public long generalUsedBytes() {
        return generalRegion.usedBytes();
    }

    public long auxUsedBytes() {
        return auxRegion.usedBytes();
    }

    public SlabSpace axisSpace() {
        return axisSpace;
    }

    public SlabSpace generalSpace() {
        return generalSpace;
    }

    public SlabSpace auxSpace() {
        return auxSpace;
    }

    private void releaseRanges(Resident resident, long timelineValue) {
        if (resident.axisBytes > 0L) {
            axisRegion.releaseDeferred(resident.desc.axisSlabOffset(), resident.axisBytes, timelineValue);
        }
        if (resident.generalBytes > 0L) {
            generalRegion.releaseDeferred(resident.desc.generalSlabOffset(), resident.generalBytes, timelineValue);
        }
        if (resident.auxBytes > 0L && resident.desc.hasAuxiliaryColumn()) {
            auxRegion.releaseDeferred((long) resident.desc.mcEntityBase() * 2L, resident.auxBytes, timelineValue);
        }
        if (resident.lightBytes > 0L) {
            auxRegion.releaseDeferred((long) resident.desc.lightDataBase() * 4L, resident.lightBytes,
                    timelineValue);
        }
    }

    private static SectionDesc withAux(SectionDesc desc, int mcEntityBase) {
        return new SectionDesc(desc.sectionX(), desc.sectionY(), desc.sectionZ(), desc.flags(),
                desc.axisSlabOffset(), desc.generalSlabOffset(), desc.axisQuadCount(),
                desc.generalQuadCount(), desc.lightDataBase(), mcEntityBase);
    }
}
