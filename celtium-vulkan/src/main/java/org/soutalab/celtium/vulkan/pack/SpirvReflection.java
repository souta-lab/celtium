/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.vulkan.pack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal SPIR-V reflection reader for the pack executor: entry-point interface variables
 * (location, name, component type), descriptor bindings (set, binding, kind) and compute local
 * sizes. The pack compiler's contracts deliberately carry no reflection metadata, so the
 * executor recovers exactly the facts it needs from the module words themselves. Pure function
 * of the word array; no Vulkan calls.
 */
public final class SpirvReflection {

    // Opcodes used below.
    private static final int OP_NAME = 5;
    private static final int OP_MEMBER_NAME = 6;
    private static final int OP_ENTRY_POINT = 15;
    private static final int OP_EXECUTION_MODE = 16;
    private static final int OP_TYPE_FLOAT = 21;
    private static final int OP_TYPE_IMAGE = 25;
    private static final int OP_TYPE_SAMPLER = 26;
    private static final int OP_TYPE_SAMPLED_IMAGE = 27;
    private static final int OP_TYPE_ARRAY = 28;
    private static final int OP_TYPE_RUNTIME_ARRAY = 29;
    private static final int OP_TYPE_STRUCT = 30;
    private static final int OP_TYPE_POINTER = 32;
    private static final int OP_VARIABLE = 59;
    private static final int OP_DECORATE = 71;

    // Decorations.
    private static final int DECORATION_BLOCK = 2;
    private static final int DECORATION_BUFFER_BLOCK = 3;
    private static final int DECORATION_LOCATION = 30;
    private static final int DECORATION_BINDING = 33;
    private static final int DECORATION_DESCRIPTOR_SET = 34;

    // Execution modes.
    private static final int EXEC_LOCAL_SIZE = 17;

    // Storage classes.
    private static final int STORAGE_UNIFORM_CONSTANT = 0;
    private static final int STORAGE_INPUT = 1;
    private static final int STORAGE_UNIFORM = 2;
    private static final int STORAGE_OUTPUT = 3;
    private static final int STORAGE_STORAGE_BUFFER = 12;

    /** The descriptor flavour a reflected variable binds as. */
    public enum DescriptorKind {
        UNIFORM_BUFFER, STORAGE_BUFFER, COMBINED_IMAGE_SAMPLER, SAMPLED_IMAGE, STORAGE_IMAGE, SAMPLER
    }

    /** One reflected descriptor variable. {@code name} may be empty when the module carries no OpName. */
    public record DescriptorVariable(String name, int set, int binding, DescriptorKind kind,
                                     int pointeeTypeId) {
    }

    /** One reflected stage interface variable (vertex input / fragment output). */
    public record InterfaceVariable(String name, int location, int vectorWidth, boolean floatingPoint) {
    }

    /** Everything the executor reflects from one module. */
    public record ModuleInfo(
            List<DescriptorVariable> descriptors,
            List<InterfaceVariable> inputs,
            List<InterfaceVariable> outputs,
            int[] computeLocalSize,
            Map<Integer, Map<Integer, String>> structMemberNames) {

        public ModuleInfo {
            descriptors = List.copyOf(descriptors);
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            computeLocalSize = computeLocalSize == null ? null : computeLocalSize.clone();
            structMemberNames = Map.copyOf(structMemberNames);
        }

        /** The OpMemberName of one struct member, or null when the module does not name it. */
        public String memberName(int structTypeId, int memberIndex) {
            Map<Integer, String> members = structMemberNames.get(structTypeId);
            return members == null ? null : members.get(memberIndex);
        }
    }

    private SpirvReflection() {
    }

    public static ModuleInfo reflect(int[] words) {
        Map<Integer, String> names = new LinkedHashMap<>();
        Map<Integer, Map<Integer, String>> memberNames = new LinkedHashMap<>();
        Map<Integer, Integer> imageSampled = new LinkedHashMap<>();
        Map<Integer, Integer> typeKinds = new LinkedHashMap<>();
        Map<Integer, int[]> vectorTypes = new LinkedHashMap<>();
        Map<Integer, Integer> pointerPointee = new LinkedHashMap<>();
        Map<Integer, Integer> arrayElement = new LinkedHashMap<>();
        Map<Integer, Integer> structDecorations = new LinkedHashMap<>();
        Map<Integer, int[]> variableDecorations = new LinkedHashMap<>();
        Map<Integer, int[]> variables = new LinkedHashMap<>(); // id -> [resultTypeId, storageClass]
        List<Integer> entryInterfaceIds = new ArrayList<>();
        int[] localSize = null;

        int i = 5; // skip header
        while (i < words.length) {
            int wordCount = (words[i] >>> 16) & 0xFFFF;
            int opcode = words[i] & 0xFFFF;
            if (wordCount == 0) {
                break;
            }
            switch (opcode) {
                case OP_NAME -> names.put(words[i + 1], readString(words, i + 2, i + wordCount));
                case OP_MEMBER_NAME -> memberNames
                        .computeIfAbsent(words[i + 1], key -> new LinkedHashMap<>())
                        .put(words[i + 2], readString(words, i + 3, i + wordCount));
                case OP_ENTRY_POINT -> {
                    for (int k = i + 3 + stringWordLength(words, i + 3, i + wordCount); k < i + wordCount; k++) {
                        entryInterfaceIds.add(words[k]);
                    }
                }
                case OP_EXECUTION_MODE -> {
                    if (words[i + 2] == EXEC_LOCAL_SIZE) {
                        localSize = new int[]{words[i + 3], words[i + 4], words[i + 5]};
                    }
                }
                case OP_TYPE_FLOAT -> typeKinds.put(words[i + 1], OP_TYPE_FLOAT);
                case OP_TYPE_IMAGE -> {
                    typeKinds.put(words[i + 1], OP_TYPE_IMAGE);
                    // The Sampled operand: 1 = sampled-only, 2 = storage.
                    imageSampled.put(words[i + 1], wordCount >= 8 ? words[i + 7] : 0);
                }
                case OP_TYPE_SAMPLER -> typeKinds.put(words[i + 1], OP_TYPE_SAMPLER);
                case OP_TYPE_SAMPLED_IMAGE -> typeKinds.put(words[i + 1], OP_TYPE_SAMPLED_IMAGE);
                case OP_TYPE_ARRAY, OP_TYPE_RUNTIME_ARRAY -> {
                    typeKinds.put(words[i + 1], opcode);
                    arrayElement.put(words[i + 1], words[i + 2]);
                }
                case OP_TYPE_STRUCT -> typeKinds.put(words[i + 1], OP_TYPE_STRUCT);
                case 22 -> { // OpTypeInt
                    typeKinds.put(words[i + 1], 22);
                    vectorTypes.put(words[i + 1], new int[]{1, 0});
                }
                case 23 -> { // OpTypeVector: id, componentType, count
                    vectorTypes.put(words[i + 1], new int[]{words[i + 3], words[i + 2]});
                }
                case OP_TYPE_POINTER -> pointerPointee.put(words[i + 1], words[i + 3]);
                case OP_DECORATE -> {
                    int target = words[i + 1];
                    int decoration = words[i + 2];
                    if (decoration == DECORATION_BLOCK || decoration == DECORATION_BUFFER_BLOCK) {
                        structDecorations.put(target, decoration);
                    } else if (decoration == DECORATION_LOCATION || decoration == DECORATION_BINDING
                            || decoration == DECORATION_DESCRIPTOR_SET) {
                        int[] slots = variableDecorations.computeIfAbsent(target,
                                key -> new int[]{-1, -1, -1});
                        if (decoration == DECORATION_LOCATION) {
                            slots[0] = words[i + 3];
                        } else if (decoration == DECORATION_BINDING) {
                            slots[1] = words[i + 3];
                        } else {
                            slots[2] = words[i + 3];
                        }
                    }
                }
                case OP_VARIABLE -> variables.put(words[i + 2], new int[]{words[i + 1], words[i + 3]});
                default -> {
                }
            }
            i += wordCount;
        }

        List<DescriptorVariable> descriptors = new ArrayList<>();
        List<InterfaceVariable> inputs = new ArrayList<>();
        List<InterfaceVariable> outputs = new ArrayList<>();
        for (Map.Entry<Integer, int[]> entry : variables.entrySet()) {
            int id = entry.getKey();
            int typeId = entry.getValue()[0];
            int storage = entry.getValue()[1];
            int pointee = pointerPointee.getOrDefault(typeId, -1);
            int[] deco = variableDecorations.getOrDefault(id, new int[]{-1, -1, -1});
            String name = names.getOrDefault(id, "");
            if (storage == STORAGE_UNIFORM_CONSTANT || storage == STORAGE_UNIFORM
                    || storage == STORAGE_STORAGE_BUFFER) {
                DescriptorKind kind = descriptorKind(pointee, typeKinds, arrayElement, structDecorations,
                        storage, imageSampled);
                if (kind != null && deco[1] >= 0) {
                    descriptors.add(new DescriptorVariable(name, Math.max(deco[2], 0), deco[1], kind,
                            unwrap(pointee, arrayElement)));
                }
            } else if ((storage == STORAGE_INPUT || storage == STORAGE_OUTPUT)
                    && entryInterfaceIds.contains(id) && deco[0] >= 0) {
                int baseType = unwrap(pointee, arrayElement);
                int[] vector = vectorTypes.get(baseType);
                int width = vector != null ? vector[0] : 1;
                boolean floating = vector != null
                        ? typeKinds.getOrDefault(vector[1], -1) == OP_TYPE_FLOAT
                        : typeKinds.getOrDefault(baseType, -1) == OP_TYPE_FLOAT;
                InterfaceVariable variable = new InterfaceVariable(name, deco[0], width, floating);
                if (storage == STORAGE_INPUT) {
                    inputs.add(variable);
                } else {
                    outputs.add(variable);
                }
            }
        }
        descriptors.sort((a, b) -> Integer.compare(a.binding(), b.binding()));
        inputs.sort((a, b) -> Integer.compare(a.location(), b.location()));
        outputs.sort((a, b) -> Integer.compare(a.location(), b.location()));
        return new ModuleInfo(descriptors, inputs, outputs, localSize, memberNames);
    }

    private static DescriptorKind descriptorKind(int typeId, Map<Integer, Integer> typeKinds,
                                                 Map<Integer, Integer> arrayElement,
                                                 Map<Integer, Integer> structDecorations,
                                                 int storage, Map<Integer, Integer> imageSampled) {
        int base = unwrap(typeId, arrayElement);
        int kind = typeKinds.getOrDefault(base, -1);
        return switch (kind) {
            case OP_TYPE_SAMPLED_IMAGE -> DescriptorKind.COMBINED_IMAGE_SAMPLER;
            case OP_TYPE_IMAGE -> imageSampled.getOrDefault(base, 0) == 2
                    ? DescriptorKind.STORAGE_IMAGE : DescriptorKind.SAMPLED_IMAGE;
            case OP_TYPE_SAMPLER -> DescriptorKind.SAMPLER;
            case OP_TYPE_STRUCT -> {
                int deco = structDecorations.getOrDefault(base, -1);
                yield deco == DECORATION_BUFFER_BLOCK || storage == STORAGE_STORAGE_BUFFER
                        ? DescriptorKind.STORAGE_BUFFER : DescriptorKind.UNIFORM_BUFFER;
            }
            default -> null;
        };
    }

    private static int unwrap(int typeId, Map<Integer, Integer> arrayElement) {
        int current = typeId;
        while (arrayElement.containsKey(current)) {
            current = arrayElement.get(current);
        }
        return current;
    }

    private static String readString(int[] words, int start, int end) {
        StringBuilder out = new StringBuilder();
        outer:
        for (int w = start; w < end; w++) {
            int word = words[w];
            for (int b = 0; b < 4; b++) {
                int c = (word >>> (8 * b)) & 0xFF;
                if (c == 0) {
                    break outer;
                }
                out.append((char) c);
            }
        }
        return out.toString();
    }

    private static int stringWordLength(int[] words, int start, int end) {
        int length = 0;
        outer:
        for (int w = start; w < end; w++) {
            length++;
            int word = words[w];
            for (int b = 0; b < 4; b++) {
                if (((word >>> (8 * b)) & 0xFF) == 0) {
                    break outer;
                }
            }
        }
        return length;
    }
}
