package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The batch sink's push read, pinned by source reading in the house style (see
 * {@link BindlessArrayWiringTest}): a batch entry whose family reads part of its fragment
 * input from the push range is pushed its own record's bytes, read on the host at draw
 * time. The records live in the draw-record store's own home - the dedicated records pool
 * since the ring records separation - so the read must take the record's address from the
 * store. Computing the address in the frame ring's mapping at the store's offset reads
 * geometry bytes as the fragment's tint, alpha reference and fog: that was the
 * panel-background defect (the video-settings dirt bands sampled the right texture with
 * the right index and still presented saturated garbage colours that changed from run to
 * run, because the frame ring's content at the bogus address did). The behaviour that can
 * run headless - the store's address naming the written record and following a slice
 * growth - lives in {@code InstanceDataStoreTest}; what only exists as a connection
 * between the sink and the store is pinned here.
 */
class BatchSinkPushReadWiringTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java";

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative)).replace("\r\n", "\n");
    }

    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        return direct;
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int at = source.indexOf(needle); at >= 0; at = source.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }

    /** The sink pushes bytes read through the store's own record address. */
    @Test
    void sinkReadsThePushThroughTheStore() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains("drawRecords.recordAddress(firstInstance)"),
                "the batch sink reads an entry's push bytes from the store's record address");
    }

    /**
     * The store's dynamic offset belongs to descriptor binds (its set names the store's own
     * buffer, so the offset is at home there) and to nothing else: every remaining use in
     * the backend is a {@code vkCmdBindDescriptorSets} dynamic offset. A fifth use - the
     * sink's old host-side address computation in the frame ring - is the defect coming
     * back.
     */
    @Test
    void dynamicOffsetIsOnlyABindOffset() throws IOException {
        String backend = read(BACKEND);
        assertEquals(4, occurrences(backend, "drawRecords.descriptorDynamicOffset()"),
                "the dynamic offset is read only for descriptor binds");
        assertEquals(4, occurrences(backend, "descriptorDynamicOffset()"),
                "no other receiver reads a dynamic offset in the backend");
    }
}
