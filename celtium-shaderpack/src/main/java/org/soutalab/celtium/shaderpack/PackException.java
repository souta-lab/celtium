package org.soutalab.celtium.shaderpack;

/**
 * Thrown when a pack cannot be read at all - as opposed to per-file problems
 * which are reported as {@link Diagnostic}s inside a {@link PackManifest}.
 * Always carries a {@link DiagnosticCode} so callers can distinguish
 * "not a shaderpack" from "hostile archive" programmatically.
 */
public class PackException extends Exception {

    private final DiagnosticCode code;

    public PackException(DiagnosticCode code, String message) {
        super(message);
        this.code = code;
    }

    public PackException(DiagnosticCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public DiagnosticCode code() {
        return code;
    }
}
