package org.embeddedt.embeddium.impl.shadow.joml;

/**
 * Compatibility bridge for Demonica's {@code CeleritasJoml} seam (Tier C).
 * Delegates to Cleanroom's {@code org.joml.Vector3d} without bundling a second JOML jar.
 */
public class Vector3d {
    public double x;
    public double y;
    public double z;

    public Vector3d() {
    }

    public Vector3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public org.joml.Vector3d toJoml() {
        return new org.joml.Vector3d(this.x, this.y, this.z);
    }
}
