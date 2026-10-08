package org.soutalab.celtium.probe;

import org.lwjgl.opengl.EXTSemaphore;
import org.lwjgl.opengl.EXTSemaphoreFD;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.linux.UNISTD;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;

final class ExternalSemaphore implements AutoCloseable {
    private final VkInteropDevice owner;
    long semaphore;
    int glSemaphore;

    ExternalSemaphore(VkInteropDevice owner) {
        this.owner = owner;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            try {
                VkExportSemaphoreCreateInfo export = VkExportSemaphoreCreateInfo.calloc(stack).sType$Default()
                        .handleTypes(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT);
                VkSemaphoreCreateInfo create = VkSemaphoreCreateInfo.calloc(stack).sType$Default().pNext(export.address());
                LongBuffer handle = stack.mallocLong(1);
                VkInteropDevice.check(vkCreateSemaphore(owner.device, create, null, handle), "create external binary semaphore");
                semaphore = handle.get(0);
                VkSemaphoreGetFdInfoKHR info = VkSemaphoreGetFdInfoKHR.calloc(stack).sType$Default()
                        .semaphore(semaphore).handleType(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT);
                IntBuffer descriptor = stack.mallocInt(1);
                VkInteropDevice.check(KHRExternalSemaphoreFd.vkGetSemaphoreFdKHR(owner.device, info, descriptor), "export binary semaphore FD");
                int fd = descriptor.get(0);
                try {
                    glSemaphore = EXTSemaphore.glGenSemaphoresEXT();
                    EXTSemaphoreFD.glImportSemaphoreFdEXT(glSemaphore, EXTSemaphoreFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT, fd);
                    SharedImage.glCheck("import binary semaphore FD");
                    fd = -1;
                } finally {
                    if (fd >= 0) UNISTD.close(null, fd);
                }
            } catch (RuntimeException | LinkageError error) {
                close();
                throw error;
            }
        }
    }

    void signal(SharedImage color, SharedImage depth) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            EXTSemaphore.glSignalSemaphoreEXT(glSemaphore, stack.mallocInt(0),
                    stack.ints(color.texture, depth.texture),
                    stack.ints(EXTSemaphore.GL_LAYOUT_GENERAL_EXT, EXTSemaphore.GL_LAYOUT_GENERAL_EXT));
            SharedImage.glCheck("signal GL-to-Vulkan binary semaphore");
        }
    }

    void await(SharedImage color, SharedImage depth) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            EXTSemaphore.glWaitSemaphoreEXT(glSemaphore, stack.mallocInt(0),
                    stack.ints(color.texture, depth.texture),
                    stack.ints(EXTSemaphore.GL_LAYOUT_GENERAL_EXT, EXTSemaphore.GL_LAYOUT_GENERAL_EXT));
            SharedImage.glCheck("wait Vulkan-to-GL binary semaphore");
        }
    }

    @Override public void close() {
        if (glSemaphore != 0) { EXTSemaphore.glDeleteSemaphoresEXT(glSemaphore); glSemaphore = 0; }
        if (semaphore != 0) { vkDestroySemaphore(owner.device, semaphore, null); semaphore = 0; }
    }
}
