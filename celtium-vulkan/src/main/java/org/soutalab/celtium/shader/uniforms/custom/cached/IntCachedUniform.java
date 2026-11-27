/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 */
package org.soutalab.celtium.shader.uniforms.custom.cached;

import org.soutalab.celtium.core.shader.PackExecutionPlan;
import org.soutalab.celtium.shader.model.UniformUpdateFrequency;
import org.soutalab.celtium.shader.stareval.function.FunctionReturn;
import org.soutalab.celtium.shader.stareval.function.Type;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.function.IntSupplier;

public class IntCachedUniform extends CachedUniform {
    private final IntSupplier supplier;
    private int cached;

    public IntCachedUniform(String name, UniformUpdateFrequency updateFrequency, IntSupplier supplier) {
        super(name, updateFrequency);
        this.supplier = supplier;
    }

    @Override
    protected boolean doUpdate() {
        int prev = this.cached;
        this.cached = this.supplier.getAsInt();
        return prev != cached;
    }

    @Override
    public void writeStd140(MemorySegment segment, int offsetBytes) {
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, offsetBytes, this.cached);
    }

    @Override
    public PackExecutionPlan.PackUniformLayout.UniformType coreUniformType() {
        return PackExecutionPlan.PackUniformLayout.UniformType.INT;
    }

    @Override
    public void writeTo(FunctionReturn functionReturn) {
        functionReturn.intReturn = this.cached;
    }

    @Override
    public Type getType() {
        return Type.Int;
    }
}
