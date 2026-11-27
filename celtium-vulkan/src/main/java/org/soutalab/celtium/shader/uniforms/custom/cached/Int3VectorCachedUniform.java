/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 */
package org.soutalab.celtium.shader.uniforms.custom.cached;

import org.joml.Vector3i;
import org.soutalab.celtium.core.shader.PackExecutionPlan;
import org.soutalab.celtium.shader.model.UniformUpdateFrequency;
import org.soutalab.celtium.shader.parsing.VectorType;
import org.soutalab.celtium.shader.stareval.function.Type;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.function.Supplier;

public class Int3VectorCachedUniform extends VectorCachedUniform<Vector3i> {
    public Int3VectorCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<Vector3i> supplier) {
        super(name, updateFrequency, supplier);
    }

    @Override
    protected void setFrom(Vector3i other, Vector3i target) {
        target.set(other);
    }

    @Override
    protected Vector3i create() {
        return new Vector3i();
    }

    @Override
    public void writeStd140(MemorySegment segment, int offsetBytes) {
        Vector3i v = this.cached;
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, offsetBytes, v != null ? v.x : 0);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, offsetBytes + 4, v != null ? v.y : 0);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, offsetBytes + 8, v != null ? v.z : 0);
    }

    @Override
    public PackExecutionPlan.PackUniformLayout.UniformType coreUniformType() {
        return PackExecutionPlan.PackUniformLayout.UniformType.IVEC3;
    }

    @Override
    public Type getType() {
        return VectorType.I_VEC3;
    }
}
