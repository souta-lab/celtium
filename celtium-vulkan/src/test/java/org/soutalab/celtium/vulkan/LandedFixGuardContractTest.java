package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source contracts for campaign fixes whose mechanism lives inside a device-bound method and so
 * cannot be driven on a CPU-only instance - the shape {@code BoundTargetRoutedClearTest} and
 * {@code DeviceLostRobustnessTest} established for exactly this situation. Each pin names the
 * landed fix it guards. These are deliberately narrow: they fail when the guard itself is
 * removed or reverted, which is the regression this lane exists to catch (several of these fixes
 * are one-line guards a later refactor could silently undo - the region-eviction one already had
 * that shape once, in reverse).
 */
class LandedFixGuardContractTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";
    private static final String CONTEXT =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java";
    private static final String TERRAIN_RENDERER =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java";
    private static final String MOD_COMPILER =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/ModShaderCompiler.java";
    private static final String GL_STUB =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java";

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
    }

    private static int count(String text, String needle) {
        int found = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            found++;
            from += needle.length();
        }
        return found;
    }

    /**
     * 10bfe3ad4: region eviction must be guarded on the region count, not on the failed-upload
     * set. The set is drained after every upload, so the old guard returned early on every
     * ordinary frame and dropped regions kept their arenas until world end.
     */
    @Test
    void regionEvictionIsGuardedOnRegionCountNotOnTheFailedUploadSet() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains("this.terrain.regionCount() == 0"),
                "the eviction entry is guarded on the regions with resources");
        assertEquals(0, count(backend, "this.terrain.isEmpty()"),
                "the failed-upload set must not gate eviction again - it is empty on ordinary frames");
        assertTrue(read(TERRAIN_RENDERER).contains("boolean failedUploadsEmpty()"),
                "the failed-upload query keeps its own name, separate from the region count");
    }

    /**
     * a0476564b (bughunt lane 01, finding 4): a depth copy whose two names resolve to one shared
     * image is a feedback loop and is refused by name, before any extent or barrier work.
     */
    @Test
    void aDepthCopyResolvingToOneImageIsRefusedBeforeTheBarrierWork() throws IOException {
        String backend = read(BACKEND);
        int serve = backend.indexOf("public String celtium$serveDepthCopy(");
        assertTrue(serve >= 0, "celtium$serveDepthCopy is declared");
        String body = backend.substring(serve, serve + 16000);
        int refusal = body.indexOf("if (sourceImage == destImage)");
        assertTrue(refusal >= 0, "the same-image refusal is present");
        assertTrue(body.contains("resolve to the same shared depth image"),
                "the refusal names itself, in the method's existing refusal style");
        int extent = body.indexOf("sharedDepthExtent(sourceKey)");
        assertTrue(extent < 0 || refusal < extent,
                "the refusal precedes the extent lookup and the barrier built from it");
    }

    /**
     * 6e051a437: the flush/drain device-lost exits drop the device's retirements and pipeline
     * cache entry like the other exits do. {@code VulkanRetireQueueTest} pins dropFor itself;
     * this pins that the exits call it (five call sites on the fixed tree, three before the fix).
     */
    @Test
    void theDeviceLostExitsDropTheRetirements() throws IOException {
        String context = read(CONTEXT);
        assertTrue(count(context, "VulkanRetireQueue.dropFor(") >= 5,
                "every device-lost exit, flush and drain included, drops the device's retirements");
    }

    /**
     * d5bcb58e5 (bughunt lane 01, finding 1): the off-target fallback in recordImmediateOnlyPass
     * restores the whole pass state to the frame's values beside the framebuffer - the render
     * pass, the attached colour image and the pass extent - not the framebuffer alone.
     */
    @Test
    void theOffTargetFallbackRestoresTheWholePassState() throws IOException {
        String backend = read(BACKEND);
        int fallback = backend.indexOf("this.passAttachedColorImages = null;");
        assertTrue(fallback >= 0, "the fallback framebuffer rebuild is present");
        String block = backend.substring(fallback, fallback + 3000);
        assertTrue(block.contains("this.passAttachedColorImage = this.frameTarget.colorImage();"),
                "the attached colour image goes back to the frame's");
        assertTrue(block.contains("passRenderPass = renderPass;"),
                "the render pass goes back to the frame's - the pair's pass clears and discards depth");
        assertTrue(block.contains("passWidth = this.targetWidth;")
                        && block.contains("passHeight = this.targetHeight;"),
                "the pass extent goes back to the frame's, so the fallback's draws are not clipped"
                        + " to the pair's rectangle");
    }

    /**
     * 7a25b7827: a merge run is drawn while its own index source is still bound, and a new run
     * binds its command's source first when it differs - at both places a run opens (the run
     * start and the run end), which is the two {@code arena != arenaBound} blocks.
     */
    @Test
    void mergeRunsBindTheirIndexSourceBeforeDrawing() throws IOException {
        String backend = read(BACKEND);
        assertEquals(2, count(backend, "if (arena != arenaBound)"),
                "both run boundaries bind the run's own index source before any draw of it");
    }

    /**
     * ad8513594 (BH10-07): delete()'s early returns detach the backend registration too - a
     * stalled teardown that skipped it left the mixins pointing at a dead backend. Three call
     * sites of the shared detach: the two early returns and the full path.
     */
    @Test
    void deleteDetachesTheRegistrationOnItsEarlyReturnsToo() throws IOException {
        String backend = read(BACKEND);
        assertTrue(count(backend, "celtium$detachRegistration();") >= 3,
                "both early returns and the full delete path detach the registration");
    }

    /**
     * fc29006eb (audit-gl-deep G1/G3/G5): a mod shader compile is bounded - a 30 s deadline
     * ({@code -Dceltium.shaderc.timeoutMs} overrides) and at most 4 concurrent compiles, refused
     * at once over the cap - and the GL dispatch answers zero behind a Throwable guard instead of
     * letting a handler's exception escape through the upcall as a fatal JVM error.
     */
    @Test
    void shaderCompilesAreBoundedAndTheDispatchIsGuarded() throws IOException {
        String compiler = read(MOD_COMPILER);
        assertTrue(compiler.contains("celtium.shaderc.timeoutMs"),
                "the compile deadline is the named property");
        assertTrue(compiler.contains("MAX_CONCURRENT_COMPILES"),
                "concurrent compiles are capped");
        assertTrue(compiler.contains("worker.join(COMPILE_TIMEOUT_MS)"),
                "the compile worker is joined with the deadline, not unboundedly");

        String stub = read(GL_STUB);
        assertTrue(count(stub, "catch (Throwable") >= 5,
                "the dispatch entries (and their compilation rollback) catch Throwable");
        assertTrue(stub.contains("dispatchFailureSummary()"),
                "dispatch failures are counted and summarised, not swallowed silently");
    }

    /**
     * 679098852 (item 430): the terrain shaders measure dynamic-light distance directly in the
     * shared camera-relative frame. Subtracting the header camera a second time pushed every
     * measured distance out by the camera's own offset and the raise came to zero everywhere -
     * the presented image never changed, in all three terrain variants. The gain itself was
     * measured on the leg (leg-430-agera); this pins the shader shape it depends on.
     */
    @Test
    void theTerrainShadersDoNotSubtractTheCameraTwice() throws IOException {
        for (String shader : new String[]{"terrain_solid", "terrain_solid_extended", "terrain_solid_vanilla"}) {
            String source = read("celtium-vulkan/src/main/shaders/" + shader + ".vert");
            assertTrue(source.contains("_dynamic_block_light"),
                    shader + " carries the dynamic raise");
            assertTrue(source.contains("vec3 relativePosition = position;"),
                    shader + " measures in the shared camera-relative frame, as-is");
            assertEquals(0, count(source, "position - dynLights.u_DynamicLightInfo.yzw"),
                    shader + " must not subtract the header camera a second time");
        }
    }

    /** Test working directories differ between the module gate and a repo-root run; both shapes are tried. */
    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from " + Paths.get("").toAbsolutePath());
    }
}
