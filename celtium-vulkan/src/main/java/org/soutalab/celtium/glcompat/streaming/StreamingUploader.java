package org.soutalab.celtium.glcompat.streaming;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.GLStateManager;
import org.soutalab.celtium.glcompat.backend.DirectGlExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

import static org.soutalab.celtium.glcompat.gtnh.bytebuf.MemoryUtilities.memAddress0;
import static org.soutalab.celtium.glcompat.backend.DirectGlExecutor.EXECUTOR;
import static org.soutalab.celtium.glcompat.gtnh.bytebuf.MemoryUtilities.memCopy;


public final class StreamingUploader {

    private static final Logger LOGGER = LogManager.getLogger("StreamingUploader");

    public enum UploadStrategy {
        BUFFER_DATA,
        BUFFER_SUB_DATA,
        MAP_BUFFER_RANGE
    }

    private static final int MAP_WRITE_INVALIDATE_BUFFER = GL30.GL_MAP_WRITE_BIT | GL30.GL_MAP_INVALIDATE_BUFFER_BIT;

    /** Set once a mapping failure has been reported; throttles the fallback warning to a single line. */
    private static boolean mapFailureLogged;

    public static int upload(ByteBuffer data, int capacity) {
        return upload(GLStateManager.getInitConfig().getStreamingUploadStrategy(), data, capacity);
    }

    public static int upload(UploadStrategy strategy, ByteBuffer data, int capacity) {
        return upload(EXECUTOR, strategy, data, capacity);
    }

    // Package-private for tests; production callers go through upload(...) which supplies the active backend.
    static int upload(DirectGlExecutor backend, UploadStrategy strategy, ByteBuffer data, int capacity) {
        switch (strategy) {
            case BUFFER_DATA -> {
                backend.bufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
                return data.remaining();
            }
            case BUFFER_SUB_DATA -> {
                if (data.remaining() > capacity) {
                    backend.bufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
                    return data.remaining();
                }
                backend.bufferSubData(GL15.GL_ARRAY_BUFFER, 0, data);
                return capacity;
            }
            case MAP_BUFFER_RANGE -> {
                if (data.remaining() > capacity) {
                    backend.bufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
                    return data.remaining();
                }
                final int dataSize = data.remaining();
                final long dst = backend.mapBufferRangeAddress(GL15.GL_ARRAY_BUFFER, 0, dataSize, MAP_WRITE_INVALIDATE_BUFFER);
                if (dst == 0L) {
                    logMapFailureOnce();
                    backend.bufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
                    return dataSize;
                }
                memCopy(memAddress0(data), dst, dataSize);
                backend.unmapBuffer(GL15.GL_ARRAY_BUFFER);
                return capacity;
            }
            default -> throw new UnsupportedOperationException();
        }
    }

    private static void logMapFailureOnce() {
        if (!mapFailureLogged) {
            mapFailureLogged = true;
            LOGGER.warn("glMapBufferRange failed; falling back to bufferData for this streaming upload");
        }
    }
}
