package org.soutalab.celtium.glcompat.backend;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The object records the direct-GL executor keeps for every GL name it hands out. GL names are
 * executor-local integers; the sink's handles (texture table ids, buffer ids, framebuffer ids,
 * program handles) live inside the records, so the two id spaces never mix - the HAL registry
 * owns its space, the converter owns the GL space, and the record is the only translation.
 * <p>
 * Buffer contents are shadowed in a direct {@link ByteBuffer}: {@code mapBuffer} hands the
 * shadow itself out, and every write path flushes the affected range to the sink, so the GPU
 * copy and the CPU copy never disagree about what a draw reads.
 */
final class DirectGlRecords {

    private DirectGlRecords() {
    }

    static final class Texture {
        int target;
        int internalFormat;
        int width;
        int height;
        int depth = 1;
        long sinkId;
        boolean sinkCreated;
        final Map<Integer, Integer> intParams = new HashMap<>();
        final Map<Integer, Float> floatParams = new HashMap<>();
    }

    static final class Buffer {
        ByteBuffer shadow;
        long sinkId;
        boolean sinkCreated;
        boolean immutable;
        int usage;
    }

    static final class Sampler {
        final Map<Integer, Integer> intParams = new HashMap<>();
        final Map<Integer, Float> floatParams = new HashMap<>();
    }

    static final class Renderbuffer {
        int internalFormat;
        int width;
        int height;
        int samples;
        long sinkTextureId;
    }

    static final class Framebuffer {
        /** Attachment point (GL_COLOR_ATTACHMENT0 + i, GL_DEPTH_ATTACHMENT, ...) -> texture id. */
        final Map<Integer, Integer> textureAttachments = new HashMap<>();
        /** Attachment point -> renderbuffer id. */
        final Map<Integer, Integer> renderbufferAttachments = new HashMap<>();
        long sinkId;
        boolean sinkCreated;
        int width;
        int height;
        final List<Integer> drawBuffers = new ArrayList<>();
        int readBuffer = 0x8CE0; // GL_COLOR_ATTACHMENT0
    }

    static final class Shader {
        int type;
        String source = "";
        boolean compiled;
        String infoLog = "";
    }

    static final class Program {
        final List<Integer> attachedShaders = new ArrayList<>();
        final Map<String, Integer> preLinkAttribLocations = new HashMap<>();
        boolean linked;
        boolean failed;
        String infoLog = "";
        long sinkHandle;
        DirectGlShaderCompiler.CompiledProgram compiled;
        final Map<String, Integer> uniformLocations = new HashMap<>();
        final Map<String, Integer> attribLocations = new HashMap<>();
        /** Packed uniform values, laid out at the compiled block's member offsets. */
        ByteBuffer uniformBlock;
        boolean uniformsDirty;
        int uniformBlockBindingPoint = -1;
    }

    static final class Attrib {
        boolean enabled;
        boolean integer;
        int buffer;
        int size = 4;
        int type = 0x1406; // GL_FLOAT
        boolean normalized;
        int stride;
        long offset;
        int divisor;
        int bindingIndex = -1; // -1: the attribute index itself is the binding
    }

    static final class Binding {
        int buffer;
        long offset;
        int stride;
    }

    static final class VertexArray {
        final Map<Integer, Attrib> attribs = new HashMap<>();
        final Map<Integer, Binding> bindings = new HashMap<>();
        int elementBuffer;
    }
}
