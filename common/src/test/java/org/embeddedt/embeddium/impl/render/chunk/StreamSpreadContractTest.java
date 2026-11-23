package org.embeddedt.embeddium.impl.render.chunk;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QUEUE 728 (F1): the stream-spread deferral must preserve the commit contract of
 * {@code RenderSectionManager.uploadChunks} - a result is committed and its output deleted only when it is
 * processed, never when it is merely queued. The backend-side upload budget queue cannot offer this (its
 * caller commits and deletes the whole batch), which is why the deferral lives in the manager; this contract
 * keeps it there and keeps the delete tied to the processed slice.
 */
class StreamSpreadContractTest {
    private static String managerSource() throws IOException {
        Path path = Path.of("src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java");
        if (!Files.exists(path)) {
            path = Path.of("common/" + path);
        }
        return Files.readString(path);
    }

    @Test
    void spreadingIsOnByDefaultAndBranchesInUploadChunks() throws IOException {
        String source = managerSource();
        assertTrue(source.contains("System.getProperty(\"celtium.streamSpread\")"),
                "the spread switch must read the celtium.streamSpread system property");
        assertTrue(source.contains("value == null || Boolean.parseBoolean(value)"),
                "the spread switch must default on (alpha proving ground) with =false as the control arm");
        int uploadStart = source.indexOf("public void uploadChunks()");
        int uploadEnd = source.indexOf("public final void tickVisibleRenders()", uploadStart);
        String upload = source.substring(uploadStart, uploadEnd);
        assertTrue(upload.contains("if (STREAM_SPREAD)"), "uploadChunks must branch on the spread switch");
        assertTrue(upload.contains("celtium$spreadUploadChunks(results)"),
                "the spread branch must route through the spreading processor");
    }

    @Test
    void fastPathKeepsTheUnspreadCallShapeWhenCreditCoversTheArrival() throws IOException {
        String source = managerSource();
        int spreadStart = source.indexOf("celtium$spreadUploadChunks");
        int enqueue = source.indexOf("streamSpreadQueue.addAll(results)", spreadStart);
        String head = source.substring(spreadStart, enqueue);
        assertTrue(head.contains("streamSpreadQueue.isEmpty()"),
                "the fast path must require an empty deferral queue");
        assertTrue(head.contains("STREAM_SPREAD_PER_HOLDER_UPPER_MICROS"),
                "the fast path must compare credit against the conservative per-holder estimate");
        assertTrue(head.contains("celtium$processSpreadBatch(results)"),
                "the fast path must process the whole arrival in one batch call");
    }

    @Test
    void onlyProcessedSlicesAreDeletedAndTeardownDrainsTheQueue() throws IOException {
        String source = managerSource();
        int spreadStart = source.indexOf("celtium$spreadUploadChunks");
        int spreadEnd = source.indexOf("celtium$deleteDeferredSpreadResults", spreadStart);
        String spread = source.substring(spreadStart, spreadEnd);
        assertTrue(spread.contains("result.output().delete()"),
                "a processed slice must delete its results' outputs, like the unspread path");
        assertTrue(source.contains("destroyStage(failure, this::celtium$deleteDeferredSpreadResults)"),
                "teardown must delete still-deferred outputs instead of leaking their mesh buffers");
    }
}
