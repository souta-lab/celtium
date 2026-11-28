package org.soutalab.celtium.glcompat.capture;

/**
 * One function-table row: name, return type, arg types, semantics.
 *
 * <p>The row type of the stub's function table since the giant splits; it lived as a nested
 * class of the old {@code GlsmFrontend} until S3-b E4-b2, when the table's ownership moved to
 * {@link DirectGlCaptureEntry} (Neon E4 ruling §1.1: the capture entry absorbs the table's
 * {@code fn()} factory and the {@code R_*}/{@code SEM_*} encodings). The fields are public
 * because the row's readers now span three packages: this package (the table and the
 * trampolines), the old frontend (which keeps reading the same rows for its own dispatch until
 * E4-c retires it) and the dispatch in the entry itself.
 */
public final class GlFn {
    public final String name;
    public final byte ret;
    public final byte[] args;
    public final int semantics;
    /**
     * Whether this is one of the DSA {@code *Named*} entry points, whose first argument is the
     * object name rather than a target whose binding is used.
     * <p>
     * Decided once, here: the buffer-data, sub-data and map entry points used to ask
     * {@code fn.name.contains("NamedBuffer")} on every call, and vanilla uploads through them
     * for every entity and tile entity batch - the profile put {@code String.contains} at 2.1%
     * of the warm window's samples inside block-entity rendering.
     */
    public final boolean namedObject;
    /** Per-function arrival count, under the entry's fn census; the trampoline's producers. */
    public long calls;

    public GlFn(String name, byte ret, byte[] args, int semantics) {
        this.name = name;
        this.ret = ret;
        this.args = args;
        this.semantics = semantics;
        this.namedObject = name.contains("Named");
    }
}
