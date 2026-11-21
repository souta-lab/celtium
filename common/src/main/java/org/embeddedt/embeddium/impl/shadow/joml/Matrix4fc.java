package org.embeddedt.embeddium.impl.shadow.joml;

import java.nio.FloatBuffer;

/**
 * Read-only view of a 4x4 float matrix for Demonica's {@code CeleritasJoml} /
 * {@code IrisCeleritasChunkShaderInterface} seam (Tier C).
 */
public interface Matrix4fc {
    float m00();
    float m01();
    float m02();
    float m03();
    float m10();
    float m11();
    float m12();
    float m13();
    float m20();
    float m21();
    float m22();
    float m23();
    float m30();
    float m31();
    float m32();
    float m33();
    FloatBuffer get(FloatBuffer buffer);
    org.joml.Matrix4fc toJoml();
}
