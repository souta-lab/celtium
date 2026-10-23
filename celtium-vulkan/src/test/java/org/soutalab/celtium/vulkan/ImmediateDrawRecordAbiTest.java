package org.soutalab.celtium.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards the byte-for-byte contract between a draw record and the push block it stands in for.
 * <p>
 * The record-reading shader declares the same block as an {@code std430} struct, and the backend writes it by copying
 * the push block. Nothing in the type system holds those two together, so this test reads the shader and sums the
 * declared members: mat4 plus three vec4s plus the alpha/colour vec4 plus the fog block. If either side gains or loses a
 * member, the sum stops matching {@link ImmediateDrawRecords#RECORD_BYTES} and this fails instead of producing a picture
 * that is quietly a few bytes out of phase.
 * <p>
 * This is a layout test, not evidence about rendering.
 */
class ImmediateDrawRecordAbiTest {

    @Test
    void theRecordIsThePushBlockAndTheShaderAgreesWithIt() throws IOException {
        assertEquals(208, ImmediateDrawRecords.RECORD_BYTES,
                "the record is the 208-byte push block of the immediate families");
        Path shader = findShader("immediate_tex_color_records.vert");
        String source = Files.readString(shader);
        assertTrue(source.contains("std430"), "the records binding is std430");
        assertTrue(source.contains("records[gl_InstanceIndex]"),
                "the variant indexes the record array by the instance index, which is the command's firstInstance");
        int structStart = source.indexOf("struct DrawRecord {");
        assertTrue(structStart >= 0, "the record struct is declared");
        int structEnd = source.indexOf("};", structStart);
        assertTrue(structEnd > structStart, "the record struct is terminated");
        String body = source.substring(structStart, structEnd);
        long bytes = 0L;
        bytes += 64L * count(body, "mat4 ");
        bytes += 16L * count(body, "vec4 ");
        bytes += 16L * count(body, "vec2 ");
        assertEquals(ImmediateDrawRecords.RECORD_BYTES, bytes,
                "the shader's DrawRecord must be exactly one record long: member sizes are checked against RECORD_BYTES");
        int matrixOffset = source.indexOf("mat4 u_ProjectionMatrix;");
        int alphaOffset = source.indexOf("vec4 u_AlphaColour;");
        int fogOffset = source.indexOf("vec4 u_FogColor;");
        assertTrue(matrixOffset > 0 && alphaOffset > matrixOffset && fogOffset > alphaOffset,
                "the members keep the push block's order: matrix, affines, alpha/colour, fog");
    }

    private static int count(String haystack, String needle) {
        int seen = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            seen++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return seen;
    }

    private static Path findShader(String name) throws IOException {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int depth = 0; depth < 4 && dir != null; depth++) {
            Path candidate = dir.resolve("src/main/shaders").resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            candidate = dir.resolve("celtium-vulkan/src/main/shaders").resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IOException("shader not found from " + System.getProperty("user.dir") + ": " + name);
    }
}
