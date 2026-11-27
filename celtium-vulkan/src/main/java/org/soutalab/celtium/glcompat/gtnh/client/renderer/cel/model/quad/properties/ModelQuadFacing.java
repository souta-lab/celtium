package org.soutalab.celtium.glcompat.gtnh.client.renderer.cel.model.quad.properties;
/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * Only the types the converted GLSM front references are carried; the library
 * as a whole is NOT imported. See THIRD_PARTY_NOTICES.md.
 */


import java.util.Arrays;


import org.soutalab.celtium.glcompat.gtnh.client.renderer.cel.api.util.NormI8;


public enum ModelQuadFacing {

    POS_X(1, 0, 0, Axis.X),
    POS_Y(0, 1, 0, Axis.Y),
    POS_Z(0, 0, 1, Axis.Z),
    NEG_X(-1, 0, 0, Axis.X),
    NEG_Y(0, -1, 0, Axis.Y),
    NEG_Z(0, 0, -1, Axis.Z),
    UNASSIGNED(0, 0, 0, null);

    public static final ModelQuadFacing[] VALUES = ModelQuadFacing.values();
    public static final ModelQuadFacing[] DIRECTIONS = Arrays.stream(VALUES).filter(ModelQuadFacing::isDirection)
            .toArray(ModelQuadFacing[]::new);
    public static final ModelQuadFacing[] HORIZONTAL_DIRECTIONS = Arrays.stream(DIRECTIONS)
            .filter(facing -> facing.axis != Axis.Y).toArray(ModelQuadFacing[]::new);
    public static final Axis[] AXES = Axis.values();

    public static final int COUNT = VALUES.length;

    public static final int NONE = 0;
    public static final int ALL = (1 << COUNT) - 1;

    private final int packedNormal;

    private final int stepX, stepY, stepZ;

    public int getStepX() { return stepX; }
    public int getStepY() { return stepY; }
    public int getStepZ() { return stepZ; }

    private final Axis axis;

    ModelQuadFacing(int stepX, int stepY, int stepZ, Axis axis) {
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
        this.axis = axis;
        this.packedNormal = NormI8.pack(stepX, stepY, stepZ);
    }

    public ModelQuadFacing getOpposite() {
        return switch (this) {
            case POS_Y -> NEG_Y;
            case NEG_Y -> POS_Y;
            case POS_X -> NEG_X;
            case NEG_X -> POS_X;
            case POS_Z -> NEG_Z;
            case NEG_Z -> POS_Z;
            default -> UNASSIGNED;
        };
    }

    public boolean isDirection() {
        return this != UNASSIGNED;
    }

    public enum Axis {

        X,
        Y,
        Z;

        public ModelQuadFacing getFacing(boolean positive) {
            return switch (this) {
                case X -> positive ? POS_X : NEG_X;
                case Y -> positive ? POS_Y : NEG_Y;
                case Z -> positive ? POS_Z : NEG_Z;
            };
        }
    }


    // -- explicit accessors (delomboked during the S4 native conversion) --
    public int getPackedNormal() { return packedNormal; }
    public Axis getAxis() { return axis; }
}
