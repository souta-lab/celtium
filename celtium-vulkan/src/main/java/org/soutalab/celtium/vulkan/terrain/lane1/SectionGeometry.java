package org.soutalab.celtium.vulkan.terrain.lane1;

import org.soutalab.celtium.core.geometry.GeneralQuad;
import org.soutalab.celtium.core.geometry.PackedAxisQuad;

import java.util.List;

/**
 * One built section's geometry as the mesher hands it to the store (design V040-FINAL-PLAN section
 * 2.1): axis quads and general quads grouped by terrain layer, in the fixed layer order the store
 * concatenates into the section's slab ranges (SOLID, CUTOUT, TRANSLUCENT), plus the pack auxiliary
 * column's source data. Coordinates are section-local; the vertex-pulling shaders add the section
 * origin from the {@code SectionDesc}, so nothing here is translated on upload.
 * <p>
 * The auxiliary column is carried as the per-axis-quad {@code mc_Entity} ids in axis order (the
 * mesher emits them alongside the quads); the store materializes the column only when the active
 * {@code TerrainVertexRequirements} ask for it (gap G-5), and regenerates only the column when the
 * requirements change - the axis and general ranges never move.
 */
public record SectionGeometry(
        List<PackedAxisQuad> solidAxis,
        List<PackedAxisQuad> cutoutAxis,
        List<PackedAxisQuad> translucentAxis,
        List<GeneralQuad> solidGeneral,
        List<GeneralQuad> cutoutGeneral,
        List<GeneralQuad> translucentGeneral,
        short[] mcEntityIds) {

    public SectionGeometry {
        solidAxis = List.copyOf(solidAxis);
        cutoutAxis = List.copyOf(cutoutAxis);
        translucentAxis = List.copyOf(translucentAxis);
        solidGeneral = List.copyOf(solidGeneral);
        cutoutGeneral = List.copyOf(cutoutGeneral);
        translucentGeneral = List.copyOf(translucentGeneral);
        mcEntityIds = mcEntityIds == null ? new short[0] : mcEntityIds.clone();
        int axisTotal = solidAxis.size() + cutoutAxis.size() + translucentAxis.size();
        if (mcEntityIds.length != 0 && mcEntityIds.length != axisTotal) {
            throw new IllegalArgumentException("mcEntityIds has " + mcEntityIds.length
                    + " entries for " + axisTotal + " axis quads");
        }
    }

    /** Axis quads of one layer: 0 = SOLID, 1 = CUTOUT, 2 = TRANSLUCENT. */
    public List<PackedAxisQuad> axisLayer(int layer) {
        return switch (layer) {
            case 0 -> solidAxis;
            case 1 -> cutoutAxis;
            case 2 -> translucentAxis;
            default -> throw new IllegalArgumentException("layer " + layer);
        };
    }

    /** General quads of one layer: 0 = SOLID, 1 = CUTOUT, 2 = TRANSLUCENT. */
    public List<GeneralQuad> generalLayer(int layer) {
        return switch (layer) {
            case 0 -> solidGeneral;
            case 1 -> cutoutGeneral;
            case 2 -> translucentGeneral;
            default -> throw new IllegalArgumentException("layer " + layer);
        };
    }

    public int axisQuadCount() {
        return solidAxis.size() + cutoutAxis.size() + translucentAxis.size();
    }

    public int generalQuadCount() {
        return solidGeneral.size() + cutoutGeneral.size() + translucentGeneral.size();
    }

    public boolean isEmpty() {
        return axisQuadCount() == 0 && generalQuadCount() == 0;
    }
}
