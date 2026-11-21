package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The readable-mapping guard's refresh is bounded by its interval, not by a lifetime budget.
 * <p>
 * A lifetime cap of 64 reads stood here before: a long run churns its mappings, so past the cap the guard
 * answered from a mapping layout the process no longer had, and client-memory draws the layout would have
 * cleared were refused for the rest of the run. The interval throttle is the whole bound.
 */
class ReadableMappingRefreshTest {

    @Test
    void aRefreshPastTheOldLifetimeCapStillReads() throws Exception {
        int[] reads = {0};
        ReadableMemoryRanges.Provider provider = new ReadableMemoryRanges.Provider() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public ReadableMemoryRanges.Range[] read() {
                reads[0]++;
                return new ReadableMemoryRanges.Range[] {new ReadableMemoryRanges.Range(0x1000L, 0x2000L)};
            }
        };
        CeltiumGlStub stub = new CeltiumGlStub(provider);
        // The state a long run reaches: hundreds of refreshes already done, the last one long ago.
        var refreshes = CeltiumGlStub.class.getDeclaredField("readableMappingRefreshes");
        refreshes.setAccessible(true);
        refreshes.setInt(stub, 500);
        var lastRefresh = CeltiumGlStub.class.getDeclaredField("readableMappingLastRefreshNanos");
        lastRefresh.setAccessible(true);
        lastRefresh.setLong(stub, 0L);
        var method = CeltiumGlStub.class.getDeclaredMethod("celtium$refreshReadableMappings");
        method.setAccessible(true);

        ReadableMemoryRanges.Range[] ranges = (ReadableMemoryRanges.Range[]) method.invoke(stub);
        assertEquals(1, ranges.length, "the refresh still reads after hundreds of earlier refreshes");
        assertEquals(1, reads[0]);

        method.invoke(stub);
        assertEquals(1, reads[0], "a second refresh inside the interval is throttled, not re-read");
    }
}
