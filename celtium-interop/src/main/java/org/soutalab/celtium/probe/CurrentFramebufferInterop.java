package org.soutalab.celtium.probe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.lwjgl.opengl.GL45C.*;

/** Explicit validation entry point. Overwrites only the supplied test FBO's contents. */
public final class CurrentFramebufferInterop {
    public static String verify(int fbo, int colorTexture, int depthRenderbuffer, int width, int height,
                                String outputDirectory, boolean validation) throws java.io.IOException {
        Path output = Paths.get(outputDirectory);
        JsonObject report = new JsonObject();
        report.addProperty("scope", "existing Minecraft FBO, synthetic bidirectional occlusion and depth precision; not Vulkan terrain");
        report.addProperty("fbo", fbo);
        report.addProperty("colorTexture", colorTexture);
        report.addProperty("depthRenderbuffer", depthRenderbuffer);
        report.addProperty("width", width); report.addProperty("height", height);
        // A misplaced test jar can resolve a second, uninitialized GL binding
        // classloader. Check Java capabilities before any native GL call.
        if (CurrentFramebufferInterop.class.getClassLoader() != org.lwjgl.opengl.GL.class.getClassLoader())
            throw new IllegalStateException("Cleanroom interop fixture must share the initialized GL classloader; embed it in the validation mod");
        org.lwjgl.opengl.GLCapabilities capabilities = org.lwjgl.opengl.GL.getCapabilities();
        if (!capabilities.OpenGL45 || capabilities.glGetIntegerv == 0)
            throw new IllegalStateException("Current classloader has no initialized GL 4.5 context");
        report.addProperty("interopClassLoader", String.valueOf(CurrentFramebufferInterop.class.getClassLoader()));
        report.addProperty("glClassLoader", String.valueOf(org.lwjgl.opengl.GL.class.getClassLoader()));
        VkInteropDevice observed = null;
        try (GlStateSnapshot state = new GlStateSnapshot()) {
            ProbeReport.OpenGlDevice gl = CurrentGlProbe.inspect();
            report.add("openGl", new GsonBuilder().create().toJsonTree(gl));
            try (VkInteropDevice vk = VkInteropDevice.open(gl, validation)) {
                observed = vk;
                report.add("vulkan", vk.describe());
                try (SharedImage color = SharedImage.create(vk, width, height, false);
                     SharedImage depth = SharedImage.create(vk, width, height, true);
                     GlDepthBridge bridge = new GlDepthBridge(color, depth, fbo, colorTexture, depthRenderbuffer);
                     VkTriangleDraw triangle = new VkTriangleDraw(vk, color, depth)) {
                    try {
                        triangle.initializeOwnership();
                        bridge.prepareScene();
                        for (int pass = 0; pass < 4; pass++) triangle.roundtrip(bridge);
                        bridge.solid(0.75f, 0, 0, 1, 0, 0, width, height);
                        report.add("behindGeometrySamples", bridge.verify(false));
                        bridge.solid(0.125f, 0, 1, 1, 0, 0, width, height / 2);
                        report.add("foregroundGeometrySamples", bridge.verify(true));
                        bridge.screenshot(output.resolve("celtium-interop.png"));
                        bridge.prepareDepthPattern();
                        triangle.roundtrip(bridge, 1, true);
                        report.add("precisionSamples", bridge.verifyDepthPattern());
                        for (int pass = 0; pass < 8; pass++) triangle.roundtrip(bridge, 1, false);
                    } finally {
                        report.add("retirement", InteropMain.retire(vk));
                    }
                }
            }
            if (!glIsFramebuffer(fbo) || !glIsTexture(colorTexture) || !glIsRenderbuffer(depthRenderbuffer))
                throw new IllegalStateException("The original Minecraft FBO resources did not survive interop cleanup");
            if (observed.validationErrors != 0) throw new IllegalStateException("Vulkan validation reported errors");
            report.addProperty("originalResourcesPreserved", true);
            report.addProperty("depthRoundtripVerified", true);
            report.addProperty("status", "ROUNDTRIP_PASSED");
        } catch (RuntimeException | LinkageError error) {
            report.addProperty("status", "FAILED");
            report.addProperty("depthRoundtripVerified", false);
            report.addProperty("error", error.toString());
        }
        if (observed != null) {
            report.addProperty("validationErrors", observed.validationErrors);
            report.addProperty("validationWarnings", observed.validationWarnings);
            report.add("validationMessages", new GsonBuilder().create().toJsonTree(observed.validationMessages));
        }
        Files.createDirectories(output);
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n";
        Files.write(output.resolve("celtium-interop.json"), json.getBytes(StandardCharsets.UTF_8));
        return json;
    }
}
