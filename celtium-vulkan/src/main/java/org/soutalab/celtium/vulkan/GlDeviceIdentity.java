package org.soutalab.celtium.vulkan;

import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the identity of the graphics device the current OpenGL context runs on.
 * <p>
 * Query only: no framebuffer, texture, binding or cached GL state is modified. The UUIDs are the contract the
 * Vulkan device must match, so a Vulkan backend can never render on a different physical GPU than the one the
 * game's OpenGL context uses.
 */
final class GlDeviceIdentity {
    final String vendor;
    final String renderer;
    final String version;
    final boolean memoryObjectFd;
    final boolean semaphoreFd;
    final String driverUuid;
    final List<String> deviceUuids = new ArrayList<String>();

    private GlDeviceIdentity() {
        GLCapabilities capabilities = GL.getCapabilities();
        this.vendor = GL11C.glGetString(GL11C.GL_VENDOR);
        this.renderer = GL11C.glGetString(GL11C.GL_RENDERER);
        this.version = GL11C.glGetString(GL11C.GL_VERSION);
        this.memoryObjectFd = capabilities.GL_EXT_memory_object_fd;
        this.semaphoreFd = capabilities.GL_EXT_semaphore_fd;
        String driver = null;
        List<String> devices = new ArrayList<String>();
        if (capabilities.GL_EXT_memory_object) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer uuid = stack.malloc(16);
                EXTMemoryObject.glGetUnsignedBytevEXT(EXTMemoryObject.GL_DRIVER_UUID_EXT, uuid);
                driver = hex(uuid);
                int count = GL11C.glGetInteger(EXTMemoryObject.GL_NUM_DEVICE_UUIDS_EXT);
                if (count < 0 || count > 16) {
                    throw new IllegalStateException("Invalid GL device UUID count: " + count);
                }
                for (int i = 0; i < count; i++) {
                    EXTMemoryObject.glGetUnsignedBytei_vEXT(EXTMemoryObject.GL_DEVICE_UUID_EXT, i, uuid);
                    devices.add(hex(uuid));
                }
            }
        }
        this.driverUuid = driver;
        this.deviceUuids.addAll(devices);
    }

    static GlDeviceIdentity inspect() {
        return new GlDeviceIdentity();
    }

    boolean hasDeviceUuids() {
        return !this.deviceUuids.isEmpty();
    }

    static String hex(ByteBuffer bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        StringBuilder result = new StringBuilder(bytes.remaining() * 2);
        for (int i = bytes.position(); i < bytes.limit(); i++) {
            int value = bytes.get(i) & 255;
            result.append(digits[value >>> 4]).append(digits[value & 15]);
        }
        return result.toString();
    }
}
