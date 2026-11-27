/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 */
package org.soutalab.celtium.shader.uniforms.custom.cached;

import org.joml.Matrix4f;
import org.soutalab.celtium.core.shader.PackExecutionPlan;
import org.soutalab.celtium.shader.model.UniformUpdateFrequency;
import org.soutalab.celtium.shader.parsing.MatrixType;
import org.soutalab.celtium.shader.stareval.function.Type;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.function.Supplier;

public class Float4MatrixCachedUniform extends VectorCachedUniform<Matrix4f> {
    private final float[] scratch = new float[16];

    public Float4MatrixCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<Matrix4f> supplier) {
        super(name, updateFrequency, supplier);
    }

    @Override
    protected void setFrom(Matrix4f other, Matrix4f target) {
        target.set(other);
    }

    @Override
    protected Matrix4f create() {
        return new Matrix4f();
    }

    @Override
    public void writeStd140(MemorySegment segment, int offsetBytes) {
        Matrix4f m = this.cached;
        if (m != null) {
            m.get(scratch);
        } else {
            java.util.Arrays.fill(scratch, 0.0f);
        }
        for (int i = 0; i < 16; i++) {
            segment.set(ValueLayout.JAVA_FLOAT_UNALIGNED, offsetBytes + i * 4, scratch[i]);
        }
    }

    @Override
    public PackExecutionPlan.PackUniformLayout.UniformType coreUniformType() {
        return PackExecutionPlan.PackUniformLayout.UniformType.MAT4;
    }

    @Override
    public Type getType() {
        return MatrixType.MAT4;
    }
}
