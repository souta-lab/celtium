package org.soutalab.celtium.glcompat.dsa;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.GLStateManager;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.soutalab.celtium.glcompat.backend.DirectGlExecutor.EXECUTOR;

public class DSAARB extends DSAUnsupported {

    @Override
    public void generateMipmaps(int texture, int target) {
        EXECUTOR.generateTextureMipmap(texture);
    }

    @Override
    public void textureImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        // Note: DSA glTextureImage2D doesn't exist in ARB_direct_state_access
        // We need to use glTextureStorage2D + glTextureSubImage2D, or fall back to bind-based approach
        // For simplicity, use the fallback which binds, uploads, and restores
        super.textureImage2D(texture, target, level, internalformat, width, height, border, format, type, pixels);
    }

    @Override
    public void textureImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        super.textureImage2D(texture, target, level, internalformat, width, height, border, format, type, pixels);
    }

    @Override
    public void textureSubImage2D(int texture, int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        EXECUTOR.textureSubImage2D(texture, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    @Override
    public void textureSubImage2D(int texture, int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        EXECUTOR.textureSubImage2D(texture, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    @Override
    public void texParameteri(int texture, int target, int pname, int param) {
        param = GLStateManager.remapTexClamp(pname, param);
        if(!GLStateManager.updateTexParameteriCache(target, texture, pname, param)) return;

        EXECUTOR.textureParameteri(texture, target, pname, param);
    }

    @Override
    public void texParameterf(int texture, int target, int pname, float param) {
        param = GLStateManager.remapTexClamp(pname, param);
        if(!GLStateManager.updateTexParameterfCache(target, texture, pname, param)) return;

        EXECUTOR.textureParameterf(texture, target, pname, param);
    }

    @Override
    public void texParameteriv(int texture, int target, int pname, IntBuffer params) {
        GLStateManager.remapTexClampBuffer(pname, params);
        EXECUTOR.textureParameteriv(texture, target, pname, params);
    }

    @Override
    public void readBuffer(int framebuffer, int buffer) {
        EXECUTOR.namedFramebufferReadBuffer(framebuffer, buffer);
    }

    @Override
    public void drawBuffers(int framebuffer, IntBuffer buffers) {
        EXECUTOR.namedFramebufferDrawBuffers(framebuffer, buffers);
    }

    @Override
    public int getTexParameteri(int texture, int target, int pname) {
        return GLStateManager.getTexParameterOrDefault(texture, pname, () -> EXECUTOR.getTextureParameteri(texture, target, pname));
    }

    @Override
    public int getTexLevelParameteri(int texture, int level, int pname) {
        return EXECUTOR.getTextureLevelParameteri(texture, level, pname);
    }

    @Override
    public void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height) {
        EXECUTOR.copyTextureSubImage2D(destTexture, target, i, i1, i2, i3, i4, width, height);
    }

    @Override
    public void bindTextureToUnit(int unit, int texture) {
        if(GLStateManager.getBoundTextureForServerState(unit) == texture) return;

        // This is the fixed-function entry point, which only ever binds GL_TEXTURE_2D.
        bindTextureToUnit(GL11.GL_TEXTURE_2D, unit, texture);
    }

    @Override
    public void bindTextureToUnit(int target, int unit, int texture) {
        if (texture == 0) {
            super.bindTextureToUnit(unit, texture);
        } else {
            EXECUTOR.bindTextureUnit(unit, texture);
            final var binding = GLStateManager.getTextures().getTextureUnitBindings(unit);
            binding.beforeModify();
            // The unit keeps one slot for every target, so the slot has to remember which target it holds; otherwise
            // the 2D queries read a 3D or array name as the unit's 2D binding.
            binding.setBinding(texture, target);
        }
    }

    @Override
    public int bufferStorage(int target, FloatBuffer data, int usage) {
        final int buffer = EXECUTOR.createBuffers();
        EXECUTOR.namedBufferData(buffer, data, usage);
        return buffer;
    }

    @Override
    public void blitFramebuffer(int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2,
        int bufferChoice, int filter) {
        EXECUTOR.blitNamedFramebuffer(source, dest, offsetX, offsetY, width, height, offsetX2, offsetY2, width2, height2, bufferChoice, filter);
    }

    @Override
    public void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels) {
        GLStateManager.glNamedFramebufferTexture(fb, attachment, texture, levels);
    }

    @Override
    public int createFramebuffer() {
        return EXECUTOR.createFramebuffers();
    }

    @Override
    public int createTexture(int target) {
        return GLStateManager.glCreateTextures(target);
    }

    @Override
    public void textureStorage1D(int texture, int target, int levels, int internalFormat, int width) {
        EXECUTOR.textureStorage1D(texture, levels, internalFormat, width);
    }

    @Override
    public void textureStorage2D(int texture, int target, int levels, int internalFormat, int width, int height) {
        EXECUTOR.textureStorage2D(texture, levels, internalFormat, width, height);
    }

    @Override
    public void textureStorage3D(int texture, int target, int levels, int internalFormat, int width, int height, int depth) {
        EXECUTOR.textureStorage3D(texture, levels, internalFormat, width, height, depth);
    }
}
