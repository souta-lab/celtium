package org.soutalab.celtium.vulkan;

import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.vulkan.VK10.*;

/**
 * The shaderpack frontend's probes: load the configured pack once, compile a stage in process, and build and record one
 * pack program's draw. Moved out of {@link VulkanTerrainGpuBackend} unchanged; the state they read stays there.
 */
final class ShaderPackProbe {
    private final VulkanTerrainGpuBackend b;

    ShaderPackProbe(VulkanTerrainGpuBackend backend) {
        this.b = backend;
    }

    /**
     * Compiles one lowered Vulkan GLSL stage in process and reports what came back.
     * <p>
     * The S2 question is not whether a compiler exists on the development machine - the frontend's probe shells out to
     * a glslangValidator executable there - but whether one can run inside the shipped game, where no executable can be
     * installed. This is that compiler: LWJGL's shaderc binding, the same glslang, as a library. The source is the
     * smallest pair of stages a pipeline can be built from, so a failure here is the compiler and not the shader.
     */
    void celtium$probeShaderCompile() {
        if (b.shaderCompileProbed || !b.SHADER_COMPILE_PROBE) {
            return;
        }
        b.shaderCompileProbed = true;
        ShadercGlslCompiler compiler = new ShadercGlslCompiler();
        StringBuilder report = new StringBuilder("[Celtium] CELTIUM_SHADER_COMPILE compiler=").append(compiler.name())
                .append(" available=").append(compiler.available())
                .append(" reason=").append(compiler.unavailableReason());
        try {
            String vertex = "#version 450\n"
                    + "layout(location = 0) in vec3 a_Position;\n"
                    + "void main() { gl_Position = vec4(a_Position, 1.0); }\n";
            String fragment = "#version 450\n"
                    + "layout(location = 0) out vec4 o_Colour;\n"
                    + "void main() { o_Colour = vec4(0.25, 0.5, 0.75, 1.0); }\n";
            org.soutalab.celtium.shaderpack.SpirvModule vertexModule =
                    compiler.compile(org.soutalab.celtium.shaderpack.Stage.VERTEX, vertex);
            org.soutalab.celtium.shaderpack.SpirvModule fragmentModule =
                    compiler.compile(org.soutalab.celtium.shaderpack.Stage.FRAGMENT, fragment);
            report.append(" vertexWords=").append(ShadercGlslCompiler.wordCount(vertexModule))
                    .append(" vertexVersion=0x")
                    .append(Integer.toHexString(ShadercGlslCompiler.headerVersion(vertexModule)))
                    .append(" fragmentWords=").append(ShadercGlslCompiler.wordCount(fragmentModule))
                    .append(" sha=").append(vertexModule.sha256(), 0, 12);
        } catch (Throwable failure) {
            report.append(" result=FAILED error=").append(failure);
        }
        System.out.println(report);
        celtium$probePackProgram(compiler);
    }

    /**
     * The S2 chain on a real program: expand the pack's own file, lower it, compile it.
     * <p>
     * This is the step that says whether the pieces fit a pack that was written for OptiFine rather than for this
     * renderer. Every stage reports what it produced, and a failure at any of them is evidence about which stage needs
     * work - the expansion (includes and the pack's conditionals), the lowering, or the compile.
     */
    void celtium$probePackProgram(ShadercGlslCompiler compiler) {
        if (b.SHADER_PACK_PATH.isEmpty() || !compiler.available()) {
            // Say why: the probe returning silently made it look as though the pack's programs were being compiled when they were not, and the
            // difference between "no path" and "no compiler" is the difference between a missing argument and a missing native library.
            System.out.println("[Celtium] CELTIUM_PACK_PROGRAM skipped path=" + !b.SHADER_PACK_PATH.isEmpty()
                    + " compiler=" + compiler.available());
            return;
        }
        java.nio.file.Path pack = java.nio.file.Paths.get(b.SHADER_PACK_PATH);
        if (b.shaderPackManifest == null) {
            return;
        }
        // The program's paths come from the manifest, not from a guess: packs put their programs under a dimension
        // folder (BSL's gbuffers_terrain lives at /shaders/world0/gbuffers_terrain.vsh), and the manifest is what
        // resolved that. A hard-coded path reported "no such file in pack" for a pack that plainly has the program.
        // Several programs, smallest first: whether the stack overflow is a property of these packs' terrain programs
        // or of the chain itself is decided by where it starts, and one program cannot answer it.
        java.util.List<org.soutalab.celtium.shaderpack.PackPath> wanted = new java.util.ArrayList<>();
        // Which program the probe draws with. The smallest one (gbuffers_basic) proves the chain but draws no texture:
        // its fragment stage samples nothing, so the picture is flat colour. gbuffers_terrain is the program that
        // samples the block atlas, and selecting it is what turns the probe's output into something recognisably world.
        String programChoice = System.getProperty("celtium.shaderpack.program", "").trim();
        java.util.List<String> preference = programChoice.isEmpty()
                ? java.util.List.of("gbuffers_basic", "gbuffers_skybasic", "gbuffers_textured", "gbuffers_terrain")
                : java.util.List.of(programChoice);
        for (org.soutalab.celtium.shaderpack.DimensionPlan dimension : b.shaderPackManifest.dimensions()) {
            for (org.soutalab.celtium.shaderpack.ProgramEntry program : dimension.programs()) {
                if (program.isComputeOnly() || preference.stream().noneMatch(w -> program.name().contains(w))) {
                    continue;
                }
                wanted.addAll(program.stages().values());
                if (wanted.size() >= 6) {
                    break;
                }
            }
            if (wanted.size() >= 6) {
                break;
            }
        }
        if (b.SHADER_PACK_DRAW_CONSTANT && b.shaderPackDrawProbe == null) {
            try {
                b.shaderPackDrawProbe = ShaderPackDrawProbe.createConstant(b.context, compiler,
                        org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM, org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT);
                System.out.println("[Celtium] CELTIUM_PACK_DRAW constant=prepared");
                System.out.println("[Celtium] CELTIUM_PACK_DRAW constantLeg=");
            } catch (Throwable failure) {
                System.out.println("[Celtium] CELTIUM_PACK_DRAW constant=FAILED error=" + failure);
            }
        }
        if (b.SHADER_PACK_DRAW_BUILTIN && b.shaderPackDrawProbe == null) {
            try {
                b.shaderPackDrawProbe = ShaderPackDrawProbe.createBuiltin(b.context, compiler,
                        org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM, org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT);
                System.out.println("[Celtium] CELTIUM_PACK_DRAW builtin=prepared");
            } catch (Throwable failure) {
                System.out.println("[Celtium] CELTIUM_PACK_DRAW builtin=FAILED error=" + failure);
            }
        }
        if (!b.SHADER_PACK_DRAW_CONSTANT && !b.SHADER_PACK_DRAW_BUILTIN) {
            // A constant or built-in probe was created above for a different question; the pack probe would replace it
            // and the leg would measure the pack's triangle while believing it measured the constant one.
            celtium$probePackPipeline(compiler, pack);
        }
        for (org.soutalab.celtium.shaderpack.PackPath path : wanted) {
            String file = path.toString();
            StringBuilder line = new StringBuilder("[Celtium] CELTIUM_PACK_PROGRAM file=").append(file);
            try {
                org.soutalab.celtium.shaderpack.Stage stage =
                        org.soutalab.celtium.shaderpack.LegacyGlslLowering.stageOf(file);
                String expanded = org.soutalab.celtium.shaderpack.ShaderPackLoader.expand(pack, path,
                        java.util.Map.of("MC_VERSION", "11202"));
                line.append(" expandedBytes=").append(expanded.length());
                org.soutalab.celtium.shaderpack.LegacyGlslLowering.Result lowered =
                        org.soutalab.celtium.shaderpack.LegacyGlslLowering.lower(stage, expanded, java.util.Map.of());
                line.append(" loweredBytes=").append(lowered.source().length())
                        .append(" attributes=").append(lowered.attributes())
                        .append(" varyings=").append(lowered.varyings().size())
                        .append(" samplers=").append(lowered.samplers())
                        .append(" unsupported=").append(lowered.unsupported().size());
                org.soutalab.celtium.shaderpack.SpirvModule module = compiler.compile(stage, lowered.source());
                line.append(" spirvWords=").append(ShadercGlslCompiler.wordCount(module))
                        .append(" result=OK");
                // The Vulkan objects the draw will be assembled from: a shader module, and set 0 with the transform
                // block, the pack's own uniforms and one binding per sampler. Created and released here because this is
                // a probe; the pipeline itself needs render targets whose formats match the program's outputs, which is
                // the next unit.
                ShaderPackPipelineFactory factory = new ShaderPackPipelineFactory(b.context);
                try (ShaderPackPipelineFactory.ModuleHandle handle = factory.createModule(module);
                     ShaderPackPipelineFactory.LayoutHandle layout = factory.createLayoutRebased(lowered, 2)) {
                    line.append(" module=").append(handle.module() != 0L ? "ok" : "null")
                            .append(" descriptorLayout=").append(layout.descriptorSetLayout() != 0L ? "ok" : "null")
                            .append(" pipelineLayout=").append(layout.pipelineLayout() != 0L ? "ok" : "null")
                            .append(" uniformBlock=").append(layout.uniformBlockBinding())
                            .append(" samplers=").append(layout.samplerBindings().size());
                }
            } catch (Throwable failure) {
                String text = String.valueOf(failure);
                line.append(" result=FAILED error=").append(text.length() > 300 ? text.substring(0, 300) : text);
            }
            System.out.println(line);
        }
    }

    /**
     * The first pack pipeline: one program's two stages, lowered, compiled, and turned into a graphics pipeline that
     * declares its render targets through dynamic rendering rather than a render pass.
     * <p>
     * The formats here are the delivered image's own (R8G8B8A8 colour, D32 depth). They are hard-coded in the probe
     * because it creates the pipeline without rendering into anything; the draw will take them from the target it draws
     * to. What the probe is for is the step the rest of the work depends on: that a pack's shaders come out of the
     * lowering and the compiler as something Vulkan will accept as a pipeline.
     */
    void celtium$probePackPipeline(ShadercGlslCompiler compiler, java.nio.file.Path pack) {
        if (b.shaderPackManifest == null) {
            return;
        }
        for (org.soutalab.celtium.shaderpack.DimensionPlan dimension : b.shaderPackManifest.dimensions()) {
            for (org.soutalab.celtium.shaderpack.ProgramEntry program : dimension.programs()) {
                if (program.isComputeOnly() || program.stages().size() < 2) {
                    continue;
                }
                // gbuffers_basic first: its vertex stage is the one whose transform the probe can satisfy - an
                // identity model-view-projection and no shadow-pass uniforms - so a triangle drawn with it lands in
                // the viewport. The first program the manifest lists is the shadow program, whose vertices depend on
                // the shadow matrices; with a zeroed uniform block it projects to zero and is clipped away, which is
                // exactly how the first attempts recorded a draw and showed nothing.
                String programFilter = System.getProperty("celtium.shaderpack.program", "").trim();
                if (!programFilter.isEmpty()) {
                    // A named program is honoured here as well as in the preference list: this loop is what actually
                    // creates the probe, and its basic/textured filter is what kept gbuffers_terrain out.
                    if (!program.name().contains(programFilter)) {
                        continue;
                    }
                } else if (!b.shaderPackDrawProbeProgramChosen
                        && !program.name().contains("basic") && !program.name().contains("textured")) {
                    continue;
                }
                b.shaderPackDrawProbeProgramChosen = true;
                org.soutalab.celtium.shaderpack.PackPath vertexPath =
                        program.stages().get(org.soutalab.celtium.shaderpack.Stage.VERTEX);
                org.soutalab.celtium.shaderpack.PackPath fragmentPath =
                        program.stages().get(org.soutalab.celtium.shaderpack.Stage.FRAGMENT);
                if (vertexPath == null || fragmentPath == null) {
                    continue;
                }
                StringBuilder line = new StringBuilder("[Celtium] CELTIUM_PACK_PIPELINE program=")
                        .append(program.name());
                ShaderPackPipelineFactory factory = new ShaderPackPipelineFactory(b.context);
                try {
                    String vertexSource = org.soutalab.celtium.shaderpack.ShaderPackLoader.expand(pack, vertexPath,
                            java.util.Map.of("MC_VERSION", "11202"));
                    String fragmentSource = org.soutalab.celtium.shaderpack.ShaderPackLoader.expand(pack, fragmentPath,
                            java.util.Map.of("MC_VERSION", "11202"));
                    // One contract for both stages: the union of what each of them declared, so the two shaders name
                    // the same block types at the same bindings.
                    org.soutalab.celtium.shaderpack.LegacyGlslLowering.Needs vertexNeeds =
                            org.soutalab.celtium.shaderpack.LegacyGlslLowering.scan(vertexSource);
                    org.soutalab.celtium.shaderpack.LegacyGlslLowering.Needs fragmentNeeds =
                            org.soutalab.celtium.shaderpack.LegacyGlslLowering.scan(fragmentSource);
                    java.util.Map<String, String> contractUniforms = new java.util.LinkedHashMap<>(vertexNeeds.uniforms());
                    contractUniforms.putAll(fragmentNeeds.uniforms());
                    boolean contractTransforms = vertexNeeds.transforms() || fragmentNeeds.transforms();
                    // Blocks take 0 and 1; samplers start at 2. Letting the lowering number them from 0 put the first
                    // sampler on the transform block's binding - "pBindings[2].binding and pBindings[0].binding are
                    // both 0" - which then made the layout's type disagree with the vertex shader's use of binding 0.
                    java.util.Map<String, Integer> samplerBindings = new java.util.LinkedHashMap<>();
                    java.util.List<String> allSamplers = new java.util.ArrayList<>(vertexNeeds.samplers());
                    for (String sampler : fragmentNeeds.samplers()) {
                        if (!allSamplers.contains(sampler)) {
                            allSamplers.add(sampler);
                        }
                    }
                    for (int i = 0; i < allSamplers.size(); i++) {
                        samplerBindings.put(allSamplers.get(i), Integer.valueOf(2 + i));
                    }
                    // The varying interface is a contract too: a vertex output the fragment stage does not declare is
                    // WARNING-Shader-OutputNotConsumed, and the acceptance gate counts messages, not just errors.
                    java.util.List<String> contractVaryings = new java.util.ArrayList<>(vertexNeeds.varyings());
                    for (String varying : fragmentNeeds.varyings()) {
                        if (!contractVaryings.contains(varying)) {
                            contractVaryings.add(varying);
                        }
                    }
                    org.soutalab.celtium.shaderpack.LegacyGlslLowering.Result vertex =
                            org.soutalab.celtium.shaderpack.LegacyGlslLowering.lower(
                                    org.soutalab.celtium.shaderpack.Stage.VERTEX, vertexSource, java.util.Map.of(),
                                    samplerBindings, contractUniforms, contractTransforms, contractVaryings);
                    org.soutalab.celtium.shaderpack.LegacyGlslLowering.Result fragment =
                            org.soutalab.celtium.shaderpack.LegacyGlslLowering.lower(
                                    org.soutalab.celtium.shaderpack.Stage.FRAGMENT, fragmentSource, java.util.Map.of(),
                                    samplerBindings, contractUniforms, contractTransforms, contractVaryings);
                    org.soutalab.celtium.shaderpack.SpirvModule vertexModule =
                            compiler.compile(org.soutalab.celtium.shaderpack.Stage.VERTEX, vertex.source());
                    org.soutalab.celtium.shaderpack.SpirvModule fragmentModule =
                            compiler.compile(org.soutalab.celtium.shaderpack.Stage.FRAGMENT, fragment.source());
                    if (b.SHADER_PACK_DRAW_PROBE) {
                        // The draw probe owns the pipeline, the descriptor set and the vertex buffers, and records a
                        // triangle with them once. It is created from the same interfaces and modules the pipeline
                        // check above uses, so what it draws is the same program.
                        org.soutalab.celtium.vulkan.VulkanGlTexture white = b.textureRegistry.get(b.whiteTextureId());
                        // The probe compiles its own vertex module: the pack's TAA jitter rewrites gl_Position.xy from
                        // frame uniforms a probe only approximates, and a non-finite position there removes the triangle
                        // before rasterisation while the pipeline check above still passes. Everything except that one
                        // statement is the pack's own stage.
                        org.soutalab.celtium.shaderpack.SpirvModule probeVertexModule = compiler.compile(
                                org.soutalab.celtium.shaderpack.Stage.VERTEX,
                                ShaderPackDrawProbe.sanitiseProbeVertex(vertex.source()));
                        b.shaderPackDrawProbe = ShaderPackDrawProbe.create(b.context, vertex, fragment,
                                probeVertexModule, fragmentModule, org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM,
                                org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT, white);
                        line.append(" drawProbe=prepared attributes=").append(vertex.attributes().size())
                                .append(" result=OK");
                        // The execution evidence that does not depend on the delivery path: the pack's shaders render
                        // into an image this probe owns, and the pixels are read back.
                        line.append(" offscreen=[").append(b.shaderPackDrawProbe.sampleOffscreen(64)).append(']');

                        b.shaderPackDrawProbe.dumpVertexContract();
                        if (Boolean.getBoolean("celtium.shaderpack.drawProbeDebug")) {
                            // What the two stages actually say about gl_Position and the fragment colour: the pipeline
                            // and the vertex data are now known to be sound (the constant triangle draws), so the next
                            // fact to read is the shader text itself.
                            System.out.println("[Celtium] CELTIUM_PACK_SHADER_DUMP vertex=\n"
                                    + vertex.source() + "\n--- fragment ---\n" + fragment.source());
                        }
                    } else {
                        try (ShaderPackPipelineFactory.ModuleHandle vertexHandle = factory.createModule(vertexModule);
                             ShaderPackPipelineFactory.ModuleHandle fragmentHandle = factory.createModule(fragmentModule);
                             ShaderPackPipelineFactory.LayoutHandle layout = factory.createLayout(vertex, fragment)) {
                            long pipeline = factory.createPipeline(layout.pipelineLayout(), vertexHandle,
                                    fragmentHandle, vertex, org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM,
                                    org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT);
                            line.append(" pipeline=").append(pipeline != 0L ? "ok" : "null")
                                    .append(" attributes=").append(vertex.attributes().size())
                                    .append(" result=OK");
                            org.lwjgl.vulkan.VK10.vkDestroyPipeline(b.context.device(), pipeline, null);
                        }
                    }
                } catch (Throwable failure) {
                    String text = String.valueOf(failure);
                    line.append(" result=FAILED error=")
                            .append(text.substring(0, Math.min(240, text.length())));
                }
                System.out.println(line);
                return;
            }
        }
    }

    void celtium$recordShaderPackDraw() {
        if (b.shaderPackDrawProbe == null) {
            return;
        }
        if (!(b.frameTarget instanceof GllessFrameTarget target)) {
            return;
        }
        if (!b.frameBufferOpen || b.commandState.inRenderPass()) {
            // The probe draws at a frame boundary: the frame buffer must still be open and no pass may be running.
            return;
        }
        // Every frame, not once: the first frame after the probe is prepared is a loading screen, and a draw recorded
        // there is in a frame nobody captures. One triangle a frame is not a cost worth a flag.
        if (b.shaderPackDraws < 3) {
            System.out.println("[Celtium] CELTIUM_PACK_DRAW_HOOK cb=" + Long.toHexString(b.commandBuffer.address())
                    + " open=" + b.frameBufferOpen + " recording=" + b.commandState.recording()
                    + " inPass=" + b.commandState.inRenderPass() + " slot=" + b.frameSlot);
        }
        if (b.terrainCaptureVertices != null && b.terrainCaptureFinalized && !b.packGeometryAdopted) {
            // Applied here rather than where the probe is prepared: the pack loads long before the world's first terrain
            // batch arrives, so the capture is only complete by the time a draw is being recorded. It also waits for the
            // capture to close, so the first adopted geometry is several sections rather than one.
            String adopted = b.shaderPackDrawProbe.replaceGeometry(b.terrainCaptureFormat,
                    b.terrainCaptureVertices, b.terrainCaptureStride, b.terrainCaptureCount);
            // Logged on the adoption itself, not on an early frame: the capture arrives after the probe has already drawn,
            // so gating this on shaderPackDraws == 0 hid the one line that says the geometry was installed.
            if (adopted != null) {
                b.packGeometryAdopted = true;
                b.packGeometryLogged = true;
                System.out.println("[Celtium] CELTIUM_PACK_GEOMETRY " + adopted);
            }
        }

        if (b.SHADER_PACK_DRAW_BLIT) {
            // The delivery experiment: the pixels the pack's shaders already produced, transferred into the delivered
            // image at the frame boundary - no draw into the frame, no partial render area.
            if (target.colorImage() != 0L) {
                // Re-render the probe's image with whatever geometry has been installed (the capture arrives after the
                // probe is created), so the blit delivers the world through the pack's stages rather than the triangle.
                if (Boolean.getBoolean("celtium.shaderpack.probeShadow") && !b.packShadowLogged
                        && b.packGeometryAdopted) {
                    b.packShadowLogged = true;
                    System.out.println("[Celtium] CELTIUM_PACK_SHADOW "
                            + b.shaderPackDrawProbe.renderShadowDepthPlumbing());
                }
                b.shaderPackDrawProbe.setWorldMatrices(b.worldSnapshotProjection, b.lastModelViewMatrix);
                String reRendered = b.shaderPackDrawProbe.renderOffscreen();
                if (reRendered != null && !b.packReRenderLogged && reRendered.contains("vertices=3 ")) {
                    b.packReRenderLogged = true;
                    System.out.println("[Celtium] CELTIUM_PACK_RERENDER " + reRendered);
                } else if (reRendered != null && b.packReRenderLogged && !b.packGeometryReRenderLogged
                        && !reRendered.contains("vertices=3 ")) {
                    b.packGeometryReRenderLogged = true;
                    System.out.println("[Celtium] CELTIUM_PACK_RERENDER_GEOMETRY " + reRendered);
                }
                // Centre of the frame rather than the corner: the corner rectangle is uniform in the capture and the
                // probe image is not, so the location is the one thing an experiment can move to separate "the blit
                // carries the wrong content" from "something paints over the corner of the picture".
                // Full-screen by default: the question the picture has to answer now is "does the pack's output look
                // like the world", and a 256x256 rectangle in a corner answers a delivery question instead. The scaled
                // blit is the same transfer with a different destination extent.
                if (Boolean.parseBoolean(System.getProperty("celtium.shaderpack.probeFullscreen", "false"))) {
                    b.shaderPackDrawProbe.blitToFrame(b.commandBuffer, target.colorImage(), 0, 0,
                            b.frameTargetWidth, b.frameTargetHeight);
                } else {
                    int blitX = Math.max(0, (b.frameTargetWidth - 256) / 2);
                    int blitY = Math.max(0, (b.frameTargetHeight - 256) / 2);
                    b.shaderPackDrawProbe.blitToFrame(b.commandBuffer, target.colorImage(), blitX, blitY, 256, 256);
                }
                if (b.shaderPackDraws++ <= 2) {
                    // Which image the blit wrote, and which image the present will read: the delivery question is a
                    // question about identities, and the answer belongs in the log rather than in an inference from a
                    // picture that has several ways of not changing.
                    System.out.println("[Celtium] CELTIUM_PACK_DRAW blit=true draws=" + b.shaderPackDraws
                            + " frameId=" + b.frameId
                            + " wroteImage=0x" + Long.toHexString(target.colorImage())
                            + " frameTargetImage=0x" + Long.toHexString(b.frameTarget.colorImage())
                            + " sameImage=" + (target.colorImage() == b.frameTarget.colorImage())
                            + " rect=0,0 256x256 layout=GENERAL");
                }
            }
            return;
        }
        if (b.SHADER_PACK_DRAW_CLEAR) {
            if ("small".equalsIgnoreCase(b.SHADER_PACK_DRAW_PROBE_MODE)) {
                // Two rectangles in one frame, in two colours: a half-size one (the image's real dimensions matter here,
                // so they are logged) and a 64x64 one at the origin. One leg then answers both "does a partial area
                // take at all" and "does a small one".
                int width = b.frameTargetWidth;
                int height = b.frameTargetHeight;
                System.out.println("[Celtium] CELTIUM_PACK_DRAW targetSize=" + width + "x" + height);
                b.shaderPackDrawProbe.recordClear(b.commandBuffer, target, 0, 0,
                        Math.max(1, width / 2), Math.max(1, height / 2), 1.0f, 0.0f, 1.0f);
                b.shaderPackDrawProbe.recordClear(b.commandBuffer, target, 0, 0, 64, 64, 0.0f, 1.0f, 0.0f);
            } else {
                b.shaderPackDrawProbe.recordClear(b.commandBuffer, target, 0, 0, b.frameTargetWidth,
                        b.frameTargetHeight, 1.0f, 0.0f, 1.0f);
            }
        } else {
            b.shaderPackDrawProbe.record(b.commandBuffer, target);
        }
        b.frameTargetWidth = target.width();
        b.frameTargetHeight = target.height();
        b.shaderPackDraws++;
        if (b.shaderPackDraws <= 3 || b.shaderPackDraws % 300 == 0) {
            System.out.println("[Celtium] CELTIUM_PACK_DRAW recorded=true draws=" + b.shaderPackDraws);
        }
    }

    /**
     * Loads the configured shaderpack once, at the first present, and reports what the frontend made of it.
     * <p>
     * Runs here rather than in the mod's initialisation because the renderer is the component that will consume the
     * manifest, and a failure must not take the game down: a pack the frontend rejects is reported and the run
     * continues shaderless, which is the only safe direction for an experimental path.
     */
    void celtium$probeShaderPack() {
        if (b.shaderPackProbed || b.SHADER_PACK_PATH.isEmpty()) {
            return;
        }
        b.shaderPackProbed = true;
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(b.SHADER_PACK_PATH);
            long started = System.nanoTime();
            org.soutalab.celtium.shaderpack.PackManifest manifest =
                    org.soutalab.celtium.shaderpack.ShaderPackLoader.load(path);
            b.shaderPackManifest = manifest;
            // After the manifest, not before: the program probe reads the manifest to find the pack's own paths, and the
            // first version ran it first, so it returned early with nothing to report.
            celtium$probeShaderCompile();
            long millis = (System.nanoTime() - started) / 1000000L;
            for (org.soutalab.celtium.shaderpack.DimensionPlan dimension : manifest.dimensions()) {
                b.shaderPackPrograms += dimension.programs().size();
            }
            for (org.soutalab.celtium.shaderpack.Diagnostic diagnostic : manifest.diagnostics()) {
                switch (diagnostic.severity()) {
                    case ERROR: b.shaderPackDiagnostics[0]++; break;
                    case WARNING: b.shaderPackDiagnostics[1]++; break;
                    default: b.shaderPackDiagnostics[2]++; break;
                }
            }
            b.shaderPackSummary = "pack=" + manifest.label() + " files=" + manifest.fileCount()
                    + " dimensions=" + manifest.dimensions().size()
                    + " programs=" + b.shaderPackPrograms
                    + " features=" + manifest.activeFeatures().size()
                    + " options=" + manifest.options().size()
                    + " disabled=" + manifest.disabledPrograms().size()
                    + " errors=" + b.shaderPackDiagnostics[0]
                    + " warnings=" + b.shaderPackDiagnostics[1]
                    + " unsupported=" + b.shaderPackDiagnostics[2]
                    + " hasErrors=" + manifest.hasErrors()
                    + " loadMillis=" + millis;
            System.out.println("[Celtium] CELTIUM_SHADERPACK " + b.shaderPackSummary);
        } catch (Throwable error) {
            b.shaderPackSummary = "failed path=" + b.SHADER_PACK_PATH + " reason=" + b.describe(error);
            System.out.println("[Celtium] CELTIUM_SHADERPACK " + b.shaderPackSummary);
        }
    }
}
