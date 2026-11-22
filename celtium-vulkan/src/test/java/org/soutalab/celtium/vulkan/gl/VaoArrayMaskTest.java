package org.soutalab.celtium.vulkan.gl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.gl.CeltiumGlStub.VaoAttrib;

/**
 * Pins the VAO half of the indexed decoder's layout decision.
 * <p>
 * The failure this is written against: Demonica's GLSM streaming renderer carries every field of its
 * interleaved layouts in generic VAO attributes and never touches the legacy client enables, so the
 * indexed decoder - which read only the legacy enables - refused its POSITION_TEX and POSITION_TEX_COLOR
 * batches by silence and the menu lost its panorama, buttons and logo while no counter anywhere moved.
 * The mask may only name a field when a record confirms the canonical placement the layout table's rows
 * describe; these tests pin both halves, the grants and the withholdings.
 */
class VaoArrayMaskTest {

    private static final int GL_BYTE = 0x1400;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_FLOAT = 0x1406;

    /** A record set with a position attribute at index 0 and the given fields after it. */
    private static VaoAttrib[] attribs(VaoAttrib... fields) {
        VaoAttrib[] all = new VaoAttrib[16];
        for (int i = 0; i < all.length; i++) {
            all[i] = new VaoAttrib();
        }
        all[0].enabled = true;
        all[0].pointerSet = true;
        all[0].size = 3;
        all[0].type = GL_FLOAT;
        all[0].offset = 0L;
        System.arraycopy(fields, 0, all, 1, fields.length);
        return all;
    }

    private static VaoAttrib field(int size, int type, boolean normalized, long offset) {
        VaoAttrib attrib = new VaoAttrib();
        attrib.enabled = true;
        attrib.pointerSet = true;
        attrib.size = size;
        attrib.type = type;
        attrib.normalized = normalized;
        attrib.offset = offset;
        return attrib;
    }

    @Test
    void positionTexGrantsTheTextureBit() {
        // POSITION_TEX (the panorama faces, button backgrounds, the logo): stride 20, texture at byte 12.
        VaoAttrib[] attribs = attribs(field(2, GL_FLOAT, false, 12L));
        assertEquals(4, CeltiumGlStub.celtium$vaoArrayMask(attribs, 20));
    }

    @Test
    void positionTexColorGrantsTextureAndColour() {
        // POSITION_TEX_COLOR (the menu text): stride 24, texture at 12, colour at 20 as 4 normalized bytes.
        VaoAttrib[] attribs = attribs(field(2, GL_FLOAT, false, 12L), field(4, GL_UNSIGNED_BYTE, true, 20L));
        assertEquals(6, CeltiumGlStub.celtium$vaoArrayMask(attribs, 24));
    }

    @Test
    void positionColorGrantsTheColourBit() {
        // POSITION_COLOR: stride 16, the colour directly after the position at byte 12.
        VaoAttrib[] attribs = attribs(field(4, GL_UNSIGNED_BYTE, true, 12L));
        assertEquals(2, CeltiumGlStub.celtium$vaoArrayMask(attribs, 16));
    }

    @Test
    void positionTexNormalGrantsTextureAndNormal() {
        // POSITION_TEX_NORMAL: stride 24, texture at 12, the byte normal at 20.
        VaoAttrib[] attribs = attribs(field(2, GL_FLOAT, false, 12L), field(3, GL_BYTE, true, 20L));
        assertEquals(5, CeltiumGlStub.celtium$vaoArrayMask(attribs, 24));
    }

    @Test
    void theLegacyThirtyTwoByteInterleaveGrantsAllThree() {
        // Texture at 12, colour at 20, byte normal at 24: the stride-32 row the repack carries.
        VaoAttrib[] attribs = attribs(field(2, GL_FLOAT, false, 12L), field(4, GL_UNSIGNED_BYTE, true, 20L),
                field(3, GL_BYTE, true, 24L));
        assertEquals(7, CeltiumGlStub.celtium$vaoArrayMask(attribs, 32));
    }

    @Test
    void aSwappedInterleaveGrantsNothing() {
        // Colour before texture is an interleave the layout table does not know: at stride 24 the colour at
        // byte 12 is not the row's placement, and a texture field at byte 20 is not the canonical one either.
        // Granting either bit would decode the batch as a layout it is not - wrong pixels, silently.
        VaoAttrib[] attribs = attribs(field(4, GL_UNSIGNED_BYTE, true, 12L), field(2, GL_FLOAT, false, 20L));
        assertEquals(0, CeltiumGlStub.celtium$vaoArrayMask(attribs, 24));
    }

    @Test
    void aFloatFourPastPositionIsNotTheTextureField() {
        // The generic mask accepts any enabled float attribute past position as texture; this one must not:
        // a 4-float field at byte 12 is not the 2-float texture coordinate of any row.
        VaoAttrib[] attribs = attribs(field(4, GL_FLOAT, false, 12L));
        assertEquals(0, CeltiumGlStub.celtium$vaoArrayMask(attribs, 20));
    }

    @Test
    void disabledAndUnsetRecordsGrantNothing() {
        VaoAttrib disabled = field(2, GL_FLOAT, false, 12L);
        disabled.enabled = false;
        assertEquals(0, CeltiumGlStub.celtium$vaoArrayMask(attribs(disabled), 20));
        VaoAttrib unset = field(2, GL_FLOAT, false, 12L);
        unset.pointerSet = false;
        assertEquals(0, CeltiumGlStub.celtium$vaoArrayMask(attribs(unset), 20));
    }

    @Test
    void anUnnormalizedColourGrantsNothing() {
        // The colour of every known row is a normalized byte field; a raw byte field at the same offset is a
        // different layout (an integer attribute), not the colour.
        VaoAttrib[] attribs = attribs(field(2, GL_FLOAT, false, 12L), field(4, GL_UNSIGNED_BYTE, false, 20L));
        assertEquals(4, CeltiumGlStub.celtium$vaoArrayMask(attribs, 24));
    }
}
