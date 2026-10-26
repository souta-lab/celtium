package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the compiler-emitted storage layout and the native writer; no GPU or image claims. */
class ModelInstanceFogAbiTest {
    @Test void compiledShaderStrideAndFogOffsetsMatchTheNativeRecord() {
        ByteBuffer code = SpirvShaders.load(SpirvShaders.MODEL_INSTANCE_VERTEX).order(ByteOrder.LITTLE_ENDIAN);
        try {
            Map<Integer, Integer> runtimeArrays = new HashMap<>();
            Map<Integer, Integer> strides = new HashMap<>();
            Map<Integer, Map<Integer, Integer>> offsets = new HashMap<>();
            for (int at = 20; at < code.limit();) {
                int header = code.getInt(at), words = header >>> 16, op = header & 65535;
                assertTrue(words > 0 && at + words * 4 <= code.limit());
                if (op == 29) runtimeArrays.put(code.getInt(at + 4), code.getInt(at + 8)); // OpTypeRuntimeArray
                if (op == 71 && words >= 4 && code.getInt(at + 8) == 6) { // ArrayStride
                    strides.put(code.getInt(at + 4), code.getInt(at + 12));
                }
                if (op == 72 && words >= 5 && code.getInt(at + 12) == 35) { // Member Offset
                    offsets.computeIfAbsent(code.getInt(at + 4), k -> new HashMap<>())
                            .put(code.getInt(at + 8), code.getInt(at + 16));
                }
                at += words * 4;
            }
            boolean found = false;
            for (var array : runtimeArrays.entrySet()) {
                Map<Integer, Integer> members = offsets.get(array.getValue());
                if (members == null || !Integer.valueOf(240).equals(members.get(12))) continue;
                assertEquals(VulkanModelInstances.RECORD_BYTES, strides.get(array.getKey()).intValue());
                assertEquals(256, members.get(13).intValue());
                assertEquals(272, members.get(14).intValue());
                assertEquals(288, members.get(15).intValue());
                assertEquals(304, members.get(16).intValue());
                found = true;
            }
            assertTrue(found, "compiled model SSBO must contain the fog tail");
        } finally { MemoryUtil.memFree(code); }
    }

    @Test void aReusedSecondRecordDoesNotRetainFogOrOverwriteTheFirstInstance() {
        ByteBuffer records = MemoryUtil.memAlloc(2 * VulkanModelInstances.RECORD_BYTES);
        try {
            MemoryUtil.memSet(MemoryUtil.memAddress(records), 0x5a, records.capacity());
            ImmediateFogState fog = new ImmediateFogState(new NativeRasterState());
            fog.parameter(0x0B65, 9729);
            fog.parameter(0x0B63, 2);
            fog.parameter(0x0B64, 16);
            fog.color(0.2f, 0.4f, 0.6f);
            fog.enabled(true);
            float[] view = {0, 1, 0, 0, -1, 0, 0, 0, 0, 0, 1, 0, 3, 4, 5, 1};
            VulkanModelInstances.writeFogRecord(MemoryUtil.memAddress(records), fog, view);
            assertEquals(16, records.getFloat(248));
            assertEquals(0.2f, records.getFloat(256));
            assertEquals(-1, records.getFloat(276));
            assertEquals(3, records.getFloat(284));
            assertEquals(4, records.getFloat(300));
            assertEquals(5, records.getFloat(316));
            fog.enabled(false);
            VulkanModelInstances.writeFogRecord(MemoryUtil.memAddress(records) + VulkanModelInstances.RECORD_BYTES, fog, view);
            for (int i = 0; i < 240; i++) {
                assertEquals((byte) 0x5a, records.get(i));
                assertEquals((byte) 0x5a, records.get(VulkanModelInstances.RECORD_BYTES + i));
            }
            for (int i = 240; i < 320; i++) assertEquals((byte) 0, records.get(VulkanModelInstances.RECORD_BYTES + i));
            assertEquals(0.2f, records.getFloat(256), "later owner changes do not mutate the queued instance");
        } finally { MemoryUtil.memFree(records); }
    }
}
