package org.soutalab.celtium.shaderpack.cli;

import org.soutalab.celtium.shaderpack.SpirvStageCompiler;
import org.soutalab.celtium.shaderpack.SpirvModule;
import org.soutalab.celtium.shaderpack.Stage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Local development helper, NOT a pack compiler or a Vulkan render pipeline. */
public final class SpirvCompileProbe {
    private SpirvCompileProbe() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("usage: SpirvCompileProbe <glslangValidator> <VERTEX|FRAGMENT> <source.glsl> <output.spv>");
            System.exit(2);
        }
        Stage stage = Stage.valueOf(args[1].toUpperCase(java.util.Locale.ROOT));
        String source = Files.readString(Path.of(args[2]), StandardCharsets.UTF_8);
        SpirvModule module = SpirvStageCompiler.compile(Path.of(args[0]), stage, source);
        Files.write(Path.of(args[3]), module.binary());
        System.out.println("compiled=" + stage + " sha256=" + module.sha256()
                + " version=0x" + Integer.toHexString(module.spirvVersion())
                + " bytes=" + module.binary().length);
    }
}
