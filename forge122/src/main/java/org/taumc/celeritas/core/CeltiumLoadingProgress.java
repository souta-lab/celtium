package org.taumc.celeritas.core;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.ProgressManager;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend;

/**
 * Forge's loading screen on the client thread. Forge's {@code SplashProgress} thread needs a shared GL context, so it
 * is not started (CeltiumGllessSplashTransformer); its frame - layout, assets and {@code config/splash.properties} -
 * is drawn natively instead, every time Forge's {@code ProgressManager} reports progress through
 * {@code processWindowMessages}, at most every 16 ms.
 */
public final class CeltiumLoadingProgress {
    private static boolean active;
    private static boolean refreshing;
    private static long nextRefresh;
    private static long startedNanos;
    private static boolean assetsRead;
    private static BufferedImage logo;
    private static BufferedImage forge;
    private static BufferedImage font;
    private static int[] colors;
    private static boolean showMemory = true;
    private static boolean rotate;
    private static int logoOffset;
    private static int failures;
    private static int presented;

    private CeltiumLoadingProgress() { }

    public static void start() {
        active = true;
        startedNanos = System.nanoTime();
    }

    public static void finish() {
        active = false;
        logo = null;
        forge = null;
        font = null;
        Object backend = TerrainGpuBackends.current();
        if (backend instanceof VulkanTerrainGpuBackend) {
            try {
                ((VulkanTerrainGpuBackend) backend).finishLoadingProgress();
            } catch (RuntimeException | LinkageError error) {
                System.out.println("[Celtium] native loading progress cleanup failed: " + error);
            }
        }
    }

    public static void refresh() {
        if (!active || refreshing || failures >= 3 || System.nanoTime() < nextRefresh) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || !mc.isCallingFromMinecraftThread() || mc.world != null) return;
        Object current = TerrainGpuBackends.current();
        if (!(current instanceof VulkanTerrainGpuBackend)) return;
        VulkanTerrainGpuBackend backend = (VulkanTerrainGpuBackend) current;
        if (!backend.celtium$isWindowBackend() || mc.displayWidth <= 0 || mc.displayHeight <= 0) return;
        refreshing = true;
        // Forge's thread draws at up to 100 fps; 16 ms keeps the animated logo moving without the loader paying for
        // more frames than the window can show.
        nextRefresh = System.nanoTime() + 16_000_000L;
        try {
            if (!assetsRead) {
                assetsRead = true;
                readAssets(mc);
            }
            // Forge's choice of bars: the first, and the last two of the rest.
            ProgressManager.ProgressBar first = null;
            ProgressManager.ProgressBar penult = null;
            ProgressManager.ProgressBar last = null;
            Iterator<ProgressManager.ProgressBar> iterator = ProgressManager.barIterator();
            while (iterator.hasNext()) {
                if (first == null) {
                    first = iterator.next();
                } else {
                    penult = last;
                    last = iterator.next();
                }
            }
            List<ProgressManager.ProgressBar> bars = new ArrayList<>(3);
            if (first != null) bars.add(first);
            if (penult != null) bars.add(penult);
            if (last != null) bars.add(last);
            String[] titles = new String[bars.size()];
            String[] messages = new String[bars.size()];
            int[] steps = new int[bars.size()];
            int[] totals = new int[bars.size()];
            for (int i = 0; i < bars.size(); i++) {
                ProgressManager.ProgressBar bar = bars.get(i);
                titles[i] = bar.getTitle();
                messages[i] = bar.getMessage();
                steps[i] = bar.getStep();
                totals[i] = bar.getSteps();
            }
            int angle = (int) ((System.nanoTime() - startedNanos) / 10_000_000L);
            backend.presentLoadingProgress(mc.displayWidth, mc.displayHeight, colors, showMemory, rotate, logoOffset,
                    logo, forge, font, titles, messages, steps, totals, angle);
            if (presented++ == 0) {
                System.out.println("[Celtium] CELTIUM_LOADING_SCREEN first Forge splash frame presented, bars="
                        + bars.size() + " size=" + mc.displayWidth + "x" + mc.displayHeight);
            }
        } catch (Exception | LinkageError error) {
            failures++;
            System.out.println("[Celtium] native loading progress failed: " + error);
        } finally {
            refreshing = false;
        }
    }

    /** Forge's assets and its {@code config/splash.properties}, with Forge's defaults for anything missing. */
    private static void readAssets(Minecraft mc) {
        Properties config = new Properties();
        File configFile = new File(mc.gameDir, "config/splash.properties");
        if (configFile.isFile()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
                config.load(reader);
            } catch (Exception error) {
                System.out.println("[Celtium] CELTIUM_LOADING_SCREEN splash.properties unreadable: " + error);
            }
        }
        colors = new int[] {
                hex(config, "background", 0xFFFFFF), hex(config, "barBorder", 0xC0C0C0), hex(config, "bar", 0xCB3D35),
                hex(config, "barBackground", 0xFFFFFF), hex(config, "memoryGood", 0x78CB34),
                hex(config, "memoryWarn", 0xE6E84A), hex(config, "memoryLow", 0xE42F2F)};
        showMemory = !"false".equalsIgnoreCase(config.getProperty("showMemory", "true"));
        rotate = "true".equalsIgnoreCase(config.getProperty("rotate", "false"));
        try {
            logoOffset = Integer.parseInt(config.getProperty("logoOffset", "0").trim());
        } catch (NumberFormatException ignored) {
            logoOffset = 0;
        }
        logo = image(mc, new ResourceLocation("textures/gui/title/mojang.png"));
        font = image(mc, new ResourceLocation(config.getProperty("fontTexture", "textures/font/ascii.png")));
        forge = image(mc, new ResourceLocation(config.getProperty("forgeTexture", "fml:textures/gui/forge.png")));
        if (forge == null) {
            forge = image(mc, new ResourceLocation("fml:textures/gui/forge.png"));
        }
    }

    private static int hex(Properties config, String key, int fallback) {
        try {
            return Integer.decode(config.getProperty(key, "0x" + Integer.toHexString(fallback)).trim()) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** From the default resource pack, else from the classpath (Forge's own {@code fml} assets live there). */
    private static BufferedImage image(Minecraft mc, ResourceLocation location) {
        try (InputStream input = mc.defaultResourcePack.getInputStream(location)) {
            return ImageIO.read(input);
        } catch (Exception notInPack) {
            String path = "/assets/" + location.getNamespace() + "/" + location.getPath();
            try (InputStream input = CeltiumLoadingProgress.class.getResourceAsStream(path)) {
                return input == null ? null : ImageIO.read(input);
            } catch (Exception error) {
                return null;
            }
        }
    }
}
