package org.soutalab.celtium.vulkan;

import java.util.List;
import java.util.Locale;

/**
 * The recorded hardware-profile gate table: the paths a measured hardware profile has shown to be dangerous,
 * and the feature each one gates. Inherited from Vulcanizator's survival layer (survey V-02): there, a path
 * known to kill the process on a measured profile (Windows/JNA/RTX 5060 - the first fullscreen composite
 * command can terminate the process inside native code, unrecoverable from Java) is gated <em>before start</em>,
 * outside the normal config system, because preventing the command from being recorded is the only defence a
 * native crash leaves. Celtium's equivalent defence existed as a lone OS check inside
 * {@code VulkanContext.asyncPresentDefaultForOs}; this table is the recorded place for such profiles, so the
 * next measured profile is an entry here - with its citation - rather than another scattered condition.
 * <p>
 * An entry gates a feature's <em>default</em>. A deliberate opt-in at the hard layer (the feature's own JVM
 * property, set by a user who knows the profile) remains honoured: that is the tree's recorded decision for
 * the async presenter (external A/B testing is intentional, not a stale flag), and the gate's job is that no
 * user reaches the dangerous path by default or by accident.
 * <p>
 * Entries are added only on measurement - a crash or failure observed on the named profile, cited in the
 * entry. A suspicion earns no gate: issue #422's FIFO flicker on the RX 7900 XTX was not code-indicted and is
 * deliberately absent here.
 */
public final class HardwareProfileGates {

    /** A feature a hardware profile can gate. */
    public enum GatedFeature {
        /** The async presenter (frame submits and present on a worker thread). */
        ASYNC_PRESENT("asyncPresent");

        private final String id;

        GatedFeature(String id) {
            this.id = id;
        }

        /** {@return the feature's id, as it appears in diagnostics}. */
        public String id() {
            return this.id;
        }
    }

    /** One recorded profile: the OS substring (and device substring, when the profile names one) it matches. */
    private record Entry(GatedFeature feature, String osSubstring, String deviceSubstring, String citation) {
        private boolean matches(String osName, String deviceName) {
            if (osName == null || !osName.toLowerCase(Locale.ROOT).contains(this.osSubstring)) {
                return false;
            }
            if (this.deviceSubstring == null) {
                return true;
            }
            // A device-keyed profile fires only when the device is actually named: an unnamed device cannot
            // be claimed to be the measured one.
            return deviceName != null
                    && deviceName.toLowerCase(Locale.ROOT).contains(this.deviceSubstring);
        }
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry(GatedFeature.ASYNC_PRESENT, "win", null,
                    "async presenter benchmarked on Intel/Linux and AMD/Linux only; external Win11/RTX3070 "
                            + "testing twice lost the Vulkan device while the worker was submitting deferred "
                            + "batches (recorded at VulkanContext's async-present default)"));

    private HardwareProfileGates() {
    }

    /**
     * {@return whether a recorded hardware profile gates the feature on this OS / device}: the feature's
     * default must be its safe value here.
     *
     * @param osName     the OS name ({@code os.name}); null matches nothing
     * @param deviceName the Vulkan device name when known; null matches only OS-keyed entries
     */
    public static boolean gated(GatedFeature feature, String osName, String deviceName) {
        return matching(feature, osName, deviceName) != null;
    }

    /** {@return the citation of the profile gating the feature here, or null when no profile gates it}. */
    public static String citation(GatedFeature feature, String osName, String deviceName) {
        Entry entry = matching(feature, osName, deviceName);
        return entry == null ? null : entry.citation();
    }

    private static Entry matching(GatedFeature feature, String osName, String deviceName) {
        for (Entry entry : ENTRIES) {
            if (entry.feature() == feature && entry.matches(osName, deviceName)) {
                return entry;
            }
        }
        return null;
    }
}
