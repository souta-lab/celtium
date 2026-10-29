package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A blank baseline must not erase pixels already written into the current image. */
class PatchBaselineOwnershipTest {
    @Test
    void priorBlitOrPatchPreventsClearButAnImageReplacementStartsFresh() throws Exception {
        Constructor<VulkanGlTexture> constructor = VulkanGlTexture.class.getDeclaredConstructor(VulkanContext.class);
        constructor.setAccessible(true);
        VulkanGlTexture texture = constructor.newInstance((VulkanContext) null);
        set(texture, "width", 64);
        set(texture, "height", 64);
        set(texture, "generation", 7L);
        set(texture, "levelWrites", new long[] {0, 0});
        Method eligible = VulkanGlTexture.class.getDeclaredMethod("celtium$canInitializePatchBaseline");
        eligible.setAccessible(true);

        assertTrue((Boolean) eligible.invoke(texture));
        set(texture, "lastBlitGeneration", 7L);
        assertFalse((Boolean) eligible.invoke(texture), "a prior GPU blit must survive the next patch");
        set(texture, "generation", 8L);
        assertTrue((Boolean) eligible.invoke(texture), "a replacement image does not contain the old blit");
        set(texture, "levelWrites", new long[] {0, 1});
        assertFalse((Boolean) eligible.invoke(texture), "a write to any mip must survive the next patch");
        set(texture, "levelWrites", new long[] {0, 0});
        set(texture, "fullContentArrived", true);
        assertFalse((Boolean) eligible.invoke(texture), "a complete image must not be cleared");
    }

    private static void set(VulkanGlTexture texture, String name, Object value) throws Exception {
        Field field = VulkanGlTexture.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(texture, value);
    }
}
