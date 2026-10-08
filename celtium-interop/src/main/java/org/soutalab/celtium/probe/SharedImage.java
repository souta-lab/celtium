package org.soutalab.celtium.probe;

import com.google.gson.JsonObject;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.EXTMemoryObjectFD;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.linux.UNISTD;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL45C.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;

/** Dedicated, optimal image shared with GL through an opaque FD. */
final class SharedImage implements AutoCloseable {
    final VkInteropDevice owner;
    final int width, height, format, aspect, usage;
    final int flags = VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT;
    long image, memory, view, allocationBytes;
    int glMemory, texture;
    int externalFeatures;

    static SharedImage create(VkInteropDevice owner, int width, int height, boolean depth) {
        SharedImage result = new SharedImage(owner, width, height, depth);
        try {
            result.allocate(depth);
            return result;
        } catch (RuntimeException | LinkageError error) {
            result.close();
            throw error;
        }
    }

    private SharedImage(VkInteropDevice owner, int width, int height, boolean depth) {
        this.owner = owner;
        this.width = width;
        this.height = height;
        format = depth ? VK_FORMAT_D32_SFLOAT : VK_FORMAT_R8G8B8A8_UNORM;
        aspect = depth ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties features = VkFormatProperties.calloc(stack);
            vkGetPhysicalDeviceFormatProperties(owner.physical, format, features);
            int supported = features.optimalTilingFeatures();
            // EXT_external_objects requires all supported ordinary image usages
            // and mutable formats when GL texture views are available.
            int selected = VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
            if ((supported & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT) != 0) selected |= VK_IMAGE_USAGE_SAMPLED_BIT;
            if ((supported & VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT) != 0) selected |= VK_IMAGE_USAGE_STORAGE_BIT;
            if ((supported & VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT) != 0)
                selected |= VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT;
            if ((supported & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0)
                selected |= VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT;
            usage = selected;
        }
    }

    private void allocate(boolean depth) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceExternalImageFormatInfo external = VkPhysicalDeviceExternalImageFormatInfo.calloc(stack).sType$Default()
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT);
            VkPhysicalDeviceImageFormatInfo2 query = VkPhysicalDeviceImageFormatInfo2.calloc(stack).sType$Default().pNext(external.address())
                    .format(format).type(VK_IMAGE_TYPE_2D).tiling(VK_IMAGE_TILING_OPTIMAL).usage(usage).flags(flags);
            VkExternalImageFormatProperties externalProperties = VkExternalImageFormatProperties.calloc(stack).sType$Default();
            VkImageFormatProperties2 properties = VkImageFormatProperties2.calloc(stack).sType$Default().pNext(externalProperties.address());
            VkInteropDevice.check(vkGetPhysicalDeviceImageFormatProperties2(owner.physical, query, properties), "query external " + format);
            externalFeatures = externalProperties.externalMemoryProperties().externalMemoryFeatures();
            int required = VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT | VK_EXTERNAL_MEMORY_FEATURE_EXPORTABLE_BIT;
            if ((externalFeatures & required) != required
                    || (externalProperties.externalMemoryProperties().compatibleHandleTypes() & VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT) == 0)
                throw new IllegalStateException("External image cannot be imported and exported: " + format);
            if (width > properties.imageFormatProperties().maxExtent().width()
                    || height > properties.imageFormatProperties().maxExtent().height()
                    || (properties.imageFormatProperties().sampleCounts() & VK_SAMPLE_COUNT_1_BIT) == 0)
                throw new IllegalStateException("External image dimensions/sample count unsupported");
            VkExternalMemoryImageCreateInfo sharing = VkExternalMemoryImageCreateInfo.calloc(stack).sType$Default()
                    .handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT);
            VkImageCreateInfo create = VkImageCreateInfo.calloc(stack).sType$Default().pNext(sharing.address()).flags(flags)
                    .imageType(VK_IMAGE_TYPE_2D).format(format).mipLevels(1).arrayLayers(1).samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL).usage(usage).sharingMode(VK_SHARING_MODE_EXCLUSIVE).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            create.extent().set(width, height, 1);
            LongBuffer handle = stack.mallocLong(1);
            VkInteropDevice.check(vkCreateImage(owner.device, create, null, handle), "create external image");
            image = handle.get(0);
            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(owner.device, image, requirements);
            allocationBytes = requirements.size();
            VkMemoryDedicatedAllocateInfo dedicated = VkMemoryDedicatedAllocateInfo.calloc(stack).sType$Default().image(image);
            VkExportMemoryAllocateInfo export = VkExportMemoryAllocateInfo.calloc(stack).sType$Default()
                    .handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT).pNext(dedicated.address());
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default().pNext(export.address())
                    .allocationSize(allocationBytes).memoryTypeIndex(owner.memoryType(requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
            VkInteropDevice.check(vkAllocateMemory(owner.device, allocate, null, handle), "allocate dedicated external memory");
            memory = handle.get(0);
            VkInteropDevice.check(vkBindImageMemory(owner.device, image, memory, 0), "bind external image memory");
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack).sType$Default().image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D).format(format);
            viewInfo.subresourceRange().set(aspect, 0, 1, 0, 1);
            VkInteropDevice.check(vkCreateImageView(owner.device, viewInfo, null, handle), "create external image view");
            view = handle.get(0);

            glCheck("before external import");
            glMemory = EXTMemoryObject.glCreateMemoryObjectsEXT();
            EXTMemoryObject.glMemoryObjectParameterivEXT(glMemory, EXTMemoryObject.GL_DEDICATED_MEMORY_OBJECT_EXT, stack.ints(GL_TRUE));
            glCheck("dedicated GL memory parameter");
            VkMemoryGetFdInfoKHR fdInfo = VkMemoryGetFdInfoKHR.calloc(stack).sType$Default().memory(memory)
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT);
            IntBuffer descriptor = stack.mallocInt(1);
            VkInteropDevice.check(KHRExternalMemoryFd.vkGetMemoryFdKHR(owner.device, fdInfo, descriptor), "export image memory FD");
            int fd = descriptor.get(0);
            try {
                EXTMemoryObjectFD.glImportMemoryFdEXT(glMemory, allocationBytes, EXTMemoryObjectFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT, fd);
                glCheck("import image memory FD");
                fd = -1; // A successful GL import consumes the descriptor.
            } finally {
                if (fd >= 0) UNISTD.close(null, fd);
            }
            texture = glCreateTextures(GL_TEXTURE_2D);
            glTextureParameteri(texture, EXTMemoryObject.GL_TEXTURE_TILING_EXT, EXTMemoryObject.GL_OPTIMAL_TILING_EXT);
            EXTMemoryObject.glTextureStorageMem2DEXT(texture, 1, depth ? GL_DEPTH_COMPONENT32F : GL_RGBA8,
                    width, height, glMemory, 0);
            glCheck("external texture storage " + (depth ? "D32F" : "RGBA8"));
            glTextureParameteri(texture, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTextureParameteri(texture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTextureParameteri(texture, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTextureParameteri(texture, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTextureParameteri(texture, GL_TEXTURE_COMPARE_MODE, GL_NONE);
            glCheck("external texture parameters");
        }
    }

    JsonObject describe() {
        JsonObject result = new JsonObject();
        result.addProperty("format", format);
        result.addProperty("usage", usage);
        result.addProperty("flags", flags);
        result.addProperty("width", width);
        result.addProperty("height", height);
        result.addProperty("allocationBytes", allocationBytes);
        result.addProperty("dedicated", true);
        result.addProperty("externalFeatures", externalFeatures);
        result.addProperty("glImportVerified", true);
        return result;
    }

    static void glCheck(String operation) {
        int error = glGetError();
        if (error != GL_NO_ERROR) throw new IllegalStateException(operation + ": GL error 0x" + Integer.toHexString(error));
    }

    /** The caller must retire both APIs before closing an image used by the GPU. */
    @Override public void close() {
        if (texture != 0) { glDeleteTextures(texture); texture = 0; }
        if (glMemory != 0) { EXTMemoryObject.glDeleteMemoryObjectsEXT(glMemory); glMemory = 0; }
        if (view != 0) { vkDestroyImageView(owner.device, view, null); view = 0; }
        if (image != 0) { vkDestroyImage(owner.device, image, null); image = 0; }
        if (memory != 0) { vkFreeMemory(owner.device, memory, null); memory = 0; }
    }
}
