/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 * Adapted natively for Celtium Vulkan 1.3 (MC/GLSM-free).
 */
package org.soutalab.celtium.shader.uniforms.custom;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.soutalab.celtium.shader.model.FloatSupplier;
import org.soutalab.celtium.shader.model.UniformHolder;
import org.soutalab.celtium.shader.model.UniformType;
import org.soutalab.celtium.shader.model.UniformUpdateFrequency;
import org.soutalab.celtium.shader.stareval.function.Type;
import org.soutalab.celtium.shader.uniforms.custom.cached.BooleanCachedUniform;
import org.soutalab.celtium.shader.uniforms.custom.cached.CachedUniform;
import org.soutalab.celtium.shader.uniforms.custom.cached.Float2VectorCachedUniform;
import org.soutalab.celtium.shader.uniforms.custom.cached.Float3VectorCachedUniform;
import org.soutalab.celtium.shader.uniforms.custom.cached.Float4MatrixCachedUniform;
import org.soutalab.celtium.shader.uniforms.custom.cached.Float4VectorCachedUniform;
import org.soutalab.celtium.shader.uniforms.custom.cached.FloatCachedUniform;
import org.soutalab.celtium.shader.uniforms.custom.cached.Int2VectorCachedUniform;
import org.soutalab.celtium.shader.uniforms.custom.cached.Int3VectorCachedUniform;
import org.soutalab.celtium.shader.uniforms.custom.cached.IntCachedUniform;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class CustomUniformFixedInputUniformsHolder {
    private final Map<String, CachedUniform> inputUniforms;

    private CustomUniformFixedInputUniformsHolder(Map<String, CachedUniform> inputUniforms) {
        this.inputUniforms = inputUniforms;
    }

    public Type getType(String name) {
        CachedUniform uniform = inputUniforms.get(name);
        return uniform != null ? uniform.getType() : null;
    }

    public CachedUniform get(String name) {
        return inputUniforms.get(name);
    }

    public Collection<CachedUniform> getAll() {
        return Collections.unmodifiableCollection(inputUniforms.values());
    }

    public void updateAll() {
        for (CachedUniform uniform : inputUniforms.values()) {
            uniform.update();
        }
    }

    public static class Builder implements UniformHolder {
        private final Map<String, CachedUniform> uniforms = new Object2ObjectOpenHashMap<>();

        private UniformHolder add(CachedUniform uniform) {
            uniforms.put(uniform.getName(), uniform);
            return this;
        }

        @Override
        public UniformHolder uniform1f(UniformUpdateFrequency updateFrequency, String name, FloatSupplier value) {
            return add(new FloatCachedUniform(name, updateFrequency, value));
        }

        @Override
        public UniformHolder uniform1f(UniformUpdateFrequency updateFrequency, String name, IntSupplier value) {
            return add(new FloatCachedUniform(name, updateFrequency, () -> (float) value.getAsInt()));
        }

        @Override
        public UniformHolder uniform1f(UniformUpdateFrequency updateFrequency, String name, DoubleSupplier value) {
            return add(new FloatCachedUniform(name, updateFrequency, () -> (float) value.getAsDouble()));
        }

        @Override
        public UniformHolder uniform1i(UniformUpdateFrequency updateFrequency, String name, IntSupplier value) {
            return add(new IntCachedUniform(name, updateFrequency, value));
        }

        @Override
        public UniformHolder uniform1b(UniformUpdateFrequency updateFrequency, String name, BooleanSupplier value) {
            return add(new BooleanCachedUniform(name, updateFrequency, value));
        }

        @Override
        public UniformHolder uniform2f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector2f> value) {
            return add(new Float2VectorCachedUniform(name, updateFrequency, value));
        }

        @Override
        public UniformHolder uniform2i(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector2ic> value) {
            return add(new Int2VectorCachedUniform(name, updateFrequency, () -> {
                Vector2ic v = value.get();
                return v == null ? new Vector2i() : new Vector2i(v.x(), v.y());
            }));
        }

        @Override
        public UniformHolder uniform3f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3fc> value) {
            return add(new Float3VectorCachedUniform(name, updateFrequency, () -> {
                Vector3fc v = value.get();
                return v == null ? new Vector3f() : new Vector3f(v.x(), v.y(), v.z());
            }));
        }

        @Override
        public UniformHolder uniform3i(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3ic> value) {
            return add(new Int3VectorCachedUniform(name, updateFrequency, () -> {
                Vector3ic v = value.get();
                return v == null ? new Vector3i() : new Vector3i(v.x(), v.y(), v.z());
            }));
        }

        @Override
        public UniformHolder uniformTruncated3f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector4fc> value) {
            return add(new Float3VectorCachedUniform(name, updateFrequency, () -> {
                Vector4fc v = value.get();
                return v == null ? new Vector3f() : new Vector3f(v.x(), v.y(), v.z());
            }));
        }

        @Override
        public UniformHolder uniform3d(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3d> value) {
            return add(new Float3VectorCachedUniform(name, updateFrequency, () -> {
                Vector3d v = value.get();
                return v == null ? new Vector3f() : new Vector3f((float) v.x, (float) v.y, (float) v.z);
            }));
        }

        @Override
        public UniformHolder uniform4f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector4fc> value) {
            return add(new Float4VectorCachedUniform(name, updateFrequency, () -> {
                Vector4fc v = value.get();
                return v == null ? new Vector4f() : new Vector4f(v.x(), v.y(), v.z(), v.w());
            }));
        }

        @Override
        public UniformHolder uniform4fArray(UniformUpdateFrequency updateFrequency, String name, Supplier<float[]> value) {
            return add(new Float4VectorCachedUniform(name, updateFrequency, () -> {
                float[] a = value.get();
                return a != null && a.length >= 4 ? new Vector4f(a[0], a[1], a[2], a[3]) : new Vector4f();
            }));
        }

        @Override
        public UniformHolder uniformMatrix(UniformUpdateFrequency updateFrequency, String name, Supplier<Matrix4fc> value) {
            return add(new Float4MatrixCachedUniform(name, updateFrequency, () -> {
                Matrix4fc m = value.get();
                return m == null ? new Matrix4f() : new Matrix4f(m);
            }));
        }

        @Override
        public UniformHolder externallyManagedUniform(String name, UniformType type) {
            return this;
        }

        public CustomUniformFixedInputUniformsHolder build() {
            return new CustomUniformFixedInputUniformsHolder(uniforms);
        }
    }
}
