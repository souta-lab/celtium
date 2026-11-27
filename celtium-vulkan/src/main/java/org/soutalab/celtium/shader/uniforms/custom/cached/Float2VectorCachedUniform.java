/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 */
package org.soutalab.celtium.shader.uniforms.custom.cached;

import org.joml.Vector2f;
import org.soutalab.celtium.core.shader.PackExecutionPlan;
import org.soutalab.celtium.shader.model.UniformUpdateFrequency;
import org.soutalab.celtium.shader.parsing.VectorType;
import org.soutalab.celtium.shader.stareval.function.Type;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.function.Supplier;

public class Float2VectorCachedUniform extends VectorCachedUniform<Vector2f> {
    public Float2VectorCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<Vector2f> supplier) {
        super(name, updateFrequency, supplier);
    }

    @Override
    protected void setFrom(Vector2f other, Vector2f target) {
        target.set(other);
    }

    @Override
    protected Vector2f create() {
        return new Vector2f();
    }

    @Override
    public void writeStd140(MemorySegment segment, int offsetBytes) {
        Vector2f v = this.cached;
        segment.set(ValueLayout.JAVA_FLOAT_UNALIGNED, offsetBytes, v != null ? v.x : 0.0f);
        segment.set(ValueLayout.JAVA_FLOAT_UNALIGNED, offsetBytes + 4, v != null ? v.y : 0.0f);
    }

    @Override
    public PackExecutionPlan.PackUniformLayout.UniformType coreUniformType() {
        return PackExecutionPlan.PackUniformLayout.UniformType.VEC2;
    }

    @Override
    public Type getType() {
        return VectorType.VEC2;
    }
}
