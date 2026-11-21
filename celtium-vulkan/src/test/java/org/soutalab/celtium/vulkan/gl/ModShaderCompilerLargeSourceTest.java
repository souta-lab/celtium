package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that {@link ModShaderCompiler} encodes GLSL sources on the native heap ({@code MemoryUtil.memUTF8})
 * rather than LWJGL's 64 KiB {@code MemoryStack}, so shader-pack stages larger than 64 KiB
 * (such as ComplementaryReimagined's {@code gbuffers_terrain.fsh}) compile without {@code Out of stack space}.
 */
final class ModShaderCompilerLargeSourceTest {

    @Test
    void compilerEncodesSourceOffMemoryStackAndFreesIt() throws Exception {
        Path source = Path.of("src/main/java/org/soutalab/celtium/vulkan/gl/ModShaderCompiler.java");
        if (!Files.exists(source)) {
            source = Path.of("celtium-vulkan").resolve(source);
        }
        String text = Files.readString(source);
        assertTrue(text.contains("MemoryUtil.memUTF8(source, false)"),
                "ModShaderCompiler must encode shader source via MemoryUtil.memUTF8(source, false)");
        assertTrue(text.contains("MemoryUtil.memFree(sourceBytes)"),
                "ModShaderCompiler must free sourceBytes in finally");
    }

    @Test
    void compilesShaderLargerThan64KiBWhenShadercIsAvailable() {
        Assumptions.assumeTrue(ModShaderCompiler.available(), "shaderc native not available in this JVM");
        StringBuilder bigFragment = new StringBuilder(80 * 1024);
        bigFragment.append("#version 450\nlayout(location = 0) out vec4 fragColor;\n");
        // Pad past LWJGL's default 64 KiB MemoryStack with valid comments.
        for (int i = 0; i < 1200; i++) {
            bigFragment.append("// padding line to exceed 64 KiB LWJGL MemoryStack limit: ").append(i).append('\n');
        }
        bigFragment.append("void main() { fragColor = vec4(1.0); }\n");
        assertTrue(bigFragment.length() > 70_000);
        byte[] spirv = ModShaderCompiler.compile(bigFragment.toString(), false);
        assertNotNull(spirv);
        assertTrue(spirv.length > 0);
    }
}
