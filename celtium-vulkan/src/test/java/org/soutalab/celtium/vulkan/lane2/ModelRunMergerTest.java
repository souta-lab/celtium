package org.soutalab.celtium.vulkan.lane2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRunMergerTest {

    private static ModelRunMerger.Run run(long pipeline, int projection, int fog, int firstIndex) {
        return new ModelRunMerger.Run(
                new ModelRunMerger.RunKey(pipeline, projection, fog),
                6, 1, firstIndex, 0, 0);
    }

    @Test
    void susyScaleFrameCompressesToOneDrawPerKey() {
        // The plan's NA-3 measurement shape: 112 runs in a frame. Here they cycle through
        // 7 pipelines x 2 projections under one fog state = 14 keys.
        ModelRunMerger.Run[] runs = new ModelRunMerger.Run[112];
        for (int i = 0; i < runs.length; i++) {
            runs[i] = run(100 + (i % 7), i % 2, 1, i * 6);
        }
        ModelRunMerger.Result result = ModelRunMerger.merge(runs);
        assertEquals(112, result.commandCount());
        assertEquals(14, result.drawCalls());
        int total = 0;
        for (int count : result.groupCommandCounts()) {
            total += count;
        }
        assertEquals(112, total);
    }

    @Test
    void groupsFollowFirstAppearanceAndKeepCaptureOrderInside() {
        ModelRunMerger.Run[] runs = {
                run(7, 0, 0, 0),
                run(9, 0, 0, 60),
                run(7, 0, 0, 120),
                run(7, 1, 0, 180),
                run(9, 0, 0, 240),
        };
        ModelRunMerger.Result result = ModelRunMerger.merge(runs);
        assertEquals(3, result.drawCalls());
        assertEquals(new ModelRunMerger.RunKey(7, 0, 0), result.groupKeys()[0]);
        assertEquals(new ModelRunMerger.RunKey(9, 0, 0), result.groupKeys()[1]);
        assertEquals(new ModelRunMerger.RunKey(7, 1, 0), result.groupKeys()[2]);
        // Group 0 holds runs 0 and 2, in capture order: firstIndex 0 then 120.
        int base = result.groupCommandOffsets()[0] * ModelRunMerger.COMMAND_INTS;
        assertEquals(0, result.commands()[base + 2]);
        assertEquals(120, result.commands()[base + ModelRunMerger.COMMAND_INTS + 2]);
        assertEquals(2, result.groupCommandCounts()[0]);
        // Group 1 holds runs 1 and 4.
        base = result.groupCommandOffsets()[1] * ModelRunMerger.COMMAND_INTS;
        assertEquals(60, result.commands()[base + 2]);
        assertEquals(240, result.commands()[base + ModelRunMerger.COMMAND_INTS + 2]);
    }

    @Test
    void emptyAndSingleRunFrames() {
        ModelRunMerger.Result empty = ModelRunMerger.merge(new ModelRunMerger.Run[0]);
        assertEquals(0, empty.drawCalls());
        assertEquals(0, empty.commandCount());

        ModelRunMerger.Result single = ModelRunMerger.merge(
                new ModelRunMerger.Run[]{run(3, 0, 0, 42)});
        assertEquals(1, single.drawCalls());
        assertEquals(42, single.commands()[2]);
    }

    @Test
    void fogAndProjectionArePartOfTheKey() {
        ModelRunMerger.Run[] runs = {
                run(5, 0, 0, 0),
                run(5, 0, 1, 6),
                run(5, 1, 0, 12),
        };
        ModelRunMerger.Result result = ModelRunMerger.merge(runs);
        assertEquals(3, result.drawCalls());
        assertTrue(result.groupCommandCounts()[0] == 1);
    }
}
