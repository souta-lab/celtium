package org.soutalab.celtium.probe;

import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.DoubleBuffer;
import static org.lwjgl.opengl.GL45C.*;

/** Restore the GL state touched by the test before returning to Minecraft. */
final class GlStateSnapshot implements AutoCloseable {
    private final int program = glGetInteger(GL_CURRENT_PROGRAM);
    private final int vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
    private final int drawFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
    private final int readFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
    private final int depthFunction = glGetInteger(GL_DEPTH_FUNC);
    private final boolean depthWrite = glGetBoolean(GL_DEPTH_WRITEMASK);
    private final int[] viewport = new int[4], scissor = new int[4];
    private final boolean[] colorWrite = new boolean[4];
    private final int[] enabledStates = { GL_DEPTH_TEST, GL_BLEND, GL_CULL_FACE, GL_STENCIL_TEST, GL_SCISSOR_TEST, GL_FRAMEBUFFER_SRGB };
    private final boolean[] enabled = new boolean[enabledStates.length];
    private final int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
    private final int texture0;
    private final int sampler0 = glGetIntegeri(GL_SAMPLER_BINDING, 0);
    private final int clipOrigin = glGetInteger(GL_CLIP_ORIGIN);
    private final int clipDepth = glGetInteger(GL_CLIP_DEPTH_MODE);
    private final double[] depthRange = new double[2];

    GlStateSnapshot() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer values = stack.mallocInt(4);
            glGetIntegerv(GL_VIEWPORT, values); values.get(viewport); values.clear();
            glGetIntegerv(GL_SCISSOR_BOX, values); values.get(scissor);
            ByteBuffer mask = stack.malloc(4);
            glGetBooleanv(GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < 4; i++) colorWrite[i] = mask.get(i) != 0;
            DoubleBuffer range = stack.mallocDouble(2);
            glGetDoublev(GL_DEPTH_RANGE, range); range.get(depthRange);
        }
        for (int i = 0; i < enabledStates.length; i++) enabled[i] = glIsEnabled(enabledStates[i]);
        glActiveTexture(GL_TEXTURE0);
        texture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
        glActiveTexture(activeTexture);
    }

    @Override public void close() {
        glUseProgram(program);
        glBindVertexArray(vao);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFbo);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
        glDepthFunc(depthFunction);
        glDepthMask(depthWrite);
        glDepthRange(depthRange[0], depthRange[1]);
        glClipControl(clipOrigin, clipDepth);
        glColorMask(colorWrite[0], colorWrite[1], colorWrite[2], colorWrite[3]);
        for (int i = 0; i < enabledStates.length; i++) {
            if (enabled[i]) glEnable(enabledStates[i]); else glDisable(enabledStates[i]);
        }
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture0);
        glBindSampler(0, sampler0);
        glActiveTexture(activeTexture);
        SharedImage.glCheck("restore Minecraft GL state");
    }
}
