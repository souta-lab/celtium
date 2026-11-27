/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 * Adapted natively for Celtium Vulkan 1.3 (GLSM-free, Celeritas-free, DH-free).
 */
package org.soutalab.celtium.shader.transform;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.soutalab.celtium.shader.ShaderLogger;
import org.soutalab.celtium.shader.model.InputAvailability;
import org.soutalab.celtium.shader.model.TextureType;
import org.soutalab.celtium.shader.model.Tri;
import org.soutalab.celtium.shader.shaderpack.texture.TextureStage;
import org.soutalab.celtium.shader.transform.parameter.AttributeParameters;
import org.soutalab.celtium.shader.transform.parameter.CeltiumTerrainParameters;
import org.soutalab.celtium.shader.transform.parameter.ComputeParameters;
import org.soutalab.celtium.shader.transform.parameter.Parameters;
import org.soutalab.celtium.shader.transform.parameter.TextureStageParameters;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class TransformPatcher {

    private static final int MAX_CACHE_ENTRIES = 400;
    private static final Map<CacheKey, Map<PatchShaderType, String>> cache =
            new LinkedHashMap<>(MAX_CACHE_ENTRIES + 1, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, Map<PatchShaderType, String>> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            };

    private static final class CacheKey {
        final Parameters parameters;
        final String vertex;
        final String geometry;
        final String tessControl;
        final String tessEval;
        final String fragment;
        final String compute;

        CacheKey(Parameters parameters, String vertex, String geometry, String tessControl, String tessEval, String fragment) {
            this.parameters = parameters;
            this.vertex = vertex;
            this.geometry = geometry;
            this.tessControl = tessControl;
            this.tessEval = tessEval;
            this.fragment = fragment;
            this.compute = null;
        }

        CacheKey(Parameters parameters, String compute) {
            this.parameters = parameters;
            this.vertex = null;
            this.geometry = null;
            this.tessControl = null;
            this.tessEval = null;
            this.fragment = null;
            this.compute = compute;
        }

        @Override
        public int hashCode() {
            return Objects.hash(parameters, vertex, geometry, tessControl, tessEval, fragment, compute);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CacheKey other)) return false;
            return Objects.equals(parameters, other.parameters)
                    && Objects.equals(vertex, other.vertex)
                    && Objects.equals(geometry, other.geometry)
                    && Objects.equals(tessControl, other.tessControl)
                    && Objects.equals(tessEval, other.tessEval)
                    && Objects.equals(fragment, other.fragment)
                    && Objects.equals(compute, other.compute);
        }
    }

    private static Map<PatchShaderType, String> transform(
            String vertex, String geometry, String tessControl, String tessEval, String fragment, Parameters parameters) {
        if (vertex == null && geometry == null && tessControl == null && tessEval == null && fragment == null) {
            return null;
        }

        CacheKey key = new CacheKey(parameters, vertex, geometry, tessControl, tessEval, fragment);
        synchronized (cache) {
            Map<PatchShaderType, String> cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }

        Map<PatchShaderType, String> transformed = ShaderTransformer.transform(
                vertex, geometry, tessControl, tessEval, fragment, parameters);
        return cacheResult(key, transformed);
    }

    static Map<PatchShaderType, String> transformCompute(String compute, Parameters parameters) {
        if (compute == null) {
            return null;
        }
        CacheKey key = new CacheKey(parameters, compute);
        synchronized (cache) {
            Map<PatchShaderType, String> cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Map<PatchShaderType, String> transformed = ShaderTransformer.transformCompute(compute, parameters);
        return cacheResult(key, transformed);
    }

    private static Map<PatchShaderType, String> cacheResult(CacheKey key, Map<PatchShaderType, String> transformed) {
        if (transformed == null) {
            return null;
        }
        EnumMap<PatchShaderType, String> copy = new EnumMap<>(PatchShaderType.class);
        copy.putAll(transformed);
        Map<PatchShaderType, String> immutable = Collections.unmodifiableMap(copy);
        synchronized (cache) {
            Map<PatchShaderType, String> existing = cache.get(key);
            if (existing != null) {
                return existing;
            }
            cache.put(key, immutable);
            return immutable;
        }
    }

    public static Map<PatchShaderType, String> patchAttributes(
            String vertex, String geometry, String tessControl, String tessEval, String fragment, InputAvailability inputs) {
        return transform(vertex, geometry, tessControl, tessEval, fragment,
                new AttributeParameters(Patch.ATTRIBUTES, geometry != null,
                        tessControl != null || tessEval != null, inputs, ShaderInstancing.NONE));
    }

    public static Map<PatchShaderType, String> patchAttributes(
            String vertex, String geometry, String fragment, InputAvailability inputs) {
        return patchAttributes(vertex, geometry, null, null, fragment, inputs);
    }

    /**
     * The attributes patch with an explicit instancing contract (the weather feed contract's
     * {@code ShaderInstancing.WEATHER} variant). The pack compiler's registered default
     * {@code gbuffers_weather} stays on {@link #patchAttributes} (instancing NONE - the
     * vanilla rain/snow path through Lane 2); this entry exists so the instanced variant is
     * reachable and testable for the producer that binds the 80-byte instance record.
     */
    public static Map<PatchShaderType, String> patchAttributesInstanced(
            String vertex, String geometry, String fragment, InputAvailability inputs,
            ShaderInstancing instancing) {
        return transform(vertex, geometry, null, null, fragment,
                new AttributeParameters(Patch.ATTRIBUTES, geometry != null, false, inputs, instancing));
    }

    public static Map<PatchShaderType, String> patchCeltiumTerrain(
            String vertex, String geometry, String fragment) {
        return transform(vertex, geometry, null, null, fragment,
                new CeltiumTerrainParameters(Patch.CELTIUM_TERRAIN));
    }

    public static Map<PatchShaderType, String> patchCeltiumTerrain(
            String vertex, String geometry, String fragment,
            Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
        return transform(vertex, geometry, null, null, fragment,
                new CeltiumTerrainParameters(Patch.CELTIUM_TERRAIN, textureMap));
    }

    public static Map<PatchShaderType, String> patchComposite(String vertex, String geometry, String fragment) {
        return patchComposite(vertex, geometry, null, null, fragment, TextureStage.COMPOSITE_AND_FINAL, null);
    }

    public static Map<PatchShaderType, String> patchComposite(
            String vertex, String geometry, String fragment,
            TextureStage stage, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
        return patchComposite(vertex, geometry, null, null, fragment, stage, textureMap);
    }

    public static Map<PatchShaderType, String> patchComposite(
            String vertex, String geometry, String tessControl, String tessEval, String fragment,
            TextureStage stage, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
        return transform(vertex, geometry, tessControl, tessEval, fragment,
                new TextureStageParameters(Patch.COMPOSITE, stage, textureMap));
    }

    public static String patchCompute(
            String name, String compute, TextureStage stage,
            Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
        Map<PatchShaderType, String> result = transformCompute(
                compute, new ComputeParameters(Patch.COMPUTE, stage, textureMap));
        return result != null ? result.get(PatchShaderType.COMPUTE) : null;
    }

    public static void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
        ShaderTransformer.clearSessionState();
        ShaderLogger.LOGGER.debug("[ShaderTransformCache] cleared");
    }
}
