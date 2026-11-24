package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A replay's completion is filed under the list that was replayed - including when the list calls another
 * list on the way.
 * <p>
 * The replay used to record its completion under {@code lastCalledList}, which a nested {@code glCallList}
 * overwrites: the outer list's completion was filed under the inner list's name, so the census counted the
 * inner list twice and the outer list never, and the attribution the quad capture reads stayed on the inner
 * list for the rest of the outer list's commands.
 */
class DisplayListNestedReplayTest {

    private static final int OUTER = 777001;
    private static final int INNER = 777002;
    private static final int GL_COMPILE = 0x1300;

    @Test
    void anOuterListsReplayIsFiledUnderItsOwnName() throws Exception {
        var ctor = GlsmFrontend.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        var installed = GlsmFrontend.class.getDeclaredField("installed");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        installed.set(null, ctor.newInstance());
        try {
            GlsmFrontend.compatibilityNewList(INNER, GL_COMPILE);
            // A recorded call to an undefined list: defined behaviour (a no-op) at replay, and it gives the
            // inner list a body.
            GlsmFrontend.compatibilityCallList(777999);
            GlsmFrontend.compatibilityEndList();
            GlsmFrontend.compatibilityNewList(OUTER, GL_COMPILE);
            GlsmFrontend.compatibilityCallList(INNER);
            GlsmFrontend.compatibilityEndList();

            GlsmFrontend.compatibilityCallList(OUTER);
            String census = GlsmFrontend.listCensus();
            assertTrue(census.contains(OUTER + ":x1"),
                    "the outer list's replay is filed under the outer list; got: " + census);
            assertTrue(census.contains(INNER + ":x1"),
                    "the inner list's replay is filed under the inner list; got: " + census);
        } finally {
            installed.set(null, previous);
        }
    }
}
