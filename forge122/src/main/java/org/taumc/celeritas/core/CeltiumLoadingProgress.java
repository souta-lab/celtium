package org.taumc.celeritas.core;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.ProgressManager;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend;

/** Forge supplies progress; Vulkan owns the startup canvas and presents it on the client thread. */
public final class CeltiumLoadingProgress {
    private static boolean active;
    private static boolean refreshing;
    private static long nextRefresh;
    private static BufferedImage logo;
    private static boolean logoRead;
    private static int failures;

    private CeltiumLoadingProgress() { }

    public static void start() { active = true; }

    public static void finish() {
        active = false;
        logo = null;
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
        nextRefresh = System.nanoTime() + 100_000_000L;
        try {
            List<ProgressManager.ProgressBar> bars = new ArrayList<>();
            Iterator<ProgressManager.ProgressBar> iterator = ProgressManager.barIterator();
            while (iterator.hasNext()) bars.add(iterator.next());
            if (bars.isEmpty()) return;
            if (!logoRead) {
                logoRead = true;
                try (InputStream input = mc.defaultResourcePack.getInputStream(new ResourceLocation("textures/gui/title/mojang.png"))) {
                    logo = ImageIO.read(input);
                }
            }
            int count = Math.min(3, bars.size());
            String[] labels = new String[count];
            int[] steps = new int[count];
            int[] totals = new int[count];
            for (int i = 0; i < count; i++) {
                ProgressManager.ProgressBar bar = bars.get(i == 0 ? 0 : bars.size() - count + i);
                labels[i] = bar.getTitle() + ": " + bar.getMessage();
                steps[i] = bar.getStep();
                totals[i] = bar.getSteps();
            }
            backend.presentLoadingProgress(mc.displayWidth, mc.displayHeight, logo, labels, steps, totals);
        } catch (Exception | LinkageError error) {
            failures++;
            System.out.println("[Celtium] native loading progress failed: " + error);
        } finally {
            refreshing = false;
        }
    }
}
