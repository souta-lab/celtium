package org.soutalab.celtium.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK10.*;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.EXTDebugUtils;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackDataEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCreateInfoEXT;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;

/**
 * The validation layer and its debug-utils messenger.
 * <p>
 * A separate class on purpose: the callback type changed in LWJGL 3.4 ({@code VkDebugUtilsMessengerCallbackEXT} before,
 * {@code VkDebugUtilsMessengerCallbackEXTI} after), so a game instance whose instance libraries still carry the 3.4.1
 * Vulkan binding failed to link {@code VulkanContext} at all - the window renderer could not be created and no window
 * appeared (measured on the user's Cleanroom 0.7.12 instance, which resolves lwjgl-vulkan 3.4.1 while everything else is
 * 3.4.3). Nothing on the shipping path touches this class unless validation is requested, so a run without validation
 * also starts on the older binding; {@link ValidationBridge} is the only door in and it is named after that gap.
 */
final class ValidationSupport {

    private static final VkDebugUtilsMessengerCallbackEXT[] CALLBACK = new VkDebugUtilsMessengerCallbackEXT[1];
    private static long messenger;

    /**
     * Adds the validation layer and the debug-utils messenger to the instance create info. Only reached when
     * {@link VulkanContext#VALIDATION_PROPERTY} is set, so a normal run enables no layer and no instance extension at all.
     */
    static VkDebugUtilsMessengerCreateInfoEXT enable(VulkanContext context, MemoryStack stack,
            VkInstanceCreateInfo createInfo) {
        if (!VulkanContext.instanceExtensionNames(stack).contains(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME)) {
            throw new IllegalStateException("Validation was requested with -D" + VulkanContext.VALIDATION_PROPERTY + "=true, but the"
                    + " loader does not offer " + EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME + ".");
        }
        if (!VulkanContext.instanceLayerNames(stack).contains(VulkanContext.VALIDATION_LAYER_NAME)) {
            throw new IllegalStateException("Validation was requested with -D" + VulkanContext.VALIDATION_PROPERTY + "=true, but the"
                    + " layer " + VulkanContext.VALIDATION_LAYER_NAME + " is not installed.");
        }
        createInfo.ppEnabledLayerNames(stack.pointers(stack.UTF8(VulkanContext.VALIDATION_LAYER_NAME)));
        createInfo.ppEnabledExtensionNames(stack.pointers(stack.UTF8(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME)));
        CALLBACK[0] = VkDebugUtilsMessengerCallbackEXT.create((severity, types, data, user) -> {
            VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(data);
            String idName = callbackData.pMessageIdNameString();
            long idNumber = callbackData.messageIdNumber();
            String message = callbackData.pMessageString();
            // Structured, one line per message, because the harness has to decide from this text whether a run is clean and
            // a free-form message is exactly what defeated the old check: it skipped anything not starting with "vk", so a
            // line reading "Validation Error: [ VUID-... ]" counted as nothing. Severity and message id are what the gate
            // keys on now, and newlines are folded so one message cannot look like two lines or like none.
            String severityName;
            if ((severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
                severityName = "ERROR";
            } else if ((severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) {
                severityName = "WARNING";
            } else if ((severity & org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0) {
                severityName = "INFO";
            } else {
                severityName = "VERBOSE";
            }
            String identifier = idName != null && !idName.isEmpty() ? idName : Long.toString(idNumber);
            String single = message == null ? "" : message.replace('\n', ' ').replace('\r', ' ').trim();
            System.err.println("Celtium Vulkan validation: severity=" + severityName + " id=" + identifier + " " + single);
            return VK_FALSE;
        });
        VkDebugUtilsMessengerCreateInfoEXT messengerInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack).sType$Default()
                .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
                        | VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT)
                .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
                        | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
                        | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                .pfnUserCallback(CALLBACK[0]);
        // Chained into the instance create info as well, so the messenger also reports whatever the driver says
        // while the instance itself is created and destroyed.
        createInfo.pNext(messengerInfo.address());
        return messengerInfo;
    }


    static long createMessenger(VulkanContext context, Object messengerInfo) {
        try (MemoryStack stack = stackPush()) {
            java.nio.LongBuffer messengerHandle = stack.mallocLong(1);
            if (EXTDebugUtils.vkCreateDebugUtilsMessengerEXT(context.instance(), (VkDebugUtilsMessengerCreateInfoEXT) messengerInfo,
                    null, messengerHandle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateDebugUtilsMessengerEXT failed");
            }
            messenger = messengerHandle.get(0);
            // One line so a run can prove the messenger exists: a clean run prints nothing else, and "no validation
            // messages" has to be distinguishable from "validation was never installed".
            System.out.println("[Celtium] CELTIUM_VALIDATION messenger=0x" + Long.toHexString(messenger)
                    + " layer=" + VulkanContext.VALIDATION_LAYER_NAME);
            return messenger;
        }
    }

    static void destroy(VulkanContext context, VkInstance instance, long handle) {
        if (handle != VK_NULL_HANDLE) {
            EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(instance, handle, null);
        }
        messenger = VK_NULL_HANDLE;
        if (CALLBACK[0] != null) {
            CALLBACK[0].free();
            CALLBACK[0] = null;
        }
    }
}
