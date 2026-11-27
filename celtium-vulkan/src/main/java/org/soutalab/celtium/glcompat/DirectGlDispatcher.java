package org.soutalab.celtium.glcompat;

import org.soutalab.celtium.core.state.LightStateRegister;
import org.soutalab.celtium.glcompat.backend.DirectGlExecutor;

/**
 * The converter lane's front door (v0.4.0 plan section 2.3, interface 3): the GL 3.3 / FFP /
 * DisplayList state machine (the converted {@code GLStateManager} and its companions in this
 * package) sits behind this dispatcher, and the dispatcher's one contract job is the Lane 2
 * &harr; Lane 3 state exchange the plan fixes:
 * <ul>
 *   <li>when a mod's raw GL call enters the converter lane, the dispatcher syncs the
 *   {@link LightStateRegister}'s current values (matrix stack, bound texture, the fixed state
 *   key) into the converter and marks the register entered;</li>
 *   <li>when the excursion returns, the dispatcher writes the converter's resulting state back
 *   into the register and marks it dirty, because the mod may have changed GL state behind the
 *   register's back;</li>
 *   <li>Lane 2 code never calls this class or the sink - it reads the register only. That
 *   separation is what the Gate G2 source-scan contract test pins.</li>
 * </ul>
 * The register is attached once by the frame owner; until then the dispatcher still dispatches
 * (the converter keeps its own state), the exchange simply has no Lane 2 side to sync with.
 */
public final class DirectGlDispatcher {

    private static LightStateRegister register;

    private DirectGlDispatcher() {
    }

    /** Attaches the shared register (frame owner's seat; called once at renderer start). */
    public static void attachRegister(LightStateRegister shared) {
        register = shared;
    }

    /** The attached register, or {@code null} before attachment. */
    public static LightStateRegister register() {
        return register;
    }

    /**
     * Opens a converter-lane excursion: the register's values become the converter's starting
     * state, and the register is marked entered (its values are no longer stale).
     */
    public static void enterLane3() {
        LightStateRegister current = register;
        if (current != null) {
            DirectGlExecutor.EXECUTOR.syncFromRegister(current);
            current.markLane3Entered();
        }
    }

    /**
     * Closes a converter-lane excursion: the converter's resulting state is written back and
     * the register is marked dirty for its Lane 2 readers.
     */
    public static void exitLane3() {
        LightStateRegister current = register;
        if (current != null) {
            DirectGlExecutor.EXECUTOR.syncToRegister(current);
            current.markLane3Returned();
        }
    }
}
