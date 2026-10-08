package org.soutalab.celtium.vulkan;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JSON this module reports is read by the mod command and by the validation fixture, so the field names and
 * the fail-closed defaults are part of the contract even though creating a real device needs a GPU.
 */
class VulkanDeviceReportTest {
    @Test
    void hexUsesTheWholeBufferWindow() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.position(2).limit(5);
        buffer.put(2, (byte) 0x00).put(3, (byte) 0xab).put(4, (byte) 0xff);
        assertEquals("00abff", GlDeviceIdentity.hex(buffer));
    }

    @Test
    void defaultsNeverClaimNativeTerrain() {
        VulkanDeviceReport report = new VulkanDeviceReport();
        assertEquals("UNSUPPORTED_OR_UNAVAILABLE", report.status);
        assertEquals("opengl", report.effectiveTerrainBackend);
        assertFalse(report.nativeTerrainImplemented);
        assertFalse(report.logicalDeviceCreated);
        assertEquals(1, report.requiredContract.samples);
    }

    @Test
    void jsonFieldNamesMatchWhatTheModAndFixtureRead() {
        VulkanDeviceReport report = new VulkanDeviceReport();
        report.status = "DEVICE_READY";
        report.logicalDeviceCreated = true;
        report.matched = new VulkanDeviceReport.MatchedDevice();
        report.matched.name = "test device";
        report.matched.uuidMatchesOpenGl = true;
        report.matched.driverUuidMatchesOpenGl = true;
        report.matched.queueFamilyIndex = 0;
        report.matched.externalMemoryFd = true;
        report.matched.externalSemaphoreFd = true;
        report.matched.binarySemaphoreImportable = true;
        report.matched.binarySemaphoreExportable = true;
        report.matched.colorImportable = true;
        report.matched.depthImportable = true;

        JsonObject json = JsonParser.parseString(VulkanBootstrap.toJson(report)).getAsJsonObject();
        assertEquals("DEVICE_READY", json.get("status").getAsString());
        assertTrue(json.get("logicalDeviceCreated").getAsBoolean());
        assertFalse(json.get("nativeTerrainImplemented").getAsBoolean());
        assertEquals("opengl", json.get("effectiveTerrainBackend").getAsString());
        JsonObject matched = json.getAsJsonObject("matched");
        assertTrue(matched.get("uuidMatchesOpenGl").getAsBoolean());
        assertTrue(matched.get("driverUuidMatchesOpenGl").getAsBoolean());
        assertEquals(0, matched.get("queueFamilyIndex").getAsInt());
        assertTrue(matched.get("binarySemaphoreImportable").getAsBoolean());
        assertTrue(matched.get("colorImportable").getAsBoolean());
        assertTrue(matched.get("depthImportable").getAsBoolean());
    }
}
