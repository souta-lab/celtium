package org.soutalab.celtium.vulkan.terrain.lane1;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.frame.VulkanCommandStream;
import org.soutalab.celtium.core.shader.AttachmentFormatKey;
import org.soutalab.celtium.core.shader.BlendStateSpec;
import org.soutalab.celtium.core.shader.DescriptorBindingPlan;
import org.soutalab.celtium.core.shader.SpirvModuleBundle;
import org.soutalab.celtium.core.shader.TerrainPassKind;
import org.soutalab.celtium.core.shader.TerrainStageProgram;
import org.soutalab.celtium.vulkan.VulkanContext;
import org.soutalab.celtium.vulkan.VulkanHostBuffer;
import org.soutalab.celtium.vulkan.pack.ShaderPackRuntime;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stream contract between the pack executor and the terrain frontend's draw sink
 * (R-109): the sink must consume any {@link VulkanCommandStream} in the interface's terms.
 * Before the fix, {@code Lane1TerrainFrontend.sinkDraw} unchecked-cast the stream to its
 * private {@code ShellStream}, so the executor's {@link Lane1CommandStream} hit a
 * deterministic {@code ClassCastException} on the first terrain stage of every pack frame
 * and the pack deactivated itself.
 * <p>
 * These tests drive the <b>real</b> sink with the <b>real</b> executor stream - no fake
 * sink, no fake stream (a fake-only contract test is what let the NO_FRAME wiring sleep).
 * The frontend is allocated without its constructor (which needs a live device) and only
 * the fields the sink touches at command count 0 are installed; count 0 is exactly the
 * census-confirmed condition under which the cast fired regardless of scene content, and
 * the only shape reachable without a device (count &gt; 0 reaches VK calls).
 */
class Lane1StreamContractTest {

    /** FRAME_SLOTS is property-clamped to 1..4; the cursor array is sized for the max. */
    private static final int CURSOR_SLOTS = 4;

    private static Unsafe unsafe() throws Exception {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (Unsafe) theUnsafe.get(null);
    }

    private static void setField(Object target, Class<?> owner, String name, Object value)
            throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    /**
     * A device-less context: the private no-arg constructor (the DirectGlUniformSnapshotTest
     * precedent) plus a dummy device handle, so wrapping a {@link VkCommandBuffer} reads a
     * null capabilities object instead of dereferencing a null device.
     */
    private static VulkanContext testContext() throws Exception {
        Constructor<VulkanContext> constructor = VulkanContext.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        VulkanContext context = constructor.newInstance();
        setField(context, VulkanContext.class, "device",
                unsafe().allocateInstance(VkDevice.class));
        return context;
    }

    /** A frontend with only the state the sink touches at command count 0, plus its real
     *  draw stage (built by the real {@code rebuildDrawStage}, so the sink under test is the
     *  one production registers). */
    private static Lane1TerrainFrontend newFrontend(VulkanContext context) throws Exception {
        Lane1TerrainFrontend frontend = (Lane1TerrainFrontend) unsafe()
                .allocateInstance(Lane1TerrainFrontend.class);
        TerrainSectionStore store = new TerrainSectionStore(new HostSlabSpace(1 << 20),
                new HostSlabSpace(1 << 20), new HostSlabSpace(1 << 16));
        setField(frontend, Lane1TerrainFrontend.class, "context", context);
        setField(frontend, Lane1TerrainFrontend.class, "world", new TerrainWorld(store));
        setField(frontend, Lane1TerrainFrontend.class, "indirectCursor", new int[CURSOR_SLOTS]);
        setField(frontend, Lane1TerrainFrontend.class, "indirectBuffers",
                new VulkanHostBuffer[CURSOR_SLOTS]);
        Method rebuild = Lane1TerrainFrontend.class.getDeclaredMethod("rebuildDrawStage");
        rebuild.setAccessible(true);
        rebuild.invoke(frontend);
        return frontend;
    }

    /** Drives the real private sink directly (stage-driven recording would reach real HAL
     *  calls in beginRendering; the stage's own contract is TerrainDrawStageTest's seat). */
    private static void callSinkDraw(Lane1TerrainFrontend frontend, VulkanCommandStream stream)
            throws Exception {
        Method sink = Lane1TerrainFrontend.class.getDeclaredMethod("sinkDraw",
                VulkanCommandStream.class, TerrainPassKind.class, int[].class, int.class);
        sink.setAccessible(true);
        try {
            sink.invoke(frontend, stream, TerrainPassKind.MAIN_SOLID, new int[0], 0);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    private static int[] cursors(Lane1TerrainFrontend frontend) throws Exception {
        return (int[]) getField(frontend, Lane1TerrainFrontend.class, "indirectCursor");
    }

    private static Lane1CommandStream executorStream(VulkanContext context,
                                                       Consumer<TerrainStageProgram> binder,
                                                       long frameId, int frameSlot) {
        return new Lane1CommandStream(context, 1L, binder, new float[0][], 1.0f, 0,
                frameId, frameSlot);
    }

    private static TerrainStageProgram program() {
        return new TerrainStageProgram(TerrainPassKind.MAIN_SOLID,
                SpirvModuleBundle.of(new int[]{0x07230203}, new int[]{0x07230203}),
                AttachmentFormatKey.of(new int[]{44}, 0, 0),
                BlendStateSpec.opaque(1), 0.0f, true, true, DescriptorBindingPlan.empty());
    }

    @BeforeEach
    void closeSeatDefensively() {
        ShaderPackRuntime.endNativeFrame();
    }

    @AfterEach
    void closeSeat() {
        // The seat is static state: leaking an open seat breaks every test in this JVM
        // that assumes no seat is open.
        ShaderPackRuntime.endNativeFrame();
    }

    @Test
    void executorStreamPassesThroughTheRealSink() throws Exception {
        VulkanContext context = testContext();
        Lane1TerrainFrontend frontend = newFrontend(context);
        List<TerrainStageProgram> bound = new ArrayList<>();
        Lane1CommandStream stream = executorStream(context, bound::add, 7L, 1);
        cursors(frontend)[0] = 5;
        // Before the fix this line threw ClassCastException (ShellStream cast). The sink
        // must now consume the executor's stream through the interface alone.
        callSinkDraw(frontend, stream);
        assertEquals(0, cursors(frontend)[0],
                "the per-frame cursor reset must fire on the presented frame id");
    }

    @Test
    void sinkResetKeysOnThePresentedFrameId() throws Exception {
        VulkanContext context = testContext();
        Lane1TerrainFrontend frontend = newFrontend(context);
        Consumer<TerrainStageProgram> binder = program -> { };

        cursors(frontend)[0] = 5;
        callSinkDraw(frontend, executorStream(context, binder, 7L, 0));
        assertEquals(0, cursors(frontend)[0], "first sight of frame 7 resets the cursors");

        cursors(frontend)[0] = 5;
        callSinkDraw(frontend, executorStream(context, binder, 7L, 0));
        assertEquals(5, cursors(frontend)[0], "the same frame id must not reset twice");

        cursors(frontend)[0] = 5;
        callSinkDraw(frontend, executorStream(context, binder, 8L, 0));
        assertEquals(0, cursors(frontend)[0], "a new frame id resets again");
    }

    @Test
    void shellStreamStillResolvesItsOwnSlotAndFrame() throws Exception {
        VulkanContext context = testContext();
        Lane1TerrainFrontend frontend = newFrontend(context);
        setField(frontend, Lane1TerrainFrontend.class, "frameId", 33L);

        Class<?> shellClass = Class.forName(
                "org.soutalab.celtium.vulkan.terrain.lane1.Lane1TerrainFrontend$ShellStream");
        Constructor<?> shellConstructor = shellClass.getDeclaredConstructor(
                Lane1TerrainFrontend.class, VkCommandBuffer.class, long.class, int.class,
                int.class);
        shellConstructor.setAccessible(true);
        VkCommandBuffer dummyBuffer =
                (VkCommandBuffer) unsafe().allocateInstance(VkCommandBuffer.class);
        VulkanCommandStream shell = (VulkanCommandStream) shellConstructor.newInstance(
                frontend, dummyBuffer, 0L, 1, 0);

        assertEquals(1, shell.frameSlot(), "the shell stream presents its construction slot");
        assertEquals(33L, shell.frameId(),
                "the shell stream presents the frontend's current frame id");
        // The frontend's own flow still passes through the sink unchanged.
        callSinkDraw(frontend, shell);
    }

    @Test
    void seatValuesAreStoredAndCleared() throws Exception {
        VulkanContext context = testContext();
        VkCommandBuffer dummyBuffer =
                (VkCommandBuffer) unsafe().allocateInstance(VkCommandBuffer.class);
        ActiveRenderTargets targets = new ActiveRenderTargets(
                List.of(new ActiveRenderTargets.RenderTargetView(7L, 44,
                        ActiveRenderTargets.LoadOp.LOAD, ActiveRenderTargets.StoreOp.STORE)),
                null, 64, 64);

        ShaderPackRuntime.beginNativeFrame(context, dummyBuffer, targets, ctx -> null, 100L, 1);
        assertEquals(100L, ShaderPackRuntime.seatFrameId());
        assertEquals(1, ShaderPackRuntime.seatFrameSlot());
        assertTrue(ShaderPackRuntime.nativeFrameOpen());

        ShaderPackRuntime.endNativeFrame();
        assertEquals(-1, ShaderPackRuntime.seatFrameSlot(),
                "closing the seat returns the slot to its -1 sentinel");
        assertFalse(ShaderPackRuntime.nativeFrameOpen());
    }

    @Test
    void lane1StreamPresentsSeatValuesAndDelegatesBinding() throws Exception {
        VulkanContext context = testContext();
        List<TerrainStageProgram> bound = new ArrayList<>();
        Lane1CommandStream stream = executorStream(context, bound::add, 100L, 1);

        assertEquals(100L, stream.frameId());
        assertEquals(1, stream.frameSlot());

        TerrainStageProgram program = program();
        stream.bindTerrainProgram(program);
        assertEquals(List.of(program), bound,
                "binding delegates to the executor's binder exactly once");
    }
}
