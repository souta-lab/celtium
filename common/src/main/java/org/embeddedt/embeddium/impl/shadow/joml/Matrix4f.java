package org.embeddedt.embeddium.impl.shadow.joml;

import java.nio.FloatBuffer;

/**
 * Compatibility bridge for Demonica's {@code CeleritasJoml} /
 * {@code IrisCeleritasChunkShaderInterface} seam (Tier C).
 * Delegates all math to Cleanroom's {@code org.joml.Matrix4f}.
 */
public class Matrix4f implements Matrix4fc {
    private final org.joml.Matrix4f delegate;

    public Matrix4f() {
        this.delegate = new org.joml.Matrix4f();
    }

    public Matrix4f(org.joml.Matrix4fc source) {
        this.delegate = new org.joml.Matrix4f(source);
    }

    public Matrix4f(
            float m00, float m01, float m02, float m03,
            float m10, float m11, float m12, float m13,
            float m20, float m21, float m22, float m23,
            float m30, float m31, float m32, float m33) {
        this.delegate = new org.joml.Matrix4f(
                m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23,
                m30, m31, m32, m33);
    }

    public Matrix4f set(Matrix4fc mat) {
        this.delegate.set(
                mat.m00(), mat.m01(), mat.m02(), mat.m03(),
                mat.m10(), mat.m11(), mat.m12(), mat.m13(),
                mat.m20(), mat.m21(), mat.m22(), mat.m23(),
                mat.m30(), mat.m31(), mat.m32(), mat.m33());
        return this;
    }

    public Matrix4f invert() {
        this.delegate.invert();
        return this;
    }

    public Matrix4f transpose() {
        this.delegate.transpose();
        return this;
    }

    @Override public float m00() { return this.delegate.m00(); }
    @Override public float m01() { return this.delegate.m01(); }
    @Override public float m02() { return this.delegate.m02(); }
    @Override public float m03() { return this.delegate.m03(); }
    @Override public float m10() { return this.delegate.m10(); }
    @Override public float m11() { return this.delegate.m11(); }
    @Override public float m12() { return this.delegate.m12(); }
    @Override public float m13() { return this.delegate.m13(); }
    @Override public float m20() { return this.delegate.m20(); }
    @Override public float m21() { return this.delegate.m21(); }
    @Override public float m22() { return this.delegate.m22(); }
    @Override public float m23() { return this.delegate.m23(); }
    @Override public float m30() { return this.delegate.m30(); }
    @Override public float m31() { return this.delegate.m31(); }
    @Override public float m32() { return this.delegate.m32(); }
    @Override public float m33() { return this.delegate.m33(); }

    @Override
    public FloatBuffer get(FloatBuffer buffer) {
        return this.delegate.get(buffer);
    }

    @Override
    public org.joml.Matrix4fc toJoml() {
        return this.delegate;
    }
}
