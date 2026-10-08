package org.soutalab.celtium.probe;

import com.google.gson.JsonObject;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.lwjgl.vulkan.EXTDebugUtils.*;

/** Small, single-queue experiment. Not a terrain device abstraction. */
final class VkInteropDevice implements AutoCloseable {
    VkInstance instance;
    VkPhysicalDevice physical;
    VkDevice device;
    VkQueue queue;
    int queueFamily;
    VkPhysicalDeviceMemoryProperties memoryTypes;
    String name, deviceUuid, driverUuid;
    private long debugMessenger;
    private VkDebugUtilsMessengerCallbackEXT callback;
    int validationErrors;
    int validationWarnings;
    final List<String> validationMessages = new ArrayList<String>();
    private long commandPool;
    private final VkCommandBuffer[] commands = new VkCommandBuffer[2];
    private final long[] fences = new long[2];
    private final boolean[] submitted = new boolean[2];
    private int nextCommand;
    private int activeCommand;

    static VkInteropDevice open(ProbeReport.OpenGlDevice gl, boolean validation) {
        VkInteropDevice result = new VkInteropDevice();
        try {
            result.initialize(gl, validation);
            return result;
        } catch (RuntimeException | LinkageError error) {
            result.close();
            throw error;
        }
    }

    private void initialize(ProbeReport.OpenGlDevice gl, boolean validation) {
        if (!gl.memoryObjectFd || !gl.semaphoreFd || gl.deviceUuids.isEmpty())
            throw new IllegalStateException("The GL context lacks external FD objects or device UUIDs");
        if (gl.renderer.toLowerCase(java.util.Locale.ROOT).contains("llvmpipe"))
            throw new IllegalStateException("Software GL is not a hardware interop proof");
        if (VK.getInstanceVersionSupported() < VK12.VK_API_VERSION_1_2)
            throw new IllegalStateException("This experiment requires Vulkan 1.2");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo app = VkApplicationInfo.calloc(stack).sType$Default()
                    .pApplicationName(stack.UTF8("Celtium headless interop experiment"))
                    .apiVersion(VK12.VK_API_VERSION_1_2);
            VkInstanceCreateInfo info = VkInstanceCreateInfo.calloc(stack).sType$Default().pApplicationInfo(app);
            VkDebugUtilsMessengerCreateInfoEXT debug = null;
            if (validation) {
                IntBuffer count = stack.ints(0);
                check(vkEnumerateInstanceLayerProperties(count, null), "enumerate validation layers");
                boolean found = false;
                try (VkLayerProperties.Buffer layers = VkLayerProperties.calloc(count.get(0))) {
                    check(vkEnumerateInstanceLayerProperties(count, layers), "enumerate validation layers");
                    for (int i = 0; i < count.get(0); i++)
                        found |= "VK_LAYER_KHRONOS_validation".equals(layers.get(i).layerNameString());
                }
                if (!found) throw new IllegalStateException("Requested validation layer is not installed");
                info.ppEnabledLayerNames(stack.pointers(stack.UTF8("VK_LAYER_KHRONOS_validation")));
                info.ppEnabledExtensionNames(stack.pointers(stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME),
                        stack.UTF8(EXTValidationFeatures.VK_EXT_VALIDATION_FEATURES_EXTENSION_NAME)));
                callback = VkDebugUtilsMessengerCallbackEXT.create((severity, type, address, user) -> {
                    String message = VkDebugUtilsMessengerCallbackDataEXT.create(address).pMessageString();
                    if ((severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) validationErrors++;
                    if ((severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) validationWarnings++;
                    if (validationMessages.size() < 64) validationMessages.add(message);
                    System.err.println("Vulkan validation: " + message);
                    return VK_FALSE;
                });
                debug = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack).sType$Default()
                        .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT)
                        .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
                                | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT).pfnUserCallback(callback);
                VkValidationFeaturesEXT features = VkValidationFeaturesEXT.calloc(stack).sType$Default()
                        .pEnabledValidationFeatures(stack.ints(EXTValidationFeatures.VK_VALIDATION_FEATURE_ENABLE_SYNCHRONIZATION_VALIDATION_EXT))
                        .pNext(debug.address());
                info.pNext(features.address());
            }
            PointerBuffer pointer = stack.mallocPointer(1);
            check(vkCreateInstance(info, null, pointer), "create Vulkan instance");
            instance = new VkInstance(pointer.get(0), info);
            if (debug != null) {
                LongBuffer handle = stack.mallocLong(1);
                check(vkCreateDebugUtilsMessengerEXT(instance, debug, null, handle), "create validation messenger");
                debugMessenger = handle.get(0);
            }
            IntBuffer count = stack.ints(0);
            check(vkEnumeratePhysicalDevices(instance, count, null), "enumerate devices");
            PointerBuffer devices = stack.mallocPointer(count.get(0));
            check(vkEnumeratePhysicalDevices(instance, count, devices), "enumerate devices");
            for (int i = 0; i < count.get(0); i++) {
                VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(i), instance);
                VkPhysicalDeviceIDProperties ids = VkPhysicalDeviceIDProperties.calloc(stack).sType$Default();
                VkPhysicalDeviceProperties2 properties = VkPhysicalDeviceProperties2.calloc(stack).sType$Default().pNext(ids.address());
                vkGetPhysicalDeviceProperties2(candidate, properties);
                String uuid = CurrentGlProbe.hex(ids.deviceUUID());
                if (!uuid.matches("0{32}") && gl.deviceUuids.contains(uuid)
                        && gl.driverUuid.equals(CurrentGlProbe.hex(ids.driverUUID()))
                        && properties.properties().deviceType() != VK_PHYSICAL_DEVICE_TYPE_CPU) {
                    if (physical != null) throw new IllegalStateException("Ambiguous matching Vulkan devices");
                    physical = candidate;
                    name = properties.properties().deviceNameString();
                    deviceUuid = uuid;
                    driverUuid = CurrentGlProbe.hex(ids.driverUUID());
                }
            }
            if (physical == null) throw new IllegalStateException("No hardware Vulkan device with matching GL device/driver UUIDs");
            check(vkEnumerateDeviceExtensionProperties(physical, (String) null, count, null), "enumerate device extensions");
            Set<String> extensions = new HashSet<String>();
            try (VkExtensionProperties.Buffer properties = VkExtensionProperties.calloc(count.get(0))) {
                check(vkEnumerateDeviceExtensionProperties(physical, (String) null, count, properties), "enumerate device extensions");
                for (int i = 0; i < count.get(0); i++) extensions.add(properties.get(i).extensionNameString());
            }
            String memoryExtension = KHRExternalMemoryFd.VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME;
            String semaphoreExtension = KHRExternalSemaphoreFd.VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME;
            if (!extensions.contains(memoryExtension) || !extensions.contains(semaphoreExtension))
                throw new IllegalStateException("Matched device lacks external memory/semaphore FD extensions");
            VkPhysicalDeviceExternalSemaphoreInfo semaphoreInfo = VkPhysicalDeviceExternalSemaphoreInfo.calloc(stack).sType$Default()
                    .handleType(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT);
            VkExternalSemaphoreProperties semaphoreProperties = VkExternalSemaphoreProperties.calloc(stack).sType$Default();
            vkGetPhysicalDeviceExternalSemaphoreProperties(physical, semaphoreInfo, semaphoreProperties);
            int required = VK_EXTERNAL_SEMAPHORE_FEATURE_IMPORTABLE_BIT | VK_EXTERNAL_SEMAPHORE_FEATURE_EXPORTABLE_BIT;
            if ((semaphoreProperties.externalSemaphoreFeatures() & required) != required
                    || (semaphoreProperties.compatibleHandleTypes() & VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT) == 0)
                throw new IllegalStateException("Opaque FD binary semaphore sharing unsupported");
            vkGetPhysicalDeviceQueueFamilyProperties(physical, count, null);
            VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.calloc(count.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(physical, count, families);
            queueFamily = -1;
            for (int i = 0; i < count.get(0); i++) {
                if ((families.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0 && families.get(i).queueCount() > 0) {
                    queueFamily = i;
                    break;
                }
            }
            if (queueFamily < 0) throw new IllegalStateException("No graphics queue");
            VkDeviceQueueCreateInfo.Buffer queueInfo = VkDeviceQueueCreateInfo.calloc(1, stack);
            queueInfo.get(0).sType$Default().queueFamilyIndex(queueFamily).pQueuePriorities(stack.floats(1));
            VkDeviceCreateInfo create = VkDeviceCreateInfo.calloc(stack).sType$Default().pQueueCreateInfos(queueInfo)
                    .ppEnabledExtensionNames(stack.pointers(stack.UTF8(memoryExtension), stack.UTF8(semaphoreExtension)));
            check(vkCreateDevice(physical, create, null, pointer), "create Vulkan logical device");
            device = new VkDevice(pointer.get(0), physical, create);
            vkGetDeviceQueue(device, queueFamily, 0, pointer);
            queue = new VkQueue(pointer.get(0), device);
            memoryTypes = VkPhysicalDeviceMemoryProperties.calloc();
            vkGetPhysicalDeviceMemoryProperties(physical, memoryTypes);
            LongBuffer handle = stack.mallocLong(1);
            VkCommandPoolCreateInfo pool = VkCommandPoolCreateInfo.calloc(stack).sType$Default()
                    .queueFamilyIndex(queueFamily).flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
            check(vkCreateCommandPool(device, pool, null, handle), "create command pool");
            commandPool = handle.get(0);
            VkCommandBufferAllocateInfo allocate = VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
                    .commandPool(commandPool).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(2);
            PointerBuffer buffers = stack.mallocPointer(2);
            check(vkAllocateCommandBuffers(device, allocate, buffers), "allocate command buffers");
            for (int i = 0; i < commands.length; i++) {
                commands[i] = new VkCommandBuffer(buffers.get(i), device);
                check(vkCreateFence(device, VkFenceCreateInfo.calloc(stack).sType$Default().flags(VK_FENCE_CREATE_SIGNALED_BIT), null, handle), "create fence");
                fences[i] = handle.get(0);
            }
        }
    }

    int memoryType(int allowed, int required) {
        for (int i = 0; i < memoryTypes.memoryTypeCount(); i++)
            if ((allowed & (1 << i)) != 0 && (memoryTypes.memoryTypes(i).propertyFlags() & required) == required) return i;
        throw new IllegalStateException("No compatible Vulkan memory type");
    }

    VkCommandBuffer begin() {
        activeCommand = nextCommand++ % commands.length;
        check(vkWaitForFences(device, fences[activeCommand], true, 5_000_000_000L), "wait command slot fence");
        check(vkResetFences(device, fences[activeCommand]), "reset command fence");
        submitted[activeCommand] = false;
        check(vkResetCommandBuffer(commands[activeCommand], 0), "reset command buffer");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            check(vkBeginCommandBuffer(commands[activeCommand], VkCommandBufferBeginInfo.calloc(stack).sType$Default()
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)), "begin command buffer");
        }
        return commands[activeCommand];
    }

    void submit(long wait, long signal) {
        VkCommandBuffer command = commands[activeCommand];
        check(vkEndCommandBuffer(command), "end command buffer");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default().pCommandBuffers(stack.pointers(command));
            if (wait != 0) submit.pWaitSemaphores(stack.longs(wait)).pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT));
            if (signal != 0) submit.pSignalSemaphores(stack.longs(signal));
            check(vkQueueSubmit(queue, submit, fences[activeCommand]), "submit graphics queue");
            submitted[activeCommand] = true;
        }
    }

    void awaitSubmitted() {
        for (int i = 0; i < fences.length; i++) if (submitted[i])
            check(vkWaitForFences(device, fences[i], true, 5_000_000_000L), "retire Vulkan command slot");
    }

    int pendingCommandSlots() {
        int pending = 0;
        for (int i = 0; i < fences.length; i++) if (submitted[i]) {
            int result = vkGetFenceStatus(device, fences[i]);
            if (result == VK_NOT_READY) pending++;
            else check(result, "inspect retirement fence");
        }
        return pending;
    }

    JsonObject describe() {
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        result.addProperty("deviceUuid", deviceUuid);
        result.addProperty("driverUuid", driverUuid);
        result.addProperty("queueFamily", queueFamily);
        return result;
    }

    static void check(int result, String operation) {
        if (result != VK_SUCCESS) throw new IllegalStateException(operation + ": VkResult " + result);
    }

    @Override public void close() {
        if (device != null) {
            vkDeviceWaitIdle(device); // Shutdown only, never a pass synchronization mechanism.
            for (long fence : fences) if (fence != 0) vkDestroyFence(device, fence, null);
            if (commandPool != 0) vkDestroyCommandPool(device, commandPool, null);
            vkDestroyDevice(device, null);
            device = null;
        }
        if (memoryTypes != null) { memoryTypes.free(); memoryTypes = null; }
        if (instance != null) {
            if (debugMessenger != 0) vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, null);
            vkDestroyInstance(instance, null);
            instance = null;
        }
        if (callback != null) { callback.free(); callback = null; }
    }
}
