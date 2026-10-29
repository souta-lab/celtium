package org.embeddedt.embeddium.impl.render.viewport;

import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.embeddedt.embeddium.impl.util.PositionUtil;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.RoundingMode;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.joml.Vector3ic;

public final class Viewport {
    private final Frustum frustum;
    private final CameraTransform transform;

    private final Vector3i chunkCoords;
    private final Vector3i blockCoords;

    private final @Nullable Matrix4fc vpMatrix;
    private final @Nullable Vector3fc frustumOffset;

    public Viewport(Frustum frustum, Vector3d position) {
        this(frustum, position, null);
    }

    public Viewport(Frustum frustum, Vector3d position, @Nullable Matrix4fc vpMatrix) {
        this(frustum, position, vpMatrix, null);
    }

    public Viewport(Frustum frustum, Vector3d position, @Nullable Matrix4fc vpMatrix, @Nullable Vector3fc frustumOffset) {
        this.frustum = frustum;
        this.transform = new CameraTransform(position.x, position.y, position.z);

        this.vpMatrix = vpMatrix == null ? null : new Matrix4f(vpMatrix);
        this.frustumOffset = frustumOffset == null ? null : new Vector3f(frustumOffset);

        this.chunkCoords = new Vector3i(
                PositionUtil.posToSectionCoord(position.x),
                PositionUtil.posToSectionCoord(position.y),
                PositionUtil.posToSectionCoord(position.z)
        );

        this.blockCoords = new Vector3i(position.x, position.y, position.z, RoundingMode.FLOOR);
    }

    public boolean isBoxVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return this.frustum.testAab(
                (float)(minX - this.transform.intX) - this.transform.fracX,
                (float)(minY - this.transform.intY) - this.transform.fracY,
                (float)(minZ - this.transform.intZ) - this.transform.fracZ,
                (float)(maxX - this.transform.intX) - this.transform.fracX,
                (float)(maxY - this.transform.intY) - this.transform.fracY,
                (float)(maxZ - this.transform.intZ) - this.transform.fracZ
        );
    }

    public boolean isBoxVisible(int intOriginX, int intOriginY, int intOriginZ, float floatSize) {
        return isBoxVisible(intOriginX, intOriginY, intOriginZ, floatSize, floatSize, floatSize);
    }

    public boolean isBoxVisible(int intOriginX, int intOriginY, int intOriginZ, float floatSizeX, float floatSizeY, float floatSizeZ) {
        float floatOriginX = (intOriginX - this.transform.intX) - this.transform.fracX;
        float floatOriginY = (intOriginY - this.transform.intY) - this.transform.fracY;
        float floatOriginZ = (intOriginZ - this.transform.intZ) - this.transform.fracZ;

        return this.frustum.testAab(
                floatOriginX - floatSizeX,
                floatOriginY - floatSizeY,
                floatOriginZ - floatSizeZ,

                floatOriginX + floatSizeX,
                floatOriginY + floatSizeY,
                floatOriginZ + floatSizeZ
        );
    }

    public int intersectCameraRelativeBox(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return this.frustum.intersectAab(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public @Nullable Matrix4fc getVpMatrix() {
        return this.vpMatrix;
    }

    /** True only when the search would test the same boxes against the same camera. */
    public boolean sameSearchView(@Nullable Viewport other) {
        if (other == null || this.vpMatrix == null || other.vpMatrix == null
                || this.frustumOffset == null || other.frustumOffset == null
                || !this.blockCoords.equals(other.blockCoords)) {
            return false;
        }
        CameraTransform a = this.transform;
        CameraTransform b = other.transform;
        if (a.intX != b.intX || a.intY != b.intY || a.intZ != b.intZ
                || a.fracX != b.fracX || a.fracY != b.fracY || a.fracZ != b.fracZ
                || !this.frustumOffset.equals(other.frustumOffset)) {
            return false;
        }
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                if (this.vpMatrix.get(column, row) != other.vpMatrix.get(column, row)) {
                    return false;
                }
            }
        }
        return true;
    }

    public Frustum getFrustum() {
        return this.frustum;
    }

    public CameraTransform getTransform() {
        return this.transform;
    }

    public Vector3ic getChunkCoord() {
        return this.chunkCoords;
    }

    public Vector3ic getBlockCoord() {
        return this.blockCoords;
    }
}
