package org.soutalab.celtium.vulkan;

import java.util.ArrayList;
import java.util.List;

/** Result of the P3 device bootstrap. A created device is not a renderer: no terrain is uploaded or drawn. */
public final class VulkanDeviceReport {
    public final int schemaVersion = 1;
    public final String scope = "device-bootstrap-only";
    public final String effectiveTerrainBackend = "opengl";
    public final boolean nativeTerrainImplemented = false;
    public String status = "UNSUPPORTED_OR_UNAVAILABLE";
    public String lwjglVersion;
    public String loaderVersion;
    public OpenGl openGl;
    public Contract requiredContract = new Contract();
    public MatchedDevice matched;
    public boolean logicalDeviceCreated;
    public final List<String> reasons = new ArrayList<String>();
    public final List<String> warnings = new ArrayList<String>();

    public static final class OpenGl {
        public String vendor;
        public String renderer;
        public String version;
        public String driverUuid;
        public final List<String> deviceUuids = new ArrayList<String>();
        public boolean memoryObjectFd;
        public boolean semaphoreFd;
    }

    /** The interop contract already proven on both target GPUs in P1b. */
    public static final class Contract {
        public final String colorFormat = "R8G8B8A8_UNORM";
        public final String depthFormat = "D32_SFLOAT";
        public final int samples = 1;
        public final String[] deviceExtensions = {
                "VK_KHR_external_memory_fd",
                "VK_KHR_external_semaphore_fd",
        };
        public final String[] semaphoreDirections = {"import", "export"};
        public final int apiVersionMajor = 1;
        public final int apiVersionMinor = 2;
    }

    public static final class MatchedDevice {
        public String name;
        public String deviceUuid;
        public String driverUuid;
        public String apiVersion;
        public int deviceType;
        public String deviceTypeName;
        public boolean uuidMatchesOpenGl;
        public boolean driverUuidMatchesOpenGl;
        public int queueFamilyIndex = -1;
        public int queueCount;
        public boolean graphicsQueue;
        public boolean transferQueue;
        public boolean externalMemoryFd;
        public boolean externalSemaphoreFd;
        public boolean binarySemaphoreImportable;
        public boolean binarySemaphoreExportable;
        public boolean colorImportable;
        public boolean depthImportable;
        public boolean dedicatedAllocationRequired;
        public final List<String> enabledExtensions = new ArrayList<String>();
        public final List<String> missingExtensions = new ArrayList<String>();
    }
}
