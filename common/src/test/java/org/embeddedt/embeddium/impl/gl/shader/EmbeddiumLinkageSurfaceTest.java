package org.embeddedt.embeddium.impl.gl.shader;

import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat3v;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix3f;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix4f;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionFlags;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the Embeddium classes that look dead from inside this tree but are linked by name
 * from Demonica's bundled Iris, so a future dead-code sweep cannot delete them.
 * <p>
 * None of {@link ShaderLoader}, {@link GlUniformFloat3v}, {@link GlUniformMatrix3f} or
 * {@link GlUniformMatrix4f} has a single reference outside its own file in this
 * repository, which makes them the first candidates any reference-count cleanup picks.
 * They are nevertheless load-bearing: the constant pools of Demonica-0.9.1.jar
 * (measured 2026-11-22 from the stock jar, classes
 * {@code net.coderbot.iris.celeritas.IrisCeleritasChunkShaderInterface},
 * {@code net.coderbot.iris.pipeline.transform.ShaderTransformer},
 * {@code net.coderbot.iris.postprocess.CenterDepthSampler} and
 * {@code com.demonica.render.EndPortalCompositeRenderer}) contain Methodrefs to
 * <ul>
 *   <li>{@code ShaderLoader.loadShader(ShaderType, String, ShaderConstants)}</li>
 *   <li>{@code ShaderLoader.getShaderSource(String)}</li>
 *   <li>{@code GlUniformMatrix4f.<init>(int)} and its {@code set} overloads</li>
 *   <li>{@code GlUniformMatrix3f.<init>(int)} and its {@code set} overloads</li>
 *   <li>{@code GlUniformFloat3v.<init>(int)} and {@code set(float, float, float)}</li>
 * </ul>
 * In the shipped jar the {@code org.joml} types in these signatures are relocated to
 * {@code org.embeddedt.embeddium.impl.shadow.joml}, which is the name Demonica's
 * references carry; the source-level signatures pinned here are what that relocation
 * starts from. Deleting or renaming any of these members is a NoSuchMethodError /
 * NoClassDefFoundError in Demonica at runtime, not a compile error here - the same
 * failure shape as the removed-static-field regression fixed by 94ced124f.
 * <p>
 * {@link RenderSectionFlags} is pinned for the same reason through its own documented
 * contract: Demonica's ChunkAnimator integration reads the {@code HAS_BLOCK_GEOMETRY}
 * bit position, so the constant's value is part of the surface even though the class
 * itself documents that nothing inside celtium should use it.
 */
class EmbeddiumLinkageSurfaceTest {

    @Test
    void shaderLoaderEntryPointsExist() throws Exception {
        assertNotNull(ShaderLoader.class.getMethod("loadShader",
                ShaderType.class, String.class, ShaderConstants.class));
        assertNotNull(ShaderLoader.class.getMethod("getShaderSource", String.class));
        assertNotNull(ShaderLoader.class.getMethod("useLegacyGlsl"));
        assertNotNull(ShaderLoader.class.getMethod("downgradeIfNeeded",
                ShaderType.class, String.class));
    }

    @Test
    void uniformClassesKeepTheirIntConstructors() throws Exception {
        assertNotNull(GlUniformFloat3v.class.getConstructor(int.class));
        assertNotNull(GlUniformMatrix3f.class.getConstructor(int.class));
        assertNotNull(GlUniformMatrix4f.class.getConstructor(int.class));
    }

    @Test
    void uniformSetOverloadsExist() throws Exception {
        assertNotNull(GlUniformFloat3v.class.getMethod("set", float[].class));
        assertNotNull(GlUniformFloat3v.class.getMethod("set",
                float.class, float.class, float.class));
        assertNotNull(GlUniformMatrix3f.class.getMethod("set", Matrix3f.class));
        assertNotNull(GlUniformMatrix3f.class.getMethod("set", Matrix3fc.class));
        assertNotNull(GlUniformMatrix4f.class.getMethod("set", Matrix4f.class));
        assertNotNull(GlUniformMatrix4f.class.getMethod("set", Matrix4fc.class));
    }

    @Test
    void renderSectionFlagsKeepTheDemonicaBitPosition() throws Exception {
        assertEquals(0, RenderSectionFlags.HAS_BLOCK_GEOMETRY,
                "Demonica builds render lists with 1 << HAS_BLOCK_GEOMETRY and hands them "
                        + "to celtium's ChunkRenderList; the bit position is a published contract");
    }
}
