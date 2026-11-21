package org.embeddedt.embeddium.impl.shadow.joml;

import java.nio.FloatBuffer;

/**
 * Compatibility bridge for Demonica's {@code IrisCeleritasChunkShaderInterface} seam (Tier C).
 * Delegates all math to Cleanroom's {@code org.joml.Matrix3f}.
 */
public class Matrix3f implements Matrix3fc {
    private final org.joml.Matrix3f delegate;

    public Matrix3f() {
        this.delegate = new org.joml.Matrix3f();
    }

    public Matrix3f(org.joml.Matrix3fc source) {
        this.delegate = new org.joml.Matrix3f(source);
    }

    public Matrix3f set(Matrix4fc mat) {
        this.delegate.set(
                mat.m00(), mat.m01(), mat.m02(),
                mat.m10(), mat.m11(), mat.m12(),
                mat.m20(), mat.m21(), mat.m22());
        return this;
    }

    public Matrix3f set(Matrix3fc mat) {
        this.delegate.set(
                mat.m00(), mat.m01(), mat.m02(),
                mat.m10(), mat.m11(), mat.m12(),
                mat.m20(), mat.m21(), mat.m22());
        return this;
    }

    public Matrix3f invert() {
        this.delegate.invert();
        return this;
    }

    public Matrix3f transpose() {
        this.delegate.transpose();
        return this;
    }

    @Override public float m00() { return this.delegate.m00(); }
    @Override public float m01() { return this.delegate.m01(); }
    @Override public float m02() { return this.delegate.m02(); }
    @Override public float m10() { return this.delegate.m10(); }
    @Override public float m11() { return this.delegate.m11(); }
    @Override public float m12() { return this.delegate.m12(); }
    @Override public float m20() { return this.delegate.m20(); }
    @Override public float m21() { return this.delegate.m21(); }
    @Override public float m22() { return this.delegate.m22(); }

    @Override
    public FloatBuffer get(FloatBuffer buffer) {
        return this.delegate.get(buffer);
    }

    @Override
    public org.joml.Matrix3fc toJoml() {
        return this.delegate;
    }
}
