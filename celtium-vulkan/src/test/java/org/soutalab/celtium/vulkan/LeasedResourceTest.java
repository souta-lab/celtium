package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeasedResourceTest {
    @Test
    void replacingTheOwnerKeepsQueuedAndInFlightDrawsAliveUntilBothComplete() {
        List<String> destroyed = new ArrayList<>();
        LeasedResource<String> resource = new LeasedResource<>("old generation", destroyed::add);
        LeasedResource.Lease<String> queued = resource.acquire();
        LeasedResource.Lease<String> inFlight = resource.acquire();
        resource.close();
        assertEquals("old generation", queued.value());
        assertEquals("old generation", inFlight.value());
        assertTrue(destroyed.isEmpty());
        inFlight.close();
        assertTrue(destroyed.isEmpty());
        queued.close();
        assertEquals(List.of("old generation"), destroyed);
    }

    @Test
    void abortAndRetryDoNotReleaseTheLeaseAndDoubleCloseIsHarmless() {
        List<Integer> destroyed = new ArrayList<>();
        LeasedResource<Integer> resource = new LeasedResource<>(42, destroyed::add);
        LeasedResource.Lease<Integer> lease = resource.acquire();
        resource.close();
        resource.close();
        assertThrows(IllegalStateException.class, resource::acquire);
        // Retrying a recording keeps the same draw lease; no fixed number of frame waits can invalidate it.
        for (int retry = 0; retry < 10; retry++) {
            assertEquals(42, lease.value());
            assertTrue(destroyed.isEmpty());
        }
        lease.close();
        lease.close();
        assertEquals(List.of(42), destroyed);
        assertThrows(IllegalStateException.class, lease::value);
    }

    @Test
    void leaseCanEndBeforeTheOwnerAndAnUnusedResourceIsDestroyedImmediately() {
        List<Integer> destroyed = new ArrayList<>();
        LeasedResource<Integer> resource = new LeasedResource<>(1, destroyed::add);
        resource.acquire().close();
        assertTrue(destroyed.isEmpty());
        resource.close();
        new LeasedResource<>(2, destroyed::add).close();
        assertEquals(List.of(1, 2), destroyed);
    }

    @Test
    void groupRetirementReleasesALeaseEvenWhenObjectPoolingIsDisabled() {
        List<String> destroyed = new ArrayList<>();
        LeasedResource<String> resource = new LeasedResource<>("mesh", destroyed::add);
        ImmediateGroup group = new ImmediateGroup(null, null, null, 7, 0, 0, null, 1);
        group.geometryLease = resource.acquire();
        resource.close();
        assertTrue(destroyed.isEmpty());
        ImmediateGroup.recycle(group);
        assertEquals(List.of("mesh"), destroyed);
        assertNull(group.geometryLease);
    }
}
