package org.soutalab.celtium.shaderpack;

/**
 * Compiles one already-lowered Vulkan GLSL stage into SPIR-V.
 * <p>
 * The frontend stays pure JDK: this interface is the seam the renderer implements, so the compiler that ships with the
 * mod (an in-process binding, which the mod's own module can carry) does not become a dependency of the pack parser.
 * The external-tool implementation in {@link SpirvStageCompiler} remains for development, where a glslangValidator
 * executable is available and its diagnostics are the ones being iterated on.
 */
public interface GlslCompiler {
    /** {@return the compiled module}, or throws when the source cannot be compiled. */
    SpirvModule compile(Stage stage, String source) throws SpirvStageCompiler.CompileFailure;

    /** {@return a short name for logs}, e.g. {@code shaderc} or {@code glslangValidator}. */
    String name();

    /** {@return whether this compiler can run in this process right now}, false when its bindings are absent. */
    default boolean available() {
        return true;
    }
}
