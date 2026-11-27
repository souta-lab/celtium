package org.soutalab.celtium.forge.terrain;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Celtium's own probe of the Fluidlogged API (mod id {@code fluidlogged_api}): the terrain
 * scanner asks it for one chunk section's logged fluids, and the mesher renders those fluids
 * inside their (non-fluid) host blocks. The probe is reflective end to end - the API is an
 * optional mod, and the terrain lane must build and run identically when it is absent.
 * <p>
 * The API surface mirrored here is the one the mod publishes and celtium's existing
 * compatibility code already exercises: the chunk capability
 * ({@code IFluidStateCapability.get(provider)} &rarr; {@code getContainer(sectionY)}), the
 * container's {@code forEach} over (packed position, {@code FluidState}) with its
 * {@code deserializeX/Y/Z} decoders, and {@code FluidState.getState()}. Any failure - mod
 * absent, API moved, capability missing - degrades to "no fluids", never to an exception in a
 * build: the probe resolves once, caches the outcome, and answers empty.
 * <p>
 * Only the section's own 16&sup3; cells are read; fluids logged in the apron (neighbour
 * sections) are not captured, so a fluid face on a section border culls against blocks only.
 */
public final class FluidloggedProbe {
    public static final String MODID = "fluidlogged_api";

    private static final int[] EMPTY = new int[0];

    private final boolean present;
    private final Method capabilityGet;
    private final Method getContainer;
    private final Method fluidStateGetState;
    private volatile boolean broken;

    private FluidloggedProbe(boolean present, Method capabilityGet, Method getContainer,
                             Method fluidStateGetState) {
        this.present = present;
        this.capabilityGet = capabilityGet;
        this.getContainer = getContainer;
        this.fluidStateGetState = fluidStateGetState;
    }

    /** Resolves the API once. Never throws. */
    public static FluidloggedProbe resolve() {
        try {
            if (!Loader.isModLoaded(MODID)) {
                return absent();
            }
            Class<?> capability = Class.forName(
                    "git.jbredwards.fluidlogged_api.api.capability.IFluidStateCapability");
            Class<?> fluidState = Class.forName(
                    "git.jbredwards.fluidlogged_api.api.util.FluidState");
            Method get = null;
            for (Method method : capability.getMethods()) {
                if (method.getName().equals("get") && method.getParameterCount() == 1
                        && java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    get = method;
                    break;
                }
            }
            if (get == null) {
                return absent();
            }
            // getContainer is declared on the capability interface itself (the shape the
            // mod's own examples use), so it resolves without an instance.
            Method container = capability.getMethod("getContainer", int.class);
            Method getState = fluidState.getMethod("getState");
            return new FluidloggedProbe(true, get, container, getState);
        } catch (Throwable ignored) {
            return absent();
        }
    }

    private static FluidloggedProbe absent() {
        return new FluidloggedProbe(false, null, null, null);
    }

    public boolean present() {
        return present && !broken;
    }

    /**
     * The logged fluids of one chunk section as state-table ids over the section's own
     * 16&sup3; cells (index {@code x + 16 * (z + 16 * y)}, section-local; {@code sectionIndex}
     * is the section's Y index, 0..15), or an empty array when the API is absent, the section
     * holds no fluids, or the read failed.
     */
    public int[] sectionFluids(Chunk chunk, int sectionIndex) {
        if (!present() || chunk == null) {
            return EMPTY;
        }
        try {
            Object capability = capabilityGet.invoke(null, chunk);
            if (capability == null) {
                return EMPTY;
            }
            // The API's container lookup takes a block-space Y of the section (its own
            // storage code compares deserializeY >> 4 against the argument's >> 4).
            Object container = getContainer.invoke(capability, sectionIndex << 4);
            if (container == null) {
                return EMPTY;
            }
            Method forEach = findForEach(container.getClass());
            if (forEach == null) {
                return EMPTY;
            }
            Method deserializeX = container.getClass().getMethod("deserializeX", int.class);
            Method deserializeY = container.getClass().getMethod("deserializeY", int.class);
            Method deserializeZ = container.getClass().getMethod("deserializeZ", int.class);
            int[] fluids = new int[4096];
            boolean[] any = {false};
            Class<?> consumerType = forEach.getParameterTypes()[0];
            InvocationHandler handler = (proxy, method, args) -> {
                if (args != null && args.length == 2 && args[0] instanceof Number packed
                        && args[1] != null) {
                    int p = packed.intValue();
                    int x = (int) deserializeX.invoke(container, p);
                    int y = (int) deserializeY.invoke(container, p);
                    int z = (int) deserializeZ.invoke(container, p);
                    // deserializeY answers in block space (the container's own example code
                    // compares y >> 4 against the section index); x/z are chunk-local.
                    if ((y >> 4) == sectionIndex && x >= 0 && x < 16 && z >= 0 && z < 16) {
                        Object state = fluidStateGetState.invoke(args[1]);
                        if (state instanceof IBlockState blockState) {
                            fluids[x + 16 * (z + 16 * (y & 15))] =
                                    Block.BLOCK_STATE_IDS.get(blockState);
                            any[0] = true;
                        }
                    }
                }
                return null;
            };
            Object consumer = Proxy.newProxyInstance(
                    FluidloggedProbe.class.getClassLoader(), new Class<?>[]{consumerType}, handler);
            forEach.invoke(container, consumer);
            return any[0] ? fluids : EMPTY;
        } catch (Throwable failure) {
            broken = true;
            return EMPTY;
        }
    }

    private static Method findForEach(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals("forEach") && method.getParameterCount() == 1) {
                return method;
            }
        }
        return null;
    }
}
