package org.soutalab.celtium.vulkan.gl;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The line-width range queries (GL_LINE_WIDTH_RANGE / GL_ALIASED_LINE_WIDTH_RANGE) answer the device's
 * range, never the width currently set. The aliased case answered the current width, so at GLSM's init
 * (width still 1.0) the range read as [1, 1] even on devices whose range is wider; GLSM enabled its
 * geometry-shader wide-line emulation on that answer, the mod-program lowering does not carry a geometry
 * stage, and the first wide FFP line draw failed its link and crashed the client (demonica-gui-3,
 * finding (c)). No Vulkan backend exists in this JVM, so the device arm falls back to 1.0 - the arm a
 * leg verifies against a real device; what is pinned here is that the answer does not track the set
 * width, which is the defect itself.
 */
class LineWidthRangeAnswerTest {

    private static final int GL_LINE_WIDTH = 0x0B21;
    private static final int GL_LINE_WIDTH_RANGE = 0x0B22;
    private static final int GL_ALIASED_LINE_WIDTH_RANGE = 0x846E;

    @Test
    void theRangeMaxIsNotTheWidthCurrentlySet() throws Exception {
        var ctor = GlsmFrontend.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        GlsmFrontend stub = ctor.newInstance();
        Field installed = GlsmFrontend.class.getDeclaredField("installed");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        installed.set(null, stub);
        FloatBuffer out = MemoryUtil.memAllocFloat(2);
        try {
            GlsmFrontend.celtium$lineWidth(4.0f);

            // The state query still answers the width that was set - it is a state, not a range.
            GlsmFrontend.celtium$getFloats(GL_LINE_WIDTH, MemoryUtil.memAddress(out), false);
            assertEquals(4.0f, out.get(0), "GL_LINE_WIDTH answers the set width");

            GlsmFrontend.celtium$getFloats(GL_ALIASED_LINE_WIDTH_RANGE, MemoryUtil.memAddress(out), false);
            assertEquals(1.0f, out.get(0), "the range starts at 1.0");
            assertEquals(1.0f, out.get(1),
                    "with no backend the range max is the 1.0 fallback - and never the set width (4.0),"
                            + " which is what the pre-fix answer reported");

            GlsmFrontend.celtium$getFloats(GL_LINE_WIDTH_RANGE, MemoryUtil.memAddress(out), false);
            assertEquals(1.0f, out.get(0), "the range starts at 1.0");
            assertEquals(1.0f, out.get(1), "GL_LINE_WIDTH_RANGE answers the same range as the aliased query");
        } finally {
            MemoryUtil.memFree(out);
            installed.set(null, previous);
        }
    }
}
