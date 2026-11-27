package org.soutalab.celtium.glcompat;
/*
 * celtium-native replacement for the LWJGL2 ContextCapabilities view the original
 * GLSM read. There is no real GL context in celtium's GL-less runtime, so capability
 * answers describe the emulated context the converter presents to mods.
 */

/**
 * Capability view of the emulated GL context presented by the direct-GL converter.
 *
 * <p>The converter presents a GL 3.3 core profile context. Version flags above 3.3
 * are false; extension flags are true only where the converter's sink implements the
 * feature (sampler objects and texture storage, both part of its texture model).
 * Field names keep the LWJGL2 {@code ContextCapabilities} spellings the converted
 * code was written against, so call sites read unchanged.
 */
public final class GlCapabilities {

    public final boolean OpenGL30 = true;
    public final boolean OpenGL31 = true;
    public final boolean OpenGL32 = true;
    public final boolean OpenGL33 = true;
    public final boolean OpenGL40 = false;
    public final boolean OpenGL42 = false;
    public final boolean OpenGL43 = false;
    public final boolean OpenGL44 = false;
    public final boolean OpenGL45 = false;

    public final boolean GL_ARB_buffer_storage = false;
    public final boolean GL_ARB_clear_texture = false;
    public final boolean GL_ARB_compute_shader = false;
    public final boolean GL_ARB_draw_buffers_blend = false;
    public final boolean GL_ARB_sampler_objects = true;
    public final boolean GL_ARB_shader_image_load_store = false;
    public final boolean GL_ARB_shader_storage_buffer_object = false;
    public final boolean GL_ARB_stencil_texturing = false;
    public final boolean GL_ARB_tessellation_shader = false;
    public final boolean GL_ARB_texture_storage = true;
    public final boolean GL_EXT_direct_state_access = false;
    public final boolean GL_EXT_gpu_shader4 = false;
    public final boolean GL_ARB_framebuffer_object = true;
    public final boolean GL_EXT_framebuffer_object = false;
    public final boolean GL_ARB_vertex_array_object = true;
    public final boolean GL_EXT_shader_image_load_store = false;

    /** The single emulated profile the converter presents. */
    public static final GlCapabilities EMULATED_GL33 = new GlCapabilities();

    private GlCapabilities() {
    }
}
