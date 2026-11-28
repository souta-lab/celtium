package org.soutalab.celtium.glcompat.capture;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.VulkanDiagnostics;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The capture entry's {@code paramSite()} must follow {@link VulkanDiagnostics#withinLimit(int, int)} so default runs without
 * {@code -Dceltium.vulkan.diagnostics=true} never capture stack traces on every {@code glTexParameter*} call.
 */
class TextureParameterSiteGuardTest {
    @Test
    void doesNotCaptureStackTraceWhenDiagnosticsAreDisabled() throws Exception {
        if (VulkanDiagnostics.withinLimit(0, 24)) {
            return;
        }
        Method paramSite = DirectGlCaptureEntry.class.getDeclaredMethod("paramSite");
        paramSite.setAccessible(true);
        assertEquals("", paramSite.invoke(null));
    }
}
