package org.soutalab.celtium.vulkan.gl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which GL commands a display list executes instead of compiling.
 * <p>
 * {@code glNewList(name, GL_COMPILE)} used to store every command except the two that delimit the list, so a
 * {@code glGetIntegerv} issued between NewList and EndList was deferred: the caller read its output array uninitialised
 * right after the call, and the stored command wrote through that native pointer later, during {@code glCallList}, when
 * the buffer could already be out of scope (#393). §5.4 of the OpenGL 1.2.1 specification names the commands that are
 * not compiled, and this is the set the stub now routes straight through.
 */
class DisplayListCompileContractTest {

    @Test
    @DisplayName("object-name allocation and deletion execute immediately")
    void nameAllocationAndDeletionAreNotCompiled() {
        assertTrue(CeltiumGlStub.notCompiledIntoDisplayList(CeltiumGlStub.SEM_GEN),
                "glGenLists has to return a name to the caller that asked for it");
        assertTrue(CeltiumGlStub.notCompiledIntoDisplayList(CeltiumGlStub.SEM_DELETE),
                "glDeleteLists retires names now; replaying it later would retire a name that was reissued");
    }

    @Test
    @DisplayName("queries execute immediately")
    void queriesAreNotCompiled() {
        assertTrue(CeltiumGlStub.notCompiledIntoDisplayList(CeltiumGlStub.SEM_GET_INTEGERV),
                "a caller between NewList and EndList reads the value it asked for");
        assertTrue(CeltiumGlStub.notCompiledIntoDisplayList(CeltiumGlStub.SEM_IS_ENABLED),
                "glIsEnabled is a query");
    }

    @Test
    @DisplayName("drawing state is compiled")
    void drawingAndStateAreCompiled() {
        assertFalse(CeltiumGlStub.notCompiledIntoDisplayList(CeltiumGlStub.SEM_NOOP),
                "state the stub records for the Vulkan side is part of the list's meaning");
    }
}
