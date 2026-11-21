package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The font atlas registry's generation gate, pinned without a device.
 * <p>
 * A registration describes one page of one atlas build and is honoured only while that build is the current
 * one. The gate is the atlas's GL name, and GL names are recycled: a rebuild deletes the old atlas texture and
 * the new build can be handed the very same name. A page the old build registered and the new build does not
 * have must not resurface under the recycled name - {@link FontAtlasRegistry#remapFor} would hand its stale
 * rectangle to {@code FontBatch}, which would rewrite a segment's UVs into an atlas that does not hold the page
 * there. Moving the gate therefore drops the previous build's registrations; the game side registers the new
 * build's pages immediately after.
 */
class FontAtlasRegistryTest {

    @BeforeEach
    void resetRegistry() {
        FontAtlasRegistry.invalidate();
    }

    @AfterEach
    void clearRegistry() {
        FontAtlasRegistry.invalidate();
    }

    @Test
    void aRegistrationIsHonouredWhileItsAtlasIsCurrent() {
        FontAtlasRemap remap = new FontAtlasRemap(5, 0.5f, 0.25f, 0.5f, 0.0f);
        FontAtlasRegistry.setCurrentAtlas(5);
        FontAtlasRegistry.register(100, remap);
        assertSame(remap, FontAtlasRegistry.remapFor(100));
    }

    @Test
    void aRegistrationGoesInertWhenAnotherAtlasBecomesCurrent() {
        FontAtlasRegistry.setCurrentAtlas(5);
        FontAtlasRegistry.register(100, new FontAtlasRemap(5, 0.5f, 0.25f, 0.5f, 0.0f));
        FontAtlasRegistry.setCurrentAtlas(7);
        assertNull(FontAtlasRegistry.remapFor(100));
    }

    @Test
    void aStaleRegistrationDoesNotResurfaceWhenTheAtlasNameIsRecycled() {
        // Build one: atlas name 5, page 100 registered.
        FontAtlasRegistry.setCurrentAtlas(5);
        FontAtlasRegistry.register(100, new FontAtlasRemap(5, 0.5f, 0.25f, 0.5f, 0.0f));
        // Build two: a different atlas, name 7. Page 100 is not part of it.
        FontAtlasRegistry.setCurrentAtlas(7);
        // Build three: the allocator hands name 5 out again, to a new atlas whose pages are registered
        // after the gate moves - page 100 is not among them (its own name may since have been recycled to
        // an unrelated texture). The build-one rectangle must stay dead.
        FontAtlasRegistry.setCurrentAtlas(5);
        assertNull(FontAtlasRegistry.remapFor(100));
    }

    @Test
    void aPageRegisteredForTheNewBuildAnswersWithTheNewRectangle() {
        FontAtlasRegistry.setCurrentAtlas(5);
        FontAtlasRegistry.register(100, new FontAtlasRemap(5, 0.5f, 0.25f, 0.5f, 0.0f));
        FontAtlasRegistry.setCurrentAtlas(7);
        FontAtlasRegistry.setCurrentAtlas(5);
        FontAtlasRemap rebuilt = new FontAtlasRemap(5, 0.25f, 0.0f, 0.25f, 0.5f);
        FontAtlasRegistry.register(100, rebuilt);
        assertSame(rebuilt, FontAtlasRegistry.remapFor(100));
    }
}
