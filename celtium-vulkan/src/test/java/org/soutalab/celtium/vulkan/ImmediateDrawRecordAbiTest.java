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
 * that is quietly a few bytes out of phase. The lit struct is checked the same way, and a third test holds the lit push
 * block's declared range to the last offset its vertex stage declares - the fog block at 224/240 is what made that block
 * 256 bytes, and a range shorter than the declaration is a pipeline-creation error rather than a wrong picture.
 * <p>
 * This is a layout test, not evidence about rendering.
 */
class ImmediateDrawRecordAbiTest {

    @Test
    void theRecordIsThePushBlockAndTheShaderAgreesWithIt() throws IOException {
        assertEquals(256, ImmediateDrawRecords.RECORD_BYTES,
                "the record is the widest push block of the immediate families (the lit ITEM block's 256 bytes)");
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

    @Test
    void theLitRecordUsesTheSameStride() throws IOException {
        String source = Files.readString(findShader("immediate_tex_color_lit_records.vert"));
        assertTrue(source.contains("std430"), "the lit records binding is std430");
        assertTrue(source.contains("records[gl_InstanceIndex]"), "the lit variant indexes by the instance index");
        int structStart = source.indexOf("struct DrawRecord {");
        assertTrue(structStart >= 0, "the lit record struct is declared");
        int structEnd = source.indexOf("};", structStart);
        String body = source.substring(structStart, structEnd);
        long bytes = 64L * count(body, "mat4 ") + 16L * count(body, "vec4 ") + 16L * count(body, "vec2 ");
        // The lit block's 256 bytes: the lighting payload, the light coordinate, the bindless index's slot and the fog
        // block. The two families share one record array, so the unlit struct pads to the same stride.
        assertEquals(ImmediateDrawRecords.RECORD_BYTES, bytes,
                "the lit struct must be one record long, padded to the shared stride");
    }

    @Test
    void theLitPushRangeCoversEveryOffsetTheVertexStageDeclares() throws IOException {
        String source = Files.readString(findShader("immediate_tex_color_lit.vert"));
        int last = -1;
        int widest = 0;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("layout\\(offset = (\\d+)\\)\\s+(vec4|vec2|float)").matcher(source);
        while (matcher.find()) {
            int offset = Integer.parseInt(matcher.group(1));
            int size = "vec4".equals(matcher.group(2)) ? 16 : "vec2".equals(matcher.group(2)) ? 8 : 4;
            last = Math.max(last, offset);
            widest = Math.max(widest, offset + size);
        }
        assertTrue(last >= 0, "the lit vertex stage declares explicit offsets");
        assertEquals(240, last, "the fog block's params are the last member the lit vertex stage declares");
        assertEquals(256, widest, "the lit block ends at 256 bytes");
        assertEquals(256, VulkanImmediateItemPipeline.celtium$pushBytesLit(),
                "the pipeline's declared range must cover the whole block: a shorter range is a pipeline-creation error");
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
