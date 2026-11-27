package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shader-code buffer of {@code VulkanDirectGlSink.createShaderModule} stays off the thread
 * MemoryStack, pinned as a source contract (MemoryStack sweep 2026-11-28, candidate C-1).
 * <p>
 * The method receives the whole runtime-compiled SPIR-V as an {@code int[]} whose size is set by
 * the mod's shaders and has no bound. It used to copy that array onto the thread MemoryStack
 * (64 KiB by default), next to the create-info structs in the same frame - the same overflow
 * shape that crashed the pack path in S5-b, where a 222 KiB module met the 64 KiB stack. The
 * path needs a device to run, so what is pinned here is the allocation shape: the code buffer is
 * heap-allocated at the input size, nothing input-sized is calloc'ed from the stack, and the
 * buffer is freed in a {@code finally} so the failure path of the checked creation call frees it
 * too. {@code VulkanModProgramPipeline} is the in-tree precedent for this shape.
 */
class DirectGlSinkShaderModuleHeapTest {

    /** The whole of {@code createShaderModule}, from its signature. */
    private static String createShaderModuleBody() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDirectGlSink.java"));
        return methodSlice(source, "private long createShaderModule(int[] words)", 2000);
    }

    // The code buffer is allocated on the heap at exactly the input size, before the stack frame
    // opens for the small create-info structs.
    @Test
    void codeBufferIsHeapAllocatedAtInputSize() throws IOException {
        String body = createShaderModuleBody();
        int allocated = body.indexOf("ByteBuffer code = MemoryUtil.memAlloc(words.length * Integer.BYTES);");
        int frameOpened = body.indexOf("MemoryStack.stackPush()");
        assertTrue(allocated >= 0, "the SPIR-V code buffer is heap-allocated at the input size");
        assertTrue(frameOpened > allocated, "the heap allocation precedes the stack frame");
    }

    // Nothing sized by the input is taken from the stack: the unbounded calloc that overflowed
    // the 64 KiB frame must not come back.
    @Test
    void noInputSizedStackAllocationRemains() throws IOException {
        String body = createShaderModuleBody();
        assertFalse(body.contains("stack.calloc(words.length"),
                "the input-sized stack calloc is gone");
        assertFalse(body.contains("stack.malloc(words.length"),
                "no input-sized stack malloc either");
    }

    // The buffer is freed in a finally after the creation call, so a failed vkCreateShaderModule
    // (which throws out of check) frees it exactly like the success path does.
    @Test
    void codeBufferIsFreedInFinallyAfterCreation() throws IOException {
        String body = createShaderModuleBody();
        int created = body.indexOf("vkCreateShaderModule(this.device, moduleInfo, null, module)");
        int finallyBlock = body.indexOf("} finally {", created);
        int freed = body.indexOf("MemoryUtil.memFree(code);", finallyBlock);
        assertTrue(created >= 0, "the module is created from the heap buffer");
        assertTrue(finallyBlock > created, "a finally block follows the creation call");
        assertTrue(freed > finallyBlock, "the heap buffer is freed in the finally block");
    }

    /** A slice of one method, from its signature, long enough to cover the lines this pin reads. */
    private static String methodSlice(String source, String signature, int length) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "cannot find " + signature);
        return source.substring(start, Math.min(source.length(), start + length));
    }

    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from "
                + Paths.get("").toAbsolutePath());
    }
}
