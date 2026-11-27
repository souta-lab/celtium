/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 */
package org.soutalab.celtium.shader.uniforms.custom.cached;

import org.soutalab.celtium.core.shader.PackExecutionPlan;
import org.soutalab.celtium.shader.model.FloatSupplier;
import org.soutalab.celtium.shader.model.UniformUpdateFrequency;
import org.soutalab.celtium.shader.stareval.function.FunctionReturn;
import org.soutalab.celtium.shader.stareval.function.Type;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class FloatCachedUniform extends CachedUniform {
    private final FloatSupplier supplier;
    private float cached;

    public FloatCachedUniform(String name, UniformUpdateFrequency updateFrequency, FloatSupplier supplier) {
        super(name, updateFrequency);
        this.supplier = supplier;
    }

    @Override
    protected boolean doUpdate() {
        float prev = this.cached;
        this.cached = this.supplier.getAsFloat();
        return Float.floatToRawIntBits(prev) != Float.floatToRawIntBits(cached);
    }

    @Override
    public void writeStd140(MemorySegment segment, int offsetBytes) {
        segment.set(ValueLayout.JAVA_FLOAT_UNALIGNED, offsetBytes, this.cached);
    }

    @Override
    public PackExecutionPlan.PackUniformLayout.UniformType coreUniformType() {
        return PackExecutionPlan.PackUniformLayout.UniformType.FLOAT;
    }

    @Override
    public void writeTo(FunctionReturn functionReturn) {
        functionReturn.floatReturn = this.cached;
    }

    @Override
    public Type getType() {
        return Type.Float;
    }
}
