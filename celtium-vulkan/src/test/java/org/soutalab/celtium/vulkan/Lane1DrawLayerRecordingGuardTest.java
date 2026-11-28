package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkCommandBuffer;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-100: {@link VulkanTerrainGpuBackend#celtium$lane1DrawLayer} records into the frame's
 * unified command buffer on the premise that a preceding window opened the recording, and
 * nothing at the entry re-opens it. After a swapchain rebuild is consumed at the present tail
 * (a present-mode change applied in-world), the first terrain draw arrived with the buffer
 * EXECUTABLE and the client died at {@code beginPass} - after the acquire barriers and
 * {@code vkCmdBeginRenderPass} had already been recorded natively.
 * <p>
 * The form-1 guard refuses the layer whenever the buffer is not recording, counted by
 * {@code lane1GuardNotRecording}. The backend is device-bound, so the test allocates it
 * without a constructor (the {@code FlushPendingBeforeGlDrawTest} shape) and installs only
 * the fields the guard family reads: a real {@link VulkanCommandBufferState} driven to the
 * state under test, a non-null command buffer / frame target, and a coordinator whose stage
 * marks need no host.
 */
class Lane1DrawLayerRecordingGuardTest {

    private static Unsafe unsafe() throws Exception {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (Unsafe) theUnsafe.get(null);
    }

    private static void setField(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static long longField(Object target, Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.getLong(target);
    }

    /** A backend whose guard family sees the given command-buffer state and nothing else live. */
    private static VulkanTerrainGpuBackend backendWithState(VulkanCommandBufferState state) throws Exception {
        Unsafe unsafe = unsafe();
        VulkanTerrainGpuBackend backend =
                (VulkanTerrainGpuBackend) unsafe.allocateInstance(VulkanTerrainGpuBackend.class);
        setField(backend, VulkanTerrainGpuBackend.class, "frameCoordinator", new FrameCoordinator(null));
        setField(backend, VulkanTerrainGpuBackend.class, "commandBuffer",
                unsafe.allocateInstance(VkCommandBuffer.class));
        setField(backend, VulkanTerrainGpuBackend.class, "frameTarget",
                java.lang.reflect.Proxy.newProxyInstance(
                        Lane1DrawLayerRecordingGuardTest.class.getClassLoader(),
                        new Class<?>[] {FrameImageTarget.class},
                        (proxy, method, args) -> {
                            throw new UnsupportedOperationException(
                                    "the guard family must not call the frame target: " + method.getName());
                        }));
        setField(backend, VulkanTerrainGpuBackend.class, "commandState", state);
        return backend;
    }

    private static void drawLayer(VulkanTerrainGpuBackend backend) {
        backend.celtium$lane1DrawLayer(0, new float[16], new float[16], 0.0, 0.0, 0.0);
    }

    @Test
    void executableBufferIsRefusedAndCounted() throws Exception {
        VulkanCommandBufferState state = new VulkanCommandBufferState(4242L);
        state.begin();
        state.end();
        VulkanTerrainGpuBackend backend = backendWithState(state);

        drawLayer(backend);

        String summary = backend.celtium$lane1DiagnosticsSummary();
        assertTrue(summary.contains("guardNotRecording=1"), summary);
        assertTrue(summary.contains("guardCmd=0"), summary);
        assertTrue(summary.contains("guardStall=0"), summary);
        assertTrue(summary.contains("guardTarget=0"), summary);
        assertTrue(summary.contains("guardPass=0"), summary);
        assertTrue(summary.contains("drawCalls=1"), summary);
    }

    @Test
    void initialBufferIsRefusedAndCounted() throws Exception {
        VulkanTerrainGpuBackend backend = backendWithState(new VulkanCommandBufferState(4243L));

        drawLayer(backend);

        assertTrue(backend.celtium$lane1DiagnosticsSummary().contains("guardNotRecording=1"),
                backend.celtium$lane1DiagnosticsSummary());
    }

    @Test
    void pendingBufferIsRefusedAndCounted() throws Exception {
        VulkanCommandBufferState state = new VulkanCommandBufferState(4244L);
        state.begin();
        state.end();
        state.submitted();
        VulkanTerrainGpuBackend backend = backendWithState(state);

        drawLayer(backend);

        assertTrue(backend.celtium$lane1DiagnosticsSummary().contains("guardNotRecording=1"),
                backend.celtium$lane1DiagnosticsSummary());
    }

    @Test
    void renderPassStateStillLandsOnThePassGuard() throws Exception {
        VulkanCommandBufferState state = new VulkanCommandBufferState(4245L);
        state.begin();
        state.beginPass(7L);
        VulkanTerrainGpuBackend backend = backendWithState(state);

        drawLayer(backend);

        String summary = backend.celtium$lane1DiagnosticsSummary();
        assertTrue(summary.contains("guardPass=1"), summary);
        assertTrue(summary.contains("guardNotRecording=0"), summary);
    }

    @Test
    void recordingBufferPassesTheGuardAndLandsOnTheViewsGuard() throws Exception {
        VulkanCommandBufferState state = new VulkanCommandBufferState(4246L);
        state.begin();
        VulkanTerrainGpuBackend backend = backendWithState(state);
        // Past the guard, the entry prepares the frame and then needs the atlas/lightmap
        // views; with the frontend installed, the frame already prepared and no atlas, the
        // views guard is the landing pad that proves the recording guard let the call through.
        setField(backend, VulkanTerrainGpuBackend.class, "lane1",
                unsafe().allocateInstance(
                        org.soutalab.celtium.vulkan.terrain.lane1.Lane1TerrainFrontend.class));
        long frameId = longField(backend, VulkanTerrainGpuBackend.class, "frameId");
        setField(backend, VulkanTerrainGpuBackend.class, "lane1PreparedFrame", frameId);

        drawLayer(backend);

        assertEquals(0L, longField(backend, VulkanTerrainGpuBackend.class, "lane1GuardNotRecording"));
        assertEquals(1L, longField(backend, VulkanTerrainGpuBackend.class, "lane1GuardViews"));
    }
}
