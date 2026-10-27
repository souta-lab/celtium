package org.soutalab.celtium.vulkan;

import java.lang.reflect.Method;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;

/**
 * The one door to {@link ValidationSupport}, kept free of every debug-utils type.
 * <p>
 * Loading that class resolves the callback type, which exists only in LWJGL 3.4.3 and later; a run without validation
 * must not pay that dependency, because the renderer itself works on the 3.4.1 binding too (a game instance that ships
 * 3.4.1 for lwjgl-vulkan and 3.4.3 for the rest is a real configuration - measured on the user's modpack, where the
 * window renderer could not be created at all until this gap was named).
 */
final class ValidationBridge {

    private static final String SUPPORT = "org.soutalab.celtium.vulkan.ValidationSupport";
    private static final String CALLBACK = "org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackEXTI";

    private ValidationBridge() {
    }

    /** {@return whether this LWJGL binding has the 3.4.3 callback API}, i.e. whether validation can run at all. */
    static boolean available() {
        try {
            Class.forName(CALLBACK);
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }

    static Object enable(VulkanContext context, MemoryStack stack, VkInstanceCreateInfo createInfo) {
        try {
            Class<?> support = Class.forName(SUPPORT);
            Method enable = support.getDeclaredMethod("enable", VulkanContext.class, MemoryStack.class,
                    VkInstanceCreateInfo.class);
            enable.setAccessible(true);
            return enable.invoke(null, context, stack, createInfo);
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("validation support is present but could not be reached: " + error, error);
        }
    }

    static long createMessenger(VulkanContext context, Object messengerInfo) {
        try {
            Class<?> support = Class.forName(SUPPORT);
            Method create = support.getDeclaredMethod("createMessenger", VulkanContext.class, Object.class);
            create.setAccessible(true);
            return ((Long) create.invoke(null, context, messengerInfo)).longValue();
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("the validation messenger could not be created: " + error, error);
        }
    }

    static void destroy(VulkanContext context, VkInstance instance, long messenger) {
        try {
            Class<?> support = Class.forName(SUPPORT);
            Method destroy = support.getDeclaredMethod("destroy", VulkanContext.class, VkInstance.class, Long.TYPE);
            destroy.setAccessible(true);
            destroy.invoke(null, context, instance, Long.valueOf(messenger));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Destroying the messenger is best effort; the instance is going away anyway.
        }
    }
}
