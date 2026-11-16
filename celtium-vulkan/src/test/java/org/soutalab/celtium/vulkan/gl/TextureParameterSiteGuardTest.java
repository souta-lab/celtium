package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.VulkanDiagnostics;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code celtium$paramSite()} must follow {@link VulkanDiagnostics#withinLimit(int, int)} so default runs without
 * {@code -Dceltium.vulkan.diagnostics=true} never capture stack traces on every {@code glTexParameter*} call.
 */
class TextureParameterSiteGuardTest {
    @Test
    void doesNotCaptureStackTraceWhenDiagnosticsAreDisabled() throws Exception {
        if (VulkanDiagnostics.withinLimit(0, 24)) {
            return;
        }
        Method paramSite = CeltiumGlStub.class.getDeclaredMethod("celtium$paramSite");
        paramSite.setAccessible(true);
        assertEquals("", paramSite.invoke(null));
    }
}
