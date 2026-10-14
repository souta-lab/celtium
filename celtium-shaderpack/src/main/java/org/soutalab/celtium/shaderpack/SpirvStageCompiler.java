package org.soutalab.celtium.shaderpack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Isolated S2 compiler probe for a SINGLE already-lowered Vulkan GLSL 450+ stage.
 * Uses an explicit glslangValidator executable via argument-array ProcessBuilder;
 * never uses a shell, never trusts shader source as arguments or file paths.
 *
 * This is NOT an OptiFine GLSL 120/130 translator. Legacy attributes, fixed-function
 * builtins, sampler bindings, descriptor layouts and pass state require separate lowering.
 * The subprocess has a deadline and its output/diagnostics have strict size limits.
 */
public final class SpirvStageCompiler {
    private static final int MAX_SOURCE_BYTES = 1024 * 1024;
    private static final int MAX_LOG_BYTES = 64 * 1024;
    private static final Duration DEADLINE = Duration.ofSeconds(15);
    private static final Pattern VERSION = Pattern.compile("(?m)^\\s*#version\\s+(\\d+)");

    private SpirvStageCompiler() {}

    public static SpirvModule compile(Path glslangValidator, Stage stage, String source)
            throws IOException, InterruptedException, CompileFailure {
        if (stage != Stage.VERTEX && stage != Stage.FRAGMENT) {
            throw new CompileFailure("unsupported S2 stage: " + stage);
        }
        byte[] code = source.getBytes(StandardCharsets.UTF_8);
        if (code.length == 0 || code.length > MAX_SOURCE_BYTES || source.indexOf('\u0000') >= 0) {
            throw new CompileFailure("GLSL source empty, invalid or above 1 MiB cap");
        }
        Matcher version = VERSION.matcher(source);
        if (!version.find()) {
            throw new CompileFailure("missing GLSL #version; S2 requires explicit Vulkan GLSL 450+");
        }
        if (Integer.parseInt(version.group(1)) < 450) {
            throw new CompileFailure("GLSL " + version.group(1)
                    + " is legacy OptiFine syntax; needs explicit S2 lowering before Vulkan compilation");
        }
        if (!Files.isRegularFile(glslangValidator) || !Files.isExecutable(glslangValidator)) {
            throw new CompileFailure("glslangValidator not executable: " + glslangValidator);
        }

        Path tmp = Files.createTempDirectory("celtium-spirv-stage-");
        try {
            Path input = tmp.resolve(stage == Stage.VERTEX ? "stage.vert" : "stage.frag");
            Path binary = tmp.resolve("out.spv");
            Path log = tmp.resolve("compile.log");
            Files.write(input, code);
            Process proc = new ProcessBuilder(List.of(
                    glslangValidator.toAbsolutePath().toString(),
                    "-V", "--target-env", "vulkan1.2", "--spirv-val",
                    "-S", stage == Stage.VERTEX ? "vert" : "frag",
                    "-e", "main", "-o", binary.toString(), input.toString()))
                    .directory(tmp.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            boolean finished;
            try {
                finished = proc.waitFor(DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                proc.destroyForcibly();
                proc.waitFor();
                Thread.currentThread().interrupt();
                throw e;
            }
            if (!finished) {
                proc.destroyForcibly();
                proc.waitFor();
                throw new CompileFailure("glslang timed out after " + DEADLINE.toSeconds() + " seconds");
            }
            if (proc.exitValue() != 0) {
                byte[] details;
                try (var in = Files.newInputStream(log)) {
                    details = in.readNBytes(MAX_LOG_BYTES);
                }
                String excerpt = new String(details, StandardCharsets.UTF_8);
                throw new CompileFailure("glslang rejected " + stage + " stage (exit="
                        + proc.exitValue() + "): " + excerpt);
            }
            if (!Files.isRegularFile(binary) || Files.size(binary) > 8 * 1024 * 1024) {
                throw new CompileFailure("compiler produced missing or oversized SPIR-V");
            }
            try {
                return SpirvModule.inspect(stage, Files.readAllBytes(binary));
            } catch (IllegalArgumentException invalid) {
                throw new CompileFailure("invalid compiler output: " + invalid.getMessage());
            }
        } finally {
            // No compiler output or pack input is retained in the runtime directory.
            try (var walk = Files.walk(tmp)) {
                for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    public static final class CompileFailure extends Exception {
        public CompileFailure(String message) { super(message); }
    }
}
