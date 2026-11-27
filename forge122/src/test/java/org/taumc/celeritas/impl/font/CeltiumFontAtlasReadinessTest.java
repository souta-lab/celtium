package org.taumc.celeritas.impl.font;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Behavioral pin for DP-25: calling {@link CeltiumFontAtlas#active()} before Minecraft is initialised
 * must return {@code false} without throwing and must not permanently latch {@code buildFailed}.
 */
class CeltiumFontAtlasReadinessTest {

    @Test
    void activeBeforeMinecraftReadyDoesNotLatchBuildFailed() throws Exception {
        Field buildFailedField = CeltiumFontAtlas.class.getDeclaredField("buildFailed");
        buildFailedField.setAccessible(true);

        assertFalse(CeltiumFontAtlas.active(),
                "active() returns false when Minecraft is absent");
        assertFalse(buildFailedField.getBoolean(null),
                "transient unready build must not latch buildFailed");
        assertFalse(CeltiumFontAtlas.active(),
                "repeated active() calls while unready remain retryable without throwing");
        assertFalse(buildFailedField.getBoolean(null),
                "buildFailed remains false across repeated unready calls");
    }

    @Test
    void buildGuardsDoNotLatchOrInvalidateRegistry() throws Exception {
        java.nio.file.Path direct = java.nio.file.Paths.get(
                "forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java");
        java.nio.file.Path path = java.nio.file.Files.exists(direct) ? direct
                : java.nio.file.Paths.get("../forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontAtlas.java");
        String src = java.nio.file.Files.readString(path).replace("\r\n", "\n");
        int start = src.indexOf("private static void build() {");
        int end = src.indexOf("private static void registerAtlasRemaps() {", start);
        org.junit.jupiter.api.Assertions.assertTrue(start >= 0 && end > start, "build() located");
        String body = src.substring(start, end);
        assertFalse(body.contains("buildFailed = true"),
                "unready guards in build() must not latch buildFailed");
        assertFalse(body.contains("FontAtlasRegistry.invalidate()"),
                "unready guards in build() must not invalidate FontAtlasRegistry");
    }
}
