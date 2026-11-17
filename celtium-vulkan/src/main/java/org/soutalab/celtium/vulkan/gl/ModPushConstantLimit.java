package org.soutalab.celtium.vulkan.gl;

/**
 * The push-constant ceiling a mod program's uniform block is lowered against: the selected device's own
 * {@code maxPushConstantsSize}, or the Vulkan guaranteed minimum while no device has been selected.
 * <p>
 * Why a holder and not a parameter: the lowering runs from {@code glLinkProgram} through the static
 * {@link ModProgramRegistry}, with no {@code VulkanContext} on that path, so the number the device selection already
 * reads and already enforces is recorded once where the device is chosen and read here by {@link ModGlslTranslator}.
 * It is the same limit {@code VulkanPostChain} takes from the device properties, and the selection refuses to run
 * below it ({@code VulkanContext.PUSH_CONSTANT_BYTES_USED} = 256), so a block this accepts is one the pipeline layout
 * can declare: {@code VulkanModProgramPipeline} sizes its push range from the lowered block itself.
 * <p>
 * The ceiling used to be a fixed 128 in the translator - the size every implementation guarantees, and the size that
 * held every GTCEu program - which refused every fixed-function emulation program GLSM generates, because those
 * declare three matrices (192 bytes) before any optional uniform. A mod can link a shader during startup, so the
 * no-device case is real rather than a formality: it keeps the guaranteed minimum, and a refusal says which of the two
 * numbers it compared against.
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
     * cannot be exceeded, and erring smaller only refuses a block rather than declaring a range the device lacks.
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

    /** {@return the ceiling a lowered block is checked against}: the device's limit, or the guaranteed minimum. */
    public static int limit() {
        return deviceNoted ? deviceLimit : GUARANTEED_BYTES;
    }

    /** {@return where {@link #limit()} came from}, so a refusal names its own basis and not just a number. */
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
