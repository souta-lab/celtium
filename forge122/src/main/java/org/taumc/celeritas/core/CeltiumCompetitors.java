package org.taumc.celeritas.core;

/**
 * Mods that replace a part of the renderer Celtium owns, detected by a class only their jar carries, and refused at
 * coremod setup with a message that names them.
 * <p>
 * Each entry is a measured conflict, not a guess. Without this the failure surfaced somewhere unrelated: Custom Loading
 * Screen's transformer threw a NullPointerException inside {@code SplashProgress} during game init (E2E-E v1.87.3, leg
 * e2ee-amd2; the same pack with Celeritas passed that point), because Celtium draws the Forge loading screen itself.
 * {@code -Dceltium.allowCompetitors=true} skips the check for diagnosis.
 * <p>
 * The OptiFine, Vintagium and Relictium entries (2026-11-17, item 311) rest on the rendering-mod survey of the same
 * date: each is a wholesale replacement of a path Celtium owns, so the conflict is structural rather than a leg
 * measurement, and each marker class was verified in the mod's own jar, as the entry comments record.
 */
final class CeltiumCompetitors {

    private static final String[][] KNOWN = {
            // {class resource, mod name, what it replaces}
            {"alexiil/mc/mod/load/coremod/ClsPlugin.class", "Custom Loading Screen (customloadingscreen)",
                    "the Forge loading screen, which Celtium draws natively"},
            // SmoothFont: VerifyError in FontRenderer.func_78263_a against Celtium's text path (leg e2ee-amd3).
            {"bre/smoothfont/FontRendererHook.class", "SmoothFont (smoothfont)",
                    "the font renderer's glyph drawing, which Celtium batches natively"},
            // Gnetum: the HUD is drawn into its own framebuffer and composited later (CELTIUM_HUD_GROUP route=chain-pair,
            // CELTIUM_FBO_BIND firstBoundBy=FramebufferManager#reset <- gnetum$loadWorld, legs e2ee-amd14/15); the
            // presented frame lost the hotbar, the hand and the minimap and was darkened or washed out around them.
            {"me/decce/gnetum/FramebufferManager.class", "Gnetum (gnetum)",
                    "the HUD's drawing, which Celtium draws natively every frame"},
            {"meldexun/nothirium/mc/Nothirium.class", "Nothirium", "the terrain renderer"},
            {"zone/rong/naughthirium/Naughthirium.class", "Naughthirium", "the terrain renderer"},
            // OptiFine: a competing whole-renderer overhaul - terrain, the GL/shader pipeline and fonts at once.
            // Marker is its coremod transformer class, verified in the official OptiFine_1.12.2_HD_U_G5.jar from
            // optifine.net (sha256 3b000679...; the jar's MANIFEST names optifine.OptiFineForgeTweaker as its
            // TweakClass).
            {"optifine/OptiFineClassTransformer.class", "OptiFine (optifine)",
                    "the terrain renderer, the GL/shader pipeline and the font renderer"},
            // Vintagium: Sodium's chunk engine ported to 1.12.2 - a second terrain renderer. Marker is its @Mod
            // class (MODID "vintagium", MODNAME "Vintagium" in Asek3/sodium-1.12), verified present in the shipped
            // vintagium-v0.2.jar (sha256 f2492302...) and absent from Relictium's jar, so the two forks are told
            // apart.
            {"me/jellysquid/mods/sodium/client/SodiumClientMod.class", "Vintagium (vintagium)",
                    "the terrain renderer"},
            // Relictium: a Vintagium fork. Its 1.2.0 mcmod.info carries modid "vintagium" again, but the jar's own
            // @Mod class is unique to Relictium; verified in relictium-1.2.0.jar (Modrinth, sha256 2a2d91da...).
            {"io/themade4/relictium/Relictium.class", "Relictium (relictium)", "the terrain renderer"},
    };

    private CeltiumCompetitors() {
    }

    private static final boolean ALLOW_COMPETITORS = Boolean.getBoolean("celtium.allowCompetitors");

    static void check(ClassLoader loader) {
        if (ALLOW_COMPETITORS || loader == null) {
            return;
        }
        StringBuilder found = new StringBuilder();
        for (String[] entry : KNOWN) {
            if (loader.getResource(entry[0]) != null) {
                found.append("\n  - ").append(entry[1]).append(": replaces ").append(entry[2]);
            }
        }
        if (found.length() > 0) {
            throw new IllegalStateException("Celtium replaces Minecraft's renderer and cannot run with mods that"
                    + " replace the same parts. Remove these mods from the instance:" + found);
        }
    }
}
