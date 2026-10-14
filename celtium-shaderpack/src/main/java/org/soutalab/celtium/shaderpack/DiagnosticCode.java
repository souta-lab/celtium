package org.soutalab.celtium.shaderpack;

/**
 * Stable machine-readable codes for every condition the frontend can report.
 * Tests and the future mod-side diagnostics surface key off these, not messages.
 */
public enum DiagnosticCode {
    // ---- Pack container ----
    /** Input archive has no readable entries at all. */
    EMPTY_ARCHIVE,
    /** Input contains no {@code shaders/} root - not an OptiFine-style pack. */
    NO_SHADER_ROOT,
    /** A zip entry name is malformed or cannot be canonicalized. */
    MALFORMED_ENTRY_PATH,
    /** A zip entry name tries to escape the archive root ({@code ..}, absolute, drive letter). */
    ENTRY_TRAVERSAL_REJECTED,
    /** The archive holds more entries than the configured limit. */
    ENTRY_LIMIT_EXCEEDED,
    /** A single uncompressed entry exceeds the per-file limit. */
    FILE_LIMIT_EXCEEDED,
    /** The sum of uncompressed entries exceeds the total limit. */
    TOTAL_SIZE_LIMIT_EXCEEDED,
    /** An entry's expansion ratio is implausible for a real shaderpack (zip bomb). */
    SUSPICIOUS_COMPRESSION,
    /** The archive contains the same canonical path twice. */
    DUPLICATE_ENTRY,
    /** The entry is encrypted; encrypted packs are out of scope. */
    ENCRYPTED_ENTRY,
    /** A directory pack member resolves outside the pack root through a symlink. */
    SYMLINK_ESCAPE,
    /** A directory pack member is not a regular file/directory (fifo, device, ...). */
    NOT_REGULAR_FILE,
    /** Underlying I/O failure. */
    IO_ERROR,
    /** shaders/ found nested one level under a single top directory - tolerated. */
    NESTED_SHADERS_ROOT,

    // ---- Source preprocessing ----
    /** {@code #include} of a file that is already on the active include stack. */
    INCLUDE_CYCLE,
    /** {@code #include} target does not exist in the pack. */
    INCLUDE_NOT_FOUND,
    /** {@code #include} target escapes the {@code shaders/} root. */
    INCLUDE_ESCAPE_REJECTED,
    /** {@code #include} of a known-binary resource (png, ...) - skipped. */
    BINARY_INCLUDE,
    /** Include nesting exceeded the depth limit. */
    INCLUDE_DEPTH_EXCEEDED,
    /** A single program expanded more include directives than the limit. */
    EXPANSION_LIMIT_EXCEEDED,
    /** A single program produced more expanded bytes than the limit. */
    OUTPUT_LIMIT_EXCEEDED,
    /** Conditional groups unbalanced ({@code #endif} without {@code #if}, etc.). */
    UNBALANCED_CONDITIONAL,
    /** A conditional expression could not be evaluated. */
    EXPRESSION_ERROR,
    /** A preprocessor directive the frontend does not implement (e.g. {@code #pragma}). */
    UNKNOWN_DIRECTIVE,
    /** {@code #error} reached on a live branch - the pack itself reports the failure. */
    PACK_ERROR_DIRECTIVE,
    /** Source is not valid UTF-8; decoded with replacement. */
    NON_UTF8_SOURCE,
    /** A line exceeds the sanity limit and was rejected. */
    LINE_LIMIT_EXCEEDED,

    // ---- Program census ----
    /** A program file whose basename is not a known OptiFine/Iris program family. */
    UNKNOWN_PROGRAM_NAME,
    /** A lone .vsh/.fsh/.gsh without a fragment counterpart where one is required. */
    STAGE_WITHOUT_PAIR,
    /** Geometry shader stage - not in the initial native compatibility scope. */
    UNSUPPORTED_STAGE_GEOMETRY,
    /** Compute shader stage - not in the initial native compatibility scope. */
    UNSUPPORTED_STAGE_COMPUTE,
    /** Tessellation control/evaluation stage - not in the initial compatibility scope. */
    UNSUPPORTED_STAGE_TESSELLATION,

    // ---- Properties ----
    /** A properties line that could not be parsed as key=value. */
    MALFORMED_PROPERTY_LINE,
    /** A properties key the frontend does not recognize - kept verbatim in the census. */
    UNKNOWN_PROPERTY_KEY,
    /** {@code image.*} declarations - custom images need storage-image support (S7). */
    UNSUPPORTED_FEATURE_CUSTOM_IMAGES,
    /** {@code iris.features.*} declares features that must be checked individually. */
    IRIS_FEATURE_DECLARATION,
    /** Custom uniform/variable expressions ({@code uniform.*}, {@code variable.*}). */
    CUSTOM_UNIFORM_EXPRESSION,

    // ---- Misc ----
    /** File carries a UTF-8 BOM; it is stripped before processing. */
    BOM_PRESENT,
    /** Non-OptiFine program files (voxy_*, dh_* under an unexpected name, etc.). */
    AUXILIARY_PROGRAM
}
