package org.soutalab.celtium.vulkan.pack;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SpirvReflection over hand-assembled modules: interface variables, descriptor kinds
 * (including the storage-image distinction the Sampled operand carries), compute local
 * sizes and block member names.
 */
class SpirvReflectionTest {

    /** Tiny SPIR-V assembler for test modules. */
    private static final class Asm {
        private final List<Integer> words = new ArrayList<>(
                List.of(0x07230203, 0x00010300, 0, 1000, 0));

        Asm op(int opcode, int... operands) {
            words.add(((operands.length + 1) << 16) | opcode);
            for (int operand : operands) {
                words.add(operand);
            }
            return this;
        }

        Asm opStr(int opcode, int firstOperand, String text, int... rest) {
            List<Integer> operands = new ArrayList<>();
            operands.add(firstOperand);
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int total = bytes.length + 1;
            for (int i = 0; i < total; i += 4) {
                int word = 0;
                for (int b = 0; b < 4 && i + b < bytes.length; b++) {
                    word |= (bytes[i + b] & 0xFF) << (8 * b);
                }
                operands.add(word);
            }
            for (int operand : rest) {
                operands.add(operand);
            }
            return op(opcode, operands.stream().mapToInt(Integer::intValue).toArray());
        }

        Asm opStr2(int opcode, int first, int second, String text) {
            List<Integer> operands = new ArrayList<>();
            operands.add(first);
            operands.add(second);
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int total = bytes.length + 1;
            for (int i = 0; i < total; i += 4) {
                int word = 0;
                for (int b = 0; b < 4 && i + b < bytes.length; b++) {
                    word |= (bytes[i + b] & 0xFF) << (8 * b);
                }
                operands.add(word);
            }
            return op(opcode, operands.stream().mapToInt(Integer::intValue).toArray());
        }

        int[] build() {
            return words.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    private static final int OP_CAPABILITY = 17;
    private static final int OP_MEMORY_MODEL = 14;
    private static final int OP_ENTRY_POINT = 15;
    private static final int OP_EXECUTION_MODE = 16;
    private static final int OP_NAME = 5;
    private static final int OP_MEMBER_NAME = 6;
    private static final int OP_TYPE_FLOAT = 21;
    private static final int OP_TYPE_VECTOR = 23;
    private static final int OP_TYPE_IMAGE = 25;
    private static final int OP_TYPE_SAMPLED_IMAGE = 27;
    private static final int OP_TYPE_STRUCT = 30;
    private static final int OP_TYPE_POINTER = 32;
    private static final int OP_VARIABLE = 59;
    private static final int OP_DECORATE = 71;

    @Test
    void reflectsVertexInterfaceDescriptorsAndBlockMembers() {
        Asm asm = new Asm();
        asm.op(OP_CAPABILITY, 1); // Shader
        asm.op(OP_MEMORY_MODEL, 0, 1); // Logical, GLSL450
        // OpEntryPoint: model=0, entry=4, name="main", interface = 5, 6
        asm.words.add(((2 + 2 + 2) << 16) | OP_ENTRY_POINT);
        asm.words.add(0);
        asm.words.add(4);
        asm.words.add(0x6E69616D); // "main"
        asm.words.add(0);
        asm.words.add(5);
        asm.words.add(6);
        asm.opStr(OP_NAME, 5, "inPos");
        asm.opStr(OP_NAME, 6, "outUv");
        asm.opStr(OP_NAME, 7, "ubo");
        asm.opStr(OP_NAME, 8, "tex");
        asm.opStr2(OP_MEMBER_NAME, 40, 0, "iris_WeatherParams0");
        asm.opStr2(OP_MEMBER_NAME, 40, 1, "sunPosition");
        asm.op(OP_DECORATE, 5, 30, 0); // Location 0
        asm.op(OP_DECORATE, 6, 30, 1); // Location 1
        asm.op(OP_DECORATE, 7, 34, 0); // DescriptorSet 0
        asm.op(OP_DECORATE, 7, 33, 0); // Binding 0
        asm.op(OP_DECORATE, 8, 34, 0);
        asm.op(OP_DECORATE, 8, 33, 2); // Binding 2
        asm.op(OP_DECORATE, 40, 2); // Block
        asm.op(OP_TYPE_FLOAT, 20, 32);
        asm.op(OP_TYPE_VECTOR, 21, 20, 4);
        asm.op(OP_TYPE_VECTOR, 22, 20, 2);
        asm.op(OP_TYPE_IMAGE, 23, 20, 1, 0, 0, 0, 1, 0); // 2D, sampled=1
        asm.op(OP_TYPE_SAMPLED_IMAGE, 24, 23);
        asm.op(OP_TYPE_STRUCT, 40, 21, 21);
        asm.op(OP_TYPE_POINTER, 30, 1, 21); // Input vec4
        asm.op(OP_TYPE_POINTER, 31, 3, 22); // Output vec2
        asm.op(OP_TYPE_POINTER, 32, 2, 40); // Uniform struct
        asm.op(OP_TYPE_POINTER, 33, 0, 24); // UniformConstant sampled image
        asm.op(OP_VARIABLE, 30, 5, 1);
        asm.op(OP_VARIABLE, 31, 6, 3);
        asm.op(OP_VARIABLE, 32, 7, 2);
        asm.op(OP_VARIABLE, 33, 8, 0);

        SpirvReflection.ModuleInfo info = SpirvReflection.reflect(asm.build());

        assertEquals(1, info.inputs().size());
        assertEquals("inPos", info.inputs().get(0).name());
        assertEquals(0, info.inputs().get(0).location());
        assertEquals(4, info.inputs().get(0).vectorWidth());
        assertTrue(info.inputs().get(0).floatingPoint());
        assertEquals(1, info.outputs().size());
        assertEquals(1, info.outputs().get(0).location());
        assertEquals(2, info.outputs().get(0).vectorWidth());

        assertEquals(2, info.descriptors().size());
        SpirvReflection.DescriptorVariable ubo = info.descriptors().get(0);
        assertEquals("ubo", ubo.name());
        assertEquals(0, ubo.binding());
        assertEquals(SpirvReflection.DescriptorKind.UNIFORM_BUFFER, ubo.kind());
        assertEquals(40, ubo.pointeeTypeId());
        SpirvReflection.DescriptorVariable tex = info.descriptors().get(1);
        assertEquals("tex", tex.name());
        assertEquals(2, tex.binding());
        assertEquals(SpirvReflection.DescriptorKind.COMBINED_IMAGE_SAMPLER, tex.kind());

        assertEquals("iris_WeatherParams0", info.memberName(40, 0));
        assertEquals("sunPosition", info.memberName(40, 1));
    }

    @Test
    void reflectsComputeLocalSizeStorageImageAndStorageBuffer() {
        Asm asm = new Asm();
        asm.op(OP_CAPABILITY, 1);
        asm.op(OP_MEMORY_MODEL, 0, 1);
        asm.words.add(((1 + 2 + 2) << 16) | OP_ENTRY_POINT);
        asm.words.add(5); // GLCompute
        asm.words.add(4);
        asm.words.add(0x6E69616D);
        asm.words.add(0);
        asm.op(OP_EXECUTION_MODE, 4, 17, 8, 4, 2); // LocalSize 8 4 2
        asm.opStr(OP_NAME, 7, "img");
        asm.opStr(OP_NAME, 8, "ssbo");
        asm.op(OP_DECORATE, 7, 34, 0);
        asm.op(OP_DECORATE, 7, 33, 1);
        asm.op(OP_DECORATE, 8, 34, 0);
        asm.op(OP_DECORATE, 8, 33, 3);
        asm.op(OP_DECORATE, 40, 3); // BufferBlock
        asm.op(OP_TYPE_FLOAT, 20, 32);
        asm.op(OP_TYPE_IMAGE, 23, 20, 1, 0, 0, 0, 2, 0); // 2D, sampled=2 (storage)
        asm.op(OP_TYPE_STRUCT, 40, 20);
        asm.op(OP_TYPE_POINTER, 30, 0, 23); // UniformConstant image
        asm.op(OP_TYPE_POINTER, 31, 12, 40); // StorageBuffer struct
        asm.op(OP_VARIABLE, 30, 7, 0);
        asm.op(OP_VARIABLE, 31, 8, 12);

        SpirvReflection.ModuleInfo info = SpirvReflection.reflect(asm.build());

        assertEquals(List.of(8, 4, 2),
                List.of(info.computeLocalSize()[0], info.computeLocalSize()[1], info.computeLocalSize()[2]));
        assertEquals(2, info.descriptors().size());
        assertEquals(SpirvReflection.DescriptorKind.STORAGE_IMAGE, info.descriptors().get(0).kind());
        assertEquals(1, info.descriptors().get(0).binding());
        assertEquals(SpirvReflection.DescriptorKind.STORAGE_BUFFER, info.descriptors().get(1).kind());
        assertEquals(3, info.descriptors().get(1).binding());
    }
}
