package org.soutalab.celtium.vulkan.gl;

/**
 * The push-constant ceiling that chooses a mod program uniform block's route: the selected device's own
 * {@code maxPushConstantsSize}, or the Vulkan guaranteed minimum while no device has been selected. A block at
 * or below it travels as push constants; a block above it is lowered to a uniform buffer instead of refused
 * ({@link ModGlslTranslator}), because the ceiling is a property of the push route, not of the block.
 * <p>
 * Why a holder and not a parameter: the lowering runs from {@code glLinkProgram} through the static
 * {@link ModProgramRegistry}, with no {@code VulkanContext} on that path, so the number the device selection already
 * reads and already enforces is recorded once where the device is chosen and read here by {@link ModGlslTranslator}.
 * It is the same limit {@code VulkanPostChain} takes from the device properties, and the selection refuses to run
 * below it ({@code VulkanContext.PUSH_CONSTANT_BYTES_USED} = 256): {@code VulkanModProgramPipeline} sizes its
 * push range from the lowered block itself, so a block this ceiling routes to push is one the layout can declare.
 * <p>
 * The ceiling used to be a fixed 128 in the translator - the size every implementation guarantees, and the size that
 * held every GTCEu program - which refused every fixed-function emulation program GLSM generates, because those
 * declare three matrices (192 bytes) before any optional uniform. A mod can link a shader during startup, so the
 * no-device case is real rather than a formality: it keeps the guaranteed minimum as the route threshold, so a
 * block above it takes the buffer route - which any device can carry - until selection records the real number.
 */
public final class ModPushConstantLimit {

    /** The size every Vulkan implementation guarantees (1.0-1.3; 256 from 1.4), and the answer before selection. */
    public static final int GUARANTEED_BYTES = 128;

    private static volatile int deviceLimit;
    private static volatile boolean deviceNoted;

    private ModPushConstantLimit() {
    }

    /**
     * Records the push-constant limit of the device selection settled on. Called from the candidate loop, where the
     * limit is already read to refuse a device that cannot carry the immediate path.
     * <p>
     * A lower value already recorded is kept: if this process ever selects twice, the smaller budget is the one that
     * cannot be exceeded, and erring smaller only routes a block to the uniform buffer rather than declaring a
     * push range the device lacks.
     */
    public static void noteDevice(int maxPushConstantsSize) {
        if (maxPushConstantsSize <= 0) {
            throw new IllegalArgumentException("maxPushConstantsSize must be positive, got " + maxPushConstantsSize);
        }
        synchronized (ModPushConstantLimit.class) {
            if (!deviceNoted || maxPushConstantsSize < deviceLimit) {
                deviceLimit = maxPushConstantsSize;
                deviceNoted = true;
            }
        }
    }

    /** {@return the ceiling a lowered block's route is chosen against}: the device's limit, or the guaranteed minimum. */
    public static int limit() {
        return deviceNoted ? deviceLimit : GUARANTEED_BYTES;
    }

    /** {@return where {@link #limit()} came from}, so a route decision names its own basis and not just a number. */
    public static String source() {
        return deviceNoted
                ? "the selected device's maxPushConstantsSize"
                : "no device selected yet: the " + GUARANTEED_BYTES + "-byte Vulkan minimum";
    }

    /** Test-only: forget the noted device, so the no-device fallback can be asserted in the same JVM. */
    static void forgetDeviceForTest() {
        synchronized (ModPushConstantLimit.class) {
            deviceNoted = false;
            deviceLimit = 0;
        }
    }
}
