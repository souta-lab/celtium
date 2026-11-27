package org.soutalab.celtium.shader;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.shader.SpirvModuleBundle;
import org.soutalab.celtium.shader.compiler.PackSpirvCompiler;
import org.soutalab.celtium.shader.model.InputAvailability;
import org.soutalab.celtium.shader.transform.PatchShaderType;
import org.soutalab.celtium.shader.transform.ShaderInstancing;
import org.soutalab.celtium.shader.transform.TransformPatcher;
import org.soutalab.celtium.vulkan.pack.SpirvReflection;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The weather feed contract (Neon ruling, 2026-11-27; plan section 8.3 item 7, option (a)):
 * the WEATHER instancing variant of the attributes patch declares the corner at location 2
 * and the five per-instance attributes at locations 5..9, emits the reconstruction prologue,
 * lowers and compiles through the real shaderc path, and reflection recovers the six input
 * locations plus the four weather uniforms from the compiled module.
 */
class WeatherTransformContractTest {

    private static final String WEATHER_VSH = """
            #version 120
            varying vec2 texcoord;
            varying vec4 color;
            void main() {
                texcoord = gl_MultiTexCoord0.xy;
                color = gl_Color;
                gl_Position = ftransform();
            }
            """;

    private static final String WEATHER_FSH = """
            #version 120
            uniform sampler2D texture;
            varying vec2 texcoord;
            varying vec4 color;
            void main() {
                gl_FragColor = texture2D(texture, texcoord) * color;
            }
            """;

    @Test
    void weatherVariantDeclaresTheContractAndCompiles() {
        Map<PatchShaderType, String> transformed = TransformPatcher.patchAttributesInstanced(
                WEATHER_VSH, null, WEATHER_FSH, InputAvailability.ALL, ShaderInstancing.WEATHER);
        assertNotNull(transformed);
        String vsh = transformed.get(PatchShaderType.VERTEX);
        String fsh = transformed.get(PatchShaderType.FRAGMENT);
        assertNotNull(vsh);
        assertNotNull(fsh);

        // (a) The six vertex inputs and the four per-frame uniforms are declared.
        assertTrue(vsh.contains("layout(location = 2) in vec2 iris_WeatherCorner"), vsh);
        assertTrue(vsh.contains("layout(location = 5) in vec4 iris_InstColumnSpan"), vsh);
        assertTrue(vsh.contains("layout(location = 6) in vec4 iris_InstJitter"), vsh);
        assertTrue(vsh.contains("layout(location = 7) in vec4 iris_InstWeatherColor"), vsh);
        assertTrue(vsh.contains("layout(location = 8) in vec4 iris_InstWeatherParams"), vsh);
        assertTrue(vsh.contains("layout(location = 9) in vec4 iris_InstWeatherLayer"), vsh);
        for (int i = 0; i < 4; i++) {
            assertTrue(vsh.contains("uniform vec4 iris_WeatherParams" + i + ";"), vsh);
        }
        // The reconstruction prologue runs at the head of main.
        assertTrue(vsh.contains("iris_wColumn"), vsh);
        assertTrue(vsh.contains("iris_InstWeatherParams"), vsh);

        // (b)+(c) Lowering and the real shaderc compile succeed with valid SPIR-V out.
        PackSpirvCompiler.CompiledProgram compiled = PackSpirvCompiler.SHADERC.compileGraphics(
                "gbuffers_weather", vsh, fsh, null);
        assertNotNull(compiled);
        assertEquals(SpirvModuleBundle.SPIRV_MAGIC, compiled.spirv().vertexWords()[0]);
        assertEquals(SpirvModuleBundle.SPIRV_MAGIC, compiled.spirv().fragmentWords()[0]);

        // (d) Reflection recovers the contract's input locations and the weather uniforms.
        SpirvReflection.ModuleInfo vertex = SpirvReflection.reflect(compiled.spirv().vertexWords());
        Set<Integer> locations = vertex.inputs().stream()
                .map(SpirvReflection.InterfaceVariable::location)
                .collect(Collectors.toSet());
        assertTrue(locations.containsAll(Set.of(2, 5, 6, 7, 8, 9)),
                "reflected input locations: " + locations);

        Set<String> memberNames = vertex.descriptors().stream()
                .filter(d -> d.kind() == SpirvReflection.DescriptorKind.UNIFORM_BUFFER)
                .flatMap(d -> vertex.structMemberNames()
                        .getOrDefault(d.pointeeTypeId(), Map.of()).values().stream())
                .collect(Collectors.toSet());
        Set<String> variableNames = vertex.descriptors().stream()
                .map(SpirvReflection.DescriptorVariable::name)
                .collect(Collectors.toSet());
        for (int i = 0; i < 4; i++) {
            String name = "iris_WeatherParams" + i;
            assertTrue(memberNames.contains(name) || variableNames.contains(name),
                    "weather uniform " + name + " not recovered; members=" + memberNames
                            + " variables=" + variableNames);
        }
    }

    @Test
    void defaultWeatherProgramStaysOnTheNonInstancedVariant() {
        // The pack compiler's registered gbuffers_weather keeps ShaderInstancing.NONE (the
        // vanilla rain/snow path through Lane 2); the plain patch must not declare the
        // instanced attributes.
        Map<PatchShaderType, String> transformed = TransformPatcher.patchAttributes(
                WEATHER_VSH, null, WEATHER_FSH, InputAvailability.ALL);
        assertNotNull(transformed);
        String vsh = transformed.get(PatchShaderType.VERTEX);
        assertNotNull(vsh);
        assertTrue(!vsh.contains("iris_InstColumnSpan"), vsh);
    }
}
