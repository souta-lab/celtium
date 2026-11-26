package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gated flush the S3R-3 landing-A wiring adds to the upload service: the owner's deferral
 * gate is consulted per DEFERRABLE request at batch-formation time, a refusal leaves the
 * refused request and everything behind it queued (counted as deferred, exactly like the
 * staging-space deferral), and IMMEDIATE requests never reach the gate. The submitter is the
 * same fake shape the skeleton's tests use.
 */
class TextureUploadGateTest {

    /** Records every batch and stamps them 100, 101, 102, ... in submission order. */
    private static final class FakeSubmitter implements TextureUploadService.BatchSubmitter {
        final List<List<TextureUploadService.ResolvedUpload>> batches =
                new ArrayList<List<TextureUploadService.ResolvedUpload>>();
        private long nextStamp = 100L;

        @Override
        public long submit(List<TextureUploadService.ResolvedUpload> batch) {
            this.batches.add(new ArrayList<TextureUploadService.ResolvedUpload>(batch));
            return this.nextStamp++;
        }
    }

    private static TextureUploadService.UploadRequest request(long image, long bytes,
            TextureUploadService.Urgency urgency) {
        return new TextureUploadService.UploadRequest(image, 0, 7, false, urgency,
                new TextureUploadService.Region(0, 0, 1, 0, 0, 0, 1, 1, 1), 0x1000L, bytes);
    }

    @Test
    void aGateRefusalAtTheHeadDefersTheWholeQueue() {
        TextureUploadService service = new TextureUploadService(4096L, 1L);
        FakeSubmitter submitter = new FakeSubmitter();
        TextureUploadService.UploadRequest head = request(11L, 100L, TextureUploadService.Urgency.DEFERRABLE);
        service.enqueue(head);
        service.enqueue(request(12L, 200L, TextureUploadService.Urgency.DEFERRABLE));

        TextureUploadService.FlushResult result = service.flush(0L, submitter, request -> false);

        assertEquals(0, result.batchesSubmitted());
        assertEquals(2, result.deferredRequests());
        assertEquals(300L, result.deferredBytes());
        assertEquals(300L, service.deferredBytesTotal());
        assertEquals(2, service.pendingRequests());
        assertTrue(service.isQueued(head));
    }

    @Test
    void aGateRefusalMidQueueSubmitsWhatCameBeforeIt() {
        TextureUploadService service = new TextureUploadService(4096L, 1L);
        FakeSubmitter submitter = new FakeSubmitter();
        TextureUploadService.UploadRequest refused = request(12L, 200L, TextureUploadService.Urgency.DEFERRABLE);
        service.enqueue(request(11L, 100L, TextureUploadService.Urgency.DEFERRABLE));
        service.enqueue(refused);
        service.enqueue(request(13L, 400L, TextureUploadService.Urgency.DEFERRABLE));

        TextureUploadService.FlushResult result =
                service.flush(0L, submitter, request -> request != refused);

        assertEquals(1, result.batchesSubmitted());
        assertEquals(1, result.requestsSubmitted());
        assertEquals(100L, result.bytesSubmitted());
        assertEquals(2, result.deferredRequests());
        assertEquals(600L, result.deferredBytes());
        assertEquals(2, service.pendingRequests());
        assertTrue(service.isQueued(refused));
    }

    @Test
    void theGateIsNeverConsultedForImmediateRequests() {
        TextureUploadService service = new TextureUploadService(4096L, 1L);
        FakeSubmitter submitter = new FakeSubmitter();
        List<TextureUploadService.UploadRequest> consulted =
                new ArrayList<TextureUploadService.UploadRequest>();
        service.enqueue(request(11L, 100L, TextureUploadService.Urgency.IMMEDIATE));
        service.enqueue(request(12L, 200L, TextureUploadService.Urgency.DEFERRABLE));

        TextureUploadService.FlushResult result = service.flush(0L, submitter, request -> {
            consulted.add(request);
            return true;
        });

        assertEquals(2, result.requestsSubmitted());
        assertEquals(1, consulted.size());
        assertEquals(TextureUploadService.Urgency.DEFERRABLE, consulted.get(0).urgency);
    }

    @Test
    void aRefusedRequestIsRejudgedOnTheNextFlush() {
        TextureUploadService service = new TextureUploadService(4096L, 1L);
        FakeSubmitter submitter = new FakeSubmitter();
        boolean[] allow = { false };
        TextureUploadService.UploadRequest only = request(11L, 100L, TextureUploadService.Urgency.DEFERRABLE);
        service.enqueue(only);

        service.flush(0L, submitter, request -> allow[0]);
        assertTrue(service.isQueued(only));

        allow[0] = true;
        TextureUploadService.FlushResult second = service.flush(0L, submitter, request -> allow[0]);
        assertEquals(1, second.requestsSubmitted());
        assertFalse(service.isQueued(only));
    }

    @Test
    void imageLevelsDefaultsToOneAndTheOverloadKeepsTheCount() {
        TextureUploadService.UploadRequest plain = request(11L, 100L, TextureUploadService.Urgency.DEFERRABLE);
        assertEquals(1, plain.imageLevels);
        TextureUploadService.UploadRequest chained = new TextureUploadService.UploadRequest(11L, 0, 7, true,
                TextureUploadService.Urgency.DEFERRABLE,
                new TextureUploadService.Region(0, 0, 1, 0, 0, 0, 1, 1, 1), 0x1000L, 100L, 5);
        assertEquals(5, chained.imageLevels);
    }
}
