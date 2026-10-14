package org.soutalab.celtium.shaderpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SpirvStageCompilerTest {
    @TempDir Path temp;

    private static byte[] fixtureSpv(int executionModel) {
        // SPIR-V header + a small OpEntryPoint. Sufficient for structural tests,
        // not valid Shader Module execution; real compiler/spirv-val check is separate.
        ByteBuffer b = ByteBuffer.allocate(10 * 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x07230203).putInt(0x00010500).putInt(8).putInt(3).putInt(0);
        b.putInt((5 << 16) | 15).putInt(executionModel).putInt(1).putInt(0x6e69616d).putInt(0);
        return b.array();
    }

    @Test void inspectsValidEntryPointsAndClonesBytecode() {
        byte[] words = fixtureSpv(0);
        SpirvModule v = SpirvModule.inspect(Stage.VERTEX, words);
        assertEquals(0x00010500, v.spirvVersion());
        assertEquals(64, v.sha256().length());
        words[0] = 0;
        assertEquals(0x03, v.binary()[0]);
        byte[] copy = v.binary();
        copy[0] = 0;
        assertEquals(0x03, v.binary()[0]);
        assertEquals(Stage.FRAGMENT, SpirvModule.inspect(Stage.FRAGMENT, fixtureSpv(4)).stage());
    }

    @Test void rejectsWrongOrMissingEntryPoint() {
        assertThrows(IllegalArgumentException.class, () -> SpirvModule.inspect(Stage.FRAGMENT, fixtureSpv(0)));
        assertThrows(IllegalArgumentException.class, () -> SpirvModule.inspect(Stage.COMPUTE, fixtureSpv(5)));
        byte[] missing = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x07230203).putInt(0x00010500).putInt(8).putInt(3).putInt(0).array();
        assertThrows(IllegalArgumentException.class, () -> SpirvModule.inspect(Stage.VERTEX, missing));
    }

    @Test void rejectsCorruptedBinary() {
        byte[] magic = fixtureSpv(0);
        magic[0] = 42;
        assertThrows(IllegalArgumentException.class, () -> SpirvModule.inspect(Stage.VERTEX, magic));
        byte[] malformed = fixtureSpv(0);
        ByteBuffer.wrap(malformed).order(ByteOrder.LITTLE_ENDIAN).putInt(5 * 4, (100 << 16) | 15);
        assertThrows(IllegalArgumentException.class, () -> SpirvModule.inspect(Stage.VERTEX, malformed));
        byte[] tooNew = fixtureSpv(0);
        ByteBuffer.wrap(tooNew).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 0x00010600);
        assertThrows(IllegalArgumentException.class, () -> SpirvModule.inspect(Stage.VERTEX, tooNew));
        assertThrows(IllegalArgumentException.class,
                () -> SpirvModule.inspect(Stage.VERTEX, new byte[] {3,2,35,7,1}));
    }

    @Test void legacySourceRejectedBeforeToolInvocation() {
        Path missingCompiler = temp.resolve("does-not-exist");
        SpirvStageCompiler.CompileFailure failure = assertThrows(SpirvStageCompiler.CompileFailure.class,
                () -> SpirvStageCompiler.compile(missingCompiler, Stage.VERTEX,
                        "#version 130\nvoid main(){ gl_Position=gl_Vertex; }"));
        assertTrue(failure.getMessage().contains("legacy OptiFine"));
        assertThrows(SpirvStageCompiler.CompileFailure.class,
                () -> SpirvStageCompiler.compile(missingCompiler, Stage.VERTEX, "void main() {}"));
        assertThrows(SpirvStageCompiler.CompileFailure.class,
                () -> SpirvStageCompiler.compile(missingCompiler, Stage.COMPUTE, "#version 450\nvoid main() {}"));
        assertThrows(SpirvStageCompiler.CompileFailure.class,
                () -> SpirvStageCompiler.compile(missingCompiler, Stage.VERTEX, "#version 450\n" + "x".repeat(1024*1024)));
    }

    private Path fakeCompiler(byte[] binary, int exit) throws Exception {
        Path artifact = temp.resolve("binary.spv");
        Files.write(artifact, binary);
        Path runner = temp.resolve("tool.sh");
        String script = "#!/bin/sh\n"
                + "while [ \"$#\" -gt 0 ]; do\n"
                + " if [ \"$1\" = '-o' ]; then\n"
                + "  shift\n"
                + (exit == 0 ? "  cp '" + artifact + "' \"$1\"\n" : "  echo 'synthetic compiler failure'\n")
                + "  exit " + exit + "\n"
                + " fi\n shift\ndone\nexit 2\n";
        Files.writeString(runner, script);
        assertTrue(runner.toFile().setExecutable(true));
        return runner;
    }

    @Test void executableProcessCompilesAndValidatesExpectedEntryPoint() throws Exception {
        SpirvModule result = SpirvStageCompiler.compile(fakeCompiler(fixtureSpv(4), 0), Stage.FRAGMENT,
                "#version 450\nlayout(location=0) out vec4 color;void main(){color=vec4(1);}");
        assertEquals(Stage.FRAGMENT, result.stage());
        assertEquals(40, result.binary().length);
        assertEquals(0, Files.list(temp).filter(f -> f.getFileName().toString().startsWith("celtium-spirv")).count());
    }

    @Test void rejectsFailedCompilerAndStageMismatch() throws Exception {
        SpirvStageCompiler.CompileFailure failure = assertThrows(SpirvStageCompiler.CompileFailure.class,
                () -> SpirvStageCompiler.compile(fakeCompiler(fixtureSpv(4), 1), Stage.VERTEX,
                        "#version 450\nvoid main(){gl_Position=vec4(0);}"));
        assertTrue(failure.getMessage().contains("synthetic compiler failure"));
        assertThrows(SpirvStageCompiler.CompileFailure.class,
                () -> SpirvStageCompiler.compile(fakeCompiler(fixtureSpv(4), 0), Stage.VERTEX,
                        "#version 450\nvoid main(){gl_Position=vec4(0);}"));
    }

    @Test void actualGlslangCompilerWhenConfigured() throws Exception {
        String glslang = System.getenv().getOrDefault("CELTIUM_GLSLANG_VALIDATOR", "");
        assumeTrue(!glslang.isBlank() && Files.isExecutable(Path.of(glslang)), "compiler not installed in CI");
        SpirvModule vert = SpirvStageCompiler.compile(Path.of(glslang), Stage.VERTEX,
                "#version 450\nlayout(location=0) in vec3 xyz;\n"
                        + "void main(){gl_Position=vec4(xyz,1.0);}\n");
        SpirvModule frag = SpirvStageCompiler.compile(Path.of(glslang), Stage.FRAGMENT,
                "#version 450\nlayout(location=0) out vec4 outColor;\n"
                        + "void main(){outColor=vec4(1,0,0,1);}\n");
        assertNotEquals(vert.sha256(), frag.sha256());
        assertEquals(0x00010500, vert.spirvVersion());
        assertEquals(0x00010500, frag.spirvVersion());
    }
}
