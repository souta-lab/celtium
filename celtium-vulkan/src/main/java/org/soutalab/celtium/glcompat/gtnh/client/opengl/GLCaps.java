package org.soutalab.celtium.glcompat.gtnh.client.opengl;

/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * Only the types the converted GLSM front references are carried; the library
 * as a whole is NOT imported. See THIRD_PARTY_NOTICES.md.
 */


import org.lwjgl.opengl.GL30;

import org.soutalab.celtium.glcompat.gtnh.client.renderer.vao.VaoFunctions;

public final class GLCaps {

    private static final boolean bufferStorage;
    private static final boolean texStorage;

    // To replace the obfuscated OpenGlHelper method names
    public static final FBOFunctions FBO;
    public static final VaoFunctions VAO;

    public static boolean bufferStorageSupported() {
        return bufferStorage;
    }

    public static boolean texStorageSupported() {
        return texStorage;
    }

    public static boolean fboSupported() {
        return FBO != null;
    }

    static {
        org.soutalab.celtium.glcompat.GlCapabilities caps = org.soutalab.celtium.glcompat.GlCapabilities.EMULATED_GL33;
        bufferStorage = caps.OpenGL44 || caps.GL_ARB_buffer_storage;
        texStorage = caps.OpenGL42 || caps.GL_ARB_texture_storage;

        // The emulated context is GL 3.3 core, where framebuffer objects are core;
        // the EXT dispatch table from the LWJGL2 era has no counterpart here.
        FBO = new FBOGL3();

        VAO = UniversalVAO.getImplementation(caps);
    }

    private static final class FBOGL3 implements FBOFunctions {

        @Override
        public void glBindFramebuffer(int target, int framebuffer) {
            GL30.glBindFramebuffer(target, framebuffer);
        }

        @Override
        public void glBindRenderbuffer(int target, int renderbuffer) {
            GL30.glBindRenderbuffer(target, renderbuffer);
        }

        @Override
        public void glDeleteRenderbuffers(int renderbuffer) {
            GL30.glDeleteRenderbuffers(renderbuffer);
        }

        @Override
        public void glDeleteFramebuffers(int framebuffer) {
            GL30.glDeleteFramebuffers(framebuffer);
        }

        @Override
        public int glGenFramebuffers() {
            return GL30.glGenFramebuffers();
        }

        @Override
        public int glGenRenderbuffers() {
            return GL30.glGenRenderbuffers();
        }

        @Override
        public void glRenderbufferStorage(int target, int internalformat, int width, int height) {
            GL30.glRenderbufferStorage(target, internalformat, width, height);
        }

        @Override
        public void glFramebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) {
            GL30.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer);
        }

        @Override
        public int glCheckFramebufferStatus(int target) {
            return GL30.glCheckFramebufferStatus(target);
        }

        @Override
        public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
            GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
        }
    }

}
