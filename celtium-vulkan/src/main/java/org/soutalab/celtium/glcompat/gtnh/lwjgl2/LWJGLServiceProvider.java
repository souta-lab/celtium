package org.soutalab.celtium.glcompat.gtnh.lwjgl2;
/*
 * celtium-native replacement for the LWJGL service probe the GTNHLib-derived code
 * consulted. There is no LWJGL2/LWJGL3 service split in celtium's GL-less runtime:
 * version and extension questions are answered from the emulated GL 3.3 core
 * profile the converter presents (see GlCapabilities).
 */

import org.soutalab.celtium.glcompat.GlCapabilities;

/** Version/extension probe answered from the emulated context profile. */
public final class LWJGLServiceProvider {

    /** The probe handle, kept in the supplier shape so call sites read unchanged. */
    public static final Service LWJGL = new Service();

    public static final int POINTER_SIZE = 8;
    public static final long NULL = 0L;

    private LWJGLServiceProvider() {
    }

    /** Answers about the emulated GL 3.3 core context. */
    public static final class Service {

        private Service() {
        }

        public int getPointerSize() {
            return POINTER_SIZE;
        }

        /** The emulated context is GL 3.3 core: versions up to 3.3 are supported. */
        public boolean isOpenGLVersionSupported(int major, int minor) {
            return major < 3 || (major == 3 && minor <= 3);
        }

        public boolean isExtensionSupported(GLExtension extension) {
            final GlCapabilities caps = GlCapabilities.EMULATED_GL33;
            return switch (extension) {
                case ARB_buffer_storage -> caps.GL_ARB_buffer_storage;
                case ARB_compute_shader -> caps.GL_ARB_compute_shader;
                case ARB_shader_storage_buffer_object -> caps.GL_ARB_shader_storage_buffer_object;
                case ARB_direct_state_access -> caps.GL_EXT_direct_state_access;
                case ARB_texture_storage -> caps.GL_ARB_texture_storage;
                case ARB_multi_draw_indirect, ARB_draw_elements_base_vertex, ARB_sync,
                     ARB_timer_query, KHR_debug, ARB_uniform_buffer_object,
                     ARB_vertex_array_object, ARB_map_buffer_range, ARB_copy_buffer,
                     ARB_base_instance -> true;
                case ARB_debug_output, AMD_debug_output, ARB_compatibility -> false;
            };
        }
    }
}
