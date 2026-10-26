package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelRunConstantsTest {
    @Test void packetSpanAndSnapshotsAreIndependentOfLaterOwnerChanges() {
        ByteBuffer packet = ByteBuffer.allocate(128).order(ByteOrder.nativeOrder());
        for (int i = 0; i < packet.capacity(); i++) packet.put(i, (byte) 0x5a);
        float[] projection = {2,0,0,0, 0,3,0,0, 0,0,-1,-1, 0,0,-0.2f,0};
        ImmediateFogState owner = new ImmediateFogState(new NativeRasterState());
        owner.enabled(true); owner.parameter(0x0B65, 9729); owner.parameter(0x0B64, 16);
        owner.color(0.2f, 0.4f, 0.6f);
        ModelRunConstants.write(packet, projection, owner.snapshot());
        owner.color(0.9f, 0.8f, 0.7f);
        projection[0] = 10;
        assertEquals(0, packet.position());
        assertEquals(ModelRunConstants.BYTES, packet.remaining());
        assertEquals(2, packet.getFloat(0));
        assertEquals(0.2f, packet.getFloat(64));
        assertEquals(16, packet.getFloat(88));
        assertEquals(3, (int) packet.getFloat(92) & 7);
        packet.limit(packet.capacity());
        for (int i = ModelRunConstants.BYTES; i < packet.capacity(); i++) assertEquals((byte) 0x5a, packet.get(i));
    }

    @Test void fogDisabledRewritesTheWholePreviousPayload() {
        ByteBuffer packet = ByteBuffer.allocate(ModelRunConstants.BYTES);
        float[] projection = {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
        ModelRunConstants.write(packet, projection, new float[] {3,1,0,16, 1,0,0,1});
        ModelRunConstants.write(packet, projection, null);
        for (int i = 64; i < ModelRunConstants.BYTES; i++) assertEquals((byte) 0, packet.get(i));
        assertEquals(1, packet.getFloat(0));
    }
}
