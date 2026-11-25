package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The failure-path cleanup of {@code ChainRecorder.Program.create}, pinned as a source contract
 * (audit-vulkan-core F3).
 * <p>
 * Creating a program makes three device objects in sequence - the pipeline layout, the vertex shader
 * module, the fragment shader module - and then the pipeline. The caller ({@code programFor}) caches
 * every refusal in {@code programRefusals} and never retries the key, so a handle dropped when one of
 * the later steps threw leaked for the life of the device: one layout and two modules per refused
 * program. The leak is bounded (one refusal per program key), but the cleanup is the contract, and it
 * is structural - the path needs a device to run, so what is pinned here is that the handles are
 * tracked in locals from creation and that the failure path destroys exactly the ones that exist,
 * each behind its own non-zero guard, before the failure is rethrown to the caller's refusal cache.
 */
class PostChainProgramCreateCleanupTest {

    /** The whole of {@code Program.create}, from its signature. */
    private static String createBody() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/hal/ChainRecorder.java"));
        return methodSlice(source, "static Program create(VulkanContext context", 14000);
    }

    // The handles exist as locals, zeroed before anything is created, and each creation writes its
    // own local - so the failure path can tell what exists without reading the stage buffer back.
    @Test
    void handlesAreTrackedInLocalsFromCreation() throws IOException {
        String body = createBody();
        int layoutDeclared = body.indexOf("long layout = 0L;");
        int layoutAssigned = body.indexOf("layout = handle.get(0);");
        assertTrue(layoutDeclared >= 0, "the layout is a zeroed local before creation");
        assertTrue(layoutAssigned > layoutDeclared, "the created layout lands in the tracked local");
        int vertexCreated = body.indexOf("vertexModule = createShaderModule(");
        int vertexStaged = body.indexOf(".module(vertexModule)");
        assertTrue(vertexCreated >= 0 && vertexStaged > vertexCreated,
                "the vertex module is created into its local before the stage names it");
        int fragmentCreated = body.indexOf("fragmentModule = createShaderModule(");
        int fragmentStaged = body.indexOf(".module(fragmentModule)");
        assertTrue(fragmentCreated >= 0 && fragmentStaged > fragmentCreated,
                "the fragment module is created into its local before the stage names it");
    }

    // The success path still destroys both modules once the pipeline exists, and only then hands
    // the pipeline and the layout to the Program (whose teardown owns the layout from there).
    @Test
    void successDestroysBothModulesAndHandsTheLayoutOver() throws IOException {
        String body = createBody();
        int pipelineChecked = body.indexOf("vkCreateGraphicsPipelines(post ");
        int vertexDestroyed = body.indexOf("vkDestroyShaderModule(device, vertexModule, null);",
                pipelineChecked);
        int fragmentDestroyed = body.indexOf("vkDestroyShaderModule(device, fragmentModule, null);",
                pipelineChecked);
        int returned = body.indexOf("return new Program(pipeline, layout,");
        assertTrue(pipelineChecked >= 0, "the pipeline creation is checked");
        assertTrue(vertexDestroyed > pipelineChecked && fragmentDestroyed > vertexDestroyed,
                "both modules are destroyed once the pipeline exists");
        assertTrue(returned > fragmentDestroyed,
                "the Program takes the pipeline and the layout only after the modules are gone");
    }

    // The failure path: a catch over the creation sequence destroys the fragment module, the vertex
    // module and the layout - each only when its local says it was created - and rethrows, so the
    // caller's refusal caching sees the same failure it saw before the cleanup existed.
    @Test
    void failureDestroysWhatWasCreatedAndRethrows() throws IOException {
        String body = createBody();
        int caught = body.indexOf("catch (RuntimeException | Error failure)");
        assertTrue(caught >= 0, "the creation sequence has a failure path");
        String failurePath = body.substring(caught);
        int fragmentGuard = failurePath.indexOf("if (fragmentModule != 0L)");
        int fragmentDestroyed = failurePath.indexOf("vkDestroyShaderModule(device, fragmentModule, null);");
        int vertexGuard = failurePath.indexOf("if (vertexModule != 0L)");
        int vertexDestroyed = failurePath.indexOf("vkDestroyShaderModule(device, vertexModule, null);");
        int layoutGuard = failurePath.indexOf("if (layout != 0L)");
        int layoutDestroyed = failurePath.indexOf("vkDestroyPipelineLayout(device, layout, null);");
        int rethrown = failurePath.indexOf("throw failure;");
        assertTrue(fragmentGuard >= 0 && fragmentDestroyed > fragmentGuard,
                "a created fragment module is destroyed on the failure path");
        assertTrue(vertexGuard > fragmentDestroyed && vertexDestroyed > vertexGuard,
                "a created vertex module is destroyed on the failure path");
        assertTrue(layoutGuard > vertexDestroyed && layoutDestroyed > layoutGuard,
                "a created layout is destroyed on the failure path");
        assertTrue(rethrown > layoutDestroyed, "the failure is rethrown after the cleanup");
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
