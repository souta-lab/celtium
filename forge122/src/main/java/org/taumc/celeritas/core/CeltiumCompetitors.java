package org.taumc.celeritas.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.taumc.celeritas.CeleritasVintage;

import java.io.IOException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Enumeration;

/**
 * Mods that replace a part of the renderer Celtium owns, detected by a class only their jar carries, and refused at
 * coremod setup with a message that names them. The Phosphor entry is the one exception to the first sentence: it
 * replaces no renderer path, but its startup injection crashes whenever Celtium is present (measured, item 439), so
 * it is refused here, with a pointer to the lighting engine the README recommends, instead of surfacing as a
 * Mixin crash.
 * <p>
 * Each entry is a measured conflict, not a guess. Without this the failure surfaced somewhere unrelated: Custom Loading
 * Screen's transformer threw a NullPointerException inside {@code SplashProgress} during game init (E2E-E v1.87.3, leg
 * e2ee-amd2; the same pack with Celeritas passed that point), because Celtium draws the Forge loading screen itself.
 * {@code -Dceltium.allowCompetitors=true} skips the check for diagnosis.
 * <p>
 * The OptiFine, Vintagium and Relictium entries (2026-11-17, item 311) rest on the rendering-mod survey of the same
 * date: each is a wholesale replacement of a path Celtium owns, so the conflict is structural rather than a leg
 * measurement, and each marker class was verified in the mod's own jar, as the entry comments record.
 * <p>
 * The check has a second seat. The first seat is the coremod's {@code injectData}, but the plugin class name itself
 * can be shadowed: Actinium's jar carries a class with the same fully qualified name (a memberless existence marker),
 * and when that jar is processed first the loader resolves the name to the marker, cannot instantiate it, logs one
 * error line and silently continues without Celtium's coremod - no transformers, no mixins, no refusal. The mod
 * lifecycle is independent of the coremod, so {@code CeleritasVintage.onConstruct} always runs; it re-runs this
 * shared check there and adds {@link #checkPluginShadowing(ClassLoader)}, which detects that the plugin class name
 * resolved outside Celtium's own jar.
 */
public final class CeltiumCompetitors {

    private static final Logger LOGGER = LogManager.getLogger("Celtium");

    private static final String PLUGIN_CLASS_NAME = "org.taumc.celeritas.core.CeleritasLoadingPlugin";
    private static final String PLUGIN_CLASS_RESOURCE = "org/taumc/celeritas/core/CeleritasLoadingPlugin.class";

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
            {"zone/rong/naughthirium/Naughthirium.class", "Naughthirium",
                    "nothing of its own — it is a mixin patch layer over Nothirium (refused above) that"
                            + " un-freezes what Nothirium's static terrain bake freezes"},
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
            // Phosphor: a measured startup crash, not a renderer replacement (item 439, 2026-11-20). Its sliced
            // @ModifyVariable on Chunk.setBlockState finds no target whenever Celtium is present and the launch
            // aborts with an InjectionError (leg 439a). An E1 build with Celtium's only Chunk mixin removed
            // entirely crashed identically, so the trigger is Celtium's presence at config/plugin level, not a
            // mixin sharing the Chunk target. Marker is Phosphor's coremod plugin class, verified in
            // phosphor-forge-mc1.12.2-0.2.7-universal.jar (sha256 4ddfdeca...) and absent from the maintained
            // fork Hesperus (leg 439b, passed), whose plugin class is renamed PhosphorFMLPlugin - so the fork
            // is not itself refused. The message recommends Pulsar, the lighting engine the README's Install
            // section names ("Use Pulsar Lighting Engine, not Alfheim (unsupported)").
            {"me/jellysquid/mods/phosphor/core/PhosphorFMLLoadingPlugin.class", "Phosphor (phosphorlighting)",
                    "the light-propagation internals of Chunk, and it crashes at startup whenever Celtium is"
                            + " present. Phosphor is unmaintained; use Pulsar Lighting Engine instead"
                            + " - see docs/mod-compatibility.md"},
            // External Celeritas addons (2026-11-27, decision D-87): Celtium abolished the Celeritas addon
            // connection surface (Ledger I/II/III) and provides Leaf Culling and GPU-shader Dynamic Lights
            // natively. Refusing these jars at startup prevents opaque MixinApplyError crashes and points users
            // at the native equivalents. Marker classes verified against Karnatour/CeleritasDynamicLights
            // (1.2.11), celeritasleafculling-1.0.2.jar (sha256 cc384657...), and
            // celeritasextra-0.11.3-beta.jar (sha256 83c5c39f...).
            {"toni/sodiumdynamiclights/SodiumDynamicLights.class", "Celeritas Dynamic Lights (celeritasdynamiclights)",
                    "Celeritas addon hooks that Celtium no longer supports. Celtium includes native GPU-shader"
                            + " Dynamic Lights (enable with -Dceltium.dynamicLights=true); remove this addon"
                            + " - see docs/mod-compatibility.md"},
            {"toni/sodiumleafculling/CeleritasLeafCulling.class", "Celeritas Leaf Culling (celeritasleafculling)",
                    "Celeritas addon hooks that Celtium no longer supports. Leaf culling is already built into"
                            + " Celtium natively (leafCullingMode, default SOLID); remove this addon"
                            + " - see docs/mod-compatibility.md"},
            {"com/sumirelabs/celeritasextra/CeleritasExtraMod.class", "Celeritas Extra (celeritasextra)",
                    "Celeritas internal GUI/renderer hooks that Celtium no longer supports. Core features"
                            + " (visible-only animated textures, idle frame cap) are built into Celtium natively;"
                            + " remove this addon - see docs/mod-compatibility.md"},
            // Whole-renderer coexistence refusals (2026-11-27): Celtium holds the whole rendering stack
            // itself, so neither of these runs alongside it. Actinium is a competing whole-renderer
            // overhaul: its GLSM forwards every GL call to a single backend while Celtium's engine owns
            // the same surface, and it ships a second copy of the terrain renderer. Marker is its @Mod
            // class, verified in the Actinium source tree (Actinium-Dev/Actinium, tip ce77eed9). The
            // CeleritasLoadingPlugin class name exists in both jars and must not be used as a marker.
            // Demonica is the Angelica fork whose shader hosting Celtium used to interoperate with; that
            // combination was retired when Celtium took shader pack interpretation in-house. Marker is
            // its @Mod class, verified in Demonica-0.9.1.jar.
            {"com/dhj/actinium/Actinium.class", "Actinium (actinium)",
                    "the terrain renderer, the GL/shader pipeline and shader pack support. Celtium now"
                            + " provides all of these itself and cannot run together with Actinium;"
                            + " remove Actinium - see docs/mod-compatibility.md"},
            {"com/demonica/Demonica.class", "Demonica (demonica)",
                    "the shader pipeline. Celtium now interprets shader packs itself, and the"
                            + " Celtium + Demonica combination is no longer supported; remove Demonica"
                            + " - see docs/mod-compatibility.md"},
    };

    private CeltiumCompetitors() {
    }

    private static final boolean ALLOW_COMPETITORS = Boolean.getBoolean("celtium.allowCompetitors");

    public static void check(ClassLoader loader) {
        if (ALLOW_COMPETITORS || loader == null) {
            return;
        }
        StringBuilder found = collectKnown(loader);
        if (found.length() > 0) {
            throw new IllegalStateException("Celtium replaces Minecraft's renderer and cannot run with mods that"
                    + " replace the same parts. Remove these mods from the instance:" + found);
        }
    }

    private static StringBuilder collectKnown(ClassLoader loader) {
        StringBuilder found = new StringBuilder();
        for (String[] entry : KNOWN) {
            if (loader.getResource(entry[0]) != null) {
                found.append("\n  - ").append(entry[1]).append(": replaces ").append(entry[2]);
            }
        }
        return found;
    }

    /**
     * The second seat's own detection: the loading plugin's class name resolved to a class outside Celtium's jar,
     * so the coremod never ran and the first seat's check never happened. The main signal is the CodeSource
     * mismatch between the resolved plugin class and the {@code @Mod} class, which in the shipped jar share one
     * artifact; the plugin class is loaded without initializing it. A duplicate class name on the classpath
     * (visible through {@code getResources}) corroborates the mismatch and is logged, but is never a refusal
     * reason on its own, because it does not say which copy won. When a known competitor is present as well, the
     * refusal keeps the first seat's header and lists the shadowing as one more line; on its own it uses a header
     * that says what happened. The same {@code -Dceltium.allowCompetitors=true} flag skips this check.
     */
    public static void checkPluginShadowing(ClassLoader loader) {
        if (ALLOW_COMPETITORS || loader == null) {
            return;
        }
        Class<?> pluginClass;
        try {
            pluginClass = Class.forName(PLUGIN_CLASS_NAME, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return;
        }
        URL pluginSource = codeSourceLocation(pluginClass);
        URL ownSource = codeSourceLocation(CeleritasVintage.class);
        if (!sourcesDiffer(pluginSource, ownSource)) {
            return;
        }
        int copies = 0;
        try {
            Enumeration<URL> all = loader.getResources(PLUGIN_CLASS_RESOURCE);
            while (all.hasMoreElements()) {
                all.nextElement();
                copies++;
            }
        } catch (IOException ignored) {
            // The CodeSource mismatch already decides; the copy count only corroborates it in the log.
        }
        LOGGER.error("Celtium's loading plugin class {} resolved to {}, which is not Celtium's own jar ({});"
                + " {} classpath copies of the class exist. Celtium's coremod was skipped by the loader.",
                PLUGIN_CLASS_NAME, pluginSource, ownSource, copies);
        String shadowLine = "\n  - " + jarFileName(pluginSource) + " (another mod): ships a class with the same"
                + " name as Celtium's loading plugin (org.taumc.celeritas.core.CeleritasLoadingPlugin), which"
                + " prevents Celtium itself from loading";
        StringBuilder found = collectKnown(loader);
        if (found.length() > 0) {
            throw new IllegalStateException("Celtium replaces Minecraft's renderer and cannot run with mods that"
                    + " replace the same parts. Remove these mods from the instance:" + found + shadowLine);
        }
        throw new IllegalStateException("Celtium cannot load: another mod ships a class with the same name as"
                + " Celtium's loading plugin, so Celtium's own plugin was replaced before it could run."
                + " Remove these mods from the instance:" + shadowLine);
    }

    private static URL codeSourceLocation(Class<?> type) {
        ProtectionDomain domain = type.getProtectionDomain();
        CodeSource source = domain == null ? null : domain.getCodeSource();
        return source == null ? null : source.getLocation();
    }

    /** Whether two code source locations name different artifacts; an unknown location never differs. */
    static boolean sourcesDiffer(URL first, URL second) {
        return first != null && second != null && !first.toExternalForm().equals(second.toExternalForm());
    }

    /** The file name a code source location points at (the jar, or the last path segment of a classes directory). */
    static String jarFileName(URL source) {
        if (source == null) {
            return "an unknown jar";
        }
        String form = source.toExternalForm();
        int bang = form.indexOf("!/");
        if (bang >= 0) {
            form = form.substring(0, bang);
        }
        if (form.endsWith("/")) {
            form = form.substring(0, form.length() - 1);
        }
        int slash = form.lastIndexOf('/');
        String name = slash >= 0 ? form.substring(slash + 1) : form;
        return name.isEmpty() ? form : name;
    }
}
