package org.taumc.celeritas.lwjgl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The upgrading delegate's contract (BH10-01): a degraded first service keeps answering - nothing that
 * worked through the fallback breaks - until an upgrade attempt produces a capable service, and from
 * that call on the capable service answers. Attempts are throttled, and a throwing upgrade source is
 * indistinguishable from one that keeps returning the fallback.
 */
class UpgradingLWJGLServiceTest {

    /** A service answering only the methods these tests call; anything else is unsupported. */
    private static LWJGLService fakeService(boolean degraded, int pointerSize, int vaoName) {
        return (LWJGLService) Proxy.newProxyInstance(UpgradingLWJGLServiceTest.class.getClassLoader(),
                new Class<?>[] { LWJGLService.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "isDegraded" -> degraded;
                    case "getPointerSize" -> pointerSize;
                    case "glGenVertexArrays" -> vaoName;
                    case "toString" -> "fake(degraded=" + degraded + ")";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void capableDelegateIsUsedDirectlyAndNeverUpgraded() {
        AtomicInteger attempts = new AtomicInteger();
        LWJGLService capable = fakeService(false, 8, 42);
        UpgradingLWJGLService service = new UpgradingLWJGLService(capable, () -> {
            attempts.incrementAndGet();
            return fakeService(false, 8, 99);
        }, 0L);

        assertFalse(service.isDegraded());
        assertEquals(42, service.glGenVertexArrays());
        assertEquals(0, attempts.get(), "a capable delegate leaves nothing to upgrade");
    }

    @Test
    void degradedDelegateUpgradesOnceACapableServiceExists() {
        AtomicInteger attempts = new AtomicInteger();
        LWJGLService degraded = fakeService(true, 8, -1);
        LWJGLService capable = fakeService(false, 8, 42);
        UpgradingLWJGLService service = new UpgradingLWJGLService(degraded, () -> {
            attempts.incrementAndGet();
            return capable;
        }, 0L);

        assertTrue(service.isDegraded());
        assertEquals(42, service.glGenVertexArrays(), "the first capable call already sees the upgrade");
        assertFalse(service.isDegraded());
        assertEquals(42, service.glGenVertexArrays());
        assertEquals(1, attempts.get(), "the upgrade happens once, not per call");
    }

    @Test
    void degradedDelegateKeepsAnsweringWhileUpgradesStayDegraded() {
        AtomicInteger attempts = new AtomicInteger();
        LWJGLService degraded = fakeService(true, 8, -1);
        UpgradingLWJGLService service = new UpgradingLWJGLService(degraded, () -> {
            attempts.incrementAndGet();
            return fakeService(true, 8, -1);
        }, 0L);

        assertEquals(8, service.getPointerSize(), "the fallback's own answers are unaffected");
        assertEquals(8, service.getPointerSize());
        assertTrue(service.isDegraded());
        assertEquals(2, attempts.get(), "with no throttle each call retries - and keeps the fallback");
    }

    @Test
    void throttledAttemptsDoNotRetryInsideTheWindow() {
        AtomicInteger attempts = new AtomicInteger();
        LWJGLService degraded = fakeService(true, 8, -1);
        UpgradingLWJGLService service = new UpgradingLWJGLService(degraded, () -> {
            attempts.incrementAndGet();
            return fakeService(true, 8, -1);
        }, Long.MAX_VALUE);

        service.getPointerSize();
        service.getPointerSize();
        service.getPointerSize();

        assertEquals(1, attempts.get(), "attempts inside the retry window are suppressed");
    }

    @Test
    void throwingUpgradeSourceKeepsTheDegradedAnswer() {
        LWJGLService degraded = fakeService(true, 8, -1);
        Supplier<LWJGLService> broken = () -> {
            throw new IllegalStateException("no capabilities on this thread either");
        };
        UpgradingLWJGLService service = new UpgradingLWJGLService(degraded, broken, 0L);

        assertEquals(8, service.getPointerSize(),
                "an upgrade attempt must never break a call the fallback could answer");
        assertTrue(service.isDegraded());
    }
}
