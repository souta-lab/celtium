package org.embeddedt.embeddium.impl.gui;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.taumc.celeritas.api.options.structure.OptionStorage;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.embeddedt.embeddium.impl.gui.options.TextProvider;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class SodiumGameOptions implements OptionStorage<SodiumGameOptions> {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String DEFAULT_FILE_NAME = "embeddium-options.json";

    public final QualitySettings quality = new QualitySettings();
    public final AdvancedSettings advanced = new AdvancedSettings();
    public final PerformanceSettings performance = new PerformanceSettings();
    public final NotificationSettings notifications = new NotificationSettings();

    private boolean readOnly;

    private Path configPath;

    public static SodiumGameOptions defaults() {
        var options = new SodiumGameOptions();
        options.configPath = getConfigPath(DEFAULT_FILE_NAME);

        return options;
    }

    @Override
    public SodiumGameOptions getData() {
        return this;
    }

    @Override
    public void save() {
        try {
            this.writeChanges();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't save configuration changes", e);
        }

        LOGGER.info("Flushed changes to Embeddium configuration");
    }

    public static class PerformanceSettings {
        public int chunkBuilderThreads = 0;
        @SerializedName("always_defer_chunk_updates_v2") // this will reset the option in older configs
        public boolean alwaysDeferChunkUpdates = true;

        public boolean animateOnlyVisibleTextures = true;
        public boolean useEntityCulling = true;
        public boolean useFogOcclusion = true;
        public boolean useBlockFaceCulling = true;
        // Default ON (item 351, D23): the compact 24-byte vertex is the honest packing floor for the
        // full terrain attribute set - every word in it is fully allocated (position 63/64 bits,
        // colour 32, texcoord 36 across two sites, draw/light 32, rdhFactor 4x8-bit bilinear colour
        // correction), so no smaller stride keeps every layout's semantics. The 32-byte vanilla-like
        // format stays one option away for content that needs full float position/UV precision.
        // Saved configs keep their stored value; this default governs fresh configurations only.
        public boolean useCompactVertexFormat = true;
        @SerializedName("use_translucent_face_sorting_v2")
        public boolean useTranslucentFaceSorting = true;
        public boolean useRenderPassOptimization = true;
        public boolean useRenderPassConsolidation = true;

        public AsyncOcclusionMode asyncOcclusionMode = AsyncOcclusionMode.ONLY_SHADOW;

        public boolean useRasterOcclusionCulling = false;

        // Idle/unfocused frame cap (item 388, absorption rank #12 - the FPS Reducer mechanism, in
        // Celtium's own shape). Default OFF, deliberately: the headless fixture has neither focus nor
        // input, so a default-on cap would silently cap every validation/KPI leg. When on, the cap is
        // applied inside CeltiumFramePacing as min(player frame limit, cap); the values are the
        // unfocused cap, the idle cap, and the input-free seconds before the idle cap applies.
        public boolean useIdleFrameCap = false;
        public int unfocusedFrameLimit = 30;
        public int idleFrameLimit = 30;
        public int idleDelaySeconds = 300;
    }

    public static class AdvancedSettings {
        public int cpuRenderAheadLimit = 3;
    }

    public static class QualitySettings {
        public GraphicsQuality weatherQuality = GraphicsQuality.DEFAULT;

        /**
         * Consumed in RenderGlobalMixin: when vanilla reloads the renderers it sets the leaves
         * graphics level from the raw vanilla graphics mode; the redirect substitutes
         * {@code leavesQuality.isFancy(vanillaMode)} as the value handed to
         * {@code BlockLeaves.setGraphicsLevel}, which is what drives leaf face culling
         * (BlockLeaves.shouldSideBeRendered) and the leaves render layer in the mesher.
         */
        public GraphicsQuality leavesQuality = GraphicsQuality.DEFAULT;

        public boolean enableVignette = true;

        @SerializedName("use_quad_normals_for_shading_v2")
        public boolean useQuadNormalsForShading = true;

        /**
         * Chunk fade-in duration. Kept at 0 under the Vulkan renderer (BH-33): the native terrain shader declares the
         * fade uniforms ({@code u_ChunkFadeEnabled} at push offset 60, {@code u_ChunkFadeParams} at 64 with the chunk
         * age in y), but the backend's {@code writeUniforms(RenderRegion, TerrainRenderPass, CameraTransform)} has no
         * per-section age to supply, so a non-zero value would be reported as active while every section faded by the
         * same amount. The options page pins both binding directions to 0 and says why; the real port needs a
         * section-age source at the draw site (a terrain-path task).
         */
        public int chunkFadeInDuration = 0;

        public int legacyBiomeBlendRadius = 0;
    }

    public static class NotificationSettings {
        public boolean forceDisableDonationPrompts = false;

        public boolean hasClearedDonationButton = false;
        public boolean hasSeenDonationPrompt = false;
    }

    public enum GraphicsQuality implements TextProvider {
        DEFAULT(List.of("options.gamma.default", "generator.default")),
        FANCY("options.clouds.fancy"),
        FAST("options.clouds.fast");

        private final TextComponent name;

        GraphicsQuality(String name) {
            this.name = TextComponent.translatable(name);
        }

        GraphicsQuality(List<String> names) {
            this.name = TextComponent.translatable(names);
        }

        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }

        public boolean isFancy(boolean fancyGraphics) {
            return (this == FANCY) || (this == DEFAULT && fancyGraphics);
        }
    }

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    /**
     * Moves values a stale config file can carry into states this build mishandles back to their safe values -
     * the safe-config migration inherited from Vulcanizator's survival layer (survey V-02: its loader migrates
     * stale configs to safe values at load, logged, instead of trusting whatever an older build or another
     * renderer wrote). Called from {@link #load(String)} after parsing; {@code load} re-saves the file, so a
     * migrated value persists. Pure and static so the rules are unit-testable without a config file. A rule is
     * added only for a value this tree's own record shows is mishandled - never on suspicion.
     *
     * @return one description per migration applied, in rule order; empty when nothing had to move
     */
    public static List<String> migrateUnsafeValues(SodiumGameOptions config) {
        List<String> applied = new ArrayList<>();
        // BH-33 (see the field's javadoc): under the Vulkan renderer a non-zero fade duration would be
        // reported as active while the backend has no per-section age to supply, so every section would fade
        // by the same amount. The options page already pins the value to 0 in both directions; a file written
        // by another renderer or an older version is the one carrier that can still bring a non-zero value in.
        if (config.quality.chunkFadeInDuration != 0) {
            applied.add("quality.chunk_fade_in_duration " + config.quality.chunkFadeInDuration + " -> 0"
                    + " (BH-33: no per-section age source under the Vulkan renderer)");
            config.quality.chunkFadeInDuration = 0;
        }
        return applied;
    }

    public static SodiumGameOptions load() {
        return load(DEFAULT_FILE_NAME);
    }

    public static SodiumGameOptions load(String name) {
        Path path = getConfigPath(name);
        SodiumGameOptions config;
        boolean resaveConfig = true;

        if (Files.exists(path)) {
            try (FileReader reader = new FileReader(path.toFile())) {
                config = GSON.fromJson(reader, SodiumGameOptions.class);
            } catch (IOException e) {
                throw new RuntimeException("Could not parse config", e);
            } catch (JsonSyntaxException e) {
                LOGGER.error("Could not parse config, will fallback to default settings", e);
                config = new SodiumGameOptions();
                resaveConfig = false;
            }
        } else {
            config = new SodiumGameOptions();
        }

        config.configPath = path;

        // TODO Embeddium: Remove the field completely in 0.4
        config.notifications.forceDisableDonationPrompts = false;

        for (String migration : migrateUnsafeValues(config)) {
            LOGGER.warn("Migrated a stale config value to its safe value: {}", migration);
        }

        try {
            if(resaveConfig)
                config.writeChanges();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't update config file", e);
        }

        return config;
    }

    private static Path getConfigPath(String name) {
        return Paths.get("config", name);
    }

    @Deprecated
    public void writeChanges() throws IOException {
        writeToDisk(this);
    }

    public static void writeToDisk(SodiumGameOptions config) throws IOException {
        if (config.isReadOnly()) {
            throw new IllegalStateException("Config file is read-only");
        }

        Path dir = config.configPath.getParent();

        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        } else if (!Files.isDirectory(dir)) {
            throw new IOException("Not a directory: " + dir);
        }

        // Use a temporary location next to the config's final destination
        Path tempPath = config.configPath.resolveSibling(config.configPath.getFileName() + ".tmp");

        // Write the file to our temporary location
        Files.writeString(tempPath, GSON.toJson(config));

        // Atomically replace the old config file (if it exists) with the temporary file
        Files.move(tempPath, config.configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean isReadOnly() {
        return this.readOnly;
    }

    public void setReadOnly() {
        this.readOnly = true;
    }

    public String getFileName() {
        return this.configPath.getFileName().toString();
    }
}
