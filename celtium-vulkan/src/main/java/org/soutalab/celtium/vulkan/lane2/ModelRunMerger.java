package org.soutalab.celtium.vulkan.lane2;

import java.util.Arrays;

/**
 * NA-3 run merging for the model replay path (design V040-FINAL-PLAN section 4.2, stage
 * S3-a item 3): model replays arrive as many small runs - the Susy measurement behind the
 * plan counted 112 runs per frame - and every run used to become its own indirect draw.
 * Runs that share the NA-3 key, (pipeline, projection, fog), are one drawing state, so this
 * merger regroups a frame's runs into one multi-draw-indirect segment per key: the commands
 * of a key become consecutive entries of a single indirect buffer, issued with one
 * {@code vkCmdDrawIndexedIndirect} per group instead of one call per run.
 *
 * <p>Merging regroups; it never reorders inside a key (a key's commands keep their capture
 * order, which is the order their blending and depth writes observe), and it never merges
 * across keys. Groups are emitted in first-appearance order of their key, so the frame's
 * overall progression from one drawing state to the next is the one the capture produced.
 *
 * <p>The command layout is {@code VkDrawIndexedIndirectCommand}'s: (indexCount,
 * instanceCount, firstIndex, vertexOffset, firstInstance), five ints per command.
 */
public final class ModelRunMerger {

    /** Ints per indirect command, matching {@code VkDrawIndexedIndirectCommand}. */
    public static final int COMMAND_INTS = 5;

    private ModelRunMerger() {
    }

    /** The NA-3 merge key: one pipeline under one projection and one fog state. */
    public record RunKey(long pipelineHandle, int projectionId, int fogId) {
    }

    /**
     * One replay run before merging: its key and the indirect-draw arguments its geometry
     * contributes (instanceCount is the run's own; merged commands keep it).
     */
    public record Run(RunKey key, int indexCount, int instanceCount, int firstIndex,
                      int vertexOffset, int firstInstance) {
    }

    /**
     * The merged frame: {@code groups} in first-appearance order, each naming the key and
     * the consecutive command range {@code [commandOffset, commandOffset + commandCount)}
     * of {@code commands} (five ints per command, in capture order within the group).
     */
    public record Result(RunKey[] groupKeys, int[] groupCommandOffsets, int[] groupCommandCounts,
                         int[] commands) {

        /** Total indirect commands across all groups (equals the input run count). */
        public int commandCount() {
            return this.commands.length / COMMAND_INTS;
        }

        /** Draw calls the merged frame issues: one per group. */
        public int drawCalls() {
            return this.groupKeys.length;
        }
    }

    /** Merges a frame's runs. The input is not modified. */
    public static Result merge(Run[] runs) {
        // Group assignment by first appearance, with a small open-addressing table keyed on
        // the record's hash; run counts per frame are in the low hundreds, so this stays a
        // linear pass with a tiny constant.
        int capacity = 16;
        while (capacity < runs.length * 2) {
            capacity *= 2;
        }
        RunKey[] table = new RunKey[capacity];
        int[] groupOfSlot = new int[capacity];
        RunKey[] groupKeys = new RunKey[runs.length];
        int[] groupOfRun = new int[runs.length];
        int groups = 0;
        for (int i = 0; i < runs.length; i++) {
            RunKey key = runs[i].key();
            int slot = (key.hashCode() & 0x7FFF_FFFF) % capacity;
            while (table[slot] != null && !table[slot].equals(key)) {
                slot = (slot + 1) % capacity;
            }
            if (table[slot] == null) {
                table[slot] = key;
                groupOfSlot[slot] = groups;
                groupKeys[groups++] = key;
            }
            groupOfRun[i] = groupOfSlot[slot];
        }
        int[] counts = new int[groups];
        for (int group : groupOfRun) {
            counts[group]++;
        }
        int[] offsets = new int[groups];
        int total = 0;
        for (int g = 0; g < groups; g++) {
            offsets[g] = total;
            total += counts[g];
        }
        int[] commands = new int[total * COMMAND_INTS];
        int[] cursor = Arrays.copyOf(offsets, groups);
        for (int i = 0; i < runs.length; i++) {
            int at = cursor[groupOfRun[i]]++ * COMMAND_INTS;
            Run run = runs[i];
            commands[at] = run.indexCount();
            commands[at + 1] = run.instanceCount();
            commands[at + 2] = run.firstIndex();
            commands[at + 3] = run.vertexOffset();
            commands[at + 4] = run.firstInstance();
        }
        return new Result(Arrays.copyOf(groupKeys, groups), offsets, counts, commands);
    }
}
