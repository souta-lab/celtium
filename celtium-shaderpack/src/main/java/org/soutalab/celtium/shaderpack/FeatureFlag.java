package org.soutalab.celtium.shaderpack;

/**
 * Capability features a pack exercises. The manifest separates
 * {@code activeFeatures} (live under the evaluated option environment) from
 * {@code conditionalFeatures} (present in the pack but behind disabled options
 * or dead branches) so a caller never has to guess whether e.g. compute shaders
 * will actually run.
 */
public enum FeatureFlag {
    /** {@code .csh} stages exist in at least one program. */
    COMPUTE_STAGE,
    /** {@code .gsh} stages exist. */
    GEOMETRY_STAGE,
    /** {@code .tcs}/{@code .tes} stages exist. */
    TESSELLATION_STAGE,
    /** {@code image.*} declarations - storage images (S7 scope). */
    CUSTOM_IMAGES,
    /** {@code uniform.*} / {@code variable.*} expressions - custom uniform evaluation. */
    CUSTOM_UNIFORMS,
    /** {@code texture.*} bindings to in-pack images. */
    CUSTOM_TEXTURES,
    /** {@code program.<world>/<name>.enabled} gating. */
    PROGRAM_ENABLE_CONDITIONS,
    /** Non-default {@code dimension.properties} mapping. */
    DIMENSION_MAP,
    /** {@code iris.features.*} declarations. */
    IRIS_FEATURE_FLAGS,
    /** {@code dh_*} programs (Distant Horizons). */
    DH_PROGRAMS,
    /** {@code voxy_*} programs or files (Voxy). */
    VOXY_PROGRAMS,
    /** Programs whose basename is outside the known OptiFine families. */
    UNKNOWN_PROGRAM_NAMES,
    /** {@code profile.*} option presets exist. */
    PROFILES,
    /** {@code alphaTest.*} program overrides. */
    ALPHA_TEST_OVERRIDES,
    /** {@code blend.*} program blend overrides. */
    BLEND_OVERRIDES,
    /** {@code size.*} / {@code scale.*} buffer size overrides. */
    BUFFER_SIZE_OVERRIDES,
    /** {@code flip.*} render-target flip directives. */
    TARGET_FLIP_DIRECTIVES
}
