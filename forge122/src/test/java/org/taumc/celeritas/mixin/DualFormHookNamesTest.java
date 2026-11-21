package org.taumc.celeritas.mixin;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dual-form hook names of the LANE-07 sweep (8c8a6839a, with a9e3db579): every hook the sweep
 * touched names its target in both the MCP and the SRG form.
 * <p>
 * Item 430's lesson, which the sweep answered: a hook named in one form only is silently unbound
 * in the other environment behind {@code require = 0} - the mixin loads, nothing logs, and the
 * behaviour the hook carried simply never happens (there, the dynamic-light raise). The sweep
 * wrote both names into {@code method =} and the SRG form into {@code @At} targets across ten
 * mixin classes, each SRG name taken from the build's own mcp_stable 39 table.
 * <p>
 * These tests read the compiled classes' constant pools - the same bytes Mixin reads, in the
 * shape {@code RenderItemTargetsTest} established - and require both forms of every swept pair
 * to still be there. The tick-split invoke targets are pinned as an SRG set: their MCP forms
 * live inside owner-qualified {@code @At} target strings, while the SRG forms are the sweep's
 * own contribution, absent before it.
 */
class DualFormHookNamesTest {

    /** Mixin class resource -> the (MCP, SRG) method-hook pairs the sweep wrote into it. */
    private static final Map<String, String[][]> METHOD_PAIRS = Map.of(
            "/org/taumc/celeritas/mixin/core/DynamicTextureMixin.class",
            new String[][]{{"updateDynamicTexture", "func_110564_a"}},
            "/org/taumc/celeritas/mixin/core/GlStateManagerMixin.class",
            new String[][]{{"matrixMode", "func_179128_n"}},
            "/org/taumc/celeritas/mixin/core/LightmapCoordsMixin.class",
            new String[][]{{"setLightmapTextureCoords", "func_77475_a"}},
            "/org/taumc/celeritas/mixin/core/MinecraftMixin.class",
            new String[][]{
                    {"runGameLoop", "func_71411_J"},
                    {"updateDisplay", "func_175601_h"},
                    {"runTick", "func_71407_l"},
                    {"createDisplay", "func_175609_am"},
                    {"refreshResources", "func_110436_a"},
                    {"drawSplashScreen", "func_180510_a"},
                    {"resize", "func_71370_a"},
                    {"init", "func_71384_a"}},
            "/org/taumc/celeritas/mixin/core/MinecraftTickSplitMixin.class",
            new String[][]{{"runTick", "func_71407_l"}},
            "/org/taumc/celeritas/mixin/core/MinecraftTickTimingMixin.class",
            new String[][]{{"runTick", "func_71407_l"}},
            "/org/taumc/celeritas/mixin/core/WorldTickSplitMixin.class",
            new String[][]{{"updateEntities", "func_72939_s"}},
            "/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.class",
            new String[][]{
                    {"renderEntities", "func_180446_a"},
                    {"loadRenderers", "func_72712_a"},
                    {"updateClouds", "func_72734_e"}},
            "/org/taumc/celeritas/mixin/features/options/MixinGuiIngameForge.class",
            new String[][]{{"renderGameOverlay", "func_175180_a"}});

    @Test
    void everySweptMethodHookNamesBothForms() throws IOException {
        for (Map.Entry<String, String[][]> entry : METHOD_PAIRS.entrySet()) {
            Set<String> utf8 = constantPoolUtf8(readMixinBytes(entry.getKey()));
            for (String[] pair : entry.getValue()) {
                assertTrue(utf8.contains(pair[0]),
                        entry.getKey() + " must keep the MCP name " + pair[0]);
                assertTrue(utf8.contains(pair[1]),
                        entry.getKey() + " must keep the SRG name " + pair[1]
                                + " beside " + pair[0] + " - one form alone is silently unbound"
                                + " in the other environment");
            }
        }
    }

    @Test
    void theSplashAnchorNamesAllThreeForms() throws IOException {
        // The sweep found this anchor matched nothing in either environment: the table names the
        // method init (func_71384_a) and no startGame exists in it. All three names stay listed.
        Set<String> utf8 = constantPoolUtf8(readMixinBytes(
                "/org/taumc/celeritas/mixin/core/MinecraftMixin.class"));
        assertTrue(utf8.contains("startGame"), "the historical name stays listed");
        assertTrue(utf8.contains("init"), "the MCP name stays listed");
        assertTrue(utf8.contains("func_71384_a"), "the SRG name stays listed");
    }

    @Test
    void theTickSplitInvokeTargetsKeepTheirSrgForms() throws IOException {
        Set<String> utf8 = constantPoolUtf8(readMixinBytes(
                "/org/taumc/celeritas/mixin/core/MinecraftTickSplitMixin.class"));
        for (String srg : new String[]{"func_146269_k", "func_184118_az", "func_184124_aB",
                "func_72734_e", "func_72835_b", "func_72939_s", "func_73660_a", "func_73831_a",
                "func_73876_c", "func_74428_b", "func_78765_e"}) {
            assertTrue(utf8.contains(srg),
                    "MinecraftTickSplitMixin must keep the invoke target " + srg);
        }
    }

    @Test
    void theChunkUnloadAndFancyGraphicsTargetsKeepTheirSrgForms() throws IOException {
        Set<String> chunk = constantPoolUtf8(readMixinBytes(
                "/org/taumc/celeritas/mixin/core/terrain/ClientChunkManagerMixin.class"));
        assertTrue(chunk.contains("func_73234_b"), "the provideChunk hook's SRG name stays");
        assertTrue(chunk.contains("func_76623_d"), "the Chunk.onUnload target's SRG form stays");

        Set<String> gui = constantPoolUtf8(readMixinBytes(
                "/org/taumc/celeritas/mixin/features/options/MixinGuiIngameForge.class"));
        assertTrue(gui.contains("func_71375_t"),
                "the isFancyGraphicsEnabled target's SRG form (a9e3db579) stays");
    }

    private static byte[] readMixinBytes(String resource) throws IOException {
        try (InputStream in = DualFormHookNamesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "compiled mixin class not on the test classpath: " + resource);
            return in.readAllBytes();
        }
    }

    /** Every CONSTANT_Utf8 entry of the class file's constant pool. */
    private static Set<String> constantPoolUtf8(byte[] bytes) throws IOException {
        Set<String> out = new HashSet<>();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        try {
            in.readInt(); // magic
            in.readUnsignedShort(); // minor
            in.readUnsignedShort(); // major
            int count = in.readUnsignedShort();
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> out.add(in.readUTF());
                    case 3, 4 -> in.skipBytes(4);
                    case 5, 6 -> {
                        in.skipBytes(8);
                        i++; // long/double take two slots
                    }
                    case 7, 8, 16, 19, 20 -> in.skipBytes(2);
                    case 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                    case 15 -> in.skipBytes(3);
                    default -> throw new IOException("unknown constant pool tag " + tag);
                }
            }
        } catch (EOFException e) {
            throw new IOException("truncated class file", e);
        }
        return out;
    }
}
