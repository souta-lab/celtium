/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 * Packs all built-in shader pack uniforms and stareval custom uniforms into a single std140 UBO
 * layout (PackExecutionPlan.PackUniformLayout) and writes them into an FFM MemorySegment once per
 * frame — eliminating per-draw glUniform* calls entirely (V040-FINAL-PLAN section 2.2 P0 & 2.3).
 */
package org.soutalab.celtium.shader.uniforms;

import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.soutalab.celtium.core.frame.FrameContext;
import org.soutalab.celtium.core.shader.PackExecutionPlan.PackUniformLayout;
import org.soutalab.celtium.core.shader.PackExecutionPlan.PackUniformLayout.UniformField;
import org.soutalab.celtium.core.shader.PackExecutionPlan.PackUniformLayout.UniformType;
import org.soutalab.celtium.shader.model.UniformHolder;
import org.soutalab.celtium.shader.model.UniformUpdateFrequency;
import org.soutalab.celtium.shader.uniforms.custom.CustomUniforms;
import org.soutalab.celtium.shader.uniforms.custom.cached.CachedUniform;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Std140UniformPacker {

    @FunctionalInterface
    private interface FieldWriter {
        void write(MemorySegment segment, int offset, FrameContext ctx, ShaderFrameState state);
    }

    private record BoundField(UniformField field, FieldWriter writer) {
    }

    private final PackUniformLayout layout;
    private final List<BoundField> boundFields;
    private final CustomUniforms customUniforms;
    private final float[] matScratch = new float[16];

    private Std140UniformPacker(PackUniformLayout layout, List<BoundField> boundFields, CustomUniforms customUniforms) {
        this.layout = layout;
        this.boundFields = List.copyOf(boundFields);
        this.customUniforms = customUniforms;
    }

    public PackUniformLayout layout() {
        return layout;
    }

    public CustomUniforms customUniforms() {
        return customUniforms;
    }

    /**
     * Evaluates all stareval custom uniforms and writes every built-in and custom uniform into
     * {@code packUniformUbo} in a single sequential pass.
     */
    public void writeFrame(MemorySegment packUniformUbo, FrameContext ctx, ShaderFrameState state) {
        Objects.requireNonNull(packUniformUbo, "packUniformUbo");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(state, "state");
        if (packUniformUbo.byteSize() < layout.blockSizeBytes()) {
            throw new IllegalArgumentException("packUniformUbo size " + packUniformUbo.byteSize()
                    + " is smaller than layout blockSizeBytes " + layout.blockSizeBytes());
        }
        if (customUniforms != null) {
            customUniforms.updateAll();
        }
        for (BoundField bf : boundFields) {
            bf.writer.write(packUniformUbo, bf.field.offsetBytes(), ctx, state);
        }
    }

    /**
     * Builds the complete standard Iris/OptiFine built-in uniform set plus all exported stareval
     * custom uniforms from {@code customUniformsBuilder}, packed into one std140 block.
     */
    public static Std140UniformPacker buildStandard(
            CustomUniforms.Builder customUniformsBuilder,
            ShaderFrameState liveStateRef) {
        Builder b = new Builder();
        registerBuiltins(b);

        CustomUniforms compiledCustom = null;
        if (customUniformsBuilder != null) {
            compiledCustom = customUniformsBuilder.build(holder -> registerBuiltinInputsForStareval(holder, liveStateRef));
            for (CachedUniform cu : compiledCustom.getExportedUniforms()) {
                b.addCustom(cu);
            }
        }
        return b.build(compiledCustom);
    }

    private static void registerBuiltinInputsForStareval(UniformHolder h, ShaderFrameState s) {
        UniformUpdateFrequency perFrame = UniformUpdateFrequency.PER_FRAME;
        h.uniform1f(perFrame, "viewWidth", () -> (float) s.viewWidth);
        h.uniform1f(perFrame, "viewHeight", () -> (float) s.viewHeight);
        h.uniform1f(perFrame, "aspectRatio", s::aspectRatio);
        h.uniform1i(perFrame, "worldTime", () -> (int) (s.worldTime % 24000L));
        h.uniform1i(perFrame, "worldDay", () -> s.worldDay);
        h.uniform1i(perFrame, "moonPhase", () -> s.moonPhase);
        h.uniform1f(perFrame, "sunAngle", () -> s.sunAngle);
        h.uniform1f(perFrame, "shadowAngle", () -> s.shadowAngle);
        h.uniform1f(perFrame, "frameTime", () -> s.frameTime);
        h.uniform1f(perFrame, "frameTimeCounter", () -> s.frameTimeCounter);
        h.uniform1i(perFrame, "frameCounter", () -> s.frameCounter);
        h.uniform1f(perFrame, "rainStrength", () -> s.rainStrength);
        h.uniform1f(perFrame, "wetness", () -> s.wetness);
        h.uniform1f(perFrame, "thunderStrength", () -> s.thunderStrength);
        h.uniform1i(perFrame, "isEyeInWater", () -> s.isEyeInWater);
        h.uniform1f(perFrame, "nightVision", () -> s.nightVision);
        h.uniform1f(perFrame, "blindness", () -> s.blindness);
        h.uniform1f(perFrame, "darknessFactor", () -> s.darknessFactor);
        h.uniform1f(perFrame, "screenBrightness", () -> s.screenBrightness);
        h.uniform1f(perFrame, "near", () -> s.nearPlane);
        h.uniform1f(perFrame, "far", () -> s.farPlane);
        h.uniform1f(perFrame, "centerDepthSmooth", () -> s.centerDepthSmooth);
        h.uniform1f(perFrame, "eyeAltitude", () -> (float) s.cameraPosition.y);
        h.uniform3d(perFrame, "cameraPosition", () -> new Vector3d(s.cameraPosition));
        h.uniform3d(perFrame, "previousCameraPosition", () -> new Vector3d(s.previousCameraPosition));
        h.uniform3f(perFrame, "skyColor", s::skyColor);
        h.uniform3f(perFrame, "fogColor", s::fogColor);
        h.uniform1f(perFrame, "fogDensity", () -> s.fogDensity);
        h.uniform1f(perFrame, "fogStart", () -> s.fogStart);
        h.uniform1f(perFrame, "fogEnd", () -> s.fogEnd);
        h.uniform1i(perFrame, "heldItemId", () -> s.heldItemId);
        h.uniform1i(perFrame, "heldBlockLightValue", () -> s.heldBlockLightValue);
        h.uniform1i(perFrame, "heldItemId2", () -> s.heldItemId2);
        h.uniform1i(perFrame, "heldBlockLightValue2", () -> s.heldBlockLightValue2);
        h.uniform1i(perFrame, "entityId", () -> s.entityId);
        h.uniform1i(perFrame, "blockEntityId", () -> s.blockEntityId);
        h.uniform1i(perFrame, "biome", () -> s.biomeId);
        h.uniform1i(perFrame, "biome_category", () -> s.biomeCategory);
        h.uniform1i(perFrame, "biome_precipitation", () -> s.biomePrecipitation);
        h.uniform1f(perFrame, "temperature", () -> s.biomeTemperature);
        h.uniform1f(perFrame, "rainfall", () -> s.biomeRainfall);
        h.uniform1b(perFrame, "hideGUI", () -> s.hideGUI);
        h.uniform1b(perFrame, "firstPersonCamera", () -> s.firstPersonCamera);
        h.uniform1b(perFrame, "is_sneaking", () -> s.isSneaking);
        h.uniform1b(perFrame, "is_sprinting", () -> s.isSprinting);
        h.uniform1b(perFrame, "is_on_ground", () -> s.isOnGround);
        h.uniform1b(perFrame, "is_spectator", () -> s.isSpectator);
        h.uniform1b(perFrame, "is_hurt", () -> s.isHurt);
        h.uniform1b(perFrame, "is_invisible", () -> s.isInvisible);
        h.uniform1b(perFrame, "is_burning", () -> s.isBurning);
        h.uniform1f(perFrame, "playerMood", () -> s.playerMood);
        h.uniform1f(perFrame, "currentPlayerHealth", () -> s.currentPlayerHealth);
        h.uniform1f(perFrame, "maxPlayerHealth", () -> s.maxPlayerHealth);
        h.uniform2i(perFrame, "eyeBrightness", () -> new Vector2i(s.eyeBrightnessBlock, s.eyeBrightnessSky));
        h.uniform2f(perFrame, "eyeBrightnessSmooth", () -> new Vector2f(s.eyeBrightnessSmoothBlock, s.eyeBrightnessSmoothSky));
        h.uniformMatrix(perFrame, "gbufferModelView", s::gbufferModelView);
        h.uniformMatrix(perFrame, "gbufferModelViewInverse", s::gbufferModelViewInverse);
        h.uniformMatrix(perFrame, "gbufferPreviousModelView", s::gbufferPreviousModelView);
        h.uniformMatrix(perFrame, "gbufferProjection", s::gbufferProjection);
        h.uniformMatrix(perFrame, "gbufferProjectionInverse", s::gbufferProjectionInverse);
        h.uniformMatrix(perFrame, "gbufferPreviousProjection", s::gbufferPreviousProjection);
        h.uniformMatrix(perFrame, "shadowModelView", s::shadowModelView);
        h.uniformMatrix(perFrame, "shadowModelViewInverse", s::shadowModelViewInverse);
        h.uniformMatrix(perFrame, "shadowProjection", s::shadowProjection);
        h.uniformMatrix(perFrame, "shadowProjectionInverse", s::shadowProjectionInverse);
    }

    private static void registerBuiltins(Builder b) {
        // Matrices (64 B each, 16-byte aligned)
        b.addMat4("gbufferModelView", (c, s) -> s.gbufferModelView());
        b.addMat4("gbufferModelViewInverse", (c, s) -> s.gbufferModelViewInverse());
        b.addMat4("gbufferPreviousModelView", (c, s) -> s.gbufferPreviousModelView());
        b.addMat4("gbufferProjection", (c, s) -> s.gbufferProjection());
        b.addMat4("gbufferProjectionInverse", (c, s) -> s.gbufferProjectionInverse());
        b.addMat4("gbufferPreviousProjection", (c, s) -> s.gbufferPreviousProjection());
        b.addMat4("shadowModelView", (c, s) -> s.shadowModelView());
        b.addMat4("shadowModelViewInverse", (c, s) -> s.shadowModelViewInverse());
        b.addMat4("shadowProjection", (c, s) -> s.shadowProjection());
        b.addMat4("shadowProjectionInverse", (c, s) -> s.shadowProjectionInverse());

        // Vec4 / Vec3 (16-byte aligned)
        b.addVec4("entityColor", (c, s) -> s.entityColor());
        b.addVec3("cameraPosition", (c, s) -> new org.joml.Vector3f(
                (float) s.cameraPosition.x, (float) s.cameraPosition.y, (float) s.cameraPosition.z));
        b.addVec3("previousCameraPosition", (c, s) -> new org.joml.Vector3f(
                (float) s.previousCameraPosition.x, (float) s.previousCameraPosition.y, (float) s.previousCameraPosition.z));
        b.addVec3("sunPosition", (c, s) -> s.sunPosition);
        b.addVec3("moonPosition", (c, s) -> s.moonPosition);
        b.addVec3("shadowLightPosition", (c, s) -> s.shadowLightPosition);
        b.addVec3("upPosition", (c, s) -> s.upPosition);
        b.addVec3("skyColor", (c, s) -> s.skyColor());
        b.addVec3("fogColor", (c, s) -> s.fogColor());

        // Vec2 / IVec2 (8-byte aligned)
        b.addIVec2("eyeBrightness", (c, s) -> s.eyeBrightnessBlock, (c, s) -> s.eyeBrightnessSky);
        b.addVec2("eyeBrightnessSmooth", (c, s) -> s.eyeBrightnessSmoothBlock, (c, s) -> s.eyeBrightnessSmoothSky);

        // Scalars (4-byte aligned)
        b.addFloat("viewWidth", (c, s) -> (float) s.viewWidth);
        b.addFloat("viewHeight", (c, s) -> (float) s.viewHeight);
        b.addFloat("aspectRatio", (c, s) -> s.aspectRatio());
        b.addFloat("near", (c, s) -> s.nearPlane);
        b.addFloat("far", (c, s) -> s.farPlane);
        b.addFloat("sunAngle", (c, s) -> s.sunAngle);
        b.addFloat("shadowAngle", (c, s) -> s.shadowAngle);
        b.addFloat("frameTime", (c, s) -> s.frameTime);
        b.addFloat("frameTimeCounter", (c, s) -> s.frameTimeCounter);
        b.addFloat("rainStrength", (c, s) -> s.rainStrength);
        b.addFloat("wetness", (c, s) -> s.wetness);
        b.addFloat("thunderStrength", (c, s) -> s.thunderStrength);
        b.addFloat("nightVision", (c, s) -> s.nightVision);
        b.addFloat("blindness", (c, s) -> s.blindness);
        b.addFloat("darknessFactor", (c, s) -> s.darknessFactor);
        b.addFloat("screenBrightness", (c, s) -> s.screenBrightness);
        b.addFloat("centerDepthSmooth", (c, s) -> s.centerDepthSmooth);
        b.addFloat("eyeAltitude", (c, s) -> (float) s.cameraPosition.y);
        b.addFloat("fogDensity", (c, s) -> s.fogDensity);
        b.addFloat("fogStart", (c, s) -> s.fogStart);
        b.addFloat("fogEnd", (c, s) -> s.fogEnd);
        b.addFloat("temperature", (c, s) -> s.biomeTemperature);
        b.addFloat("rainfall", (c, s) -> s.biomeRainfall);
        b.addFloat("playerMood", (c, s) -> s.playerMood);
        b.addFloat("currentPlayerHealth", (c, s) -> s.currentPlayerHealth);
        b.addFloat("maxPlayerHealth", (c, s) -> s.maxPlayerHealth);

        b.addInt("worldTime", (c, s) -> (int) (s.worldTime % 24000L));
        b.addInt("worldDay", (c, s) -> s.worldDay);
        b.addInt("moonPhase", (c, s) -> s.moonPhase);
        b.addInt("frameCounter", (c, s) -> (int) (c.frameId() % 720720L));
        b.addInt("isEyeInWater", (c, s) -> s.isEyeInWater);
        b.addInt("heldItemId", (c, s) -> s.heldItemId);
        b.addInt("heldBlockLightValue", (c, s) -> s.heldBlockLightValue);
        b.addInt("heldItemId2", (c, s) -> s.heldItemId2);
        b.addInt("heldBlockLightValue2", (c, s) -> s.heldBlockLightValue2);
        b.addInt("entityId", (c, s) -> s.entityId);
        b.addInt("blockEntityId", (c, s) -> s.blockEntityId);
        b.addInt("currentRenderedItemId", (c, s) -> s.currentRenderedItemId);
        b.addInt("biome", (c, s) -> s.biomeId);
        b.addInt("biome_category", (c, s) -> s.biomeCategory);
        b.addInt("biome_precipitation", (c, s) -> s.biomePrecipitation);
        b.addInt("fogShape", (c, s) -> s.fogShape);
        b.addInt("hideGUI", (c, s) -> s.hideGUI ? 1 : 0);
        b.addInt("firstPersonCamera", (c, s) -> s.firstPersonCamera ? 1 : 0);
    }

    public static int std140Alignment(UniformType type) {
        return switch (type) {
            case FLOAT, INT, UINT -> 4;
            case VEC2, IVEC2 -> 8;
            case VEC3, VEC4, IVEC3, IVEC4, MAT3, MAT4 -> 16;
        };
    }

    public static int std140Size(UniformType type) {
        return switch (type) {
            case FLOAT, INT, UINT -> 4;
            case VEC2, IVEC2 -> 8;
            case VEC3, IVEC3 -> 12;
            case VEC4, IVEC4 -> 16;
            case MAT3 -> 48;
            case MAT4 -> 64;
        };
    }

    private static int alignUp(int value, int alignment) {
        int mask = alignment - 1;
        return (value + mask) & ~mask;
    }

    @FunctionalInterface
    private interface FrameFloatFn { float apply(FrameContext c, ShaderFrameState s); }
    @FunctionalInterface
    private interface FrameIntFn { int apply(FrameContext c, ShaderFrameState s); }
    @FunctionalInterface
    private interface FrameVec3Fn { Vector3fc apply(FrameContext c, ShaderFrameState s); }
    @FunctionalInterface
    private interface FrameVec4Fn { Vector4fc apply(FrameContext c, ShaderFrameState s); }
    @FunctionalInterface
    private interface FrameMat4Fn { Matrix4fc apply(FrameContext c, ShaderFrameState s); }

    public static final class Builder {
        private final Map<String, BoundField> fields = new LinkedHashMap<>();
        private int cursor = 0;

        private void register(String name, UniformType type, FieldWriter writer) {
            if (fields.containsKey(name)) {
                return;
            }
            int align = std140Alignment(type);
            int size = std140Size(type);
            int offset = alignUp(cursor, align);
            UniformField uf = new UniformField(name, type, offset, size);
            fields.put(name, new BoundField(uf, writer));
            cursor = offset + size;
        }

        public Builder addFloat(String name, FrameFloatFn fn) {
            register(name, UniformType.FLOAT, (seg, off, c, s) ->
                    seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off, fn.apply(c, s)));
            return this;
        }

        public Builder addInt(String name, FrameIntFn fn) {
            register(name, UniformType.INT, (seg, off, c, s) ->
                    seg.set(ValueLayout.JAVA_INT_UNALIGNED, off, fn.apply(c, s)));
            return this;
        }

        public Builder addVec2(String name, FrameFloatFn xFn, FrameFloatFn yFn) {
            register(name, UniformType.VEC2, (seg, off, c, s) -> {
                seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off, xFn.apply(c, s));
                seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off + 4, yFn.apply(c, s));
            });
            return this;
        }

        public Builder addIVec2(String name, FrameIntFn xFn, FrameIntFn yFn) {
            register(name, UniformType.IVEC2, (seg, off, c, s) -> {
                seg.set(ValueLayout.JAVA_INT_UNALIGNED, off, xFn.apply(c, s));
                seg.set(ValueLayout.JAVA_INT_UNALIGNED, off + 4, yFn.apply(c, s));
            });
            return this;
        }

        public Builder addVec3(String name, FrameVec3Fn fn) {
            register(name, UniformType.VEC3, (seg, off, c, s) -> {
                Vector3fc v = fn.apply(c, s);
                seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off, v.x());
                seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off + 4, v.y());
                seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off + 8, v.z());
            });
            return this;
        }

        public Builder addVec4(String name, FrameVec4Fn fn) {
            register(name, UniformType.VEC4, (seg, off, c, s) -> {
                Vector4fc v = fn.apply(c, s);
                seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off, v.x());
                seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off + 4, v.y());
                seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off + 8, v.z());
                seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off + 12, v.w());
            });
            return this;
        }

        public Builder addMat4(String name, FrameMat4Fn fn) {
            register(name, UniformType.MAT4, (seg, off, c, s) -> {
                float[] tmp = new float[16];
                fn.apply(c, s).get(tmp);
                for (int i = 0; i < 16; i++) {
                    seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off + i * 4, tmp[i]);
                }
            });
            return this;
        }

        public Builder addCustom(CachedUniform uniform) {
            register(uniform.getName(), uniform.coreUniformType(), (seg, off, c, s) ->
                    uniform.writeStd140(seg, off));
            return this;
        }

        public Std140UniformPacker build(CustomUniforms customUniforms) {
            int totalBytes = alignUp(cursor, 16);
            List<UniformField> uniformFields = new ArrayList<>(fields.size());
            List<BoundField> boundList = new ArrayList<>(fields.size());
            for (BoundField bf : fields.values()) {
                uniformFields.add(bf.field);
                boundList.add(bf);
            }
            return new Std140UniformPacker(new PackUniformLayout(uniformFields, totalBytes), boundList, customUniforms);
        }
    }
}
