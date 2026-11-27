package org.taumc.celeritas;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.soutalab.celtium.forge.CeltiumCommand;
import org.soutalab.celtium.forge.ui.CeltiumConfig;
import org.soutalab.celtium.forge.ui.CeltiumF3Overlay;
import org.taumc.celeritas.core.CeltiumCompetitors;

@Mod(modid = CeleritasVintage.MODID, useMetadata = true, clientSideOnly = true, acceptableRemoteVersions = "*")
public class CeleritasVintage {
    public static final String MODID = "celeritas";
    private static final Logger LOGGER = LogManager.getLogger("Celtium");
    public static String VERSION;
    private static final CeltiumConfig CONFIG = loadConfig();

    @EventHandler
    public void onConstruct(FMLConstructionEvent event) {
        // The startup refusal's second seat: if another jar won the CeleritasLoadingPlugin class name, the
        // loader skipped Celtium's coremod silently and the first seat (the plugin's injectData) never ran.
        // Construction is reached regardless of the coremod, so the shadowing detection runs first - its
        // refusal lists the known competitors and the shadowing together - and the shared check follows for
        // the case the coremod ran but a competitor is only visible now.
        ClassLoader loader = CeleritasVintage.class.getClassLoader();
        CeltiumCompetitors.checkPluginShadowing(loader);
        CeltiumCompetitors.check(loader);
        VERSION = Loader.instance().getIndexedModList().get(MODID).getVersion();
        MinecraftForge.EVENT_BUS.register(new CeltiumF3Overlay());
    }

    @EventHandler
    public void onInit(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new CeltiumCommand());
        LOGGER.info("Celtium terrain backend: {}. Optional Vulkan device check: /celtium vulkan",
                org.soutalab.celtium.forge.TerrainBackendStatus.describe());
        if ((Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment")) {
        }
        if (Loader.isModLoaded("alfheim")) {
            // Unsupported with Celtium (2026-10-26): Alfheim's client light pass re-marks the same sections for rebuild
            // every tick (Enigmatica 2 Expert: ~80,000 marks a second over ~440 sections, with LittleTiles' light
            // listener feeding it back), so distant terrain never settles. Pulsar Lighting Engine is the supported
            // Cleanroom lighting engine.
            LOGGER.warn("Alfheim is installed. Celtium does not support Alfheim; replace it with Pulsar Lighting Engine"
                    + " (https://modrinth.com/mod/pulsar-lighting-engine). Distant terrain may stay unbuilt or dark.");
        }
    }

    public static Logger logger() {
        return LOGGER;
    }

    private static CeltiumConfig loadConfig() {
        try {
            return CeltiumConfig.load();
        } catch (Exception e) {
            LOGGER.error("Failed to load configuration file", e);
            LOGGER.error("Using default configuration file in read-only mode");
            CeltiumConfig config = new CeltiumConfig();
            config.setReadOnly();
            return config;
        }
    }

    public static CeltiumConfig options() {
        if (CONFIG == null) {
            throw new IllegalStateException("Config not yet available");
        } else {
            return CONFIG;
        }
    }
}
