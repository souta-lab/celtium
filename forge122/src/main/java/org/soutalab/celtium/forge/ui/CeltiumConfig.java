package org.soutalab.celtium.forge.ui;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Celtium's native client configuration, stored at {@code config/celtium-options.json}.
 *
 * <p>Clean v0.4.0 schema: does not read any legacy options file, carries no donation
 * notification flags, and omits retired Celeritas terrain/raster legacy fields. Writes are
 * atomic (temp file + rename) and loads are defensive against missing, empty, malformed, or
 * partially-null JSON files.
 */
public class CeltiumConfig {
    private static final Logger LOGGER = LogManager.getLogger("Celtium");

    public static final String DEFAULT_FILE_NAME = "celtium-options.json";

    public QualitySettings quality = new QualitySettings();
    public PerformanceSettings performance = new PerformanceSettings();
    public LightingSettings lighting = new LightingSettings();
    public AdvancedSettings advanced = new AdvancedSettings();

    private transient boolean readOnly;
    private transient Path configPath;

    public static class QualitySettings {
        public GraphicsQuality weatherQuality = GraphicsQuality.DEFAULT;

        /**
         * Consumed in RenderGlobalMixin at the end of {@code loadRenderers}: substitutes
         * {@code leavesQuality.isFancy(vanillaMode)} into {@code BlockLeaves.setGraphicsLevel}.
         */
        public GraphicsQuality leavesQuality = GraphicsQuality.DEFAULT;

        /**
         * Celtium's native leaf culling (III-1). Default is SOLID; NONE disables leaf culling.
         */
        public LeafCullingMode leafCullingMode = LeafCullingMode.SOLID;

        public boolean enableVignette = true;
        public int legacyBiomeBlendRadius = 0;
    }

    public static class PerformanceSettings {
        public int chunkBuilderThreads = 0;
        public boolean alwaysDeferChunkUpdates = true;
        public boolean animateOnlyVisibleTextures = true;
        public boolean useEntityCulling = true;
        public boolean useFogOcclusion = true;
        public boolean useBlockFaceCulling = true;
        public boolean useTranslucentFaceSorting = true;

        // Idle/unfocused frame cap (default OFF so headless validation/KPI legs are not silently capped).
        public boolean useIdleFrameCap = false;
        public int unfocusedFrameLimit = 30;
        public int idleFrameLimit = 30;
        public int idleDelaySeconds = 300;
    }

    public static class LightingSettings {
        /**
         * Native GPU Dynamic Lights toggle (v0.4.0 default ON). Can still be overridden for A/B
         * fixture legs via {@code -Dceltium.dynamicLights=true|false}.
         */
        public boolean enableDynamicLights = true;
    }

    public static class AdvancedSettings {
        public int cpuRenderAheadLimit = 3;
    }

    public enum GraphicsQuality {
        DEFAULT("celtium.options.quality.default"),
        FANCY("options.clouds.fancy"),
        FAST("options.clouds.fast");

        private final String translationKey;

        GraphicsQuality(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return this.translationKey;
        }

        public boolean isFancy(boolean fancyGraphics) {
            return (this == FANCY) || (this == DEFAULT && fancyGraphics);
        }
    }

    /**
     * Native leaf-culling modes: NONE turns both mechanisms off; HOLLOW culls leaf faces facing
     * two or more consecutive leaves; SOLID_AGGRESSIVE renders a leaf as opaque when blocked on
     * all four horizontal sides; SOLID requires all six sides to be blocked.
     */
    public enum LeafCullingMode {
        NONE("celtium.options.leaf_culling_mode.none"),
        HOLLOW("celtium.options.leaf_culling_mode.hollow"),
        SOLID_AGGRESSIVE("celtium.options.leaf_culling_mode.solid_aggressive"),
        SOLID("celtium.options.leaf_culling_mode.solid");

        private final String translationKey;

        LeafCullingMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return this.translationKey;
        }
    }

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE | Modifier.TRANSIENT | Modifier.STATIC)
            .create();

    public static CeltiumConfig defaults() {
        CeltiumConfig config = new CeltiumConfig();
        config.configPath = getConfigPath(DEFAULT_FILE_NAME);
        return config;
    }

    /**
     * Whether a parsed config has every section present so callers never observe a null section.
     */
    static boolean isUsable(CeltiumConfig config) {
        return config != null
                && config.quality != null
                && config.performance != null
                && config.lighting != null
                && config.advanced != null;
    }

    /**
     * Replaces any null enum fields (e.g. from an unknown enum token in JSON) and clamps numeric
     * fields into their valid bounds.
     */
    static void sanitizeValues(CeltiumConfig config) {
        if (config.quality.weatherQuality == null) {
            config.quality.weatherQuality = GraphicsQuality.DEFAULT;
        }
        if (config.quality.leavesQuality == null) {
            config.quality.leavesQuality = GraphicsQuality.DEFAULT;
        }
        if (config.quality.leafCullingMode == null) {
            config.quality.leafCullingMode = LeafCullingMode.SOLID;
        }
        config.quality.legacyBiomeBlendRadius = clamp(config.quality.legacyBiomeBlendRadius, 0, 14);
        config.performance.chunkBuilderThreads = Math.max(0, config.performance.chunkBuilderThreads);
        config.performance.unfocusedFrameLimit = clamp(config.performance.unfocusedFrameLimit, 10, 260);
        config.performance.idleFrameLimit = clamp(config.performance.idleFrameLimit, 10, 260);
        config.performance.idleDelaySeconds = clamp(config.performance.idleDelaySeconds, 30, 1800);
        config.advanced.cpuRenderAheadLimit = clamp(config.advanced.cpuRenderAheadLimit, 0, 9);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static CeltiumConfig load() {
        return load(getConfigPath(DEFAULT_FILE_NAME));
    }

    public static CeltiumConfig load(String fileName) {
        return load(getConfigPath(fileName));
    }

    public static CeltiumConfig load(Path path) {
        CeltiumConfig config;
        boolean resaveConfig = true;

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, CeltiumConfig.class);
            } catch (IOException e) {
                throw new RuntimeException("Could not read Celtium config: " + path, e);
            } catch (JsonSyntaxException e) {
                LOGGER.error("Could not parse Celtium config at {}, falling back to defaults without overwriting file", path, e);
                config = new CeltiumConfig();
                resaveConfig = false;
            }
        } else {
            config = new CeltiumConfig();
        }

        if (!isUsable(config)) {
            LOGGER.error("Celtium config at {} was empty or missing required sections, falling back to defaults without overwriting file", path);
            config = new CeltiumConfig();
            resaveConfig = false;
        }

        config.configPath = path;
        sanitizeValues(config);

        if (resaveConfig) {
            try {
                writeToDisk(config);
            } catch (IOException e) {
                throw new RuntimeException("Could not write Celtium config: " + path, e);
            }
        }

        return config;
    }

    private static Path getConfigPath(String name) {
        return Paths.get("config", name);
    }

    public void save() {
        try {
            writeToDisk(this);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't save Celtium configuration changes", e);
        }
        LOGGER.info("Flushed changes to Celtium configuration");
    }

    public static void writeToDisk(CeltiumConfig config) throws IOException {
        if (config.isReadOnly()) {
            throw new IllegalStateException("Config file is read-only");
        }
        Path target = config.configPath != null ? config.configPath : getConfigPath(DEFAULT_FILE_NAME);
        Path dir = target.getParent();
        if (dir != null) {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            } else if (!Files.isDirectory(dir)) {
                throw new IOException("Not a directory: " + dir);
            }
        }

        Path tempPath = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tempPath, GSON.toJson(config), StandardCharsets.UTF_8);

        try {
            Files.move(tempPath, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempPath, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public boolean isReadOnly() {
        return this.readOnly;
    }

    public void setReadOnly() {
        this.readOnly = true;
    }

    public Path getConfigPath() {
        return this.configPath;
    }

    public String getFileName() {
        return this.configPath != null ? this.configPath.getFileName().toString() : DEFAULT_FILE_NAME;
    }
}
