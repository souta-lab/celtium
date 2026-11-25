package org.soutalab.celtium.vulkan.compat;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.VulkanPostChain;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The post chain adapter's planning rules, which are the old executor's refusal semantics restated:
 * a chain whose input nothing supplies is refused by program name, the outline chain's external
 * target becomes one import, and "main" always resolves to the shared frame image. Pinned without a
 * device because a wrong plan here is silent in game - the chain would run on an empty target and
 * report success.
 */
class PostChainGraphAdapterTest {

    private static PostChainPass pass(String program, boolean inMain, int inId,
            boolean outMain, int outId) {
        PostChainPass pass = new PostChainPass();
        pass.program = program;
        pass.inIsMain = inMain;
        pass.inFbId = inId;
        pass.inW = 854;
        pass.inH = 480;
        pass.outIsMain = outMain;
        pass.outFbId = outId;
        pass.outW = 854;
        pass.outH = 480;
        pass.push = new float[1];
        pass.pushBytes = 4;
        return pass;
    }

    @Test
    void aTwoPassChainOverTheFrameImageConverts() {
        PostChainPass blur = pass("blur", true, 11, false, 22);
        PostChainPass blit = pass("blit", false, 22, true, 11);
        PostChainGraphAdapter.Result result = PostChainGraphAdapter.convert(List.of(blur, blit));
        assertTrue(result.accepted());
        assertNull(result.unsupplied());
        assertEquals(0, result.imports().size());
        assertEquals(2, result.nodes().size());
        assertEquals(PostChainGraphAdapter.FRAME, result.nodes().get(0).input());
        assertEquals(PostChainGraphAdapter.attachment(22), result.nodes().get(0).output());
        assertEquals(PostChainGraphAdapter.attachment(22), result.nodes().get(1).input());
        assertTrue(result.nodes().get(1).writesFrameImage());
    }

    @Test
    void theOutlineChainsExternalTargetBecomesOneImport() {
        PostChainPass sobel = pass("entity_outline", false, 77, false, 77);
        sobel.groupMainFbId = 77;
        sobel.groupMainTextureId = 5;
        sobel.groupMainW = 854;
        sobel.groupMainH = 480;
        PostChainGraphAdapter.Result result = PostChainGraphAdapter.convert(List.of(sobel));
        assertTrue(result.accepted());
        assertEquals(1, result.imports().size());
        PostChainGraphAdapter.Import imported = result.imports().get(0);
        assertEquals(PostChainGraphAdapter.attachment(77), imported.attachment());
        assertEquals(5, imported.textureId());
        assertEquals(PostChainGraphAdapter.attachment(77), result.nodes().get(0).input());
        assertEquals(PostChainGraphAdapter.attachment(77), result.nodes().get(0).output());
    }

    @Test
    void anInputNothingSuppliesRefusesTheChainByProgram() {
        PostChainPass spider = pass("spider", false, 99, true, 11);
        PostChainGraphAdapter.Result result = PostChainGraphAdapter.convert(List.of(spider));
        assertFalse(result.accepted());
        assertEquals("spider", result.unsupplied());
    }

    @Test
    void anAuxiliaryNamingTheGroupMainResolvesToTheFrameImage() {
        PostChainPass bits = pass("bits", true, 11, true, 11);
        bits.auxFbIds = new int[] {0};
        bits.auxW = new int[] {854};
        bits.auxH = new int[] {480};
        PostChainGraphAdapter.Result result = PostChainGraphAdapter.convert(List.of(bits));
        assertTrue(result.accepted());
        assertEquals(List.of(PostChainGraphAdapter.FRAME), result.nodes().get(0).auxInputs());
    }

    @Test
    void anUnsuppliedAuxiliaryRefusesTheChain() {
        PostChainPass bits = pass("bits", true, 11, true, 11);
        bits.auxFbIds = new int[] {55};
        bits.auxW = new int[] {854};
        bits.auxH = new int[] {480};
        PostChainGraphAdapter.Result result = PostChainGraphAdapter.convert(List.of(bits));
        assertFalse(result.accepted());
        assertEquals("bits", result.unsupplied());
    }

    @Test
    void anEmptyChainConvertsToNothingAndIsAccepted() {
        PostChainGraphAdapter.Result result = PostChainGraphAdapter.convert(List.of());
        assertTrue(result.accepted());
        assertEquals(0, result.nodes().size());
    }
}
