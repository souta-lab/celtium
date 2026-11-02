package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one scale rule every integer {@code glColor*} spelling goes through, checked without a GPU.
 * <p>
 * The defect this pins: the transformer answered glColor4ui/4i/4us/4s and their buffer forms with a zero, so a mod
 * that set its colour that way drew with whatever colour had been set before - the same wrong-picture class the float
 * and byte spellings were pulled out of. The conversion is what has to agree between the widths: 255, 65535 and
 * 0xFFFFFFFF are all 1.0 (the maximum of that width, which is also the bit pattern a Java caller writes for it), and
 * half of each range is 0.5.
 * <p>
 * The unsigned reading is deliberate and is what the existing byte routes already did: GL's own signed normalisation
 * would read {@code (byte) 255} as -1 and clamp it to black, but the game-side writers fill these bytes as unsigned
 * values, so the same rule is applied at 16 and 32 bits.
 */
class LegacyColourComponentTest {

    /** Float tolerance for the mid-range values: the 32-bit scale cannot be exact in a float. */
    private static final float EPSILON = 1.0E-6F;

    @Test
    void byteEndpointsAndMiddle() {
        assertEquals(0.0F, CeltiumGlStub.celtium$colourComponent((byte) 0));
        // (byte) 255 is the bit pattern a caller writes for the maximum; it must be white, not GL's -1.
        assertEquals(1.0F, CeltiumGlStub.celtium$colourComponent((byte) 255));
        assertEquals(128.0F / 255.0F, CeltiumGlStub.celtium$colourComponent((byte) 128), EPSILON);
        assertEquals(1.0F / 255.0F, CeltiumGlStub.celtium$colourComponent((byte) 1), EPSILON);
    }

    @Test
    void shortEndpointsAndMiddle() {
        assertEquals(0.0F, CeltiumGlStub.celtium$colourComponent((short) 0));
        assertEquals(1.0F, CeltiumGlStub.celtium$colourComponent((short) 65535));
        assertEquals(1.0F, CeltiumGlStub.celtium$colourComponent((short) -1));
        assertEquals(32768.0F / 65535.0F, CeltiumGlStub.celtium$colourComponent((short) 32768), EPSILON);
        assertEquals(255.0F / 65535.0F, CeltiumGlStub.celtium$colourComponent((short) 255), EPSILON);
    }

    @Test
    void intEndpointsAndMiddle() {
        assertEquals(0.0F, CeltiumGlStub.celtium$colourComponent(0));
        // -1 is 0xFFFFFFFF, which is how a caller writes the maximum of an unsigned int.
        assertEquals(1.0F, CeltiumGlStub.celtium$colourComponent(-1));
        assertEquals(1.0F, CeltiumGlStub.celtium$colourComponent(0xFFFFFFFF), EPSILON);
        // 0x7FFFFFFF is half the range, not the maximum: only the full 32 bits reach 1.0, exactly as only 65535 (not
        // 32767) reaches it at the short width.
        assertEquals(0.5F, CeltiumGlStub.celtium$colourComponent(0x7FFFFFFF), EPSILON);
        assertEquals(0.5F, CeltiumGlStub.celtium$colourComponent(0x80000000), EPSILON);
        assertEquals(0.5F, CeltiumGlStub.celtium$colourComponent(Integer.MIN_VALUE), EPSILON);
    }

    @Test
    void everyWidthAgreesOnTheMaximumAndOnZero() {
        // The three routes have to agree, because a layout's colour bytes are written by whichever spelling the mod
        // used: a width whose maximum was not 1.0 would darken or brighten one mod against another.
        assertEquals(CeltiumGlStub.celtium$colourComponent((byte) 255), CeltiumGlStub.celtium$colourComponent((short) 65535));
        assertEquals(CeltiumGlStub.celtium$colourComponent((byte) 0), CeltiumGlStub.celtium$colourComponent((short) 0));
        assertEquals(CeltiumGlStub.celtium$colourComponent((byte) 0), CeltiumGlStub.celtium$colourComponent(0));
    }
}
