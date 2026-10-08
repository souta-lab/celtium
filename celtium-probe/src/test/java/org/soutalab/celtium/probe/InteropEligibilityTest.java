package org.soutalab.celtium.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InteropEligibilityTest {
    private static final String UUID = "0123456789abcdef0123456789abcdef";

    @Test void matchingCapabilitiesDoNotClaimRendererOrDepthSuccess() {
        ProbeReport report = eligible();
        InteropEligibility.evaluate(report);
        assertEquals("CANDIDATE_FOR_INTEROP_TEST", report.status);
        assertEquals(UUID, report.matchedVulkanDeviceUuid);
        assertEquals("opengl", report.effectiveTerrainBackend);
        assertFalse(report.nativeTerrainImplemented);
        assertFalse(report.depthRoundtripVerified);
    }

    @Test void aDifferentGpuCannotPassBecauseItHasTheRightExtensions() {
        ProbeReport report = eligible();
        report.vulkanDevices.get(0).deviceUuid = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        assertRejected(report, "matches");
    }

    @Test void unknownZeroUuidsDoNotEstablishGpuIdentity() {
        ProbeReport report = eligible();
        report.openGl.deviceUuids.clear();
        report.openGl.deviceUuids.add("00000000000000000000000000000000");
        report.vulkanDevices.get(0).deviceUuid = "00000000000000000000000000000000";
        assertRejected(report, "matches");
    }

    @Test void matchesAnyEnumeratedGlDeviceRatherThanAssumingTheFirst() {
        ProbeReport report = eligible();
        report.openGl.deviceUuids.add(0, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        InteropEligibility.evaluate(report);
        assertEquals("CANDIDATE_FOR_INTEROP_TEST", report.status);
    }

    @Test void colorSupportAloneDoesNotPassTheDepthGate() {
        ProbeReport report = eligible();
        report.vulkanDevices.get(0).imageFormats.remove(1);
        assertRejected(report, "D24_UNORM_S8_UINT");
    }

    @Test void anAlternativeDepthFormatDoesNotSatisfyTheRequiredFormat() {
        ProbeReport report = eligible();
        report.vulkanDevices.get(0).imageFormats.get(1).name = "D32_SFLOAT";
        assertRejected(report, "D24_UNORM_S8_UINT");
    }

    @Test void exportOnlyImagesAreRejected() {
        ProbeReport report = eligible();
        report.vulkanDevices.get(0).imageFormats.get(1).importable = false;
        assertRejected(report, "D24_UNORM_S8_UINT");
    }

    @Test void failedImageQueryIsRejectedEvenIfOtherFieldsAreSet() {
        ProbeReport report = eligible();
        report.vulkanDevices.get(0).imageFormats.get(1).queryResult = -11;
        assertRejected(report, "D24_UNORM_S8_UINT");
    }

    @Test void incompatibleHandlesAndSamplesAreRejected() {
        ProbeReport report = eligible();
        report.vulkanDevices.get(0).imageFormats.get(1).opaqueFdCompatible = false;
        assertRejected(report, "D24_UNORM_S8_UINT");
        report = eligible();
        report.vulkanDevices.get(0).imageFormats.get(1).singleSample = false;
        assertRejected(report, "D24_UNORM_S8_UINT");
    }

    @Test void semaphoreMustSupportBothDirections() {
        ProbeReport report = eligible();
        report.vulkanDevices.get(0).binarySemaphoreImportable = false;
        assertRejected(report, "binary semaphores");
    }

    @Test void missingGlFdExtensionIsRejected() {
        ProbeReport report = eligible();
        report.openGl.memoryObjectFd = false;
        assertRejected(report, "OpenGL external");
    }

    @Test void unsupportedOsAndApiAndSoftwareDevicesAreRejected() {
        ProbeReport report = eligible();
        report.operatingSystem = "Windows 11";
        assertRejected(report, "Linux");
        report = eligible();
        report.vulkanDevices.get(0).apiMinor = 1;
        assertRejected(report, "Vulkan 1.2");
        report = eligible();
        report.vulkanDevices.get(0).deviceType = 4;
        assertRejected(report, "CPU/software");
    }

    @Test void aPreviousLoaderFailureCannotTurnIntoSuccess() {
        ProbeReport report = eligible();
        report.reasons.add("Loader failed");
        assertRejected(report, "Loader failed");
    }

    @Test void missingContextIsAReportedFailure() {
        ProbeReport report = eligible();
        report.openGl = null;
        assertRejected(report, "No current");
    }

    @Test void reportSerializationKeepsTheUnverifiedFlagsExplicit() {
        ProbeReport report = eligible();
        String json = CeltiumProbe.toJson(report);
        assertTrue(json.contains("\"nativeTerrainImplemented\": false"));
        assertTrue(json.contains("\"depthRoundtripVerified\": false"));
    }

    private static void assertRejected(ProbeReport report, String reasonFragment) {
        InteropEligibility.evaluate(report);
        assertEquals("UNSUPPORTED_OR_UNAVAILABLE", report.status);
        assertTrue(report.reasons.stream().anyMatch(reason -> reason.contains(reasonFragment)), report.reasons.toString());
    }

    private static ProbeReport eligible() {
        ProbeReport report = new ProbeReport();
        report.operatingSystem = "Linux";
        report.openGl = new ProbeReport.OpenGlDevice();
        report.openGl.deviceUuids.add(UUID);
        report.openGl.driverUuid = UUID;
        report.openGl.memoryObject = report.openGl.memoryObjectFd = true;
        report.openGl.semaphore = report.openGl.semaphoreFd = true;
        ProbeReport.VulkanDevice vk = new ProbeReport.VulkanDevice();
        vk.deviceUuid = vk.driverUuid = UUID;
        vk.apiMajor = 1;
        vk.apiMinor = 2;
        vk.deviceType = 2;
        vk.externalMemoryFd = vk.externalSemaphoreFd = true;
        vk.binarySemaphoreImportable = vk.binarySemaphoreExportable = true;
        for (String name : new String[]{report.requiredColorFormat, report.requiredDepthFormat}) {
            ProbeReport.ImageFormat format = new ProbeReport.ImageFormat();
            format.name = name;
            format.importable = format.exportable = format.opaqueFdCompatible = format.singleSample = true;
            vk.imageFormats.add(format);
        }
        report.vulkanDevices.add(vk);
        return report;
    }
}
