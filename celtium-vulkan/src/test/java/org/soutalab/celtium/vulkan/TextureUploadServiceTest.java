package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The upload queue's flush policy, without a device: FIFO order is never rearranged, a batch
 * ends at the first request that does not fit, a deferrable head defers (and is counted), and
 * an immediate head reports the timeline value to await instead of deferring. The submitter is
 * a fake that hands out increasing timeline stamps and remembers every batch it was given, so
 * the tests read the service's behaviour off what the device seam would have recorded.
 */
class TextureUploadServiceTest {

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

        long lastStamp() {
            return this.nextStamp - 1L;
        }
    }

    private static TextureUploadService.UploadRequest request(long image, long bytes,
            TextureUploadService.Urgency urgency) {
        return new TextureUploadService.UploadRequest(image, 0, 7, false, urgency,
                new TextureUploadService.Region(0, 0, 1, 0, 0, 0, 1, 1, 1), 0x1000L, bytes);
    }

    @Test
    void aFlushSubmitsTheWholeQueueAsOneFifoBatch() {
        TextureUploadService service = new TextureUploadService(4096L, 1L);
        FakeSubmitter submitter = new FakeSubmitter();
        service.enqueue(request(11L, 100L, TextureUploadService.Urgency.DEFERRABLE));
        service.enqueue(request(12L, 200L, TextureUploadService.Urgency.IMMEDIATE));
        service.enqueue(request(13L, 300L, TextureUploadService.Urgency.DEFERRABLE));

        TextureUploadService.FlushResult result = service.flush(0L, submitter);

        assertEquals(1, result.batchesSubmitted());
        assertEquals(3, result.requestsSubmitted());
        assertEquals(600L, result.bytesSubmitted());
        assertEquals(0, result.deferredRequests());
        assertEquals(0L, result.blockedOnValue());
        assertEquals(0, service.pendingRequests());
        assertEquals(1L, service.submits());
        assertEquals(600L, service.uploadedBytes());
        List<TextureUploadService.ResolvedUpload> batch = submitter.batches.get(0);
        assertEquals(11L, batch.get(0).request.image);
        assertEquals(12L, batch.get(1).request.image);
        assertEquals(13L, batch.get(2).request.image);
        assertEquals(0L, batch.get(0).stagingOffset);
        assertEquals(100L, batch.get(1).stagingOffset);
        assertEquals(300L, batch.get(2).stagingOffset);
    }

    @Test
    void aBatchEndsAtTheFirstRequestThatDoesNotFitAndTheRestDefers() {
        TextureUploadService service = new TextureUploadService(1024L, 1L);
        FakeSubmitter submitter = new FakeSubmitter();
        service.enqueue(request(11L, 400L, TextureUploadService.Urgency.DEFERRABLE));
        service.enqueue(request(12L, 400L, TextureUploadService.Urgency.DEFERRABLE));
        service.enqueue(request(13L, 400L, TextureUploadService.Urgency.DEFERRABLE));

        TextureUploadService.FlushResult first = service.flush(0L, submitter);

        assertEquals(1, first.batchesSubmitted());
        assertEquals(2, first.requestsSubmitted(), "two 400-byte slices fit the 1024-byte pool");
        assertEquals(1, first.deferredRequests());
        assertEquals(400L, first.deferredBytes());
        assertEquals(400L, service.deferredBytesTotal());
        assertEquals(1, service.pendingRequests());

        TextureUploadService.FlushResult second = service.flush(submitter.lastStamp(), submitter);

        assertEquals(1, second.batchesSubmitted());
        assertEquals(1, second.requestsSubmitted(), "the deferred upload goes out once its space completes");
        assertEquals(0, service.pendingRequests());
        assertEquals(2L, service.submits());
        assertEquals(1200L, service.uploadedBytes());
    }

    @Test
    void anImmediateHeadReportsTheValueToAwaitInsteadOfDeferring() {
        TextureUploadService service = new TextureUploadService(1024L, 1L);
        FakeSubmitter submitter = new FakeSubmitter();
        service.enqueue(request(11L, 600L, TextureUploadService.Urgency.IMMEDIATE));
        service.flush(0L, submitter);
        long stamp = submitter.lastStamp();

        service.enqueue(request(12L, 600L, TextureUploadService.Urgency.IMMEDIATE));
        TextureUploadService.FlushResult blocked = service.flush(0L, submitter);

        assertEquals(0, blocked.batchesSubmitted());
        assertEquals(0, blocked.deferredRequests(), "an immediate upload is never counted as deferred");
        assertEquals(stamp, blocked.blockedOnValue(),
                "the flush names the completion that frees the oldest staging data");
        assertEquals(0L, service.deferredBytesTotal());
        assertEquals(1, service.pendingRequests());

        TextureUploadService.FlushResult retried = service.flush(stamp, submitter);
        assertEquals(1, retried.requestsSubmitted());
        assertEquals(0L, retried.blockedOnValue());
    }

    @Test
    void aDeferrableHeadNeverReportsBlockedOn() {
        TextureUploadService service = new TextureUploadService(1024L, 1L);
        FakeSubmitter submitter = new FakeSubmitter();
        service.enqueue(request(11L, 600L, TextureUploadService.Urgency.DEFERRABLE));
        service.flush(0L, submitter);

        service.enqueue(request(12L, 600L, TextureUploadService.Urgency.DEFERRABLE));
        TextureUploadService.FlushResult result = service.flush(0L, submitter);

        assertEquals(0L, result.blockedOnValue());
        assertEquals(1, result.deferredRequests());
        assertEquals(600L, result.deferredBytes());
    }

    @Test
    void slicesAreReusedOnceTheirBatchCompletes() {
        TextureUploadService service = new TextureUploadService(1024L, 1L);
        FakeSubmitter submitter = new FakeSubmitter();
        service.enqueue(request(11L, 512L, TextureUploadService.Urgency.IMMEDIATE));
        service.flush(0L, submitter);
        assertEquals(0L, submitter.batches.get(0).get(0).stagingOffset);

        service.enqueue(request(12L, 512L, TextureUploadService.Urgency.IMMEDIATE));
        service.flush(submitter.lastStamp(), submitter);
        assertEquals(0L, submitter.batches.get(1).get(0).stagingOffset,
                "the completed batch's slice is the first one handed out again");
    }

    @Test
    void anOversizeUploadIsRefusedAtEnqueue() {
        TextureUploadService service = new TextureUploadService(1024L, 1L);
        assertThrows(IllegalArgumentException.class,
                () -> service.enqueue(request(11L, 1025L, TextureUploadService.Urgency.DEFERRABLE)));
        assertEquals(0, service.pendingRequests());
    }

    @Test
    void anEmptyFlushSubmitsNothing() {
        TextureUploadService service = new TextureUploadService(1024L, 1L);
        FakeSubmitter submitter = new FakeSubmitter();
        TextureUploadService.FlushResult result = service.flush(0L, submitter);
        assertEquals(0, result.batchesSubmitted());
        assertTrue(submitter.batches.isEmpty());
        assertEquals(0L, service.submits());
    }

    @Test
    void requestValidationRejectsNonsense() {
        assertThrows(IllegalArgumentException.class, () -> request(0L, 16L,
                TextureUploadService.Urgency.DEFERRABLE));
        assertThrows(IllegalArgumentException.class, () -> request(11L, 0L,
                TextureUploadService.Urgency.DEFERRABLE));
        assertThrows(IllegalArgumentException.class,
                () -> new TextureUploadService.Region(0, 0, 1, 0, 0, 0, 0, 1, 1));
    }
}
