package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.ImageSlot;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderTargetRegistryTest {

    @Test
    void repeatBindClearAndLayoutQueriesPreserveBookkeeping() {
        RenderTargetRegistry registry = new RenderTargetRegistry();
        long mainId = RenderTargetRegistry.rawFramebufferIdentity(1);
        long chainId = RenderTargetRegistry.rawFramebufferIdentity(2);

        assertEquals(ImageSlot.MAIN, registry.noteBind(mainId, true, "main"));
        assertEquals(ImageSlot.CHAIN_PAIR, registry.noteBind(chainId, false, "chain"));
        // Repeat binds overwrite slot/main/generation cleanly in place.
        assertEquals(ImageSlot.CHAIN_PAIR, registry.noteBind(chainId, false, "chain"));
        assertEquals(ImageSlot.MAIN, registry.slotFor(mainId));
        assertEquals(ImageSlot.CHAIN_PAIR, registry.slotFor(chainId));

        registry.noteLayout(ImageSlot.MAIN, 2);
        registry.noteLayout(ImageSlot.MAIN, 5);
        assertEquals(5, registry.layoutOf(ImageSlot.MAIN));

        registry.notePairFramebuffer(ImageSlot.DEFAULT_PAIR, 1001L, 10L);
        registry.notePairFramebuffer(ImageSlot.DEFAULT_PAIR, 1002L, 10L);
        registry.notePairFramebuffer(ImageSlot.DEFAULT_PAIR, 1003L, 20L);
        assertEquals(1002L, registry.pairFramebuffer(ImageSlot.DEFAULT_PAIR, 10L));
        assertEquals(1003L, registry.pairFramebuffer(ImageSlot.DEFAULT_PAIR, 20L));
        assertEquals(2, registry.pairFramebufferCreations(ImageSlot.DEFAULT_PAIR));

        registry.noteChainPairFramebuffer(chainId, 2001L, 10L);
        registry.noteChainPairFramebuffer(chainId, 2002L, 20L);
        assertEquals(2001L, registry.chainPairFramebuffer(chainId, 10L));
        assertEquals(2, registry.chainPairFramebufferCreations(chainId));
        assertEquals(2, registry.chainPairFramebufferCreationsTotal());
        Collection<Long> allHandles = registry.allChainPairFramebufferHandles();
        assertTrue(allHandles.contains(2001L));
        assertTrue(allHandles.contains(2002L));

        registry.noteChainPairCleared(chainId, 41L, mainId);
        registry.noteChainPairCleared(chainId, 42L, chainId);
        assertEquals(42L, registry.chainPairClearedFrame(chainId));
        assertEquals(chainId, registry.chainPairClearContentIdentity(chainId));

        registry.noteChainPairLayout(chainId, 3);
        registry.noteChainPairLayout(chainId, 7);
        assertEquals(7, registry.chainPairLayoutOf(chainId));

        registry.beginFrame(1L);
        assertTrue(registry.noteClear(ImageSlot.MAIN, true, false));
        assertFalse(registry.noteClear(ImageSlot.CHAIN_PAIR, true, false));
        assertTrue(registry.noteClear(ImageSlot.CHAIN_PAIR, false, false));
        assertEquals("{MAIN=1, CHAIN_PAIR=1}", registry.clearsBySlot());

        registry.noteFrameTargetReplaced();
        assertEquals(-1, registry.layoutOf(ImageSlot.MAIN));
        assertEquals(-1, registry.chainPairLayoutOf(chainId));

        registry.forgetChainPair(chainId);
        assertEquals(-1L, registry.chainPairClearedFrame(chainId));
        assertEquals(0, registry.chainPairFramebufferCreations(chainId));
    }
}
