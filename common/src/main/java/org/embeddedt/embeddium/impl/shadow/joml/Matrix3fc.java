package org.embeddedt.embeddium.impl.shadow.joml;

import java.nio.FloatBuffer;

/**
 * Read-only view of a 3x3 float matrix for Demonica's {@code IrisCeleritasChunkShaderInterface} seam (Tier C).
 */
public interface Matrix3fc {
    float m00();
    float m01();
    float m02();
    float m10();
    float m11();
    float m12();
    float m20();
    float m21();
    float m22();
    FloatBuffer get(FloatBuffer buffer);
    org.joml.Matrix3fc toJoml();
}
