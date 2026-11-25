package org.soutalab.celtium.vulkan.upload;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UploadQueueTest {

    private static final class RecordingSink implements UploadQueue.CommandSink {
        final List<String> events = new ArrayList<>();

        @Override
        public void copyBuffer(long stagingOffset, long dstBufferHandle, long dstOffset, int sizeBytes) {
            events.add("BUF:" + stagingOffset + "->" + dstBufferHandle + "@" + dstOffset + ":" + sizeBytes);
        }

        @Override
        public void transitionImageToTransferDst(long imageHandle) {
            events.add("IMG_TO_DST:" + imageHandle);
        }

        @Override
        public void copyBufferToImage(long stagingOffset, long dstImageHandle, int mipLevel,
                                      int x, int y, int width, int height, int sizeBytes) {
            events.add("IMG:" + stagingOffset + "->" + dstImageHandle + "[" + x + "," + y + "," + width + "x" + height + "]");
        }

        @Override
        public void transitionImageToShaderRead(long imageHandle) {
            events.add("IMG_TO_READ:" + imageHandle);
        }

        @Override
        public void transferToGraphicsBarrier() {
            events.add("BARRIER");
        }
    }

    @Test
    void budgetDefaultsMatchSpecAndCarryOverExcess() {
        assertEquals(4 * 1024 * 1024, UploadQueue.forIntegratedGpu().frameBudgetBytes());
        assertEquals(16 * 1024 * 1024, UploadQueue.forDiscreteGpu().frameBudgetBytes());

        UploadQueue queue = new UploadQueue(1024);
        queue.enqueue(UploadRequest.bufferCopy(UploadRequest.Priority.BACKGROUND, 0, 1L, 0, 600));
        queue.enqueue(UploadRequest.bufferCopy(UploadRequest.Priority.BACKGROUND, 600, 1L, 600, 600));

        RecordingSink sink1 = new RecordingSink();
        UploadQueue.FrameUploadReport r1 = queue.drainFrame(sink1);
        assertEquals(600, r1.bytesUploaded());
        assertEquals(1, r1.bufferCopies());
        assertEquals(1, r1.deferredRemaining());
        assertTrue(r1.budgetExhausted());
        assertEquals(List.of("BUF:0->1@0:600", "BARRIER"), sink1.events);

        RecordingSink sink2 = new RecordingSink();
        UploadQueue.FrameUploadReport r2 = queue.drainFrame(sink2);
        assertEquals(600, r2.bytesUploaded());
        assertEquals(1, r2.bufferCopies());
        assertEquals(0, r2.deferredRemaining());
        assertFalse(r2.budgetExhausted());
    }

    @Test
    void immediatePriorityPreemptsBackground() {
        UploadQueue queue = new UploadQueue(1024);
        queue.enqueue(UploadRequest.bufferCopy(UploadRequest.Priority.BACKGROUND, 0, 10L, 0, 800));
        queue.enqueue(UploadRequest.bufferCopy(UploadRequest.Priority.IMMEDIATE, 800, 20L, 0, 800));

        RecordingSink sink = new RecordingSink();
        UploadQueue.FrameUploadReport report = queue.drainFrame(sink);
        assertEquals(800, report.bytesUploaded());
        assertEquals(1, report.deferredRemaining());
        assertEquals("BUF:800->20@0:800", sink.events.get(0));
    }

    @Test
    void oversizedSingleItemIsNotStarved() {
        UploadQueue queue = new UploadQueue(1024);
        queue.enqueue(UploadRequest.bufferCopy(UploadRequest.Priority.IMMEDIATE, 0, 1L, 0, 4096));

        RecordingSink sink = new RecordingSink();
        UploadQueue.FrameUploadReport report = queue.drainFrame(sink);
        assertEquals(4096, report.bytesUploaded());
        assertEquals(1, report.bufferCopies());
        assertEquals(0, report.deferredRemaining());
    }

    @Test
    void animatedSpritesCoalesceAndBatchImageTransitions() {
        UploadQueue queue = new UploadQueue(64 * 1024);
        long atlasHandle = 777L;

        // Same animated sprite sub-rect updated twice before P0 drain -> should coalesce to stagingOffset=2048
        assertFalse(queue.enqueue(UploadRequest.imageSubrectCopy(
                UploadRequest.Priority.IMMEDIATE, 1024, atlasHandle, 0, 16, 32, 16, 16, 1024)));
        assertTrue(queue.enqueue(UploadRequest.imageSubrectCopy(
                UploadRequest.Priority.IMMEDIATE, 2048, atlasHandle, 0, 16, 32, 16, 16, 1024)));

        // Different sprite on same atlas -> batched under same single image transition pair
        assertFalse(queue.enqueue(UploadRequest.imageSubrectCopy(
                UploadRequest.Priority.IMMEDIATE, 3072, atlasHandle, 0, 48, 32, 16, 16, 1024)));

        assertEquals(1L, queue.totalCoalescedSprites());

        RecordingSink sink = new RecordingSink();
        UploadQueue.FrameUploadReport report = queue.drainFrame(sink);
        assertEquals(2048, report.bytesUploaded());
        assertEquals(2, report.imageCopies());
        assertEquals(1, report.distinctImagesTouched());
        assertEquals(List.of(
                "IMG_TO_DST:777",
                "IMG:2048->777[16,32,16x16]",
                "IMG:3072->777[48,32,16x16]",
                "IMG_TO_READ:777",
                "BARRIER"
        ), sink.events);
    }

    @Test
    void emptyFrameEmitsZeroBarriers() {
        UploadQueue queue = UploadQueue.forIntegratedGpu();
        RecordingSink sink = new RecordingSink();
        UploadQueue.FrameUploadReport report = queue.drainFrame(sink);
        assertEquals(0, report.bytesUploaded());
        assertTrue(sink.events.isEmpty());
    }
}
