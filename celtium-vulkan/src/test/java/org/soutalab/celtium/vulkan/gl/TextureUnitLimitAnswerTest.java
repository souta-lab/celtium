package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The texture-unit limit family, checked without a GPU.
 * <p>
 * The answers are one story (TEXUNIT-DESIGN-2026-11-17): per stage 32, the size of the
 * {@code NativeTextureBindings} ledger; combined 64, per-stage times the two stages (vertex and
 * fragment) whose sampler descriptors the mod-program pipeline binds. Combined is asked on two
 * pnames: 0x8F39, and 0x8B4D - the value LWJGL2's GL20 assigns to
 * {@code GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS} (35661, javap-verified on the game's 2.9.4 jar).
 * GLSM sizes its sampler-object cache from the LWJGL2 constant, so the 16 this stub used to
 * return on 0x8B4D under a geometry label sized that cache to 16 and the first deferred pass
 * overflowed it (leg 10, 2026-11-17). The geometry limit's own pname (0x8C29) stays unanswered:
 * geometry is not a lowered stage.
 */
class TextureUnitLimitAnswerTest {

    @Test
    void textureUnitLimitsAreOneStoryOnEveryPname() throws Exception {
        var ctor = CeltiumGlStub.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        var installed = CeltiumGlStub.class.getDeclaredField("installed");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        installed.set(null, ctor.newInstance());
        try {
            assertEquals(32, CeltiumGlStub.celtium$getInteger(0x8872),
                    "GL_MAX_TEXTURE_IMAGE_UNITS - the binding ledger's size");
            assertEquals(32, CeltiumGlStub.celtium$getInteger(0x8B4C),
                    "GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS - the pipeline binds vertex-stage samplers too");
            assertEquals(64, CeltiumGlStub.celtium$getInteger(0x8F39),
                    "GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS - per-stage x the two stages served");
            assertEquals(64, CeltiumGlStub.celtium$getInteger(0x8B4D),
                    "the LWJGL2 GL20 combined constant (35661) - the pname GLSM's cache sizing sends");
            assertEquals(Integer.MIN_VALUE, CeltiumGlStub.celtium$getInteger(0x8C29),
                    "GL_MAX_GEOMETRY_TEXTURE_IMAGE_UNITS stays unanswered - geometry is not lowered");
        } finally {
            installed.set(null, previous);
        }
    }
}
