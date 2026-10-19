package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.shaderpack.DimensionPlan;
import org.soutalab.celtium.shaderpack.LegacyGlslLowering;
import org.soutalab.celtium.shaderpack.PackException;
import org.soutalab.celtium.shaderpack.PackManifest;
import org.soutalab.celtium.shaderpack.PackPath;
import org.soutalab.celtium.shaderpack.ProgramEntry;
import org.soutalab.celtium.shaderpack.ShaderPackLoader;
import org.soutalab.celtium.shaderpack.SpirvModule;
import org.soutalab.celtium.shaderpack.SpirvStageCompiler;
import org.soutalab.celtium.shaderpack.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The S2 chain against a real shaderpack, offline.
 * <p>
 * The in-game probe is the acceptance evidence, but it costs a game leg per iteration and prints through a log. The
 * same chain runs here on a pack that is present on this machine, so a lowering bug can be found and fixed against the
 * pack itself rather than against a hand-written approximation of it.
 * <p>
 * Assumption-gated: a machine without the pack skips the test instead of failing it.
 */
class RealPackLoweringTest {
    private static final Path[] PACKS = {
            Path.of(System.getProperty("user.home"),
                    ".local/share/PrismLauncher/instances/Fabulously Optimized(1)/minecraft/shaderpacks/"
                            + "BSL_v10.1.8.zip"),
    };

    @Test
    void lowersAndCompilesRealPackPrograms() throws Exception {
        Path pack = null;
        for (Path candidate : PACKS) {
            if (Files.isRegularFile(candidate)) {
                pack = candidate;
                break;
            }
        }
        Assumptions.assumeTrue(pack != null, "no real shaderpack on this machine");
        ShadercGlslCompiler compiler = new ShadercGlslCompiler();
        Assumptions.assumeTrue(compiler.available(), () -> "shaderc unavailable: " + compiler.unavailableReason());

        PackManifest manifest = ShaderPackLoader.load(pack);
        List<PackPath> wanted = new ArrayList<>();
        for (DimensionPlan dimension : manifest.dimensions()) {
            for (ProgramEntry program : dimension.programs()) {
                if (program.isComputeOnly()) {
                    continue;
                }
                for (PackPath path : program.stages().values()) {
                    wanted.add(path);
                }
                if (wanted.size() >= 12) {
                    break;
                }
            }
            if (wanted.size() >= 12) {
                break;
            }
        }
        Assumptions.assumeTrue(!wanted.isEmpty(), "the pack has no non-compute programs");

        List<String> failures = new ArrayList<>();
        int compiled = 0;
        for (PackPath path : wanted) {
            Stage stage = LegacyGlslLowering.stageOf(path.toString());
            try {
                String expanded = ShaderPackLoader.expand(pack, path, Map.of("MC_VERSION", "11202"));
                LegacyGlslLowering.Result lowered = LegacyGlslLowering.lower(stage, expanded, Map.of());
                SpirvModule module = compiler.compile(stage, lowered.source());
                assertTrue(ShadercGlslCompiler.wordCount(module) > 20, path + " compiled to a tiny module");
                compiled++;
            } catch (SpirvStageCompiler.CompileFailure | PackException failure) {
                failures.add(path + ": " + failure.getMessage());
                // The compiler reports a line; printing it turns "somewhere in 60 kB" into a line to read.
                java.util.regex.Matcher where = java.util.regex.Pattern.compile("celtium-stage:(\\d+):").matcher(
                        String.valueOf(failure.getMessage()));
                if (where.find()) {
                    try {
                        String expanded = ShaderPackLoader.expand(pack, path, Map.of("MC_VERSION", "11202"));
                        LegacyGlslLowering.Result lowered = LegacyGlslLowering.lower(stage, expanded, Map.of());
                        String[] text = lowered.source().split("\n", -1);
                        int at = Integer.parseInt(where.group(1)) - 1;
                        for (int i = Math.max(0, at - 1); i < Math.min(text.length, at + 2); i++) {
                            System.out.println("[real-pack]   " + (i + 1) + ": " + text[i]);
                        }
                    } catch (RuntimeException | PackException ignored) {
                        // The source could not be rebuilt for the diagnostic; the failure is already recorded.
                    }
                }
            }
        }
        System.out.println("[real-pack] compiled " + compiled + " of " + wanted.size());
        for (String failure : failures) {
            System.out.println("[real-pack] FAILED " + failure);
        }
        assertTrue(compiled > 0, "no program of the real pack compiled: " + failures);
    }
}
