package org.soutalab.celtium.forge.ui;

import com.mojang.realmsclient.gui.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.taumc.celeritas.CeleritasVintage;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Celtium's F3 debug overlay panel, separated from the mod entry class.
 */
public final class CeltiumF3Overlay {
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
     *
     * <p>Off by default: the full set is one line of ~31,000 characters which wraps into ~780 rows
     * in the font renderer (~28 ms/frame of glyph drawing). {@code -Dceltium.f3.fullSummary=true}
     * restores the full panel on demand; the authoritative counter surface is the log's
     * {@code CELTIUM_VERIFY} line.
     */
    private static final boolean FULL_SUMMARY = Boolean.getBoolean("celtium.f3.fullSummary");

    /** How many F3 frames a built section is shown for; {@code -Dceltium.f3.sectionRefreshFrames} overrides it. */
    private static final int SECTION_REFRESH_FRAMES =
            Math.max(1, Integer.getInteger("celtium.f3.sectionRefreshFrames", 20).intValue());

    private static List<String> cachedSection;
    private static long cachedSectionBuiltAt;
    private static long sectionRebuilds;

    private static long f3Events;
    private static long f3ReducedReturns;

    private static void addCuratedLines(List<String> strings) {
        String deviceLine = org.soutalab.celtium.vulkan.VulkanContext.deviceSummary();
        strings.add("Device: " + (deviceLine == null || deviceLine.isEmpty()
                ? "no native device selected yet" : deviceLine));
        strings.add("Backend: " + org.soutalab.celtium.forge.TerrainBackendStatus.name());
        strings.add("Frame: " + org.soutalab.celtium.vulkan.FramePassTimings.summaryShort());
        strings.add("Full counters: CELTIUM_VERIFY in the log (-Dceltium.f3.fullSummary=true for the panel)");
    }

    private static List<String> cachedSection() {
        boolean stale = cachedSection == null
                || f3Events - cachedSectionBuiltAt >= SECTION_REFRESH_FRAMES;
        if (stale) {
            cachedSection = new ArrayList<>();
            org.soutalab.celtium.vulkan.CeltiumBackend backend =
                    org.soutalab.celtium.vulkan.CeltiumBackends.current();
            if (backend != null) {
                cachedSection.add("Terrain backend: " + backend.getName());
                if (backend instanceof org.soutalab.celtium.vulkan.VulkanBackendAdapter vulkan) {
                    cachedSection.add(vulkan.celtium$lane1DiagnosticsSummary());
                }
            }
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

    private static void noteF3Event(int event, boolean reducedReturn) {
        if (event != 0) {
            f3Events++;
            if (org.soutalab.celtium.vulkan.PeriodicLog.ENABLED && f3Events % 120 == 0) {
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

    private static void noteSectionBuild(long nanos, int linesAdded, List<String> strings) {
        long chars = 0L;
        for (int i = strings.size() - linesAdded; i < strings.size(); i++) {
            chars += strings.get(i).length();
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
            Arrays.sort(sorted);
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
        noteF3Event(1, false);
        if (!Minecraft.getMinecraft().gameSettings.showDebugInfo) {
            return;
        }

        List<String> strings = event.getRight();
        strings.add("");
        strings.add(String.format("%s%s Renderer (%s)", ChatFormatting.AQUA, "Celtium", CeleritasVintage.VERSION));

        if (Minecraft.getMinecraft().isReducedDebug()) {
            noteF3Event(0, true);
            return;
        }

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

        long sectionStart = System.nanoTime();
        int stringsBefore = strings.size();

        if (org.soutalab.celtium.vulkan.CeltiumBackends.current() != null) {
            if (FULL_SUMMARY) {
                strings.addAll(cachedSection());
            } else {
                addCuratedLines(strings);
            }
        }
        noteSectionBuild(System.nanoTime() - sectionStart, strings.size() - stringsBefore, strings);

        for (int i = 0; i < strings.size(); i++) {
            String str = strings.get(i);
            if (str.startsWith("Allocated:")) {
                strings.add(i + 1, getNativeMemoryString());
                break;
            }
        }
    }

    private static String getNativeMemoryString() {
        return "Off-Heap: +" + (getNativeMemoryUsage() / (1024L * 1024L)) + "MB";
    }

    private static long getNativeMemoryUsage() {
        return ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed();
    }
}
