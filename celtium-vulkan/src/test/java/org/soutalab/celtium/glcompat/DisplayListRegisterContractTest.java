package org.soutalab.celtium.glcompat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.state.LightStateRegister;

/**
 * The compiling-list register contract of the direct-GL display-list manager (G2-07,
 * NEON ruling 2026-11-28): the manager is the open/close owner for the direct-GL
 * path and publishes the compiling id to the shared register - the open before
 * {@code glNewList} returns, the restore (the outer id when nested, 0 at the
 * outermost level) after {@code glEndList} - and a converter excursion must not
 * overwrite the id in between.
 */
final class DisplayListRegisterContractTest {

    private static final int GL_COMPILE = 0x1300;

    private LightStateRegister register;

    @BeforeEach
    void attach() {
        this.register = new LightStateRegister();
        DirectGlDispatcher.attachRegister(this.register);
    }

    @AfterEach
    void detach() {
        DisplayListManager.abortIfLeaked();
        DirectGlDispatcher.attachRegister(null);
        this.register.close();
    }

    @Test
    void openPublishesAndCloseRestoresZero() {
        assertEquals(0, this.register.compilingListId());
        DisplayListManager.glNewList(7, GL_COMPILE);
        assertEquals(7, this.register.compilingListId());
        DisplayListManager.glEndList();
        assertEquals(0, this.register.compilingListId());
    }

    @Test
    void nestedListsRestoreTheOuterId() {
        DisplayListManager.glNewList(11, GL_COMPILE);
        assertEquals(11, this.register.compilingListId());
        DisplayListManager.glNewList(12, GL_COMPILE);
        assertEquals(12, this.register.compilingListId());
        DisplayListManager.glEndList();
        assertEquals(11, this.register.compilingListId());
        DisplayListManager.glEndList();
        assertEquals(0, this.register.compilingListId());
    }

    @Test
    void converterExcursionLeavesTheCompilingIdUntouched() {
        DisplayListManager.glNewList(9, GL_COMPILE);
        DirectGlDispatcher.enterLane3();
        DirectGlDispatcher.exitLane3();
        assertEquals(9, this.register.compilingListId());
        DisplayListManager.glEndList();
        assertEquals(0, this.register.compilingListId());
    }
}
