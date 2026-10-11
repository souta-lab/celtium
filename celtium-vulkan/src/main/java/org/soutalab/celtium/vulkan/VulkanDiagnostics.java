package org.soutalab.celtium.vulkan;

/**
 * One gate for the repeating diagnostic lines the renderer writes to stdout.
 * <p>
 * Measured on the operator's own session (2026-10-11): with nothing but {@code -Dceltium.vulkan.glless=true}, the
 * renderer printed {@code CELTIUM_MODEL_MATRIX} for every model list of every frame, and the game's log passed
 * <b>100,000 lines</b> - Prism stopped displaying it and warned that the game was still writing to the file and wasting
 * storage. A repeating line on the render thread is also a synchronous write per line, which is a real cost in the
 * frame the user is playing.
 * <p>
 * So the repeating instruments are off unless something is actually measuring: a validation run
 * ({@code -Dceltium.validation=true} is what the fixture sets, {@code -Dceltium.vulkan.validation=true} is the Khronos
 * layer), or an explicit {@code -Dceltium.vulkan.diagnostics=true}. One-shot lines that identify the configuration
 * (the GL-less markers, the device, the window renderer, the backend description) are deliberately NOT behind this
 * gate: they are what a support log needs, and they are written once.
 */
public final class VulkanDiagnostics {
    /** Explicit switch, for a support session that wants the repeating lines without a validation run. */
    public static final String PROPERTY = "celtium.vulkan.diagnostics";

    private static final boolean ENABLED =
            Boolean.getBoolean(PROPERTY)
                    || Boolean.getBoolean("celtium.validation")
                    || Boolean.getBoolean("celtium.vulkan.validation");

    private VulkanDiagnostics() {
    }

    /** {@return whether the repeating diagnostic lines should be written this run}. */
    public static boolean enabled() {
        return ENABLED;
    }

    /**
     * {@return whether a bounded diagnostic line may still be written}.
     * <p>
     * The bound is the second half of the fix: a validation run is allowed to print, but not without limit. Callers
     * keep their own counter and pass it in, so the line that stops is the caller's own named one.
     */
    public static boolean withinLimit(int written, int limit) {
        return ENABLED && written < limit;
    }
}
