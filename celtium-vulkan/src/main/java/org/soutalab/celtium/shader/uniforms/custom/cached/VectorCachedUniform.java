/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 */
package org.soutalab.celtium.shader.uniforms.custom.cached;

import org.soutalab.celtium.shader.model.UniformUpdateFrequency;
import org.soutalab.celtium.shader.stareval.function.FunctionReturn;

import java.util.function.Supplier;

public abstract class VectorCachedUniform<T> extends CachedUniform {
    private final Supplier<T> supplier;
    protected T cached;

    public VectorCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<T> supplier) {
        super(name, updateFrequency);
        this.supplier = supplier;
    }

    protected abstract void setFrom(T other, T target);

    protected abstract T create();

    @Override
    protected boolean doUpdate() {
        T next = this.supplier.get();
        if (next == null) {
            return false;
        }
        if (this.cached == null) {
            this.cached = create();
            setFrom(next, this.cached);
            return true;
        }
        if (!this.cached.equals(next)) {
            setFrom(next, this.cached);
            return true;
        }
        return false;
    }

    @Override
    public void writeTo(FunctionReturn functionReturn) {
        functionReturn.objectReturn = this.cached;
    }
}
