package org.soutalab.celtium.glcompat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/** Decides whether instrumentation is worth emitting this frame. */
public final class CaptureGate {

    private static final Logger LOGGER = LogManager.getLogger("Actinium");
    private static final boolean IS_LINUX = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    private static final Path PROC_SELF_MAPS = Paths.get("/proc/self/maps");
    private static final String[] CAPTURE_ENV = { "RENDERDOC_CAPTURE_KEYPRESS", "ENABLE_VULKAN_RENDERDOC_CAPTURE", "RENDERDOC_HOOK_EGL" };
    private static final String[] CAPTURE_LIBS = { "renderdoc", "glxtrace", "egltrace", "apitrace" };
    public static final boolean FORCED = Boolean.getBoolean("angelica.debug.markers") || Boolean.getBoolean("org.lwjgl.util.Debug");
    public static final boolean TOOL_ATTACHED = detectCaptureTool();
    public static boolean markersThisFrame = true;

    private CaptureGate() {}

    public static void refresh() {
        // No live profiler input: the Tracy integration from upstream is not ported.
        markersThisFrame = gateValue(FORCED, TOOL_ATTACHED, false);
    }

    static boolean gateValue(boolean forced, boolean toolAttached, boolean tracyConnected) {
        return forced || toolAttached || tracyConnected;
    }

    public static boolean enabledAtStartup() {
        return FORCED || TOOL_ATTACHED;
    }

    private static boolean detectCaptureTool() {
        if (detectFromEnv(System::getenv)) {
            LOGGER.info("Capture tool detected via environment; instrumentation enabled");
            return true;
        }
        if (!IS_LINUX) return false;
        try {
            if (detectFromMaps(Files.readAllLines(PROC_SELF_MAPS, StandardCharsets.ISO_8859_1))) {
                LOGGER.info("Capture tool library detected in process map; instrumentation enabled");
                return true;
            }
        } catch (IOException e) {
            LOGGER.warn("Could not read {} for capture tool detection: {}", PROC_SELF_MAPS, e.toString());
        }
        return false;
    }

    static boolean detectFromEnv(UnaryOperator<String> env) {
        for (String key : CAPTURE_ENV) {
            if (env.apply(key) != null) return true;
        }
        return false;
    }

    static boolean detectFromMaps(List<String> mapLines) {
        for (int i = 0; i < mapLines.size(); i++) {
            final String line = mapLines.get(i).toLowerCase(Locale.ROOT);
            for (String lib : CAPTURE_LIBS) {
                if (line.contains(lib)) return true;
            }
        }
        return false;
    }
}
