package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.NativeRasterState;
import org.soutalab.celtium.vulkan.NativeTextureBindings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * M-8 (GLSM superset proof, Q-1 residual): the contracts GlStateQueries' rewired reads rely
 * on. The rewiring changed WHERE the query answers read from, not what they read:
 * <ul>
 *   <li>the generic buffer bindings behind GL_UNIFORM_BUFFER_BINDING (0x8A28, via target
 *   0x8A11), GL_COPY_READ_BUFFER_BINDING (0x8F36), GL_COPY_WRITE_BUFFER_BINDING (0x8F37) and
 *   GL_ELEMENT_ARRAY_BUFFER_BINDING (0x8895, via target 0x8893) come from
 *   {@link GlsmBufferState} directly - the same table the stub's boundBuffer door read for
 *   every non-pixel, non-array target;</li>
 *   <li>the raster and texture-binding reads come from the owner instances the stub
 *   holds - for the installed stub those are the very {@code current()} singletons
 *   {@code GlsmFrontend.install()} builds it on; an injected stub's own owners answer
 *   for that stub, because its writers write through them.</li>
 * </ul>
 * Not run by the alpha lane (desk-only rule); the merge gate runs it.
 */
class GlsmM8RewireTest {

    @Test
    void genericBufferBindingsRoundTripThroughTheOwner() {
        GlsmBufferState buffers = GlsmBufferState.current();
        int[] targets = {0x8A11, 0x8F36, 0x8F37, 0x8893};
        // The owner is a JVM singleton shared by the whole suite: other test classes bind
        // these targets through their stubs and do not all unbind (DrawElementsBufferBoundsTest
        // leaves its element-array name bound, for one). Save whatever the suite left, establish
        // the unbound precondition explicitly instead of assuming a pristine singleton, and put
        // the saved bindings back afterwards so this test leaves no residue of its own either.
        int[] saved = new int[targets.length];
        for (int i = 0; i < targets.length; i++) {
            saved[i] = buffers.bound(targets[i]);
            buffers.bind(targets[i], 0);
        }
        try {
            for (int i = 0; i < targets.length; i++) {
                assertEquals(0, buffers.bound(targets[i]), "target starts unbound");
                buffers.bind(targets[i], 9100 + i);
                assertEquals(9100 + i, buffers.bound(targets[i]));
            }
        } finally {
            for (int i = 0; i < targets.length; i++) {
                buffers.bind(targets[i], saved[i]);
            }
        }
    }

    @Test
    void rasterAndTextureOwnersAreTheSingletonsTheStubInstallsOn() {
        assertSame(NativeRasterState.current(), NativeRasterState.current());
        assertSame(NativeTextureBindings.current(), NativeTextureBindings.current());
        // The rewired query reads must be answerable from the owners alone.
        NativeTextureBindings textures = NativeTextureBindings.current();
        assertEquals(textures.texture2d(), textures.texture2d(textures.activeUnit()));
    }
}
