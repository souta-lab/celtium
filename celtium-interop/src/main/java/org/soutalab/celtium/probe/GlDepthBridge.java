package org.soutalab.celtium.probe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.lwjgl.system.MemoryStack;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL45C.*;

/** GPU conversions around an unchanged RGBA8 / D24-renderbuffer FBO. */
final class GlDepthBridge implements AutoCloseable {
    private static final float[] PRECISION_DEPTHS = { 0, 1f / 16777215f, 0.000001f, 0.0001f,
            0.1f, 0.25f, 0.499f, 0.4999999f, 0.5f, 0.5000001f, 0.75f, 0.9f, 0.9999f, 0.9999999f, 1 };
    final int width, height;
    int originalFbo, originalColor, originalDepth;
    int sharedFbo;
    private int scratchFbo, scratchDepth;
    private int program, vertexArray;
    private int modeLocation, depthLocation, colorLocation;
    private final SharedImage color, depth;
    private final boolean ownsOriginal;

    GlDepthBridge(SharedImage color, SharedImage depth) {
        this(color, depth, 0, 0, 0);
    }

    GlDepthBridge(SharedImage color, SharedImage depth, int existingFbo, int existingColor, int existingDepth) {
        this.color = color;
        this.depth = depth;
        width = color.width;
        height = color.height;
        ownsOriginal = existingFbo == 0;
        originalFbo = existingFbo;
        originalColor = existingColor;
        originalDepth = existingDepth;
        try {
            allocate();
        } catch (RuntimeException | LinkageError error) {
            close();
            throw error;
        }
    }

    private void allocate() {
        if (ownsOriginal) {
            originalColor = glCreateTextures(GL_TEXTURE_2D);
            glTextureStorage2D(originalColor, 1, GL_RGBA8, width, height);
            originalDepth = glCreateRenderbuffers();
            glNamedRenderbufferStorage(originalDepth, GL_DEPTH_COMPONENT24, width, height);
            originalFbo = glCreateFramebuffers();
            glNamedFramebufferTexture(originalFbo, GL_COLOR_ATTACHMENT0, originalColor, 0);
            glNamedFramebufferRenderbuffer(originalFbo, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, originalDepth);
            glNamedFramebufferDrawBuffer(originalFbo, GL_COLOR_ATTACHMENT0);
            glNamedFramebufferReadBuffer(originalFbo, GL_COLOR_ATTACHMENT0);
        } else {
            if (!glIsFramebuffer(originalFbo) || !glIsTexture(originalColor) || !glIsRenderbuffer(originalDepth)
                    || glGetTextureLevelParameteri(originalColor, 0, GL_TEXTURE_INTERNAL_FORMAT) != GL_RGBA8
                    || glGetNamedRenderbufferParameteri(originalDepth, GL_RENDERBUFFER_INTERNAL_FORMAT) != GL_DEPTH_COMPONENT24
                    || glGetNamedRenderbufferParameteri(originalDepth, GL_RENDERBUFFER_STENCIL_SIZE) != 0
                    || glGetNamedRenderbufferParameteri(originalDepth, GL_RENDERBUFFER_SAMPLES) > 1
                    || glGetTextureLevelParameteri(originalColor, 0, GL_TEXTURE_WIDTH) != width
                    || glGetTextureLevelParameteri(originalColor, 0, GL_TEXTURE_HEIGHT) != height
                    || glGetNamedFramebufferAttachmentParameteri(originalFbo, GL_COLOR_ATTACHMENT0, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) != originalColor
                    || glGetNamedFramebufferAttachmentParameteri(originalFbo, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) != originalDepth)
                throw new IllegalStateException("Existing FBO does not match the verified RGBA8/D24-renderbuffer single-sample contract");
        }
        complete(originalFbo);

        scratchDepth = glCreateTextures(GL_TEXTURE_2D);
        glTextureStorage2D(scratchDepth, 1, GL_DEPTH_COMPONENT24, width, height);
        glTextureParameteri(scratchDepth, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTextureParameteri(scratchDepth, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTextureParameteri(scratchDepth, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        scratchFbo = glCreateFramebuffers();
        glNamedFramebufferTexture(scratchFbo, GL_DEPTH_ATTACHMENT, scratchDepth, 0);
        glNamedFramebufferDrawBuffer(scratchFbo, GL_NONE);
        glNamedFramebufferReadBuffer(scratchFbo, GL_NONE);
        complete(scratchFbo);

        sharedFbo = glCreateFramebuffers();
        glNamedFramebufferTexture(sharedFbo, GL_COLOR_ATTACHMENT0, color.texture, 0);
        glNamedFramebufferTexture(sharedFbo, GL_DEPTH_ATTACHMENT, depth.texture, 0);
        glNamedFramebufferDrawBuffer(sharedFbo, GL_COLOR_ATTACHMENT0);
        glNamedFramebufferReadBuffer(sharedFbo, GL_COLOR_ATTACHMENT0);
        complete(sharedFbo);

        int vertex = shader(GL_VERTEX_SHADER,
                "#version 330 core\nuniform float depthValue;\n"
                + "void main(){vec2 p[3]=vec2[](vec2(-1,-1),vec2(3,-1),vec2(-1,3));"
                + "gl_Position=vec4(p[gl_VertexID],depthValue*2.0-1.0,1.0);}\n");
        int fragment = shader(GL_FRAGMENT_SHADER,
                "#version 330 core\nuniform int copyDepth; uniform sampler2D sourceDepth;"
                + "uniform vec4 colorValue; uniform float depthValue; out vec4 outputColor;\n"
                + "void main(){outputColor=colorValue; gl_FragDepth=copyDepth==1"
                + "?texelFetch(sourceDepth,ivec2(gl_FragCoord.xy),0).r:depthValue;}\n");
        program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
            throw new IllegalStateException("GL bridge link failed: " + glGetProgramInfoLog(program));
        modeLocation = glGetUniformLocation(program, "copyDepth");
        depthLocation = glGetUniformLocation(program, "depthValue");
        colorLocation = glGetUniformLocation(program, "colorValue");
        glProgramUniform1i(program, glGetUniformLocation(program, "sourceDepth"), 0);
        vertexArray = glCreateVertexArrays();
        SharedImage.glCheck("create ordinary and shared FBOs and conversion shader");
    }

    private static int shader(int type, String source) {
        int result = glCreateShader(type);
        glShaderSource(result, source);
        glCompileShader(result);
        if (glGetShaderi(result, GL_COMPILE_STATUS) == GL_FALSE)
            throw new IllegalStateException("GL bridge shader failed: " + glGetShaderInfoLog(result));
        return result;
    }

    private static void complete(int fbo) {
        int status = glCheckNamedFramebufferStatus(fbo, GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Incomplete FBO: 0x" + Integer.toHexString(status));
    }

    void prepareScene() {
        clearOriginal();
        solid(0.25f, 1, 0, 0, 0, 0, width / 2, height);
    }

    void clearOriginal() {
        glDisable(GL_SCISSOR_TEST);
        glColorMask(true, true, true, true);
        glDepthMask(true);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glClearNamedFramebufferfv(originalFbo, GL_COLOR, 0, stack.floats(0, 0, 0, 1));
            glClearNamedFramebufferfv(originalFbo, GL_DEPTH, 0, stack.floats(1));
        }
    }

    void prepareDepthPattern() {
        clearOriginal();
        for (int i = 0; i < PRECISION_DEPTHS.length; i++) {
            int start = i * width / PRECISION_DEPTHS.length;
            int end = (i + 1) * width / PRECISION_DEPTHS.length;
            solid(PRECISION_DEPTHS[i], 1, 0, 0, start, 0, end - start, height);
        }
    }

    JsonArray verifyDepthPattern() {
        JsonArray result = new JsonArray();
        for (int i = 0; i < PRECISION_DEPTHS.length; i++) {
            int start = i * width / PRECISION_DEPTHS.length;
            int end = (i + 1) * width / PRECISION_DEPTHS.length;
            float expected = (float) (Math.round(PRECISION_DEPTHS[i] * (double) 16777215) / (double) 16777215);
            sample(result, (start + end) / 2, height / 2, PRECISION_DEPTHS[i] < 1 ? 255 : 0, 0, 0, expected);
            result.get(result.size() - 1).getAsJsonObject().addProperty("sourceDepth", PRECISION_DEPTHS[i]);
        }
        SharedImage.glCheck("15-value D24 precision pattern");
        return result;
    }

    private void state(int fbo) {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
        glViewport(0, 0, width, height);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_FRAMEBUFFER_SRGB);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDepthRange(0, 1);
        glClipControl(GL_LOWER_LEFT, GL_NEGATIVE_ONE_TO_ONE);
        glUseProgram(program);
        glBindVertexArray(vertexArray);
    }

    void solid(float value, float r, float g, float b, int x, int y, int w, int h) {
        state(originalFbo);
        glColorMask(true, true, true, true);
        glDepthFunc(GL_LESS);
        glEnable(GL_SCISSOR_TEST);
        glScissor(x, y, w, h);
        glUniform1i(modeLocation, 0);
        glUniform1f(depthLocation, value);
        glUniform4f(colorLocation, r, g, b, 1);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glDisable(GL_SCISSOR_TEST);
    }

    private void convertDepth(int sourceTexture, int targetFbo) {
        state(targetFbo);
        glColorMask(false, false, false, false);
        glDepthFunc(GL_ALWAYS);
        glUniform1i(modeLocation, 1);
        glActiveTexture(GL_TEXTURE0);
        glBindSampler(0, 0);
        glBindTexture(GL_TEXTURE_2D, sourceTexture);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindTexture(GL_TEXTURE_2D, 0);
        glColorMask(true, true, true, true);
    }

    void toVulkan() {
        glBlitNamedFramebuffer(originalFbo, sharedFbo, 0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBlitNamedFramebuffer(originalFbo, scratchFbo, 0, 0, width, height, 0, 0, width, height, GL_DEPTH_BUFFER_BIT, GL_NEAREST);
        convertDepth(scratchDepth, sharedFbo);
        SharedImage.glCheck("GL RGBA8 copy and D24-to-D32 shader conversion");
    }

    void fromVulkan() {
        glBlitNamedFramebuffer(sharedFbo, originalFbo, 0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        convertDepth(depth.texture, originalFbo);
        SharedImage.glCheck("GL RGBA8 return and D32-to-D24 shader conversion");
        if (glGetNamedFramebufferAttachmentParameteri(originalFbo, GL_COLOR_ATTACHMENT0, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) != originalColor
                || glGetNamedFramebufferAttachmentParameteri(originalFbo, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) != originalDepth)
            throw new IllegalStateException("The original FBO attachments were replaced");
    }

    JsonArray verify(boolean foreground) {
        JsonArray samples = new JsonArray();
        sample(samples, width / 4, height * 3 / 4, 255, 0, 0, 0.25f);
        sample(samples, width * 3 / 4, height * 3 / 4, 0, 255, 0, 0.5f);
        if (foreground) {
            sample(samples, width / 4, height / 4, 0, 255, 255, 0.125f);
            sample(samples, width * 3 / 4, height / 4, 0, 255, 255, 0.125f);
        }
        SharedImage.glCheck("pixel/depth verification readback");
        return samples;
    }

    private void sample(JsonArray result, int x, int y, int r, int g, int b, float expectedDepth) {
        // CPU readback is the test oracle only; both depth conversion paths run on the GPU.
        glBindFramebuffer(GL_READ_FRAMEBUFFER, originalFbo);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer rgba = stack.malloc(4);
            FloatBuffer actualDepth = stack.mallocFloat(1);
            glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
            glReadPixels(x, y, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, actualDepth);
            int red = rgba.get(0) & 255, green = rgba.get(1) & 255, blue = rgba.get(2) & 255;
            if (red != r || green != g || blue != b || (rgba.get(3) & 255) != 255
                    || !Float.isFinite(actualDepth.get(0)) || Math.abs(actualDepth.get(0) - expectedDepth) > 2f / 16777215f)
                throw new IllegalStateException("Pixel mismatch at " + x + "," + y + ": " + red + "," + green + "," + blue
                        + " depth=" + actualDepth.get(0) + "; expected " + r + "," + g + "," + b + " depth=" + expectedDepth);
            JsonObject value = new JsonObject();
            value.addProperty("x", x); value.addProperty("y", y);
            value.addProperty("r", red); value.addProperty("g", green); value.addProperty("b", blue);
            value.addProperty("depth", actualDepth.get(0));
            result.add(value);
        }
    }

    void screenshot(Path path) throws IOException {
        ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memAlloc(width * height * 4);
        try {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, originalFbo);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int offset = (y * width + x) * 4;
                int pixel = ((pixels.get(offset + 3) & 255) << 24) | ((pixels.get(offset) & 255) << 16)
                        | ((pixels.get(offset + 1) & 255) << 8) | (pixels.get(offset + 2) & 255);
                image.setRGB(x, height - y - 1, pixel);
            }
            java.nio.file.Files.createDirectories(path.toAbsolutePath().getParent());
            if (!ImageIO.write(image, "png", path.toFile())) throw new IOException("PNG writer unavailable");
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(pixels);
        }
    }

    @Override public void close() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindVertexArray(0);
        glUseProgram(0);
        glDeleteVertexArrays(vertexArray);
        if (program != 0) glDeleteProgram(program);
        glDeleteFramebuffers(sharedFbo);
        glDeleteFramebuffers(scratchFbo);
        glDeleteTextures(scratchDepth);
        if (ownsOriginal) {
            glDeleteFramebuffers(originalFbo);
            glDeleteTextures(originalColor);
            glDeleteRenderbuffers(originalDepth);
        }
    }
}
