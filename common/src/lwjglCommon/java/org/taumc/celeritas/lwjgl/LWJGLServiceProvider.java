package org.taumc.celeritas.lwjgl;

/**
 * Loads LWJGLService via ServiceLoader, picking highest priority.
 */
public final class LWJGLServiceProvider {
    public static final LWJGLService LWJGL = createSingleton();
    public static final int POINTER_SIZE = LWJGL.getPointerSize();
    public static final long NULL = 0L;

    private LWJGLServiceProvider() {}

    static LWJGLService constructInstance(String className) {
        try {
            var clz = Class.forName(className);
            var method = clz.getDeclaredMethod("create");
            return (LWJGLService)method.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * The singleton is created by whichever thread touches this class first, and on the GL-less Vulkan
     * path that can be a ChunkBuilder worker with no GL capabilities: the instance it gets is the degraded
     * all-NONE fallback (that fallback is what keeps the worker alive - creating the real service there
     * used to fail the whole class initialization). Freezing that fallback as the process-wide service
     * left the render thread with VAO generation throwing and timer queries silently reading zero for the
     * rest of the session (BH10-01), so a degraded first instance is wrapped in a delegate that re-creates
     * the service once a thread with capabilities asks, and swaps it in.
     */
    private static LWJGLService createSingleton() {
        LWJGLService instance = createInstance();
        return instance.isDegraded() ? new UpgradingLWJGLService(instance, LWJGLServiceProvider::createInstance)
                : instance;
    }

    static LWJGLService createInstance() {
        var override = System.getProperty("celeritas.lwjglService");

        if (override != null) {
            return constructInstance(override);
        }

        // LWJGL 3 only: Celtium runs on Cleanroom, and the LWJGL 2 service it inherited was never selected there.
        return constructInstance("org.taumc.celeritas.lwjgl.lwjgl3.LWJGL3Service");
    }
}
