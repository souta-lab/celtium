package org.soutalab.celtium.shaderpack;

/**
 * One finding produced while loading a pack. Diagnostics are data: they are
 * collected, sorted, and end up verbatim in the {@link PackManifest}; nothing
 * is printed or thrown for recoverable conditions.
 */
public record Diagnostic(Severity severity, DiagnosticCode code, SourceLocation location, String message)
        implements Comparable<Diagnostic> {

    public Diagnostic {
        if (severity == null || code == null || message == null) {
            throw new IllegalArgumentException("severity, code and message are required");
        }
    }

    public static Diagnostic error(DiagnosticCode code, SourceLocation where, String message) {
        return new Diagnostic(Severity.ERROR, code, where, message);
    }

    public static Diagnostic warning(DiagnosticCode code, SourceLocation where, String message) {
        return new Diagnostic(Severity.WARNING, code, where, message);
    }

    public static Diagnostic unsupported(DiagnosticCode code, SourceLocation where, String message) {
        return new Diagnostic(Severity.UNSUPPORTED, code, where, message);
    }

    /** Deterministic order: location, then severity (worst first), then code, then text. */
    @Override
    public int compareTo(Diagnostic other) {
        int byPath = location.path().compareTo(other.location().path());
        if (byPath != 0) {
            return byPath;
        }
        int byLine = Integer.compare(location.line(), other.location().line());
        if (byLine != 0) {
            return byLine;
        }
        int bySeverity = severity.compareTo(other.severity());
        if (bySeverity != 0) {
            return bySeverity;
        }
        int byCode = code.compareTo(other.code());
        return byCode != 0 ? byCode : message.compareTo(other.message);
    }

    @Override
    public String toString() {
        return severity + " " + code + " " + location + " " + message;
    }
}
