package org.soutalab.celtium.shaderpack;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Shader file extension to pipeline stage. */
public enum Stage {
    VERTEX(".vsh"),
    TESS_CONTROL(".tcs"),
    TESS_EVAL(".tes"),
    GEOMETRY(".gsh"),
    FRAGMENT(".fsh"),
    COMPUTE(".csh");

    private final String extension;

    Stage(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    private static final Map<String, Stage> BY_EXTENSION = Map.of(
            ".vsh", VERTEX, ".tcs", TESS_CONTROL, ".tes", TESS_EVAL,
            ".gsh", GEOMETRY, ".fsh", FRAGMENT, ".csh", COMPUTE);

    /** @return the stage for a file name like {@code composite1.fsh}, or empty for non-stage extensions */
    static Optional<Stage> ofFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_EXTENSION.get(fileName.substring(dot).toLowerCase(Locale.ROOT)));
    }

    /** Basename without the stage extension ({@code composite1.fsh} → {@code composite1}). */
    static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
