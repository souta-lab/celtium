package org.soutalab.celtium.glcompat.gtnh.client.opengl;

/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * Only the types the converted GLSM front references are carried; the library
 * as a whole is NOT imported. See THIRD_PARTY_NOTICES.md.
 */


public interface FBOFunctions {

    void glBindFramebuffer(int target, int framebuffer);

    void glBindRenderbuffer(int target, int renderbuffer);

    void glDeleteRenderbuffers(int renderbuffer);

    void glDeleteFramebuffers(int framebuffer);

    int glGenFramebuffers();

    int glGenRenderbuffers();

    void glRenderbufferStorage(int target, int internalformat, int width, int height);

    void glFramebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer);

    int glCheckFramebufferStatus(int target);

    void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level);
}
