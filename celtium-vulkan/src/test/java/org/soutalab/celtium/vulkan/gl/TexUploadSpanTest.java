package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The span a compiled texture upload's payload capture copies has to be the span the conversion later
 * reads: rows padded to {@code GL_UNPACK_ALIGNMENT}, the last row unpadded.
 * <p>
 * The capture used to size itself by a fixed alignment of 4. At alignment 8 it copied too little, and the
 * conversion at replay read past the owned buffer; with a row length in play it misaligned every row after
 * the first. A row length or skip the conversion refuses is refused here too, as an unsized payload.
 */
class TexUploadSpanTest {

    private static CeltiumGlStub stub() throws Exception {
        var ctor = CeltiumGlStub.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return (CeltiumGlStub) ctor.newInstance();
    }

    private static void setField(CeltiumGlStub stub, String name, int value) throws Exception {
        var field = CeltiumGlStub.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(stub, value);
    }

    @Test
    void theSpanPadsRowsToTheUnpackAlignment() throws Exception {
        CeltiumGlStub stub = stub();
        setField(stub, "unpackAlignment", 8);
        // A 9-byte row pads to 16: two padded rows plus the last row unpadded.
        assertEquals(16L * 2 + 9L, stub.texUploadSpanBytes(9, 3, 3));
        setField(stub, "unpackAlignment", 1);
        assertEquals(9L * 2 + 9L, stub.texUploadSpanBytes(9, 3, 3), "alignment 1 pads nothing");
        setField(stub, "unpackAlignment", 4);
        assertEquals(12L * 2 + 9L, stub.texUploadSpanBytes(9, 3, 3), "the old fixed span, at the default");
    }

    @Test
    void aRowLengthOrSkipTheConversionRefusesIsRefusedHereToo() throws Exception {
        CeltiumGlStub stub = stub();
        setField(stub, "unpackRowLength", 4);
        assertEquals(-1L, stub.texUploadSpanBytes(9, 3, 3), "a row length wider than the image");
        setField(stub, "unpackRowLength", 3);
        assertEquals(12L * 2 + 9L, stub.texUploadSpanBytes(9, 3, 3),
                "a row length equal to the width is the conversion's own exception");
        setField(stub, "unpackRowLength", 0);
        setField(stub, "unpackSkipPixels", 1);
        assertEquals(-1L, stub.texUploadSpanBytes(9, 3, 3), "a pixel skip");
        setField(stub, "unpackSkipPixels", 0);
        setField(stub, "unpackSkipRows", 1);
        assertEquals(-1L, stub.texUploadSpanBytes(9, 3, 3), "a row skip");
    }
}
