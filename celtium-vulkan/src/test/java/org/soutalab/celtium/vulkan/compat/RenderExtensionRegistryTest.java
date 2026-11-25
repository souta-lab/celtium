package org.soutalab.celtium.vulkan.compat;

import org.embeddedt.embeddium.impl.render.chunk.light.DynamicLightService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry's bookkeeping and the dynamic light extension's gather: registration order is gather
 * order, duplicate ids are refused, and the extension hands the service's sources to the context sink
 * unchanged - the frame block, not the extension, owns where they land.
 */
class RenderExtensionRegistryTest {

    /** A context that records what it is handed. */
    private static final class RecordingContext implements FrameExtensionContext {
        float[] lights;
        int lightCount = -1;

        @Override
        public double cameraX() {
            return 10.0;
        }

        @Override
        public double cameraY() {
            return 70.0;
        }

        @Override
        public double cameraZ() {
            return -30.0;
        }

        @Override
        public long frameIndex() {
            return 42L;
        }

        @Override
        public void writeLights(float[] sources, int count) {
            this.lights = sources;
            this.lightCount = count;
        }
    }

    private static FrameExtension named(String id, List<String> calls) {
        return new FrameExtension() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public void gather(FrameExtensionContext context) {
                calls.add(id);
            }
        };
    }

    @Test
    void gatherAllRunsInRegistrationOrder() {
        RenderExtensionRegistry registry = new RenderExtensionRegistry();
        List<String> calls = new ArrayList<>();
        registry.register(named("second-registered-first", calls));
        registry.register(named("alpha", calls));
        registry.gatherAll(new RecordingContext());
        assertEquals(List.of("second-registered-first", "alpha"), calls);
    }

    @Test
    void duplicateIdsAreRefused() {
        RenderExtensionRegistry registry = new RenderExtensionRegistry();
        List<String> calls = new ArrayList<>();
        registry.register(named("dup", calls));
        assertThrows(IllegalArgumentException.class, () -> registry.register(named("dup", calls)));
    }

    @Test
    void theDynamicLightExtensionHandsTheGatherToTheSink() {
        DynamicLightService service = (out, cameraX, cameraY, cameraZ) -> {
            assertEquals(10.0, cameraX);
            assertEquals(70.0, cameraY);
            assertEquals(-30.0, cameraZ);
            out[0] = 1.0F;
            out[1] = 2.0F;
            out[2] = 3.0F;
            out[3] = 14.0F;
            return 1;
        };
        DynamicLightExtension extension = new DynamicLightExtension(() -> service);
        RecordingContext context = new RecordingContext();
        extension.gather(context);
        assertEquals(1, context.lightCount);
        assertEquals(14.0F, context.lights[3]);
        assertTrue(extension.id().equals(DynamicLightExtension.ID));
    }

    @Test
    void anAbsentServiceContributesNoSources() {
        DynamicLightExtension extension = new DynamicLightExtension(() -> null);
        RecordingContext context = new RecordingContext();
        extension.gather(context);
        assertEquals(0, context.lightCount);
    }
}
