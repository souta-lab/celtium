package org.soutalab.celtium.shaderpack;

/** Severity of a {@link Diagnostic} produced while reading a shaderpack. */
public enum Severity {
    /** The pack cannot be loaded, or the affected program cannot be planned. */
    ERROR,
    /** Something unusual but non-fatal; load continues. */
    WARNING,
    /**
     * The pack uses a feature the S1 frontend deliberately does not implement
     * (compute stage, geometry stage, custom images, ...). Not a malformed pack -
     * a documented capability gap. These must never be silently dropped.
     */
    UNSUPPORTED
}
