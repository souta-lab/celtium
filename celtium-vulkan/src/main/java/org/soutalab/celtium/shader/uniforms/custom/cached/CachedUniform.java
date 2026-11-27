/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 * Adapted natively for Celtium Vulkan 1.3: writes directly into an FFM MemorySegment std140 block.
 */
package org.soutalab.celtium.shader.uniforms.custom.cached;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.soutalab.celtium.core.shader.PackExecutionPlan;
import org.soutalab.celtium.shader.model.UniformUpdateFrequency;
import org.soutalab.celtium.shader.parsing.VectorType;
import org.soutalab.celtium.shader.stareval.expression.Expression;
import org.soutalab.celtium.shader.stareval.expression.VariableExpression;
import org.soutalab.celtium.shader.stareval.function.FunctionContext;
import org.soutalab.celtium.shader.stareval.function.FunctionReturn;
import org.soutalab.celtium.shader.stareval.function.Type;

import java.lang.foreign.MemorySegment;

public abstract class CachedUniform implements VariableExpression {
    private final String name;
    private final UniformUpdateFrequency updateFrequency;
    private boolean changed = true;

    public CachedUniform(String name, UniformUpdateFrequency updateFrequency) {
        this.name = name;
        this.updateFrequency = updateFrequency;
    }

    public static CachedUniform forExpression(String name, Type type, Expression expression, FunctionContext context) {
        final FunctionReturn held = new FunctionReturn();
        final UniformUpdateFrequency frequency = UniformUpdateFrequency.CUSTOM;
        if (type.equals(Type.Boolean)) {
            return new BooleanCachedUniform(name, frequency, () -> {
                expression.evaluateTo(context, held);
                return held.booleanReturn;
            });
        } else if (type.equals(Type.Int)) {
            return new IntCachedUniform(name, frequency, () -> {
                expression.evaluateTo(context, held);
                return held.intReturn;
            });
        } else if (type.equals(Type.Float)) {
            return new FloatCachedUniform(name, frequency, () -> {
                expression.evaluateTo(context, held);
                return held.floatReturn;
            });
        } else if (type.equals(VectorType.VEC2)) {
            return new Float2VectorCachedUniform(name, frequency, () -> {
                expression.evaluateTo(context, held);
                return (Vector2f) held.objectReturn;
            });
        } else if (type.equals(VectorType.VEC3)) {
            return new Float3VectorCachedUniform(name, frequency, () -> {
                expression.evaluateTo(context, held);
                return (Vector3f) held.objectReturn;
            });
        } else if (type.equals(VectorType.VEC4)) {
            return new Float4VectorCachedUniform(name, frequency, () -> {
                expression.evaluateTo(context, held);
                return (Vector4f) held.objectReturn;
            });
        } else {
            throw new IllegalArgumentException("Custom uniforms of type: " + type + " are currently not supported");
        }
    }

    public void markUnchanged() {
        this.changed = false;
    }

    public void update() {
        doUpdate();
        this.changed = true;
    }

    protected abstract boolean doUpdate();

    /** Writes the current cached value into the std140 pack uniform block at {@code offsetBytes}. */
    public abstract void writeStd140(MemorySegment segment, int offsetBytes);

    /** Returns the Celtium core uniform type for std140 layout registration. */
    public abstract PackExecutionPlan.PackUniformLayout.UniformType coreUniformType();

    @Override
    public void evaluateTo(FunctionContext context, FunctionReturn functionReturn) {
        this.writeTo(functionReturn);
    }

    public abstract void writeTo(FunctionReturn functionReturn);

    public abstract Type getType();

    public String getName() {
        return name;
    }

    public UniformUpdateFrequency getUpdateFrequency() {
        return updateFrequency;
    }
}
