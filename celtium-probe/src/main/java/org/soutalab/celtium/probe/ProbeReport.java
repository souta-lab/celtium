package org.soutalab.celtium.probe;

import java.util.ArrayList;
import java.util.List;

/** A capability query is deliberately separate from an interop or renderer success. */
public final class ProbeReport {
    public final int schemaVersion = 1;
    public final String scope = "capability-query-only";
    /**
     * The terrain backend this process is actually running, filled in by the caller that has a running client
     * ({@code /celtium probe}) and left at {@code unknown} by the query itself.
     * <p>
     * These two fields used to be compile-time constants - {@code "opengl"} and {@code false} - which made the report
     * state the opposite of the truth in a GL-less run whose renderer was drawing terrain natively. A capability query
     * cannot know the answer, so it no longer claims to; {@link #terrainBackendSource} records where the value came
     * from, and {@link #depthRoundtripVerified} stays false because no query proves a roundtrip.
     */
    public String effectiveTerrainBackend = "unknown";
    public boolean nativeTerrainImplemented = false;
    /** Where {@link #effectiveTerrainBackend} came from, so a default is never read as an observation. */
    public String terrainBackendSource = "not observed by the capability query";
    public final boolean depthRoundtripVerified = false;
    public String operatingSystem = System.getProperty("os.name");
    public String contextSource = "current-gl-context";
    public Integer eglDeviceIndex;
    public String lwjglVersion;
    public String vulkanLoaderVersion;
    public String requiredColorFormat = "R8G8B8A8_UNORM";
    public String requiredDepthFormat = "D24_UNORM_S8_UINT";
    public String status = "UNAVAILABLE";
    public OpenGlDevice openGl;
    public final List<VulkanDevice> vulkanDevices = new ArrayList<VulkanDevice>();
    public String matchedVulkanDeviceUuid;
    public final List<String> reasons = new ArrayList<String>();
    public final List<String> warnings = new ArrayList<String>();

    public static final class OpenGlDevice {
        public String vendor;
        public String renderer;
        public String version;
        public String driverUuid;
        public final List<String> deviceUuids = new ArrayList<String>();
        public boolean memoryObject;
        public boolean memoryObjectFd;
        public boolean semaphore;
        public boolean semaphoreFd;
    }

    public static final class VulkanDevice {
        public String name;
        public String apiVersion;
        public int apiMajor;
        public int apiMinor;
        public int deviceType;
        public String deviceUuid;
        public String driverUuid;
        public boolean externalMemoryFd;
        public boolean externalSemaphoreFd;
        public boolean binarySemaphoreImportable;
        public boolean binarySemaphoreExportable;
        public final List<ImageFormat> imageFormats = new ArrayList<ImageFormat>();
    }

    public static final class ImageFormat {
        public String name;
        public int vulkanFormat;
        public int queryResult;
        public boolean importable;
        public boolean exportable;
        public boolean opaqueFdCompatible;
        public boolean singleSample;
        public boolean dedicatedAllocationRequired;

        public boolean supportsInteropExperiment() {
            return queryResult == 0 && importable && exportable && opaqueFdCompatible && singleSample;
        }
    }
}
