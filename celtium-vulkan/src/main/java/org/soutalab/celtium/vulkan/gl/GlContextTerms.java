package org.soutalab.celtium.vulkan.gl;

/**
 * The terms of the one GL context this GL-less renderer creates: the version it reports, and the profile
 * it claims, decided once at window creation from the creation request that was actually in force.
 * <p>
 * The default terms are the BH-35 floor ({@code "3.0.0 Celtium"}, see the GL_VERSION case in
 * {@code GlsmFrontend.stringFor}): 3.0 is the version whose gates are all backed for the
 * <i>compatibility</i> promise, which is what a compatibility context - the default request - asks for.
 * Advertising more for that promise claims entry points this stub answers with recorded no-ops.
 * <p>
 * A <i>core</i>-profile request is a different promise: the requester (a user setting in
 * {@code forge_early.cfg}, or a mod writing {@code ForgeEarlyConfig} before the window exists) declares
 * it will use only the programmable surface. That surface is the one this renderer executes natively -
 * shaders are captured, compiled to SPIR-V by {@code ModShaderCompiler} and drawn by the mod-program
 * pipeline, and buffers, textures, vertex arrays and framebuffer attachments are recorded and issued
 * by the terrain/immediate backends. So when the request in force at window creation is a core profile
 * at 3.3 or above, the created context reports {@code "3.3.0 Celtium"} - the backed cap, never the
 * requested number itself (a 4.6 request does not make this a 4.6 context) - and the version integers
 * and {@code GL_CONTEXT_PROFILE_MASK} tell the same story from the same terms.
 * <p>
 * The terms never depend on who asks: every surface (the stub's LWJGL2-side {@code glGetString}, the
 * LWJGL3 {@code nglGetString} route in {@code CeltiumGllessHint}, and the integer queries) reads this
 * one value, because two surfaces disagreeing about the version is a crash, not a difference of
 * opinion (the two used to disagree - string 3.0, integers 4.6 - and a capability consumer took the
 * 4.4 path and stalled the client; see the GL_MAJOR_VERSION case in {@code GlsmFrontend}).
 * <p>
 * The latch is first-wins and is fed from the window-creation path only (forge122's context-terms
 * transformer, at the same anchor the requester itself writes the request). If nothing latches - a
 * host generation without that anchor, or a failed read - the terms stay at the default floor and the
 * run is exactly the run this renderer has always made. Design record:
 * {@code GLVERSION-DESIGN-2026-11-17.md} in the celtium plan workspace.
 */
public final class GlContextTerms {
    /** GL_CONTEXT_CORE_PROFILE_BIT, the one bit of GL_CONTEXT_PROFILE_MASK a core context sets. */
    public static final int CORE_PROFILE_BIT = 0x00000001;

    /** The backed cap for a core-profile context: the highest version this design claims (see class doc). */
    private static final int CORE_CAP_MAJOR = 3;
    private static final int CORE_CAP_MINOR = 3;

    /** The BH-35 floor: what a compatibility (or unrequested) context reports. */
    private static final Terms DEFAULT_TERMS = new Terms(3, 0, false);

    private static volatile Terms current = DEFAULT_TERMS;
    private static volatile boolean latched;

    private GlContextTerms() {
    }

    /** An immutable set of context terms: one story for the string, the integers and the profile mask. */
    public static final class Terms {
        private final int major;
        private final int minor;
        private final boolean coreProfile;

        private Terms(int major, int minor, boolean coreProfile) {
            this.major = major;
            this.minor = minor;
            this.coreProfile = coreProfile;
        }

        public int major() {
            return this.major;
        }

        public int minor() {
            return this.minor;
        }

        public boolean coreProfile() {
            return this.coreProfile;
        }

        /** The GL_VERSION string for these terms, in the stub's established spelling. */
        public String versionString() {
            return this.major + "." + this.minor + ".0 Celtium";
        }

        /**
         * {@return the GL_CONTEXT_PROFILE_MASK value for these terms, or a negative value when the terms
         * make no profile claim} - the default (compatibility, 3.0) terms predate the profile concept, so
         * the query keeps falling through to whatever answered it before this class existed.
         */
        public int profileMask() {
            return this.coreProfile ? CORE_PROFILE_BIT : -1;
        }
    }

    /**
     * The pure decision: which terms a creation request earns.
     *
     * @param requestedMajor       the major version in force at window creation
     * @param requestedMinor       the minor version in force at window creation
     * @param compatibilityProfile whether the request in force is a compatibility-profile context
     * @return the core terms when the request is a core profile at or above the backed cap, otherwise
     * the default floor terms - a core request below 3.3 earns the floor too, because this design
     * claims exactly one core point and no other
     */
    public static Terms resolve(int requestedMajor, int requestedMinor, boolean compatibilityProfile) {
        if (!compatibilityProfile
                && (requestedMajor > CORE_CAP_MAJOR
                        || requestedMajor == CORE_CAP_MAJOR && requestedMinor >= CORE_CAP_MINOR)) {
            return new Terms(CORE_CAP_MAJOR, CORE_CAP_MINOR, true);
        }
        return DEFAULT_TERMS;
    }

    /**
     * Latches the terms from the creation request in force at window creation. First call wins: the
     * process creates one window and one context, and a later call (a second window attempt, a repeated
     * transformer hit) must not rewrite the terms of the context that already exists.
     */
    public static void noteContextRequest(int requestedMajor, int requestedMinor, boolean compatibilityProfile) {
        if (latched) {
            return;
        }
        synchronized (GlContextTerms.class) {
            if (latched) {
                return;
            }
            current = resolve(requestedMajor, requestedMinor, compatibilityProfile);
            latched = true;
        }
    }

    /** {@return whether a creation request has been latched} - false means the terms are the default floor. */
    public static boolean latched() {
        return latched;
    }

    /** {@return the terms of the created context} - the default floor until a request latches. */
    public static Terms current() {
        return current;
    }

    /** The GL_VERSION string both GL surfaces answer, from the one set of terms. */
    public static String versionString() {
        return current.versionString();
    }

    /** Drops the latch and restores the default terms. For tests only. */
    static synchronized void resetForTest() {
        current = DEFAULT_TERMS;
        latched = false;
    }
}
