package org.soutalab.celtium.probe;

import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

final class CurrentGlProbe {
    private CurrentGlProbe() { }

    /** Query only: no FBO, texture, binding or cached GL state is modified. */
    static ProbeReport.OpenGlDevice inspect() {
        GLCapabilities caps = GL.getCapabilities();
        ProbeReport.OpenGlDevice result = new ProbeReport.OpenGlDevice();
        result.vendor = GL11C.glGetString(GL11C.GL_VENDOR);
        result.renderer = GL11C.glGetString(GL11C.GL_RENDERER);
        result.version = GL11C.glGetString(GL11C.GL_VERSION);
        result.memoryObject = caps.GL_EXT_memory_object;
        result.memoryObjectFd = caps.GL_EXT_memory_object_fd;
        result.semaphore = caps.GL_EXT_semaphore;
        result.semaphoreFd = caps.GL_EXT_semaphore_fd;
        if (result.memoryObject) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer uuid = stack.malloc(16);
                EXTMemoryObject.glGetUnsignedBytevEXT(EXTMemoryObject.GL_DRIVER_UUID_EXT, uuid);
                result.driverUuid = hex(uuid);
                int count = GL11C.glGetInteger(EXTMemoryObject.GL_NUM_DEVICE_UUIDS_EXT);
                if (count < 0 || count > 16) {
                    throw new IllegalStateException("Invalid GL device UUID count: " + count);
                }
                for (int i = 0; i < count; i++) {
                    EXTMemoryObject.glGetUnsignedBytei_vEXT(EXTMemoryObject.GL_DEVICE_UUID_EXT, i, uuid);
                    result.deviceUuids.add(hex(uuid));
                }
            }
        }
        return result;
    }

    static String hex(ByteBuffer bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        StringBuilder result = new StringBuilder(bytes.remaining() * 2);
        for (int i = bytes.position(); i < bytes.limit(); i++) {
            int b = bytes.get(i) & 255;
            result.append(digits[b >>> 4]).append(digits[b & 15]);
        }
        return result.toString();
    }
}
