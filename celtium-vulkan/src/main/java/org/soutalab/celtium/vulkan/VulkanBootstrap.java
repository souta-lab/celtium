package org.soutalab.celtium.vulkan;

import com.google.gson.GsonBuilder;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRExternalMemoryFd;
import org.lwjgl.vulkan.KHRExternalSemaphoreFd;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkExternalImageFormatProperties;
import org.lwjgl.vulkan.VkExternalMemoryProperties;
import org.lwjgl.vulkan.VkExternalSemaphoreProperties;
import org.lwjgl.vulkan.VkImageFormatProperties2;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceExternalImageFormatInfo;
import org.lwjgl.vulkan.VkPhysicalDeviceExternalSemaphoreInfo;
import org.lwjgl.vulkan.VkPhysicalDeviceIDProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceImageFormatInfo2;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.vulkan.KHRExternalMemoryFd.VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreFd.VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;

/**
 * P3 step 1: create a Vulkan logical device for exactly the GPU the current OpenGL context runs on.
 * <p>
 * This is a bootstrap and a check, not a renderer. It creates an instance, matches the physical device by the
 * OpenGL device UUID, verifies the interoperability contract already proven in P1b, creates one logical device
 * with a graphics/transfer queue and then destroys everything again. No terrain is uploaded or drawn, and the
 * terrain backend keeps using OpenGL. Every failure is reported as a reason; nothing falls back to another GPU.
 * <p>
 * The class is loaded reflectively by the mod so that an installation without Vulkan bindings keeps working.
 */
public final class VulkanBootstrap {
    private static final int REQUIRED_API_MAJOR = 1;
    private static final int REQUIRED_API_MINOR = 2;

    private VulkanBootstrap() {
    }

    public static VulkanDeviceReport bootstrap() {
        VulkanDeviceReport report = new VulkanDeviceReport();
        try {
            report.lwjglVersion = org.lwjgl.Version.getVersion();
        } catch (RuntimeException | LinkageError error) {
            report.warnings.add("LWJGL version unavailable: " + describe(error));
        }
        try {
            report.openGl = openGlIdentity();
        } catch (RuntimeException | LinkageError error) {
            report.reasons.add("OpenGL device identity unavailable: " + describe(error));
            return finish(report);
        }
        if (report.openGl.deviceUuids.isEmpty()) {
            report.reasons.add("The current OpenGL context does not expose device UUIDs"
                    + " (GL_EXT_memory_object / GL_EXT_memory_object_fd missing).");
            return finish(report);
        }
        if (!report.openGl.memoryObjectFd || !report.openGl.semaphoreFd) {
            report.reasons.add("The current OpenGL context lacks GL_EXT_memory_object_fd or GL_EXT_semaphore_fd.");
            return finish(report);
        }
        try {
            inspectVulkan(report);
        } catch (RuntimeException | LinkageError error) {
            report.reasons.add("Vulkan device creation unavailable: " + describe(error));
        }
        return finish(report);
    }

    public static String bootstrapJson() {
        return toJson(bootstrap());
    }

    /** Serialization is separate from device creation so the report contract can be tested without a GPU. */
    public static String toJson(VulkanDeviceReport report) {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report);
    }

    private static VulkanDeviceReport.OpenGl openGlIdentity() {
        GlDeviceIdentity identity = GlDeviceIdentity.inspect();
        VulkanDeviceReport.OpenGl result = new VulkanDeviceReport.OpenGl();
        result.vendor = identity.vendor;
        result.renderer = identity.renderer;
        result.version = identity.version;
        result.driverUuid = identity.driverUuid;
        result.deviceUuids.addAll(identity.deviceUuids);
        result.memoryObjectFd = identity.memoryObjectFd;
        result.semaphoreFd = identity.semaphoreFd;
        return result;
    }

    private static void inspectVulkan(VulkanDeviceReport report) {
        int loaderVersion = VK.getInstanceVersionSupported();
        report.loaderVersion = version(loaderVersion);
        if (VK_VERSION_MAJOR(loaderVersion) < REQUIRED_API_MAJOR
                || (VK_VERSION_MAJOR(loaderVersion) == REQUIRED_API_MAJOR
                && VK_VERSION_MINOR(loaderVersion) < REQUIRED_API_MINOR)) {
            report.reasons.add("Vulkan loader " + report.loaderVersion + " is older than the required 1.2.");
            return;
        }

        VkInstance instance = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo application = VkApplicationInfo.calloc(stack).sType$Default()
                    .pApplicationName(stack.UTF8("Celtium terrain backend"))
                    .applicationVersion(1)
                    .apiVersion(Math.min(loaderVersion, VK12.VK_API_VERSION_1_2));
            VkInstanceCreateInfo create = VkInstanceCreateInfo.calloc(stack).sType$Default()
                    .pApplicationInfo(application);
            PointerBuffer handle = stack.mallocPointer(1);
            check(vkCreateInstance(create, null, handle), "vkCreateInstance");
            instance = new VkInstance(handle.get(0), create);

            IntBuffer count = stack.mallocInt(1);
            check(vkEnumeratePhysicalDevices(instance, count, null), "vkEnumeratePhysicalDevices(count)");
            if (count.get(0) == 0) {
                report.reasons.add("No Vulkan physical device is present.");
                return;
            }
            PointerBuffer devices = stack.mallocPointer(count.get(0));
            check(vkEnumeratePhysicalDevices(instance, count, devices), "vkEnumeratePhysicalDevices");

            VkPhysicalDevice matched = null;
            for (int i = 0; i < count.get(0) && matched == null; i++) {
                VkPhysicalDevice device = new VkPhysicalDevice(devices.get(i), instance);
                Candidate candidate = Candidate.of(device);
                if (candidate.matches(report.openGl)) {
                    matched = device;
                    report.matched = candidate.toReport(report.openGl);
                }
            }
            if (matched == null) {
                report.reasons.add("No Vulkan physical device matches the OpenGL device UUID "
                        + String.join(", ", report.openGl.deviceUuids)
                        + ". A different driver or a software Vulkan device is not used instead.");
                return;
            }

            verifyContract(matched, report);
            if (!report.matched.missingExtensions.isEmpty()) {
                report.reasons.add("The matched device is missing required device extensions: "
                        + String.join(", ", report.matched.missingExtensions));
                return;
            }
            if (!report.matched.binarySemaphoreImportable || !report.matched.binarySemaphoreExportable) {
                report.reasons.add("The matched device cannot both import and export binary semaphores as opaque FDs.");
                return;
            }
            if (!report.matched.colorImportable || !report.matched.depthImportable) {
                report.reasons.add("The matched device cannot import "
                        + report.requiredContract.colorFormat + " or " + report.requiredContract.depthFormat
                        + " as an external image.");
                return;
            }
            if (report.matched.queueFamilyIndex < 0) {
                report.reasons.add("The matched device has no graphics and transfer queue family.");
                return;
            }

            VkDevice logical = createLogicalDevice(matched, stack, report);
            if (logical != null) {
                report.logicalDeviceCreated = true;
                vkDestroyDevice(logical, null);
            }
        } finally {
            if (instance != null) {
                vkDestroyInstance(instance, null);
            }
        }
    }

    private static VkDevice createLogicalDevice(VkPhysicalDevice device, MemoryStack stack, VulkanDeviceReport report) {
        FloatBuffer priority = stack.mallocFloat(1);
        priority.put(0, 1.0f);
        VkDeviceQueueCreateInfo.Buffer queues = VkDeviceQueueCreateInfo.calloc(1, stack);
        queues.get(0).sType$Default()
                .queueFamilyIndex(report.matched.queueFamilyIndex)
                .pQueuePriorities(priority);
        PointerBuffer names = stack.mallocPointer(report.matched.enabledExtensions.size());
        for (String extension : report.matched.enabledExtensions) {
            names.put(stack.UTF8(extension));
        }
        names.flip();
        VkDeviceCreateInfo create = VkDeviceCreateInfo.calloc(stack).sType$Default()
                .pQueueCreateInfos(queues)
                .ppEnabledExtensionNames(names);
        PointerBuffer handle = stack.mallocPointer(1);
        int result = vkCreateDevice(device, create, null, handle);
        if (result != VK_SUCCESS) {
            report.reasons.add("vkCreateDevice returned " + result + ".");
            return null;
        }
        return new VkDevice(handle.get(0), device, create);
    }

    private static void verifyContract(VkPhysicalDevice device, VulkanDeviceReport report) {
        VulkanDeviceReport.MatchedDevice matched = report.matched;
        Set<String> extensions = deviceExtensions(device);
        for (String required : report.requiredContract.deviceExtensions) {
            if (extensions.contains(required)) {
                matched.enabledExtensions.add(required);
            } else {
                matched.missingExtensions.add(required);
            }
        }
        matched.externalMemoryFd = extensions.contains(VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME);
        matched.externalSemaphoreFd = extensions.contains(VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceExternalSemaphoreInfo semaphoreInfo = VkPhysicalDeviceExternalSemaphoreInfo.calloc(stack)
                    .sType$Default()
                    .handleType(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT);
            VkExternalSemaphoreProperties semaphore = VkExternalSemaphoreProperties.calloc(stack).sType$Default();
            vkGetPhysicalDeviceExternalSemaphoreProperties(device, semaphoreInfo, semaphore);
            int features = semaphore.externalSemaphoreFeatures();
            boolean compatible = (semaphore.compatibleHandleTypes()
                    & VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT) != 0;
            matched.binarySemaphoreImportable = compatible
                    && (features & VK_EXTERNAL_SEMAPHORE_FEATURE_IMPORTABLE_BIT) != 0;
            matched.binarySemaphoreExportable = compatible
                    && (features & VK_EXTERNAL_SEMAPHORE_FEATURE_EXPORTABLE_BIT) != 0;
        }

        ExternalFormat color = externalFormat(device, VK_FORMAT_R8G8B8A8_UNORM, false);
        matched.colorImportable = color.importable;
        ExternalFormat depth = externalFormat(device, VK_FORMAT_D32_SFLOAT, true);
        matched.depthImportable = depth.importable;
        matched.dedicatedAllocationRequired = color.dedicatedOnly || depth.dedicatedOnly;

        QueueFamily queue = graphicsQueueFamily(device);
        matched.queueFamilyIndex = queue.index;
        matched.queueCount = queue.count;
        matched.graphicsQueue = queue.graphics;
        matched.transferQueue = queue.transfer;
    }

    private static Set<String> deviceExtensions(VkPhysicalDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            check(vkEnumerateDeviceExtensionProperties(device, (String) null, count, null),
                    "vkEnumerateDeviceExtensionProperties(count)");
            Set<String> result = new HashSet<String>();
            if (count.get(0) == 0) {
                return result;
            }
            try (VkExtensionProperties.Buffer properties = VkExtensionProperties.calloc(count.get(0))) {
                check(vkEnumerateDeviceExtensionProperties(device, (String) null, count, properties),
                        "vkEnumerateDeviceExtensionProperties");
                for (int i = 0; i < count.get(0); i++) {
                    result.add(properties.get(i).extensionNameString());
                }
            }
            return result;
        }
    }

    private static ExternalFormat externalFormat(VkPhysicalDevice device, int format, boolean depth) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceExternalImageFormatInfo external = VkPhysicalDeviceExternalImageFormatInfo.calloc(stack)
                    .sType$Default()
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT);
            int usage = VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT
                    | (depth ? VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT : VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
            VkPhysicalDeviceImageFormatInfo2 info = VkPhysicalDeviceImageFormatInfo2.calloc(stack).sType$Default()
                    .pNext(external.address())
                    .format(format)
                    .type(VK_IMAGE_TYPE_2D)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(usage);
            VkExternalImageFormatProperties externalProperties = VkExternalImageFormatProperties.calloc(stack)
                    .sType$Default();
            VkImageFormatProperties2 properties = VkImageFormatProperties2.calloc(stack).sType$Default()
                    .pNext(externalProperties.address());
            int result = vkGetPhysicalDeviceImageFormatProperties2(device, info, properties);
            ExternalFormat format0 = new ExternalFormat();
            if (result != VK_SUCCESS) {
                return format0;
            }
            VkExternalMemoryProperties memory = externalProperties.externalMemoryProperties();
            int features = memory.externalMemoryFeatures();
            format0.importable = (features & VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT) != 0
                    && (memory.compatibleHandleTypes() & VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT) != 0
                    && (properties.imageFormatProperties().sampleCounts() & VK_SAMPLE_COUNT_1_BIT) != 0;
            format0.dedicatedOnly = (features & VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT) != 0;
            return format0;
        }
    }

    private static QueueFamily graphicsQueueFamily(VkPhysicalDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            vkGetPhysicalDeviceQueueFamilyProperties(device, count, null);
            QueueFamily result = new QueueFamily();
            if (count.get(0) == 0) {
                return result;
            }
            VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.calloc(count.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(device, count, families);
            for (int i = 0; i < count.get(0); i++) {
                int flags = families.get(i).queueFlags();
                boolean graphics = (flags & VK_QUEUE_GRAPHICS_BIT) != 0;
                boolean transfer = (flags & VK_QUEUE_TRANSFER_BIT) != 0;
                if (graphics && transfer) {
                    result.index = i;
                    result.count = families.get(i).queueCount();
                    result.graphics = true;
                    result.transfer = true;
                    return result;
                }
            }
            return result;
        }
    }

    private static VulkanDeviceReport finish(VulkanDeviceReport report) {
        boolean contractSatisfied = report.matched != null
                && report.logicalDeviceCreated
                && report.matched.missingExtensions.isEmpty()
                && report.matched.binarySemaphoreImportable
                && report.matched.binarySemaphoreExportable
                && report.matched.colorImportable
                && report.matched.depthImportable;
        if (contractSatisfied) {
            report.status = "DEVICE_READY";
        } else if (report.matched != null) {
            report.status = "CONTRACT_UNSATISFIED";
        } else if (!report.reasons.isEmpty()) {
            report.status = "UNSUPPORTED_OR_UNAVAILABLE";
        } else {
            report.status = "NO_MATCHING_DEVICE";
            report.reasons.add("No Vulkan physical device matched the OpenGL device UUID.");
        }
        report.warnings.add("Creating a logical device does not render terrain. Uploads, arenas and draws are P3 work.");
        return report;
    }

    private static String version(int version) {
        return VK_VERSION_MAJOR(version) + "." + VK_VERSION_MINOR(version) + "." + VK_VERSION_PATCH(version);
    }

    private static String deviceTypeName(int deviceType) {
        switch (deviceType) {
            case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU: return "integrated-gpu";
            case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU: return "discrete-gpu";
            case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU: return "virtual-gpu";
            case VK_PHYSICAL_DEVICE_TYPE_CPU: return "cpu";
            default: return "other";
        }
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " returned " + result);
        }
    }

    static String describe(Throwable error) {
        return error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
    }

    private static final class ExternalFormat {
        boolean importable;
        boolean dedicatedOnly;
    }

    private static final class QueueFamily {
        int index = -1;
        int count;
        boolean graphics;
        boolean transfer;
    }

    /** Physical device identity, inspected before any contract query so a mismatch never issues device work. */
    private static final class Candidate {
        String name;
        String deviceUuid;
        String driverUuid;
        String apiVersion;
        int apiMajor;
        int apiMinor;
        int deviceType;

        static Candidate of(VkPhysicalDevice device) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Candidate candidate = new Candidate();
                VkPhysicalDeviceIDProperties ids = VkPhysicalDeviceIDProperties.calloc(stack).sType$Default();
                VkPhysicalDeviceProperties2 properties = VkPhysicalDeviceProperties2.calloc(stack)
                        .sType$Default()
                        .pNext(ids.address());
                vkGetPhysicalDeviceProperties2(device, properties);
                candidate.name = properties.properties().deviceNameString();
                int api = properties.properties().apiVersion();
                candidate.apiVersion = version(api);
                candidate.apiMajor = VK_VERSION_MAJOR(api);
                candidate.apiMinor = VK_VERSION_MINOR(api);
                candidate.deviceType = properties.properties().deviceType();
                candidate.deviceUuid = GlDeviceIdentity.hex(ids.deviceUUID());
                candidate.driverUuid = GlDeviceIdentity.hex(ids.driverUUID());
                return candidate;
            }
        }

        boolean matches(VulkanDeviceReport.OpenGl openGl) {
            if (this.deviceType == VK_PHYSICAL_DEVICE_TYPE_CPU) {
                return false;
            }
            if (this.apiMajor < REQUIRED_API_MAJOR
                    || (this.apiMajor == REQUIRED_API_MAJOR && this.apiMinor < REQUIRED_API_MINOR)) {
                return false;
            }
            return openGl.deviceUuids.contains(this.deviceUuid);
        }

        VulkanDeviceReport.MatchedDevice toReport(VulkanDeviceReport.OpenGl openGl) {
            VulkanDeviceReport.MatchedDevice result = new VulkanDeviceReport.MatchedDevice();
            result.name = this.name;
            result.deviceUuid = this.deviceUuid;
            result.driverUuid = this.driverUuid;
            result.apiVersion = this.apiVersion;
            result.deviceType = this.deviceType;
            result.deviceTypeName = deviceTypeName(this.deviceType);
            result.uuidMatchesOpenGl = openGl.deviceUuids.contains(this.deviceUuid);
            result.driverUuidMatchesOpenGl = this.driverUuid != null && this.driverUuid.equals(openGl.driverUuid);
            return result;
        }
    }
}
