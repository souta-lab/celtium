package org.soutalab.celtium.core.glcompat;

/**
 * An opaque handle to a converted compute program, owned by the sink's program registry. Zero is
 * never a valid program; it exists as the "no program" value a failed conversion reports.
 */
public record ComputeProgramHandle(long handle) {

    public static final ComputeProgramHandle NONE = new ComputeProgramHandle(0L);

    public boolean isValid() {
        return handle != 0L;
    }
}
