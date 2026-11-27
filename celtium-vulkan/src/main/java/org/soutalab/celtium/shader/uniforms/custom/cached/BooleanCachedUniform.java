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
import java.util.function.BooleanSupplier;

public class BooleanCachedUniform extends CachedUniform {
    private final BooleanSupplier supplier;
    private boolean cached;

    public BooleanCachedUniform(String name, UniformUpdateFrequency updateFrequency, BooleanSupplier supplier) {
        super(name, updateFrequency);
        this.supplier = supplier;
    }

    @Override
    protected boolean doUpdate() {
        boolean prev = this.cached;
        this.cached = this.supplier.getAsBoolean();
        return prev != cached;
    }

    @Override
    public void writeStd140(MemorySegment segment, int offsetBytes) {
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, offsetBytes, this.cached ? 1 : 0);
    }

    @Override
    public PackExecutionPlan.PackUniformLayout.UniformType coreUniformType() {
        return PackExecutionPlan.PackUniformLayout.UniformType.INT;
    }

    @Override
    public void writeTo(FunctionReturn functionReturn) {
        functionReturn.booleanReturn = this.cached;
    }

    @Override
    public Type getType() {
        return Type.Boolean;
    }
}
