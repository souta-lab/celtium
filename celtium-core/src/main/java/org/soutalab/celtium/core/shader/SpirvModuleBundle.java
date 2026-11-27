package org.soutalab.celtium.core.shader;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * One program's compiled SPIR-V, per stage: the vertex and fragment modules are always present, the
 * geometry module only when the source pack defined one. Words are raw SPIR-V (32-bit), validated
 * against the SPIR-V magic number at construction - a bundle that is not SPIR-V is a compiler bug,
 * and it is refused here rather than at pipeline creation time on the render thread.
 * <p>
 * This is the celtium-native replacement for the {@code GlProgram} type the old supplier-shaped
 * interface passed around: no GL program name, no GL state, just the compiled modules the Vulkan
 * HAL turns into pipelines.
 */
public record SpirvModuleBundle(int[] vertexWords, int[] fragmentWords, int[] geometryWords) {

    /** The first word of every SPIR-V module. */
    public static final int SPIRV_MAGIC = 0x07230203;

    public SpirvModuleBundle {
        vertexWords = checkedCopy("vertexWords", vertexWords);
        fragmentWords = checkedCopy("fragmentWords", fragmentWords);
        geometryWords = geometryWords == null ? null : checkedCopy("geometryWords", geometryWords);
    }

    private static int[] checkedCopy(String name, int[] words) {
        Objects.requireNonNull(words, name);
        if (words.length == 0 || words[0] != SPIRV_MAGIC) {
            throw new IllegalArgumentException(name + " is not a SPIR-V module (bad magic or empty)");
        }
        return words.clone();
    }

    public static SpirvModuleBundle of(int[] vertexWords, int[] fragmentWords) {
        return new SpirvModuleBundle(vertexWords, fragmentWords, null);
    }

    @Override
    public int[] vertexWords() {
        return vertexWords.clone();
    }

    @Override
    public int[] fragmentWords() {
        return fragmentWords.clone();
    }

    @Override
    public int[] geometryWords() {
        return geometryWords == null ? null : geometryWords.clone();
    }

    public Optional<int[]> geometry() {
        return geometryWords == null ? Optional.empty() : Optional.of(geometryWords.clone());
    }

    public boolean hasGeometry() {
        return geometryWords != null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SpirvModuleBundle bundle)) {
            return false;
        }
        return Arrays.equals(vertexWords, bundle.vertexWords)
                && Arrays.equals(fragmentWords, bundle.fragmentWords)
                && Arrays.equals(geometryWords, bundle.geometryWords);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(vertexWords);
        result = 31 * result + Arrays.hashCode(fragmentWords);
        result = 31 * result + Arrays.hashCode(geometryWords);
        return result;
    }

    @Override
    public String toString() {
        return "SpirvModuleBundle[vertex=" + vertexWords.length + " words, fragment="
                + fragmentWords.length + " words, geometry="
                + (geometryWords == null ? "absent" : geometryWords.length + " words") + "]";
    }
}
