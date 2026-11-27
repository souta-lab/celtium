package org.soutalab.celtium.glcompat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


/**
 * Selects cached command metadata without issuing diagnostic OpenGL state queries.
 */
final class GpuCommandDiagnostics {
    /** Marks metadata that cannot be trusted for the current OpenGL context. */
    static final int UNKNOWN = -1;

    private GpuCommandDiagnostics() {
    }

    /**
     * Determines whether the process-wide GLSM cache belongs to the current context.
     *
     * @param cachingEnabled whether normal GLSM cache tracking is enabled
     * @param bypassCache whether callers explicitly bypass GLSM state caches
     * @param currentContext current thread's OpenGL context identity
     * @param cachedContext OpenGL context identity that owns the cached state
     * @return whether cached program, framebuffer, and texture state is trustworthy
     */
    static boolean isCacheTrusted(
        boolean cachingEnabled,
        boolean bypassCache,
        Object currentContext,
        Object cachedContext
    ) {
        return cachingEnabled && !bypassCache && currentContext != null && currentContext == cachedContext;
    }

    /**
     * Returns cached metadata only when it belongs to the current context.
     *
     * @param cacheTrusted whether the cache belongs to the current context
     * @param cachedValue cached metadata value
     * @return cached value, or {@link #UNKNOWN} when attribution is unsafe
     */
    static int trustedValue(boolean cacheTrusted, int cachedValue) {
        return cacheTrusted ? cachedValue : UNKNOWN;
    }
}
