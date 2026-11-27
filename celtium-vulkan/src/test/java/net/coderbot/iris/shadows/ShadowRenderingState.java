package net.coderbot.iris.shadows;

/**
 * Test stub of Iris's shadow-rendering state, standing in for the guest class
 * {@code DemonicaTerrainBridge} binds reflectively. It exists only on the test
 * classpath: its presence makes the bridge's resolution succeed, and its two
 * switches let a test choose between a genuine answer and a guest that throws -
 * the two outcomes the bridge used to collapse into the same silent
 * {@code false} (drawpath audit DP-06).
 */
public final class ShadowRenderingState {

    /** The answer {@link #areShadowsCurrentlyBeingRendered()} returns when not throwing. */
    public static volatile boolean answer;

    /** When set, the guest method throws instead of answering. */
    public static volatile boolean throwOnCall;

    private ShadowRenderingState() {
    }

    public static boolean areShadowsCurrentlyBeingRendered() {
        if (throwOnCall) {
            throw new IllegalStateException("stub shadow state failure");
        }
        return answer;
    }

    /** Restores the quiet defaults (not throwing, answering false). */
    public static void resetStub() {
        answer = false;
        throwOnCall = false;
    }
}
