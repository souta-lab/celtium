package org.soutalab.celtium.shader;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.frame.FrameContext;
import org.soutalab.celtium.core.shader.PackExecutionPlan.PackUniformLayout;
import org.soutalab.celtium.shader.uniforms.ShaderFrameState;
import org.soutalab.celtium.shader.uniforms.Std140UniformPacker;
import org.soutalab.celtium.shader.uniforms.custom.CustomUniforms;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.*;

class StarevalAndCustomUniformsTest {

    @Test
    void evaluatesDependentCustomUniformsInTopologicalOrder() {
        ShaderFrameState state = new ShaderFrameState();
        state.worldTime = 6000L;
        state.rainStrength = 0.25f;

        CustomUniforms.Builder builder = new CustomUniforms.Builder();
        builder.addVariable("float", "halfRain", "rainStrength * 0.5", false);
        builder.addVariable("float", "wetFactor", "halfRain + 0.5", true);
        builder.addVariable("vec2", "screenScaled", "vec2(viewWidth * 0.5, viewHeight * 0.5)", true);

        Std140UniformPacker packer = Std140UniformPacker.buildStandard(builder, state);
        PackUniformLayout layout = packer.layout();

        assertEquals(0, layout.blockSizeBytes() % 16, "std140 block size must be 16-byte aligned");
        PackUniformLayout.UniformField wetField = layout.fieldNamed("wetFactor").orElseThrow();
        PackUniformLayout.UniformField screenField = layout.fieldNamed("screenScaled").orElseThrow();
        assertTrue(layout.fieldNamed("halfRain").isEmpty(), "non-uniform variable must not be exported to UBO");

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ubo = arena.allocate(layout.blockSizeBytes());
            state.viewWidth = 1920;
            state.viewHeight = 1080;
            packer.writeFrame(ubo, new FrameContext(42L, 100, 0.5f), state);

            float wet = ubo.get(ValueLayout.JAVA_FLOAT_UNALIGNED, wetField.offsetBytes());
            assertEquals(0.625f, wet, 1e-5f);

            float sx = ubo.get(ValueLayout.JAVA_FLOAT_UNALIGNED, screenField.offsetBytes());
            float sy = ubo.get(ValueLayout.JAVA_FLOAT_UNALIGNED, screenField.offsetBytes() + 4);
            assertEquals(960.0f, sx, 1e-4f);
            assertEquals(540.0f, sy, 1e-4f);
        }
    }

    @Test
    void writesBuiltinMatricesAndScalarsAtAlignedStd140Offsets() {
        ShaderFrameState state = new ShaderFrameState();
        state.viewWidth = 1280;
        state.viewHeight = 720;
        state.gbufferModelView.set(new Matrix4f().translation(3.0f, 4.0f, 5.0f));
        state.cameraPosition.set(10.5, 64.25, -22.75);

        Std140UniformPacker packer = Std140UniformPacker.buildStandard(null, state);
        PackUniformLayout layout = packer.layout();

        PackUniformLayout.UniformField mvField = layout.fieldNamed("gbufferModelView").orElseThrow();
        PackUniformLayout.UniformField camField = layout.fieldNamed("cameraPosition").orElseThrow();
        PackUniformLayout.UniformField fcField = layout.fieldNamed("frameCounter").orElseThrow();

        assertEquals(0, mvField.offsetBytes() % 16);
        assertEquals(0, camField.offsetBytes() % 16);
        assertEquals(0, fcField.offsetBytes() % 4);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ubo = arena.allocate(layout.blockSizeBytes());
            packer.writeFrame(ubo, new FrameContext(77L, 200, 0.25f), state);

            // Column 3 of translation matrix (indices 12, 13, 14)
            assertEquals(3.0f, ubo.get(ValueLayout.JAVA_FLOAT_UNALIGNED, mvField.offsetBytes() + 12 * 4), 1e-5f);
            assertEquals(4.0f, ubo.get(ValueLayout.JAVA_FLOAT_UNALIGNED, mvField.offsetBytes() + 13 * 4), 1e-5f);
            assertEquals(5.0f, ubo.get(ValueLayout.JAVA_FLOAT_UNALIGNED, mvField.offsetBytes() + 14 * 4), 1e-5f);

            assertEquals(10.5f, ubo.get(ValueLayout.JAVA_FLOAT_UNALIGNED, camField.offsetBytes()), 1e-5f);
            assertEquals(64.25f, ubo.get(ValueLayout.JAVA_FLOAT_UNALIGNED, camField.offsetBytes() + 4), 1e-5f);
            assertEquals(-22.75f, ubo.get(ValueLayout.JAVA_FLOAT_UNALIGNED, camField.offsetBytes() + 8), 1e-5f);

            assertEquals(77, ubo.get(ValueLayout.JAVA_INT_UNALIGNED, fcField.offsetBytes()));
        }
    }
}
