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
 * <p>
 * The per-corner smooth-light column (R-107) is carried as two {@code int}s per quad - axis
 * quads first, then general quads, each in layer order - and, unlike the pack column, is
 * unconditional: the built-in vertex shaders always read it through the descriptor's
 * {@code lightDataBase}. The 7-argument constructor (test fixtures and other geometry sources
 * without a mesher) fills it with the neutral field (full sky sum, no block light, AO step 15),
 * which reproduces the pre-R-107 flat look at full brightness.
 */
public record SectionGeometry(
        List<PackedAxisQuad> solidAxis,
        List<PackedAxisQuad> cutoutAxis,
        List<PackedAxisQuad> translucentAxis,
        List<GeneralQuad> solidGeneral,
        List<GeneralQuad> cutoutGeneral,
        List<GeneralQuad> translucentGeneral,
        short[] mcEntityIds,
        int[] cornerLight) {

    /** The neutral corner-light field: sky sum 60, block sum 0, AO step 15 (all four corners). */
    public static final int NEUTRAL_CORNER_FIELD = 60 | (15 << 12);

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
        int quadTotal = axisTotal + solidGeneral.size() + cutoutGeneral.size()
                + translucentGeneral.size();
        cornerLight = cornerLight == null ? new int[0] : cornerLight.clone();
        if (cornerLight.length != 0 && cornerLight.length != quadTotal * 2) {
            throw new IllegalArgumentException("cornerLight has " + cornerLight.length
                    + " ints for " + quadTotal + " quads");
        }
        if (cornerLight.length == 0 && quadTotal > 0) {
            // Invariant for the store: a non-empty geometry always carries a full column.
            cornerLight = neutralColumn(quadTotal);
        }
    }

    /** Geometry without a mesher corner-light column: the neutral column is synthesised. */
    public SectionGeometry(
            List<PackedAxisQuad> solidAxis,
            List<PackedAxisQuad> cutoutAxis,
            List<PackedAxisQuad> translucentAxis,
            List<GeneralQuad> solidGeneral,
            List<GeneralQuad> cutoutGeneral,
            List<GeneralQuad> translucentGeneral,
            short[] mcEntityIds) {
        this(solidAxis, cutoutAxis, translucentAxis, solidGeneral, cutoutGeneral,
                translucentGeneral, mcEntityIds, neutralColumn(
                        solidAxis.size() + cutoutAxis.size() + translucentAxis.size()
                                + solidGeneral.size() + cutoutGeneral.size()
                                + translucentGeneral.size()));
    }

    private static int[] neutralColumn(int quadTotal) {
        int[] column = new int[quadTotal * 2];
        int word = NEUTRAL_CORNER_FIELD | (NEUTRAL_CORNER_FIELD << 16);
        java.util.Arrays.fill(column, word);
        return column;
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
