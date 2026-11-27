package org.embeddedt.embeddium.impl.render.chunk.shader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * A compiled OpenGL shader object.
 */
public class GlShader extends GlObject {
    private static final Logger LOGGER = LogManager.getLogger(GlShader.class);

    private final String name;

    public GlShader(ShaderType type, String name, String src) {
        this.name = name;

        int handle = GL20C.glCreateShader(type.id);
        glShaderSourceWorkaround(handle, src);
        GL20C.glCompileShader(handle);

        String log = GL20C.glGetShaderInfoLog(handle);

        if (!log.isEmpty()) {
            LOGGER.warn("Shader compilation log for " + this.name + ": " + log);
        }

        int result = GL20C.glGetShaderi(handle, GL20.GL_COMPILE_STATUS);

        if (result != GL20.GL_TRUE) {
            throw new RuntimeException("Shader compilation failed, see log for details");
        }

        this.setHandle(handle);
    }

    /**
     * AMD driver workaround, carried over verbatim from the retired LWJGL service's
     * {@code glShaderSourceSafe}: pass null for the string length to force null-terminator reliance.
     * Some AMD drivers don't receive or interpret the length correctly, resulting in an access
     * violation when the driver tries to read past the string memory.
     */
    private static void glShaderSourceWorkaround(int shader, CharSequence source) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, true);
            PointerBuffer pointers = stack.mallocPointer(1);
            pointers.put(sourceBuffer);
            GL20C.nglShaderSource(shader, 1, pointers.address0(), 0);
            APIUtil.apiArrayFree(pointers.address0(), 1);
        }
    }

    public String getName() {
        return this.name;
    }

    @Override
    protected void destroyInternal() {
        GL20C.glDeleteShader(this.handle());
    }
}
