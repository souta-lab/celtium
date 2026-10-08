package org.soutalab.celtium.probe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import org.lwjgl.Version;
import static org.lwjgl.opengl.GL11C.glFlush;
import static org.lwjgl.opengl.GL32C.*;
import static org.lwjgl.opengl.GL45C.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Standalone headless proof, never loaded or embedded by the Minecraft mod. */
public final class InteropMain {
    public static void main(String[] args) throws Exception {
        int deviceIndex = -1;
        boolean validation = false;
        Path output = null;
        Path screenshots = null;
        int width = 64, height = 64, frames = 4, handoffs = 4, cycles = 1;
        int measuredFrames = 0;
        String[] sizes = null;
        for (int i = 0; i < args.length; i++) {
            if ("--egl-device".equals(args[i])) deviceIndex = Integer.parseInt(args[++i]);
            else if ("--validation".equals(args[i])) validation = true;
            else if ("--output".equals(args[i])) output = Paths.get(args[++i]);
            else if ("--screenshots".equals(args[i])) screenshots = Paths.get(args[++i]);
            else if ("--size".equals(args[i])) {
                String[] dimensions = args[++i].split("x");
                width = Integer.parseInt(dimensions[0]); height = Integer.parseInt(dimensions[1]);
            }
            else if ("--sizes".equals(args[i])) sizes = args[++i].split(",");
            else if ("--frames".equals(args[i])) frames = Integer.parseInt(args[++i]);
            else if ("--handoffs".equals(args[i])) handoffs = Integer.parseInt(args[++i]);
            else if ("--cycles".equals(args[i])) cycles = Integer.parseInt(args[++i]);
            else if ("--measure-frames".equals(args[i])) measuredFrames = Integer.parseInt(args[++i]);
            else throw new IllegalArgumentException("Unknown argument: " + args[i]);
        }
        if (deviceIndex < 0) throw new IllegalArgumentException("Select an explicit --egl-device; no default GPU fallback");
        if (width < 32 || height < 8 || width > 4096 || height > 4096 || frames < 1 || handoffs < 1 || cycles < 1
                || measuredFrames < 0 || measuredFrames > 10000)
            throw new IllegalArgumentException("Invalid dimensions or iteration count");
        JsonObject report = new JsonObject();
        report.addProperty("scope", "standalone RGBA8/D24-to-D32 roundtrip and occlusion experiment; not native terrain");
        report.addProperty("lwjglVersion", Version.getVersion());
        report.addProperty("validationRequested", validation);
        report.addProperty("depthRoundtripVerified", false);
        report.addProperty("framesPerCycle", frames);
        report.addProperty("handoffsPerFrame", handoffs);
        JsonArray cases = new JsonArray();
        report.add("cases", cases);
        VkInteropDevice vk = null;
        int exitCode = 1;
        try (HeadlessGlContext context = HeadlessGlContext.create(deviceIndex)) {
            ProbeReport.OpenGlDevice gl = CurrentGlProbe.inspect();
            report.add("openGl", new GsonBuilder().create().toJsonTree(gl));
            try (VkInteropDevice opened = VkInteropDevice.open(gl, validation)) {
                vk = opened;
                report.add("vulkan", vk.describe());
                report.addProperty("fdCountBeforeCycles", descriptorCount());
                for (int cycle = 0; cycle < cycles; cycle++) {
                    if (sizes != null) {
                        String[] dimensions = sizes[cycle % sizes.length].split("x");
                        width = Integer.parseInt(dimensions[0]); height = Integer.parseInt(dimensions[1]);
                        if (width < 32 || height < 8 || width > 4096 || height > 4096)
                            throw new IllegalArgumentException("Invalid resize dimensions");
                    }
                    JsonObject test = new JsonObject();
                    test.addProperty("cycle", cycle);
                    cases.add(test);
                    try (SharedImage color = SharedImage.create(vk, width, height, false);
                         SharedImage depth = SharedImage.create(vk, width, height, true);
                         GlDepthBridge glBridge = new GlDepthBridge(color, depth);
                         VkTriangleDraw triangle = new VkTriangleDraw(vk, color, depth)) {
                        test.add("color", color.describe());
                        test.add("depth", depth.describe());
                        test.addProperty("originalFbo", glBridge.originalFbo);
                        test.addProperty("originalColor", glBridge.originalColor);
                        test.addProperty("originalDepthRenderbuffer", glBridge.originalDepth);
                        try {
                            triangle.initializeOwnership();
                            for (int frame = 0; frame < frames; frame++) {
                                glBridge.prepareScene();
                                for (int handoff = 0; handoff < handoffs; handoff++) triangle.roundtrip(glBridge);
                                // Far GL geometry must be hidden by the returned Vulkan depth.
                                glBridge.solid(0.75f, 0, 0, 1, 0, 0, width, height);
                                test.add("behindGeometrySamples", glBridge.verify(false));
                                // Near GL geometry must remain visible over both GL and Vulkan surfaces.
                                glBridge.solid(0.125f, 0, 1, 1, 0, 0, width, height / 2);
                                test.add("foregroundGeometrySamples", glBridge.verify(true));
                            }
                            glBridge.prepareDepthPattern();
                            triangle.roundtrip(glBridge, 1f, true);
                            test.add("precisionSamples", glBridge.verifyDepthPattern());
                            test.addProperty("depthToleranceD24Steps", 2);
                            if (measuredFrames > 0) test.add("cost", measure(vk, glBridge, triangle, measuredFrames, handoffs));
                            if (screenshots != null) {
                                glBridge.prepareScene();
                                triangle.roundtrip(glBridge);
                                glBridge.solid(0.125f, 0, 1, 1, 0, 0, width, height / 2);
                                glBridge.verify(true);
                                Path png = screenshots.resolve("interop-" + width + "x" + height + "-" + cycle + ".png");
                                glBridge.screenshot(png);
                                test.addProperty("screenshot", png.toString());
                            }
                            test.addProperty("originalAttachmentsPreserved", true);
                            test.addProperty("oracleClearedOriginalBeforeReturn", true);
                            for (int pendingPass = 0; pendingPass < 8; pendingPass++) triangle.roundtrip(glBridge, 0.5f, false);
                        } finally {
                            test.add("retirement", retire(vk));
                        }
                    }
                    test.addProperty("resourcesRetired", true);
                    test.addProperty("fdCountAfterRetirement", descriptorCount());
                }
            }
            SharedImage.glCheck("interop cleanup");
            if (vk.validationErrors != 0) throw new IllegalStateException("Vulkan validation errors: " + vk.validationErrors);
            report.addProperty("status", "ROUNDTRIP_PASSED");
            report.addProperty("depthRoundtripVerified", true);
            exitCode = 0;
        } catch (RuntimeException | LinkageError | java.io.IOException error) {
            error.printStackTrace();
            report.addProperty("status", "FAILED");
            report.addProperty("error", error.toString());
        }
        if (vk != null) {
            report.addProperty("validationErrors", vk.validationErrors);
            report.addProperty("validationWarnings", vk.validationWarnings);
            report.add("validationMessages", new GsonBuilder().create().toJsonTree(vk.validationMessages));
        }
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n";
        if (output != null) {
            Path parent = output.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Files.write(output, json.getBytes(StandardCharsets.UTF_8));
        }
        System.out.print(json);
        if (exitCode != 0) System.exit(exitCode);
    }

    static JsonObject retire(VkInteropDevice vk) {
        long completion = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        glFlush();
        try {
            JsonObject result = new JsonObject();
            int initially = glClientWaitSync(completion, 0, 0);
            if (initially == GL_WAIT_FAILED) throw new IllegalStateException("GL retirement fence query failed");
            result.addProperty("glFenceInitiallySignaled", initially == GL_ALREADY_SIGNALED || initially == GL_CONDITION_SATISFIED);
            result.addProperty("pendingVulkanSlots", vk.pendingCommandSlots());
            vk.awaitSubmitted();
            int waited = glClientWaitSync(completion, 0, 5_000_000_000L);
            if (waited != GL_ALREADY_SIGNALED && waited != GL_CONDITION_SATISFIED)
                throw new IllegalStateException("GL resources could not be retired: 0x" + Integer.toHexString(waited));
            result.addProperty("bothApisCompletedBeforeDestruction", true);
            return result;
        } finally {
            glDeleteSync(completion);
        }
    }

    private static int descriptorCount() throws java.io.IOException {
        int count = 0;
        try (java.nio.file.DirectoryStream<Path> descriptors = Files.newDirectoryStream(Paths.get("/proc/self/fd"))) {
            for (Path ignored : descriptors) count++;
        }
        return count;
    }

    private static JsonObject measure(VkInteropDevice vk, GlDepthBridge gl, VkTriangleDraw triangle, int frames, int handoffs) {
        gl.prepareScene();
        for (int frame = 0; frame < 32; frame++)
            for (int handoff = 0; handoff < handoffs; handoff++) triangle.roundtrip(gl, 0.5f, false);
        retire(vk);
        int[] queries = new int[frames * 2];
        for (int i = 0; i < queries.length; i++) queries[i] = glCreateQueries(GL_TIMESTAMP);
        try {
            long started = System.nanoTime();
            for (int frame = 0; frame < frames; frame++) {
                glQueryCounter(queries[frame * 2], GL_TIMESTAMP);
                for (int handoff = 0; handoff < handoffs; handoff++) triangle.roundtrip(gl, 0.5f, false);
                glQueryCounter(queries[frame * 2 + 1], GL_TIMESTAMP);
            }
            retire(vk);
            double elapsed = (System.nanoTime() - started) / 1e6;
            List<Double> gpuDurations = new ArrayList<Double>();
            for (int frame = 0; frame < frames; frame++) {
                long begin = glGetQueryObjectui64(queries[frame * 2], GL_QUERY_RESULT);
                long end = glGetQueryObjectui64(queries[frame * 2 + 1], GL_QUERY_RESULT);
                if (end < begin) throw new IllegalStateException("Non-monotonic GL timestamp result");
                gpuDurations.add((end - begin) / 1e6);
            }
            Collections.sort(gpuDurations);
            JsonObject result = new JsonObject();
            result.addProperty("scope", "GL timestamp span across GL/Vulkan copy+draw+binary-semaphore handoffs, including harness glGetError and command-slot fence waits; no per-frame readback, not Minecraft FPS");
            result.addProperty("sampleCount", frames);
            result.addProperty("warmupFrames", 32);
            result.addProperty("handoffsPerSample", handoffs);
            result.addProperty("gpuSpanP50Ms", percentile(gpuDurations, 0.5));
            result.addProperty("gpuSpanP95Ms", percentile(gpuDurations, 0.95));
            result.addProperty("gpuSpanP99Ms", percentile(gpuDurations, 0.99));
            result.addProperty("cpuBatchIncludingCompletionMs", elapsed);
            result.addProperty("completedBatchMsPerSample", elapsed / frames);
            gl.verify(false);
            SharedImage.glCheck("timestamp cost experiment");
            return result;
        } finally {
            for (int query : queries) glDeleteQueries(query);
        }
    }

    private static double percentile(List<Double> sorted, double percentile) {
        return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * percentile) - 1));
    }
}
