package org.soutalab.celtium.glcompat.backend;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;
import org.soutalab.celtium.core.glcompat.ComputeProgramHandle;
import org.soutalab.celtium.core.glcompat.DirectGlDrawState;
import org.soutalab.celtium.core.glcompat.DirectGlVulkanSink;
import org.soutalab.celtium.core.glcompat.DrawCommand;
import org.soutalab.celtium.core.glcompat.IndexSpan;
import org.soutalab.celtium.core.glcompat.PrimitiveTopology;
import org.soutalab.celtium.core.glcompat.VertexBindingSpan;
import org.soutalab.celtium.core.state.LightStateRegister;
import org.soutalab.celtium.core.state.RenderStateKey;
import org.soutalab.celtium.glcompat.GLSMMatrices;
import org.soutalab.celtium.vulkan.DirectGlSinkExtensions;

/**
 * The working half of {@link DirectGlExecutor}: the GL object records, the fixed-function state
 * the draws assemble from, and the translation of every executor call into the celtium-native
 * sink surface ({@link DirectGlVulkanSink}). The executor class itself is the supplier-shaped
 * facade GLSM calls; everything semantic lives here.
 * <p>
 * State discipline: the executor tracks its own copy of every fixed-function value its methods
 * receive (GLSM keeps its own copies for its state-machine duties; both are fed by the same
 * call stream). At a draw, the tracked state becomes a {@link DirectGlDrawState} - the
 * {@link RenderStateKey} for the packed half, the extended state channel for the rest - and the
 * current VAO's attributes become one canonical vertex span: when the VAO's buffers already
 * match the program's canonical packing the span points straight into the sink buffer; when
 * they do not, the referenced vertices are re-packed from the CPU shadows into a streaming
 * buffer. Either way the sink sees exactly one span per draw, as its contract fixes.
 * <p>
 * Known limits, stated once here and again in the stage's landing record: clears and blits have
 * no sink op (the main target's clear belongs to Lane 2; a mod framebuffer's clear is dropped),
 * {@code readPixels}/{@code getTexImage} answer zeros for content only the GPU holds, instanced
 * attributes (divisor &gt; 0) are fetched per-vertex, and compute programs are refused by the
 * inherited shader compiler.
 */
final class DirectGlRuntime {

    // GL constants (spec values; the executor speaks GL on its face and Vulkan to the sink).
    static final int GL_POINTS = 0x0000;
    static final int GL_LINES = 0x0001;
    static final int GL_LINE_LOOP = 0x0002;
    static final int GL_LINE_STRIP = 0x0003;
    static final int GL_TRIANGLES = 0x0004;
    static final int GL_TRIANGLE_STRIP = 0x0005;
    static final int GL_TRIANGLE_FAN = 0x0006;
    static final int GL_QUADS = 0x0007;
    static final int GL_TEXTURE_2D = 0x0DE1;
    static final int GL_TEXTURE_3D = 0x806F;
    static final int GL_TEXTURE_CUBE_MAP = 0x8513;
    static final int GL_ARRAY_BUFFER = 0x8892;
    static final int GL_ELEMENT_ARRAY_BUFFER = 0x8893;
    static final int GL_UNIFORM_BUFFER = 0x8A11;
    static final int GL_FRAMEBUFFER = 0x8D40;
    static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    static final int GL_RENDERBUFFER = 0x8D41;
    static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    static final int GL_DEPTH_ATTACHMENT = 0x8D00;
    static final int GL_STENCIL_ATTACHMENT = 0x8D20;
    static final int GL_DEPTH_STENCIL_ATTACHMENT = 0x821A;
    static final int GL_VERTEX_SHADER = 0x8B31;
    static final int GL_FRAGMENT_SHADER = 0x8B30;
    static final int GL_GEOMETRY_SHADER = 0x8DD9;
    static final int GL_COMPUTE_SHADER = 0x91B9;
    static final int GL_BLEND = 0x0BE2;
    static final int GL_DEPTH_TEST = 0x0B71;
    static final int GL_CULL_FACE = 0x0B44;
    static final int GL_SCISSOR_TEST = 0x0C11;
    static final int GL_STENCIL_TEST = 0x0B90;
    static final int GL_COLOR_LOGIC_OP = 0x0BF2;
    static final int GL_TEXTURE0 = 0x84C0;
    static final int GL_UNSIGNED_BYTE = 0x1401;
    static final int GL_UNSIGNED_SHORT = 0x1403;
    static final int GL_UNSIGNED_INT = 0x1405;
    static final int GL_FLOAT = 0x1406;
    static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    static final int GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT = 0x8CD7;
    static final int GL_LINK_STATUS = 0x8B82;
    static final int GL_COMPILE_STATUS = 0x8B81;
    static final int GL_ACTIVE_UNIFORMS = 0x8B86;
    static final int GL_ACTIVE_ATTRIBUTES = 0x8B89;
    static final int GL_INFO_LOG_LENGTH = 0x8B84;
    static final int GL_SHADER_SOURCE_LENGTH = 0x8B88;
    static final int GL_TEXTURE_WIDTH = 0x1000;
    static final int GL_TEXTURE_HEIGHT = 0x1001;
    static final int GL_TEXTURE_INTERNAL_FORMAT = 0x1003;
    static final int GL_BUFFER_SIZE = 0x8764;
    static final int GL_BUFFER_USAGE = 0x8765;
    static final int GL_VERTEX_ATTRIB_ARRAY_ENABLED = 0x8622;
    static final int GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING = 0x889F;
    static final int GL_VERTEX_ATTRIB_ARRAY_SIZE = 0x8623;
    static final int GL_VERTEX_ATTRIB_ARRAY_STRIDE = 0x8624;
    static final int GL_VERTEX_ATTRIB_ARRAY_TYPE = 0x8625;
    static final int GL_VERTEX_ATTRIB_ARRAY_NORMALIZED = 0x886A;
    static final int GL_VERTEX_ATTRIB_ARRAY_DIVISOR = 0x88FE;
    static final int GL_MAX_TEXTURE_SIZE = 0x0D33;
    static final int GL_MAX_TEXTURE_IMAGE_UNITS = 0x8872;
    static final int GL_MAX_VERTEX_ATTRIBS = 0x8869;
    static final int GL_VIEWPORT = 0x0BA2;
    static final int GL_VENDOR = 0x1F00;
    static final int GL_RENDERER = 0x1F01;
    static final int GL_VERSION = 0x1F02;
    static final int GL_SHADING_LANGUAGE_VERSION = 0x8B8C;
    static final int GL_EXTENSIONS = 0x1F03;
    static final int GL_NO_ERROR = 0;
    static final int GL_ALREADY_SIGNALED = 0x911A;
    static final int GL_SYNC_GPU_COMMANDS_COMPLETE = 0x9117;

    private DirectGlVulkanSink sink;

    // Object spaces.
    private int nextTexture = 1;
    private int nextBuffer = 1;
    private int nextSampler = 1;
    private int nextRenderbuffer = 1;
    private int nextFramebuffer = 1;
    private int nextShader = 1;
    private int nextProgram = 1;
    private int nextVertexArray = 1;
    private long nextSync = 1L;
    final Map<Integer, DirectGlRecords.Texture> textures = new HashMap<>();
    final Map<Integer, DirectGlRecords.Buffer> buffers = new HashMap<>();
    final Map<Integer, DirectGlRecords.Sampler> samplers = new HashMap<>();
    final Map<Integer, DirectGlRecords.Renderbuffer> renderbuffers = new HashMap<>();
    final Map<Integer, DirectGlRecords.Framebuffer> framebuffers = new HashMap<>();
    final Map<Integer, DirectGlRecords.Shader> shaders = new HashMap<>();
    final Map<Integer, DirectGlRecords.Program> programs = new HashMap<>();
    final Map<Integer, DirectGlRecords.VertexArray> vertexArrays = new HashMap<>();

    // Fixed-function state.
    final Set<Integer> enabledCaps = new HashSet<>();
    final Map<Integer, Set<Integer>> enabledCapsIndexed = new HashMap<>();
    int blendSrcRgb = 1, blendDstRgb = 0, blendSrcAlpha = 1, blendDstAlpha = 0; // GL_ONE, GL_ZERO
    int blendEquationRgb = 0x8006, blendEquationAlpha = 0x8006; // GL_FUNC_ADD
    final float[] blendColor = {0f, 0f, 0f, 0f};
    int depthFunc = 0x0201; // GL_LESS
    boolean depthMask = true;
    final boolean[] colorMask = {true, true, true, true};
    int cullFaceMode = 0x0405; // GL_BACK
    int frontFaceMode = 0x0901; // GL_CCW
    int polygonMode = 0x1B02; // GL_FILL
    float polygonOffsetFactor, polygonOffsetUnits;
    int stencilFunc = 0x0207, stencilRef, stencilFuncMask = 0xFFFFFFFF; // GL_ALWAYS
    int stencilWriteMask = 0xFFFFFFFF;
    int stencilFail = 0x1E00, stencilDepthFail = 0x1E00, stencilPass = 0x1E00; // GL_KEEP
    final int[] viewport = {0, 0, 0, 0};
    final int[] scissor = {0, 0, 0, 0};
    final double[] depthRange = {0.0, 1.0};
    final float[] clearColor = {0f, 0f, 0f, 0f};
    double clearDepth = 1.0;
    int clearStencil;
    float lineWidth = 1.0f;
    float pointSize = 1.0f;
    int logicOp = 0x1503; // GL_COPY
    final Map<Integer, Integer> pixelStore = new HashMap<>();

    // Bindings.
    int activeTextureUnit;
    final Map<Long, Integer> boundTextures = new HashMap<>(); // (unit, target) -> texture id
    final Map<Integer, Integer> boundSamplers = new HashMap<>(); // unit -> sampler id
    final Map<Integer, Integer> boundBuffers = new HashMap<>(); // target -> buffer id
    final Map<Long, Integer> boundIndexedBuffers = new HashMap<>(); // (target, index) -> buffer id
    int currentProgram;
    int boundVertexArray;
    final Map<Integer, Integer> boundFramebuffers = new HashMap<>(); // target -> fbo id
    int boundRenderbuffer;
    int drawBufferMode = 0x8CE0;
    int readBufferMode = 0x8CE0;
    final float[][] genericAttribs = new float[16][4];

    // Streaming (repack) buffers in sink space.
    private long streamVertexSinkId;
    private long streamVertexCapacity;
    private long streamIndexSinkId;
    private long streamIndexCapacity;

    private long lastPushedExtendedHash = Long.MIN_VALUE;
    private int droppedDraws;

    DirectGlRuntime() {
        this.vertexArrays.put(0, new DirectGlRecords.VertexArray());
    }

    // -- sink management ------------------------------------------------------

    public void setSink(DirectGlVulkanSink sink) {
        this.sink = sink;
    }

    DirectGlVulkanSink sink() {
        return this.sink;
    }

    private DirectGlSinkExtensions ext() {
        return this.sink instanceof DirectGlSinkExtensions extensions ? extensions : null;
    }

    int droppedDraws() {
        return this.droppedDraws;
    }

    // -- register sync (the interface-3 contract exchange) ----------------------

    /** Register -> converter: the excursion starts from Lane 2's truth. */
    public void syncFromRegister(LightStateRegister register) {
        RenderStateKey key = register.key();
        setCap(GL_BLEND, key.blendEnabled());
        setCap(GL_DEPTH_TEST, key.depthTestEnabled());
        this.depthMask = key.depthWriteEnabled();
        setCap(GL_STENCIL_TEST, key.stencilTestEnabled());
        setCap(GL_SCISSOR_TEST, key.scissorTestEnabled());
        setCap(GL_COLOR_LOGIC_OP, key.logicOpEnabled());
        setCap(GL_CULL_FACE, key.cullMode() != RenderStateKey.CULL_OFF);
        this.cullFaceMode = switch (key.cullMode()) {
            case RenderStateKey.CULL_FRONT -> 0x0404;
            case RenderStateKey.CULL_FRONT_AND_BACK -> 0x0408;
            default -> 0x0405;
        };
        this.polygonMode = switch (key.polygonMode()) {
            case RenderStateKey.POLYGON_LINE -> 0x1B01;
            case RenderStateKey.POLYGON_POINT -> 0x1B00;
            default -> 0x1B02;
        };
        int mask = key.colorWriteMask();
        for (int i = 0; i < 4; i++) {
            this.colorMask[i] = (mask & (1 << i)) != 0;
        }
        this.activeTextureUnit = key.activeTextureUnit();
        int bound = register.boundTextureId();
        if (bound != 0) {
            this.boundTextures.put(textureKey(this.activeTextureUnit, GL_TEXTURE_2D), bound);
        }
        float[] matrix = new float[16];
        register.currentMatrix(matrix);
        GLSMMatrices.setModelViewMatrix(new Matrix4f().set(matrix));
    }

    /** Converter -> register: Lane 2 re-reads the state the excursion leaves behind. */
    public void syncToRegister(LightStateRegister register) {
        register.setKey(stateKey());
        Integer bound = this.boundTextures.get(textureKey(this.activeTextureUnit, GL_TEXTURE_2D));
        register.setBoundTextureId(bound == null ? 0 : bound);
        float[] matrix = new float[16];
        GLSMMatrices.getModelViewMatrix().get(matrix);
        register.setCurrentMatrix(matrix);
    }

    private void setCap(int cap, boolean enabled) {
        if (enabled) {
            this.enabledCaps.add(cap);
        } else {
            this.enabledCaps.remove(cap);
        }
    }

    /** The packed half of the draw state, from the tracked fixed-function values. */
    RenderStateKey stateKey() {
        int mask = 0;
        for (int i = 0; i < 4; i++) {
            if (this.colorMask[i]) {
                mask |= 1 << i;
            }
        }
        int cull = !this.enabledCaps.contains(GL_CULL_FACE) ? RenderStateKey.CULL_OFF
                : switch (this.cullFaceMode) {
                    case 0x0404 -> RenderStateKey.CULL_FRONT;
                    case 0x0408 -> RenderStateKey.CULL_FRONT_AND_BACK;
                    default -> RenderStateKey.CULL_BACK;
                };
        int polygon = switch (this.polygonMode) {
            case 0x1B01 -> RenderStateKey.POLYGON_LINE;
            case 0x1B00 -> RenderStateKey.POLYGON_POINT;
            default -> RenderStateKey.POLYGON_FILL;
        };
        return RenderStateKey.builder()
                .blendEnabled(this.enabledCaps.contains(GL_BLEND))
                .depthTestEnabled(this.enabledCaps.contains(GL_DEPTH_TEST))
                .depthWriteEnabled(this.depthMask)
                .stencilTestEnabled(this.enabledCaps.contains(GL_STENCIL_TEST))
                .scissorTestEnabled(this.enabledCaps.contains(GL_SCISSOR_TEST))
                .logicOpEnabled(this.enabledCaps.contains(GL_COLOR_LOGIC_OP))
                .cullMode(cull)
                .polygonMode(polygon)
                .colorWriteMask(mask)
                .activeTextureUnit(this.activeTextureUnit)
                .build();
    }

    /** Pushes the extended fixed state to the sink when it (or the dynamic rects) changed. */
    private void pushExtendedState() {
        DirectGlSinkExtensions ext = ext();
        if (ext == null) {
            return;
        }
        DirectGlSinkExtensions.ExtendedFixedState state = DirectGlSinkExtensions.ExtendedFixedState.of(
                blendFactor(this.blendSrcRgb), blendFactor(this.blendDstRgb),
                blendFactor(this.blendSrcAlpha), blendFactor(this.blendDstAlpha),
                blendOp(this.blendEquationRgb), blendOp(this.blendEquationAlpha),
                this.blendColor[0], this.blendColor[1], this.blendColor[2], this.blendColor[3],
                compareOp(this.depthFunc), logicOpVk(this.logicOp),
                compareOp(this.stencilFunc), this.stencilRef, this.stencilFuncMask, this.stencilWriteMask,
                stencilOp(this.stencilFail), stencilOp(this.stencilDepthFail), stencilOp(this.stencilPass),
                this.frontFaceMode == 0x0901, this.polygonOffsetFactor, this.polygonOffsetUnits,
                this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3],
                this.scissor[0], this.scissor[1], this.scissor[2], this.scissor[3],
                this.lineWidth);
        long signature = state.hash()
                ^ ((long) this.viewport[2] << 40) ^ ((long) this.viewport[3] << 24)
                ^ ((long) this.scissor[2] << 12) ^ Float.floatToIntBits(this.lineWidth);
        if (signature != this.lastPushedExtendedHash) {
            this.lastPushedExtendedHash = signature;
            ext.setExtendedFixedState(state);
        }
    }

    // GL -> Vulkan enum translations.
    static int blendFactor(int gl) {
        return switch (gl) {
            case 0 -> org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ZERO;
            case 1 -> org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE;
            case 0x0300 -> org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_COLOR;
            case 0x0301 -> org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
            case 0x0302 -> org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA;
            case 0x0303 -> org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            case 0x0304 -> org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_DST_ALPHA;
            case 0x0305 -> org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
            case 0x0306 -> org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_DST_COLOR;
            case 0x0307 -> org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
            case 0x0308 -> org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA_SATURATE;
            case 0x8001 -> org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_CONSTANT_COLOR;
            case 0x8002 -> org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
            case 0x8003 -> org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_CONSTANT_ALPHA;
            case 0x8004 -> org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA;
            default -> org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE;
        };
    }

    static int blendOp(int gl) {
        return switch (gl) {
            case 0x800A -> org.lwjgl.vulkan.VK10.VK_BLEND_OP_SUBTRACT;
            case 0x800B -> org.lwjgl.vulkan.VK10.VK_BLEND_OP_REVERSE_SUBTRACT;
            case 0x8007 -> org.lwjgl.vulkan.VK10.VK_BLEND_OP_MIN;
            case 0x8008 -> org.lwjgl.vulkan.VK10.VK_BLEND_OP_MAX;
            default -> org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD;
        };
    }

    static int compareOp(int gl) {
        return switch (gl) {
            case 0x0200 -> org.lwjgl.vulkan.VK10.VK_COMPARE_OP_NEVER;
            case 0x0201 -> org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS;
            case 0x0202 -> org.lwjgl.vulkan.VK10.VK_COMPARE_OP_EQUAL;
            case 0x0203 -> org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
            case 0x0204 -> org.lwjgl.vulkan.VK10.VK_COMPARE_OP_GREATER;
            case 0x0205 -> org.lwjgl.vulkan.VK10.VK_COMPARE_OP_NOT_EQUAL;
            case 0x0206 -> org.lwjgl.vulkan.VK10.VK_COMPARE_OP_GREATER_OR_EQUAL;
            case 0x0207 -> org.lwjgl.vulkan.VK10.VK_COMPARE_OP_ALWAYS;
            default -> org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
        };
    }

    static int stencilOp(int gl) {
        return switch (gl) {
            case 0 -> org.lwjgl.vulkan.VK10.VK_STENCIL_OP_ZERO;
            case 0x1E01 -> org.lwjgl.vulkan.VK10.VK_STENCIL_OP_REPLACE;
            case 0x1E02 -> org.lwjgl.vulkan.VK10.VK_STENCIL_OP_INCREMENT_AND_CLAMP;
            case 0x1E03 -> org.lwjgl.vulkan.VK10.VK_STENCIL_OP_DECREMENT_AND_CLAMP;
            case 0x1E04 -> org.lwjgl.vulkan.VK10.VK_STENCIL_OP_INVERT;
            case 0x8507 -> org.lwjgl.vulkan.VK10.VK_STENCIL_OP_INCREMENT_AND_WRAP;
            case 0x8508 -> org.lwjgl.vulkan.VK10.VK_STENCIL_OP_DECREMENT_AND_WRAP;
            default -> org.lwjgl.vulkan.VK10.VK_STENCIL_OP_KEEP;
        };
    }

    static int logicOpVk(int gl) {
        return switch (gl) {
            case 0x1500 -> org.lwjgl.vulkan.VK10.VK_LOGIC_OP_CLEAR;
            case 0x1501 -> org.lwjgl.vulkan.VK10.VK_LOGIC_OP_AND;
            case 0x1502 -> org.lwjgl.vulkan.VK10.VK_LOGIC_OP_AND_REVERSE;
            case 0x1504 -> org.lwjgl.vulkan.VK10.VK_LOGIC_OP_AND_INVERTED;
            case 0x1505 -> org.lwjgl.vulkan.VK10.VK_LOGIC_OP_NO_OP;
            case 0x1506 -> org.lwjgl.vulkan.VK10.VK_LOGIC_OP_XOR;
            case 0x1507 -> org.lwjgl.vulkan.VK10.VK_LOGIC_OP_OR;
            case 0x1508 -> org.lwjgl.vulkan.VK10.VK_LOGIC_OP_NOR;
            case 0x1509 -> org.lwjgl.vulkan.VK10.VK_LOGIC_OP_EQUIVALENT;
            case 0x150A -> org.lwjgl.vulkan.VK10.VK_LOGIC_OP_INVERT;
            case 0x150B -> org.lwjgl.vulkan.VK10.VK_LOGIC_OP_OR_REVERSE;
            case 0x150C -> org.lwjgl.vulkan.VK10.VK_LOGIC_OP_COPY_INVERTED;
            case 0x150D -> org.lwjgl.vulkan.VK10.VK_LOGIC_OP_OR_INVERTED;
            case 0x150E -> org.lwjgl.vulkan.VK10.VK_LOGIC_OP_NAND;
            case 0x150F -> org.lwjgl.vulkan.VK10.VK_LOGIC_OP_SET;
            default -> org.lwjgl.vulkan.VK10.VK_LOGIC_OP_COPY;
        };
    }

    static long textureKey(int unit, int target) {
        return ((long) unit << 32) | (target & 0xFFFFFFFFL);
    }

    // -- lifecycle / context answers ----------------------------------------------

    public void init() {
    }

    public void shutdown() {
        if (this.sink != null) {
            for (DirectGlRecords.Buffer buffer : this.buffers.values()) {
                if (buffer.sinkCreated) {
                    this.sink.bufferOps().destroyBuffer(buffer.sinkId);
                }
            }
            for (DirectGlRecords.Program program : this.programs.values()) {
                if (program.sinkHandle != 0L) {
                    this.sink.shaderOps().destroyProgram(program.sinkHandle);
                }
            }
        }
    }

    public boolean isAvailable() {
        return true;
    }

    public String getName() {
        return "Celtium DirectGl (Vulkan 1.3)";
    }

    public boolean hasContext() {
        return true;
    }

    public boolean isCurrent() {
        return true;
    }

    public int getMinGLSLVersion() {
        return 330;
    }

    public void flush() {
    }

    public void finish() {
    }

    public long getContextHandle() {
        return 1L;
    }

    // -- fixed-function state setters ------------------------------------------------

    public void enable(int cap) {
        this.enabledCaps.add(cap);
    }

    public void disable(int cap) {
        this.enabledCaps.remove(cap);
    }

    public void enablei(int cap, int index) {
        this.enabledCapsIndexed.computeIfAbsent(cap, key -> new HashSet<>()).add(index);
    }

    public void disablei(int cap, int index) {
        Set<Integer> set = this.enabledCapsIndexed.get(cap);
        if (set != null) {
            set.remove(index);
        }
    }

    public boolean isEnabledIndexed(int cap, int index) {
        Set<Integer> set = this.enabledCapsIndexed.get(cap);
        return set != null && set.contains(index);
    }

    public void blendFunc(int sfactor, int dfactor) {
        this.blendSrcRgb = sfactor;
        this.blendDstRgb = dfactor;
        this.blendSrcAlpha = sfactor;
        this.blendDstAlpha = dfactor;
    }

    public void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        this.blendSrcRgb = srcRGB;
        this.blendDstRgb = dstRGB;
        this.blendSrcAlpha = srcAlpha;
        this.blendDstAlpha = dstAlpha;
    }

    public void blendFuncSeparatei(int buf, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        if (buf == 0) {
            blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
        }
    }

    public void blendEquation(int mode) {
        this.blendEquationRgb = mode;
        this.blendEquationAlpha = mode;
    }

    public void blendEquationSeparate(int modeRGB, int modeAlpha) {
        this.blendEquationRgb = modeRGB;
        this.blendEquationAlpha = modeAlpha;
    }

    public void blendColor(float red, float green, float blue, float alpha) {
        this.blendColor[0] = red;
        this.blendColor[1] = green;
        this.blendColor[2] = blue;
        this.blendColor[3] = alpha;
    }

    public void depthFunc(int func) {
        this.depthFunc = func;
    }

    public void depthMask(boolean flag) {
        this.depthMask = flag;
    }

    public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        this.colorMask[0] = red;
        this.colorMask[1] = green;
        this.colorMask[2] = blue;
        this.colorMask[3] = alpha;
    }

    public void cullFace(int mode) {
        this.cullFaceMode = mode;
    }

    public void frontFace(int mode) {
        this.frontFaceMode = mode;
    }

    public void polygonMode(int face, int mode) {
        this.polygonMode = mode;
    }

    public void polygonOffset(float factor, float units) {
        this.polygonOffsetFactor = factor;
        this.polygonOffsetUnits = units;
    }

    public void stencilFunc(int func, int ref, int mask) {
        this.stencilFunc = func;
        this.stencilRef = ref;
        this.stencilFuncMask = mask;
    }

    public void stencilFuncSeparate(int face, int func, int ref, int mask) {
        stencilFunc(func, ref, mask);
    }

    public void stencilOp(int sfail, int dpfail, int dppass) {
        this.stencilFail = sfail;
        this.stencilDepthFail = dpfail;
        this.stencilPass = dppass;
    }

    public void stencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        stencilOp(sfail, dpfail, dppass);
    }

    public void stencilMask(int mask) {
        this.stencilWriteMask = mask;
    }

    public void stencilMaskSeparate(int face, int mask) {
        stencilMask(mask);
    }

    public void viewport(int x, int y, int width, int height) {
        this.viewport[0] = x;
        this.viewport[1] = y;
        this.viewport[2] = width;
        this.viewport[3] = height;
    }

    public void depthRange(double nearVal, double farVal) {
        this.depthRange[0] = nearVal;
        this.depthRange[1] = farVal;
    }

    public void scissor(int x, int y, int width, int height) {
        this.scissor[0] = x;
        this.scissor[1] = y;
        this.scissor[2] = width;
        this.scissor[3] = height;
    }

    public void clearColor(float red, float green, float blue, float alpha) {
        this.clearColor[0] = red;
        this.clearColor[1] = green;
        this.clearColor[2] = blue;
        this.clearColor[3] = alpha;
    }

    public void clearDepth(double depth) {
        this.clearDepth = depth;
    }

    public void clearStencil(int s) {
        this.clearStencil = s;
    }

    public void clear(int mask) {
        // No sink op exists for clears (see the class doc): the main target's clear is Lane 2's,
        // and a mod framebuffer's clear is a recorded limitation of this stage.
    }

    public void lineWidth(float width) {
        this.lineWidth = width;
    }

    public void pointSize(float size) {
        this.pointSize = size;
    }

    public void logicOp(int opcode) {
        this.logicOp = opcode;
    }

    public void hint(int target, int mode) {
    }

    public void pointParameterf(int pname, float param) {
    }

    public void pointParameteri(int pname, int param) {
    }

    public void drawBuffer(int mode) {
        this.drawBufferMode = mode;
        DirectGlRecords.Framebuffer fbo = currentFramebuffer(GL_FRAMEBUFFER);
        if (fbo != null) {
            fbo.drawBuffers.clear();
            fbo.drawBuffers.add(mode);
        }
    }

    public void drawBuffers(int buffer) {
        drawBuffer(buffer);
    }

    public void drawBuffers(IntBuffer bufs) {
        DirectGlRecords.Framebuffer fbo = currentFramebuffer(GL_FRAMEBUFFER);
        if (fbo != null) {
            fbo.drawBuffers.clear();
            IntBuffer view = bufs.duplicate();
            while (view.hasRemaining()) {
                fbo.drawBuffers.add(view.get());
            }
        }
        if (bufs.hasRemaining()) {
            this.drawBufferMode = bufs.get(bufs.position());
        }
    }

    public void drawBuffers(int[] bufs) {
        DirectGlRecords.Framebuffer fbo = currentFramebuffer(GL_FRAMEBUFFER);
        if (fbo != null) {
            fbo.drawBuffers.clear();
            for (int buf : bufs) {
                fbo.drawBuffers.add(buf);
            }
        }
        if (bufs.length > 0) {
            this.drawBufferMode = bufs[0];
        }
    }

    public void readBuffer(int mode) {
        this.readBufferMode = mode;
    }

    public void pixelStorei(int pname, int param) {
        this.pixelStore.put(pname, param);
    }

    DirectGlRecords.Framebuffer currentFramebuffer(int target) {
        int id = this.boundFramebuffers.getOrDefault(
                target == GL_FRAMEBUFFER ? GL_DRAW_FRAMEBUFFER : target, 0);
        if (target == GL_FRAMEBUFFER) {
            id = this.boundFramebuffers.getOrDefault(GL_DRAW_FRAMEBUFFER,
                    this.boundFramebuffers.getOrDefault(GL_FRAMEBUFFER, 0));
        }
        return id == 0 ? null : this.framebuffers.get(id);
    }

    int boundFramebufferId(int target) {
        if (target == GL_FRAMEBUFFER) {
            return this.boundFramebuffers.getOrDefault(GL_DRAW_FRAMEBUFFER,
                    this.boundFramebuffers.getOrDefault(GL_FRAMEBUFFER, 0));
        }
        return this.boundFramebuffers.getOrDefault(target, 0);
    }

    // -- generic state queries ---------------------------------------------------------

    public int getInteger(int pname) {
        return switch (pname) {
            case GL_MAX_TEXTURE_SIZE -> 16384;
            case GL_MAX_TEXTURE_IMAGE_UNITS -> 32;
            case GL_MAX_VERTEX_ATTRIBS -> 16;
            case 0x0D32 -> this.viewport[2]; // GL_MAX_VIEWPORT_DIMS handled by the buffer form
            default -> 0;
        };
    }

    public void getInteger(int pname, IntBuffer params) {
        if (pname == GL_VIEWPORT) {
            for (int i = 0; i < 4 && params.hasRemaining(); i++) {
                params.put(params.position() + i, this.viewport[i]);
            }
            return;
        }
        if (params.hasRemaining()) {
            params.put(params.position(), getInteger(pname));
        }
    }

    public int getIntegerIndexed(int pname, int index) {
        return getInteger(pname);
    }

    public long getInteger64Indexed(int pname, int index) {
        return getInteger(pname);
    }

    public float getFloat(int pname) {
        return 0.0f;
    }

    public void getFloat(int pname, FloatBuffer params) {
        if (params.hasRemaining()) {
            params.put(params.position(), getFloat(pname));
        }
    }

    public boolean getBoolean(int pname) {
        return this.enabledCaps.contains(pname);
    }

    public void getBoolean(int pname, ByteBuffer params) {
        if (params.hasRemaining()) {
            params.put(params.position(), (byte) (getBoolean(pname) ? 1 : 0));
        }
    }

    public String getString(int pname) {
        return switch (pname) {
            case GL_VENDOR -> "Celtium";
            case GL_RENDERER -> "Celtium Vulkan (direct-GL converter)";
            case GL_VERSION -> "4.6 (Compatibility Profile) Mesa-compatible";
            case GL_SHADING_LANGUAGE_VERSION -> "4.60";
            case GL_EXTENSIONS -> "";
            default -> "";
        };
    }

    public String getStringi(int name, int index) {
        return "";
    }

    public int getError() {
        return GL_NO_ERROR;
    }

    // -- sync objects ---------------------------------------------------------------------

    public long fenceSync(int condition, int flags) {
        return this.nextSync++;
    }

    public int clientWaitSync(long sync, int flags, long timeout) {
        // The converter's submissions are frame-ordered on one queue; a fence a mod waits on is
        // reported signaled rather than stalling the render thread on a proxy object.
        return GL_ALREADY_SIGNALED;
    }

    public void deleteSync(long sync) {
    }

    public void waitSync(long sync, int flags, long timeout) {
    }

    public void memoryBarrier(int barriers) {
        // Ordering between converter draws is submission order on the frame's one command
        // buffer; barriers with image/buffer targets GLSM expresses are recorded by the sink's
        // own transitions.
    }

    // -- textures ---------------------------------------------------------------------------

    private static final int GL_RGBA = 0x1908;
    private static final int GL_BGRA = 0x80E1;
    private static final int GL_RGB = 0x1907;
    private static final int GL_BGR = 0x80E0;
    private static final int GL_RED = 0x1903;
    private static final int GL_RG = 0x8227;
    private static final int GL_LUMINANCE = 0x1909;
    private static final int GL_LUMINANCE_ALPHA = 0x190A;
    private static final int GL_ALPHA = 0x1906;
    private static final int GL_UNSIGNED_INT_8_8_8_8_REV = 0x8367;
    private static final int GL_UNSIGNED_INT_8_8_8_8 = 0x8035;

    public int genTextures() {
        int id = this.nextTexture++;
        this.textures.put(id, new DirectGlRecords.Texture());
        return id;
    }

    public void genTextures(IntBuffer textures) {
        int position = textures.position();
        int remaining = textures.remaining();
        for (int i = 0; i < remaining; i++) {
            textures.put(position + i, genTextures());
        }
    }

    public int createTextures(int target) {
        int id = genTextures();
        this.textures.get(id).target = target;
        return id;
    }

    public void deleteTextures(int texture) {
        DirectGlRecords.Texture rec = this.textures.remove(texture);
        if (rec != null && rec.sinkCreated && this.sink != null) {
            this.sink.textureOps().destroyTexture(rec.sinkId);
        }
        this.boundTextures.values().removeIf(id -> id == texture);
    }

    public boolean isTexture(int texture) {
        return this.textures.containsKey(texture);
    }

    public void bindTexture(int target, int texture) {
        this.boundTextures.put(textureKey(this.activeTextureUnit, target), texture);
        if (texture != 0) {
            DirectGlRecords.Texture rec = this.textures.get(texture);
            if (rec != null && rec.target == 0) {
                rec.target = target;
            }
        }
    }

    public void bindTextureUnit(int unit, int texture) {
        this.boundTextures.put(textureKey(unit, GL_TEXTURE_2D), texture);
    }

    public void activeTexture(int texture) {
        this.activeTextureUnit = Math.max(0, texture - GL_TEXTURE0);
    }

    private DirectGlRecords.Texture boundTexture(int target) {
        Integer id = this.boundTextures.get(textureKey(this.activeTextureUnit, target));
        return id == null || id == 0 ? null : this.textures.get(id);
    }

    /** Converts one level's pixels to the RGBA8 shape the sink's texture table carries. */
    ByteBuffer toRgba(int width, int height, int format, int type, ByteBuffer pixels) {
        if (pixels == null) {
            return null;
        }
        ByteBuffer src = pixels.duplicate();
        int count = width * height;
        ByteBuffer out = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder());
        if (format == GL_RGBA && type == GL_UNSIGNED_BYTE) {
            int limit = Math.min(src.remaining(), count * 4);
            ByteBuffer slice = src.slice();
            slice.limit(limit);
            out.put(slice);
        } else if (format == GL_BGRA && type == GL_UNSIGNED_INT_8_8_8_8_REV) {
            // Packed BGRA in memory order B,G,R,A per pixel already equals RGBA byte order
            // on a little-endian read of the packed int; the bytes in the buffer are B,G,R,A.
            for (int i = 0; i < count && src.remaining() >= 4; i++) {
                byte b = src.get(), g = src.get(), r = src.get(), a = src.get();
                out.put(r).put(g).put(b).put(a);
            }
        } else if (format == GL_BGRA && type == GL_UNSIGNED_BYTE) {
            for (int i = 0; i < count && src.remaining() >= 4; i++) {
                byte b = src.get(), g = src.get(), r = src.get(), a = src.get();
                out.put(r).put(g).put(b).put(a);
            }
        } else if ((format == GL_RGB || format == GL_BGR) && type == GL_UNSIGNED_BYTE) {
            boolean bgr = format == GL_BGR;
            for (int i = 0; i < count && src.remaining() >= 3; i++) {
                byte c0 = src.get(), c1 = src.get(), c2 = src.get();
                if (bgr) {
                    out.put(c2).put(c1).put(c0);
                } else {
                    out.put(c0).put(c1).put(c2);
                }
                out.put((byte) 0xFF);
            }
        } else if (format == GL_RED && type == GL_UNSIGNED_BYTE) {
            for (int i = 0; i < count && src.hasRemaining(); i++) {
                byte r = src.get();
                out.put(r).put((byte) 0).put((byte) 0).put((byte) 0xFF);
            }
        } else if (format == GL_LUMINANCE && type == GL_UNSIGNED_BYTE) {
            for (int i = 0; i < count && src.hasRemaining(); i++) {
                byte l = src.get();
                out.put(l).put(l).put(l).put((byte) 0xFF);
            }
        } else if (format == GL_LUMINANCE_ALPHA && type == GL_UNSIGNED_BYTE) {
            for (int i = 0; i < count && src.remaining() >= 2; i++) {
                byte l = src.get(), a = src.get();
                out.put(l).put(l).put(l).put(a);
            }
        } else if (format == GL_ALPHA && type == GL_UNSIGNED_BYTE) {
            for (int i = 0; i < count && src.hasRemaining(); i++) {
                byte a = src.get();
                out.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put(a);
            }
        } else {
            // An unconverted shape: pass the bytes through when the size already matches
            // RGBA8; otherwise the level's content is dropped (its dimensions still record).
            if (src.remaining() < count * 4) {
                return null;
            }
            ByteBuffer slice = src.slice();
            slice.limit(count * 4);
            out.put(slice);
        }
        out.flip();
        return out;
    }

    private void texImage(int textureId, DirectGlRecords.Texture rec, int level, int internalFormat,
                          int width, int height, int format, int type, ByteBuffer pixels) {
        if (rec == null) {
            return;
        }
        if (level == 0) {
            rec.width = width;
            rec.height = height;
            rec.internalFormat = internalFormat;
        }
        if (this.sink == null) {
            return;
        }
        if (!rec.sinkCreated && level == 0) {
            rec.sinkId = this.sink.textureOps().createTexture2D(width, height, internalFormat, 1);
            rec.sinkCreated = true;
        }
        if (rec.sinkCreated && pixels != null) {
            ByteBuffer rgba = toRgba(width, height, format, type, pixels);
            if (rgba != null) {
                this.sink.textureOps().writeTexture(rec.sinkId, level, 0, 0, width, height,
                        MemorySegment.ofBuffer(rgba));
            }
        }
    }

    private void texSubImage(int textureId, DirectGlRecords.Texture rec, int level, int x, int y,
                             int width, int height, int format, int type, ByteBuffer pixels) {
        if (rec == null || this.sink == null || !rec.sinkCreated || pixels == null) {
            return;
        }
        ByteBuffer rgba = toRgba(width, height, format, type, pixels);
        if (rgba != null) {
            this.sink.textureOps().writeTexture(rec.sinkId, level, x, y, width, height,
                    MemorySegment.ofBuffer(rgba));
        }
    }

    private static ByteBuffer bufferOf(IntBuffer buffer) {
        if (buffer == null) {
            return null;
        }
        IntBuffer view = buffer.duplicate();
        ByteBuffer out = ByteBuffer.allocateDirect(view.remaining() * 4).order(ByteOrder.nativeOrder());
        out.asIntBuffer().put(view);
        return out;
    }

    private static ByteBuffer bufferOf(FloatBuffer buffer) {
        if (buffer == null) {
            return null;
        }
        FloatBuffer view = buffer.duplicate();
        ByteBuffer out = ByteBuffer.allocateDirect(view.remaining() * 4).order(ByteOrder.nativeOrder());
        out.asFloatBuffer().put(view);
        return out;
    }

    private static ByteBuffer bufferOf(DoubleBuffer buffer) {
        if (buffer == null) {
            return null;
        }
        DoubleBuffer view = buffer.duplicate();
        ByteBuffer out = ByteBuffer.allocateDirect(view.remaining() * 8).order(ByteOrder.nativeOrder());
        out.asDoubleBuffer().put(view);
        return out;
    }

    private static ByteBuffer bufferOf(ShortBuffer buffer) {
        if (buffer == null) {
            return null;
        }
        ShortBuffer view = buffer.duplicate();
        ByteBuffer out = ByteBuffer.allocateDirect(view.remaining() * 2).order(ByteOrder.nativeOrder());
        out.asShortBuffer().put(view);
        return out;
    }

    public void texImage2D(int target, int level, int internalFormat, int width, int height,
                           int border, int format, int type, ByteBuffer pixels) {
        DirectGlRecords.Texture rec = boundTexture(target);
        texImage(0, rec, level, internalFormat, width, height, format, type, pixels);
    }

    public void texImage2D(int target, int level, int internalFormat, int width, int height,
                           int border, int format, int type, IntBuffer pixels) {
        texImage2D(target, level, internalFormat, width, height, border, format, type, bufferOf(pixels));
    }

    public void texImage2D(int target, int level, int internalFormat, int width, int height,
                           int border, int format, int type, FloatBuffer pixels) {
        texImage2D(target, level, internalFormat, width, height, border, format, type, bufferOf(pixels));
    }

    public void texImage2D(int target, int level, int internalFormat, int width, int height,
                           int border, int format, int type, DoubleBuffer pixels) {
        texImage2D(target, level, internalFormat, width, height, border, format, type, bufferOf(pixels));
    }

    public void texImage2D(int target, int level, int internalFormat, int width, int height,
                           int border, int format, int type, long pixelBufferOffset) {
        DirectGlRecords.Texture rec = boundTexture(target);
        ByteBuffer pixels = readBoundPixelBuffer(pixelBufferOffset, width, height, format, type);
        texImage(0, rec, level, internalFormat, width, height, format, type, pixels);
    }

    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height,
                              int format, int type, ByteBuffer pixels) {
        texSubImage(0, boundTexture(target), level, xoffset, yoffset, width, height, format, type, pixels);
    }

    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height,
                              int format, int type, IntBuffer pixels) {
        texSubImage2D(target, level, xoffset, yoffset, width, height, format, type, bufferOf(pixels));
    }

    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height,
                              int format, int type, long pixels) {
        ByteBuffer data = readBoundPixelBuffer(pixels, width, height, format, type);
        texSubImage2D(target, level, xoffset, yoffset, width, height, format, type, data);
    }

    public void textureSubImage2D(int texture, int level, int xoffset, int yoffset, int width,
                                  int height, int format, int type, ByteBuffer pixels) {
        texSubImage(texture, this.textures.get(texture), level, xoffset, yoffset, width, height,
                format, type, pixels);
    }

    public void textureSubImage2D(int texture, int level, int xoffset, int yoffset, int width,
                                  int height, int format, int type, IntBuffer pixels) {
        textureSubImage2D(texture, level, xoffset, yoffset, width, height, format, type, bufferOf(pixels));
    }

    public void textureImage2DEXT(int texture, int target, int level, int internalformat, int width,
                                  int height, int border, int format, int type, ByteBuffer pixels) {
        texImage(texture, this.textures.get(texture), level, internalformat, width, height, format, type, pixels);
    }

    public void textureImage2DEXT(int texture, int target, int level, int internalformat, int width,
                                  int height, int border, int format, int type, IntBuffer pixels) {
        textureImage2DEXT(texture, target, level, internalformat, width, height, border, format, type,
                bufferOf(pixels));
    }

    /** Reads pixels out of the bound GL_PIXEL_UNPACK_BUFFER's shadow, when one is bound. */
    private ByteBuffer readBoundPixelBuffer(long offset, int width, int height, int format, int type) {
        Integer bufferId = this.boundBuffers.get(0x88EC); // GL_PIXEL_UNPACK_BUFFER
        if (bufferId == null) {
            return null;
        }
        DirectGlRecords.Buffer buffer = this.buffers.get(bufferId);
        if (buffer == null || buffer.shadow == null) {
            return null;
        }
        ByteBuffer view = buffer.shadow.duplicate();
        if (offset >= view.capacity()) {
            return null;
        }
        view.position((int) offset);
        return view.slice();
    }

    public void texImage1D(int target, int level, int internalFormat, int width, int border,
                           int format, int type, ByteBuffer pixels) {
        DirectGlRecords.Texture rec = boundTexture(target);
        if (rec != null && level == 0) {
            rec.width = width;
            rec.height = 1;
            rec.internalFormat = internalFormat;
        }
    }

    public void texImage3D(int target, int level, int internalFormat, int width, int height,
                           int depth, int border, int format, int type, ByteBuffer pixels) {
        DirectGlRecords.Texture rec = boundTexture(target);
        if (rec != null && level == 0) {
            rec.width = width;
            rec.height = height;
            rec.depth = depth;
            rec.internalFormat = internalFormat;
        }
    }

    public void texImage3D(int target, int level, int internalFormat, int width, int height,
                           int depth, int border, int format, int type, IntBuffer pixels) {
        texImage3D(target, level, internalFormat, width, height, depth, border, format, type,
                bufferOf(pixels));
    }

    public void texSubImage1D(int target, int level, int xoffset, int width, int format, int type,
                              ByteBuffer pixels) {
    }

    public void texSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset,
                              int width, int height, int depth, int format, int type, ByteBuffer pixels) {
    }

    public void textureSubImage3D(int texture, int level, int xoffset, int yoffset, int zoffset,
                                  int width, int height, int depth, int format, int type,
                                  ByteBuffer pixels) {
    }

    public void texStorage1D(int target, int levels, int internalFormat, int width) {
        DirectGlRecords.Texture rec = boundTexture(target);
        if (rec != null) {
            rec.width = width;
            rec.height = 1;
            rec.internalFormat = internalFormat;
        }
    }

    public void texStorage2D(int target, int levels, int internalFormat, int width, int height) {
        DirectGlRecords.Texture rec = boundTexture(target);
        storage(rec, internalFormat, width, height, 1);
    }

    public void texStorage3D(int target, int levels, int internalFormat, int width, int height, int depth) {
        DirectGlRecords.Texture rec = boundTexture(target);
        storage(rec, internalFormat, width, height, depth);
    }

    public void textureStorage1D(int texture, int levels, int internalFormat, int width) {
        DirectGlRecords.Texture rec = this.textures.get(texture);
        if (rec != null) {
            rec.width = width;
            rec.height = 1;
            rec.internalFormat = internalFormat;
        }
    }

    public void textureStorage2D(int texture, int levels, int internalFormat, int width, int height) {
        storage(this.textures.get(texture), internalFormat, width, height, 1);
    }

    public void textureStorage3D(int texture, int levels, int internalFormat, int width, int height,
                                 int depth) {
        storage(this.textures.get(texture), internalFormat, width, height, depth);
    }

    private void storage(DirectGlRecords.Texture rec, int internalFormat, int width, int height, int depth) {
        if (rec == null) {
            return;
        }
        rec.width = width;
        rec.height = height;
        rec.depth = depth;
        rec.internalFormat = internalFormat;
        if (this.sink != null && !rec.sinkCreated) {
            rec.sinkId = this.sink.textureOps().createTexture2D(width, height, internalFormat, 1);
            rec.sinkCreated = true;
        }
    }

    public void compressedTexImage2D(int target, int level, int internalFormat, int width, int height,
                                     int border, ByteBuffer pixels) {
        DirectGlRecords.Texture rec = boundTexture(target);
        if (rec != null && level == 0) {
            rec.width = width;
            rec.height = height;
            rec.internalFormat = internalFormat;
        }
    }

    public void compressedTexImage2D(int target, int level, int internalFormat, int width, int height,
                                     int border, int imageSize, long data) {
        DirectGlRecords.Texture rec = boundTexture(target);
        if (rec != null && level == 0) {
            rec.width = width;
            rec.height = height;
            rec.internalFormat = internalFormat;
        }
    }

    public void compressedTexImage3D(int target, int level, int internalFormat, int width, int height,
                                     int depth, int border, ByteBuffer pixels) {
    }

    public void compressedTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset,
                                        int width, int height, int depth, int format, ByteBuffer pixels) {
    }

    public void compressedTextureSubImage3D(int texture, int level, int xoffset, int yoffset,
                                            int zoffset, int width, int height, int depth, int format,
                                            ByteBuffer pixels) {
    }

    public void copyTexImage1D(int target, int level, int internalFormat, int x, int y, int width,
                               int border) {
    }

    public void copyTexImage2D(int target, int level, int internalFormat, int x, int y, int width,
                               int height, int border) {
        DirectGlRecords.Texture rec = boundTexture(target);
        if (rec != null && level == 0) {
            rec.width = width;
            rec.height = height;
            rec.internalFormat = internalFormat;
        }
    }

    public void copyTexSubImage1D(int target, int level, int xoffset, int x, int y, int width) {
    }

    public void copyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y,
                                  int width, int height) {
    }

    public void copyTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset,
                                  int x, int y, int width, int height) {
    }

    public void copyTextureSubImage2D(int texture, int target, int level, int xoffset, int yoffset,
                                      int x, int y, int width, int height) {
    }

    public void copyTextureSubImage3D(int texture, int level, int xoffset, int yoffset, int zoffset,
                                      int x, int y, int width, int height) {
    }

    public void getTexImage(int target, int level, int format, int type, ByteBuffer pixels) {
        // Content the GPU holds is not read back in this stage (see the class doc); the
        // caller's buffer is zero-filled so no stale bytes leak through.
        if (pixels != null) {
            ByteBuffer view = pixels.duplicate();
            while (view.hasRemaining()) {
                view.put((byte) 0);
            }
        }
    }

    public void getTexImage(int target, int level, int format, int type, IntBuffer pixels) {
        if (pixels != null) {
            IntBuffer view = pixels.duplicate();
            while (view.hasRemaining()) {
                view.put(0);
            }
        }
    }

    public void getTexImage(int target, int level, int format, int type, long pixelBufferOffset) {
    }

    public void getTextureImage(int texture, int level, int format, int type, ByteBuffer pixels) {
        getTexImage(GL_TEXTURE_2D, level, format, type, pixels);
    }

    public void getCompressedTexImage(int target, int level, ByteBuffer pixels) {
        getTexImage(target, level, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    }

    public void getCompressedTextureImage(int texture, int level, ByteBuffer pixels) {
        getTexImage(GL_TEXTURE_2D, level, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    }

    public void texParameteri(int target, int pname, int param) {
        DirectGlRecords.Texture rec = boundTexture(target);
        if (rec != null) {
            rec.intParams.put(pname, param);
        }
    }

    public void texParameterf(int target, int pname, float param) {
        DirectGlRecords.Texture rec = boundTexture(target);
        if (rec != null) {
            rec.floatParams.put(pname, param);
        }
    }

    public void texParameteriv(int target, int pname, IntBuffer params) {
        if (params.hasRemaining()) {
            texParameteri(target, pname, params.get(params.position()));
        }
    }

    public void texParameterfv(int target, int pname, FloatBuffer params) {
        if (params.hasRemaining()) {
            texParameterf(target, pname, params.get(params.position()));
        }
    }

    public void texParameterIiv(int target, int pname, IntBuffer params) {
        texParameteriv(target, pname, params);
    }

    public void texParameterIuiv(int target, int pname, IntBuffer params) {
        texParameteriv(target, pname, params);
    }

    public void textureParameteri(int texture, int target, int pname, int param) {
        DirectGlRecords.Texture rec = this.textures.get(texture);
        if (rec != null) {
            rec.intParams.put(pname, param);
        }
    }

    public void textureParameterf(int texture, int target, int pname, float param) {
        DirectGlRecords.Texture rec = this.textures.get(texture);
        if (rec != null) {
            rec.floatParams.put(pname, param);
        }
    }

    public void textureParameteriv(int texture, int target, int pname, IntBuffer params) {
        if (params.hasRemaining()) {
            textureParameteri(texture, target, pname, params.get(params.position()));
        }
    }

    public void textureParameterfv(int texture, int target, int pname, FloatBuffer params) {
        if (params.hasRemaining()) {
            textureParameterf(texture, target, pname, params.get(params.position()));
        }
    }

    public void textureParameterIiv(int texture, int target, int pname, IntBuffer params) {
        textureParameteriv(texture, target, pname, params);
    }

    public void textureParameterIuiv(int texture, int target, int pname, IntBuffer params) {
        textureParameteriv(texture, target, pname, params);
    }

    public int getTexParameteri(int target, int pname) {
        DirectGlRecords.Texture rec = boundTexture(target);
        return rec == null ? 0 : rec.intParams.getOrDefault(pname, 0);
    }

    public float getTexParameterf(int target, int pname) {
        DirectGlRecords.Texture rec = boundTexture(target);
        return rec == null ? 0f : rec.floatParams.getOrDefault(pname, 0f);
    }

    public int getTextureParameteri(int texture, int target, int pname) {
        DirectGlRecords.Texture rec = this.textures.get(texture);
        return rec == null ? 0 : rec.intParams.getOrDefault(pname, 0);
    }

    public float getTextureParameterf(int texture, int target, int pname) {
        DirectGlRecords.Texture rec = this.textures.get(texture);
        return rec == null ? 0f : rec.floatParams.getOrDefault(pname, 0f);
    }

    public int getTexLevelParameteri(int target, int level, int pname) {
        DirectGlRecords.Texture rec = boundTexture(target);
        return levelParam(rec, level, pname);
    }

    public int getTextureLevelParameteri(int texture, int level, int pname) {
        return levelParam(this.textures.get(texture), level, pname);
    }

    private int levelParam(DirectGlRecords.Texture rec, int level, int pname) {
        if (rec == null) {
            return 0;
        }
        return switch (pname) {
            case GL_TEXTURE_WIDTH -> Math.max(1, rec.width >> level);
            case GL_TEXTURE_HEIGHT -> Math.max(1, rec.height >> level);
            case GL_TEXTURE_INTERNAL_FORMAT -> rec.internalFormat;
            default -> 0;
        };
    }

    public void generateMipmap(int target) {
        // The sink's registry textures carry level 0; mip generation for converter textures is
        // served by the registry's own upload path when it materializes the texture.
    }

    public void generateTextureMipmap(int texture) {
    }

    public void clearTexImage(int texture, int level, int format, int type) {
    }

    public void clearTexSubImage(int texture, int level, int xoffset, int yoffset, int zoffset,
                                 int width, int height, int depth, int format, int type,
                                 ByteBuffer data) {
    }

    public void bindImageTexture(int unit, int texture, int level, boolean layered, int layer,
                                 int access, int format) {
    }

    public void copyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY,
                                 int srcZ, int dstName, int dstTarget, int dstLevel, int dstX,
                                 int dstY, int dstZ, int srcWidth, int srcHeight, int srcDepth) {
    }

    // -- samplers ---------------------------------------------------------------------------------

    public int genSamplers() {
        int id = this.nextSampler++;
        this.samplers.put(id, new DirectGlRecords.Sampler());
        return id;
    }

    public void deleteSamplers(int sampler) {
        this.samplers.remove(sampler);
        this.boundSamplers.values().removeIf(id -> id == sampler);
    }

    public boolean isSampler(int sampler) {
        return this.samplers.containsKey(sampler);
    }

    public void bindSampler(int unit, int sampler) {
        if (sampler == 0) {
            this.boundSamplers.remove(unit);
        } else {
            this.boundSamplers.put(unit, sampler);
        }
    }

    public void samplerParameteri(int sampler, int pname, int param) {
        DirectGlRecords.Sampler rec = this.samplers.get(sampler);
        if (rec != null) {
            rec.intParams.put(pname, param);
        }
    }

    public void samplerParameterf(int sampler, int pname, float param) {
        DirectGlRecords.Sampler rec = this.samplers.get(sampler);
        if (rec != null) {
            rec.floatParams.put(pname, param);
        }
    }

    // -- buffers ------------------------------------------------------------------------------------

    private static final int GL_PIXEL_UNPACK_BUFFER = 0x88EC;
    private static final int GL_PIXEL_PACK_BUFFER = 0x88EB;

    public int genBuffers() {
        int id = this.nextBuffer++;
        this.buffers.put(id, new DirectGlRecords.Buffer());
        return id;
    }

    public int createBuffers() {
        return genBuffers();
    }

    public void deleteBuffers(int buffer) {
        DirectGlRecords.Buffer rec = this.buffers.remove(buffer);
        if (rec != null && rec.sinkCreated && this.sink != null) {
            this.sink.bufferOps().destroyBuffer(rec.sinkId);
        }
        this.boundBuffers.values().removeIf(id -> id == buffer);
        this.boundIndexedBuffers.values().removeIf(id -> id == buffer);
    }

    public void deleteBuffers(IntBuffer buffers) {
        IntBuffer view = buffers.duplicate();
        while (view.hasRemaining()) {
            deleteBuffers(view.get());
        }
    }

    public boolean isBuffer(int buffer) {
        return this.buffers.containsKey(buffer);
    }

    public void bindBuffer(int target, int buffer) {
        if (buffer == 0) {
            this.boundBuffers.remove(target);
        } else {
            this.boundBuffers.put(target, buffer);
        }
        if (target == GL_ELEMENT_ARRAY_BUFFER) {
            currentVertexArray().elementBuffer = buffer;
        }
    }

    public void bindBufferBase(int target, int index, int buffer) {
        bindBuffer(target, buffer);
        if (buffer == 0) {
            this.boundIndexedBuffers.remove(indexedKey(target, index));
        } else {
            this.boundIndexedBuffers.put(indexedKey(target, index), buffer);
        }
    }

    public void bindBufferRange(int target, int index, int buffer, long offset, long size) {
        bindBufferBase(target, index, buffer);
    }

    public int getIndexedBufferBinding(int target, int index) {
        return this.boundIndexedBuffers.getOrDefault(indexedKey(target, index), 0);
    }

    private static long indexedKey(int target, int index) {
        return ((long) target << 32) | (index & 0xFFFFFFFFL);
    }

    private DirectGlRecords.Buffer boundBuffer(int target) {
        Integer id = this.boundBuffers.get(target);
        return id == null || id == 0 ? null : this.buffers.get(id);
    }

    private void bufferDataImpl(DirectGlRecords.Buffer rec, ByteBuffer data, long size, int usage) {
        if (rec == null) {
            return;
        }
        rec.usage = usage;
        long capacity = data != null ? data.remaining() : Math.max(0L, size);
        ByteBuffer shadow = ByteBuffer.allocateDirect((int) Math.max(1L, capacity))
                .order(ByteOrder.nativeOrder());
        if (data != null) {
            shadow.put(data.duplicate());
        }
        shadow.flip();
        rec.shadow = shadow;
        syncBufferToSink(rec);
    }

    private void syncBufferToSink(DirectGlRecords.Buffer rec) {
        if (this.sink == null || rec.shadow == null) {
            return;
        }
        if (!rec.sinkCreated) {
            rec.sinkId = this.sink.bufferOps().createBuffer(rec.shadow.capacity(), 0);
            rec.sinkCreated = true;
        }
        ByteBuffer view = rec.shadow.duplicate();
        view.position(0);
        this.sink.bufferOps().writeBuffer(rec.sinkId, 0L, MemorySegment.ofBuffer(view));
    }

    private void bufferSubDataImpl(DirectGlRecords.Buffer rec, long offset, ByteBuffer data) {
        if (rec == null || data == null) {
            return;
        }
        if (rec.shadow == null) {
            bufferDataImpl(rec, null, offset + data.remaining(), 0x88E4);
        }
        ByteBuffer view = rec.shadow.duplicate();
        if (offset + data.remaining() > view.capacity()) {
            // Grow: GL would error; the converter grows the shadow and the sink buffer instead,
            // which keeps a sloppy mod rendering rather than corrupting memory.
            ByteBuffer grown = ByteBuffer.allocateDirect((int) (offset + data.remaining()))
                    .order(ByteOrder.nativeOrder());
            ByteBuffer old = rec.shadow.duplicate();
            old.position(0);
            grown.put(old);
            rec.shadow = grown;
            rec.sinkCreated = false;
            view = grown.duplicate();
        }
        view.position((int) offset);
        view.put(data.duplicate());
        if (this.sink != null) {
            if (!rec.sinkCreated) {
                syncBufferToSink(rec);
            } else {
                ByteBuffer slice = rec.shadow.duplicate();
                slice.position((int) offset);
                slice.limit((int) offset + data.remaining());
                this.sink.bufferOps().writeBuffer(rec.sinkId, offset,
                        MemorySegment.ofBuffer(slice.slice()));
            }
        }
    }

    public void bufferData(int target, long size, int usage) {
        bufferDataImpl(boundBuffer(target), null, size, usage);
    }

    public void bufferData(int target, ByteBuffer data, int usage) {
        bufferDataImpl(boundBuffer(target), data, data == null ? 0 : data.remaining(), usage);
    }

    public void bufferData(int target, FloatBuffer data, int usage) {
        bufferData(target, bufferOf(data), usage);
    }

    public void bufferData(int target, ShortBuffer data, int usage) {
        bufferData(target, bufferOf(data), usage);
    }

    public void bufferData(int target, IntBuffer data, int usage) {
        bufferData(target, bufferOf(data), usage);
    }

    public void bufferData(int target, DoubleBuffer data, int usage) {
        bufferData(target, bufferOf(data), usage);
    }

    public void bufferData(int target, int[] data, int usage) {
        bufferData(target, bufferOf(IntBuffer.wrap(data)), usage);
    }

    public void bufferData(int target, float[] data, int usage) {
        bufferData(target, bufferOf(FloatBuffer.wrap(data)), usage);
    }

    public void namedBufferData(int buffer, ByteBuffer data, int usage) {
        bufferDataImpl(this.buffers.get(buffer), data, data == null ? 0 : data.remaining(), usage);
    }

    public void namedBufferData(int buffer, FloatBuffer data, int usage) {
        namedBufferData(buffer, bufferOf(data), usage);
    }

    public void namedBufferData(int buffer, long size, int usage) {
        bufferDataImpl(this.buffers.get(buffer), null, size, usage);
    }

    public void bufferSubData(int target, long offset, ByteBuffer data) {
        bufferSubDataImpl(boundBuffer(target), offset, data);
    }

    public void bufferSubData(int target, long offset, ShortBuffer data) {
        bufferSubDataImpl(boundBuffer(target), offset, bufferOf(data));
    }

    public void bufferSubData(int target, long offset, IntBuffer data) {
        bufferSubDataImpl(boundBuffer(target), offset, bufferOf(data));
    }

    public void bufferSubData(int target, long offset, FloatBuffer data) {
        bufferSubDataImpl(boundBuffer(target), offset, bufferOf(data));
    }

    public void bufferSubData(int target, long offset, DoubleBuffer data) {
        bufferSubDataImpl(boundBuffer(target), offset, bufferOf(data));
    }

    public void namedBufferSubData(int buffer, long offset, ByteBuffer data) {
        bufferSubDataImpl(this.buffers.get(buffer), offset, data);
    }

    public void bufferStorage(int target, ByteBuffer data, int flags) {
        DirectGlRecords.Buffer rec = boundBuffer(target);
        bufferDataImpl(rec, data, data == null ? 0 : data.remaining(), 0x88E4);
        if (rec != null) {
            rec.immutable = true;
        }
    }

    public void bufferStorage(int target, long size, int flags) {
        DirectGlRecords.Buffer rec = boundBuffer(target);
        bufferDataImpl(rec, null, size, 0x88E4);
        if (rec != null) {
            rec.immutable = true;
        }
    }

    public ByteBuffer mapBuffer(int target, int access) {
        DirectGlRecords.Buffer rec = boundBuffer(target);
        if (rec == null || rec.shadow == null) {
            return null;
        }
        ByteBuffer view = rec.shadow.duplicate();
        view.position(0);
        // duplicate() resets the byte order to BIG_ENDIAN on current JDKs; a mapped view must
        // hand the caller the shadow's (native) order or typed writes land byte-swapped.
        view.order(rec.shadow.order());
        return view;
    }

    public ByteBuffer mapBuffer(int target, int access, long length, ByteBuffer old_buffer) {
        return mapBuffer(target, access);
    }

    public ByteBuffer mapBufferRange(int target, long offset, long length, int access) {
        DirectGlRecords.Buffer rec = boundBuffer(target);
        if (rec == null || rec.shadow == null) {
            return null;
        }
        ByteBuffer view = rec.shadow.duplicate();
        if (offset + length > view.capacity()) {
            return null;
        }
        view.position((int) offset);
        view.limit((int) (offset + length));
        ByteBuffer slice = view.slice();
        slice.order(rec.shadow.order());
        return slice;
    }

    public long mapBufferRangeAddress(int target, long offset, long length, int access) {
        ByteBuffer mapped = mapBufferRange(target, offset, length, access);
        if (mapped == null) {
            return 0L;
        }
        return MemorySegment.ofBuffer(mapped).address();
    }

    public boolean unmapBuffer(int target) {
        DirectGlRecords.Buffer rec = boundBuffer(target);
        if (rec == null) {
            return false;
        }
        // The shadow is the mapped memory itself, so unmapping is the flush to the sink.
        if (rec.sinkCreated && this.sink != null && rec.shadow != null) {
            ByteBuffer view = rec.shadow.duplicate();
            view.position(0);
            this.sink.bufferOps().writeBuffer(rec.sinkId, 0L, MemorySegment.ofBuffer(view));
        } else {
            syncBufferToSink(rec);
        }
        return true;
    }

    public void flushMappedBufferRange(int target, long offset, long length) {
        DirectGlRecords.Buffer rec = boundBuffer(target);
        if (rec == null || this.sink == null || !rec.sinkCreated || rec.shadow == null) {
            return;
        }
        ByteBuffer view = rec.shadow.duplicate();
        view.position((int) offset);
        view.limit((int) Math.min(view.capacity(), offset + length));
        this.sink.bufferOps().writeBuffer(rec.sinkId, offset, MemorySegment.ofBuffer(view.slice()));
    }

    public void getBufferSubData(int target, long offset, ByteBuffer data) {
        DirectGlRecords.Buffer rec = boundBuffer(target);
        if (rec == null || rec.shadow == null || data == null) {
            return;
        }
        ByteBuffer view = rec.shadow.duplicate();
        view.position((int) Math.min(offset, view.capacity()));
        view.limit((int) Math.min(view.capacity(), offset + data.remaining()));
        data.put(view.slice());
    }

    public void getBufferSubData(int target, long offset, ShortBuffer data) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(data.remaining() * 2).order(ByteOrder.nativeOrder());
        getBufferSubData(target, offset, bytes);
        bytes.flip();
        data.put(bytes.asShortBuffer());
    }

    public void getBufferSubData(int target, long offset, IntBuffer data) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(data.remaining() * 4).order(ByteOrder.nativeOrder());
        getBufferSubData(target, offset, bytes);
        bytes.flip();
        data.put(bytes.asIntBuffer());
    }

    public void getBufferSubData(int target, long offset, FloatBuffer data) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(data.remaining() * 4).order(ByteOrder.nativeOrder());
        getBufferSubData(target, offset, bytes);
        bytes.flip();
        data.put(bytes.asFloatBuffer());
    }

    public void getBufferSubData(int target, long offset, DoubleBuffer data) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(data.remaining() * 8).order(ByteOrder.nativeOrder());
        getBufferSubData(target, offset, bytes);
        bytes.flip();
        data.put(bytes.asDoubleBuffer());
    }

    public int getBufferParameteri(int target, int pname) {
        DirectGlRecords.Buffer rec = boundBuffer(target);
        if (rec == null) {
            return 0;
        }
        return switch (pname) {
            case GL_BUFFER_SIZE -> rec.shadow == null ? 0 : rec.shadow.capacity();
            case GL_BUFFER_USAGE -> rec.usage;
            default -> 0;
        };
    }

    public void copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset,
                                  long size) {
        DirectGlRecords.Buffer src = boundBuffer(readTarget);
        DirectGlRecords.Buffer dst = boundBuffer(writeTarget);
        if (src == null || dst == null || src.shadow == null) {
            return;
        }
        ByteBuffer view = src.shadow.duplicate();
        view.position((int) readOffset);
        view.limit((int) Math.min(view.capacity(), readOffset + size));
        bufferSubDataImpl(dst, writeOffset, view.slice());
    }

    public void clearBufferData(int target, int internalformat, int format, int type, ByteBuffer data) {
        DirectGlRecords.Buffer rec = boundBuffer(target);
        if (rec != null && rec.shadow != null) {
            ByteBuffer view = rec.shadow.duplicate();
            while (view.hasRemaining()) {
                view.put((byte) 0);
            }
            syncBufferToSink(rec);
        }
    }

    public void clearBufferSubData(int target, int internalFormat, long offset, long size, int format,
                                   int type, ByteBuffer data) {
        DirectGlRecords.Buffer rec = boundBuffer(target);
        if (rec != null && rec.shadow != null) {
            ByteBuffer view = rec.shadow.duplicate();
            view.position((int) Math.min(offset, view.capacity()));
            view.limit((int) Math.min(view.capacity(), offset + size));
            while (view.hasRemaining()) {
                view.put((byte) 0);
            }
            if (rec.sinkCreated && this.sink != null) {
                ByteBuffer slice = rec.shadow.duplicate();
                slice.position((int) offset);
                slice.limit((int) Math.min(slice.capacity(), offset + size));
                this.sink.bufferOps().writeBuffer(rec.sinkId, offset,
                        MemorySegment.ofBuffer(slice.slice()));
            }
        }
    }

    // -- framebuffers / renderbuffers ------------------------------------------------------------

    public int genFramebuffers() {
        int id = this.nextFramebuffer++;
        this.framebuffers.put(id, new DirectGlRecords.Framebuffer());
        return id;
    }

    public int createFramebuffers() {
        return genFramebuffers();
    }

    public void deleteFramebuffers(int framebuffer) {
        DirectGlRecords.Framebuffer rec = this.framebuffers.remove(framebuffer);
        if (rec != null && rec.sinkCreated && this.sink != null) {
            this.sink.framebufferOps().destroyFramebuffer(rec.sinkId);
        }
        this.boundFramebuffers.values().removeIf(id -> id == framebuffer);
    }

    public boolean isFramebuffer(int framebuffer) {
        return this.framebuffers.containsKey(framebuffer);
    }

    public void bindFramebuffer(int target, int framebuffer) {
        if (target == GL_FRAMEBUFFER) {
            bindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
            bindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            return;
        }
        if (framebuffer == 0) {
            this.boundFramebuffers.remove(target);
        } else {
            this.boundFramebuffers.put(target, framebuffer);
        }
    }

    private void attachTexture(int fboId, int attachment, int texture, int level) {
        DirectGlRecords.Framebuffer rec = this.framebuffers.get(fboId);
        if (rec == null) {
            return;
        }
        if (texture == 0) {
            rec.textureAttachments.remove(attachment);
        } else {
            rec.textureAttachments.put(attachment, texture);
        }
        rec.sinkCreated = false;
        updateFboExtent(rec);
    }

    private void attachRenderbuffer(int fboId, int attachment, int renderbuffer) {
        DirectGlRecords.Framebuffer rec = this.framebuffers.get(fboId);
        if (rec == null) {
            return;
        }
        if (renderbuffer == 0) {
            rec.renderbufferAttachments.remove(attachment);
        } else {
            rec.renderbufferAttachments.put(attachment, renderbuffer);
        }
        rec.sinkCreated = false;
        updateFboExtent(rec);
    }

    private void updateFboExtent(DirectGlRecords.Framebuffer rec) {
        int width = 0, height = 0;
        for (int textureId : rec.textureAttachments.values()) {
            DirectGlRecords.Texture texture = this.textures.get(textureId);
            if (texture != null) {
                width = Math.max(width, texture.width);
                height = Math.max(height, texture.height);
            }
        }
        for (int rbId : rec.renderbufferAttachments.values()) {
            DirectGlRecords.Renderbuffer rb = this.renderbuffers.get(rbId);
            if (rb != null) {
                width = Math.max(width, rb.width);
                height = Math.max(height, rb.height);
            }
        }
        rec.width = width;
        rec.height = height;
    }

    public void framebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        attachTexture(boundFramebufferId(target), attachment, texture, level);
    }

    public void framebufferTexture(int target, int attachment, int texture, int level) {
        attachTexture(boundFramebufferId(target), attachment, texture, level);
    }

    public void framebufferTexture1D(int target, int attachment, int textarget, int texture, int level) {
        attachTexture(boundFramebufferId(target), attachment, texture, level);
    }

    public void framebufferTexture3D(int target, int attachment, int textarget, int texture, int level,
                                     int zoffset) {
        attachTexture(boundFramebufferId(target), attachment, texture, level);
    }

    public void framebufferTextureLayer(int target, int attachment, int texture, int level, int layer) {
        attachTexture(boundFramebufferId(target), attachment, texture, level);
    }

    public void namedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        attachTexture(framebuffer, attachment, texture, level);
    }

    public void framebufferRenderbuffer(int target, int attachment, int renderbuffertarget,
                                        int renderbuffer) {
        attachRenderbuffer(boundFramebufferId(target), attachment, renderbuffer);
    }

    public int checkFramebufferStatus(int target) {
        DirectGlRecords.Framebuffer rec = currentFramebuffer(target);
        if (rec == null) {
            return GL_FRAMEBUFFER_COMPLETE; // the default framebuffer is always complete
        }
        boolean any = !rec.textureAttachments.isEmpty() || !rec.renderbufferAttachments.isEmpty();
        return any && rec.width > 0 ? GL_FRAMEBUFFER_COMPLETE
                : GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT;
    }

    public int getFramebufferAttachmentParameteri(int target, int attachment, int pname) {
        DirectGlRecords.Framebuffer rec = currentFramebuffer(target);
        if (rec == null) {
            return 0;
        }
        // GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE (0x8CD0): TEXTURE 0x1702 / RENDERBUFFER 0x8D41
        if (pname == 0x8CD0) {
            if (rec.textureAttachments.containsKey(attachment)) {
                return 0x1702;
            }
            if (rec.renderbufferAttachments.containsKey(attachment)) {
                return GL_RENDERBUFFER;
            }
            return 0;
        }
        // GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME (0x8CD1)
        if (pname == 0x8CD1) {
            Integer texture = rec.textureAttachments.get(attachment);
            if (texture != null) {
                return texture;
            }
            return rec.renderbufferAttachments.getOrDefault(attachment, 0);
        }
        return 0;
    }

    public void blitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0,
                                int dstX1, int dstY1, int mask, int filter) {
        // No sink op exists for blits in this stage (see the class doc's limits).
    }

    public void blitNamedFramebuffer(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0,
                                     int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1,
                                     int mask, int filter) {
    }

    public void namedFramebufferReadBuffer(int framebuffer, int mode) {
        DirectGlRecords.Framebuffer rec = this.framebuffers.get(framebuffer);
        if (rec != null) {
            rec.readBuffer = mode;
        }
    }

    public void namedFramebufferDrawBuffers(int framebuffer, IntBuffer bufs) {
        DirectGlRecords.Framebuffer rec = this.framebuffers.get(framebuffer);
        if (rec != null) {
            rec.drawBuffers.clear();
            IntBuffer view = bufs.duplicate();
            while (view.hasRemaining()) {
                rec.drawBuffers.add(view.get());
            }
        }
    }

    public void readPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        // GPU-held content is not read back in this stage; zero-fill (see getTexImage).
        getTexImage(GL_TEXTURE_2D, 0, format, type, pixels);
    }

    public void readPixels(int x, int y, int width, int height, int format, int type, FloatBuffer pixels) {
        if (pixels != null) {
            FloatBuffer view = pixels.duplicate();
            while (view.hasRemaining()) {
                view.put(0f);
            }
        }
    }

    public void readPixels(int x, int y, int width, int height, int format, int type, IntBuffer pixels) {
        if (pixels != null) {
            IntBuffer view = pixels.duplicate();
            while (view.hasRemaining()) {
                view.put(0);
            }
        }
    }

    public void readPixels(int x, int y, int width, int height, int format, int type,
                           long pixelBufferOffset) {
    }

    public int genRenderbuffers() {
        int id = this.nextRenderbuffer++;
        this.renderbuffers.put(id, new DirectGlRecords.Renderbuffer());
        return id;
    }

    public void deleteRenderbuffers(int renderbuffer) {
        this.renderbuffers.remove(renderbuffer);
        if (this.boundRenderbuffer == renderbuffer) {
            this.boundRenderbuffer = 0;
        }
    }

    public boolean isRenderbuffer(int renderbuffer) {
        return this.renderbuffers.containsKey(renderbuffer);
    }

    public void bindRenderbuffer(int target, int renderbuffer) {
        this.boundRenderbuffer = renderbuffer;
    }

    public void renderbufferStorage(int target, int internalformat, int width, int height) {
        renderbufferStorageMultisample(target, 0, internalformat, width, height);
    }

    public void renderbufferStorageMultisample(int target, int samples, int internalformat,
                                               int width, int height) {
        DirectGlRecords.Renderbuffer rec = this.renderbuffers.get(this.boundRenderbuffer);
        if (rec == null) {
            return;
        }
        rec.internalFormat = internalformat;
        rec.width = width;
        rec.height = height;
        rec.samples = samples;
        if (this.sink != null && rec.sinkTextureId == 0L) {
            // A renderbuffer is a sink texture the framebuffer assembly references by id.
            rec.sinkTextureId = this.sink.textureOps().createTexture2D(width, height, internalformat, 1);
        }
    }

    /**
     * Ensures the sink side of a custom framebuffer exists: its color/depth attachments are the
     * sink ids of the attached textures (or renderbuffer textures), created on first use and
     * recreated whenever the attachment set changes.
     *
     * @return the sink framebuffer id, or 0 when the framebuffer is the default one / unusable
     */
    long ensureSinkFramebuffer(int fboId) {
        DirectGlRecords.Framebuffer rec = this.framebuffers.get(fboId);
        if (rec == null || this.sink == null) {
            return 0L;
        }
        if (rec.sinkCreated) {
            return rec.sinkId;
        }
        long colorSinkId = 0L;
        long depthSinkId = 0L;
        for (Map.Entry<Integer, Integer> entry : rec.textureAttachments.entrySet()) {
            long sinkId = sinkTextureId(entry.getValue());
            if (entry.getKey() == GL_DEPTH_ATTACHMENT || entry.getKey() == GL_DEPTH_STENCIL_ATTACHMENT) {
                depthSinkId = sinkId;
            } else if (entry.getKey() >= GL_COLOR_ATTACHMENT0 && colorSinkId == 0L) {
                colorSinkId = sinkId;
            }
        }
        for (Map.Entry<Integer, Integer> entry : rec.renderbufferAttachments.entrySet()) {
            DirectGlRecords.Renderbuffer rb = this.renderbuffers.get(entry.getValue());
            if (rb == null) {
                continue;
            }
            if (entry.getKey() == GL_DEPTH_ATTACHMENT || entry.getKey() == GL_DEPTH_STENCIL_ATTACHMENT) {
                depthSinkId = rb.sinkTextureId;
            } else if (entry.getKey() >= GL_COLOR_ATTACHMENT0 && colorSinkId == 0L) {
                colorSinkId = rb.sinkTextureId;
            }
        }
        if (rec.width <= 0 || rec.height <= 0) {
            return 0L;
        }
        rec.sinkId = this.sink.framebufferOps().createFramebuffer(
                colorSinkId == 0L ? new long[0] : new long[] {colorSinkId},
                depthSinkId, rec.width, rec.height);
        rec.sinkCreated = true;
        return rec.sinkId;
    }

    /** The sink texture id for a GL texture name, creating the sink texture if it has storage. */
    long sinkTextureId(int textureId) {
        DirectGlRecords.Texture rec = this.textures.get(textureId);
        if (rec == null) {
            return 0L;
        }
        if (!rec.sinkCreated && this.sink != null && rec.width > 0) {
            rec.sinkId = this.sink.textureOps().createTexture2D(rec.width, rec.height,
                    rec.internalFormat, 1);
            rec.sinkCreated = true;
        }
        return rec.sinkCreated ? rec.sinkId : 0L;
    }

    // -- shaders / programs / uniforms --------------------------------------------------------------

    public int createShader(int type) {
        int id = this.nextShader++;
        DirectGlRecords.Shader rec = new DirectGlRecords.Shader();
        rec.type = type;
        this.shaders.put(id, rec);
        return id;
    }

    public void deleteShader(int shader) {
        this.shaders.remove(shader);
    }

    public boolean isShader(int obj) {
        return this.shaders.containsKey(obj);
    }

    public void shaderSource(int shader, CharSequence source) {
        DirectGlRecords.Shader rec = this.shaders.get(shader);
        if (rec != null) {
            rec.source = source == null ? "" : source.toString();
            rec.compiled = false;
        }
    }

    public void compileShader(int shader) {
        DirectGlRecords.Shader rec = this.shaders.get(shader);
        if (rec != null) {
            // Real compilation happens at link time, where both stages are known; the flag and
            // an empty log are the honest answers until then (GLSM reads LINK_STATUS, not this).
            rec.compiled = true;
            rec.infoLog = "";
        }
    }

    public int getShaderi(int shader, int pname) {
        DirectGlRecords.Shader rec = this.shaders.get(shader);
        if (rec == null) {
            return 0;
        }
        return switch (pname) {
            case GL_COMPILE_STATUS -> rec.compiled ? 1 : 0;
            case 0x8B4F -> rec.type; // GL_SHADER_TYPE
            case GL_INFO_LOG_LENGTH -> rec.infoLog.length();
            case GL_SHADER_SOURCE_LENGTH -> rec.source.length();
            default -> 0;
        };
    }

    public String getShaderInfoLog(int shader, int maxLength) {
        DirectGlRecords.Shader rec = this.shaders.get(shader);
        return rec == null ? "" : rec.infoLog;
    }

    public String getShaderInfoLog(int shader) {
        return getShaderInfoLog(shader, 8192);
    }

    public void getShaderInfoLog(int shader, IntBuffer length, ByteBuffer infoLog) {
        writeInfoLog(getShaderInfoLog(shader, 8192), length, infoLog);
    }

    public void getShaderInfoLog(int shader, int[] length, ByteBuffer infoLog) {
        String log = getShaderInfoLog(shader, 8192);
        if (length != null && length.length > 0) {
            length[0] = log.length();
        }
        writeBytes(log, infoLog);
    }

    public void getShaderSource(int shader, IntBuffer length, ByteBuffer source) {
        DirectGlRecords.Shader rec = this.shaders.get(shader);
        String text = rec == null ? "" : rec.source;
        if (length != null && length.hasRemaining()) {
            length.put(length.position(), text.length());
        }
        writeBytes(text, source);
    }

    public void getShaderSource(int shader, int[] length, ByteBuffer source) {
        DirectGlRecords.Shader rec = this.shaders.get(shader);
        String text = rec == null ? "" : rec.source;
        if (length != null && length.length > 0) {
            length[0] = text.length();
        }
        writeBytes(text, source);
    }

    public String getShaderSource(int shader, int maxLength) {
        DirectGlRecords.Shader rec = this.shaders.get(shader);
        if (rec == null) {
            return "";
        }
        return rec.source.length() <= maxLength ? rec.source : rec.source.substring(0, maxLength);
    }

    private static void writeBytes(String text, ByteBuffer out) {
        if (out == null) {
            return;
        }
        ByteBuffer view = out.duplicate();
        int limit = Math.min(view.remaining(), text.length());
        for (int i = 0; i < limit; i++) {
            view.put((byte) text.charAt(i));
        }
        if (view.hasRemaining()) {
            view.put((byte) 0);
        }
    }

    private static void writeInfoLog(String log, IntBuffer length, ByteBuffer infoLog) {
        if (length != null && length.hasRemaining()) {
            length.put(length.position(), log.length());
        }
        writeBytes(log, infoLog);
    }

    public int createProgram() {
        int id = this.nextProgram++;
        this.programs.put(id, new DirectGlRecords.Program());
        return id;
    }

    public void deleteProgram(int program) {
        DirectGlRecords.Program rec = this.programs.remove(program);
        if (rec != null && rec.sinkHandle != 0L && this.sink != null) {
            this.sink.shaderOps().destroyProgram(rec.sinkHandle);
        }
        if (this.currentProgram == program) {
            this.currentProgram = 0;
        }
    }

    public boolean isProgram(int obj) {
        return this.programs.containsKey(obj);
    }

    public void attachShader(int program, int shader) {
        DirectGlRecords.Program rec = this.programs.get(program);
        if (rec != null && !rec.attachedShaders.contains(shader)) {
            rec.attachedShaders.add(shader);
        }
    }

    public void detachShader(int program, int shader) {
        DirectGlRecords.Program rec = this.programs.get(program);
        if (rec != null) {
            rec.attachedShaders.remove((Integer) shader);
        }
    }

    public void getAttachedShaders(int program, IntBuffer count, IntBuffer shadersOut) {
        DirectGlRecords.Program rec = this.programs.get(program);
        int n = rec == null ? 0 : rec.attachedShaders.size();
        if (count != null && count.hasRemaining()) {
            count.put(count.position(), n);
        }
        if (shadersOut != null && rec != null) {
            IntBuffer view = shadersOut.duplicate();
            for (int shader : rec.attachedShaders) {
                if (view.hasRemaining()) {
                    view.put(shader);
                }
            }
        }
    }

    public void getAttachedShaders(int program, int[] count, int[] shadersOut) {
        DirectGlRecords.Program rec = this.programs.get(program);
        int n = rec == null ? 0 : rec.attachedShaders.size();
        if (count != null && count.length > 0) {
            count[0] = n;
        }
        if (shadersOut != null && rec != null) {
            for (int i = 0; i < Math.min(n, shadersOut.length); i++) {
                shadersOut[i] = rec.attachedShaders.get(i);
            }
        }
    }

    public void linkProgram(int program) {
        DirectGlRecords.Program rec = this.programs.get(program);
        if (rec == null) {
            return;
        }
        rec.linked = false;
        rec.failed = false;
        rec.infoLog = "";
        String vertexSource = null;
        String fragmentSource = null;
        String computeSource = null;
        for (int shaderId : rec.attachedShaders) {
            DirectGlRecords.Shader shader = this.shaders.get(shaderId);
            if (shader == null) {
                continue;
            }
            switch (shader.type) {
                case GL_VERTEX_SHADER -> vertexSource = shader.source;
                case GL_FRAGMENT_SHADER -> fragmentSource = shader.source;
                case GL_COMPUTE_SHADER -> computeSource = shader.source;
                default -> {
                }
            }
        }
        DirectGlShaderCompiler.CompiledProgram compiled;
        if (computeSource != null) {
            compiled = DirectGlShaderCompiler.compileCompute(computeSource);
            if (compiled.ok() && this.sink != null) {
                rec.sinkHandle = this.sink.shaderOps().createComputeProgram(compiled.bundle()).handle();
            }
        } else {
            compiled = DirectGlShaderCompiler.compileProgram(vertexSource, fragmentSource);
            if (compiled.ok() && this.sink != null) {
                rec.sinkHandle = this.sink.shaderOps().createProgram(compiled.bundle());
                DirectGlSinkExtensions ext = ext();
                if (ext != null) {
                    ext.registerProgramAttribs(rec.sinkHandle, attribDescs(compiled));
                }
            }
        }
        rec.compiled = compiled;
        if (compiled.ok()) {
            rec.linked = true;
            rec.uniformLocations.clear();
            rec.attribLocations.clear();
            for (int i = 0; i < compiled.uniforms().size(); i++) {
                rec.uniformLocations.put(compiled.uniforms().get(i).name(), i);
            }
            for (org.soutalab.celtium.vulkan.gl.ModGlslTranslator.Attribute attribute
                    : compiled.attributes()) {
                Integer override = rec.preLinkAttribLocations.get(attribute.name());
                rec.attribLocations.put(attribute.name(),
                        override != null ? override : attribute.location());
            }
            int blockSize = Math.max(16, compiled.uniformBlockSize());
            rec.uniformBlock = ByteBuffer.allocateDirect(blockSize).order(ByteOrder.nativeOrder());
            rec.uniformsDirty = true;
        } else {
            rec.failed = true;
            rec.infoLog = compiled.failure() == null ? "link failed" : compiled.failure();
        }
    }

    private static DirectGlSinkExtensions.AttribDesc[] attribDescs(
            DirectGlShaderCompiler.CompiledProgram compiled) {
        var attributes = compiled.attributes();
        DirectGlSinkExtensions.AttribDesc[] out = new DirectGlSinkExtensions.AttribDesc[attributes.size()];
        for (int i = 0; i < attributes.size(); i++) {
            var attribute = attributes.get(i);
            out[i] = new DirectGlSinkExtensions.AttribDesc(attribute.location(),
                    vkFormatFor(attribute.type()));
        }
        return out;
    }

    /** GLSL attribute type -> Vulkan format (the converter's canonical vertex packing). */
    static int vkFormatFor(String glslType) {
        return switch (glslType) {
            case "float" -> org.lwjgl.vulkan.VK10.VK_FORMAT_R32_SFLOAT;
            case "vec2" -> org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_SFLOAT;
            case "vec3" -> org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT;
            case "vec4" -> org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
            case "int" -> org.lwjgl.vulkan.VK10.VK_FORMAT_R32_SINT;
            case "ivec2" -> org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_SINT;
            case "ivec3" -> org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SINT;
            case "ivec4" -> org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_SINT;
            case "uint" -> org.lwjgl.vulkan.VK10.VK_FORMAT_R32_UINT;
            case "uvec2" -> org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_UINT;
            case "uvec3" -> org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_UINT;
            case "uvec4" -> org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_UINT;
            default -> org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
        };
    }

    static int componentCount(String glslType) {
        return switch (glslType) {
            case "float", "int", "uint" -> 1;
            case "vec2", "ivec2", "uvec2" -> 2;
            case "vec3", "ivec3", "uvec3" -> 3;
            default -> 4;
        };
    }

    public void validateProgram(int program) {
    }

    public void programParameteri(int program, int pname, int value) {
    }

    public void programBinary(int program, int binaryFormat, ByteBuffer binary) {
    }

    public void getProgramBinary(int program, IntBuffer length, IntBuffer binaryFormat,
                                 ByteBuffer binary) {
    }

    public void useProgram(int program) {
        this.currentProgram = program;
    }

    public String getProgramInfoLog(int program, int maxLength) {
        DirectGlRecords.Program rec = this.programs.get(program);
        return rec == null ? "" : rec.infoLog;
    }

    public String getProgramInfoLog(int program) {
        return getProgramInfoLog(program, 8192);
    }

    public void getProgramInfoLog(int program, IntBuffer length, ByteBuffer infoLog) {
        writeInfoLog(getProgramInfoLog(program, 8192), length, infoLog);
    }

    public void getProgramInfoLog(int program, int[] length, ByteBuffer infoLog) {
        String log = getProgramInfoLog(program, 8192);
        if (length != null && length.length > 0) {
            length[0] = log.length();
        }
        writeBytes(log, infoLog);
    }

    public int getProgrami(int program, int pname) {
        DirectGlRecords.Program rec = this.programs.get(program);
        if (rec == null) {
            return 0;
        }
        return switch (pname) {
            case GL_LINK_STATUS -> rec.linked ? 1 : 0;
            case GL_ACTIVE_UNIFORMS -> rec.compiled == null ? 0 : rec.compiled.uniforms().size();
            case GL_ACTIVE_ATTRIBUTES -> rec.compiled == null ? 0 : rec.compiled.attributes().size();
            case GL_INFO_LOG_LENGTH -> rec.infoLog.length();
            case 0x8B85 -> rec.compiled == null ? 0 : rec.compiled.uniformBlockSize(); // ACTIVE_UNIFORM_BLOCKS size proxy
            default -> 0;
        };
    }

    public void getProgramiv(int program, int pname, IntBuffer params) {
        if (params.hasRemaining()) {
            params.put(params.position(), getProgrami(program, pname));
        }
    }

    public void bindAttribLocation(int program, int index, CharSequence name) {
        DirectGlRecords.Program rec = this.programs.get(program);
        if (rec != null) {
            rec.preLinkAttribLocations.put(name.toString(), index);
        }
    }

    public void bindFragDataLocation(int program, int colorNumber, CharSequence name) {
    }

    public int getAttribLocation(int program, CharSequence name) {
        DirectGlRecords.Program rec = this.programs.get(program);
        if (rec == null) {
            return -1;
        }
        return rec.attribLocations.getOrDefault(name.toString(), -1);
    }

    public int getAttribLocation(int program, ByteBuffer name) {
        return getAttribLocation(program, readName(name));
    }

    public int getUniformLocation(int program, CharSequence name) {
        DirectGlRecords.Program rec = this.programs.get(program);
        if (rec == null) {
            return -1;
        }
        String key = name.toString();
        Integer location = rec.uniformLocations.get(key);
        if (location == null && key.endsWith("[0]")) {
            location = rec.uniformLocations.get(key.substring(0, key.length() - 3));
        }
        return location == null ? -1 : location;
    }

    public int getUniformLocation(int program, ByteBuffer name) {
        return getUniformLocation(program, readName(name));
    }

    private static String readName(ByteBuffer buffer) {
        if (buffer == null) {
            return "";
        }
        ByteBuffer view = buffer.duplicate();
        StringBuilder out = new StringBuilder();
        while (view.hasRemaining()) {
            byte b = view.get();
            if (b == 0) {
                break;
            }
            out.append((char) b);
        }
        return out.toString();
    }

    public String getActiveUniform(int program, int index, int maxLength, IntBuffer sizeType) {
        DirectGlRecords.Program rec = this.programs.get(program);
        if (rec == null || rec.compiled == null || index >= rec.compiled.uniforms().size()) {
            return "";
        }
        var uniform = rec.compiled.uniforms().get(index);
        if (sizeType != null) {
            if (sizeType.hasRemaining()) {
                sizeType.put(sizeType.position(), Math.max(1, uniform.arrayLength()));
            }
            if (sizeType.remaining() > 1) {
                sizeType.put(sizeType.position() + 1, glTypeFor(uniform.type()));
            }
        }
        return uniform.name();
    }

    public void getActiveUniform(int program, int index, IntBuffer length, IntBuffer size,
                                 IntBuffer type, ByteBuffer name) {
        String found = activeName(program, index, true, size, type);
        if (length != null && length.hasRemaining()) {
            length.put(length.position(), found.length());
        }
        writeBytes(found, name);
    }

    public void getActiveUniform(int program, int index, int[] length, int[] size, int[] type,
                                 ByteBuffer name) {
        DirectGlRecords.Program rec = this.programs.get(program);
        String found = "";
        if (rec != null && rec.compiled != null && index < rec.compiled.uniforms().size()) {
            var uniform = rec.compiled.uniforms().get(index);
            found = uniform.name();
            if (size != null && size.length > 0) {
                size[0] = Math.max(1, uniform.arrayLength());
            }
            if (type != null && type.length > 0) {
                type[0] = glTypeFor(uniform.type());
            }
        }
        if (length != null && length.length > 0) {
            length[0] = found.length();
        }
        writeBytes(found, name);
    }

    public void getActiveAttrib(int program, int index, IntBuffer length, IntBuffer size,
                                IntBuffer type, ByteBuffer name) {
        String found = activeName(program, index, false, size, type);
        if (length != null && length.hasRemaining()) {
            length.put(length.position(), found.length());
        }
        writeBytes(found, name);
    }

    public String getActiveAttrib(int program, int index, int maxLength, IntBuffer sizeType) {
        DirectGlRecords.Program rec = this.programs.get(program);
        if (rec == null || rec.compiled == null || index >= rec.compiled.attributes().size()) {
            return "";
        }
        var attribute = rec.compiled.attributes().get(index);
        if (sizeType != null) {
            if (sizeType.hasRemaining()) {
                sizeType.put(sizeType.position(), 1);
            }
            if (sizeType.remaining() > 1) {
                sizeType.put(sizeType.position() + 1, glTypeFor(attribute.type()));
            }
        }
        return attribute.name();
    }

    public void getActiveAttrib(int program, int index, int[] length, int[] size, int[] type,
                                ByteBuffer name) {
        DirectGlRecords.Program rec = this.programs.get(program);
        String found = "";
        if (rec != null && rec.compiled != null && index < rec.compiled.attributes().size()) {
            var attribute = rec.compiled.attributes().get(index);
            found = attribute.name();
            if (size != null && size.length > 0) {
                size[0] = 1;
            }
            if (type != null && type.length > 0) {
                type[0] = glTypeFor(attribute.type());
            }
        }
        if (length != null && length.length > 0) {
            length[0] = found.length();
        }
        writeBytes(found, name);
    }

    private String activeName(int program, int index, boolean uniform, IntBuffer size, IntBuffer type) {
        DirectGlRecords.Program rec = this.programs.get(program);
        if (rec == null || rec.compiled == null) {
            return "";
        }
        if (uniform) {
            if (index >= rec.compiled.uniforms().size()) {
                return "";
            }
            var member = rec.compiled.uniforms().get(index);
            if (size != null && size.hasRemaining()) {
                size.put(size.position(), Math.max(1, member.arrayLength()));
            }
            if (type != null && type.hasRemaining()) {
                type.put(type.position(), glTypeFor(member.type()));
            }
            return member.name();
        }
        if (index >= rec.compiled.attributes().size()) {
            return "";
        }
        var attribute = rec.compiled.attributes().get(index);
        if (size != null && size.hasRemaining()) {
            size.put(size.position(), 1);
        }
        if (type != null && type.hasRemaining()) {
            type.put(type.position(), glTypeFor(attribute.type()));
        }
        return attribute.name();
    }

    static int glTypeFor(String glslType) {
        return switch (glslType) {
            case "float" -> 0x1406;
            case "vec2" -> 0x8B50;
            case "vec3" -> 0x8B51;
            case "vec4" -> 0x8B52;
            case "int" -> 0x1404;
            case "ivec2" -> 0x8B53;
            case "ivec3" -> 0x8B54;
            case "ivec4" -> 0x8B55;
            case "uint" -> 0x1405;
            case "uvec2" -> 0x8DC6;
            case "uvec3" -> 0x8DC7;
            case "uvec4" -> 0x8DC8;
            case "bool" -> 0x8B56;
            case "mat3" -> 0x8B5B;
            case "mat4" -> 0x8B5C;
            case "sampler2D" -> 0x8B5E;
            case "samplerCube" -> 0x8B60;
            default -> 0x1406;
        };
    }

    public int getUniformBlockIndex(int program, CharSequence name) {
        DirectGlRecords.Program rec = this.programs.get(program);
        return rec != null && rec.linked ? 0 : -1;
    }

    public int getUniformBlockIndex(int program, ByteBuffer name) {
        return getUniformBlockIndex(program, readName(name));
    }

    public void uniformBlockBinding(int program, int blockIndex, int binding) {
        DirectGlRecords.Program rec = this.programs.get(program);
        if (rec != null) {
            rec.uniformBlockBindingPoint = binding;
        }
    }

    // -- uniform setters: values land in the program's packed block at member offsets ----------------

    private org.soutalab.celtium.vulkan.gl.ModGlslTranslator.UniformMember memberOf(int location) {
        DirectGlRecords.Program rec = this.programs.get(this.currentProgram);
        if (rec == null || rec.compiled == null || location < 0
                || location >= rec.compiled.uniforms().size()) {
            return null;
        }
        return rec.compiled.uniforms().get(location);
    }

    private ByteBuffer blockOf(int location) {
        DirectGlRecords.Program rec = this.programs.get(this.currentProgram);
        return rec == null ? null : rec.uniformBlock;
    }

    private void markUniformsDirty() {
        DirectGlRecords.Program rec = this.programs.get(this.currentProgram);
        if (rec != null) {
            rec.uniformsDirty = true;
        }
    }

    private void putFloats(int location, float[] values) {
        var member = memberOf(location);
        ByteBuffer block = blockOf(location);
        if (member == null || block == null) {
            return;
        }
        int offset = member.offset();
        for (int i = 0; i < values.length && offset + 4 <= block.capacity(); i++, offset += 4) {
            block.putFloat(offset, values[i]);
        }
        markUniformsDirty();
    }

    private void putInts(int location, int[] values) {
        var member = memberOf(location);
        ByteBuffer block = blockOf(location);
        if (member == null || block == null) {
            return;
        }
        int offset = member.offset();
        for (int i = 0; i < values.length && offset + 4 <= block.capacity(); i++, offset += 4) {
            block.putInt(offset, values[i]);
        }
        markUniformsDirty();
    }

    public void uniform1f(int location, float v0) {
        putFloats(location, new float[] {v0});
    }

    public void uniform1d(int location, double v0) {
        putFloats(location, new float[] {(float) v0});
    }

    public void uniform1i(int location, int v0) {
        putInts(location, new int[] {v0});
    }

    public void uniform1ui(int location, int v0) {
        putInts(location, new int[] {v0});
    }

    public void uniform2f(int location, float v0, float v1) {
        putFloats(location, new float[] {v0, v1});
    }

    public void uniform2i(int location, int v0, int v1) {
        putInts(location, new int[] {v0, v1});
    }

    public void uniform2ui(int location, int v0, int v1) {
        putInts(location, new int[] {v0, v1});
    }

    public void uniform3f(int location, float v0, float v1, float v2) {
        putFloats(location, new float[] {v0, v1, v2});
    }

    public void uniform3i(int location, int v0, int v1, int v2) {
        putInts(location, new int[] {v0, v1, v2});
    }

    public void uniform3ui(int location, int v0, int v1, int v2) {
        putInts(location, new int[] {v0, v1, v2});
    }

    public void uniform4f(int location, float v0, float v1, float v2, float v3) {
        putFloats(location, new float[] {v0, v1, v2, v3});
    }

    public void uniform4i(int location, int v0, int v1, int v2, int v3) {
        putInts(location, new int[] {v0, v1, v2, v3});
    }

    public void uniform4ui(int location, int v0, int v1, int v2, int v3) {
        putInts(location, new int[] {v0, v1, v2, v3});
    }

    public void uniform1fv(int location, FloatBuffer values) {
        putFloats(location, readFloats(values, 1));
    }

    public void uniform1fv(int location, float[] values) {
        putFloats(location, values);
    }

    public void uniform1iv(int location, IntBuffer value) {
        putInts(location, readInts(value, 1));
    }

    public void uniform2(int location, FloatBuffer value) {
        putFloats(location, readFloats(value, 2));
    }

    public void uniform2iv(int location, IntBuffer value) {
        putInts(location, readInts(value, 2));
    }

    public void uniform3(int location, FloatBuffer value) {
        putFloats(location, readFloats(value, 3));
    }

    public void uniform3fv(int location, float[] values) {
        putFloats(location, values);
    }

    public void uniform3iv(int location, IntBuffer value) {
        putInts(location, readInts(value, 3));
    }

    public void uniform4(int location, FloatBuffer value) {
        putFloats(location, readFloats(value, 4));
    }

    public void uniform4fv(int location, float[] values) {
        putFloats(location, values);
    }

    public void uniform4iv(int location, IntBuffer value) {
        putInts(location, readInts(value, 4));
    }

    private static float[] readFloats(FloatBuffer buffer, int components) {
        if (buffer == null) {
            return new float[0];
        }
        FloatBuffer view = buffer.duplicate();
        float[] out = new float[Math.min(view.remaining(), components * 256)];
        view.get(out);
        return out;
    }

    private static int[] readInts(IntBuffer buffer, int components) {
        if (buffer == null) {
            return new int[0];
        }
        IntBuffer view = buffer.duplicate();
        int[] out = new int[Math.min(view.remaining(), components * 256)];
        view.get(out);
        return out;
    }

    public void uniformMatrix2(int location, boolean transpose, FloatBuffer matrices) {
        putMatrix(location, 2, transpose, matrices);
    }

    public void uniformMatrix3(int location, boolean transpose, FloatBuffer matrices) {
        putMatrix(location, 3, transpose, matrices);
    }

    public void uniformMatrix4(int location, boolean transpose, FloatBuffer matrices) {
        putMatrix(location, 4, transpose, matrices);
    }

    public void uniformMatrix3(int location, boolean transpose, float[] matrices) {
        putMatrix(location, 3, transpose, FloatBuffer.wrap(matrices));
    }

    public void uniformMatrix4(int location, boolean transpose, float[] matrices) {
        putMatrix(location, 4, transpose, FloatBuffer.wrap(matrices));
    }

    private void putMatrix(int location, int dimension, boolean transpose, FloatBuffer matrices) {
        var member = memberOf(location);
        ByteBuffer block = blockOf(location);
        if (member == null || block == null || matrices == null) {
            return;
        }
        FloatBuffer view = matrices.duplicate();
        int count = dimension * dimension;
        if (view.remaining() < count) {
            return;
        }
        float[] values = new float[count];
        view.get(values);
        int offset = member.offset();
        // The block layout is std140-style: each matrix column occupies a vec4 slot.
        for (int column = 0; column < dimension; column++) {
            for (int row = 0; row < dimension; row++) {
                float value = transpose ? values[row * dimension + column]
                        : values[column * dimension + row];
                int at = offset + (column * 4 + row) * 4;
                if (at + 4 <= block.capacity()) {
                    block.putFloat(at, value);
                }
            }
        }
        markUniformsDirty();
    }

    public void getUniformfv(int program, int location, FloatBuffer params) {
        DirectGlRecords.Program rec = this.programs.get(program);
        if (rec == null || rec.uniformBlock == null || rec.compiled == null
                || location >= rec.compiled.uniforms().size()) {
            return;
        }
        var member = rec.compiled.uniforms().get(location);
        FloatBuffer view = params.duplicate();
        int components = Math.min(view.remaining(), Math.max(1, member.size() / 4));
        for (int i = 0; i < components; i++) {
            view.put(rec.uniformBlock.getFloat(member.offset() + i * 4));
        }
    }

    public void getUniformiv(int program, int location, IntBuffer params) {
        DirectGlRecords.Program rec = this.programs.get(program);
        if (rec == null || rec.uniformBlock == null || rec.compiled == null
                || location >= rec.compiled.uniforms().size()) {
            return;
        }
        var member = rec.compiled.uniforms().get(location);
        IntBuffer view = params.duplicate();
        int components = Math.min(view.remaining(), Math.max(1, member.size() / 4));
        for (int i = 0; i < components; i++) {
            view.put(rec.uniformBlock.getInt(member.offset() + i * 4));
        }
    }

    /** Uploads the current program's uniform block to the sink when it changed. */
    void flushUniforms(DirectGlRecords.Program rec) {
        if (rec == null || !rec.uniformsDirty || rec.uniformBlock == null) {
            return;
        }
        rec.uniformsDirty = false;
        DirectGlSinkExtensions ext = ext();
        if (ext != null && rec.sinkHandle != 0L) {
            ByteBuffer view = rec.uniformBlock.duplicate();
            view.position(0);
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment segment = arena.allocate(view.capacity());
                segment.asByteBuffer().put(view);
                ext.updateUniforms(rec.sinkHandle, segment);
            }
        }
    }

    // -- vertex arrays --------------------------------------------------------------------------------

    public int genVertexArrays() {
        int id = this.nextVertexArray++;
        this.vertexArrays.put(id, new DirectGlRecords.VertexArray());
        return id;
    }

    public void deleteVertexArrays(int array) {
        this.vertexArrays.remove(array);
        if (this.boundVertexArray == array) {
            this.boundVertexArray = 0;
        }
    }

    public boolean isVertexArray(int array) {
        return this.vertexArrays.containsKey(array);
    }

    public void bindVertexArray(int array) {
        this.boundVertexArray = array;
        if (array != 0 && !this.vertexArrays.containsKey(array)) {
            this.vertexArrays.put(array, new DirectGlRecords.VertexArray());
        }
        Integer element = this.boundBuffers.get(GL_ELEMENT_ARRAY_BUFFER);
        DirectGlRecords.VertexArray vao = currentVertexArray();
        if (element != null) {
            vao.elementBuffer = element;
        }
    }

    DirectGlRecords.VertexArray currentVertexArray() {
        return this.vertexArrays.computeIfAbsent(this.boundVertexArray,
                key -> new DirectGlRecords.VertexArray());
    }

    private DirectGlRecords.Attrib attrib(int index) {
        return currentVertexArray().attribs.computeIfAbsent(index, key -> new DirectGlRecords.Attrib());
    }

    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride,
                                    long pointer) {
        DirectGlRecords.Attrib attrib = attrib(index);
        attrib.size = size;
        attrib.type = type;
        attrib.normalized = normalized;
        attrib.stride = stride;
        attrib.offset = pointer;
        attrib.integer = false;
        Integer buffer = this.boundBuffers.get(GL_ARRAY_BUFFER);
        attrib.buffer = buffer == null ? 0 : buffer;
    }

    public void vertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        vertexAttribPointer(index, size, type, false, stride, pointer);
        attrib(index).integer = true;
    }

    public void enableVertexAttribArray(int index) {
        attrib(index).enabled = true;
    }

    public void disableVertexAttribArray(int index) {
        attrib(index).enabled = false;
    }

    public void vertexAttribDivisor(int index, int divisor) {
        attrib(index).divisor = divisor;
    }

    public void vertexAttribFormat(int attribindex, int size, int type, boolean normalized,
                                   int relativeoffset) {
        DirectGlRecords.Attrib attrib = attrib(attribindex);
        attrib.size = size;
        attrib.type = type;
        attrib.normalized = normalized;
        attrib.offset = relativeoffset;
        attrib.integer = false;
    }

    public void vertexAttribIFormat(int attribindex, int size, int type, int relativeoffset) {
        vertexAttribFormat(attribindex, size, type, false, relativeoffset);
        attrib(attribindex).integer = true;
    }

    public void vertexAttribBinding(int attribindex, int bindingindex) {
        attrib(attribindex).bindingIndex = bindingindex;
    }

    public void bindVertexBuffer(int bindingindex, int buffer, long offset, int stride) {
        DirectGlRecords.Binding binding = currentVertexArray().bindings
                .computeIfAbsent(bindingindex, key -> new DirectGlRecords.Binding());
        binding.buffer = buffer;
        binding.offset = offset;
        binding.stride = stride;
    }

    public void getVertexAttribi(int index, int pname, IntBuffer params) {
        DirectGlRecords.Attrib attrib = currentVertexArray().attribs.get(index);
        int value = 0;
        if (attrib != null) {
            value = switch (pname) {
                case GL_VERTEX_ATTRIB_ARRAY_ENABLED -> attrib.enabled ? 1 : 0;
                case GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING -> attrib.buffer;
                case GL_VERTEX_ATTRIB_ARRAY_SIZE -> attrib.size;
                case GL_VERTEX_ATTRIB_ARRAY_STRIDE -> attrib.stride;
                case GL_VERTEX_ATTRIB_ARRAY_TYPE -> attrib.type;
                case GL_VERTEX_ATTRIB_ARRAY_NORMALIZED -> attrib.normalized ? 1 : 0;
                case GL_VERTEX_ATTRIB_ARRAY_DIVISOR -> attrib.divisor;
                default -> 0;
            };
        }
        if (params.hasRemaining()) {
            params.put(params.position(), value);
        }
    }

    public void vertexAttrib2f(int index, float v0, float v1) {
        this.genericAttribs[index][0] = v0;
        this.genericAttribs[index][1] = v1;
    }

    public void vertexAttrib3f(int index, float v0, float v1, float v2) {
        this.genericAttribs[index][0] = v0;
        this.genericAttribs[index][1] = v1;
        this.genericAttribs[index][2] = v2;
    }

    public void vertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
        this.genericAttribs[index][0] = v0;
        this.genericAttribs[index][1] = v1;
        this.genericAttribs[index][2] = v2;
        this.genericAttribs[index][3] = v3;
    }

    public void primitiveRestartIndex(int index) {
    }

    public void abandonTextureStaging(org.soutalab.celtium.glcompat.texture.TextureStaging staging) {
    }

    // -- draw assembly -----------------------------------------------------------------------------------

    private static int typeSize(int glType) {
        return switch (glType) {
            case 0x1400, 0x1401 -> 1; // BYTE, UNSIGNED_BYTE
            case 0x1402, 0x1403 -> 2; // SHORT, UNSIGNED_SHORT
            case 0x1404, 0x1405, 0x1406 -> 4; // INT, UNSIGNED_INT, FLOAT
            case 0x140A -> 8; // DOUBLE
            default -> 4;
        };
    }

    private static PrimitiveTopology topologyOf(int mode) {
        return switch (mode) {
            case GL_POINTS -> PrimitiveTopology.POINTS;
            case GL_LINES -> PrimitiveTopology.LINES;
            case GL_LINE_STRIP, GL_LINE_LOOP -> PrimitiveTopology.LINE_STRIP;
            case GL_TRIANGLE_STRIP -> PrimitiveTopology.TRIANGLE_STRIP;
            case GL_TRIANGLE_FAN -> PrimitiveTopology.TRIANGLE_FAN;
            default -> PrimitiveTopology.TRIANGLES;
        };
    }

    /** One enabled per-vertex attribute, resolved against its binding. */
    private record ResolvedAttrib(int index, int buffer, int size, int type, boolean normalized,
                                  int stride, long offset) {
        int byteSize() {
            return size * typeSize(type);
        }

        int effectiveStride() {
            return stride == 0 ? byteSize() : stride;
        }
    }

    private java.util.List<ResolvedAttrib> resolvedAttribs() {
        DirectGlRecords.VertexArray vao = currentVertexArray();
        java.util.List<ResolvedAttrib> out = new java.util.ArrayList<>();
        java.util.List<Integer> indices = new java.util.ArrayList<>(vao.attribs.keySet());
        java.util.Collections.sort(indices);
        for (int index : indices) {
            DirectGlRecords.Attrib attrib = vao.attribs.get(index);
            if (!attrib.enabled || attrib.divisor > 0) {
                continue; // instanced attributes are a recorded limitation (class doc)
            }
            int buffer = attrib.buffer;
            long offset = attrib.offset;
            int stride = attrib.stride;
            if (attrib.bindingIndex >= 0) {
                DirectGlRecords.Binding binding = vao.bindings.get(attrib.bindingIndex);
                if (binding != null) {
                    buffer = binding.buffer;
                    offset += binding.offset;
                    if (binding.stride != 0) {
                        stride = binding.stride;
                    }
                }
            }
            out.add(new ResolvedAttrib(index, buffer, attrib.size, attrib.type, attrib.normalized,
                    stride, offset));
        }
        return out;
    }

    public void drawArrays(int mode, int first, int count) {
        assembleDraw(mode, first, count, false, 0, 0L, null, 1, 0, 0);
    }

    public void drawArraysInstanced(int mode, int first, int count, int primcount) {
        assembleDraw(mode, first, count, false, 0, 0L, null, primcount, 0, 0);
    }

    public void drawElements(int mode, int count, int type, long indices) {
        assembleDraw(mode, 0, count, true, type, indices, null, 1, 0, 0);
    }

    public void drawElements(int mode, int count, int type, ByteBuffer indices) {
        assembleDraw(mode, 0, count, true, type, 0L, indices, 1, 0, 0);
    }

    public void drawElements(int mode, ByteBuffer indices) {
        int type = GL_UNSIGNED_INT;
        int count = indices == null ? 0 : indices.remaining() / 4;
        assembleDraw(mode, 0, count, true, type, 0L, indices, 1, 0, 0);
    }

    public void drawElements(int mode, ShortBuffer indices) {
        ByteBuffer bytes = bufferOf(indices);
        int count = indices == null ? 0 : indices.remaining();
        assembleDraw(mode, 0, count, true, GL_UNSIGNED_SHORT, 0L, bytes, 1, 0, 0);
    }

    public void drawElements(int mode, IntBuffer indices) {
        ByteBuffer bytes = bufferOf(indices);
        int count = indices == null ? 0 : indices.remaining();
        assembleDraw(mode, 0, count, true, GL_UNSIGNED_INT, 0L, bytes, 1, 0, 0);
    }

    public void drawElementsInstanced(int mode, int count, int type, long indices, int primcount) {
        assembleDraw(mode, 0, count, true, type, indices, null, primcount, 0, 0);
    }

    public void drawElementsBaseVertex(int mode, int count, int type, long indices, int baseVertex) {
        assembleDraw(mode, 0, count, true, type, indices, null, 1, baseVertex, 0);
    }

    public void drawRangeElements(int mode, int start, int end, int count, int type, long indices) {
        assembleDraw(mode, 0, count, true, type, indices, null, 1, 0, 0);
    }

    public void drawRangeElementsBaseVertex(int mode, int start, int end, int count, int type,
                                            long indices, int baseVertex) {
        assembleDraw(mode, 0, count, true, type, indices, null, 1, baseVertex, 0);
    }

    public void multiDrawArrays(int mode, IntBuffer firsts, IntBuffer counts) {
        if (firsts == null || counts == null) {
            return;
        }
        IntBuffer f = firsts.duplicate();
        IntBuffer c = counts.duplicate();
        while (f.hasRemaining() && c.hasRemaining()) {
            drawArrays(mode, f.get(), c.get());
        }
    }

    public void multiDrawArrays(int mode, int[] firsts, int[] counts) {
        int n = Math.min(firsts.length, counts.length);
        for (int i = 0; i < n; i++) {
            drawArrays(mode, firsts[i], counts[i]);
        }
    }

    public void multiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices,
                                            int drawcount, long pBaseVertex) {
        // The arrays live at native addresses (LWJGL callers); read them through FFM.
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment counts = MemorySegment.ofAddress(pCount)
                    .reinterpret((long) drawcount * 4, arena, null);
            MemorySegment indices = MemorySegment.ofAddress(pIndices)
                    .reinterpret((long) drawcount * 8, arena, null);
            MemorySegment bases = MemorySegment.ofAddress(pBaseVertex)
                    .reinterpret((long) drawcount * 4, arena, null);
            for (int i = 0; i < drawcount; i++) {
                int count = counts.get(java.lang.foreign.ValueLayout.JAVA_INT, (long) i * 4);
                long indexPtr = indices.get(java.lang.foreign.ValueLayout.JAVA_LONG, (long) i * 8);
                int base = bases.get(java.lang.foreign.ValueLayout.JAVA_INT, (long) i * 4);
                assembleDraw(mode, 0, count, true, type, indexPtr, null, 1, base, 0);
            }
        } catch (RuntimeException failure) {
            this.droppedDraws++;
        }
    }

    public void multiDrawArraysIndirect(int mode, long indirect, int drawcount, int stride) {
        Integer bufferId = this.boundBuffers.get(0x8F3F); // GL_DRAW_INDIRECT_BUFFER
        DirectGlRecords.Buffer buffer = bufferId == null ? null : this.buffers.get(bufferId);
        if (buffer == null || buffer.shadow == null) {
            this.droppedDraws++;
            return;
        }
        int step = stride == 0 ? 16 : stride;
        for (int i = 0; i < drawcount; i++) {
            long at = indirect + (long) i * step;
            if (at + 16 > buffer.shadow.capacity()) {
                break;
            }
            int count = buffer.shadow.getInt((int) at);
            int instances = buffer.shadow.getInt((int) at + 4);
            int first = buffer.shadow.getInt((int) at + 8);
            int firstInstance = buffer.shadow.getInt((int) at + 12);
            assembleDraw(mode, first, count, false, 0, 0L, null, instances, 0, firstInstance);
        }
    }

    public void multiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        Integer bufferId = this.boundBuffers.get(0x8F3F);
        DirectGlRecords.Buffer buffer = bufferId == null ? null : this.buffers.get(bufferId);
        if (buffer == null || buffer.shadow == null) {
            this.droppedDraws++;
            return;
        }
        int step = stride == 0 ? 20 : stride;
        for (int i = 0; i < drawcount; i++) {
            long at = indirect + (long) i * step;
            if (at + 20 > buffer.shadow.capacity()) {
                break;
            }
            int count = buffer.shadow.getInt((int) at);
            int instances = buffer.shadow.getInt((int) at + 4);
            int firstIndex = buffer.shadow.getInt((int) at + 8);
            int baseVertex = buffer.shadow.getInt((int) at + 12);
            int firstInstance = buffer.shadow.getInt((int) at + 16);
            assembleDraw(mode, 0, count, true, type,
                    (long) firstIndex * typeSize(type), null, instances, baseVertex, firstInstance);
        }
    }

    public void dispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ) {
        DirectGlRecords.Program program = this.programs.get(this.currentProgram);
        if (program == null || !program.linked || program.sinkHandle == 0L || this.sink == null) {
            return;
        }
        flushUniforms(program);
        this.sink.dispatchCompute(new ComputeProgramHandle(program.sinkHandle),
                numGroupsX, numGroupsY, numGroupsZ);
    }

    public void dispatchComputeIndirect(long offset) {
        Integer bufferId = this.boundBuffers.get(0x8F3F);
        DirectGlRecords.Buffer buffer = bufferId == null ? null : this.buffers.get(bufferId);
        if (buffer == null || buffer.shadow == null || offset + 12 > buffer.shadow.capacity()) {
            return;
        }
        dispatchCompute(buffer.shadow.getInt((int) offset), buffer.shadow.getInt((int) offset + 4),
                buffer.shadow.getInt((int) offset + 8));
    }

    /**
     * The one draw path every draw variant funnels into: resolve the program, the canonical
     * vertex span (direct or re-packed), the index span, the render target, and submit.
     */
    private void assembleDraw(int mode, int first, int count, boolean indexed, int indexType,
                              long indexOffset, ByteBuffer clientIndices, int instanceCount,
                              int baseVertex, int firstInstance) {
        DirectGlRecords.Program program = this.programs.get(this.currentProgram);
        if (program == null || !program.linked || program.failed || program.sinkHandle == 0L
                || this.sink == null || count <= 0) {
            this.droppedDraws++;
            return;
        }
        java.util.List<ResolvedAttrib> attribs = resolvedAttribs();
        if (attribs.isEmpty()) {
            this.droppedDraws++;
            return;
        }
        for (ResolvedAttrib attrib : attribs) {
            DirectGlRecords.Buffer buffer = this.buffers.get(attrib.buffer());
            if (attrib.buffer() == 0 || buffer == null || buffer.shadow == null || !buffer.sinkCreated) {
                this.droppedDraws++;
                return;
            }
        }
        flushUniforms(program);
        pushExtendedState();

        int canonicalStride = 0;
        for (ResolvedAttrib attrib : attribs) {
            canonicalStride += attrib.byteSize();
        }

        // The vertex range the draw touches.
        int rangeStart;
        int rangeCount;
        int[] indexValues = null;
        if (indexed) {
            indexValues = readIndices(indexType, indexOffset, clientIndices, count);
            if (indexValues == null) {
                this.droppedDraws++;
                return;
            }
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int value : indexValues) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            rangeStart = min == Integer.MAX_VALUE ? 0 : min;
            rangeCount = max == Integer.MIN_VALUE ? 0 : max - min + 1;
        } else {
            rangeStart = first;
            rangeCount = count;
        }
        if (rangeCount <= 0) {
            this.droppedDraws++;
            return;
        }

        boolean canonical = isCanonical(attribs, canonicalStride);
        VertexBindingSpan span;
        int drawFirstVertex;
        int drawVertexOffset;
        if (canonical) {
            ResolvedAttrib firstAttrib = attribs.get(0);
            DirectGlRecords.Buffer buffer = this.buffers.get(firstAttrib.buffer());
            long base = firstAttrib.offset();
            int capacity = (int) ((buffer.shadow.capacity() - base) / canonicalStride);
            span = new VertexBindingSpan(buffer.sinkId, base, canonicalStride, Math.max(0, capacity));
            drawFirstVertex = indexed ? 0 : first;
            drawVertexOffset = baseVertex;
        } else {
            span = repack(attribs, canonicalStride, rangeStart, rangeCount);
            if (span == null) {
                this.droppedDraws++;
                return;
            }
            drawFirstVertex = 0;
            drawVertexOffset = baseVertex - rangeStart;
        }

        IndexSpan indexSpan = null;
        DrawCommand command;
        if (indexed) {
            if (mode == GL_QUADS) {
                indexSpan = quadIndices(indexValues, canonical ? 0 : rangeStart);
                if (indexSpan == null) {
                    this.droppedDraws++;
                    return;
                }
                command = DrawCommand.drawIndexedInstanced(indexSpan.indexCount(), 0, 0,
                        instanceCount, firstInstance);
                submit(program, PrimitiveTopology.TRIANGLES, span, indexSpan, command);
                return;
            }
            if (!canonical || clientIndices != null) {
                // On the repack path the indices are rebased (range start folded in); on the
                // canonical path client indices upload as they are. Either way they ride the
                // streaming index buffer.
                indexSpan = streamIndices(indexValues, canonical ? 0 : rangeStart, indexType);
            } else {
                DirectGlRecords.Buffer element = this.buffers.get(currentVertexArray().elementBuffer);
                if (element == null || !element.sinkCreated) {
                    this.droppedDraws++;
                    return;
                }
                indexSpan = new IndexSpan(element.sinkId, indexOffset, count,
                        indexType == GL_UNSIGNED_INT ? IndexSpan.IndexType.UINT32
                                : IndexSpan.IndexType.UINT16);
            }
            if (indexSpan == null) {
                this.droppedDraws++;
                return;
            }
            command = DrawCommand.drawIndexedInstanced(count, 0, drawVertexOffset,
                    instanceCount, firstInstance);
        } else if (mode == GL_QUADS) {
            int[] sequential = new int[count];
            for (int i = 0; i < count; i++) {
                sequential[i] = rangeStart + i;
            }
            indexSpan = quadIndices(sequential, canonical ? 0 : rangeStart);
            if (indexSpan == null) {
                this.droppedDraws++;
                return;
            }
            command = DrawCommand.drawIndexedInstanced(indexSpan.indexCount(), 0, 0,
                    instanceCount, firstInstance);
            submit(program, PrimitiveTopology.TRIANGLES, span, indexSpan, command);
            return;
        } else {
            command = DrawCommand.drawInstanced(count, drawFirstVertex, instanceCount, firstInstance);
        }
        submit(program, topologyOf(mode), span, indexSpan, command);
    }

    private void submit(DirectGlRecords.Program program, PrimitiveTopology topology,
                        VertexBindingSpan span, IndexSpan indexSpan, DrawCommand command) {
        int fboId = boundFramebufferId(GL_DRAW_FRAMEBUFFER);
        int renderTargetId = 0;
        if (fboId != 0) {
            long sinkFbo = ensureSinkFramebuffer(fboId);
            if (sinkFbo == 0L) {
                this.droppedDraws++;
                return;
            }
            renderTargetId = (int) sinkFbo;
        }
        DirectGlDrawState state = new DirectGlDrawState(stateKey(), program.sinkHandle, topology,
                renderTargetId);
        this.sink.submitDraw(state, span, indexSpan, command);
    }

    private boolean isCanonical(java.util.List<ResolvedAttrib> attribs, int canonicalStride) {
        int buffer = attribs.get(0).buffer();
        long cumulative = 0L;
        long base = attribs.get(0).offset();
        for (ResolvedAttrib attrib : attribs) {
            if (attrib.buffer() != buffer) {
                return false;
            }
            if (attrib.offset() - base != cumulative) {
                return false;
            }
            if (attribs.size() > 1 && attrib.effectiveStride() != canonicalStride) {
                return false;
            }
            cumulative += attrib.byteSize();
        }
        return true;
    }

    /** Re-packs the touched vertex range into the streaming buffer in canonical order. */
    private VertexBindingSpan repack(java.util.List<ResolvedAttrib> attribs, int canonicalStride,
                                     int rangeStart, int rangeCount) {
        long needed = (long) canonicalStride * rangeCount;
        ByteBuffer stream = streamVertexShadow(needed);
        if (stream == null) {
            return null;
        }
        for (int v = 0; v < rangeCount; v++) {
            int srcVertex = rangeStart + v;
            int dstBase = v * canonicalStride;
            int cursor = dstBase;
            for (ResolvedAttrib attrib : attribs) {
                DirectGlRecords.Buffer buffer = this.buffers.get(attrib.buffer());
                ByteBuffer shadow = buffer.shadow.duplicate();
                long src = attrib.offset() + (long) srcVertex * attrib.effectiveStride();
                if (src + attrib.byteSize() > shadow.capacity()) {
                    return null;
                }
                for (int i = 0; i < attrib.byteSize(); i++) {
                    stream.put(cursor + i, shadow.get((int) src + i));
                }
                cursor += attrib.byteSize();
            }
        }
        stream.position(0);
        this.sink.bufferOps().writeBuffer(this.streamVertexSinkId, 0L, MemorySegment.ofBuffer(stream));
        return new VertexBindingSpan(this.streamVertexSinkId, 0L, canonicalStride, rangeCount);
    }

    private ByteBuffer streamVertexShadow(long needed) {
        if (this.streamVertexCapacity < needed) {
            long capacity = Math.max(needed, 1L << 16);
            if (this.streamVertexSinkId != 0L) {
                this.sink.bufferOps().destroyBuffer(this.streamVertexSinkId);
            }
            this.streamVertexSinkId = this.sink.bufferOps().createBuffer(capacity, 0);
            this.streamVertexCapacity = capacity;
        }
        return ByteBuffer.allocateDirect((int) needed).order(ByteOrder.nativeOrder());
    }

    /** Writes rebased indices into the streaming index buffer and returns their span. */
    private IndexSpan streamIndices(int[] values, int subtract, int glType) {
        boolean wide = glType == GL_UNSIGNED_INT;
        long bytes = (long) values.length * (wide ? 4 : 2);
        if (this.streamIndexCapacity < bytes) {
            long capacity = Math.max(bytes, 1L << 15);
            if (this.streamIndexSinkId != 0L) {
                this.sink.bufferOps().destroyBuffer(this.streamIndexSinkId);
            }
            this.streamIndexSinkId = this.sink.bufferOps().createBuffer(capacity, 0);
            this.streamIndexCapacity = capacity;
        }
        ByteBuffer shadow = ByteBuffer.allocateDirect((int) bytes).order(ByteOrder.nativeOrder());
        for (int value : values) {
            if (wide) {
                shadow.putInt(value - subtract);
            } else {
                shadow.putShort((short) (value - subtract));
            }
        }
        shadow.flip();
        this.sink.bufferOps().writeBuffer(this.streamIndexSinkId, 0L, MemorySegment.ofBuffer(shadow));
        return new IndexSpan(this.streamIndexSinkId, 0L, values.length,
                wide ? IndexSpan.IndexType.UINT32 : IndexSpan.IndexType.UINT16);
    }

    /** Expands quad indices (four per quad) into triangle indices (six per quad). */
    private IndexSpan quadIndices(int[] quadVertices, int subtract) {
        int quads = quadVertices.length / 4;
        if (quads == 0) {
            return null;
        }
        int[] triangles = new int[quads * 6];
        for (int q = 0; q < quads; q++) {
            int a = quadVertices[q * 4], b = quadVertices[q * 4 + 1];
            int c = quadVertices[q * 4 + 2], d = quadVertices[q * 4 + 3];
            triangles[q * 6] = a;
            triangles[q * 6 + 1] = b;
            triangles[q * 6 + 2] = c;
            triangles[q * 6 + 3] = a;
            triangles[q * 6 + 4] = c;
            triangles[q * 6 + 5] = d;
        }
        return streamIndices(triangles, subtract, GL_UNSIGNED_INT);
    }

    private int[] readIndices(int type, long offset, ByteBuffer clientIndices, int count) {
        int[] out = new int[count];
        if (clientIndices != null) {
            ByteBuffer view = clientIndices.duplicate();
            for (int i = 0; i < count; i++) {
                if (type == GL_UNSIGNED_INT) {
                    if (view.remaining() < 4) {
                        return null;
                    }
                    out[i] = view.getInt();
                } else if (type == GL_UNSIGNED_SHORT) {
                    if (view.remaining() < 2) {
                        return null;
                    }
                    out[i] = view.getShort() & 0xFFFF;
                } else {
                    if (!view.hasRemaining()) {
                        return null;
                    }
                    out[i] = view.get() & 0xFF;
                }
            }
            return out;
        }
        DirectGlRecords.Buffer element = this.buffers.get(currentVertexArray().elementBuffer);
        if (element == null || element.shadow == null) {
            return null;
        }
        ByteBuffer shadow = element.shadow.duplicate();
        for (int i = 0; i < count; i++) {
            long at = offset + (long) i * typeSize(type);
            if (at + typeSize(type) > shadow.capacity()) {
                return null;
            }
            if (type == GL_UNSIGNED_INT) {
                out[i] = shadow.getInt((int) at);
            } else if (type == GL_UNSIGNED_SHORT) {
                out[i] = shadow.getShort((int) at) & 0xFFFF;
            } else {
                out[i] = shadow.get((int) at) & 0xFF;
            }
        }
        return out;
    }
}
