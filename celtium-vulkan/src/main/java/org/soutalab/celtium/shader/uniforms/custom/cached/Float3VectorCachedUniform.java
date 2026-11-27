/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 */
package org.soutalab.celtium.shader.uniforms.custom.cached;

import org.joml.Vector3f;
import org.soutalab.celtium.core.shader.PackExecutionPlan;
import org.soutalab.celtium.shader.model.UniformUpdateFrequency;
import org.soutalab.celtium.shader.parsing.VectorType;
import org.soutalab.celtium.shader.stareval.function.Type;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.function.Supplier;

public class Float3VectorCachedUniform extends VectorCachedUniform<Vector3f> {
    public Float3VectorCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<Vector3f> supplier) {
        super(name, updateFrequency, supplier);
    }

    @Override
    protected void setFrom(Vector3f other, Vector3f target) {
        target.set(other);
    }

    @Override
    protected Vector3f create() {
        return new Vector3f();
    }

    @Override
    public void writeStd140(MemorySegment segment, int offsetBytes) {
        Vector3f v = this.cached;
        segment.set(ValueLayout.JAVA_FLOAT_UNALIGNED, offsetBytes, v != null ? v.x : 0.0f);
        segment.set(ValueLayout.JAVA_FLOAT_UNALIGNED, offsetBytes + 4, v != null ? v.y : 0.0f);
        segment.set(ValueLayout.JAVA_FLOAT_UNALIGNED, offsetBytes + 8, v != null ? v.z : 0.0f);
    }

    @Override
    public PackExecutionPlan.PackUniformLayout.UniformType coreUniformType() {
        return PackExecutionPlan.PackUniformLayout.UniformType.VEC3;
    }

    @Override
    public Type getType() {
        return VectorType.VEC3;
    }
}
