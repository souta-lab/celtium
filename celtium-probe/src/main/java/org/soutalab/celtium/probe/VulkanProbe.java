package org.soutalab.celtium.probe;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;

final class VulkanProbe {
    private VulkanProbe() { }

    static void inspect(ProbeReport report) {
        int loaderVersion = VK.getInstanceVersionSupported();
        report.vulkanLoaderVersion = version(loaderVersion);
        if (loaderVersion < VK_API_VERSION_1_1) {
            report.reasons.add("Vulkan 1.1 is needed to query physical-device UUIDs and external object capabilities.");
            return;
        }
        VkInstance instance = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo app = VkApplicationInfo.calloc(stack).sType$Default()
                    .pApplicationName(stack.UTF8("Celtium capability probe"))
                    .applicationVersion(1).apiVersion(Math.min(loaderVersion, VK12.VK_API_VERSION_1_2));
            VkInstanceCreateInfo create = VkInstanceCreateInfo.calloc(stack).sType$Default().pApplicationInfo(app);
            PointerBuffer pointer = stack.mallocPointer(1);
            check(vkCreateInstance(create, null, pointer), "vkCreateInstance");
            instance = new VkInstance(pointer.get(0), create);
            IntBuffer count = stack.mallocInt(1);
            check(vkEnumeratePhysicalDevices(instance, count, null), "vkEnumeratePhysicalDevices(count)");
            if (count.get(0) == 0) return;
            PointerBuffer devices = stack.mallocPointer(count.get(0));
            check(vkEnumeratePhysicalDevices(instance, count, devices), "vkEnumeratePhysicalDevices");
            for (int i = 0; i < count.get(0); i++) {
                VkPhysicalDevice device = new VkPhysicalDevice(devices.get(i), instance);
                report.vulkanDevices.add(inspectDevice(device));
            }
        } finally {
            if (instance != null) vkDestroyInstance(instance, null);
        }
    }

    private static ProbeReport.VulkanDevice inspectDevice(VkPhysicalDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ProbeReport.VulkanDevice result = new ProbeReport.VulkanDevice();
            VkPhysicalDeviceIDProperties ids = VkPhysicalDeviceIDProperties.calloc(stack).sType$Default();
            VkPhysicalDeviceProperties2 props = VkPhysicalDeviceProperties2.calloc(stack).sType$Default().pNext(ids.address());
            vkGetPhysicalDeviceProperties2(device, props);
            result.name = props.properties().deviceNameString();
            int api = props.properties().apiVersion();
            result.apiVersion = version(api);
            result.apiMajor = VK_VERSION_MAJOR(api);
            result.apiMinor = VK_VERSION_MINOR(api);
            result.deviceType = props.properties().deviceType();
            result.deviceUuid = CurrentGlProbe.hex(ids.deviceUUID());
            result.driverUuid = CurrentGlProbe.hex(ids.driverUUID());

            Set<String> extensions = deviceExtensions(device, stack);
            result.externalMemoryFd = extensions.contains(KHRExternalMemoryFd.VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME);
            result.externalSemaphoreFd = extensions.contains(KHRExternalSemaphoreFd.VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME);
            if (result.externalSemaphoreFd) {
                VkPhysicalDeviceExternalSemaphoreInfo info = VkPhysicalDeviceExternalSemaphoreInfo.calloc(stack).sType$Default()
                        .handleType(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT);
                VkExternalSemaphoreProperties semaphore = VkExternalSemaphoreProperties.calloc(stack).sType$Default();
                vkGetPhysicalDeviceExternalSemaphoreProperties(device, info, semaphore);
                int features = semaphore.externalSemaphoreFeatures();
                boolean compatible = (semaphore.compatibleHandleTypes() & VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT) != 0;
                result.binarySemaphoreImportable = compatible && (features & VK_EXTERNAL_SEMAPHORE_FEATURE_IMPORTABLE_BIT) != 0;
                result.binarySemaphoreExportable = compatible && (features & VK_EXTERNAL_SEMAPHORE_FEATURE_EXPORTABLE_BIT) != 0;
            }
            if (result.externalMemoryFd) {
                result.imageFormats.add(imageFormat(device, "R8G8B8A8_UNORM", VK_FORMAT_R8G8B8A8_UNORM, false));
                result.imageFormats.add(imageFormat(device, "R8G8B8A8_SRGB", VK_FORMAT_R8G8B8A8_SRGB, false));
                result.imageFormats.add(imageFormat(device, "X8_D24_UNORM_PACK32", VK_FORMAT_X8_D24_UNORM_PACK32, true));
                result.imageFormats.add(imageFormat(device, "D24_UNORM_S8_UINT", VK_FORMAT_D24_UNORM_S8_UINT, true));
                result.imageFormats.add(imageFormat(device, "D32_SFLOAT", VK_FORMAT_D32_SFLOAT, true));
                result.imageFormats.add(imageFormat(device, "D32_SFLOAT_S8_UINT", VK_FORMAT_D32_SFLOAT_S8_UINT, true));
            }
            return result;
        }
    }

    private static Set<String> deviceExtensions(VkPhysicalDevice device, MemoryStack stack) {
        IntBuffer count = stack.mallocInt(1);
        check(vkEnumerateDeviceExtensionProperties(device, (String) null, count, null), "vkEnumerateDeviceExtensionProperties(count)");
        // Modern drivers expose hundreds of extensions; their structs can exceed the default native stack.
        try (VkExtensionProperties.Buffer props = VkExtensionProperties.calloc(count.get(0))) {
            check(vkEnumerateDeviceExtensionProperties(device, (String) null, count, props), "vkEnumerateDeviceExtensionProperties");
            Set<String> result = new HashSet<String>();
            for (int i = 0; i < count.get(0); i++) result.add(props.get(i).extensionNameString());
            return result;
        }
    }

    private static ProbeReport.ImageFormat imageFormat(VkPhysicalDevice device, String name, int format, boolean depth) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ProbeReport.ImageFormat result = new ProbeReport.ImageFormat();
            result.name = name;
            result.vulkanFormat = format;
            VkPhysicalDeviceExternalImageFormatInfo external = VkPhysicalDeviceExternalImageFormatInfo.calloc(stack).sType$Default()
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT);
            int usage = VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT |
                    (depth ? VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT : VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
            VkPhysicalDeviceImageFormatInfo2 info = VkPhysicalDeviceImageFormatInfo2.calloc(stack).sType$Default()
                    .pNext(external.address()).format(format).type(VK_IMAGE_TYPE_2D).tiling(VK_IMAGE_TILING_OPTIMAL).usage(usage);
            VkExternalImageFormatProperties externalProps = VkExternalImageFormatProperties.calloc(stack).sType$Default();
            VkImageFormatProperties2 props = VkImageFormatProperties2.calloc(stack).sType$Default().pNext(externalProps.address());
            result.queryResult = vkGetPhysicalDeviceImageFormatProperties2(device, info, props);
            if (result.queryResult == VK_SUCCESS) {
                VkExternalMemoryProperties memory = externalProps.externalMemoryProperties();
                int features = memory.externalMemoryFeatures();
                result.importable = (features & VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT) != 0;
                result.exportable = (features & VK_EXTERNAL_MEMORY_FEATURE_EXPORTABLE_BIT) != 0;
                result.dedicatedAllocationRequired = (features & VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT) != 0;
                result.opaqueFdCompatible = (memory.compatibleHandleTypes() & VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT) != 0;
                result.singleSample = (props.imageFormatProperties().sampleCounts() & VK_SAMPLE_COUNT_1_BIT) != 0;
            } else if (result.queryResult != VK_ERROR_FORMAT_NOT_SUPPORTED) {
                throw new IllegalStateException("External image format query failed for " + name + ": " + result.queryResult);
            }
            return result;
        }
    }

    private static String version(int version) {
        return VK_VERSION_MAJOR(version) + "." + VK_VERSION_MINOR(version) + "." + VK_VERSION_PATCH(version);
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) throw new IllegalStateException(operation + " returned " + result);
    }
}
