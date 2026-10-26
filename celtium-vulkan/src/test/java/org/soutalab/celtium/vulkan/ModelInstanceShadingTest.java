package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import static org.junit.jupiter.api.Assertions.*;

class ModelInstanceShadingTest {
    @Test void reusedRecordCarriesItsOwnLightingAndAlphaEnable() {
        long record = MemoryUtil.nmemAlloc(VulkanModelInstances.RECORD_BYTES);
        try {
            MemoryUtil.memSet(record, 0x5a, VulkanModelInstances.RECORD_BYTES);
            float[] light = new float[20];
            for (int i = 0; i < light.length; i++) light[i] = i + 0.25f;
            VulkanModelInstances.writeShadingRecord(record, light, new float[] {240, 224}, 7, 11, 0x0204);
            light[0] = -1;
            assertEquals(0.25f, MemoryUtil.memGetFloat(record + 128));
            assertEquals(19.25f, MemoryUtil.memGetFloat(record + 204));
            assertEquals(240, MemoryUtil.memGetFloat(record + 208));
            assertEquals(224, MemoryUtil.memGetFloat(record + 212));
            assertEquals(0x0204, MemoryUtil.memGetInt(record + 232));
            assertEquals(1, MemoryUtil.memGetInt(record + 236));
            VulkanModelInstances.writeShadingRecord(record, null, null, 8, 8, 0);
            for (int i = 128; i < 216; i++) assertEquals((byte) 0, MemoryUtil.memGetByte(record + i));
            assertEquals(8, MemoryUtil.memGetInt(record + 224));
            assertEquals(8, MemoryUtil.memGetInt(record + 228));
            assertEquals(0, MemoryUtil.memGetInt(record + 232));
            assertEquals(0, MemoryUtil.memGetInt(record + 236));
            for (int i = 0; i < 128; i++) assertEquals((byte) 0x5a, MemoryUtil.memGetByte(record + i));
            for (int i = 240; i < 256; i++) assertEquals((byte) 0x5a, MemoryUtil.memGetByte(record + i));
        } finally { MemoryUtil.nmemFree(record); }
    }
}
