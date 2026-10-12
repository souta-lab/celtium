package org.taumc.celeritas;

import com.mojang.realmsclient.gui.ChatFormatting;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.common.util.MathUtil;
import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import org.taumc.celeritas.impl.command.TogglePassCommand;
import org.soutalab.celtium.forge.CeltiumCommand;
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;

@Mod(modid = CeleritasVintage.MODID, useMetadata = true, clientSideOnly = true, acceptableRemoteVersions = "*")
public class CeleritasVintage {
    public static final String MODID = "celeritas";
    private static final Logger LOGGER = LogManager.getLogger("Celtium");
    public static String VERSION;
    private static final SodiumGameOptions CONFIG = loadConfig();

    @EventHandler
    public void onConstruct(FMLConstructionEvent event) {
        VERSION = Loader.instance().getIndexedModList().get(MODID).getVersion();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @EventHandler
    public void onInit(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new CeltiumCommand());
        LOGGER.info("Celtium terrain backend: {}. Optional Vulkan capability query: /celtium probe",
                org.soutalab.celtium.forge.TerrainBackendStatus.describe());
        if ((Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment")) {
            ClientCommandHandler.instance.registerCommand(new TogglePassCommand());
        }

    }

    /** Calls, total and worst build time, and the shape of what was added; printed once per 120 F3 frames. */
    private static long f3Calls;
    private static long f3Nanos;
    private static long f3WorstNanos;
    private static long f3Chars;
    private static long f3MaxChars;
    private static int f3MaxLines;
    private static final long[] f3Recent = new long[120];
    private static int f3RecentAt;

    /**
     * Whether the panel shows the full counter set.
     * <p>
     * Off by default since 2026-10-12 by measurement: the full set is one line of ~31,000 characters (measured
     * CELTIUM_F3_SECTION charsPerCall=31,378), the font renderer wraps it into ~780 rows, and the gui bracket goes
     * from 2.6 ms (overlay off) to 31.5 ms (overlay on) at 854x480 - about 28 ms of glyph drawing per frame. An
     * overlay that costs 28 ms is not a debug overlay. The panel is therefore no longer a counter source by default;
     * the authoritative surface is the log's CELTIUM_VERIFY line, which every fixture run now ends with
     * (via=fixture end). {@code -Dceltium.f3.fullSummary=true} restores the old panel exactly, so the old cost stays
     * measurable and a leg that wants counters off the panel can still have them.
     */
    private static final boolean FULL_SUMMARY = Boolean.getBoolean("celtium.f3.fullSummary");

    /**
     * Adds the default panel's bounded subset: three short lines, because the complaint about this panel was its
     * length. The device/driver line above it is the fourth. Everything else - the per-pass split of every bracket,
     * the host-buffer allocation census, the unmodelled GL entry points, all 602 counters - stays in the log's
     * {@code CELTIUM_VERIFY} line and in the panel under {@code -Dceltium.f3.fullSummary=true}, and the line below
     * says so where the user can read it.
     */
    private static void celtium$addCuratedLines(java.util.List<String> strings) {
        strings.add("Backend: " + org.soutalab.celtium.forge.TerrainBackendStatus.name());
        strings.add("Frame: " + org.soutalab.celtium.vulkan.FramePassTimings.summaryShort());
        strings.add("Full counters: CELTIUM_VERIFY in the log (-Dceltium.f3.fullSummary=true for the panel)");
    }

    /** How many F3 frames a built section is shown for; {@code -Dceltium.f3.sectionRefreshFrames} overrides it. */
    private static final int SECTION_REFRESH_FRAMES =
            Math.max(1, Integer.getInteger("celtium.f3.sectionRefreshFrames", 20).intValue());

    private static java.util.List<String> cachedSection;
    private static long cachedSectionBuiltAt;
    private static long sectionRebuilds;

    /**
     * {@return the renderer's debug strings, rebuilt only every {@link #SECTION_REFRESH_FRAMES} F3 frames}.
     * <p>
     * The list is copied into a new list so the caller cannot mutate the cache, and the copy is what keeps the
     * cadence honest: a frame between rebuilds adds exactly the same strings the last rebuild produced.
     */
    private static java.util.List<String> celtium$cachedSection(CeleritasWorldRenderer renderer) {
        boolean stale = cachedSection == null
                || f3Events - cachedSectionBuiltAt >= SECTION_REFRESH_FRAMES;
        if (stale) {
            cachedSection = new ArrayList<>(renderer.getDebugStrings());
            cachedSectionBuiltAt = f3Events;
            sectionRebuilds++;
            if (sectionRebuilds == 1 || sectionRebuilds % 20 == 0) {
                int chars = 0;
                for (String line : cachedSection) {
                    chars += line.length();
                }
                System.out.println("[Celtium] CELTIUM_F3_SECTION_REBUILD builds=" + sectionRebuilds
                        + " lines=" + cachedSection.size() + " chars=" + chars
                        + " refreshFrames=" + SECTION_REFRESH_FRAMES);
            }
        }
        return cachedSection;
    }

    private static long f3Events;
    private static long f3ReducedReturns;

    /** Counts every F3 event and every early return, printed once per 120 events. */
    private static void celtium$noteF3Event(int event, long ignored, int ignoredLines, boolean reducedReturn) {
        if (event != 0) {
            f3Events++;
            if (f3Events % 120 == 0) {
                System.out.println("[Celtium] CELTIUM_F3_EVENT events=" + f3Events + " reducedDebugReturns="
                        + f3ReducedReturns + " showDebugInfo="
                        + Minecraft.getMinecraft().gameSettings.showDebugInfo + " hideGUI="
                        + Minecraft.getMinecraft().gameSettings.hideGUI);
            }
        }
        if (reducedReturn) {
            f3ReducedReturns++;
        }
    }

    private static void celtium$noteSectionBuild(long nanos, int linesAdded, java.util.List<String> strings) {
        long chars = 0L;
        int longest = 0;
        for (int i = strings.size() - linesAdded; i < strings.size(); i++) {
            int length = strings.get(i).length();
            chars += length;
            if (length > longest) {
                longest = length;
            }
        }
        f3Calls++;
        f3Nanos += nanos;
        f3Chars += chars;
        if (nanos > f3WorstNanos) {
            f3WorstNanos = nanos;
        }
        if (chars > f3MaxChars) {
            f3MaxChars = chars;
        }
        f3Recent[f3RecentAt++ % f3Recent.length] = nanos;
        if (linesAdded > f3MaxLines) {
            f3MaxLines = linesAdded;
        }
        if (f3Calls % 120 == 0) {
            long[] sorted = f3Recent.clone();
            java.util.Arrays.sort(sorted);
            System.out.println("[Celtium] CELTIUM_F3_SECTION calls=" + f3Calls
                    + " buildMillisTotal=" + (f3Nanos / 1000000L)
                    + " buildMillisPerCall=" + (f3Nanos / f3Calls / 1000L) / 1000.0
                    + " worstMillis=" + (f3WorstNanos / 1000000L)
                    + " recentP50Micros=" + (sorted[sorted.length / 2] / 1000L)
                    + " recentWorstMicros=" + (sorted[sorted.length - 1] / 1000L)
                    + " charsPerCall=" + (f3Chars / f3Calls)
                    + " charsWorst=" + f3MaxChars + " linesAdded=" + linesAdded + " linesWorst=" + f3MaxLines);
        }
    }

    @SubscribeEvent
    public void onF3Text(RenderGameOverlayEvent.Text event) {
        // Counted before every early return: whether this handler runs at all, and where it stops, is the first fact
        // the F3 lag needs - a handler that returns early looks exactly like a renderer with no F3 section, and the
        // 36 KB summary is only built on the far side of the reduced-debug check.
        celtium$noteF3Event(1, 0L, 0, false);
        if (!Minecraft.getMinecraft().gameSettings.showDebugInfo) {
            return;
        }

        var strings = event.getRight();
        strings.add("");
        strings.add(String.format("%s%s Renderer (%s)", ChatFormatting.AQUA, "Celtium", VERSION));
        // The "Terrain backend: ..." line used to be here as well, while the renderer's own debug list already adds
        // "Terrain Backend: <same description>" (RenderSectionManager.getDebugStrings, from
        // CeleritasWorldRenderer.getTerrainBackendDescription) - the same string twice, differing only in the capital
        // B, which is what the user reported as two labels drawn over each other. This section keeps the renderer's
        // line and lost its own; TerrainBackendStatus.describe() is still what the renderer's description resolves to.

        // Embeddium: Show a lot less with reduced debug info
        if (Minecraft.getMinecraft().isReducedDebug()) {
            celtium$noteF3Event(0, 0L, 0, true);
            return;
        }

        // Which device, which driver, which Vulkan - one compact line, because the user's complaint was that the
        // driver and Vulkan version could not be found anywhere in F3. Built from the live context's own accessors and
        // never by creating a context: before a world exists they answer "unavailable", and the line says so in the
        // same shape TerrainBackendStatus uses for the before-a-world case.
        String deviceLine = org.soutalab.celtium.vulkan.VulkanContext.driverSummary();
        Boolean matchedGl = org.soutalab.celtium.vulkan.VulkanContext.matchedOpenGlDevice();
        String matched = matchedGl == null ? "no device selected yet" : matchedGl.toString();
        if (deviceLine == null || deviceLine.isEmpty() || "unavailable".equals(deviceLine)) {
            strings.add("Celtium device: not created yet (no Vulkan device has been selected); loader="
                    + org.soutalab.celtium.vulkan.VulkanContext.loaderVersion()
                    + "; matchedOpenGl=" + matched);
        } else {
            strings.add("Celtium device: " + deviceLine + "; loader="
                    + org.soutalab.celtium.vulkan.VulkanContext.loaderVersion()
                    + "; matchedOpenGl=" + matched);
        }

        // The F3 section's own cost, split from the overlay's rendering cost: the counter text is one enormous line
        // (36,497 characters in the widest leg measured), and whether the lag is *building* it or *drawing* it decides
        // what the fix has to be. Bounded: one summary line per 120 calls, plus the worst call.
        long sectionStart = System.nanoTime();
        int stringsBefore = strings.size();

        var renderer = CeleritasWorldRenderer.instanceNullable();

        if (renderer != null) {
            // The counter text is one line of ~31,000 characters (measured) and it is *drawn*, not only built: the
            // font renderer wraps it into ~780 visual lines every frame, which is what the F3 lag is. Building it was
            // measured at 0.911 ms per frame (p50 670 us, worst 3.6 ms) - small next to the drawing, but it is pure
            // waste to rebuild 602 fields for a panel refreshed on a cadence, so the text is built once and refreshed
            // every SECTION_REFRESH_FRAMES. Every field stays in it; what the user sees can be up to that many F3
            // frames stale (about 0.5-1.1 s at this scene's frame times).
            if (FULL_SUMMARY) {
                strings.addAll(celtium$cachedSection(renderer));
            } else {
                celtium$addCuratedLines(strings);
            }
        }
        celtium$noteSectionBuild(System.nanoTime() - sectionStart, strings.size() - stringsBefore, strings);

        for (int i = 0; i < strings.size(); i++) {
            String str = strings.get(i);

            if (str.startsWith("Allocated:")) {
                strings.add(i + 1, getNativeMemoryString());

                break;
            }
        }
    }

    private static String getNativeMemoryString() {
        return "Off-Heap: +" + MathUtil.toMib(getNativeMemoryUsage()) + "MB";
    }

    private static long getNativeMemoryUsage() {
        return ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed() + NativeBuffer.getTotalAllocated();
    }

    public static Logger logger() {
        return LOGGER;
    }

    private static SodiumGameOptions loadConfig() {
        try {
            return SodiumGameOptions.load();
        } catch (Exception e) {
            LOGGER.error("Failed to load configuration file", e);
            LOGGER.error("Using default configuration file in read-only mode");
            SodiumGameOptions config = new SodiumGameOptions();
            config.setReadOnly();
            return config;
        }
    }

    public static SodiumGameOptions options() {
        if (CONFIG == null) {
            throw new IllegalStateException("Config not yet available");
        } else {
            return CONFIG;
        }
    }
}
