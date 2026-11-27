/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 */
package org.soutalab.celtium.shader.uniforms.custom.cached;

import org.joml.Vector2i;
import org.soutalab.celtium.core.shader.PackExecutionPlan;
import org.soutalab.celtium.shader.model.UniformUpdateFrequency;
import org.soutalab.celtium.shader.parsing.VectorType;
import org.soutalab.celtium.shader.stareval.function.Type;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.function.Supplier;

public class Int2VectorCachedUniform extends VectorCachedUniform<Vector2i> {
    public Int2VectorCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<Vector2i> supplier) {
        super(name, updateFrequency, supplier);
    }

    @Override
    protected void setFrom(Vector2i other, Vector2i target) {
        target.set(other);
    }

    @Override
    protected Vector2i create() {
        return new Vector2i();
    }

    @Override
    public void writeStd140(MemorySegment segment, int offsetBytes) {
        Vector2i v = this.cached;
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, offsetBytes, v != null ? v.x : 0);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, offsetBytes + 4, v != null ? v.y : 0);
    }

    @Override
    public PackExecutionPlan.PackUniformLayout.UniformType coreUniformType() {
        return PackExecutionPlan.PackUniformLayout.UniformType.IVEC2;
    }

    @Override
    public Type getType() {
        return VectorType.I_VEC2;
    }
}
