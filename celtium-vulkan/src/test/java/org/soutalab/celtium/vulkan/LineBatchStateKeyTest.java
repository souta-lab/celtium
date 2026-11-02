package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The line-batch flag has to be a flag, not a coincidence of the state hash.
 * <p>
 * {@code celtium$stateKey} is a 64-bit polynomial hash over the captured state, and the accept path marks a line draw by
 * setting bit 61 of the result. The prepare path then reads that bit to choose the line-list pipeline - so a state whose
 * <em>hash</em> set bit 61 was drawn as {@code VK_PRIMITIVE_TOPOLOGY_LINE_LIST}: a quad became three line segments, its
 * two opposing edges and its diagonal.
 * <p>
 * Measured (creative inventory, guiScale 3, legs r24b/r24d on AMD RX 6400): the hovered slot's half-white highlight is
 * {@code POSITION_COLOR}, four vertices, colour {@code 0x80FFFFFF}, state
 * {@code [blend=1 src=6 dst=7 depthTest=0 depthWrite=1 cull=1 alphaRef=0 depthFunc=515 mask=0x7 alphaFunc=0]} and its
 * hash is {@code 0x364d31f191fb43aa}, whose bit 61 is set - so the slot's highlight was drawn as three one-pixel lines
 * instead of a 50 % white rectangle. Poisoning the same quad to opaque magenta made exactly those three lines visible in
 * the presented picture, which is what identified the pipeline.
 */
class LineBatchStateKeyTest {

    /** The measured hovered-slot highlight state, in the captured array's own layout. */
    private static final int[] HIGHLIGHT = {
            1,                              // blend on
            6, 7,                           // SRC_ALPHA / ONE_MINUS_SRC_ALPHA (Vulkan enums)
            0,                              // depth test off
            1,                              // depth write on
            1,                              // cull on
            Float.floatToIntBits(0.0f),     // alpha reference (the test is disabled)
            515,                            // GL_LEQUAL, the last function the game set
            0,                              // lighting off
            1, 0,                           // alpha factors ONE / ZERO
            0x7,                            // colour write mask: RGB only (GlStateManager.colorMask(...,false))
            0, 0,                           // ADD / ADD
            0,                              // alpha function disabled
            0,                              // texture environment is not the combine
    };

    private static long rawHash(int formatId, int[] state) {
        long key = formatId;
        for (int value : state) {
            key = (key * 31) + value;
        }
        return key;
    }

    @Test
    void theMeasuredHighlightHashWouldHaveSetTheLineFlag() {
        // The defect, pinned as a number: without the mask this state is a line batch by accident.
        assertNotEquals(0L, rawHash(VulkanImmediateFormats.POSITION_COLOR, HIGHLIGHT)
                & VulkanTerrainGpuBackend.LINE_KEY_BIT,
                "the hash of the measured highlight state sets bit 61 - the mask is what keeps it a triangle batch");
    }

    @Test
    void theStateKeyNeverSetsTheLineFlag() {
        assertEquals(0L, VulkanTerrainGpuBackend.celtium$stateKey(VulkanImmediateFormats.POSITION_COLOR, HIGHLIGHT)
                & VulkanTerrainGpuBackend.LINE_KEY_BIT);
        // The contract, over the slots that vary: every combination of the boolean slots, both colour masks and a sweep
        // of the depth function and the alpha reference.
        for (int blend = 0; blend <= 1; blend++) {
            for (int depthTest = 0; depthTest <= 1; depthTest++) {
                for (int depthWrite = 0; depthWrite <= 1; depthWrite++) {
                    for (int cull = 0; cull <= 1; cull++) {
                        for (int mask : new int[] {0x7, 0xf, 0x0, 0x8}) {
                            for (int depthFunc = 512; depthFunc <= 519; depthFunc++) {
                                int[] state = HIGHLIGHT.clone();
                                state[0] = blend;
                                state[3] = depthTest;
                                state[4] = depthWrite;
                                state[5] = cull;
                                state[11] = mask;
                                state[7] = depthFunc;
                                long key = VulkanTerrainGpuBackend.celtium$stateKey(
                                        VulkanImmediateFormats.POSITION_COLOR, state);
                                assertEquals(0L, key & VulkanTerrainGpuBackend.LINE_KEY_BIT,
                                        "a triangle state must never carry the line flag: blend=" + blend
                                                + " depthTest=" + depthTest + " depthWrite=" + depthWrite
                                                + " cull=" + cull + " mask=0x" + Integer.toHexString(mask)
                                                + " depthFunc=" + depthFunc);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void aLineBatchAndATriangleBatchWithTheSameStateStillDiffer() {
        long triangle = VulkanTerrainGpuBackend.celtium$stateKey(VulkanImmediateFormats.POSITION_COLOR, HIGHLIGHT);
        long line = triangle | VulkanTerrainGpuBackend.LINE_KEY_BIT;
        assertNotEquals(triangle, line, "the flag must separate the two batches, or they would share a pipeline");
        assertTrue((line & VulkanTerrainGpuBackend.LINE_KEY_BIT) != 0L, "the line batch keeps its flag");
    }
}
