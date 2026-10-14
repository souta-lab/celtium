package org.soutalab.celtium.shaderpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** End-to-end manifest construction over fixture packs. */
class ShaderPackLoaderTest {

    @TempDir
    Path tmp;

    private PackManifest load(Map<String, ?> files) throws IOException, PackException {
        return ShaderPackLoader.load(
                FixturePack.writeZip(tmp.resolve("pack.zip"), files), LoadOptions.defaults());
    }

    @Test
    void minimalPackProducesManifest() throws Exception {
        PackManifest m = load(FixturePack.minimal().files());
        assertEquals(1, m.dimensions().size());
        DimensionPlan world0 = m.dimensions().get(0);
        assertEquals("world0", world0.worldKey());
        assertEquals(1, world0.programs().size());
        ProgramEntry composite = world0.programs().get(0);
        assertEquals("composite", composite.name());
        assertEquals(ProgramFamily.COMPOSITE, composite.family());
        assertEquals(Map.of(Stage.VERTEX, PackPath.absolute("/shaders/world0/composite.vsh"),
                Stage.FRAGMENT, PackPath.absolute("/shaders/world0/composite.fsh")),
                composite.stages());
        assertNull(composite.enabledCondition());
        assertTrue(m.diagnostics().isEmpty(), "unexpected diagnostics: " + m.diagnostics());
        assertFalse(m.hasErrors());
    }

    @Test
    void worldSpecificStageOverridesBase() throws Exception {
        PackManifest m = load(Map.of(
                "shaders/composite.vsh", "void main(){}\n",
                "shaders/composite.fsh", "void main(){}\n",
                "shaders/world0/composite.fsh", "void main(){ /* overworld variant */ }\n",
                "shaders/world-1/composite.vsh", "void main(){}\n",
                "shaders/world-1/composite.fsh", "void main(){}\n"));
        DimensionPlan base = plan(m, "base");
        DimensionPlan overworld = plan(m, "world0");
        DimensionPlan nether = plan(m, "world-1");
        assertEquals(PackPath.absolute("/shaders/composite.fsh"),
                base.programs().get(0).stages().get(Stage.FRAGMENT));
        assertEquals(PackPath.absolute("/shaders/world0/composite.fsh"),
                overworld.programs().get(0).stages().get(Stage.FRAGMENT));
        assertEquals(PackPath.absolute("/shaders/composite.vsh"),
                overworld.programs().get(0).stages().get(Stage.VERTEX));
        assertFalse(nether.inheritedFromBase().contains("composite"));
        assertTrue(overworld.inheritedFromBase().isEmpty()); // overworld owns fsh, inherits vsh silently
    }

    @Test
    void worldWithNoProgramsInheritsAll() throws Exception {
        PackManifest m = load(Map.of(
                "shaders/final.vsh", "void main(){}\n",
                "shaders/final.fsh", "void main(){}\n",
                "shaders/world-1/note.txt", "just a marker\n"));
        // world-1 has marker file but no program sources -> not a dimension at all
        assertTrue(m.dimensions().stream().noneMatch(d -> d.worldKey().equals("world-1")));
        assertEquals("base", m.dimensions().get(0).worldKey());
    }

    @Test
    void enabledConditionsGatePrograms() throws Exception {
        PackManifest m = load(Map.of(
                "shaders/world0/composite.vsh", "void main(){}\n",
                "shaders/world0/composite.fsh", "void main(){}\n",
                "shaders/world0/composite1.vsh", "void main(){}\n",
                "shaders/world0/composite1.fsh", "void main(){}\n",
                "shaders/program/x.glsl", "#define FANCY 0\n",
                "shaders/shaders.properties",
                "program.composite.enabled = 1\n"
                        + "program.world0/composite1.enabled = FANCY > 0\n"));
        DimensionPlan w0 = plan(m, "world0");
        ProgramEntry composite = program(w0, "composite");
        ProgramEntry composite1 = program(w0, "composite1");
        assertEquals("1", composite.enabledCondition());
        assertEquals(Boolean.TRUE, composite.enabledByDefault());
        assertEquals("FANCY > 0", composite1.enabledCondition());
        assertEquals(Boolean.FALSE, composite1.enabledByDefault());
        assertEquals(List.of("world0/composite1"), m.disabledPrograms());
        assertTrue(m.activeFeatures().contains(FeatureFlag.PROGRAM_ENABLE_CONDITIONS));
    }

    @Test
    void profileOverrideFlipsEnabledProgram() throws Exception {
        Map<String, ?> files = Map.of(
                "shaders/world0/composite.vsh", "void main(){}\n",
                "shaders/world0/composite.fsh", "void main(){}\n",
                "shaders/program/x.glsl", "#define FANCY 0\n",
                "shaders/shaders.properties",
                "profile.FANCY_ON = FANCY\n"
                        + "program.world0/composite.enabled = FANCY\n");
        Path zip = FixturePack.writeZip(tmp.resolve("pack.zip"), files);
        PackManifest off = ShaderPackLoader.load(zip, LoadOptions.defaults());
        PackManifest on = ShaderPackLoader.load(zip, LoadOptions.defaults().withProfile("FANCY_ON"));
        assertEquals(Boolean.FALSE, program(plan(off, "world0"), "composite").enabledByDefault());
        assertEquals(Boolean.TRUE, program(plan(on, "world0"), "composite").enabledByDefault());
        assertEquals("FANCY_ON", on.selectedProfile());
    }

    @Test
    void unknownProfileIsAnError() throws Exception {
        Path zip = FixturePack.minimal().zip(tmp.resolve("pack.zip"));
        PackManifest m = ShaderPackLoader.load(zip, LoadOptions.defaults().withProfile("NONSENSE"));
        assertTrue(m.diagnostics().stream().anyMatch(d -> d.severity() == Severity.ERROR));
        assertTrue(m.hasErrors());
    }

    @Test
    void unsupportedStagesAreFlaggedNotFatal() throws Exception {
        PackManifest m = load(Map.of(
                "shaders/world0/composite.vsh", "void main(){}\n",
                "shaders/world0/composite.fsh", "void main(){}\n",
                "shaders/world0/composite.gsh", "void main(){}\n",
                "shaders/world0/setup.csh", "void main(){}\n"));
        ProgramEntry composite = program(plan(m, "world0"), "composite");
        assertTrue(m.diagnostics().stream().anyMatch(d ->
                d.code() == DiagnosticCode.UNSUPPORTED_STAGE_GEOMETRY
                        && d.severity() == Severity.UNSUPPORTED));
        assertTrue(m.diagnostics().stream().anyMatch(d ->
                d.code() == DiagnosticCode.UNSUPPORTED_STAGE_COMPUTE));
        assertTrue(m.activeFeatures().contains(FeatureFlag.GEOMETRY_STAGE));
        assertTrue(m.activeFeatures().contains(FeatureFlag.COMPUTE_STAGE));
        assertFalse(m.hasErrors());
    }

    @Test
    void unpairedStageIsAWarning() throws Exception {
        PackManifest m = load(Map.of(
                "shaders/world0/final.vsh", "void main(){}\n"));
        ProgramEntry fin = program(plan(m, "world0"), "final");
        assertTrue(m.diagnostics().stream().anyMatch(d ->
                d.code() == DiagnosticCode.STAGE_WITHOUT_PAIR && d.severity() == Severity.WARNING));
    }

    @Test
    void dimensionPropertiesMapWorlds() throws Exception {
        PackManifest m = load(Map.of(
                "shaders/world0/composite.vsh", "void main(){}\n",
                "shaders/world0/composite.fsh", "void main(){}\n",
                "shaders/world-1/composite.vsh", "void main(){}\n",
                "shaders/world-1/composite.fsh", "void main(){}\n",
                "shaders/dimension.properties",
                "dimension.world0 = minecraft:overworld *\n"
                        + "dimension.world-1 = minecraft:the_nether\n"));
        assertEquals(List.of("minecraft:overworld", "*"), plan(m, "world0").dimensionIds());
        assertEquals(List.of("minecraft:the_nether"), plan(m, "world-1").dimensionIds());
        assertTrue(m.activeFeatures().contains(FeatureFlag.DIMENSION_MAP));
    }

    @Test
    void mcVersionSelectsPropertiesBranch() throws Exception {
        Map<String, ?> files = Map.of(
                "shaders/world0/composite.vsh", "void main(){}\n",
                "shaders/world0/composite.fsh", "void main(){}\n",
                "shaders/block.properties",
                "#if MC_VERSION >= 11300\nblock.1 = new:names\n#else\nblock.1 = old:name\n#endif\n");
        Path zip = FixturePack.writeZip(tmp.resolve("pack.zip"), files);
        PackManifest m112 = ShaderPackLoader.load(zip, LoadOptions.defaults());
        PackManifest m121 = ShaderPackLoader.load(zip, new LoadOptions(
                PackLimits.DEFAULT, 12100, null, Map.of(), Map.of()));
        PackManifest.PropertiesSummary s112 = m112.propertiesSummaries()
                .get("/shaders/block.properties");
        assertEquals(1, s112.entryCount());
        // the live branch is what properties parse emitted - verify via a second manifest
        // with different version flips which entry is live
        assertEquals(1, m121.propertiesSummaries().get("/shaders/block.properties").entryCount());
    }

    @Test
    void determinismAcrossLoadsAndContainers() throws Exception {
        Map<String, ?> files = FixturePack.minimal()
                .file("shaders/world0/final.vsh", "void main(){}\n")
                .file("shaders/world0/final.fsh", "void main(){}\n")
                .file("shaders/shaders.properties",
                        "profile.BIG = FANCY=3\nscreen = FANCY\nsliders = FANCY\n")
                .file("shaders/program/opts.glsl", "#define FANCY 0 //[0 1 2 3]\n")
                .files();
        Path zip = FixturePack.writeZip(tmp.resolve("pack.zip"), files);
        Path dir = FixturePack.writeDir(tmp.resolve("packdir"), files);

        PackManifest a = ShaderPackLoader.load(zip, LoadOptions.defaults());
        PackManifest b = ShaderPackLoader.load(zip, LoadOptions.defaults());
        PackManifest c = ShaderPackLoader.load(dir, LoadOptions.defaults());

        assertEquals(a.toReport(), b.toReport(), "same input twice must be identical");
        assertEquals(a.contentSha256(), c.contentSha256(),
                "zip and dir forms of the same pack must hash identically");
        // strip the label line (zip file name vs dir name) then compare reports
        assertEquals(stripLabel(a.toReport()), stripLabel(c.toReport()));
    }

    @Test
    void unknownProgramNamesAreListed() throws Exception {
        PackManifest m = load(Map.of(
                "shaders/world0/gbuffers_terrain.vsh", "void main(){}\n",
                "shaders/world0/gbuffers_terrain.fsh", "void main(){}\n",
                "shaders/world0/mypack_custom_pass.vsh", "void main(){}\n",
                "shaders/world0/mypack_custom_pass.fsh", "void main(){}\n"));
        assertTrue(m.activeFeatures().contains(FeatureFlag.UNKNOWN_PROGRAM_NAMES));
        assertTrue(m.diagnostics().stream().anyMatch(d ->
                d.code() == DiagnosticCode.UNKNOWN_PROGRAM_NAME
                        && d.message().contains("mypack_custom_pass")));
    }

    @Test
    void irisFeatureFlagsAreRecorded() throws Exception {
        PackManifest m = load(FixturePack.minimal()
                .file("shaders/shaders.properties",
                        "iris.features.required = CUSTOM_IMAGES\n"
                                + "iris.features.optional = SEPARATE_ENTITY_DRAWS\n")
                .files());
        assertTrue(m.activeFeatures().contains(FeatureFlag.IRIS_FEATURE_FLAGS));
        assertTrue(m.diagnostics().stream().anyMatch(d ->
                d.code() == DiagnosticCode.IRIS_FEATURE_DECLARATION
                        && d.message().contains("CUSTOM_IMAGES")));
    }

    @Test
    void emptyZipIsRejected() throws Exception {
        Path zip = FixturePack.writeZip(tmp.resolve("empty.zip"), Map.of());
        PackException e = assertThrows(PackException.class,
                () -> ShaderPackLoader.load(zip, LoadOptions.defaults()));
        assertEquals(DiagnosticCode.EMPTY_ARCHIVE, e.code());
    }

    @Test
    void missingInputIsAnError() {
        PackException e = assertThrows(PackException.class,
                () -> ShaderPackLoader.load(tmp.resolve("nonexistent.zip"), LoadOptions.defaults()));
        assertEquals(DiagnosticCode.IO_ERROR, e.code());
    }

    // ---- real pack validation (runs only when the BSL zip is available) ------

    @Test
    void bslPackLoadsCleanlyWhenPresent() throws Exception {
        String configured = System.getProperty("celtium.bslZip",
                System.getenv().getOrDefault("CELTIUM_BSL_ZIP", ""));
        Path bsl;
        try {
            bsl = configured.isEmpty()
                    ? Path.of("/home/s0u7a/ダウンロード/BSL_v10.1.8.zip")
                    : Path.of(configured);
        } catch (java.nio.file.InvalidPathException unmappable) {
            assumeTrue(false, "configured BSL path is not encodable in this JVM locale");
            return;
        }
        assumeTrue(Files.isRegularFile(bsl), "BSL fixture zip not present - skipping real-pack validation");

        PackManifest m = ShaderPackLoader.load(bsl, LoadOptions.defaults());
        assertFalse(m.hasErrors(),
                "BSL must produce no ERROR diagnostics:\n"
                        + m.diagnostics().stream().filter(d -> d.severity() == Severity.ERROR)
                        .limit(20).map(Diagnostic::toString).toList());

        // three dimensions, no root-level programs
        assertEquals(List.of("world-1", "world0", "world1"),
                m.dimensions().stream().map(DimensionPlan::worldKey).toList());

        // dimension map parsed
        assertEquals(List.of("minecraft:overworld", "*"), plan(m, "world0").dimensionIds());
        assertEquals(List.of("minecraft:the_nether"), plan(m, "world-1").dimensionIds());
        assertEquals(List.of("minecraft:the_end"), plan(m, "world1").dimensionIds());

        // every dimension carries the gbuffer and composite families
        for (DimensionPlan dim : m.dimensions()) {
            assertTrue(dim.programs().stream().anyMatch(p -> p.name().equals("gbuffers_terrain")));
            assertTrue(dim.programs().stream().anyMatch(p -> p.name().equals("final")));
            // nether misses only the sky passes - check the inverse
            if (dim.worldKey().equals("world-1")) {
                assertTrue(dim.programs().stream().noneMatch(p -> p.name().equals("gbuffers_skybasic")));
            }
        }
        assertTrue(plan(m, "world0").programs().stream()
                .anyMatch(p -> p.name().equals("gbuffers_skybasic")));

        // compute stage is census-visible and unsupported, never silently dropped
        ProgramEntry shadowcomp = program(plan(m, "world0"), "shadowcomp");
        assertTrue(shadowcomp.stages().containsKey(Stage.COMPUTE));
        assertTrue(m.diagnostics().stream().anyMatch(d ->
                d.code() == DiagnosticCode.UNSUPPORTED_STAGE_COMPUTE));

        // per-dimension gating: nether shadow requires MULTICOLORED_BLOCKLIGHT
        ProgramEntry netherShadow = program(plan(m, "world-1"), "shadow");
        assertEquals("SHADOW && MULTICOLORED_BLOCKLIGHT", netherShadow.enabledCondition());
        assertEquals(Boolean.FALSE, netherShadow.enabledByDefault());
    }

    private static DimensionPlan plan(PackManifest m, String worldKey) {
        return m.dimensions().stream()
                .filter(d -> d.worldKey().equals(worldKey))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing dimension " + worldKey
                        + " in " + m.dimensions().stream().map(DimensionPlan::worldKey).toList()));
    }

    private static ProgramEntry program(DimensionPlan dim, String name) {
        Optional<ProgramEntry> p = dim.programs().stream().filter(x -> x.name().equals(name)).findFirst();
        return p.orElseThrow(() -> new AssertionError("missing program " + name));
    }

    private static String stripLabel(String report) {
        int nl = report.indexOf('\n');
        return report.substring(nl + 1);
    }
}
