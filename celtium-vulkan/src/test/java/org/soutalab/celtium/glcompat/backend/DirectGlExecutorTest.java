package org.soutalab.celtium.glcompat.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.glcompat.ComputeProgramHandle;
import org.soutalab.celtium.core.glcompat.DirectGlDrawState;
import org.soutalab.celtium.core.glcompat.DirectGlVulkanSink;
import org.soutalab.celtium.core.glcompat.DirectGlVulkanSink.BlitRegion;
import org.soutalab.celtium.core.glcompat.DirectGlVulkanSink.ClearSpec;
import org.soutalab.celtium.core.glcompat.DrawCommand;
import org.soutalab.celtium.core.glcompat.IndexSpan;
import org.soutalab.celtium.core.glcompat.PrimitiveTopology;
import org.soutalab.celtium.core.glcompat.VertexBindingSpan;
import org.soutalab.celtium.core.shader.SpirvModuleBundle;
import org.soutalab.celtium.core.state.LightStateRegister;
import org.soutalab.celtium.core.state.RenderStateKey;
import org.soutalab.celtium.vulkan.DirectGlSinkExtensions;

/**
 * Headless tests for the direct-GL converter's executor: object records, the fixed-function
 * state assembly, the register sync exchange, and - when the shader compiler is available in
 * the test JVM - full draw assembly into a recording fake sink. The fake sink is the test's
 * own; the production sink is {@code VulkanDirectGlSink}.
 */
final class DirectGlExecutorTest {

    private static final int GL_ARRAY_BUFFER = 0x8892;
    private static final int GL_ELEMENT_ARRAY_BUFFER = 0x8893;
    private static final int GL_STATIC_DRAW = 0x88E4;
    private static final int GL_FLOAT = 0x1406;
    private static final int GL_UNSIGNED_SHORT = 0x1403;
    private static final int GL_TRIANGLES = 0x0004;
    private static final int GL_QUADS = 0x0007;
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_RGBA8 = 0x8058;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int GL_COLOR_BUFFER_BIT = 0x4000;
    private static final int GL_DEPTH_BUFFER_BIT = 0x0100;
    private static final int GL_STENCIL_BUFFER_BIT = 0x0400;
    private static final int GL_SCISSOR_TEST = 0x0C11;
    private static final int GL_NEAREST = 0x2600;
    private static final int GL_LINEAR = 0x2601;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT = 0x8CD7;
    private static final int GL_VERTEX_SHADER = 0x8B31;
    private static final int GL_FRAGMENT_SHADER = 0x8B30;
    private static final int GL_LINK_STATUS = 0x8B82;
    private static final int GL_BLEND = 0x0BE2;
    private static final int GL_FRONT = 0x0404;

    /** A recording fake implementing both the sink surface and the internal extensions. */
    static final class FakeSink implements DirectGlVulkanSink, DirectGlSinkExtensions {
        long nextTexture = 100;
        long nextBuffer = 200;
        long nextFramebuffer = 300;
        long nextProgram = 400;
        final Map<Long, ByteBuffer> bufferBytes = new HashMap<>();
        final Map<Long, int[]> textureSizes = new HashMap<>();
        final List<long[]> textureWrites = new ArrayList<>();
        final List<Draw> draws = new ArrayList<>();
        final List<ExtendedFixedState> extendedStates = new ArrayList<>();
        final Map<Long, AttribDesc[]> programAttribs = new HashMap<>();
        final Map<Long, ByteBuffer> uniformUpdates = new HashMap<>();
        final List<Clear> clears = new ArrayList<>();
        final List<Blit> blits = new ArrayList<>();
        int computeDispatches;

        record Clear(long target, ClearSpec spec) {
        }

        record Blit(long source, long destination, BlitRegion region) {
        }

        record Draw(DirectGlDrawState state, VertexBindingSpan vertices, IndexSpan indices,
                    DrawCommand command) {
        }

        @Override
        public void submitDraw(DirectGlDrawState state, VertexBindingSpan vertices, IndexSpan indices,
                               DrawCommand draw) {
            this.draws.add(new Draw(state, vertices, indices, draw));
        }

        @Override
        public void dispatchCompute(ComputeProgramHandle program, int x, int y, int z) {
            this.computeDispatches++;
        }

        @Override
        public TextureOps textureOps() {
            return new TextureOps() {
                @Override
                public long createTexture2D(int width, int height, int format, int mipLevels) {
                    long id = nextTexture++;
                    textureSizes.put(id, new int[] {width, height});
                    return id;
                }

                @Override
                public void writeTexture(long textureId, int level, int x, int y, int width, int height,
                                         MemorySegment pixels) {
                    textureWrites.add(new long[] {textureId, level, x, y, width, height,
                            pixels == null ? -1 : pixels.byteSize()});
                }

                @Override
                public void destroyTexture(long textureId) {
                    textureSizes.remove(textureId);
                }
            };
        }

        @Override
        public BufferOps bufferOps() {
            return new BufferOps() {
                @Override
                public long createBuffer(long sizeBytes, int usageFlags) {
                    long id = nextBuffer++;
                    bufferBytes.put(id, ByteBuffer.allocateDirect((int) sizeBytes)
                            .order(ByteOrder.nativeOrder()));
                    return id;
                }

                @Override
                public void writeBuffer(long bufferId, long offsetBytes, MemorySegment data) {
                    ByteBuffer target = bufferBytes.get(bufferId);
                    if (target != null && data != null) {
                        MemorySegment.copy(data, 0L,
                                MemorySegment.ofBuffer(target), offsetBytes, data.byteSize());
                    }
                }

                @Override
                public void destroyBuffer(long bufferId) {
                    bufferBytes.remove(bufferId);
                }
            };
        }

        @Override
        public FramebufferOps framebufferOps() {
            return new FramebufferOps() {
                @Override
                public long createFramebuffer(long[] colorTextureIds, long depthTextureId,
                                              int width, int height) {
                    return nextFramebuffer++;
                }

                @Override
                public void destroyFramebuffer(long framebufferId) {
                }

                @Override
                public void clearFramebuffer(long framebufferId, ClearSpec spec) {
                    clears.add(new Clear(framebufferId, spec));
                }

                @Override
                public void blitFramebuffer(long source, long destination, BlitRegion region) {
                    blits.add(new Blit(source, destination, region));
                }
            };
        }

        @Override
        public ShaderOps shaderOps() {
            return new ShaderOps() {
                @Override
                public long createProgram(SpirvModuleBundle bundle) {
                    return nextProgram++;
                }

                @Override
                public ComputeProgramHandle createComputeProgram(SpirvModuleBundle bundle) {
                    return new ComputeProgramHandle(nextProgram++);
                }

                @Override
                public void destroyProgram(long programHandle) {
                }
            };
        }

        @Override
        public void setExtendedFixedState(ExtendedFixedState state) {
            this.extendedStates.add(state);
        }

        @Override
        public void registerProgramAttribs(long programHandle, AttribDesc[] attribs) {
            this.programAttribs.put(programHandle, attribs);
        }

        @Override
        public void updateUniforms(long programHandle, MemorySegment values) {
            ByteBuffer copy = ByteBuffer.allocateDirect((int) values.byteSize())
                    .order(ByteOrder.nativeOrder());
            MemorySegment.copy(values, 0L, MemorySegment.ofBuffer(copy), 0L, values.byteSize());
            this.uniformUpdates.put(programHandle, copy);
        }
    }

    private static DirectGlExecutor executorWith(FakeSink sink) {
        DirectGlExecutor executor = new DirectGlExecutor();
        executor.setSink(sink);
        return executor;
    }

    @Test
    void bufferLifecycleRoundTripsThroughTheSink() {
        FakeSink sink = new FakeSink();
        DirectGlExecutor executor = executorWith(sink);
        int buffer = executor.genBuffers();
        executor.bindBuffer(GL_ARRAY_BUFFER, buffer);
        ByteBuffer data = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        for (int i = 0; i < 4; i++) {
            data.putFloat(i * 4, i + 0.5f);
        }
        executor.bufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
        assertEquals(16, executor.getBufferParameteri(GL_ARRAY_BUFFER, 0x8764));
        // The sink received exactly one buffer with the same bytes.
        assertEquals(1, sink.bufferBytes.size());
        ByteBuffer written = sink.bufferBytes.values().iterator().next();
        assertEquals(2.5f, written.getFloat(8));
        // Map, mutate, unmap: the mutation reaches the sink on unmap.
        ByteBuffer mapped = executor.mapBuffer(GL_ARRAY_BUFFER, 0x88B9);
        mapped.putFloat(0, 99.0f);
        assertTrue(executor.unmapBuffer(GL_ARRAY_BUFFER));
        assertEquals(99.0f, sink.bufferBytes.values().iterator().next().getFloat(0));
        // Readback answers from the shadow.
        ByteBuffer out = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        executor.getBufferSubData(GL_ARRAY_BUFFER, 8, out);
        assertEquals(2.5f, out.getFloat(0));
    }

    @Test
    void textureLifecycleCreatesAndWritesSinkTextures() {
        FakeSink sink = new FakeSink();
        DirectGlExecutor executor = executorWith(sink);
        int texture = executor.genTextures();
        executor.bindTexture(GL_TEXTURE_2D, texture);
        ByteBuffer pixels = ByteBuffer.allocateDirect(4 * 4 * 4);
        executor.texImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 4, 4, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        assertEquals(1, sink.textureSizes.size());
        assertEquals(4, sink.textureSizes.values().iterator().next()[0]);
        assertEquals(1, sink.textureWrites.size());
        assertEquals(4L * 4 * 4, sink.textureWrites.get(0)[6]);
        assertEquals(4, executor.getTexLevelParameteri(GL_TEXTURE_2D, 0, 0x1000));
        assertEquals(2, executor.getTexLevelParameteri(GL_TEXTURE_2D, 1, 0x1000));
    }

    @Test
    void registerSyncRoundTripsTheFixedState() {
        FakeSink sink = new FakeSink();
        DirectGlExecutor executor = executorWith(sink);
        executor.enable(GL_BLEND);
        executor.depthMask(false);
        executor.enable(0x0B44);
        executor.cullFace(GL_FRONT);
        executor.colorMask(true, false, true, false);
        try (LightStateRegister register = new LightStateRegister()) {
            executor.syncToRegister(register);
            RenderStateKey key = register.key();
            assertTrue(key.blendEnabled());
            assertEquals(false, key.depthWriteEnabled());
            assertEquals(RenderStateKey.CULL_FRONT, key.cullMode());
            assertEquals(0b0101, key.colorWriteMask());

            // And back: a register written by Lane 2 seeds the converter's state.
            register.setKey(RenderStateKey.builder().blendEnabled(false)
                    .depthWriteEnabled(true).cullMode(RenderStateKey.CULL_BACK).build());
            executor.syncFromRegister(register);
            executor.syncToRegister(register);
            assertEquals(false, register.key().blendEnabled());
            assertTrue(register.key().depthWriteEnabled());
            assertEquals(RenderStateKey.CULL_BACK, register.key().cullMode());
        }
    }

    @Test
    void framebufferCompletenessFollowsAttachments() {
        FakeSink sink = new FakeSink();
        DirectGlExecutor executor = executorWith(sink);
        int fbo = executor.genFramebuffers();
        executor.bindFramebuffer(GL_FRAMEBUFFER, fbo);
        assertEquals(GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT,
                executor.checkFramebufferStatus(GL_FRAMEBUFFER));
        int texture = executor.genTextures();
        executor.bindTexture(GL_TEXTURE_2D, texture);
        executor.texStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, 64, 32);
        executor.framebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        assertEquals(GL_FRAMEBUFFER_COMPLETE, executor.checkFramebufferStatus(GL_FRAMEBUFFER));
    }

    @Test
    void clearAndClearBufferUseTheDrawTargetAndCurrentScissor() {
        FakeSink sink = new FakeSink();
        DirectGlExecutor executor = executorWith(sink);
        int fbo = framebufferWithColor(executor);
        executor.clearDepth(0.5);
        executor.clear(GL_DEPTH_BUFFER_BIT);

        executor.bindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
        executor.enable(GL_SCISSOR_TEST);
        executor.scissor(-3, 5, 10, 12);
        executor.clearColor(0.25f, 0.5f, 0.75f, 1.0f);
        executor.clearStencil(4);
        executor.clear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        FloatBuffer buffer = FloatBuffer.wrap(new float[] {-1, 1, 0.75f, 0.5f, 0.25f});
        buffer.position(1);
        executor.clearBufferfv(0x1800, 0, buffer); // GL_COLOR
        executor.clearBufferfi(0x84F9, 0, 0.75f, 9); // GL_DEPTH_STENCIL
        executor.clearBufferiv(0x1802, 0, IntBuffer.wrap(new int[] {13})); // GL_STENCIL
        executor.clear(GL_COLOR_BUFFER_BIT);

        assertEquals(1, buffer.position());
        ClearSpec modClear = new ClearSpec(DirectGlVulkanSink.COLOR_ASPECT | DirectGlVulkanSink.STENCIL_ASPECT,
                0.25f, 0.5f, 0.75f, 1, 0.5f, 4, true, -3, 5, 10, 12);
        assertEquals(List.of(
                new FakeSink.Clear(0, new ClearSpec(DirectGlVulkanSink.DEPTH_ASPECT,
                        0, 0, 0, 0, 0.5f, 0, false, 0, 0, 0, 0)),
                new FakeSink.Clear(300, modClear),
                new FakeSink.Clear(300, new ClearSpec(DirectGlVulkanSink.COLOR_ASPECT,
                        1, 0.75f, 0.5f, 0.25f, 0, 0, true, -3, 5, 10, 12)),
                new FakeSink.Clear(300, new ClearSpec(DirectGlVulkanSink.DEPTH_ASPECT | DirectGlVulkanSink.STENCIL_ASPECT,
                        0, 0, 0, 0, 0.75f, 9, true, -3, 5, 10, 12)),
                new FakeSink.Clear(300, new ClearSpec(DirectGlVulkanSink.STENCIL_ASPECT,
                        0, 0, 0, 0, 0, 13, true, -3, 5, 10, 12)),
                new FakeSink.Clear(300, new ClearSpec(DirectGlVulkanSink.COLOR_ASPECT,
                        0.25f, 0.5f, 0.75f, 1, 0.5f, 4, true, -3, 5, 10, 12))), sink.clears);
    }

    @Test
    void boundAndNamedBlitsKeepTheirEndpointsAndUseSinkFramebufferIds() {
        FakeSink sink = new FakeSink();
        DirectGlExecutor executor = executorWith(sink);
        int fbo = framebufferWithColor(executor);
        executor.bindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
        executor.blitFramebuffer(2, 3, 30, 14, 44, 8, 4, 24, GL_COLOR_BUFFER_BIT, GL_LINEAR);
        executor.blitNamedFramebuffer(0, fbo, 7, 8, 29, 20, 1, 2, 23, 14,
                GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT, GL_NEAREST);

        int incomplete = executor.genFramebuffers();
        executor.bindFramebuffer(GL_DRAW_FRAMEBUFFER, incomplete);
        executor.clear(GL_COLOR_BUFFER_BIT);
        executor.blitNamedFramebuffer(incomplete, 0, 0, 0, 8, 8, 0, 0, 8, 8, GL_COLOR_BUFFER_BIT, GL_NEAREST);

        assertTrue(sink.clears.isEmpty());
        assertEquals(List.of(
                new FakeSink.Blit(300, 0, new BlitRegion(2, 3, 30, 14, 44, 8, 4, 24,
                        DirectGlVulkanSink.COLOR_ASPECT, true)),
                new FakeSink.Blit(0, 300, new BlitRegion(7, 8, 29, 20, 1, 2, 23, 14,
                        DirectGlVulkanSink.DEPTH_ASPECT | DirectGlVulkanSink.STENCIL_ASPECT, false))), sink.blits);
    }

    private static int framebufferWithColor(DirectGlExecutor executor) {
        int fbo = executor.genFramebuffers();
        int texture = executor.genTextures();
        executor.bindTexture(GL_TEXTURE_2D, texture);
        executor.texStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, 64, 32);
        executor.namedFramebufferTexture(fbo, GL_COLOR_ATTACHMENT0, texture, 0);
        return fbo;
    }

    @Test
    void failedShaderLinkIsReportedNotThrown() {
        FakeSink sink = new FakeSink();
        DirectGlExecutor executor = executorWith(sink);
        int program = executor.createProgram();
        int vs = executor.createShader(GL_VERTEX_SHADER);
        executor.shaderSource(vs, "this is not glsl at all");
        executor.attachShader(program, vs);
        int fs = executor.createShader(GL_FRAGMENT_SHADER);
        executor.shaderSource(fs, "neither is this");
        executor.attachShader(program, fs);
        executor.linkProgram(program);
        assertEquals(0, executor.getProgrami(program, GL_LINK_STATUS));
        assertNotEquals("", executor.getProgramInfoLog(program, 4096));
    }

    /** Links the GTCEu-bloom-shaped program the translator's own tests use. */
    private int linkBloomProgram(DirectGlExecutor executor) {
        int program = executor.createProgram();
        int vs = executor.createShader(GL_VERTEX_SHADER);
        executor.shaderSource(vs, """
                #version 120
                attribute vec2 position;
                void main() { gl_Position = vec4(position, 0.0, 1.0); }
                """);
        executor.attachShader(program, vs);
        int fs = executor.createShader(GL_FRAGMENT_SHADER);
        executor.shaderSource(fs, """
                #version 120
                uniform sampler2D tex;
                void main() { gl_FragColor = texture2D(tex, vec2(0.5)); }
                """);
        executor.attachShader(program, fs);
        executor.linkProgram(program);
        return program;
    }

    @Test
    void drawArraysReachesTheSinkWithCanonicalSpan() {
        FakeSink sink = new FakeSink();
        DirectGlExecutor executor = executorWith(sink);
        int program = linkBloomProgram(executor);
        Assumptions.assumeTrue(executor.getProgrami(program, GL_LINK_STATUS) == 1,
                "shader compiler unavailable or translator refused in this JVM");
        executor.useProgram(program);

        int vao = executor.genVertexArrays();
        executor.bindVertexArray(vao);
        int buffer = executor.genBuffers();
        executor.bindBuffer(GL_ARRAY_BUFFER, buffer);
        ByteBuffer vertices = ByteBuffer.allocateDirect(3 * 2 * 4).order(ByteOrder.nativeOrder());
        vertices.asFloatBuffer().put(new float[] {-1f, -1f, 1f, -1f, 0f, 1f});
        executor.bufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        executor.vertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0);
        executor.enableVertexAttribArray(0);

        executor.drawArrays(GL_TRIANGLES, 0, 3);
        assertEquals(1, sink.draws.size());
        FakeSink.Draw draw = sink.draws.get(0);
        assertEquals(PrimitiveTopology.TRIANGLES, draw.state().topology());
        assertEquals(0, draw.state().renderTargetId());
        assertEquals(8, draw.vertices().strideBytes());
        assertEquals(3, draw.command().vertexCount());
        // The program's attribute layout was registered with the sink.
        assertTrue(sink.programAttribs.containsKey(draw.state().programHandle()));
    }

    @Test
    void quadDrawsExpandToIndexedTriangles() {
        FakeSink sink = new FakeSink();
        DirectGlExecutor executor = executorWith(sink);
        int program = linkBloomProgram(executor);
        Assumptions.assumeTrue(executor.getProgrami(program, GL_LINK_STATUS) == 1,
                "shader compiler unavailable or translator refused in this JVM");
        executor.useProgram(program);
        int vao = executor.genVertexArrays();
        executor.bindVertexArray(vao);
        int buffer = executor.genBuffers();
        executor.bindBuffer(GL_ARRAY_BUFFER, buffer);
        ByteBuffer vertices = ByteBuffer.allocateDirect(4 * 2 * 4).order(ByteOrder.nativeOrder());
        vertices.asFloatBuffer().put(new float[] {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f});
        executor.bufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        executor.vertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0);
        executor.enableVertexAttribArray(0);

        executor.drawArrays(GL_QUADS, 0, 4);
        assertEquals(1, sink.draws.size());
        FakeSink.Draw draw = sink.draws.get(0);
        assertEquals(PrimitiveTopology.TRIANGLES, draw.state().topology());
        assertTrue(draw.command().indexed());
        assertEquals(6, draw.command().indexCount());
    }

    @Test
    void indexedDrawUsesTheElementBuffer() {
        FakeSink sink = new FakeSink();
        DirectGlExecutor executor = executorWith(sink);
        int program = linkBloomProgram(executor);
        Assumptions.assumeTrue(executor.getProgrami(program, GL_LINK_STATUS) == 1,
                "shader compiler unavailable or translator refused in this JVM");
        executor.useProgram(program);
        int vao = executor.genVertexArrays();
        executor.bindVertexArray(vao);
        int buffer = executor.genBuffers();
        executor.bindBuffer(GL_ARRAY_BUFFER, buffer);
        ByteBuffer vertices = ByteBuffer.allocateDirect(4 * 2 * 4).order(ByteOrder.nativeOrder());
        vertices.asFloatBuffer().put(new float[] {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f});
        executor.bufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        executor.vertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0);
        executor.enableVertexAttribArray(0);
        int elements = executor.genBuffers();
        executor.bindBuffer(GL_ELEMENT_ARRAY_BUFFER, elements);
        ByteBuffer indices = ByteBuffer.allocateDirect(6 * 2).order(ByteOrder.nativeOrder());
        indices.asShortBuffer().put(new short[] {0, 1, 2, 0, 2, 3});
        executor.bufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        executor.drawElements(GL_TRIANGLES, 6, GL_UNSIGNED_SHORT, 0L);
        assertEquals(1, sink.draws.size());
        FakeSink.Draw draw = sink.draws.get(0);
        assertTrue(draw.command().indexed());
        assertEquals(6, draw.command().indexCount());
        assertEquals(IndexSpan.IndexType.UINT16, draw.indices().indexType());
    }

    @Test
    void drawWithoutProgramIsDroppedNotThrown() {
        FakeSink sink = new FakeSink();
        DirectGlExecutor executor = executorWith(sink);
        executor.drawArrays(GL_TRIANGLES, 0, 3);
        assertEquals(0, sink.draws.size());
        assertEquals(1, executor.droppedDraws());
    }

    /**
     * The interface-3 contract, clause 2, in source form: Lane 2's representative in-tree code
     * (ModelReplay, the Forge-baked-model receiver) must not reference the converter's
     * dispatcher, executor or sink - it reads the LightStateRegister only.
     */
    @Test
    void lane2RepresentativeNeverReferencesTheConverter() throws Exception {
        Path source = Path.of("src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java");
        if (!Files.exists(source)) {
            source = Path.of("celtium-vulkan").resolve(source);
        }
        Assumptions.assumeTrue(Files.exists(source), "module sources not reachable from the test cwd");
        String text = Files.readString(source);
        for (String forbidden : List.of("DirectGlDispatcher", "DirectGlVulkanSink", "DirectGlExecutor")) {
            assertTrue(!text.contains(forbidden),
                    "ModelReplay (Lane 2) must not reference " + forbidden);
        }
    }
}
