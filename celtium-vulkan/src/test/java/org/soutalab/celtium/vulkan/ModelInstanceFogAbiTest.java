package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import static org.junit.jupiter.api.Assertions.*;

/** Checks compiler-emitted storage/push layouts. This is not GPU or image evidence. */
class ModelInstanceFogAbiTest {
    @Test void compiledStagesMatchTheNativePoseRecordAndRunConstants() {
        inspect(SpirvShaders.MODEL_INSTANCE_VERTEX, true);
        inspect(SpirvShaders.MODEL_INSTANCE_FRAGMENT, false);
    }

    private static void inspect(String shader, boolean hasRecords) {
        ByteBuffer code = SpirvShaders.load(shader).order(ByteOrder.LITTLE_ENDIAN);
        try {
            Map<Integer, Integer> arrays = new HashMap<>(), strides = new HashMap<>(), pushPointers = new HashMap<>();
            Set<Integer> pushVariables = new HashSet<>();
            Map<Integer, Map<Integer, Integer>> offsets = new HashMap<>();
            for (int at = 20; at < code.limit();) {
                int header = code.getInt(at), words = header >>> 16, op = header & 65535;
                assertTrue(words > 0 && at + words * 4 <= code.limit());
                if (op == 29) arrays.put(code.getInt(at + 4), code.getInt(at + 8)); // OpTypeRuntimeArray
                if (op == 32 && code.getInt(at + 8) == 9) { // OpTypePointer, PushConstant
                    pushPointers.put(code.getInt(at + 4), code.getInt(at + 12));
                }
                if (op == 59 && words >= 4 && code.getInt(at + 12) == 9) { // OpVariable
                    pushVariables.add(code.getInt(at + 4));
                }
                if (op == 71 && words >= 4 && code.getInt(at + 8) == 6) { // ArrayStride
                    strides.put(code.getInt(at + 4), code.getInt(at + 12));
                }
                if (op == 72 && words >= 5 && code.getInt(at + 12) == 35) { // Member Offset
                    offsets.computeIfAbsent(code.getInt(at + 4), k -> new HashMap<>())
                            .put(code.getInt(at + 8), code.getInt(at + 16));
                }
                at += words * 4;
            }
            assertEquals(1, pushVariables.size(), shader);
            Map<Integer, Integer> push = offsets.get(pushPointers.get(pushVariables.iterator().next()));
            assertNotNull(push);
            assertEquals(Map.of(0, 0, 1, 64, 2, 80), push, shader);
            assertEquals(96, ModelRunConstants.BYTES);
            if (hasRecords) {
                boolean found = false;
                for (var array : arrays.entrySet()) {
                    Map<Integer, Integer> members = offsets.get(array.getValue());
                    if (members == null || !Integer.valueOf(224).equals(members.get(11))) continue;
                    assertEquals(VulkanModelInstances.RECORD_BYTES, strides.get(array.getKey()).intValue());
                    assertEquals(256, VulkanModelInstances.RECORD_BYTES);
                    assertEquals(0, members.get(0).intValue());
                    assertEquals(240, members.get(12).intValue());
                    found = true;
                }
                assertTrue(found, "compiled model SSBO must contain a 256-byte pose record");
            }
        } finally { MemoryUtil.memFree(code); }
    }
}
