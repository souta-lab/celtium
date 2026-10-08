package org.soutalab.celtium.probe;

import java.util.Locale;

/** No Vulkan device or terrain resource is allocated by this decision. */
public final class InteropEligibility {
    private InteropEligibility() { }

    public static void evaluate(ProbeReport report) {
        report.matchedVulkanDeviceUuid = null;
        if (!report.operatingSystem.toLowerCase(Locale.ROOT).contains("linux")) {
            report.reasons.add("This first probe implements Linux opaque-FD handles only.");
        }
        ProbeReport.OpenGlDevice gl = report.openGl;
        if (gl == null) {
            report.reasons.add("No current LWJGL 3 OpenGL context could be inspected.");
        } else if (!gl.memoryObject || !gl.memoryObjectFd || !gl.semaphore || !gl.semaphoreFd) {
            report.reasons.add("OpenGL external memory/semaphore FD extensions are incomplete.");
        }

        ProbeReport.VulkanDevice matched = null;
        if (gl != null) {
            for (ProbeReport.VulkanDevice vk : report.vulkanDevices) {
                if (validUuid(vk.deviceUuid) && gl.deviceUuids.contains(vk.deviceUuid)) {
                    matched = vk;
                    break;
                }
            }
        }
        if (matched == null) {
            report.reasons.add("No Vulkan physical device matches a valid OpenGL device UUID.");
        } else {
            report.matchedVulkanDeviceUuid = matched.deviceUuid;
            if (matched.deviceType == 4) {
                report.reasons.add("The matching Vulkan device is a CPU/software implementation.");
            }
            if (matched.apiMajor < 1 || (matched.apiMajor == 1 && matched.apiMinor < 2)) {
                report.reasons.add("The matching device does not meet the Vulkan 1.2 baseline.");
            }
            if (!matched.externalMemoryFd || !matched.externalSemaphoreFd) {
                report.reasons.add("Vulkan external memory/semaphore FD extensions are incomplete.");
            }
            if (!matched.binarySemaphoreImportable || !matched.binarySemaphoreExportable) {
                report.reasons.add("Opaque-FD binary semaphores are not both importable and exportable.");
            }
            requireFormat(report, matched, report.requiredColorFormat);
            requireFormat(report, matched, report.requiredDepthFormat);
            if (gl.driverUuid != null && !gl.driverUuid.equals(matched.driverUuid)) {
                report.warnings.add("GL and Vulkan driver UUIDs differ; actual cross-driver import is unverified.");
            }
        }
        report.status = report.reasons.isEmpty() ? "CANDIDATE_FOR_INTEROP_TEST" : "UNSUPPORTED_OR_UNAVAILABLE";
        report.warnings.add("GL image import, color/depth roundtrip, synchronization and copy cost still require an interop test.");
    }

    private static boolean validUuid(String uuid) {
        return uuid != null && uuid.matches("[0-9a-f]{32}") && !uuid.equals("00000000000000000000000000000000");
    }

    private static void requireFormat(ProbeReport report, ProbeReport.VulkanDevice vk, String name) {
        for (ProbeReport.ImageFormat format : vk.imageFormats) {
            if (name.equals(format.name) && format.supportsInteropExperiment()) {
                return;
            }
        }
        report.reasons.add("Required external image format is unsupported: " + name);
    }
}
