package org.soutalab.celtium.vulkan;

import org.soutalab.celtium.vulkan.gl.ModShaderCompiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD;
import static org.lwjgl.vulkan.VK10.VK_COMPARE_OP_ALWAYS;

/**
 * Core-owned registry of native fullscreen shader passes (Phase 0).
 * <p>
 * Passes registered here compile directly via {@link ModShaderCompiler} and execute through {@link PassExecutor} using
 * {@link VulkanModProgramPipeline}. Nothing here routes through {@code CeltiumGlStub} or {@code ModProgramRegistry},
 * and its counters stay separate from the stub's program/refusal census.
 */
public final class NativePassRegistry {

    /** Frame execution points where registered native passes may run. */
    public enum PassStage {
        AFTER_SHADOW,
        AFTER_TERRAIN_SOLID,
        AFTER_TERRAIN_TRANSLUCENT,
        BEFORE_PRESENT;

        /** Parses a stage name strictly; rejects null, blank or unknown names. */
        public static PassStage parse(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("pass stage must not be blank");
            }
            try {
                return valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException("invalid pass stage: " + name, unknown);
            }
        }
    }

    /** Pipeline blend, depth and cull state for a native fullscreen pass. */
    public record PassState(
            VulkanColorBlendState colorBlend,
            boolean depthTest,
            boolean depthWrite,
            int depthCompareOp,
            int cull,
            int pushBytes) {
        public PassState {
            if (colorBlend == null) {
                throw new IllegalArgumentException("colorBlend must not be null");
            }
            if (pushBytes < 0) {
                throw new IllegalArgumentException("pushBytes must be >= 0, got " + pushBytes);
            }
        }

        public static PassState opaqueFullscreen() {
            return new PassState(
                    VulkanColorBlendState.of(false, 0, 0, 0, 0, VK_BLEND_OP_ADD, VK_BLEND_OP_ADD,
                            VulkanColorBlendState.allChannels()),
                    false, false, VK_COMPARE_OP_ALWAYS, VulkanCullState.NONE, 0);
        }

        public static PassState alphaBlendFullscreen() {
            return new PassState(VulkanColorBlendState.legacy(), false, false, VK_COMPARE_OP_ALWAYS,
                    VulkanCullState.NONE, 0);
        }
    }

    /** Declaration of one native pass. */
    public record NativePassSpec(
            String name,
            String vertexGlsl,
            String fragmentGlsl,
            List<String> inputTargets,
            String outputTarget,
            PassState state,
            PassStage stage) {
        public NativePassSpec {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("pass name must not be blank");
            }
            if (vertexGlsl == null || vertexGlsl.isBlank()) {
                throw new IllegalArgumentException("vertex GLSL must not be blank for pass " + name);
            }
            if (fragmentGlsl == null || fragmentGlsl.isBlank()) {
                throw new IllegalArgumentException("fragment GLSL must not be blank for pass " + name);
            }
            if (inputTargets == null) {
                throw new IllegalArgumentException("inputTargets must not be null for pass " + name);
            }
            for (String input : inputTargets) {
                if (input == null || input.isBlank()) {
                    throw new IllegalArgumentException("input target name must not be blank for pass " + name);
                }
            }
            inputTargets = List.copyOf(inputTargets);
            if (outputTarget == null || outputTarget.isBlank()) {
                throw new IllegalArgumentException("outputTarget must not be blank for pass " + name);
            }
            if (state == null) {
                throw new IllegalArgumentException("state must not be null for pass " + name);
            }
            if (stage == null) {
                throw new IllegalArgumentException("invalid pass stage: null");
            }
        }
    }

    /** Registered pass along with its SPIR-V compilation outcome. */
    public static final class RegisteredPass {
        private final NativePassSpec spec;
        private final byte[] vertexSpirv;
        private final byte[] fragmentSpirv;
        private final String compileFailure;

        RegisteredPass(NativePassSpec spec, byte[] vertexSpirv, byte[] fragmentSpirv, String compileFailure) {
            this.spec = spec;
            this.vertexSpirv = vertexSpirv;
            this.fragmentSpirv = fragmentSpirv;
            this.compileFailure = compileFailure;
        }

        public NativePassSpec spec() {
            return this.spec;
        }

        public boolean compiled() {
            return this.vertexSpirv != null && this.fragmentSpirv != null;
        }

        public byte[] vertexSpirv() {
            return this.vertexSpirv;
        }

        public byte[] fragmentSpirv() {
            return this.fragmentSpirv;
        }

        public String compileFailure() {
            return this.compileFailure;
        }
    }

    @FunctionalInterface
    interface StageCompiler {
        byte[] compile(String source, boolean vertex);
    }

    private final RenderTargetRegistry targetRegistry;
    private final StageCompiler compiler;
    private final Map<String, RegisteredPass> passes = new LinkedHashMap<>();
    private final Map<PassStage, List<RegisteredPass>> passesByStage = new EnumMap<>(PassStage.class);
    private final Set<String> claimedOutputs = new HashSet<>();

    private int compiledPrograms;
    private int compileRefusals;
    private long executions;
    private long executionRefusals;
    private String lastFailure = "none";

    public NativePassRegistry(RenderTargetRegistry targetRegistry) {
        this(targetRegistry, ModShaderCompiler::compile);
    }

    NativePassRegistry(RenderTargetRegistry targetRegistry, StageCompiler compiler) {
        if (targetRegistry == null) {
            throw new IllegalArgumentException("targetRegistry must not be null");
        }
        this.targetRegistry = targetRegistry;
        this.compiler = compiler != null ? compiler : ModShaderCompiler::compile;
        for (PassStage stage : PassStage.values()) {
            this.passesByStage.put(stage, new ArrayList<>());
        }
    }

    /**
     * Validates and registers one native pass, compiling its GLSL stages to SPIR-V immediately.
     * <p>
     * Structural errors (undeclared input/output targets, double-written output target, invalid stage, duplicate name)
     * throw {@link IllegalArgumentException}. Shader compilation failures do not throw: the pass is retained as
     * refused, counted under {@link #compileRefusalCount()}, and skipped during execution.
     */
    public RegisteredPass register(NativePassSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("pass spec must not be null");
        }
        if (this.passes.containsKey(spec.name())) {
            throw new IllegalArgumentException("duplicate native pass: " + spec.name());
        }
        for (String input : spec.inputTargets()) {
            if (!this.targetRegistry.hasNamedTarget(input)) {
                throw new IllegalArgumentException("undeclared input target '" + input + "' for pass " + spec.name());
            }
            if (input.equals(spec.outputTarget())) {
                throw new IllegalArgumentException(
                        "output target '" + spec.outputTarget() + "' cannot also be an input for pass " + spec.name());
            }
        }
        if (!this.targetRegistry.hasNamedTarget(spec.outputTarget())) {
            throw new IllegalArgumentException(
                    "undeclared output target '" + spec.outputTarget() + "' for pass " + spec.name());
        }
        if (this.claimedOutputs.contains(spec.outputTarget())) {
            throw new IllegalArgumentException(
                    "double write to output target '" + spec.outputTarget() + "' by pass " + spec.name());
        }

        byte[] vertexSpirv = null;
        byte[] fragmentSpirv = null;
        String failure = null;
        try {
            vertexSpirv = this.compiler.compile(spec.vertexGlsl(), true);
            fragmentSpirv = this.compiler.compile(spec.fragmentGlsl(), false);
            if (vertexSpirv == null || vertexSpirv.length == 0 || fragmentSpirv == null || fragmentSpirv.length == 0) {
                vertexSpirv = null;
                fragmentSpirv = null;
                failure = "compiler returned empty SPIR-V";
            }
        } catch (RuntimeException | LinkageError compileError) {
            vertexSpirv = null;
            fragmentSpirv = null;
            failure = compileError.getMessage() == null ? compileError.getClass().getSimpleName()
                    : compileError.getMessage();
        }

        RegisteredPass registered = new RegisteredPass(spec, vertexSpirv, fragmentSpirv, failure);
        this.passes.put(spec.name(), registered);
        this.passesByStage.get(spec.stage()).add(registered);
        this.claimedOutputs.add(spec.outputTarget());

        if (registered.compiled()) {
            this.compiledPrograms++;
        } else {
            this.compileRefusals++;
            this.lastFailure = spec.name() + ": " + failure;
        }
        return registered;
    }

    public boolean hasPasses() {
        return !this.passes.isEmpty();
    }

    public boolean hasPassesFor(PassStage stage) {
        return stage != null && !this.passesByStage.get(stage).isEmpty();
    }

    public List<RegisteredPass> passesFor(PassStage stage) {
        if (stage == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(this.passesByStage.get(stage));
    }

    public RegisteredPass pass(String name) {
        return name == null ? null : this.passes.get(name);
    }

    public int registeredCount() {
        return this.passes.size();
    }

    public int compiledCount() {
        return this.compiledPrograms;
    }

    public int compileRefusalCount() {
        return this.compileRefusals;
    }

    public long executionCount() {
        return this.executions;
    }

    public long executionRefusalCount() {
        return this.executionRefusals;
    }

    public String lastFailure() {
        return this.lastFailure;
    }

    void noteExecution() {
        this.executions++;
    }

    void noteExecutionRefusal(String passName, String reason) {
        this.executionRefusals++;
        this.lastFailure = passName + ": " + reason;
    }

    /** Emits the native pass census line, completely separate from GlStub / ModProgramRegistry counters. */
    public String censusLine(String trigger) {
        return "[Celtium] CELTIUM_NATIVE_PASS via=" + trigger
                + " registered=" + this.passes.size()
                + " compiled=" + this.compiledPrograms
                + " compileRefusals=" + this.compileRefusals
                + " executed=" + this.executions
                + " execRefusals=" + this.executionRefusals
                + " lastFailure=[" + this.lastFailure + "]";
    }

    public void clear() {
        this.passes.clear();
        for (List<RegisteredPass> list : this.passesByStage.values()) {
            list.clear();
        }
        this.claimedOutputs.clear();
        this.compiledPrograms = 0;
        this.compileRefusals = 0;
        this.executions = 0L;
        this.executionRefusals = 0L;
        this.lastFailure = "none";
    }
}
