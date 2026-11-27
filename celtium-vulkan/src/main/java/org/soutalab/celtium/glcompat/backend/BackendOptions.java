package org.soutalab.celtium.glcompat.backend;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */



/**
 * Holds the render backend choice read from the game configuration, so code that runs before or
 * outside the configuration system can still ask what the player selected. The SDL GPU backend
 * reads these once it is wired up; with only the LWJGL backend present every accessor reports the
 * default.
 */
public final class BackendOptions {

    private static boolean configSdlGpu;
    private static String configDriver = "";
    private static int configGlVersion;

    private BackendOptions() {}

    /** Records the configuration values. Called once while the game configuration loads. */
    public static void latch(boolean sdlGpu, String driver, int glVersion) {
        configSdlGpu = sdlGpu;
        configDriver = driver;
        configGlVersion = glVersion;
    }

    public static boolean sdlGpuRequested() {
        return configSdlGpu;
    }

    public static String sdlGpuDriver() {
        return sdlGpuDriver(configDriver);
    }

    public static String sdlGpuDriver(String driver) {
        return driver;
    }

    public static boolean configSdlGpu() {
        return configSdlGpu;
    }

    public static String configDriver() {
        return configDriver;
    }

    public static int configGlVersion() {
        return configGlVersion;
    }
}
