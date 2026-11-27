package org.soutalab.celtium.forge.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CeltiumConfig}: defaults, atomic round-trip, clean fresh creation
 * (no embeddium-options.json import), defensive load on corrupt/null JSON, and value sanitization.
 */
class CeltiumConfigTest {

    @Test
    void defaultsMatchV040Spec() {
        CeltiumConfig config = new CeltiumConfig();

        assertEquals(CeltiumConfig.GraphicsQuality.DEFAULT, config.quality.weatherQuality);
        assertEquals(CeltiumConfig.GraphicsQuality.DEFAULT, config.quality.leavesQuality);
        assertEquals(CeltiumConfig.LeafCullingMode.SOLID, config.quality.leafCullingMode);
        assertTrue(config.quality.enableVignette);
        assertEquals(0, config.quality.legacyBiomeBlendRadius);

        assertEquals(0, config.performance.chunkBuilderThreads);
        assertTrue(config.performance.alwaysDeferChunkUpdates);
        assertTrue(config.performance.animateOnlyVisibleTextures);
        assertTrue(config.performance.useEntityCulling);
        assertTrue(config.performance.useFogOcclusion);
        assertTrue(config.performance.useBlockFaceCulling);
        assertTrue(config.performance.useTranslucentFaceSorting);
        assertFalse(config.performance.useIdleFrameCap);
        assertEquals(30, config.performance.unfocusedFrameLimit);
        assertEquals(30, config.performance.idleFrameLimit);
        assertEquals(300, config.performance.idleDelaySeconds);

        assertTrue(config.lighting.enableDynamicLights, "Dynamic Lights defaults to ON in v0.4.0");
        assertEquals(3, config.advanced.cpuRenderAheadLimit);
    }

    @Test
    void roundTripPersistsAllSectionsAndOmitsRetiredFields(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve(CeltiumConfig.DEFAULT_FILE_NAME);
        CeltiumConfig config = CeltiumConfig.load(path);

        config.quality.weatherQuality = CeltiumConfig.GraphicsQuality.FAST;
        config.quality.leavesQuality = CeltiumConfig.GraphicsQuality.FANCY;
        config.quality.leafCullingMode = CeltiumConfig.LeafCullingMode.HOLLOW;
        config.quality.enableVignette = false;
        config.quality.legacyBiomeBlendRadius = 5;

        config.performance.chunkBuilderThreads = 4;
        config.performance.alwaysDeferChunkUpdates = false;
        config.performance.animateOnlyVisibleTextures = false;
        config.performance.useEntityCulling = false;
        config.performance.useFogOcclusion = false;
        config.performance.useBlockFaceCulling = false;
        config.performance.useTranslucentFaceSorting = false;
        config.performance.useIdleFrameCap = true;
        config.performance.unfocusedFrameLimit = 20;
        config.performance.idleFrameLimit = 10;
        config.performance.idleDelaySeconds = 600;

        config.lighting.enableDynamicLights = false;
        config.advanced.cpuRenderAheadLimit = 6;

        config.save();

        String rawJson = Files.readString(path, StandardCharsets.UTF_8);
        assertFalse(rawJson.contains("notifications"), "notifications section must not exist");
        assertFalse(rawJson.contains("use_fast_block_renderer"), "retired legacy field must not exist");
        assertFalse(rawJson.contains("use_compact_vertex_format"), "retired legacy field must not exist");
        assertFalse(rawJson.contains("use_raster_occlusion_culling"), "retired legacy field must not exist");
        assertFalse(rawJson.contains("chunk_fade_in_duration"), "retired legacy field must not exist");
        assertFalse(rawJson.contains("async_occlusion_mode"), "retired legacy field must not exist");
        assertFalse(rawJson.contains("use_quad_normals_for_shading"), "retired legacy field must not exist");

        CeltiumConfig reloaded = CeltiumConfig.load(path);
        assertEquals(CeltiumConfig.GraphicsQuality.FAST, reloaded.quality.weatherQuality);
        assertEquals(CeltiumConfig.GraphicsQuality.FANCY, reloaded.quality.leavesQuality);
        assertEquals(CeltiumConfig.LeafCullingMode.HOLLOW, reloaded.quality.leafCullingMode);
        assertFalse(reloaded.quality.enableVignette);
        assertEquals(5, reloaded.quality.legacyBiomeBlendRadius);

        assertEquals(4, reloaded.performance.chunkBuilderThreads);
        assertFalse(reloaded.performance.alwaysDeferChunkUpdates);
        assertFalse(reloaded.performance.animateOnlyVisibleTextures);
        assertFalse(reloaded.performance.useEntityCulling);
        assertFalse(reloaded.performance.useFogOcclusion);
        assertFalse(reloaded.performance.useBlockFaceCulling);
        assertFalse(reloaded.performance.useTranslucentFaceSorting);
        assertTrue(reloaded.performance.useIdleFrameCap);
        assertEquals(20, reloaded.performance.unfocusedFrameLimit);
        assertEquals(10, reloaded.performance.idleFrameLimit);
        assertEquals(600, reloaded.performance.idleDelaySeconds);

        assertFalse(reloaded.lighting.enableDynamicLights);
        assertEquals(6, reloaded.advanced.cpuRenderAheadLimit);
    }

    @Test
    void freshLoadIgnoresSiblingEmbeddiumOptionsJson(@TempDir Path tempDir) throws Exception {
        Path legacy = tempDir.resolve("embeddium-options.json");
        Files.writeString(legacy, "{\"quality\":{\"enable_vignette\":false}}", StandardCharsets.UTF_8);

        Path fresh = tempDir.resolve(CeltiumConfig.DEFAULT_FILE_NAME);
        CeltiumConfig loaded = CeltiumConfig.load(fresh);

        assertTrue(Files.exists(fresh), "fresh celtium-options.json should be created");
        assertTrue(loaded.quality.enableVignette, "must not import embeddium-options.json");
    }

    @Test
    void malformedJsonFallsBackToDefaultsWithoutOverwriting(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve(CeltiumConfig.DEFAULT_FILE_NAME);
        String broken = "{ not valid json";
        Files.writeString(path, broken, StandardCharsets.UTF_8);

        CeltiumConfig loaded = CeltiumConfig.load(path);

        assertTrue(CeltiumConfig.isUsable(loaded));
        assertTrue(loaded.lighting.enableDynamicLights);
        assertEquals(broken, Files.readString(path, StandardCharsets.UTF_8),
                "corrupt file must be left untouched");
    }

    @Test
    void emptyAndNullJsonFallBackToDefaultsWithoutOverwriting(@TempDir Path tempDir) throws Exception {
        Path emptyPath = tempDir.resolve("empty.json");
        Files.writeString(emptyPath, "", StandardCharsets.UTF_8);
        assertTrue(CeltiumConfig.isUsable(CeltiumConfig.load(emptyPath)));
        assertEquals("", Files.readString(emptyPath, StandardCharsets.UTF_8));

        Path nullPath = tempDir.resolve("null.json");
        Files.writeString(nullPath, "null", StandardCharsets.UTF_8);
        assertTrue(CeltiumConfig.isUsable(CeltiumConfig.load(nullPath)));
        assertEquals("null", Files.readString(nullPath, StandardCharsets.UTF_8));

        Path nullSectionPath = tempDir.resolve("null-section.json");
        String nullSection = "{\"quality\": null}";
        Files.writeString(nullSectionPath, nullSection, StandardCharsets.UTF_8);
        assertTrue(CeltiumConfig.isUsable(CeltiumConfig.load(nullSectionPath)));
        assertEquals(nullSection, Files.readString(nullSectionPath, StandardCharsets.UTF_8));
    }

    @Test
    void partialJsonKeepsDefaultsAndSanitizesOutOfBoundsValues(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("partial.json");
        Files.writeString(path,
                "{\"quality\":{\"leaf_culling_mode\":\"UNKNOWN_TOKEN\",\"legacy_biome_blend_radius\":99},"
                        + "\"advanced\":{\"cpu_render_ahead_limit\":-5}}",
                StandardCharsets.UTF_8);

        CeltiumConfig loaded = CeltiumConfig.load(path);

        assertEquals(CeltiumConfig.LeafCullingMode.SOLID, loaded.quality.leafCullingMode);
        assertEquals(14, loaded.quality.legacyBiomeBlendRadius);
        assertEquals(0, loaded.advanced.cpuRenderAheadLimit);
        assertTrue(loaded.lighting.enableDynamicLights);
    }

    @Test
    void readOnlyConfigRejectsWrite(@TempDir Path tempDir) {
        Path path = tempDir.resolve(CeltiumConfig.DEFAULT_FILE_NAME);
        CeltiumConfig config = CeltiumConfig.load(path);
        config.setReadOnly();

        assertThrows(IllegalStateException.class, () -> CeltiumConfig.writeToDisk(config));
    }
}
