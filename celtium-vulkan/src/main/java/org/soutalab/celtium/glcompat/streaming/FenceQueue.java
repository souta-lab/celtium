package org.soutalab.celtium.glcompat.streaming;

/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import java.util.NoSuchElementException;

final class FenceQueue {
    private long[] ids = new long[8];
    private int[] bytes = new int[8];
    private int head;
    private int size;

    void enqueue(long id, int byteCount) {
        if (size == ids.length) grow();
        final int tail = (head + size) & (ids.length - 1);
        ids[tail] = id;
        bytes[tail] = byteCount;
        size++;
    }

    boolean isEmpty() { return size == 0; }

    int size() { return size; }

    long firstId() {
        if (size == 0) throw new NoSuchElementException();
        return ids[head];
    }

    int firstBytes() {
        if (size == 0) throw new NoSuchElementException();
        return bytes[head];
    }

    void dequeue() {
        if (size == 0) throw new NoSuchElementException();
        head = (head + 1) & (ids.length - 1);
        size--;
    }

    private void grow() {
        final int len = ids.length;
        final long[] newIds = new long[len * 2];
        final int[] newBytes = new int[len * 2];
        for (int i = 0; i < size; i++) {
            final int src = (head + i) & (len - 1);
            newIds[i] = ids[src];
            newBytes[i] = bytes[src];
        }
        ids = newIds;
        bytes = newBytes;
        head = 0;
    }
}
