package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Loads the SPIR-V modules of the native Vulkan terrain renderer from the classpath.
 * <p>
 * The modules are checked in as pre-compiled binaries under
 * {@code /org/soutalab/celtium/vulkan/spirv/<name>.spv}. They are produced by the extracted
 * Khronos reference compiler with:
 *
 * <pre>
 * glslangValidator -V --target-env vulkan1.2 &lt;shader&gt; -o &lt;name&gt;.spv
 * </pre>
 *
 * from the sources in {@code celtium-vulkan/src/main/shaders}. The resources live in the same
 * jar as this class, so nothing has to be shipped next to the mod.
 * <p>
 * The module is returned in native memory as a direct, read-only {@link ByteBuffer} whose
 * capacity is the exact module size in bytes, ready to be passed to
 * {@code VkShaderModuleCreateInfo.pCode(...)}. The caller owns that buffer and must release it
 * with {@link MemoryUtil#memFree(ByteBuffer)} once the {@code VkShaderModule} has been created
 * (the driver copies the code, so it may be freed immediately afterwards).
 */
public final class SpirvShaders {
    /** Classpath name of the SOLID terrain pass vertex module. */
    public static final String SOLID_VERTEX = "terrain_solid.vert";
    /** Vertex module for the uncompressed VanillaLikeChunkVertex layout (stride 32). */
    public static final String SOLID_VERTEX_VANILLA = "terrain_solid_vanilla.vert";

    /** Classpath name of the SOLID terrain pass fragment module. */
    public static final String SOLID_FRAGMENT = "terrain_solid.frag";

    /** Vertex module for the dynamic (non-terrain) geometry of roadmap stages S4/S6. */
    public static final String UI_VERTEX = "ui_quad.vert";

    /** Fragment module for the dynamic (non-terrain) geometry of roadmap stages S4/S6. */
    public static final String UI_FRAGMENT = "ui_quad.frag";

    /** Vertex module for untextured immediate-mode geometry ({@code DefaultVertexFormats.POSITION_COLOR}). */
    public static final String IMMEDIATE_COLOR_VERTEX = "immediate_color.vert";

    /** Vertex module for the BLOCK-family immediate geometry (position, colour, texture, lightmap). */
    public static final String IMMEDIATE_BLOCK_VERTEX = "immediate_block.vert";

    /** Vertex module for the particle layout (position, texture, colour, lightmap - a different order from BLOCK). */
    public static final String IMMEDIATE_PARTICLE_VERTEX = "immediate_particle.vert";

    /** Fragment module for the particle layout, sampling both the texture and the lightmap. */
    public static final String IMMEDIATE_PARTICLE_FRAGMENT = "immediate_particle.frag";

    /** Fragment module for the BLOCK-family immediate geometry, sampling both the texture and the lightmap. */
    public static final String IMMEDIATE_BLOCK_FRAGMENT = "immediate_block.frag";

    /** Vertex module for textured immediate-mode geometry ({@code DefaultVertexFormats.POSITION_TEX_COLOR}). */
    public static final String IMMEDIATE_TEX_COLOR_VERTEX = "immediate_tex_color.vert";

    /** Fragment module for textured immediate-mode geometry. */
    public static final String IMMEDIATE_TEX_COLOR_FRAGMENT = "immediate_tex_color.frag";

    /**
     * Fragment module for the lit ITEM variant (BH-30's collision fix). It is the shared
     * {@link #IMMEDIATE_TEX_COLOR_FRAGMENT} without the fog input and fog math, because the lit family's push block
     * spends 128..215 on the lighting payload and the 80-byte fog block does not fit beside it on a 256-byte device.
     * The SPIR-V interface rule is per pipeline, so this fragment is what makes the lit variant's pipeline creation
     * legal again without moving any offset.
     */
    public static final String IMMEDIATE_TEX_COLOR_LIT_FRAGMENT = "immediate_tex_color_lit.frag";

    /** Vertex module for the lit variant - the fixed-function eval for the ITEM format's normal element. */
    public static final String IMMEDIATE_TEX_COLOR_LIT_VERTEX = "immediate_tex_color_lit.vert";

    /** Vertex module for textured geometry with no colour: {@code POSITION_TEX}, as {@code RenderGlobal} draws sky and clouds. */
    public static final String IMMEDIATE_POSITION_VERTEX = "immediate_position.vert";
    public static final String IMMEDIATE_POSITION_FRAGMENT = "immediate_position.frag";
    public static final String IMMEDIATE_POSITION_TEX_VERTEX = "immediate_position_tex.vert";

    /** Fragment module for textured geometry with no colour. */
    public static final String IMMEDIATE_POSITION_TEX_FRAGMENT = "immediate_position_tex.frag";

    /** Fragment module for untextured immediate-mode geometry. */
    public static final String IMMEDIATE_COLOR_FRAGMENT = "immediate_color.frag";

    private static final String RESOURCE_DIRECTORY = "/org/soutalab/celtium/vulkan/spirv/";
    private static final String RESOURCE_SUFFIX = ".spv";

    /** A SPIR-V module is a stream of 32-bit words; the header alone is 5 words. */
    private static final int MODULE_HEADER_BYTES = 5 * 4;

    private SpirvShaders() {
    }

    /**
     * Loads the SPIR-V module {@code <name>.spv} from the classpath into a direct, read-only
     * native buffer.
     *
     * @param name module name without the {@code .spv} suffix, for example {@link #SOLID_VERTEX}
     * @return a direct, read-only buffer holding the module; the caller must free it with
     *         {@link MemoryUtil#memFree(ByteBuffer)}
     * @throws IllegalStateException if the resource is missing, unreadable or not a valid SPIR-V module
     */
    public static ByteBuffer load(String name) {
        if (name == null) {
            throw new IllegalStateException("SPIR-V shader name must not be null");
        }

        String resource = RESOURCE_DIRECTORY + name + RESOURCE_SUFFIX;
        byte[] data = read(resource);

        if (data.length < MODULE_HEADER_BYTES || (data.length & 3) != 0) {
            throw new IllegalStateException("SPIR-V module " + resource + " is not a valid module: "
                    + data.length + " bytes (expected a non-empty multiple of 4, at least "
                    + MODULE_HEADER_BYTES + ")");
        }
        // Little-endian magic number 0x07230203, which is byte-order independent when read as words.
        if (data[0] != 0x03 || data[1] != 0x02 || data[2] != 0x23 || data[3] != 0x07) {
            throw new IllegalStateException("SPIR-V module " + resource
                    + " does not start with the SPIR-V magic number 0x07230203");
        }

        ByteBuffer buffer = MemoryUtil.memAlloc(data.length);
        try {
            MemoryUtil.memCopy(data, buffer);
            buffer.clear();
            ByteBuffer readOnly = buffer.asReadOnlyBuffer();
            readOnly.order(ByteOrder.nativeOrder());
            return readOnly;
        } catch (Throwable failure) {
            MemoryUtil.memFree(buffer);
            throw failure;
        }
    }

    private static byte[] read(String resource) {
        InputStream stream = SpirvShaders.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("Missing SPIR-V shader resource " + resource
                    + " on the classpath; the celtium-vulkan jar must contain " + RESOURCE_DIRECTORY);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
            byte[] chunk = new byte[8192];
            int count;
            while ((count = stream.read(chunk)) != -1) {
                out.write(chunk, 0, count);
            }
            return out.toByteArray();
        } catch (IOException cause) {
            throw new IllegalStateException("Failed to read SPIR-V shader resource " + resource, cause);
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
                // Nothing useful can be done about a failing close on a classpath resource.
            }
        }
    }
}
